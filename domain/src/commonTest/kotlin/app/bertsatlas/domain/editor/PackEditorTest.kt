package app.bertsatlas.domain.editor

import app.bertsatlas.anki.model.RevlogEntry
import app.bertsatlas.domain.pack.BertNoteTypes
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.writeString
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PackEditorTest {
    private val root = Path(SystemTemporaryDirectory, "pack-editor-test-${Random.nextLong().toULong()}").toString()
    private val workspace = PackWorkspace("$root/library", "$root/work")
    private val editor = PackEditor(workspace)

    @AfterTest
    fun removeFiles() = LibraryFiles.removeRecursively(root)

    private fun createPack(title: String = "Data Structures"): String =
        editor.createPack(PackDraft(title = title, category = "programming", subcategory = "dsa", accentColor = "#27635B"))

    private fun basic(question: String, answer: String, section: String? = null, tags: List<String> = emptyList()) =
        CardDraft("basic", mapOf("Question" to question, "Answer" to answer), tags, section)

    private fun writeFile(path: String, text: String) {
        Path(path).parent?.let { SystemFileSystem.createDirectories(it) }
        SystemFileSystem.sink(Path(path)).buffered().use { it.writeString(text) }
    }

    @Test
    fun createsPacksOnlyOnKnownShelves() {
        val packId = createPack()
        assertEquals("data-structures", packId)
        val failure = assertFailsWith<EditorException> { editor.createPack(PackDraft("Other", category = "programming", subcategory = "nope")) }
        assertEquals(EditorFailureKind.Invalid, failure.kind)
        assertTrue("dsa" in failure.message.orEmpty())
        assertEquals(EditorFailureKind.Conflict, assertFailsWith<EditorException> {
            editor.createPack(PackDraft("Other", category = "programming", subcategory = "dsa", packId = packId))
        }.kind)
    }

    @Test
    fun addsCardsOfEveryStyleIntoSections() {
        val packId = createPack()
        val result = editor.addCards(
            packId,
            listOf(
                basic("What is a stack?", "LIFO", section = "Basics"),
                CardDraft("two-way", mapOf("Side A" to "queue", "Side B" to "FIFO")),
                CardDraft("cloze", mapOf("Text" to "A {{c1::heap}} keeps the {{c2::smallest}} on top")),
                CardDraft("typed", mapOf("Question" to "Big O of binary search", "Answer" to "O(log n)"), section = "Basics::Search"),
            ),
        )
        assertEquals(4, result.added.size)
        assertEquals(6, result.cardCount)
        val page = editor.queryCards(packId, CardQuery())
        assertEquals(4, page.total)
        assertEquals(listOf("Basics", "Basics::Search"), page.sections)
        assertEquals(2, editor.queryCards(packId, CardQuery(section = "Basics")).total)
    }

    @Test
    fun rejectsAWholeBatchWhenOneCardIsInvalid() {
        val packId = createPack()
        val failure = assertFailsWith<EditorException> {
            editor.addCards(packId, listOf(basic("ok", "yes"), CardDraft("basic", mapOf("Question" to "missing answer"))))
        }
        assertEquals(EditorFailureKind.Invalid, failure.kind)
        assertEquals(1, failure.problems.single().index)
        assertEquals(0, editor.queryCards(packId, CardQuery()).total)
    }

    @Test
    fun editsUndoAndRedo() {
        val packId = createPack()
        val ref = editor.addCards(packId, listOf(basic("What is a stack?", "LIFO"))).added.single().ref
        editor.editCard(packId, ref, CardChange(mapOf("Answer" to "Last in, first out")))
        assertEquals("Last in, first out", editor.editableCard(packId, ref).values["Answer"])
        assertEquals("Edit card", editor.undo())
        assertEquals("LIFO", editor.editableCard(packId, ref).values["Answer"])
        assertEquals("Edit card", editor.redo())
        assertEquals("Last in, first out", editor.editableCard(packId, ref).values["Answer"])
        editor.undo()
        assertEquals("Add card", editor.undo())
        assertEquals(0, editor.queryCards(packId, CardQuery()).total)
        assertNull(editor.undo())
    }

    @Test
    fun typingInOneFieldCoalescesIntoOneUndoStep() {
        val packId = createPack()
        val ref = editor.addCards(packId, listOf(basic("Stack", "LIFO"))).added.single().ref
        listOf("L", "La", "Las", "Last").forEach { editor.editCard(packId, ref, CardChange(mapOf("Answer" to it)), coalesceKey = "$ref:Answer") }
        editor.editCard(packId, ref, CardChange(mapOf("Question" to "Stack?")), coalesceKey = "$ref:Question")
        editor.undo()
        assertEquals("Stack", editor.editableCard(packId, ref).values["Question"])
        assertEquals("Last", editor.editableCard(packId, ref).values["Answer"])
        editor.undo()
        assertEquals("LIFO", editor.editableCard(packId, ref).values["Answer"])
    }

    @Test
    fun undoingADeleteRestoresTheSameCardsAndProgress() {
        val packId = createPack()
        val added = editor.addCards(packId, listOf(CardDraft("two-way", mapOf("Side A" to "queue", "Side B" to "FIFO")))).added.single()
        val before = workspace.read(packId) { pack ->
            val cards = pack.collection.cardsOfNote(added.noteId)
            pack.collection.transaction {
                pack.collection.saveCard(cards.first().copy(scheduling = cards.first().scheduling.copy(type = 2, queue = 2, due = 40, interval = 9, reps = 3)))
            }
            pack.collection.cardsOfNote(added.noteId)
        }
        editor.history.clear()
        assertEquals(1, editor.deleteCards(packId, listOf(added.ref)))
        assertEquals(0, editor.queryCards(packId, CardQuery()).total)
        editor.undo()
        val after = workspace.read(packId) { it.collection.cardsOfNote(added.noteId) }
        assertEquals(before, after)
    }

    @Test
    fun tagsMovesAndBulkEditsWithUndo() {
        val packId = createPack()
        val refs = editor.addCards(packId, (1..3).map { basic("Question $it", "answer $it") }).added.map { it.ref }
        editor.tagCards(packId, refs.take(2), add = listOf("trees"), remove = emptyList())
        assertEquals(2, editor.queryCards(packId, CardQuery(tag = "trees")).total)
        editor.moveCards(packId, refs, "Graphs")
        assertEquals(3, editor.queryCards(packId, CardQuery(section = "Graphs")).total)
        assertEquals(3, editor.bulkEdit(packId, refs, BulkFieldEdit.ReplaceText("Answer", "answer", "Answer")))
        assertEquals("Answer 2", editor.editableCard(packId, refs[1]).values["Answer"])
        editor.undo()
        assertEquals("answer 2", editor.editableCard(packId, refs[1]).values["Answer"])
        editor.undo()
        assertEquals(emptyList(), editor.queryCards(packId, CardQuery()).sections)
        assertEquals(EditorFailureKind.Invalid, assertFailsWith<EditorException> {
            editor.bulkEdit(packId, refs, BulkFieldEdit.SetValue("Answer", ""))
        }.kind)
    }

    @Test
    fun createsAndRenamesSections() {
        val packId = createPack()
        editor.createSection(packId, "Trees")
        editor.addCards(packId, listOf(basic("BST", "ordered", section = "Trees::Binary")))
        editor.renameSection(packId, "Trees", "Forest")
        assertEquals(listOf("Forest", "Forest::Binary"), editor.queryCards(packId, CardQuery()).sections)
        editor.undo()
        assertEquals(listOf("Trees", "Trees::Binary"), editor.queryCards(packId, CardQuery()).sections)
    }

    @Test
    fun addsMediaWithoutDuplicates() {
        val packId = createPack()
        writeFile("$root/in/diagram.png", "png-bytes")
        writeFile("$root/in/other/diagram.png", "different-bytes")
        val first = editor.addMedia(packId, "$root/in/diagram.png")
        assertEquals("diagram.png", first.name)
        assertEquals("<img src=\"diagram.png\">", first.reference)
        val again = editor.addMedia(packId, "$root/in/diagram.png", preferredName = "copy.png")
        assertTrue(again.reusedExisting)
        assertEquals("diagram.png", again.name)
        assertEquals("diagram-2.png", editor.addMedia(packId, "$root/in/other/diagram.png").name)
        assertEquals(EditorFailureKind.Invalid, assertFailsWith<EditorException> { editor.addMedia(packId, "$root/in/diagram.png", "../x.png") }.kind)
    }

    @Test
    fun officialPacksAreReadOnlyUntilCopied() {
        val packId = createPack("Flags")
        val added = editor.addCards(packId, listOf(basic("France", "Paris"))).added.single()
        workspace.open(packId).use { pack ->
            pack.collection.transaction {
                val card = pack.collection.cardsOfNote(added.noteId).single()
                pack.collection.saveCard(card.copy(scheduling = card.scheduling.copy(type = 2, queue = 2, interval = 12, reps = 4)))
                pack.collection.addRevlogEntry(RevlogEntry(id = 0, cardId = card.id, ease = 3, interval = 12, lastInterval = 4, easeFactor = 2500, timeMs = 3000, kind = 1))
                pack.saveManifest(pack.manifest.copy(official = true))
            }
        }
        assertEquals(EditorFailureKind.ReadOnly, assertFailsWith<EditorException> { editor.addCards(packId, listOf(basic("a", "b"))) }.kind)
        assertEquals(EditorFailureKind.ReadOnly, assertFailsWith<EditorException> { editor.editCard(packId, added.ref, CardChange(mapOf("Answer" to "x"))) }.kind)
        val copyId = editor.makeCopy(packId)
        assertEquals("flags-copy", copyId)
        workspace.read(copyId) { copy ->
            assertFalse(copy.manifest.official)
            assertEquals("Flags copy", copy.manifest.title)
            assertEquals(12, copy.collection.cards().single().scheduling.interval)
            assertEquals(1, copy.collection.revlog().size)
        }
        editor.editCard(copyId, added.ref, CardChange(mapOf("Answer" to "Paris, France")))
    }

    @Test
    fun updatesAppearanceAndPlacement() {
        val packId = createPack()
        editor.addCards(packId, listOf(basic("q", "a", section = "One")))
        editor.updateAppearance(packId, PackAppearance("Algorithms", "Interview prep", "#123456", null))
        editor.updatePlacement(packId, PackPlacement("programming", "databases"))
        workspace.read(packId) { pack ->
            assertEquals("Algorithms", pack.rootDeck.name)
            assertEquals("databases", pack.manifest.subcategory)
            assertEquals(listOf("One"), pack.sections())
        }
        editor.undo()
        editor.undo()
        workspace.read(packId) { pack ->
            assertEquals("Data Structures", pack.manifest.title)
            assertEquals("dsa", pack.manifest.subcategory)
        }
        assertFailsWith<EditorException> { editor.updatePlacement(packId, PackPlacement("programming", "nope")) }
    }

    @Test
    fun searchesSortsAndFilters() {
        val packId = createPack()
        editor.addCards(packId, listOf(basic("Zebra", "z", tags = listOf("animals")), basic("<b>Apple</b>", "a"), basic("Mango", "m", tags = listOf("animals::fruitbat"))))
        val sorted = editor.queryCards(packId, CardQuery(sort = CardSort.Alphabetical))
        assertEquals(listOf("Apple", "Mango", "Zebra"), sorted.rows.map { it.preview })
        assertEquals(listOf("Zebra", "Mango"), editor.queryCards(packId, CardQuery(tag = "animals", sort = CardSort.Alphabetical, descending = true)).rows.map { it.preview })
        assertEquals(listOf("Apple"), editor.queryCards(packId, CardQuery(search = "app")).rows.map { it.preview })
        assertEquals(1, editor.queryCards(packId, CardQuery(limit = 1, offset = 1)).rows.size)
        assertEquals(listOf("animals", "animals::fruitbat"), sorted.tags)
    }

    @Test
    fun previewsDraftsAndEdits() {
        val faces = CardPreviews.forDraft(BertNoteTypes.Cloze, mapOf("Text" to "A {{c1::heap}} keeps the {{c2::smallest}} on top"))
        assertEquals(listOf("Blank 1", "Blank 2"), faces.map { it.name })
        assertTrue("[...]" in faces.first().frontHtml)
        assertTrue("heap" in faces.first().backHtml)
        val twoWay = CardPreviews.forDraft(BertNoteTypes.TwoWay, mapOf("Side A" to "queue", "Side B" to "FIFO"))
        assertEquals(2, twoWay.size)
        val packId = createPack()
        val ref = editor.addCards(packId, listOf(basic("What is a stack?", "LIFO"))).added.single().ref
        val edited = editor.previewEdit(packId, ref, mapOf("Answer" to "Last in"))
        assertTrue("Last in" in edited.single().backHtml)
        assertEquals("LIFO", editor.editableCard(packId, ref).values["Answer"])
    }

    @Test
    fun plansTableImportsWithRowProblems() {
        val packId = createPack()
        val csv = "Question,Answer,Tags\n\"What is a \"\"stack\"\"?\",LIFO,basics\n\"Multi,comma\",FIFO,\nMissing answer,,\n"
        val plan = editor.planTableImport(packId, csv, "basic")
        assertTrue(plan.hasHeader)
        assertEquals(3, plan.rows.size)
        assertEquals("What is a \"stack\"?", plan.rows[0].draft.fields["Question"])
        assertEquals(listOf("basics"), plan.rows[0].draft.tags)
        assertEquals("Multi,comma", plan.rows[1].draft.fields["Question"])
        assertEquals(4, plan.invalidRows.single().rowNumber)
        assertEquals("Answer", plan.invalidRows.single().problems.single().field)
        editor.addCards(packId, plan.validDrafts)
        assertEquals(2, editor.queryCards(packId, CardQuery()).total)
        val pasted = TableImport.plan(TableImport.parse("Paris\tFrance\nRome\tItaly"), BertNoteTypes.Basic)
        assertFalse(pasted.hasHeader)
        assertEquals('\t', TableImport.parse("a\tb").delimiter)
        assertEquals(mapOf("Question" to "Rome", "Answer" to "Italy"), pasted.rows[1].draft.fields)
    }
}
