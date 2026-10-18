package app.bertopia.anki.io

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

internal interface ByteSource : AutoCloseable {
    fun read(buffer: ByteArray, offset: Int, length: Int): Int

    override fun close() {}
}

internal const val STREAM_CHUNK_SIZE = 256 * 1024

internal fun ByteSource.readAllBytes(): ByteArray = use { source ->
    val parts = mutableListOf<ByteArray>()
    val chunk = ByteArray(STREAM_CHUNK_SIZE)
    while (true) {
        val count = source.read(chunk, 0, chunk.size)
        if (count < 0) break
        parts += chunk.copyOf(count)
    }
    val joined = ByteArray(parts.sumOf { it.size })
    var offset = 0
    parts.forEach { part ->
        part.copyInto(joined, offset)
        offset += part.size
    }
    joined
}

internal fun ByteSource.writeTo(path: String, beforeEachChunk: (bytesWritten: Long) -> Unit = {}): Long = use { source ->
    Path(path).parent?.let { SystemFileSystem.createDirectories(it) }
    var total = 0L
    SystemFileSystem.sink(Path(path)).buffered().use { sink ->
        val chunk = ByteArray(STREAM_CHUNK_SIZE)
        while (true) {
            beforeEachChunk(total)
            val count = source.read(chunk, 0, chunk.size)
            if (count < 0) break
            sink.write(chunk, 0, count)
            total += count
        }
    }
    total
}
