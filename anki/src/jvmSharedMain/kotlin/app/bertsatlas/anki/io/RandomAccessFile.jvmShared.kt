package app.bertsatlas.anki.io

internal actual class RandomAccessFile actual constructor(path: String) : AutoCloseable {
    private val file = java.io.RandomAccessFile(path, "r")

    actual val size: Long = file.length()

    actual fun readFully(position: Long, buffer: ByteArray, offset: Int, length: Int) {
        file.seek(position)
        file.readFully(buffer, offset, length)
    }

    actual override fun close() = file.close()
}
