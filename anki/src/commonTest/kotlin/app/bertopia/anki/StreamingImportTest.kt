package app.bertopia.anki

import app.bertopia.anki.io.Files
import app.bertopia.anki.pkg.ImportCancelledException
import app.bertopia.anki.pkg.ImportMonitor
import app.bertopia.anki.pkg.ImportStage
import app.bertopia.anki.pkg.PackageFormat
import app.bertopia.anki.pkg.PackageImport
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StreamingImportTest {
    private val pngSignature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

    @Test
    fun oldestPackageWithOnlyCollectionAnki2Imports() {
        val directory = freshDirectory("oldest")
        createCollection(directory).use { target ->
            PackageImport.open(fixture("oldest-anki2-only.apkg"), "$directory/work").use { opened ->
                assertEquals(PackageFormat.Legacy, opened.format)
                assertEquals(11, opened.collection.schemaVersion)
                val deck = opened.topLevelDecks().single()
                assertEquals("Python Interview", deck.name)
                assertTrue(deck.preview!!.question.isNotBlank())
                val result = PackageImport.importDeck(opened, deck.deckId, target, "$directory/media")
                assertEquals(6, result.addedNotes)
            }
            assertEquals(3, target.revlog().size)
        }
    }

    @Test
    fun zip64PackageWithSixtySixThousandEntriesOpens() {
        val path = "/tmp/ankidecks/generated/manymedia.apkg"
        if (!Files.exists(path)) return
        PackageImport.open(path, freshDirectory("zip64")).use { opened ->
            assertEquals(66000L, opened.topLevelDecks().single().cardCount)
            assertEquals(66000, opened.media.size)
            assertContentEquals(pngSignature, opened.readMedia(opened.media.last()).copyOf(4))
        }
    }

    @Test
    fun heavyMediaStreamsToDiskAndCancelCleansUp() {
        val path = "/tmp/ankidecks/generated/heavymedia.apkg"
        if (!Files.exists(path)) return
        val directory = freshDirectory("heavy")
        createCollection(directory).use { target ->
            PackageImport.open(path, "$directory/work").use { opened ->
                val deckId = opened.topLevelDecks().single().deckId
                var copied = 0L
                val cancelling = ImportMonitor(
                    onProgress = { if (it.stage == ImportStage.CopyingMedia) copied = it.completed },
                    isCancelled = { copied >= 20 },
                )
                assertFailsWith<ImportCancelledException> {
                    PackageImport.importDeck(opened, deckId, target, "$directory/media", cancelling)
                }
                assertEquals(0, target.noteCount())
                assertTrue(Files.list("$directory/media").isEmpty())
                val result = PackageImport.importDeck(opened, deckId, target, "$directory/media")
                assertEquals(300, result.mediaFiles)
                val first = opened.media.first()
                val written = Files.read("$directory/media/${first.name}")
                assertEquals(first.size, written.size.toLong())
                assertContentEquals(pngSignature, written.copyOf(4))
            }
            assertEquals(300, target.noteCount())
        }
    }

    @Test
    fun unsafeMediaNamesAreRejected() {
        assertTrue(PackageImport.isSafeMediaName("photo 1.png"))
        listOf("../escape.png", "a/b.png", "..", "", "c:\\x.png").forEach { assertTrue(!PackageImport.isSafeMediaName(it), it) }
        assertEquals(listOf("my photo.png", "a&b.png", "song.mp3"), PackageImport.referencedMedia("""<img src="my photo.png"><img src='a&amp;b.png'>[sound:song.mp3]"""))
    }
}
