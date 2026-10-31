package com.devvault.totp

import com.devvault.crypto.HashAlgorithm
import com.devvault.crypto.hmac

/** TOTP（RFC 6238），支持 base32 密钥或 otpauth://totp/... 链接，与桌面端 totp.ts 行为一致 */
data class TotpConfig(
    val secret: ByteArray,
    val digits: Int,
    val period: Int,
    val algorithm: HashAlgorithm,
    val issuer: String? = null,
    val account: String? = null,
)

object Totp {
    private const val B32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun base32Decode(input: String): ByteArray? {
        val s = input.replace(Regex("[\\s-]"), "").trimEnd('=').uppercase()
        if (s.isEmpty() || s.any { it !in B32 }) return null
        val out = ArrayList<Byte>()
        var bits = 0
        var value = 0
        for (ch in s) {
            value = (value shl 5) or B32.indexOf(ch)
            bits += 5
            if (bits >= 8) {
                out.add(((value ushr (bits - 8)) and 0xff).toByte())
                bits -= 8
            }
        }
        return out.toByteArray()
    }

    fun parse(input: String): TotpConfig? {
        val s = input.trim()
        if (s.isEmpty()) return null
        var secret = s
        var digits = 6
        var period = 30
        var algorithm = HashAlgorithm.SHA1
        var issuer: String? = null
        var account: String? = null

        if (s.startsWith("otpauth:", ignoreCase = true)) {
            val m = Regex("^otpauth://([^/?#]+)/?([^?#]*)\\??([^#]*)", RegexOption.IGNORE_CASE).find(s) ?: return null
            if (m.groupValues[1].lowercase() != "totp") return null
            val params = m.groupValues[3].split('&').filter { it.isNotEmpty() }.associate {
                val i = it.indexOf('=')
                if (i < 0) it.lowercase() to "" else it.substring(0, i).lowercase() to urlDecode(it.substring(i + 1))
            }
            secret = params["secret"] ?: ""
            digits = params["digits"]?.toIntOrNull()?.takeIf { it != 0 } ?: 6
            period = params["period"]?.toIntOrNull()?.takeIf { it != 0 } ?: 30
            algorithm = when ((params["algorithm"] ?: "SHA1").uppercase()) {
                "SHA256" -> HashAlgorithm.SHA256
                "SHA512" -> HashAlgorithm.SHA512
                else -> HashAlgorithm.SHA1
            }
            val label = urlDecode(m.groupValues[2])
            val parts = if (':' in label) label.split(':', limit = 2) else listOf(null, label)
            issuer = params["issuer"] ?: parts[0]
            account = parts[1]?.takeIf { it.isNotEmpty() }
        }

        val bytes = base32Decode(secret) ?: return null
        if (bytes.size < 5 || digits < 6 || digits > 8 || period < 5) return null
        return TotpConfig(bytes, digits, period, algorithm, issuer, account)
    }

    fun code(cfg: TotpConfig, nowMs: Long): String {
        val counter = nowMs / 1000 / cfg.period
        val msg = ByteArray(8) { i -> (counter ushr (8 * (7 - i))).toByte() }
        val sig = hmac(cfg.algorithm, cfg.secret, msg)
        val o = sig[sig.size - 1].toInt() and 0x0f
        val bin = ((sig[o].toInt() and 0x7f) shl 24) or ((sig[o + 1].toInt() and 0xff) shl 16) or
            ((sig[o + 2].toInt() and 0xff) shl 8) or (sig[o + 3].toInt() and 0xff)
        var mod = 1
        repeat(cfg.digits) { mod *= 10 }
        return (bin % mod).toString().padStart(cfg.digits, '0')
    }

    /** 当前周期剩余秒数 */
    fun remaining(cfg: TotpConfig, nowMs: Long): Int = cfg.period - ((nowMs / 1000) % cfg.period).toInt()

    /** 6 位显示为 "084 959"，8 位显示为 "1234 5678" */
    fun format(code: String): String = if (code.length >= 6) {
        val half = code.length / 2
        code.substring(0, half) + " " + code.substring(half)
    } else code

    /** 百分号编码按 UTF-8 字节序列解码，其余字符原样保留 */
    private fun urlDecode(s: String): String {
        val out = StringBuilder()
        val pending = ArrayList<Byte>()
        fun flush() {
            if (pending.isNotEmpty()) out.append(pending.toByteArray().decodeToString())
            pending.clear()
        }
        var i = 0
        while (i < s.length) {
            val v = if (s[i] == '%' && i + 2 < s.length) s.substring(i + 1, i + 3).toIntOrNull(16) else null
            if (v != null) {
                pending.add(v.toByte())
                i += 3
            } else {
                flush()
                out.append(if (s[i] == '+') ' ' else s[i])
                i++
            }
        }
        flush()
        return out.toString()
    }
}
