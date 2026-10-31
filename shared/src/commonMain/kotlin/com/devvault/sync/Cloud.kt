package com.devvault.sync

import com.devvault.crypto.E2ee
import com.devvault.crypto.KdfParams
import com.devvault.crypto.LocalCipher
import com.devvault.data.Db
import com.devvault.data.Repository
import com.devvault.model.CloudState
import com.devvault.model.CloudUser
import com.devvault.model.ServerInfo
import com.devvault.model.SyncStatus
import com.devvault.platform.Platform
import io.ktor.http.HttpMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64

class CloudException(message: String) : Exception(message)

/**
 * 云端客户端，接口约定见桌面端 docs/server-api.md，行为与桌面端 src/main/cloud.ts 一致。
 */
class Cloud(private val db: Db, private val repo: Repository) {
    private object Key {
        const val SERVER = "cloud.serverUrl"
        const val TOKEN = "cloud.token"
        const val USER = "cloud.user"
        const val VAULT = "cloud.vaultKey"
    }

    private val apiVersion = 1
    internal val json = Json { ignoreUnknownKeys = true; explicitNulls = true; encodeDefaults = true }
    private val http = CloudHttp()

    private val _state = MutableStateFlow(CloudState())
    val state: StateFlow<CloudState> = _state.asStateFlow()

    /** 登录 / 退出时回调（同步模块据此启动或停止） */
    var onSessionChange: (Boolean) -> Unit = {}

    /** 登录失效（服务端返回 401）时回调，用于提示用户重新登录 */
    var onSessionExpired: () -> Unit = {}

    suspend fun load() {
        val s = db.io { readState() }
        _state.update { s.copy(sync = it.sync) }
    }

    private fun readState(): CloudState {
        val user = repo.getSetting(Key.USER)?.let {
            try { json.decodeFromString(CloudUser.serializer(), it) } catch (e: Exception) { null }
        }
        val token = LocalCipher.decrypt(repo.getSetting(Key.TOKEN))
        val vault = LocalCipher.decrypt(repo.getSetting(Key.VAULT))
        return CloudState(
            serverUrl = repo.getSetting(Key.SERVER) ?: "",
            user = user,
            loggedIn = user != null && token.isNotEmpty() && vault.isNotEmpty(),
        )
    }

    fun setSyncStatus(f: (SyncStatus) -> SyncStatus) = _state.update { it.copy(sync = f(it.sync)) }

    /** 当前账号的数据密钥；未登录返回 null（需在数据库线程调用） */
    internal fun vaultKey(): ByteArray? =
        LocalCipher.decrypt(repo.getSetting(Key.VAULT)).takeIf { it.isNotEmpty() }?.let { Base64.decode(it) }

    private fun token(): String = LocalCipher.decrypt(repo.getSetting(Key.TOKEN))

    /** 当前账号标识（服务器 + 用户 id），用于检测同步过程中是否切换了账号 */
    fun currentAccount(): String? = _state.value.let { if (it.loggedIn && it.user != null) "${it.serverUrl}|${it.user.id}" else null }

    private fun normalizeUrl(url: String): String {
        val u = url.trim().trimEnd('/')
        if (!Regex("^https?://[^/\\s]+", RegexOption.IGNORE_CASE).containsMatchIn(u)) {
            throw CloudException("服务器地址需以 http:// 或 https:// 开头")
        }
        return u
    }

    private suspend fun send(url: String, method: HttpMethod, body: String? = null, token: String? = null): HttpResult =
        http.send(url, method, body, token)

    private fun readError(res: HttpResult): String {
        try {
            val body = json.parseToJsonElement(res.body) as? JsonObject
            body?.get("message")?.jsonPrimitive?.contentOrNull?.let { if (it.isNotEmpty()) return it }
        } catch (_: Exception) {
            // 非 JSON 响应
        }
        return if (res.status == 401) "账号或密码错误" else "请求失败（HTTP ${res.status}）"
    }

    private suspend fun postJson(url: String, body: String): String {
        val res = send(url, HttpMethod.Post, body)
        if (!res.ok) throw CloudException(readError(res))
        return res.body
    }

    /** 连接测试：读取服务器信息并检查接口版本 */
    suspend fun serverInfo(serverUrl: String): ServerInfo {
        val base = normalizeUrl(serverUrl)
        val res = send("$base/api/server/info", HttpMethod.Get)
        if (!res.ok) {
            throw CloudException(if (res.status == 404) "该地址不是 DevVault 服务器" else readError(res))
        }
        val info = try {
            json.decodeFromString(ServerInfo.serializer(), res.body)
        } catch (e: Exception) {
            null
        }
        if (info == null || info.apiVersion == 0) throw CloudException("该地址不是 DevVault 服务器")
        if (info.apiVersion > apiVersion) throw CloudException("服务器版本较新，请升级客户端")
        if (info.apiVersion < apiVersion) throw CloudException("服务器版本过旧，请联系管理员升级")
        return info
    }

    @Serializable
    private class PreloginResponse(val kdf: KdfParams)

    @Serializable
    private class LoginResponse(val token: String, val user: JsonObject, val kdf: KdfParams? = null, val encryptedVaultKey: String)

    @Serializable
    private class LoginRequest(val username: String, val authKey: String, val device: String)

    @Serializable
    private class RegisterRequest(
        val username: String,
        val name: String,
        val email: String?,
        val kdf: KdfParams,
        val authKey: String,
        val encryptedVaultKey: String,
        val device: String,
    )

    private suspend fun saveSession(base: String, data: LoginResponse, vault: ByteArray) {
        val u = data.user
        fun field(k: String) = (u[k] as? JsonPrimitive)?.contentOrNull
        val id = field("id") ?: throw CloudException("服务器返回的数据格式不正确")
        val username = field("username") ?: ""
        val user = CloudUser(id = id, name = field("name")?.ifEmpty { null } ?: username, username = username, email = field("email"))
        db.io {
            repo.setSetting(Key.SERVER, base)
            repo.setSetting(Key.TOKEN, LocalCipher.encrypt(data.token))
            repo.setSetting(Key.VAULT, LocalCipher.encrypt(Base64.encode(vault)))
            repo.setSetting(Key.USER, json.encodeToString(CloudUser.serializer(), user))
        }
        load()
        onSessionChange(true)
    }

    private fun checkInput(username: String, password: String): String {
        val u = username.trim()
        if (u.isEmpty() || password.isEmpty()) throw CloudException("请输入账号和密码")
        return u
    }

    suspend fun login(serverUrl: String, username: String, password: String) {
        val base = normalizeUrl(serverUrl)
        val u = checkInput(username, password)
        serverInfo(base)
        val pre = json.decodeFromString(
            PreloginResponse.serializer(),
            postJson("$base/api/auth/prelogin", json.encodeToString(JsonObject.serializer(), JsonObject(mapOf("username" to JsonPrimitive(u))))),
        )
        // scrypt 计算量大，放到后台线程
        val keys = withContext(Dispatchers.Default) { E2ee.deriveKeys(password, pre.kdf) }
        val data = json.decodeFromString(
            LoginResponse.serializer(),
            postJson("$base/api/auth/login", json.encodeToString(LoginRequest.serializer(), LoginRequest(u, keys.authKey, Platform.deviceName))),
        )
        saveSession(base, data, E2ee.unwrapVaultKey(data.encryptedVaultKey, keys.wrapKey))
    }

    suspend fun register(serverUrl: String, username: String, password: String, name: String) {
        val base = normalizeUrl(serverUrl)
        val u = checkInput(username, password)
        if (password.length < 8) throw CloudException("主密码至少 8 位")
        val info = serverInfo(base)
        if (!info.allowRegistration) throw CloudException("该服务器未开放注册，请联系管理员")
        val kdf = E2ee.newKdfParams()
        val keys = withContext(Dispatchers.Default) { E2ee.deriveKeys(password, kdf) }
        val vault = E2ee.newVaultKey()
        val body = RegisterRequest(u, name.trim(), null, kdf, keys.authKey, E2ee.wrapVaultKey(vault, keys.wrapKey), Platform.deviceName)
        val data = json.decodeFromString(
            LoginResponse.serializer(),
            postJson("$base/api/auth/register", json.encodeToString(RegisterRequest.serializer(), body)),
        )
        saveSession(base, data, vault)
    }

    private suspend fun clearSession() {
        db.io {
            repo.setSetting(Key.TOKEN, null)
            repo.setSetting(Key.USER, null)
            repo.setSetting(Key.VAULT, null)
        }
        _state.update { it.copy(user = null, loggedIn = false, sync = SyncStatus()) }
        onSessionChange(false)
    }

    suspend fun logout() {
        val server = _state.value.serverUrl
        val t = db.io { token() }
        clearSession()
        // 尽力通知服务端注销，失败不影响本地退出
        if (server.isNotEmpty() && t.isNotEmpty()) {
            try { send("$server/api/auth/logout", HttpMethod.Post, token = t) } catch (_: Exception) { }
        }
    }

    @Serializable
    private class ChangePasswordRequest(val currentAuthKey: String, val kdf: KdfParams, val authKey: String, val encryptedVaultKey: String)

    /** 修改主密码：用新密码重新包裹数据密钥，已上传的数据不必重新加密；其他设备需重新登录 */
    suspend fun changePassword(current: String, next: String) {
        val s = _state.value
        val user = s.user ?: throw CloudException("该功能需要登录后使用")
        val vault = db.io { vaultKey() } ?: throw CloudException("该功能需要登录后使用")
        if (next.length < 8) throw CloudException("主密码至少 8 位")
        val pre = json.decodeFromString(
            PreloginResponse.serializer(),
            postJson("${s.serverUrl}/api/auth/prelogin", json.encodeToString(JsonObject.serializer(), JsonObject(mapOf("username" to JsonPrimitive(user.username))))),
        )
        val nextKdf = E2ee.newKdfParams()
        val (currentKeys, nextKeys) = withContext(Dispatchers.Default) { E2ee.deriveKeys(current, pre.kdf) to E2ee.deriveKeys(next, nextKdf) }
        val body = ChangePasswordRequest(currentKeys.authKey, nextKdf, nextKeys.authKey, E2ee.wrapVaultKey(vault, nextKeys.wrapKey))
        try {
            request("/api/auth/password", HttpMethod.Post, json.encodeToString(ChangePasswordRequest.serializer(), body))
        } catch (e: CloudException) {
            // 服务端对当前密码错误返回 403，统一提示
            throw if (e.message?.contains("403") == true) CloudException("当前主密码不正确") else e
        }
    }

    /** 以当前登录身份调用服务端接口；登录失效（401）时自动退出登录 */
    suspend fun request(path: String, method: HttpMethod = HttpMethod.Get, body: String? = null): String {
        val s = _state.value
        if (!s.loggedIn) throw CloudException("该功能需要登录后使用")
        val t = db.io { token() }
        val res = send("${s.serverUrl}$path", method, body, t)
        if (res.status == 401) {
            clearSession()
            onSessionExpired()
            throw CloudException("登录已过期，请重新登录")
        }
        if (!res.ok) throw CloudException(readError(res))
        return if (res.status == 204) "" else res.body
    }
}
