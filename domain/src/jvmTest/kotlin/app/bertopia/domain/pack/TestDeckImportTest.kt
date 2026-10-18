package app.bertopia.domain.pack

import app.bertopia.anki.collection.AnkiCollection
import app.bertopia.anki.pkg.ImportCancelledException
import app.bertopia.anki.pkg.ImportMonitor
import app.bertopia.anki.pkg.ImportStage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestDeckImportTest {
    private val decks = File("/tmp/ankidecks")
    private val reference = File(decks, "reference.json")

    @Test
    fun everyTestDeckImportsWithAnkiCounts() {
        if (!reference.exists()) return
        val expected = Json.parseToJsonElement(reference.readText()).jsonObject
        val report = StringBuilder()
        var failures = 0
        expected.forEach { (packagePath, packageReference) ->
            val root = createTempDirectory()
            val importer = PackImporter("$root/library", "$root/work")
            val runtime = Runtime.getRuntime()
            System.gc()
            val baseline = runtime.totalMemory() - runtime.freeMemory()
            var peak = baseline
            val monitor = ImportMonitor(onProgress = { peak = maxOf(peak, runtime.totalMemory() - runtime.freeMemory()) })
            val started = System.nanoTime()
            val expectedDecks = packageReference.jsonObject.getValue("decks").jsonObject
            val outcome = runCatching {
                importer.plan(packagePath, monitor).use { plan ->
                    val selections = plan.decks.map { DeckSelection(it.deckId, it.title, "test", "decks") }
                    val imported = importer.import(plan, selections, monitor)
                    plan.decks.zip(imported).map { (deck, pack) ->
                        val counts = AnkiCollection.open(PackLibraryLayout.collectionPath("$root/library", pack.packId)).use { collection ->
                            Triple(collection.noteCount(), collection.cards().size.toLong(), collection.revlog().size.toLong())
                        }
                        Triple(deck, pack, counts)
                    }
                }
            }
            val seconds = (System.nanoTime() - started) / 1e9
            val name = packagePath.removePrefix("/tmp/ankidecks/")
            outcome.onFailure {
                failures++
                report.appendLine("FAIL $name: ${it::class.simpleName} ${it.message}")
            }
            outcome.onSuccess { results ->
                val ours = results.associate { (deck, _, counts) -> deck.title to counts }
                val mismatches = mutableListOf<String>()
                if (ours.keys != expectedDecks.keys) mismatches += "decks ${ours.keys} vs ${expectedDecks.keys}"
                results.forEach { (deck, pack, counts) ->
                    val want = expectedDecks[deck.title]?.jsonObject ?: return@forEach
                    val wantCounts = Triple(want.getValue("notes").jsonPrimitive.int.toLong(), want.getValue("cards").jsonPrimitive.int.toLong(), want.getValue("revlog").jsonPrimitive.int.toLong())
                    if (counts != wantCounts) mismatches += "${deck.title} notes/cards/revlog $counts vs $wantCounts"
                    val wantSections = want.getValue("sections").jsonArray.map { it.jsonPrimitive.content }
                    if (deck.sections.sorted() != wantSections) mismatches += "${deck.title} sections ${deck.sections} vs $wantSections"
                    if (deck.preview == null) mismatches += "${deck.title} has no preview"
                    report.appendLine(
                        "  $name [${deck.title}] notes=${counts.first} cards=${counts.second} revlog=${counts.third} media=${pack.mediaFiles} sections=${deck.sections.size}",
                    )
                }
                if (mismatches.isNotEmpty()) failures++
                val ankiSeconds = packageReference.jsonObject.getValue("seconds").jsonPrimitive.content
                report.appendLine(
                    "${if (mismatches.isEmpty()) "OK  " else "FAIL"} $name ${"%.1f".format(seconds)}s (anki ${ankiSeconds}s) peakHeap=${(peak - baseline) / 1_000_000}MB ${mismatches.joinToString("; ")}",
                )
            }
            File(root).deleteRecursively()
        }
        File(decks, "kotlin-import-report.txt").writeText(report.toString())
        println(report)
        assertEquals(0, failures, report.toString())
    }

    @Test
    fun reimportMergesIntoTheSamePackKeepingProgress() {
        val deck = File(decks, "generated/languages.apkg")
        if (!deck.exists()) return
        val root = createTempDirectory()
        val importer = PackImporter("$root/library", "$root/work")
        val first = importer.plan(deck.path).use { plan ->
            importer.import(plan, plan.decks.map { DeckSelection(it.deckId, it.title, "languages", "spanish") }).single()
        }
        val collectionPath = PackLibraryLayout.collectionPath("$root/library", first.packId)
        val reviewedBefore = AnkiCollection.open(collectionPath).use { it.revlog().size }
        val legacy = File(decks, "generated/languages.legacy.apkg")
        val second = importer.plan(legacy.path).use { plan ->
            assertEquals(first.packId, plan.decks.single().existingPack?.packId)
            importer.import(plan, plan.decks.map { DeckSelection(it.deckId, it.title, "languages", "spanish") }).single()
        }
        assertTrue(second.mergedIntoExisting)
        assertEquals(0, second.addedNotes)
        assertEquals(first.addedNotes, second.updatedNotes)
        AnkiCollection.open(collectionPath).use { collection ->
            assertEquals(first.addedNotes.toLong(), collection.noteCount())
            assertEquals(reviewedBefore, collection.revlog().size)
            val manifest = collection.decks().firstNotNullOf { (PackManifestCodec.readDeckDescription(it.description) as? ManifestReading.Found)?.manifest }
            assertEquals(first.packId, manifest.packId)
            assertFalse(manifest.official)
            assertEquals("languages", manifest.category)
        }
        assertEquals(listOf(first.packId), importer.installedPacks().map { it.packId })
        File(root).deleteRecursively()
    }

    @Test
    fun cancellingALargeImportLeavesNoPackBehind() {
        val deck = File(decks, "generated/heavymedia.apkg")
        if (!deck.exists()) return
        val root = createTempDirectory()
        val importer = PackImporter("$root/library", "$root/work")
        var mediaCopied = 0L
        val monitor = ImportMonitor(
            onProgress = { if (it.stage == ImportStage.CopyingMedia) mediaCopied = it.completed },
            isCancelled = { mediaCopied >= 50 },
        )
        importer.plan(deck.path).use { plan ->
            assertFailsWith<ImportCancelledException> {
                importer.import(plan, plan.decks.map { DeckSelection(it.deckId, it.title, "test", "decks") }, monitor)
            }
        }
        assertTrue(importer.installedPacks().isEmpty())
        assertFalse(File("$root/library").listFiles().orEmpty().any())
        assertTrue(File("$root/work").listFiles().orEmpty().none { it.name.startsWith("staging-") || it.name.endsWith(".anki2") })
        File(root).deleteRecursively()
    }

    private fun createTempDirectory(): String = kotlin.io.path.createTempDirectory("bert-import-").toString()
}
