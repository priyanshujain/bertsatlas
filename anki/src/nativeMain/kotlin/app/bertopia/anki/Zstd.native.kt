package app.bertopia.anki

import app.bertopia.anki.io.ByteSource
import app.bertopia.anki.io.STREAM_CHUNK_SIZE
import app.bertopia.anki.zstd.native.ZSTD_DCtx_reset
import app.bertopia.anki.zstd.native.ZSTD_compress
import app.bertopia.anki.zstd.native.ZSTD_compressBound
import app.bertopia.anki.zstd.native.ZSTD_createDStream
import app.bertopia.anki.zstd.native.ZSTD_decompressStream
import app.bertopia.anki.zstd.native.ZSTD_freeDStream
import app.bertopia.anki.zstd.native.ZSTD_getErrorName
import app.bertopia.anki.zstd.native.ZSTD_inBuffer
import app.bertopia.anki.zstd.native.ZSTD_isError
import app.bertopia.anki.zstd.native.ZSTD_outBuffer
import app.bertopia.anki.zstd.native.ZSTD_reset_session_only
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.free
import kotlinx.cinterop.nativeHeap
import platform.posix.memcpy
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned

private const val CHUNK_SIZE = 128 * 1024

@OptIn(ExperimentalForeignApi::class)
actual fun zstdCompress(data: ByteArray, level: Int): ByteArray {
    val output = ByteArray(ZSTD_compressBound(data.size.convert()).toInt())
    val written = data.usePinned { input ->
        output.usePinned { out ->
            ZSTD_compress(out.addressOf(0), output.size.convert(), input.addressOfOrNull(), data.size.convert(), level)
        }
    }
    check(ZSTD_isError(written) == 0u) { ZSTD_getErrorName(written)?.toKString() ?: "zstd compress failed" }
    return output.copyOf(written.toInt())
}

@OptIn(ExperimentalForeignApi::class)
actual fun zstdDecompress(data: ByteArray): ByteArray {
    val stream = ZSTD_createDStream() ?: error("zstd stream allocation failed")
    try {
        ZSTD_DCtx_reset(stream, ZSTD_reset_session_only)
        val result = mutableListOf<ByteArray>()
        val chunk = ByteArray(CHUNK_SIZE)
        data.usePinned { input ->
            chunk.usePinned { out ->
                memScoped {
                    val inBuffer = alloc<ZSTD_inBuffer>().apply {
                        src = input.addressOfOrNull()
                        size = data.size.convert()
                        pos = 0u
                    }
                    val outBuffer = alloc<ZSTD_outBuffer>()
                    do {
                        outBuffer.dst = out.addressOf(0)
                        outBuffer.size = CHUNK_SIZE.convert()
                        outBuffer.pos = 0u
                        val status = ZSTD_decompressStream(stream, outBuffer.ptr, inBuffer.ptr)
                        check(ZSTD_isError(status) == 0u) { ZSTD_getErrorName(status)?.toKString() ?: "zstd decompress failed" }
                        result += chunk.copyOf(outBuffer.pos.toInt())
                    } while (inBuffer.pos < inBuffer.size || outBuffer.pos == outBuffer.size)
                }
            }
        }
        return concatenate(result)
    } finally {
        ZSTD_freeDStream(stream)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun kotlinx.cinterop.Pinned<ByteArray>.addressOfOrNull() =
    if (get().isEmpty()) null else addressOf(0)

@OptIn(ExperimentalForeignApi::class)
internal actual fun zstdDecodingSource(compressed: ByteSource): ByteSource = ZstdDecodingSource(compressed)

@OptIn(ExperimentalForeignApi::class)
private class ZstdDecodingSource(private val compressed: ByteSource) : ByteSource {
    private val stream = ZSTD_createDStream() ?: error("zstd stream allocation failed")
    private val inputBuffer: CPointer<ByteVar> = nativeHeap.allocArray(STREAM_CHUNK_SIZE)
    private val inBuffer = nativeHeap.alloc<ZSTD_inBuffer>().apply {
        src = inputBuffer
        size = 0u
        pos = 0u
    }
    private val outBuffer = nativeHeap.alloc<ZSTD_outBuffer>()
    private val transfer = ByteArray(STREAM_CHUNK_SIZE)
    private var inputEnded = false
    private var closed = false

    init {
        ZSTD_DCtx_reset(stream, ZSTD_reset_session_only)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        while (true) {
            if (inBuffer.pos == inBuffer.size && !inputEnded) {
                val count = compressed.read(transfer, 0, transfer.size)
                if (count < 0) {
                    inputEnded = true
                } else {
                    if (count > 0) transfer.usePinned { memcpy(inputBuffer, it.addressOf(0), count.convert()) }
                    inBuffer.size = count.convert()
                    inBuffer.pos = 0u
                }
            }
            val produced = buffer.usePinned { out ->
                outBuffer.dst = out.addressOf(offset)
                outBuffer.size = length.convert()
                outBuffer.pos = 0u
                val status = ZSTD_decompressStream(stream, outBuffer.ptr, inBuffer.ptr)
                check(ZSTD_isError(status) == 0u) { ZSTD_getErrorName(status)?.toKString() ?: "zstd decompress failed" }
                outBuffer.pos.toInt()
            }
            if (produced > 0) return produced
            if (inputEnded && inBuffer.pos == inBuffer.size) return -1
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        ZSTD_freeDStream(stream)
        nativeHeap.free(inputBuffer)
        nativeHeap.free(inBuffer)
        nativeHeap.free(outBuffer)
        compressed.close()
    }
}

private fun concatenate(parts: List<ByteArray>): ByteArray {
    val joined = ByteArray(parts.sumOf { it.size })
    var offset = 0
    parts.forEach { part ->
        part.copyInto(joined, offset)
        offset += part.size
    }
    return joined
}
