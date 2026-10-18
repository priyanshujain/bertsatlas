package app.bertopia.domain.editor

import kotlinx.io.files.Path
import kotlinx.io.files.SystemTemporaryDirectory
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.measureTimedValue

class CardTablePerformanceTest {
    private val root = Path(SystemTemporaryDirectory, "card-table-perf-${Random.nextLong().toULong()}").toString()

    @AfterTest
    fun removeFiles() = LibraryFiles.removeRecursively(root)

    @Test
    fun queriesThirtyThousandCardsQuickly() {
        val editor = PackEditor(PackWorkspace("$root/library", "$root/work"))
        val packId = editor.createPack(PackDraft("Big", category = "languages", subcategory = "vocabulary"))
        val drafts = (1..30_000).map { CardDraft("basic", mapOf("Question" to "word number $it", "Answer" to "meaning $it"), listOf("set${it % 10}"), "Part ${it % 5}") }
        val (_, addTime) = measureTimedValue { editor.addCards(packId, drafts) }
        val query = CardQuery(search = "number 2999", tag = "set9", sort = CardSort.Alphabetical, limit = 100)
        editor.queryCards(packId, query)
        val (page, queryTime) = measureTimedValue { editor.queryCards(packId, query) }
        val (all, browseTime) = measureTimedValue { editor.queryCards(packId, CardQuery(sort = CardSort.Modified, descending = true, limit = 200)) }
        println("add 30000: $addTime, filtered query: $queryTime, browse first 200: $browseTime")
        assertEquals(listOf("word number 2999", "word number 29999"), page.rows.map { it.preview })
        assertEquals(30_000, all.total)
        assertTrue(queryTime.inWholeMilliseconds < 1_000, "query took $queryTime")
        assertTrue(browseTime.inWholeMilliseconds < 1_000, "browse took $browseTime")
    }
}
