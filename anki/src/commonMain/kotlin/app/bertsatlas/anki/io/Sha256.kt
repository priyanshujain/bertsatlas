package app.bertsatlas.anki.io

class Sha256 {
    private val state = INITIAL_STATE.copyOf()
    private val block = ByteArray(64)
    private val words = IntArray(64)
    private var blockLength = 0
    private var totalBytes = 0L

    fun update(input: ByteArray, offset: Int = 0, length: Int = input.size - offset): Sha256 {
        for (index in offset until offset + length) {
            block[blockLength++] = input[index]
            if (blockLength == 64) {
                compressBlock()
                blockLength = 0
            }
        }
        totalBytes += length
        return this
    }

    fun digest(): ByteArray {
        val bitLength = totalBytes * 8
        block[blockLength++] = 0x80.toByte()
        if (blockLength > 56) {
            block.fill(0, blockLength, 64)
            compressBlock()
            blockLength = 0
        }
        block.fill(0, blockLength, 56)
        for (i in 0 until 8) block[63 - i] = (bitLength ushr (8 * i)).toByte()
        compressBlock()
        val output = ByteArray(32)
        for (i in 0 until 8) {
            output[i * 4] = (state[i] ushr 24).toByte()
            output[i * 4 + 1] = (state[i] ushr 16).toByte()
            output[i * 4 + 2] = (state[i] ushr 8).toByte()
            output[i * 4 + 3] = state[i].toByte()
        }
        return output
    }

    fun hexDigest(): String = digest().joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    private fun compressBlock() {
        for (i in 0 until 16) {
            words[i] = ((block[i * 4].toInt() and 0xFF) shl 24) or
                ((block[i * 4 + 1].toInt() and 0xFF) shl 16) or
                ((block[i * 4 + 2].toInt() and 0xFF) shl 8) or
                (block[i * 4 + 3].toInt() and 0xFF)
        }
        for (i in 16 until 64) {
            val s0 = words[i - 15].rotateRight(7) xor words[i - 15].rotateRight(18) xor (words[i - 15] ushr 3)
            val s1 = words[i - 2].rotateRight(17) xor words[i - 2].rotateRight(19) xor (words[i - 2] ushr 10)
            words[i] = words[i - 16] + s0 + words[i - 7] + s1
        }
        var a = state[0]
        var b = state[1]
        var c = state[2]
        var d = state[3]
        var e = state[4]
        var f = state[5]
        var g = state[6]
        var h = state[7]
        for (i in 0 until 64) {
            val sum1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
            val choice = (e and f) xor (e.inv() and g)
            val temp1 = h + sum1 + choice + ROUND_CONSTANTS[i] + words[i]
            val sum0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
            val majority = (a and b) xor (a and c) xor (b and c)
            val temp2 = sum0 + majority
            h = g
            g = f
            f = e
            e = d + temp1
            d = c
            c = b
            b = a
            a = temp1 + temp2
        }
        state[0] += a
        state[1] += b
        state[2] += c
        state[3] += d
        state[4] += e
        state[5] += f
        state[6] += g
        state[7] += h
    }

    companion object {
        fun hex(input: ByteArray): String = Sha256().update(input).hexDigest()

        fun hexOfFile(path: String): String {
            val digest = Sha256()
            val buffer = ByteArray(256 * 1024)
            RandomAccessFile(path).use { file ->
                var position = 0L
                val size = file.size
                while (position < size) {
                    val length = minOf(buffer.size.toLong(), size - position).toInt()
                    file.readFully(position, buffer, 0, length)
                    digest.update(buffer, 0, length)
                    position += length
                }
            }
            return digest.hexDigest()
        }

        private val INITIAL_STATE = intArrayOf(
            0x6a09e667, 0xbb67ae85.toInt(), 0x3c6ef372, 0xa54ff53a.toInt(),
            0x510e527f, 0x9b05688c.toInt(), 0x1f83d9ab, 0x5be0cd19,
        )

        private val ROUND_CONSTANTS = intArrayOf(
            0x428a2f98, 0x71374491, 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(), 0x3956c25b, 0x59f111f1, 0x923f82a4.toInt(), 0xab1c5ed5.toInt(),
            0xd807aa98.toInt(), 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
            0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
            0x983e5152.toInt(), 0xa831c66d.toInt(), 0xb00327c8.toInt(), 0xbf597fc7.toInt(), 0xc6e00bf3.toInt(), 0xd5a79147.toInt(), 0x06ca6351, 0x14292967,
            0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e.toInt(), 0x92722c85.toInt(),
            0xa2bfe8a1.toInt(), 0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(), 0xd192e819.toInt(), 0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070,
            0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
            0x748f82ee, 0x78a5636f, 0x84c87814.toInt(), 0x8cc70208.toInt(), 0x90befffa.toInt(), 0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt(),
        )
    }
}
