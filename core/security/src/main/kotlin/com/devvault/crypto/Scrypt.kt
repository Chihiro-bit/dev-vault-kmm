package com.devvault.crypto

/**
 * scrypt（RFC 7914）。桌面端默认参数 N=2^17、r=8 需要约 128MB 内存，
 * Android 端已在清单中开启 largeHeap。
 */
object Scrypt {
    fun derive(password: ByteArray, salt: ByteArray, n: Int, r: Int, p: Int, dkLen: Int): ByteArray {
        require(n > 1 && n and (n - 1) == 0) { "N 必须是 2 的幂" }
        val blockInts = 32 * r
        val b = pbkdf2Sha256(password, salt, 1, p * 128 * r)
        val x = IntArray(blockInts)
        val v = IntArray(blockInts * n)
        val y = IntArray(blockInts)
        val tmp = IntArray(16)

        for (i in 0 until p) {
            val off = i * 128 * r
            for (k in 0 until blockInts) x[k] = readLe(b, off + k * 4)
            for (j in 0 until n) {
                x.copyInto(v, j * blockInts)
                blockMix(x, y, tmp, r)
            }
            for (j in 0 until n) {
                val idx = x[(2 * r - 1) * 16] and (n - 1)
                val base = idx * blockInts
                for (k in 0 until blockInts) x[k] = x[k] xor v[base + k]
                blockMix(x, y, tmp, r)
            }
            for (k in 0 until blockInts) writeLe(b, off + k * 4, x[k])
        }
        v.fill(0)
        return pbkdf2Sha256(password, b, 1, dkLen)
    }

    /** 结果写回 b，y 为同长度的临时空间 */
    private fun blockMix(b: IntArray, y: IntArray, x: IntArray, r: Int) {
        b.copyInto(x, 0, (2 * r - 1) * 16, 2 * r * 16)
        for (i in 0 until 2 * r) {
            for (k in 0 until 16) x[k] = x[k] xor b[i * 16 + k]
            salsa8(x)
            // 偶数块放前半，奇数块放后半
            val dst = if (i % 2 == 0) (i / 2) * 16 else (r + i / 2) * 16
            x.copyInto(y, dst)
        }
        y.copyInto(b)
    }

    private fun salsa8(b: IntArray) {
        var x0 = b[0]; var x1 = b[1]; var x2 = b[2]; var x3 = b[3]
        var x4 = b[4]; var x5 = b[5]; var x6 = b[6]; var x7 = b[7]
        var x8 = b[8]; var x9 = b[9]; var x10 = b[10]; var x11 = b[11]
        var x12 = b[12]; var x13 = b[13]; var x14 = b[14]; var x15 = b[15]
        repeat(4) {
            x4 = x4 xor (x0 + x12).rotateLeft(7); x8 = x8 xor (x4 + x0).rotateLeft(9)
            x12 = x12 xor (x8 + x4).rotateLeft(13); x0 = x0 xor (x12 + x8).rotateLeft(18)
            x9 = x9 xor (x5 + x1).rotateLeft(7); x13 = x13 xor (x9 + x5).rotateLeft(9)
            x1 = x1 xor (x13 + x9).rotateLeft(13); x5 = x5 xor (x1 + x13).rotateLeft(18)
            x14 = x14 xor (x10 + x6).rotateLeft(7); x2 = x2 xor (x14 + x10).rotateLeft(9)
            x6 = x6 xor (x2 + x14).rotateLeft(13); x10 = x10 xor (x6 + x2).rotateLeft(18)
            x3 = x3 xor (x15 + x11).rotateLeft(7); x7 = x7 xor (x3 + x15).rotateLeft(9)
            x11 = x11 xor (x7 + x3).rotateLeft(13); x15 = x15 xor (x11 + x7).rotateLeft(18)
            x1 = x1 xor (x0 + x3).rotateLeft(7); x2 = x2 xor (x1 + x0).rotateLeft(9)
            x3 = x3 xor (x2 + x1).rotateLeft(13); x0 = x0 xor (x3 + x2).rotateLeft(18)
            x6 = x6 xor (x5 + x4).rotateLeft(7); x7 = x7 xor (x6 + x5).rotateLeft(9)
            x4 = x4 xor (x7 + x6).rotateLeft(13); x5 = x5 xor (x4 + x7).rotateLeft(18)
            x11 = x11 xor (x10 + x9).rotateLeft(7); x8 = x8 xor (x11 + x10).rotateLeft(9)
            x9 = x9 xor (x8 + x11).rotateLeft(13); x10 = x10 xor (x9 + x8).rotateLeft(18)
            x12 = x12 xor (x15 + x14).rotateLeft(7); x13 = x13 xor (x12 + x15).rotateLeft(9)
            x14 = x14 xor (x13 + x12).rotateLeft(13); x15 = x15 xor (x14 + x13).rotateLeft(18)
        }
        b[0] += x0; b[1] += x1; b[2] += x2; b[3] += x3
        b[4] += x4; b[5] += x5; b[6] += x6; b[7] += x7
        b[8] += x8; b[9] += x9; b[10] += x10; b[11] += x11
        b[12] += x12; b[13] += x13; b[14] += x14; b[15] += x15
    }

    private fun readLe(a: ByteArray, o: Int): Int =
        (a[o].toInt() and 0xff) or (a[o + 1].toInt() and 0xff shl 8) or
            (a[o + 2].toInt() and 0xff shl 16) or (a[o + 3].toInt() and 0xff shl 24)

    private fun writeLe(a: ByteArray, o: Int, v: Int) {
        a[o] = v.toByte(); a[o + 1] = (v ushr 8).toByte(); a[o + 2] = (v ushr 16).toByte(); a[o + 3] = (v ushr 24).toByte()
    }
}
