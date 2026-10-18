package app.bertopia.anki

import app.bertopia.anki.io.ByteSource
import com.github.luben.zstd.Zstd
import com.github.luben.zstd.ZstdInputStream
import java.io.ByteArrayInputStream
import java.io.InputStream

actual fun zstdCompress(data: ByteArray, level: Int): ByteArray = Zstd.compress(data, level)

actual fun zstdDecompress(data: ByteArray): ByteArray =
    ZstdInputStream(ByteArrayInputStream(data)).use { it.readBytes() }

internal actual fun zstdDecodingSource(compressed: ByteSource): ByteSource {
    val input = object : InputStream() {
        override fun read(): Int {
            val single = ByteArray(1)
            return if (read(single, 0, 1) < 0) -1 else single[0].toInt() and 0xFF
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = compressed.read(buffer, offset, length)

        override fun close() = compressed.close()
    }
    val decoded = ZstdInputStream(input)
    return object : ByteSource {
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            while (true) {
                val count = decoded.read(buffer, offset, length)
                if (count != 0) return count
            }
        }

        override fun close() = decoded.close()
    }
}
