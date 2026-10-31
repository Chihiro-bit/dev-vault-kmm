package com.devvault.data

import com.devvault.crypto.E2ee
import com.devvault.crypto.KdfParams
import com.devvault.crypto.Scrypt
import com.devvault.crypto.hkdfSha256
import com.devvault.platform.Platform
import com.devvault.util.Dates
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64

/** 账号安全策略（随账号同步，修改需安全码），与桌面端 src/main/policy.ts 一致 */
data class PolicyState(val allowExport: Boolean = true, val wipeOnLogout: Boolean = false, val hasCode: Boolean = false)

/** 安全码哈希：hash = HKDF-SHA256(scrypt(NFKC(安全码), salt), info = "devvault/policy-code/v1") */
@Serializable
data class CodeHash(val kdf: KdfParams, val hash: String)

class PolicyException(message: String) : Exception(message)

/**
 * 策略读写、安全码校验与本机数据清除。数据库操作需在 [Db.io] 中调用（[Db] 的方法本身是同步的）。
 * 服务端只保存密文，策略只能约束正常使用 DevVault 客户端的人。
 */
class Policy(private val db: Db, private val repo: Repository) {
    private val json = Json { ignoreUnknownKeys = true }
    private var fails = 0
    private var lockedUntil = 0L

    private fun row(): Row? = db.one("SELECT allow_export, wipe_on_logout, code FROM policy WHERE id = 1")

    private fun parseCode(raw: String?): CodeHash? = raw?.takeIf { it.isNotEmpty() }?.let {
        try {
            json.decodeFromString(CodeHash.serializer(), it).takeIf { c -> c.kdf.type == "scrypt" }
        } catch (e: Exception) {
            null
        }
    }

    fun state(): PolicyState {
        val r = row() ?: return PolicyState()
        return PolicyState(r.bool("allow_export"), r.bool("wipe_on_logout"), parseCode(r.strOrNull("code")) != null)
    }

    /** 限制了数据外流（禁止导出或退出时清除）时，切换账号不会把本机数据合并到新账号 */
    fun isRestrictive(): Boolean = state().let { !it.allowExport || it.wipeOnLogout }

    // ---------- 安全码 ----------

    private fun hashCode(code: String): CodeHash = E2ee.newKdfParams().let { CodeHash(it, PolicyCode.derive(code, it)) }

    private fun derive(code: String, kdf: KdfParams): String = PolicyCode.derive(code, kdf)

    private fun verify(code: String, stored: CodeHash) {
        val wait = lockedUntil - Dates.nowMs()
        if (wait > 0) throw PolicyException("安全码错误次数过多，请 ${(wait + 999) / 1000} 秒后再试")
        val actual = Base64.decode(derive(code, stored.kdf))
        val expected = Base64.decode(stored.hash)
        var diff = actual.size xor expected.size
        for (i in 0 until minOf(actual.size, expected.size)) diff = diff or (actual[i].toInt() xor expected[i].toInt())
        if (diff == 0) {
            fails = 0
            return
        }
        if (++fails >= MAX_FAILS) {
            fails = 0
            lockedUntil = Dates.nowMs() + LOCK_MS
        }
        throw PolicyException("安全码不正确")
    }

    /**
     * 修改策略（含 scrypt 计算，调用方应放在后台线程）。
     * 尚未设置安全码时必须提供 newCode；已设置时需提供正确的 code，提供 newCode 则同时更换。
     */
    fun update(code: String?, newCode: String?, allowExport: Boolean, wipeOnLogout: Boolean): PolicyState {
        val stored = parseCode(row()?.strOrNull("code"))
        if (stored != null) verify(code.orEmpty(), stored)
        else if (newCode.isNullOrEmpty()) throw PolicyException("请先设置安全码")
        if (newCode != null && newCode.length < MIN_CODE_LENGTH) throw PolicyException("安全码至少 $MIN_CODE_LENGTH 位")
        val codeJson = if (!newCode.isNullOrEmpty()) json.encodeToString(CodeHash.serializer(), hashCode(newCode)) else row()?.str("code").orEmpty()
        db.exec("UPDATE policy SET allow_export = ?, wipe_on_logout = ?, code = ? WHERE id = 1", allowExport, wipeOnLogout, codeJson)
        return state()
    }

    // ---------- 清除 ----------

    /** 恢复默认策略（修改时间记为 1，不会覆盖其他账号的设置） */
    fun resetRow() = db.tx {
        db.exec("INSERT INTO sync_apply (x) VALUES (1)")
        db.exec("UPDATE policy SET allow_export = 1, wipe_on_logout = 0, code = '', sync_rev = 0, sync_dirty = 1, sync_mtime = 1 WHERE id = 1")
        db.exec("DELETE FROM sync_apply")
    }

    /**
     * 清除本机的账号密码、待办、项目和进展记录，恢复到刚安装时的状态（保留应用锁等本机偏好）。
     * 写入期间开启 sync_apply，删除不会产生同步墓碑，云端数据不受影响。
     */
    fun wipeLocalData() {
        db.exec("PRAGMA secure_delete = ON")
        db.tx {
            db.exec("INSERT INTO sync_apply (x) VALUES (1)")
            for (table in listOf("todo_comments", "todos", "entries", "projects", "priorities", "statuses")) db.exec("DELETE FROM $table")
            db.exec(Schema.SEED_PRIORITIES)
            db.exec(Schema.SEED_STATUSES)
            db.exec("UPDATE policy SET allow_export = 1, wipe_on_logout = 0, code = '', sync_rev = 0, sync_dirty = 1, sync_mtime = 1 WHERE id = 1")
            db.exec("DELETE FROM sync_tombstones")
            repo.setSetting("sync.cursor", null)
            repo.setSetting("sync.account", null)
            db.exec("DELETE FROM sync_apply")
        }
        db.exec("PRAGMA secure_delete = OFF")
        // 回收已删除数据所在的页，避免残留在数据库文件或 WAL 中
        db.query("PRAGMA wal_checkpoint(TRUNCATE)")
        db.exec("VACUUM")
    }

    companion object {
        const val MIN_CODE_LENGTH = 6
        private const val MAX_FAILS = 5
        private const val LOCK_MS = 30_000L
    }
}

/** 安全码哈希计算（与桌面端 policy.ts 的 deriveCode 一致） */
object PolicyCode {
    fun derive(code: String, kdf: KdfParams): String {
        if (kdf.n > 1 shl 20 || kdf.r > 32 || kdf.p > 16) throw PolicyException("安全码参数异常")
        val master = Scrypt.derive(Platform.nfkc(code).encodeToByteArray(), Base64.decode(kdf.salt), kdf.n, kdf.r, kdf.p, 32)
        val hash = hkdfSha256(master, ByteArray(0), "devvault/policy-code/v1".encodeToByteArray(), 32)
        master.fill(0)
        return Base64.encode(hash)
    }
}
