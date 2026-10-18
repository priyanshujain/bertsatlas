package app.bertopia.anki.io

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.free
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import platform.posix.memcpy
import platform.zlib.Z_BUF_ERROR
import platform.zlib.Z_FINISH
import platform.zlib.Z_NO_FLUSH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.ZLIB_VERSION
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2_
import platform.zlib.z_stream

@OptIn(ExperimentalForeignApi::class)
internal actual fun inflateRaw(compressed: ByteArray, uncompressedSize: Int): ByteArray {
    val output = ByteArray(uncompressedSize)
    if (uncompressedSize == 0) return output
    memScoped {
        val stream = alloc<z_stream>()
        check(inflateInit2_(stream.ptr, -15, ZLIB_VERSION, sizeOf<z_stream>().convert()) == Z_OK) { "zlib init failed" }
        try {
            compressed.usePinned { input ->
                output.usePinned { out ->
                    stream.next_in = input.addressOf(0).reinterpret<UByteVar>()
                    stream.avail_in = compressed.size.convert()
                    stream.next_out = out.addressOf(0).reinterpret<UByteVar>()
                    stream.avail_out = uncompressedSize.convert()
                    val status = inflate(stream.ptr, Z_FINISH)
                    check(status == Z_STREAM_END || (status == Z_OK && stream.avail_out == 0u)) { "Deflated zip entry is corrupt" }
                }
            }
        } finally {
            inflateEnd(stream.ptr)
        }
    }
    return output
}

@OptIn(ExperimentalForeignApi::class)
internal actual class RawInflater actual constructor() : AutoCloseable {
    private val stream = nativeHeap.alloc<z_stream>()
    private val inputBuffer: CPointer<ByteVar> = nativeHeap.allocArray(STREAM_CHUNK_SIZE)
    private var finished = false
    private var closed = false

    init {
        check(inflateInit2_(stream.ptr, -15, ZLIB_VERSION, sizeOf<z_stream>().convert()) == Z_OK) { "zlib init failed" }
        stream.avail_in = 0u
    }

    actual fun setInput(buffer: ByteArray, offset: Int, length: Int) {
        require(length <= STREAM_CHUNK_SIZE)
        if (length > 0) buffer.usePinned { memcpy(inputBuffer, it.addressOf(offset), length.convert()) }
        stream.next_in = inputBuffer.reinterpret()
        stream.avail_in = length.convert()
    }

    actual fun needsInput(): Boolean = !finished && stream.avail_in == 0u

    actual fun finished(): Boolean = finished

    actual fun inflate(output: ByteArray, offset: Int, length: Int): Int {
        if (finished || length == 0) return 0
        return output.usePinned { out ->
            stream.next_out = out.addressOf(offset).reinterpret()
            stream.avail_out = length.convert()
            val status = inflate(stream.ptr, Z_NO_FLUSH)
            when (status) {
                Z_STREAM_END -> finished = true
                Z_OK, Z_BUF_ERROR -> Unit
                else -> throw ZipFormatException("Deflated zip entry is corrupt")
            }
            length - stream.avail_out.toInt()
        }
    }

    actual override fun close() {
        if (closed) return
        closed = true
        inflateEnd(stream.ptr)
        nativeHeap.free(inputBuffer)
        nativeHeap.free(stream)
    }
}
