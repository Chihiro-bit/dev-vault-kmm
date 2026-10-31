package com.devvault.sync

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal class HttpResult(val status: Int, val body: String) {
    val ok: Boolean get() = status in 200..299
}

/**
 * 不携带 SNI 的 HTTPS 请求（仍校验证书链与原始主机名）。
 * 平台不支持时 [supported] 为 false。
 */
internal expect object SniFreeHttp {
    val supported: Boolean

    suspend fun request(url: String, method: String, headers: Map<String, String>, body: String?, timeoutMs: Int): HttpResult
}

/**
 * 云端请求，移植自桌面端 src/main/cloud-http.ts：
 * 某些网络会中断携带 SNI 的 TLS 握手，对指定的服务器先用常规方式探测，失败后改用不带 SNI 的连接。
 * 不替换服务器地址，也不会把令牌发往其他主机。
 */
internal class CloudHttp {
    private val client = HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = TIMEOUT_MS
            connectTimeoutMillis = TIMEOUT_MS
        }
        expectSuccess = false
    }

    /** origin → 是否走兼容线路 */
    private val routes = HashMap<String, Boolean>()
    private val mutex = Mutex()

    private fun originOf(url: String): String =
        Regex("^(https?://[^/?#]+)", RegexOption.IGNORE_CASE).find(url)?.groupValues?.get(1)?.lowercase() ?: url

    private suspend fun normal(url: String, method: HttpMethod, headers: Map<String, String>, body: String?, timeout: Long): HttpResult =
        withContext(Dispatchers.Default) {
            val res = client.request(url) {
                this.method = method
                headers.forEach { (k, v) -> header(k, v) }
                if (body != null) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
                timeout { requestTimeoutMillis = timeout; connectTimeoutMillis = timeout }
            }
            HttpResult(res.status.value, res.bodyAsText())
        }

    private suspend fun sniFree(url: String, method: HttpMethod, headers: Map<String, String>, body: String?): HttpResult {
        val h = if (body != null) headers + ("Content-Type" to "application/json") else headers
        return SniFreeHttp.request(url, method.value, h, body, TIMEOUT_MS.toInt())
    }

    /** 只针对兼容名单中的服务器探测线路；结果缓存，请求失败时清除以便重新探测 */
    private suspend fun useCompatibilityRoute(url: String): Boolean {
        val origin = originOf(url)
        if (origin !in COMPAT_ORIGINS || !SniFreeHttp.supported) return false
        return mutex.withLock {
            routes[origin]?.let { return@withLock it }
            // 先用无身份信息的 GET 探测，不重试登录、修改或同步推送
            val route = try {
                normal("$origin/api/server/info", HttpMethod.Get, emptyMap(), null, PROBE_TIMEOUT_MS)
                false
            } catch (e: Exception) {
                val res = sniFree("$origin/api/server/info", HttpMethod.Get, emptyMap(), null)
                if (!res.ok) throw CloudException("连接探测失败（HTTP ${res.status}）")
                val info = try { Json.parseToJsonElement(res.body) as? JsonObject } catch (_: Exception) { null }
                val version = info?.get("apiVersion") as? JsonPrimitive
                if (version == null || version.isString || version.content.toDoubleOrNull() == null) {
                    throw CloudException("该地址不是 DevVault 服务器")
                }
                true
            }
            routes[origin] = route
            route
        }
    }

    suspend fun send(url: String, method: HttpMethod, body: String?, token: String?): HttpResult {
        val headers = if (token != null) mapOf("Authorization" to "Bearer $token") else emptyMap()
        try {
            return if (useCompatibilityRoute(url)) sniFree(url, method, headers, body)
            else normal(url, method, headers, body, TIMEOUT_MS)
        } catch (e: CloudException) {
            mutex.withLock { routes.remove(originOf(url)) }
            throw e
        } catch (e: Exception) {
            mutex.withLock { routes.remove(originOf(url)) }
            val name = e::class.simpleName.orEmpty()
            val timeout = "Timeout" in name || "timeout" in (e.message ?: "")
            throw CloudException(if (timeout) "连接服务器超时，请检查地址或网络" else "无法连接服务器，请检查地址或网络")
        }
    }

    companion object {
        const val TIMEOUT_MS = 15_000L
        const val PROBE_TIMEOUT_MS = 5_000L

        /** 与桌面端一致：只对当前自建服务器启用兼容探测，其他服务器使用系统默认网络 */
        val COMPAT_ORIGINS = setOf("https://vault.749log.com")
    }
}
