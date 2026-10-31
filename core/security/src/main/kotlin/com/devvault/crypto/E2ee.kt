package com.devvault.crypto

import com.devvault.platform.Platform
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64

/**
 * 端到端加密，与桌面端 src/main/e2ee.ts 完全兼容（同一账号可在电脑与手机间同步）。
 *
 *   主密码 ──scrypt(salt)──► masterKey ─┬─HKDF("devvault/auth/v1")─► authKey  提交给服务端
 *                                       └─HKDF("devvault/wrap/v1")─► wrapKey  在本机包裹 / 解开 vaultKey
 *   每条同步记录用 vaultKey 做 AES-256-GCM 加密，AAD 绑定记录类型与 id。
 *   密文格式："v1." + base64(iv[12] ‖ ciphertext ‖ tag[16])
 */
@Serializable
data class KdfParams(val type: String, val n: Int, val r: Int, val p: Int, val salt: String)

class DerivedKeys(val authKey: String, val wrapKey: ByteArray)

object E2ee {
    private const val VERSION = "v1."
    private const val VAULT_KEY_AAD = "devvault/vault-key"

    fun newKdfParams(): KdfParams =
        KdfParams(type = "scrypt", n = 1 shl 17, r = 8, p = 1, salt = Base64.encode(Platform.randomBytes(16)))

    fun deriveKeys(password: String, kdf: KdfParams): DerivedKeys {
        if (kdf.type != "scrypt") throw IllegalStateException("不支持的密钥派生算法：${kdf.type}")
        // 客户端设上限，防止被恶意服务器用超大参数拖垮
        if (kdf.n > 1 shl 20 || kdf.r > 32 || kdf.p > 16) throw IllegalStateException("服务器返回的密钥参数异常")
        val master = Scrypt.derive(
            Platform.nfkc(password).encodeToByteArray(),
            Base64.decode(kdf.salt),
            kdf.n, kdf.r, kdf.p, 32,
        )
        val auth = hkdfSha256(master, ByteArray(0), "devvault/auth/v1".encodeToByteArray(), 32)
        val wrap = hkdfSha256(master, ByteArray(0), "devvault/wrap/v1".encodeToByteArray(), 32)
        master.fill(0)
        return DerivedKeys(Base64.encode(auth), wrap)
    }

    fun seal(key: ByteArray, plain: ByteArray, aad: String, prefix: String = VERSION): String {
        val iv = Platform.randomBytes(12)
        val body = Platform.aesGcmSeal(key, iv, plain, aad.encodeToByteArray())
        return prefix + Base64.encode(iv + body)
    }

    fun open(key: ByteArray, sealed: String, aad: String, prefix: String = VERSION): ByteArray {
        if (!sealed.startsWith(prefix)) throw IllegalArgumentException("不支持的密文版本")
        val raw = Base64.decode(sealed.substring(prefix.length))
        if (raw.size < 28) throw IllegalArgumentException("密文已损坏")
        return Platform.aesGcmOpen(key, raw.copyOfRange(0, 12), raw.copyOfRange(12, raw.size), aad.encodeToByteArray())
    }

    fun newVaultKey(): ByteArray = Platform.randomBytes(32)

    fun wrapVaultKey(vaultKey: ByteArray, wrapKey: ByteArray): String = seal(wrapKey, vaultKey, VAULT_KEY_AAD)

    fun unwrapVaultKey(wrapped: String, wrapKey: ByteArray): ByteArray {
        val key = try {
            open(wrapKey, wrapped, VAULT_KEY_AAD)
        } catch (e: Exception) {
            null
        }
        if (key == null || key.size != 32) throw IllegalStateException("无法解开数据密钥，请确认密码正确")
        return key
    }

    private fun recordAad(kind: String, id: String) = "devvault/record/$kind/$id"

    fun encryptRecord(vaultKey: ByteArray, kind: String, id: String, json: String): String =
        seal(vaultKey, json.encodeToByteArray(), recordAad(kind, id))

    fun decryptRecord(vaultKey: ByteArray, kind: String, id: String, payload: String): String =
        open(vaultKey, payload, recordAad(kind, id)).decodeToString()
}

/**
 * 本机字段加密（相当于桌面端的 DPAPI / safeStorage）：密码、动态码密钥、敏感字段、登录令牌。
 * 空字符串保持为空，便于判断「是否填写」。
 */
object LocalCipher {
    private const val PREFIX = "m1."
    private const val AAD = "devvault/local"

    fun encrypt(plain: String?): String {
        if (plain.isNullOrEmpty()) return ""
        return E2ee.seal(Platform.deviceKey(), plain.encodeToByteArray(), AAD, PREFIX)
    }

    fun decrypt(stored: String?): String {
        if (stored.isNullOrEmpty()) return ""
        if (!stored.startsWith(PREFIX)) return stored
        return try {
            E2ee.open(Platform.deviceKey(), stored, AAD, PREFIX).decodeToString()
        } catch (e: Exception) {
            ""
        }
    }
}
