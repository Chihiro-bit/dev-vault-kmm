package com.devvault

import com.devvault.crypto.E2ee
import com.devvault.crypto.HashAlgorithm
import com.devvault.crypto.KdfParams
import com.devvault.crypto.Scrypt
import com.devvault.crypto.Sha256
import com.devvault.crypto.SoftAesGcm
import com.devvault.crypto.hmac
import com.devvault.platform.Platform
import com.devvault.totp.Totp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 测试向量由桌面端同款 Node.js crypto 生成，保证手机端与桌面端可互相解密 */
class CryptoCompatTest {
    private fun ByteArray.hex() = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    private fun String.unhex() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun sha256() = assertEquals(
        "97ca5175ac60b081b919120849da7d71fb00456ac6c32ebe96581ce34cfe2596",
        Sha256.digest("abc".repeat(50).encodeToByteArray()).hex(),
    )

    @Test
    fun hmacVariants() {
        assertEquals(
            "75a48a19d4cbe100644e8ac1397eea747a2d33ab",
            hmac(HashAlgorithm.SHA1, "12345678901234567890".encodeToByteArray(), "0000000000000001".unhex()).hex(),
        )
        assertEquals(
            "854e242c54ce5f31621a1f809881b3439c0a77fd594dc546b9b3a957cf6f7b5475a1302be9fde1ece18531982eaa2141907c8b2fbd8bec2e5b19b8312ace3f2b",
            hmac(HashAlgorithm.SHA512, "k".repeat(200).encodeToByteArray(), "data".encodeToByteArray()).hex(),
        )
    }

    @Test
    fun scryptRfc7914() = assertEquals(
        "fdbabe1c9d3472007856e7190d01e9fe7c6ad7cbc8237830e77376634b3731622eaf30d92e22a3886ff109279d9830dac727afb94a83ee6d8360cbdfa2cc0640",
        Scrypt.derive("password".encodeToByteArray(), "NaCl".encodeToByteArray(), 1024, 8, 16, 64).hex(),
    )

    @Test
    fun deriveKeysMatchesDesktop() {
        val keys = E2ee.deriveKeys(
            "Pässwörd ＡＢＣ 测试",
            KdfParams("scrypt", 1024, 8, 1, "AAECAwQFBgcICQoLDA0ODw=="),
        )
        assertEquals("/GVhIlOPBzyl59kuAi/B0TTBEEJ5D+91GtuonmjkT44=", keys.authKey)
        assertEquals("db328ebf8785f9b27ce0ce4db604a84af5a899b9ec74251eae57d6bd8080c949", keys.wrapKey.hex())
    }

    @Test
    fun opensDesktopCiphertext() {
        val wrapKey = "db328ebf8785f9b27ce0ce4db604a84af5a899b9ec74251eae57d6bd8080c949".unhex()
        val vault = E2ee.unwrapVaultKey(
            "v1.zjpmp0bZxhCS7VvYulKqTEsm4eo8viWwfFS6ztphMZWpCszoKndCKeJngOkFEhKjiSZoLBO10F2612+x",
            wrapKey,
        )
        assertEquals("aa".repeat(32), vault.hex())
        val json = E2ee.decryptRecord(
            vault, "entry", "abc123",
            "v1.u1tzkaTmlhVaB2Oa0wQq19TEK1r3qigDrCaD4JsV+u4CpI2OTVrhhF0Euj5yziTcmehk63H6bEE5EadxDPoD9hBuJTCtXkrGCQxhFPYb+c9ATKaAKgEZWxD6T6Kk2VKiujZ09NVAD8bYs56GAyEwkZEEeXM=",
        )
        assertEquals("""{"title":"公司 VPN","password":"p@ss","extra":[{"key":"k","value":"v","secret":true}]}""", json)
        // AAD 绑定记录 id：挪到其他记录上必须解密失败
        assertFailsWith<Exception> {
            E2ee.decryptRecord(vault, "entry", "other", "v1.u1tzkaTmlhVaB2Oa0wQq19TEK1r3qigDrCaD4JsV+u4CpI2OTVrhhF0Euj5yziTcmehk63H6bEE5EadxDPoD9hBuJTCtXkrGCQxhFPYb+c9ATKaAKgEZWxD6T6Kk2VKiujZ09NVAD8bYs56GAyEwkZEEeXM=")
        }
    }

    @Test
    fun softAesGcmMatchesPlatform() {
        for (len in listOf(0, 1, 15, 16, 17, 100, 1000)) {
            val key = Platform.randomBytes(32)
            val iv = Platform.randomBytes(12)
            val plain = Platform.randomBytes(len)
            val aad = Platform.randomBytes(len % 37)
            val soft = SoftAesGcm.seal(key, iv, plain, aad)
            assertEquals(Platform.aesGcmSeal(key, iv, plain, aad).hex(), soft.hex(), "len=$len")
            assertEquals(plain.hex(), SoftAesGcm.open(key, iv, soft, aad).hex())
            soft[0] = (soft[0] + 1).toByte()
            if (len > 0) assertFailsWith<Exception> { SoftAesGcm.open(key, iv, soft, aad) }
        }
    }

    @Test
    fun totpRfc6238() {
        // RFC 6238 附录 B，SHA-1，密钥 "12345678901234567890" 的 base32
        val cfg = Totp.parse("otpauth://totp/x?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&digits=8")!!
        assertEquals("94287082", Totp.code(cfg, 59_000L))
        assertEquals("07081804", Totp.code(cfg, 1111111109_000L))
        assertEquals("65353130", Totp.code(cfg, 20000000000_000L))
    }
}
