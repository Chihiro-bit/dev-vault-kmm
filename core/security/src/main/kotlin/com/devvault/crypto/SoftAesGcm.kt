package com.devvault.crypto

/**
 * AES-256-GCM 的纯 Kotlin 实现（96 位 IV、128 位认证标签）。
 * iOS 端使用（CommonCrypto 未公开 GCM 接口）；Android 端使用系统 javax.crypto，
 * 测试中两者互相校验，保证密文格式一致。
 */
internal object SoftAesGcm {
    private val SBOX = IntArray(256)
    private val RCON = intArrayOf(0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40, 0x80, 0x1b, 0x36)

    init {
        // 由 GF(2^8) 乘法逆元和仿射变换生成 S 盒
        var p = 1
        var q = 1
        do {
            p = p xor (p shl 1) xor (if (p and 0x80 != 0) 0x1b else 0)
            p = p and 0xff
            q = q xor (q shl 1)
            q = q xor (q shl 2)
            q = q xor (q shl 4)
            q = q and 0xff
            if (q and 0x80 != 0) q = q xor 0x09
            val x = q xor rotl8(q, 1) xor rotl8(q, 2) xor rotl8(q, 3) xor rotl8(q, 4)
            SBOX[p] = (x xor 0x63) and 0xff
        } while (p != 1)
        SBOX[0] = 0x63
    }

    private fun rotl8(x: Int, s: Int) = ((x shl s) or (x ushr (8 - s))) and 0xff

    private fun expandKey(key: ByteArray): IntArray {
        require(key.size == 32) { "AES-256 需要 32 字节密钥" }
        val nk = 8
        val rounds = 14
        val w = IntArray(4 * (rounds + 1))
        for (i in 0 until nk) {
            w[i] =
                (key[4 * i].toInt() and 0xff shl 24) or (key[4 * i + 1].toInt() and 0xff shl 16) or
                        (key[4 * i + 2].toInt() and 0xff shl 8) or (key[4 * i + 3].toInt() and 0xff)
        }
        for (i in nk until w.size) {
            var t = w[i - 1]
            if (i % nk == 0) {
                t = subWord(t.rotateLeft(8)) xor (RCON[i / nk - 1] shl 24)
            } else if (i % nk == 4) {
                t = subWord(t)
            }
            w[i] = w[i - nk] xor t
        }
        return w
    }

    private fun subWord(w: Int): Int =
        (SBOX[w ushr 24 and 0xff] shl 24) or (SBOX[w ushr 16 and 0xff] shl 16) or
                (SBOX[w ushr 8 and 0xff] shl 8) or SBOX[w and 0xff]

    private fun xtime(b: Int) = ((b shl 1) xor (if (b and 0x80 != 0) 0x1b else 0)) and 0xff

    private fun encryptBlock(w: IntArray, input: ByteArray): ByteArray {
        val s = IntArray(16) { input[it].toInt() and 0xff }
        addRoundKey(s, w, 0)
        for (round in 1 until 14) {
            subBytes(s); shiftRows(s); mixColumns(s); addRoundKey(s, w, round)
        }
        subBytes(s); shiftRows(s); addRoundKey(s, w, 14)
        return ByteArray(16) { s[it].toByte() }
    }

    private fun addRoundKey(s: IntArray, w: IntArray, round: Int) {
        for (c in 0 until 4) {
            val k = w[round * 4 + c]
            s[c * 4] = s[c * 4] xor (k ushr 24 and 0xff)
            s[c * 4 + 1] = s[c * 4 + 1] xor (k ushr 16 and 0xff)
            s[c * 4 + 2] = s[c * 4 + 2] xor (k ushr 8 and 0xff)
            s[c * 4 + 3] = s[c * 4 + 3] xor (k and 0xff)
        }
    }

    private fun subBytes(s: IntArray) {
        for (i in 0 until 16) s[i] = SBOX[s[i]]
    }

    /** 状态按列存储：s[c*4 + r] */
    private fun shiftRows(s: IntArray) {
        for (r in 1 until 4) {
            val row = IntArray(4) { c -> s[((c + r) % 4) * 4 + r] }
            for (c in 0 until 4) s[c * 4 + r] = row[c]
        }
    }

    private fun mixColumns(s: IntArray) {
        for (c in 0 until 4) {
            val a0 = s[c * 4];
            val a1 = s[c * 4 + 1];
            val a2 = s[c * 4 + 2];
            val a3 = s[c * 4 + 3]
            val t = a0 xor a1 xor a2 xor a3
            s[c * 4] = a0 xor t xor xtime(a0 xor a1)
            s[c * 4 + 1] = a1 xor t xor xtime(a1 xor a2)
            s[c * 4 + 2] = a2 xor t xor xtime(a2 xor a3)
            s[c * 4 + 3] = a3 xor t xor xtime(a3 xor a0)
        }
    }

    // ---------- GCM ----------

    private fun gfMul(x: ByteArray, y: ByteArray): ByteArray {
        val z = ByteArray(16)
        val v = y.copyOf()
        for (i in 0 until 128) {
            if ((x[i / 8].toInt() ushr (7 - i % 8)) and 1 == 1) {
                for (j in 0 until 16) z[j] = (z[j].toInt() xor v[j].toInt()).toByte()
            }
            val lsb = v[15].toInt() and 1
            for (j in 15 downTo 1) v[j] =
                ((v[j].toInt() and 0xff ushr 1) or (v[j - 1].toInt() and 1 shl 7)).toByte()
            v[0] = (v[0].toInt() and 0xff ushr 1).toByte()
            if (lsb == 1) v[0] = (v[0].toInt() xor 0xe1).toByte()
        }
        return z
    }

    private fun ghash(h: ByteArray, aad: ByteArray, ct: ByteArray): ByteArray {
        var y = ByteArray(16)
        fun absorb(data: ByteArray) {
            var i = 0
            while (i < data.size) {
                val n = minOf(16, data.size - i)
                for (j in 0 until n) y[j] = (y[j].toInt() xor data[i + j].toInt()).toByte()
                y = gfMul(y, h)
                i += 16
            }
        }
        absorb(aad)
        absorb(ct)
        val len = ByteArray(16)
        val aBits = aad.size.toLong() * 8
        val cBits = ct.size.toLong() * 8
        for (i in 0 until 8) {
            len[7 - i] = (aBits ushr (8 * i)).toByte()
            len[15 - i] = (cBits ushr (8 * i)).toByte()
        }
        for (j in 0 until 16) y[j] = (y[j].toInt() xor len[j].toInt()).toByte()
        return gfMul(y, h)
    }

    private fun inc32(counter: ByteArray) {
        for (i in 15 downTo 12) {
            counter[i] = (counter[i] + 1).toByte()
            if (counter[i].toInt() != 0) break
        }
    }

    private fun ctr(w: IntArray, j0: ByteArray, input: ByteArray): ByteArray {
        val out = ByteArray(input.size)
        val counter = j0.copyOf()
        var i = 0
        while (i < input.size) {
            inc32(counter)
            val ks = encryptBlock(w, counter)
            val n = minOf(16, input.size - i)
            for (j in 0 until n) out[i + j] = (input[i + j].toInt() xor ks[j].toInt()).toByte()
            i += 16
        }
        return out
    }

    private fun j0(iv: ByteArray): ByteArray {
        require(iv.size == 12) { "IV 必须为 12 字节" }
        return iv + byteArrayOf(0, 0, 0, 1)
    }

    /** 返回 密文 ‖ 16 字节标签 */
    fun seal(key: ByteArray, iv: ByteArray, plain: ByteArray, aad: ByteArray): ByteArray {
        val w = expandKey(key)
        val h = encryptBlock(w, ByteArray(16))
        val j0 = j0(iv)
        val ct = ctr(w, j0, plain)
        val s = ghash(h, aad, ct)
        val ek = encryptBlock(w, j0)
        val tag = ByteArray(16) { (s[it].toInt() xor ek[it].toInt()).toByte() }
        return ct + tag
    }

    fun open(key: ByteArray, iv: ByteArray, sealed: ByteArray, aad: ByteArray): ByteArray {
        if (sealed.size < 16) throw IllegalArgumentException("密文已损坏")
        val w = expandKey(key)
        val h = encryptBlock(w, ByteArray(16))
        val j0 = j0(iv)
        val ct = sealed.copyOfRange(0, sealed.size - 16)
        val tag = sealed.copyOfRange(sealed.size - 16, sealed.size)
        val s = ghash(h, aad, ct)
        val ek = encryptBlock(w, j0)
        var diff = 0
        for (i in 0 until 16) diff = diff or ((s[i].toInt() xor ek[i].toInt()) xor tag[i].toInt())
        if (diff != 0) throw IllegalArgumentException("密文校验失败")
        return ctr(w, j0, ct)
    }
}
