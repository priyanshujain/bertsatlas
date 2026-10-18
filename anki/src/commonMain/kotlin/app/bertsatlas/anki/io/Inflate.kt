package app.bertsatlas.anki.io

internal expect fun inflateRaw(compressed: ByteArray, uncompressedSize: Int): ByteArray

internal expect class RawInflater() : AutoCloseable {
    fun setInput(buffer: ByteArray, offset: Int, length: Int)
    fun needsInput(): Boolean
    fun finished(): Boolean
    fun inflate(output: ByteArray, offset: Int, length: Int): Int
    override fun close()
}

internal class InflatingSource(private val compressed: ByteSource) : ByteSource {
    private val inflater = RawInflater()
    private val input = ByteArray(STREAM_CHUNK_SIZE)
    private var inputEnded = false

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        while (true) {
            if (inflater.finished()) return -1
            if (inflater.needsInput()) {
                if (inputEnded) throw ZipFormatException("Deflated zip entry is truncated")
                val count = compressed.read(input, 0, input.size)
                if (count < 0) inputEnded = true else inflater.setInput(input, 0, count)
                continue
            }
            val produced = inflater.inflate(buffer, offset, length)
            if (produced > 0) return produced
        }
    }

    override fun close() {
        inflater.close()
        compressed.close()
    }
}
