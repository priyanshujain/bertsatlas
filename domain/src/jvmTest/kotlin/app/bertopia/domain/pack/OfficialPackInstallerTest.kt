package app.bertopia.domain.pack

import app.bertopia.anki.collection.AnkiCollection
import app.bertopia.domain.quiz.PackCardReader
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OfficialPackInstallerTest {
    private val root = kotlin.io.path.createTempDirectory("bert-official-").toFile()
    private val library = File(root, "library").path
    private val installer = OfficialPackInstaller(library, File(root, "work").path)

    @AfterTest
    fun removeTemporaryFiles() {
        root.deleteRecursively()
    }

    @Test
    fun installCopiesTheManifestCover() {
        val result = installer.install(buildPack("1.0.0", listOf(basicCard("a", "basics"))))
        assertEquals(1, result.mediaFiles)
        assertContentEquals(COVER_BYTES, File(PackLibraryLayout.mediaDirectory(library, PACK_ID), "cover.png").readBytes())
    }

    @Test
    fun updateMovesCardsToTheirNewSectionAndDropsRemovedSections() {
        installer.install(buildPack("1.0.0", listOf(basicCard("a", "basics"), basicCard("b", "extras"))))
        val result = installer.install(buildPack("1.1.0", listOf(basicCard("a", "basics"), basicCard("b", "advanced"))))
        assertEquals("1.0.0", result.previousVersion)
        assertEquals(0, result.retiredNotes)
        AnkiCollection.open(PackLibraryLayout.collectionPath(library, PACK_ID)).use { collection ->
            assertEquals(listOf("Interview", "Interview::advanced", "Interview::basics"), collection.decks().map { it.name }.filter { it.startsWith("Interview") })
            assertNull(collection.deckByName("Interview::extras"))
            val movedNote = collection.notes().single { it.guid == "b" }
            val movedCard = collection.cardsOfNote(movedNote.id).single()
            assertEquals("Interview::advanced", collection.deck(movedCard.deckId)!!.name)
        }
    }

    @Test
    fun updateKeepsCardsOfADroppedSectionInsideThePack() {
        installer.install(buildPack("1.0.0", listOf(basicCard("a", "basics"), basicCard("b", "extras"))))
        installer.install(buildPack("1.1.0", listOf(basicCard("a", "basics"))))
        AnkiCollection.open(PackLibraryLayout.collectionPath(library, PACK_ID)).use { collection ->
            assertNull(collection.deckByName("Interview::extras"))
            val retired = collection.notes().single { it.guid == "b" }
            assertTrue(BertTags.RETIRED in retired.tags)
            assertEquals("Interview", collection.deck(collection.cardsOfNote(retired.id).single().deckId)!!.name)
        }
    }

    @Test
    fun choiceNotesRoundTripThroughTheBuiltPackage() {
        val fields = mapOf(
            "Question" to "Which structure is FIFO?",
            "Choices" to "Queue\nStack\nHeap",
            "Correct" to "Queue",
            "Explanation" to "First in, first out.",
            "Hint" to "Think of a line at a shop.",
            "Source" to "CLRS",
            BertNoteTypes.BERT_META_FIELD to "",
        )
        installer.install(buildPack("1.0.0", listOf(SourceCardJson("choice", BertNoteTypes.Choice.name, "basics", fields, listOf("bert::level::mid")))))
        AnkiCollection.open(PackLibraryLayout.collectionPath(library, PACK_ID)).use { collection ->
            val notetype = collection.notetypes().single { it.name == BertNoteTypes.Choice.name }
            assertEquals(BertNoteTypes.Choice.fields, notetype.fieldNames)
            val note = collection.notes().single { it.guid == "choice" }
            assertEquals(fields, notetype.fieldNames.zip(note.fields).toMap())
            assertEquals(listOf("bert::level::mid"), note.tags)
            val card = PackCardReader.read(PACK_ID, notetype, note, 0, "Interview::basics")
            assertEquals(listOf("Queue", "Stack", "Heap"), card.authoredChoices)
            assertEquals("Queue", card.shortAnswer)
            assertEquals("First in, first out.", card.explanation)
        }
    }

    private data class SourceCardJson(val guid: String, val noteType: String, val section: String, val fields: Map<String, String>, val tags: List<String>)

    private fun basicCard(guid: String, section: String) = SourceCardJson(
        guid,
        BertNoteTypes.Basic.name,
        section,
        BertNoteTypes.Basic.fields.associateWith { "" } + mapOf("Question" to "Question $guid", "Answer" to "Answer $guid"),
        emptyList(),
    )

    private fun buildPack(version: String, cards: List<SourceCardJson>): String {
        val source = File(root, "source-$version").apply { mkdirs() }
        File(source, "cover").mkdirs()
        File(source, "cover/cover.png").writeBytes(COVER_BYTES)
        File(source, PackSourceBuilder.MANIFEST_FILE).writeText(
            """{"packId":"$PACK_ID","version":"$version","official":true,"title":"Interview","accentColor":"#27635B","coverMedia":"cover.png"}""",
        )
        File(source, PackSourceBuilder.CARDS_FILE).writeText(
            cards.joinToString(",", prefix = """{"cards":[""", postfix = "]}") { card ->
                val fields = card.fields.entries.joinToString(",") { (name, value) -> "${quoted(name)}:${quoted(value)}" }
                val tags = card.tags.joinToString(",") { quoted(it) }
                """{"guid":"${card.guid}","noteType":"${card.noteType}","section":"${card.section}","fields":{$fields},"tags":[$tags]}"""
            },
        )
        val output = File(root, "$PACK_ID-$version.apkg").path
        PackSourceBuilder.build(source.path, File(root, "build-$version").path, output)
        return output
    }

    private fun quoted(value: String) = kotlinx.serialization.json.JsonPrimitive(value).toString()

    private companion object {
        const val PACK_ID = "interview"
        val COVER_BYTES = byteArrayOf(9, 8, 7)
    }
}
