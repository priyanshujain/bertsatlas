package app.bertsatlas.anki

import app.bertsatlas.anki.collection.AnkiCollection
import app.bertsatlas.anki.io.Files
import app.bertsatlas.anki.model.Deck
import app.bertsatlas.anki.model.Note
import app.bertsatlas.anki.pkg.PackageExport
import app.bertsatlas.anki.pkg.PackageFormat
import app.bertsatlas.anki.pkg.PackageImport
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PackageTest {
    @Test
    fun modernAnkiExportReadsAsSchema18WithDecksAndMedia() {
        PackageImport.open(fixture("anki-modern.apkg"), freshDirectory("modern")).use { opened ->
            assertEquals(PackageFormat.Modern, opened.format)
            assertEquals(18, opened.collection.schemaVersion)
            val deck = opened.topLevelDecks().single()
            assertEquals("Test Pack", deck.name)
            assertEquals(4, deck.cardCount)
            assertEquals(listOf("Section A"), deck.sectionNames)
            assertEquals(listOf("pic.png"), opened.media.map { it.name })
            assertEquals(setOf("Basic", "Bert Pack Info"), opened.collection.notetypes().map { it.name }.toSet())
            assertEquals(listOf("Front", "Back"), opened.collection.notetypeByName("Basic")!!.fieldNames)
            assertEquals("{{Front}}", opened.collection.notetypeByName("Basic")!!.templates.single().questionFormat)
            assertTrue(PackManifestText.read(opened.collection.deckByName("Test Pack")!!.description)!!.contains("test.pack"))
            assertEquals("Intro text", PackManifestText.readableText(opened.collection.deckByName("Test Pack")!!.description))
        }
    }

    @Test
    fun legacyPackageAndColpkgListTheirTopLevelDecks() {
        PackageImport.open(fixture("anki-legacy.apkg"), freshDirectory("legacy")).use { opened ->
            assertEquals(PackageFormat.Legacy, opened.format)
            assertEquals(listOf("Test Pack" to 4L), opened.topLevelDecks().map { it.name to it.cardCount })
            assertEquals(listOf("pic.png"), opened.media.map { it.name })
        }
        PackageImport.open(fixture("anki-two-decks.colpkg"), freshDirectory("colpkg")).use { opened ->
            assertEquals(listOf("Other Deck" to 1L, "Test Pack" to 4L), opened.topLevelDecks().map { it.name to it.cardCount })
        }
    }

    @Test
    fun importingADeckKeepsSchedulingHistoryTagsMediaAndManifest() {
        val directory = freshDirectory("import")
        createCollection(directory).use { target ->
            PackageImport.open(fixture("anki-modern.apkg"), "$directory/work").use { opened ->
                val deckId = opened.topLevelDecks().single().deckId
                val result = PackageImport.importDeck(opened, deckId, target, "$directory/media")
                assertEquals(4, result.addedNotes)
                assertEquals(4, result.addedCards)
                assertEquals(1, result.mediaFiles)
            }
            assertEquals(listOf("Default", "Test Pack", "Test Pack::Section A"), target.decks().map { it.name })
            val reviewed = target.cards().single { it.scheduling.data.contains("\"s\"") }
            assertTrue(reviewed.scheduling.data.contains("2.3065"))
            assertEquals(1, target.revlogOfCard(reviewed.id).size)
            assertEquals(3, target.notes().count { "bert::similar::g1" in it.tags })
            assertTrue(Files.exists("$directory/media/pic.png"))
            assertTrue(PackManifestText.read(target.deckByName("Test Pack")!!.description)!!.contains("\"packId\": \"test.pack\""))
        }
    }

    @Test
    fun reimportMergesByGuidKeepingProgress() {
        val directory = freshDirectory("merge")
        createCollection(directory).use { target ->
            PackageImport.open(fixture("anki-modern.apkg"), "$directory/work").use { opened ->
                PackageImport.importDeck(opened, opened.topLevelDecks().single().deckId, target, "$directory/media")
            }
            val note = target.notes().first { it.fields.first().startsWith("Q0") }
            target.saveNote(note.copy(fields = listOf("edited", note.fields[1])))
            PackageImport.open(fixture("anki-legacy.apkg"), "$directory/work").use { opened ->
                val result = PackageImport.importDeck(opened, opened.topLevelDecks().single().deckId, target, "$directory/media")
                assertEquals(0, result.addedNotes)
                assertEquals(4, result.updatedNotes)
                assertEquals(0, result.addedCards)
            }
            assertEquals(4, target.noteCount())
            assertTrue(target.note(note.id)!!.fields.first().startsWith("Q0"))
            assertEquals(1, target.revlog().size)
        }
    }

    @Test
    fun exportedPackagesRoundTripInBothFormats() {
        val directory = freshDirectory("export")
        val sourcePath: String
        createCollection(directory).use { source ->
            sourcePath = source.path
            val notetype = source.saveNotetype(basicNotetype())
            val deck = source.saveDeck(Deck(0, "Capitals", PackManifestText.write("Learn capitals.", """{"packId":"capitals"}""")))
            source.addNote(Note(0, "g1", notetype.id, listOf("France <img src=\"fr.png\">", "Paris", ""), listOf("t")), deck.id)
        }
        Files.write("$directory/media/fr.png", byteArrayOf(1, 2, 3))
        PackageFormat.entries.forEach { format ->
            listOf(true, false).forEach { includeScheduling ->
                val output = "$directory/out-$format-$includeScheduling.apkg"
                PackageExport.export(sourcePath, "$directory/media", output, "$directory/work", format, includeScheduling)
                PackageImport.open(output, "$directory/work").use { opened ->
                    assertEquals(format, opened.format)
                    assertEquals(11, opened.collection.schemaVersion)
                    assertEquals(listOf("Capitals" to 1L), opened.topLevelDecks().map { it.name to it.cardCount })
                    assertContentEquals(byteArrayOf(1, 2, 3), opened.readMedia(opened.media.single()))
                    assertEquals("""{"packId":"capitals"}""", PackManifestText.read(opened.collection.deckByName("Capitals")!!.description))
                    assertEquals("Learn capitals.", PackManifestText.readableText(opened.collection.deckByName("Capitals")!!.description))
                }
            }
        }
        AnkiCollection.open(sourcePath).use { assertEquals(1, it.noteCount()) }
    }

    @Test
    fun manifestTextReplacesExistingBlock() {
        val first = PackManifestText.write("", """{"v":1}""")
        val second = PackManifestText.write("Intro\n$first", """{"v":2}""")
        assertEquals("""{"v":2}""", PackManifestText.read(second))
        assertEquals("Intro", PackManifestText.readableText(second))
    }
}
