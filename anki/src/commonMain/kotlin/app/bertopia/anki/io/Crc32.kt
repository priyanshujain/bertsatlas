package app.bertopia.anki.io

internal object Crc32 {
    private val table = IntArray(256) { index ->
        var value = index
        repeat(8) { value = if (value and 1 != 0) (value ushr 1) xor 0xEDB88320.toInt() else value ushr 1 }
        value
    }

    fun of(data: ByteArray): Int {
        var crc = -1
        for (byte in data) crc = table[(crc xor byte.toInt()) and 0xFF] xor (crc ushr 8)
        return crc.inv()
    }
}
