package app.bertsatlas.anki.io

class ZipFormatException(message: String) : Exception(message)

internal interface ZipStorage : AutoCloseable {
    val size: Long
    fun readFully(position: Long, buffer: ByteArray, offset: Int, length: Int)
    override fun close() {}
}

private class ByteArrayStorage(private val bytes: ByteArray) : ZipStorage {
    override val size: Long get() = bytes.size.toLong()

    override fun readFully(position: Long, buffer: ByteArray, offset: Int, length: Int) {
        if (position < 0 || position + length > bytes.size) throw ZipFormatException("Zip read out of bounds")
        bytes.copyInto(buffer, offset, position.toInt(), position.toInt() + length)
    }
}

private class FileStorage(path: String) : ZipStorage {
    private val file = RandomAccessFile(path)
    override val size: Long get() = file.size

    override fun readFully(position: Long, buffer: ByteArray, offset: Int, length: Int) {
        if (position < 0 || position + length > file.size) throw ZipFormatException("Zip read out of bounds")
        file.readFully(position, buffer, offset, length)
    }

    override fun close() = file.close()
}

internal class ZipArchive private constructor(private val storage: ZipStorage, private val entries: Map<String, Entry>) : AutoCloseable {
    class Entry(val method: Int, val compressedSize: Long, val uncompressedSize: Long, val localHeaderOffset: Long)

    val names: Set<String> get() = entries.keys

    fun contains(name: String): Boolean = name in entries

    fun uncompressedSize(name: String): Long = entry(name).uncompressedSize

    fun read(name: String): ByteArray {
        val entry = entry(name)
        if (entry.uncompressedSize > Int.MAX_VALUE - 8) throw ZipFormatException("Zip entry $name is too large to read into memory")
        return open(name).readAllBytes()
    }

    fun open(name: String): ByteSource {
        val entry = entry(name)
        val header = ByteArray(30)
        storage.readFully(entry.localHeaderOffset, header, 0, header.size)
        if (header.readInt(0) != LOCAL_HEADER_SIGNATURE) throw ZipFormatException("Bad local header for $name")
        val dataStart = entry.localHeaderOffset + 30 + header.readShort(26) + header.readShort(28)
        val raw = StoredEntrySource(storage, dataStart, entry.compressedSize)
        return when (entry.method) {
            STORED -> raw
            DEFLATED -> InflatingSource(raw)
            else -> throw ZipFormatException("Unsupported zip compression ${entry.method} for $name")
        }
    }

    override fun close() = storage.close()

    private fun entry(name: String): Entry = entries[name] ?: throw ZipFormatException("Missing zip entry $name")

    private class StoredEntrySource(private val storage: ZipStorage, start: Long, length: Long) : ByteSource {
        private var position = start
        private val end = start + length

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (position >= end) return -1
            val count = minOf(length.toLong(), end - position).toInt()
            storage.readFully(position, buffer, offset, count)
            position += count
            return count
        }
    }

    companion object {
        private const val LOCAL_HEADER_SIGNATURE = 0x04034b50
        private const val CENTRAL_HEADER_SIGNATURE = 0x02014b50
        private const val END_SIGNATURE = 0x06054b50
        private const val ZIP64_END_LOCATOR_SIGNATURE = 0x07064b50
        private const val ZIP64_END_SIGNATURE = 0x06064b50
        private const val ZIP64_EXTRA_ID = 0x0001
        private const val STORED = 0
        private const val DEFLATED = 8
        private const val MAX_END_SEARCH = 22 + 65535 + 20

        fun open(bytes: ByteArray): ZipArchive = open(ByteArrayStorage(bytes))

        fun openFile(path: String): ZipArchive {
            val storage = FileStorage(path)
            return try {
                open(storage)
            } catch (failure: Throwable) {
                storage.close()
                throw failure
            }
        }

        private fun open(storage: ZipStorage): ZipArchive {
            val tailLength = minOf(storage.size, MAX_END_SEARCH.toLong()).toInt()
            if (tailLength < 22) throw ZipFormatException("Not a zip file")
            val tailStart = storage.size - tailLength
            val tail = ByteArray(tailLength)
            storage.readFully(tailStart, tail, 0, tailLength)
            val end = (tailLength - 22 downTo 0).firstOrNull { tail.readInt(it) == END_SIGNATURE }
                ?: throw ZipFormatException("Not a zip file")
            var count = tail.readShort(end + 10).toLong()
            var directorySize = tail.readUInt(end + 12)
            var directoryOffset = tail.readUInt(end + 16)
            if (end >= 20 && tail.readInt(end - 20) == ZIP64_END_LOCATOR_SIGNATURE) {
                val zip64EndOffset = tail.readLong(end - 20 + 8)
                val record = ByteArray(56)
                storage.readFully(zip64EndOffset, record, 0, record.size)
                if (record.readInt(0) != ZIP64_END_SIGNATURE) throw ZipFormatException("Bad zip64 end record")
                count = record.readLong(32)
                directorySize = record.readLong(40)
                directoryOffset = record.readLong(48)
            }
            if (directoryOffset + directorySize > storage.size || directorySize > Int.MAX_VALUE) throw ZipFormatException("Bad central directory")
            val directory = ByteArray(directorySize.toInt())
            storage.readFully(directoryOffset, directory, 0, directory.size)
            val entries = LinkedHashMap<String, Entry>()
            var offset = 0
            repeat(count.toInt()) {
                if (offset + 46 > directory.size || directory.readInt(offset) != CENTRAL_HEADER_SIGNATURE) throw ZipFormatException("Bad central directory")
                val nameLength = directory.readShort(offset + 28)
                val extraLength = directory.readShort(offset + 30)
                val commentLength = directory.readShort(offset + 32)
                val name = directory.decodeToString(offset + 46, offset + 46 + nameLength)
                var compressedSize = directory.readUInt(offset + 20)
                var uncompressedSize = directory.readUInt(offset + 24)
                var localHeaderOffset = directory.readUInt(offset + 42)
                val extraStart = offset + 46 + nameLength
                var extra = extraStart
                while (extra + 4 <= extraStart + extraLength) {
                    val id = directory.readShort(extra)
                    val length = directory.readShort(extra + 2)
                    if (id == ZIP64_EXTRA_ID) {
                        var field = extra + 4
                        if (uncompressedSize == UINT_MAX) uncompressedSize = directory.readLong(field).also { field += 8 }
                        if (compressedSize == UINT_MAX) compressedSize = directory.readLong(field).also { field += 8 }
                        if (localHeaderOffset == UINT_MAX) localHeaderOffset = directory.readLong(field)
                    }
                    extra += 4 + length
                }
                entries[name] = Entry(directory.readShort(offset + 10), compressedSize, uncompressedSize, localHeaderOffset)
                offset += 46 + nameLength + extraLength + commentLength
            }
            return ZipArchive(storage, entries)
        }

        private const val UINT_MAX = 0xFFFFFFFFL
    }
}

internal class ZipWriter {
    private class Written(val name: ByteArray, val crc: Int, val size: Int, val offset: Int)

    private val output = ByteBuilder()
    private val written = mutableListOf<Written>()

    fun add(name: String, data: ByteArray) {
        val nameBytes = name.encodeToByteArray()
        val crc = Crc32.of(data)
        written += Written(nameBytes, crc, data.size, output.size)
        output.int(0x04034b50).short(20).short(0x0800).short(0).short(0).short(0x21)
            .int(crc).int(data.size).int(data.size).short(nameBytes.size).short(0)
            .bytes(nameBytes).bytes(data)
    }

    fun finish(): ByteArray {
        val centralStart = output.size
        written.forEach { entry ->
            output.int(0x02014b50).short(20).short(20).short(0x0800).short(0).short(0).short(0x21)
                .int(entry.crc).int(entry.size).int(entry.size).short(entry.name.size).short(0).short(0)
                .short(0).short(0).int(0).int(entry.offset).bytes(entry.name)
        }
        val centralSize = output.size - centralStart
        output.int(0x06054b50).short(0).short(0).short(written.size).short(written.size)
            .int(centralSize).int(centralStart).short(0)
        return output.toByteArray()
    }
}

private class ByteBuilder {
    private var buffer = ByteArray(1024)
    var size = 0
        private set

    private fun ensure(extra: Int) {
        if (size + extra > buffer.size) buffer = buffer.copyOf(maxOf(buffer.size * 2, size + extra))
    }

    fun short(value: Int) = apply {
        ensure(2)
        buffer[size++] = value.toByte()
        buffer[size++] = (value ushr 8).toByte()
    }

    fun int(value: Int) = apply {
        ensure(4)
        for (shift in 0..24 step 8) buffer[size++] = (value ushr shift).toByte()
    }

    fun bytes(value: ByteArray) = apply {
        ensure(value.size)
        value.copyInto(buffer, size)
        size += value.size
    }

    fun toByteArray(): ByteArray = buffer.copyOf(size)
}

private fun ByteArray.readShort(offset: Int): Int = (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)

private fun ByteArray.readInt(offset: Int): Int =
    if (offset < 0 || offset + 4 > size) 0 else readShort(offset) or (readShort(offset + 2) shl 16)

private fun ByteArray.readUInt(offset: Int): Long = readInt(offset).toLong() and 0xFFFFFFFFL

private fun ByteArray.readLong(offset: Int): Long = readUInt(offset) or (readUInt(offset + 4) shl 32)
