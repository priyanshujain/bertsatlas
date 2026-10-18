package app.bertsatlas.cli

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommandsTest {
    private val root = Path(SystemTemporaryDirectory, "bert-cli-test-${Random.nextLong().toULong()}").toString()
    private val library = "$root/library"
    private var standardInput = ""

    private val bert = Bert(
        Environment(
            homeDirectory = root,
            currentDirectory = root,
            temporaryDirectory = "$root/tmp",
            libraryOverride = library,
            readStandardInput = { standardInput },
            nowMillis = { 1_800_000_000_000L },
        ),
    )

    @AfterTest
    fun removeTemporaryFiles() = Files.removeRecursively(root)

    private fun run(vararg arguments: String, input: String = ""): Pair<ExitCode, JsonObject> {
        standardInput = input
        val result = bert.run(arguments.toList() + "--json")
        return result.exitCode to Json.parseToJsonElement(result.stdout).jsonObject
    }

    private fun createPack(): String {
        val (exit, pack) = run("packs", "create", "--title", "Data Structures", "--category", "programming", "--subcategory", "dsa")
        assertEquals(ExitCode.Ok, exit)
        return pack.getValue("packId").jsonPrimitive.content
    }

    @Test
    fun createsListsAndRenamesPacks() {
        val packId = createPack()
        assertEquals("data-structures", packId)
        assertEquals(ExitCode.Conflict, run("packs", "create", "--title", "Other", "--id", packId, "--category", "a", "--subcategory", "b").first)
        val (_, listed) = run("packs", "list")
        assertEquals(listOf(packId), listed.getValue("packs").jsonArray.map { it.jsonObject.getValue("packId").jsonPrimitive.content })
        val (exit, renamed) = run("packs", "rename", packId, "--title", "DSA")
        assertEquals(ExitCode.Ok, exit)
        assertEquals("DSA", renamed.getValue("title").jsonPrimitive.content)
    }

    @Test
    fun addsCardsInOneBatchWithSections() {
        val packId = createPack()
        val cards = (1..50).joinToString(",", "[", "]") { index ->
            """{"style":"basic","section":"Arrays","fields":{"Question":"Q$index","Answer":"A$index"},"tags":["dsa::arrays"]}"""
        }
        val (exit, added) = run("cards", "add", packId, input = cards)
        assertEquals(ExitCode.Ok, exit)
        assertEquals(50, added.getValue("added").jsonPrimitive.long.toInt())
        val (_, listed) = run("cards", "list", packId, "--section", "Arrays")
        assertEquals(50, listed.getValue("count").jsonPrimitive.long.toInt())
        val (_, stats) = run("stats", packId)
        assertEquals(50, stats.getValue("cards").jsonPrimitive.long.toInt())
    }

    @Test
    fun rejectsTheWholeBatchWhenOneCardIsInvalid() {
        val packId = createPack()
        val cards = """[{"style":"basic","fields":{"Question":"Q","Answer":"A"}},{"style":"basic","fields":{"Question":"Only a question"}}]"""
        val (exit, failure) = run("cards", "add", packId, input = cards)
        assertEquals(ExitCode.Invalid, exit)
        assertEquals("invalid", failure.getValue("error").jsonObject.getValue("code").jsonPrimitive.content)
        val (_, listed) = run("cards", "list", packId)
        assertEquals(0, listed.getValue("count").jsonPrimitive.long.toInt())
    }

    @Test
    fun rejectsUnknownStylesAndFields() {
        val packId = createPack()
        assertEquals(ExitCode.Invalid, run("cards", "add", packId, input = """{"style":"poem","fields":{}}""").first)
        assertEquals(ExitCode.Invalid, run("cards", "add", packId, input = """{"style":"basic","fields":{"Question":"Q","Answer":"A","Colour":"red"}}""").first)
        assertEquals(ExitCode.Invalid, run("cards", "add", packId, input = "not json").first)
    }

    @Test
    fun editsTagsMovesAndDeletesNotes() {
        val packId = createPack()
        val (_, added) = run("cards", "add", packId, input = """{"style":"cloze","fields":{"Text":"{{c1::Stacks}} are LIFO"}}""")
        val noteId = added.getValue("notes").jsonArray.single().jsonObject.getValue("noteId").jsonPrimitive.content
        val (editExit, edited) = run("cards", "edit", packId, noteId, input = """{"fields":{"Text":"{{c1::Stacks}} are LIFO, {{c2::queues}} FIFO"}}""")
        assertEquals(ExitCode.Ok, editExit)
        assertEquals(2, edited.getValue("cardCount").jsonPrimitive.long.toInt())
        assertEquals(ExitCode.Ok, run("cards", "tag", packId, noteId, "--add", "core").first)
        assertEquals(ExitCode.Ok, run("cards", "move", packId, noteId, "--section", "Linear").first)
        val (_, shown) = run("cards", "show", packId, noteId)
        assertEquals("Linear", shown.getValue("section").jsonPrimitive.content)
        assertEquals(listOf("core"), shown.getValue("tags").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(ExitCode.Ok, run("cards", "delete", packId, noteId).first)
        assertEquals(ExitCode.NotFound, run("cards", "show", packId, noteId).first)
    }

    @Test
    fun refusesToEditOfficialPacksButCopiesThem() {
        val packId = createPack()
        Library(library, "$root/tmp").open(packId).use { pack -> pack.collection.transaction { pack.saveManifest(pack.manifest.copy(official = true)) } }
        assertEquals(ExitCode.ReadOnly, run("cards", "add", packId, input = """{"style":"basic","fields":{"Question":"Q","Answer":"A"}}""").first)
        val (exit, copy) = run("packs", "copy", packId)
        assertEquals(ExitCode.Ok, exit)
        val copyId = copy.getValue("packId").jsonPrimitive.content
        assertEquals(false, copy.getValue("official").jsonPrimitive.content.toBoolean())
        assertEquals(ExitCode.Ok, run("cards", "add", copyId, input = """{"style":"basic","fields":{"Question":"Q","Answer":"A"}}""").first)
    }

    @Test
    fun validatesAndExportsPacks() {
        val packId = createPack()
        run("cards", "add", packId, input = """{"style":"typed","fields":{"Question":"Big O of push?","Answer":"O(1)"}}""")
        val (validExit, report) = run("validate", packId)
        assertEquals(ExitCode.Ok, validExit)
        assertEquals(true, report.getValue("valid").jsonPrimitive.content.toBoolean())
        val (exportExit, exported) = run("export", packId, "--out", "$root/out.apkg", "--legacy")
        assertEquals(ExitCode.Ok, exportExit)
        assertTrue(exported.getValue("bytes").jsonPrimitive.long > 0)
        assertEquals(ExitCode.Ok, run("validate", "$root/out.apkg").first)
    }

    @Test
    fun describesEveryCardStyle() {
        val (_, styles) = run("styles", "list")
        assertEquals(9, (styles.getValue("styles") as JsonArray).size)
        val (exit, schema) = run("styles", "schema", "two-way")
        assertEquals(ExitCode.Ok, exit)
        assertEquals("object", schema.getValue("type").jsonPrimitive.content)
        assertEquals(ExitCode.Invalid, run("styles", "schema", "poem").first)
    }

    @Test
    fun describesTheChoiceStyleSchema() {
        val (exit, schema) = run("styles", "schema", "choice")
        assertEquals(ExitCode.Ok, exit)
        assertEquals("Bert Choice card", schema.getValue("title").jsonPrimitive.content)
        val fields = schema.getValue("properties").jsonObject.getValue("fields").jsonObject
        assertEquals(listOf("Question", "Choices", "Correct"), fields.getValue("required").jsonArray.map { it.jsonPrimitive.content })
        val properties = fields.getValue("properties").jsonObject
        assertEquals(listOf("Question", "Choices", "Correct", "Explanation", "Hint", "Source", "BertMeta"), properties.keys.toList())
        assertTrue("2 to 5" in properties.getValue("Choices").jsonObject.getValue("description").jsonPrimitive.content)
        assertTrue("flipOnly" in properties.getValue("BertMeta").jsonObject.getValue("description").jsonPrimitive.content)
    }

    @Test
    fun reportsMissingPacksAndBadUsage() {
        assertEquals(ExitCode.NotFound, run("packs", "show", "nope").first)
        assertEquals(ExitCode.Usage, run("frobnicate").first)
        val packId = createPack()
        assertEquals(ExitCode.Usage, run("packs", "delete", packId).first)
        assertEquals(ExitCode.Ok, run("packs", "delete", packId, "--yes").first)
        assertTrue(!SystemFileSystem.exists(Path(library, packId)))
    }
}
