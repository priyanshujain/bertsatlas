package app.bertsatlas.anki.io

object Sha1 {
    fun digest(input: ByteArray): ByteArray {
        var h0 = 0x67452301
        var h1 = 0xEFCDAB89.toInt()
        var h2 = 0x98BADCFE.toInt()
        var h3 = 0x10325476
        var h4 = 0xC3D2E1F0.toInt()
        val bitLength = input.size.toLong() * 8
        val paddedSize = ((input.size + 8) / 64 + 1) * 64
        val message = input.copyOf(paddedSize)
        message[input.size] = 0x80.toByte()
        for (i in 0 until 8) message[paddedSize - 1 - i] = (bitLength ushr (8 * i)).toByte()
        val words = IntArray(80)
        for (chunk in 0 until paddedSize step 64) {
            for (i in 0 until 16) {
                val offset = chunk + i * 4
                words[i] = ((message[offset].toInt() and 0xFF) shl 24) or
                    ((message[offset + 1].toInt() and 0xFF) shl 16) or
                    ((message[offset + 2].toInt() and 0xFF) shl 8) or
                    (message[offset + 3].toInt() and 0xFF)
            }
            for (i in 16 until 80) words[i] = (words[i - 3] xor words[i - 8] xor words[i - 14] xor words[i - 16]).rotateLeft(1)
            var a = h0
            var b = h1
            var c = h2
            var d = h3
            var e = h4
            for (i in 0 until 80) {
                val (f, k) = when (i) {
                    in 0..19 -> ((b and c) or (b.inv() and d)) to 0x5A827999
                    in 20..39 -> (b xor c xor d) to 0x6ED9EBA1
                    in 40..59 -> ((b and c) or (b and d) or (c and d)) to 0x8F1BBCDC.toInt()
                    else -> (b xor c xor d) to 0xCA62C1D6.toInt()
                }
                val next = a.rotateLeft(5) + f + e + k + words[i]
                e = d
                d = c
                c = b.rotateLeft(30)
                b = a
                a = next
            }
            h0 += a
            h1 += b
            h2 += c
            h3 += d
            h4 += e
        }
        return intArrayOf(h0, h1, h2, h3, h4).flatMap { word -> (3 downTo 0).map { (word ushr (it * 8)).toByte() } }.toByteArray()
    }

    fun hex(input: ByteArray): String = digest(input).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}
