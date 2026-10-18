package app.bertsatlas.anki.io

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.posix.O_RDONLY
import platform.posix.SEEK_END
import platform.posix.close
import platform.posix.lseek
import platform.posix.open
import platform.posix.pread

@OptIn(ExperimentalForeignApi::class)
internal actual class RandomAccessFile actual constructor(path: String) : AutoCloseable {
    private val descriptor = open(path, O_RDONLY).also { check(it >= 0) { "Cannot open $path" } }

    actual val size: Long = lseek(descriptor, 0, SEEK_END)

    actual fun readFully(position: Long, buffer: ByteArray, offset: Int, length: Int) {
        if (length == 0) return
        buffer.usePinned { pinned ->
            var done = 0
            while (done < length) {
                val count = pread(descriptor, pinned.addressOf(offset + done), (length - done).convert(), position + done).toInt()
                check(count > 0) { "Unexpected end of file" }
                done += count
            }
        }
    }

    actual override fun close() {
        close(descriptor)
    }
}
