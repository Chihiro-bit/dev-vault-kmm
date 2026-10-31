package com.devvault.crypto

/**
 * SHA-256 / HMAC / PBKDF2 / HKDF 的纯 Kotlin 实现，Android 与 iOS 共用，
 * 输出与桌面端 Node.js crypto 完全一致（见 commonTest 中的测试向量）。
 */
object Sha256 {
    private val K = longArrayOf(
        0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
        0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
        0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
        0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
    ).map { it.toInt() }.toIntArray()

    fun digest(data: ByteArray): ByteArray {
        val h = longArrayOf(
            0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19,
        ).map { it.toInt() }.toIntArray()
        val bitLen = data.size.toLong() * 8
        val padLen = ((data.size + 9 + 63) / 64) * 64
        val msg = ByteArray(padLen)
        data.copyInto(msg)
        msg[data.size] = 0x80.toByte()
        for (i in 0 until 8) msg[padLen - 1 - i] = (bitLen ushr (8 * i)).toByte()

        val w = IntArray(64)
        for (chunk in 0 until padLen / 64) {
            val off = chunk * 64
            for (i in 0 until 16) {
                w[i] = (msg[off + i * 4].toInt() and 0xff shl 24) or
                    (msg[off + i * 4 + 1].toInt() and 0xff shl 16) or
                    (msg[off + i * 4 + 2].toInt() and 0xff shl 8) or
                    (msg[off + i * 4 + 3].toInt() and 0xff)
            }
            for (i in 16 until 64) {
                val s0 = w[i - 15].rotateRight(7) xor w[i - 15].rotateRight(18) xor (w[i - 15] ushr 3)
                val s1 = w[i - 2].rotateRight(17) xor w[i - 2].rotateRight(19) xor (w[i - 2] ushr 10)
                w[i] = w[i - 16] + s0 + w[i - 7] + s1
            }
            var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]
            var e = h[4]; var f = h[5]; var g = h[6]; var hh = h[7]
            for (i in 0 until 64) {
                val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
                val ch = (e and f) xor (e.inv() and g)
                val t1 = hh + s1 + ch + K[i] + w[i]
                val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
                val maj = (a and b) xor (a and c) xor (b and c)
                val t2 = s0 + maj
                hh = g; g = f; f = e; e = d + t1
                d = c; c = b; b = a; a = t1 + t2
            }
            h[0] += a; h[1] += b; h[2] += c; h[3] += d
            h[4] += e; h[5] += f; h[6] += g; h[7] += hh
        }
        val out = ByteArray(32)
        for (i in 0 until 8) {
            out[i * 4] = (h[i] ushr 24).toByte()
            out[i * 4 + 1] = (h[i] ushr 16).toByte()
            out[i * 4 + 2] = (h[i] ushr 8).toByte()
            out[i * 4 + 3] = h[i].toByte()
        }
        return out
    }
}

fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
    val block = 64
    val k = if (key.size > block) Sha256.digest(key) else key
    val ipad = ByteArray(block) { i -> ((if (i < k.size) k[i].toInt() else 0) xor 0x36).toByte() }
    val opad = ByteArray(block) { i -> ((if (i < k.size) k[i].toInt() else 0) xor 0x5c).toByte() }
    return Sha256.digest(opad + Sha256.digest(ipad + data))
}

/** 通用 HMAC 接口，用于 SHA-1 / SHA-256 / SHA-512 的 TOTP */
fun hmac(algorithm: HashAlgorithm, key: ByteArray, data: ByteArray): ByteArray = when (algorithm) {
    HashAlgorithm.SHA256 -> hmacSha256(key, data)
    HashAlgorithm.SHA1 -> genericHmac(64, Sha1::digest, key, data)
    HashAlgorithm.SHA512 -> genericHmac(128, Sha512::digest, key, data)
}

enum class HashAlgorithm { SHA1, SHA256, SHA512 }

private fun genericHmac(block: Int, h: (ByteArray) -> ByteArray, key: ByteArray, data: ByteArray): ByteArray {
    val k = if (key.size > block) h(key) else key
    val ipad = ByteArray(block) { i -> ((if (i < k.size) k[i].toInt() else 0) xor 0x36).toByte() }
    val opad = ByteArray(block) { i -> ((if (i < k.size) k[i].toInt() else 0) xor 0x5c).toByte() }
    return h(opad + h(ipad + data))
}

fun pbkdf2Sha256(password: ByteArray, salt: ByteArray, iterations: Int, dkLen: Int): ByteArray {
    val out = ByteArray(dkLen)
    var block = 1
    var pos = 0
    while (pos < dkLen) {
        val idx = byteArrayOf((block ushr 24).toByte(), (block ushr 16).toByte(), (block ushr 8).toByte(), block.toByte())
        var u = hmacSha256(password, salt + idx)
        val t = u.copyOf()
        for (i in 1 until iterations) {
            u = hmacSha256(password, u)
            for (j in t.indices) t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
        }
        val n = minOf(32, dkLen - pos)
        t.copyInto(out, pos, 0, n)
        pos += n
        block++
    }
    return out
}

/** HKDF-SHA256（RFC 5869），salt 为空时按规范使用 32 个 0 字节，与 Node hkdfSync 一致 */
fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
    val prk = hmacSha256(if (salt.isEmpty()) ByteArray(32) else salt, ikm)
    val out = ByteArray(length)
    var prev = ByteArray(0)
    var pos = 0
    var counter = 1
    while (pos < length) {
        prev = hmacSha256(prk, prev + info + byteArrayOf(counter.toByte()))
        val n = minOf(prev.size, length - pos)
        prev.copyInto(out, pos, 0, n)
        pos += n
        counter++
    }
    return out
}

internal object Sha1 {
    fun digest(data: ByteArray): ByteArray {
        var h0 = 0x67452301; var h1 = 0xEFCDAB89L.toInt(); var h2 = 0x98BADCFEL.toInt(); var h3 = 0x10325476; var h4 = 0xC3D2E1F0L.toInt()
        val bitLen = data.size.toLong() * 8
        val padLen = ((data.size + 9 + 63) / 64) * 64
        val msg = ByteArray(padLen)
        data.copyInto(msg)
        msg[data.size] = 0x80.toByte()
        for (i in 0 until 8) msg[padLen - 1 - i] = (bitLen ushr (8 * i)).toByte()
        val w = IntArray(80)
        for (chunk in 0 until padLen / 64) {
            val off = chunk * 64
            for (i in 0 until 16) {
                w[i] = (msg[off + i * 4].toInt() and 0xff shl 24) or (msg[off + i * 4 + 1].toInt() and 0xff shl 16) or
                    (msg[off + i * 4 + 2].toInt() and 0xff shl 8) or (msg[off + i * 4 + 3].toInt() and 0xff)
            }
            for (i in 16 until 80) w[i] = (w[i - 3] xor w[i - 8] xor w[i - 14] xor w[i - 16]).rotateLeft(1)
            var a = h0; var b = h1; var c = h2; var d = h3; var e = h4
            for (i in 0 until 80) {
                val (f, k) = when {
                    i < 20 -> ((b and c) or (b.inv() and d)) to 0x5A827999
                    i < 40 -> (b xor c xor d) to 0x6ED9EBA1
                    i < 60 -> ((b and c) or (b and d) or (c and d)) to 0x8F1BBCDCL.toInt()
                    else -> (b xor c xor d) to 0xCA62C1D6L.toInt()
                }
                val t = a.rotateLeft(5) + f + e + k + w[i]
                e = d; d = c; c = b.rotateLeft(30); b = a; a = t
            }
            h0 += a; h1 += b; h2 += c; h3 += d; h4 += e
        }
        val out = ByteArray(20)
        intArrayOf(h0, h1, h2, h3, h4).forEachIndexed { i, v ->
            out[i * 4] = (v ushr 24).toByte(); out[i * 4 + 1] = (v ushr 16).toByte()
            out[i * 4 + 2] = (v ushr 8).toByte(); out[i * 4 + 3] = v.toByte()
        }
        return out
    }
}

internal object Sha512 {
    private val K = longArrayOf(
        0x428a2f98d728ae22UL.toLong(), 0x7137449123ef65cdUL.toLong(), 0xb5c0fbcfec4d3b2fUL.toLong(), 0xe9b5dba58189dbbcUL.toLong(),
        0x3956c25bf348b538UL.toLong(), 0x59f111f1b605d019UL.toLong(), 0x923f82a4af194f9bUL.toLong(), 0xab1c5ed5da6d8118UL.toLong(),
        0xd807aa98a3030242UL.toLong(), 0x12835b0145706fbeUL.toLong(), 0x243185be4ee4b28cUL.toLong(), 0x550c7dc3d5ffb4e2UL.toLong(),
        0x72be5d74f27b896fUL.toLong(), 0x80deb1fe3b1696b1UL.toLong(), 0x9bdc06a725c71235UL.toLong(), 0xc19bf174cf692694UL.toLong(),
        0xe49b69c19ef14ad2UL.toLong(), 0xefbe4786384f25e3UL.toLong(), 0x0fc19dc68b8cd5b5UL.toLong(), 0x240ca1cc77ac9c65UL.toLong(),
        0x2de92c6f592b0275UL.toLong(), 0x4a7484aa6ea6e483UL.toLong(), 0x5cb0a9dcbd41fbd4UL.toLong(), 0x76f988da831153b5UL.toLong(),
        0x983e5152ee66dfabUL.toLong(), 0xa831c66d2db43210UL.toLong(), 0xb00327c898fb213fUL.toLong(), 0xbf597fc7beef0ee4UL.toLong(),
        0xc6e00bf33da88fc2UL.toLong(), 0xd5a79147930aa725UL.toLong(), 0x06ca6351e003826fUL.toLong(), 0x142929670a0e6e70UL.toLong(),
        0x27b70a8546d22ffcUL.toLong(), 0x2e1b21385c26c926UL.toLong(), 0x4d2c6dfc5ac42aedUL.toLong(), 0x53380d139d95b3dfUL.toLong(),
        0x650a73548baf63deUL.toLong(), 0x766a0abb3c77b2a8UL.toLong(), 0x81c2c92e47edaee6UL.toLong(), 0x92722c851482353bUL.toLong(),
        0xa2bfe8a14cf10364UL.toLong(), 0xa81a664bbc423001UL.toLong(), 0xc24b8b70d0f89791UL.toLong(), 0xc76c51a30654be30UL.toLong(),
        0xd192e819d6ef5218UL.toLong(), 0xd69906245565a910UL.toLong(), 0xf40e35855771202aUL.toLong(), 0x106aa07032bbd1b8UL.toLong(),
        0x19a4c116b8d2d0c8UL.toLong(), 0x1e376c085141ab53UL.toLong(), 0x2748774cdf8eeb99UL.toLong(), 0x34b0bcb5e19b48a8UL.toLong(),
        0x391c0cb3c5c95a63UL.toLong(), 0x4ed8aa4ae3418acbUL.toLong(), 0x5b9cca4f7763e373UL.toLong(), 0x682e6ff3d6b2b8a3UL.toLong(),
        0x748f82ee5defb2fcUL.toLong(), 0x78a5636f43172f60UL.toLong(), 0x84c87814a1f0ab72UL.toLong(), 0x8cc702081a6439ecUL.toLong(),
        0x90befffa23631e28UL.toLong(), 0xa4506cebde82bde9UL.toLong(), 0xbef9a3f7b2c67915UL.toLong(), 0xc67178f2e372532bUL.toLong(),
        0xca273eceea26619cUL.toLong(), 0xd186b8c721c0c207UL.toLong(), 0xeada7dd6cde0eb1eUL.toLong(), 0xf57d4f7fee6ed178UL.toLong(),
        0x06f067aa72176fbaUL.toLong(), 0x0a637dc5a2c898a6UL.toLong(), 0x113f9804bef90daeUL.toLong(), 0x1b710b35131c471bUL.toLong(),
        0x28db77f523047d84UL.toLong(), 0x32caab7b40c72493UL.toLong(), 0x3c9ebe0a15c9bebcUL.toLong(), 0x431d67c49c100d4cUL.toLong(),
        0x4cc5d4becb3e42b6UL.toLong(), 0x597f299cfc657e2aUL.toLong(), 0x5fcb6fab3ad6faecUL.toLong(), 0x6c44198c4a475817UL.toLong(),
    )

    fun digest(data: ByteArray): ByteArray {
        val h = longArrayOf(
            0x6a09e667f3bcc908UL.toLong(), 0xbb67ae8584caa73bUL.toLong(), 0x3c6ef372fe94f82bUL.toLong(), 0xa54ff53a5f1d36f1UL.toLong(),
            0x510e527fade682d1UL.toLong(), 0x9b05688c2b3e6c1fUL.toLong(), 0x1f83d9abfb41bd6bUL.toLong(), 0x5be0cd19137e2179UL.toLong(),
        )
        val bitLen = data.size.toLong() * 8
        val padLen = ((data.size + 17 + 127) / 128) * 128
        val msg = ByteArray(padLen)
        data.copyInto(msg)
        msg[data.size] = 0x80.toByte()
        for (i in 0 until 8) msg[padLen - 1 - i] = (bitLen ushr (8 * i)).toByte()
        val w = LongArray(80)
        for (chunk in 0 until padLen / 128) {
            val off = chunk * 128
            for (i in 0 until 16) {
                var v = 0L
                for (j in 0 until 8) v = (v shl 8) or (msg[off + i * 8 + j].toLong() and 0xff)
                w[i] = v
            }
            for (i in 16 until 80) {
                val s0 = w[i - 15].rotateRight(1) xor w[i - 15].rotateRight(8) xor (w[i - 15] ushr 7)
                val s1 = w[i - 2].rotateRight(19) xor w[i - 2].rotateRight(61) xor (w[i - 2] ushr 6)
                w[i] = w[i - 16] + s0 + w[i - 7] + s1
            }
            var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]
            var e = h[4]; var f = h[5]; var g = h[6]; var hh = h[7]
            for (i in 0 until 80) {
                val s1 = e.rotateRight(14) xor e.rotateRight(18) xor e.rotateRight(41)
                val ch = (e and f) xor (e.inv() and g)
                val t1 = hh + s1 + ch + K[i] + w[i]
                val s0 = a.rotateRight(28) xor a.rotateRight(34) xor a.rotateRight(39)
                val maj = (a and b) xor (a and c) xor (b and c)
                val t2 = s0 + maj
                hh = g; g = f; f = e; e = d + t1
                d = c; c = b; b = a; a = t1 + t2
            }
            h[0] += a; h[1] += b; h[2] += c; h[3] += d
            h[4] += e; h[5] += f; h[6] += g; h[7] += hh
        }
        val out = ByteArray(64)
        for (i in 0 until 8) for (j in 0 until 8) out[i * 8 + j] = (h[i] ushr (56 - 8 * j)).toByte()
        return out
    }
}
