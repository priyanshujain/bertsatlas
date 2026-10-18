package app.bertopia.anki

import androidx.sqlite.execSQL
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StorageSpikeTest {
    @Test
    fun sqliteCreatesFileDatabaseAndReadsRowsBack() {
        val path = temporaryDirectory().trimEnd('/') + "/anki-spike-${Random.nextLong()}.anki2"
        val connection = openCollectionDatabase(path)
        connection.execSQL("create table notes (id integer primary key, flds text not null)")
        connection.execSQL("insert into notes (id, flds) values (1, 'France\u001fParis')")
        val fields = connection.prepare("select flds from notes where id = ?").use { statement ->
            statement.bindLong(1, 1)
            statement.step()
            statement.getText(0)
        }
        connection.close()
        assertEquals("France\u001fParis", fields)
    }

    @Test
    fun zstdRoundTripsLargeAndEmptyPayloads() {
        val payload = ByteArray(1_000_000) { (it % 251).toByte() }
        assertContentEquals(payload, zstdDecompress(zstdCompress(payload)))
        assertContentEquals(ByteArray(0), zstdDecompress(zstdCompress(ByteArray(0))))
    }

    @Test
    fun zstdDecodesFrameWithoutContentSize() {
        val streamedFrameOfHello = byteArrayOf(
            0x28, 0xB5.toByte(), 0x2F, 0xFD.toByte(), 0x00, 0x58, 0x29, 0x00, 0x00,
            0x68, 0x65, 0x6C, 0x6C, 0x6F,
        )
        assertEquals("hello", zstdDecompress(streamedFrameOfHello).decodeToString())
    }

    @Test
    fun mediaEntriesDecodeFromHandEncodedProtobuf() {
        val sha1 = ByteArray(20) { it.toByte() }
        val entry = byteArrayOf(0x0A, 0x05) + "a.png".encodeToByteArray() +
            byteArrayOf(0x10, 0xD2.toByte(), 0x09) +
            byteArrayOf(0x1A, 0x14) + sha1 +
            byteArrayOf(0xF8.toByte(), 0x0F, 0x07)
        val second = byteArrayOf(0x0A, 0x05) + "b.mp3".encodeToByteArray()
        val bytes = byteArrayOf(0x0A, entry.size.toByte()) + entry +
            byteArrayOf(0x0A, second.size.toByte()) + second

        val decoded = decodeMediaEntries(bytes)

        assertEquals(2, decoded.entries.size)
        assertEquals("a.png", decoded.entries[0].name)
        assertEquals(1234, decoded.entries[0].size)
        assertContentEquals(sha1, decoded.entries[0].sha1)
        assertEquals(7, decoded.entries[0].legacyZipFilename)
        assertEquals("b.mp3", decoded.entries[1].name)
        assertNull(decoded.entries[1].legacyZipFilename)
        assertEquals(2, decodeMediaEntries(encodeMediaEntries(decoded)).entries.size)
    }
}
