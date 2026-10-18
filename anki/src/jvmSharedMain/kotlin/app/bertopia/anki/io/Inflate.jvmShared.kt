package app.bertopia.anki.io

import java.util.zip.Inflater

internal actual fun inflateRaw(compressed: ByteArray, uncompressedSize: Int): ByteArray {
    val inflater = Inflater(true)
    try {
        inflater.setInput(compressed)
        val output = ByteArray(uncompressedSize)
        var written = 0
        while (written < uncompressedSize && !inflater.finished()) {
            val count = inflater.inflate(output, written, uncompressedSize - written)
            if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
            written += count
        }
        check(written == uncompressedSize) { "Deflated zip entry is truncated" }
        return output
    } finally {
        inflater.end()
    }
}

internal actual class RawInflater actual constructor() : AutoCloseable {
    private val inflater = Inflater(true)

    actual fun setInput(buffer: ByteArray, offset: Int, length: Int) = inflater.setInput(buffer, offset, length)

    actual fun needsInput(): Boolean = inflater.needsInput()

    actual fun finished(): Boolean = inflater.finished()

    actual fun inflate(output: ByteArray, offset: Int, length: Int): Int = inflater.inflate(output, offset, length)

    actual override fun close() = inflater.end()
}
