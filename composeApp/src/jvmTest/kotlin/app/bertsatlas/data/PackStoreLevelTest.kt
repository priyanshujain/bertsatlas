package app.bertsatlas.data

import app.bertsatlas.domain.pack.BertNoteTypes
import app.bertsatlas.domain.pack.LevelSetting
import app.bertsatlas.domain.pack.OfficialPackInstaller
import app.bertsatlas.domain.pack.PackSourceBuilder
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PackStoreLevelTest {
    private val root = kotlin.io.path.createTempDirectory("bert-store-").toFile()
    private val library = File(root, "library").path

    @AfterTest
    fun removeTemporaryFiles() {
        root.deleteRecursively()
    }

    @Test
    fun levelFiltersSummaryPlayAndReviewWhileUntaggedCardsStay() = runBlocking {
        installPack(
            "mid" to listOf("bert::level::mid"),
            "senior" to listOf("bert::level::senior"),
            "both" to listOf("bert::level::mid", "bert::level::senior"),
            "untagged" to emptyList(),
        )
        val store = PackStore(library)
        val all = store.summaries().single()
        assertEquals(LevelSetting.All, all.level)
        assertEquals(listOf(LevelSetting.Mid, LevelSetting.Senior), all.levels)
        assertEquals(4, all.cardCount)
        assertEquals(setOf("mid", "senior", "both", "untagged"), playedGuids(store))

        store.chooseLevel(PACK_ID, LevelSetting.Mid)
        val mid = store.summaries().single()
        assertEquals(LevelSetting.Mid, mid.level)
        assertEquals(3, mid.cardCount)
        assertEquals(3, store.dueCounts(PACK_ID, null).newCards)
        assertEquals(setOf("mid", "both", "untagged"), playedGuids(store))

        store.chooseLevel(PACK_ID, LevelSetting.Senior)
        assertEquals(setOf("senior", "both", "untagged"), playedGuids(store))

        store.forgetLevels()
        assertEquals(4, store.summaries().single().cardCount)
        assertEquals(4, store.dueCounts(PACK_ID, null).newCards)
    }

    private suspend fun playedGuids(store: PackStore): Set<String> =
        store.catalogFor(PACK_ID, emptyList()).questions.flatMap { it.countryIds }.mapTo(mutableSetOf()) { it.substringBeforeLast(':') }

    private fun installPack(vararg cards: Pair<String, List<String>>) {
        val source = File(root, "source").apply { mkdirs() }
        File(source, PackSourceBuilder.MANIFEST_FILE).writeText(
            """{"packId":"$PACK_ID","version":"1.0.0","official":true,"title":"Interview","accentColor":"#27635B"}""",
        )
        File(source, PackSourceBuilder.CARDS_FILE).writeText(
            cards.joinToString(",", prefix = """{"cards":[""", postfix = "]}") { (guid, tags) ->
                val fields = (BertNoteTypes.Basic.fields.associateWith { "" } + mapOf("Question" to "Question $guid", "Answer" to "Answer $guid"))
                    .entries.joinToString(",") { (name, value) -> "${JsonPrimitive(name)}:${JsonPrimitive(value)}" }
                """{"guid":"$guid","noteType":"${BertNoteTypes.Basic.name}","fields":{$fields},"tags":[${tags.joinToString(",") { JsonPrimitive(it).toString() }}]}"""
            },
        )
        val output = File(root, "$PACK_ID.apkg").path
        PackSourceBuilder.build(source.path, File(root, "build").path, output)
        OfficialPackInstaller(library, File(root, "work").path).install(output)
    }

    private companion object {
        const val PACK_ID = "interview"
    }
}
