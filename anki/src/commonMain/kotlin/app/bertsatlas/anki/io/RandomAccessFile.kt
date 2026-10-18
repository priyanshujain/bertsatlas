package app.bertsatlas.anki.io

internal expect class RandomAccessFile(path: String) : AutoCloseable {
    val size: Long
    fun readFully(position: Long, buffer: ByteArray, offset: Int, length: Int)
    override fun close()
}
