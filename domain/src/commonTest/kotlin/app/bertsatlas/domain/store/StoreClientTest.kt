package app.bertsatlas.domain.store

import app.bertsatlas.anki.collection.AnkiCollection
import app.bertsatlas.anki.io.Sha256
import app.bertsatlas.anki.model.RevlogEntry
import app.bertsatlas.anki.pkg.PackageImport
import app.bertsatlas.domain.editor.LibraryFiles
import app.bertsatlas.domain.fsrs.CardScheduler
import app.bertsatlas.domain.pack.BertTags
import app.bertsatlas.domain.pack.ExportFormat
import app.bertsatlas.domain.pack.ManifestReading
import app.bertsatlas.domain.pack.PackExporter
import app.bertsatlas.domain.pack.PackLibraryLayout
import app.bertsatlas.domain.pack.PackManifestCodec
import app.bertsatlas.domain.pack.PackSourceBuilder
import kotlinx.coroutines.test.runTest
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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoreClientTest {
    private val root = Path(SystemTemporaryDirectory, "store-client-test-${Random.nextLong().toULong()}").toString()
    private val server = "$root/server"
    private val library = "$root/library"
    private val transport = DirectoryTransport(server)
    private val client = StoreClient(transport, StoreCache("$root/cache"), library, "$root/work", appVersion = "1.0.0", baseUrl = BASE_URL)

    @AfterTest
    fun removeFiles() = LibraryFiles.removeRecursively(root)

    @Test
    fun installsAVerifiedPackAsOfficial() = runTest {
        val pack = publish("1", cards = mapOf("a" to "Stack", "b" to "Queue", "c" to "Heap"))
        writeIndex(pack)
        val index = assertNotNull(client.loadIndex().index)
        val progress = mutableListOf<DownloadProgress>()
        val result = client.install(index.pack(PACK_ID)!!) { progress += it }
        assertEquals(3, result.addedNotes)
        assertTrue(progress.any { it.stage == DownloadStage.Downloading } && progress.any { it.stage == DownloadStage.Verifying })
        assertEquals(PackAvailability.UpToDate("1"), client.availability(index.pack(PACK_ID)!!))
        AnkiCollection.open(PackLibraryLayout.collectionPath(library, PACK_ID)).use { collection ->
            assertTrue(manifestOf(collection).official)
            assertEquals(3, collection.notes().size)
        }
        assertEquals(emptyList(), LibraryFiles.list("$root/work/downloads"))
        assertEquals(StoreFailureKind.AlreadyInstalled, assertFailsWith<StoreException> { client.install(index.pack(PACK_ID)!!) }.kind)
    }

    @Test
    fun rejectsATamperedDownloadAndLeavesNothingBehind() = runTest {
        val pack = publish("1", cards = mapOf("a" to "Stack"))
        writeIndex(pack.copy(sha256 = "0".repeat(64)))
        val index = assertNotNull(client.loadIndex().index)
        val failure = assertFailsWith<StoreException> { client.install(index.pack(PACK_ID)!!) }
        assertEquals(StoreFailureKind.Tampered, failure.kind)
        assertTrue("checksum" in failure.message.orEmpty())
        assertFalse(LibraryFiles.exists(PackLibraryLayout.packDirectory(library, PACK_ID)))
        assertEquals(emptyList(), LibraryFiles.list("$root/work/downloads"))

        writeIndex(pack.copy(sizeBytes = pack.sizeBytes + 1))
        assertEquals(StoreFailureKind.Tampered, assertFailsWith<StoreException> { client.install(client.loadIndex().index!!.pack(PACK_ID)!!) }.kind)
    }

    @Test
    fun updateKeepsProgressAndRetiresRemovedCards() = runTest {
        writeIndex(publish("1", cards = mapOf("a" to "Stack", "b" to "Queue", "c" to "Heap")))
        client.install(client.loadIndex().index!!.pack(PACK_ID)!!)
        val reviewedCardId = AnkiCollection.open(PackLibraryLayout.collectionPath(library, PACK_ID)).use { collection ->
            val card = collection.cardsOfNote(collection.noteByGuid(guid("b"))!!.id).single()
            collection.saveCard(card.copy(scheduling = card.scheduling.copy(type = CardScheduler.TYPE_REVIEW, queue = CardScheduler.QUEUE_REVIEW, due = 30, interval = 12, reps = 4), modifiedSecs = 1))
            collection.addRevlogEntry(RevlogEntry(id = 1_700_000_000_000, cardId = card.id, ease = 3, interval = 12, lastInterval = 4, easeFactor = 2500, timeMs = 4000, kind = 1))
            card.id
        }

        writeIndex(publish("2", cards = mapOf("a" to "Stack (LIFO)", "b" to "Queue", "d" to "Trie")))
        val index = client.loadIndex().index!!
        assertEquals(listOf(PackUpdate(index.pack(PACK_ID)!!, "1")), client.availableUpdates(index))
        val result = client.update(index.pack(PACK_ID)!!)
        assertEquals("1", result.previousVersion)
        assertEquals(1, result.addedNotes)
        assertEquals(1, result.retiredNotes)

        AnkiCollection.open(PackLibraryLayout.collectionPath(library, PACK_ID)).use { collection ->
            assertEquals("2", manifestOf(collection).version)
            assertTrue(manifestOf(collection).official)
            assertEquals("Stack (LIFO)", collection.noteByGuid(guid("a"))!!.fields[0])
            val reviewed = collection.card(reviewedCardId)!!
            assertEquals(CardScheduler.QUEUE_REVIEW, reviewed.scheduling.queue)
            assertEquals(12, reviewed.scheduling.interval)
            assertEquals(1, collection.revlogOfCard(reviewedCardId).size)
            val retired = collection.noteByGuid(guid("c"))!!
            assertTrue(BertTags.RETIRED in retired.tags)
            assertTrue(collection.cardsOfNote(retired.id).all { it.scheduling.queue == CardScheduler.QUEUE_SUSPENDED })
            assertNotNull(collection.noteByGuid(guid("d")))
        }

        writeIndex(publish("3", cards = mapOf("a" to "Stack (LIFO)", "b" to "Queue", "c" to "Heap", "d" to "Trie")))
        val restored = client.update(client.loadIndex().index!!.pack(PACK_ID)!!)
        assertEquals(1, restored.restoredNotes)
        assertEquals(0, restored.retiredNotes)
        AnkiCollection.open(PackLibraryLayout.collectionPath(library, PACK_ID)).use { collection ->
            val note = collection.noteByGuid(guid("c"))!!
            assertFalse(BertTags.RETIRED in note.tags)
            assertTrue(collection.cardsOfNote(note.id).all { it.scheduling.queue == CardScheduler.QUEUE_NEW })
        }
        assertEquals(StoreFailureKind.AlreadyInstalled, assertFailsWith<StoreException> { client.update(client.loadIndex().index!!.pack(PACK_ID)!!) }.kind)
    }

    @Test
    fun fallsBackToTheCachedIndexWhenOffline() = runTest {
        val offline = client.loadIndex()
        assertNull(offline.index)
        assertNotNull(offline.failure)

        writeIndex(publish("1", cards = mapOf("a" to "Stack")))
        assertEquals(IndexSource.Network, client.loadIndex().source)
        transport.offline = true
        val cached = client.loadIndex()
        assertEquals(IndexSource.Cache, cached.source)
        assertEquals(StoreFailureKind.Network, cached.failure?.kind)
        assertEquals(PACK_ID, cached.index?.packs?.single()?.packId)
        assertEquals(cached.index?.taxonomy(), client.taxonomy())
    }

    @Test
    fun refusesPacksThatNeedANewerApp() = runTest {
        writeIndex(publish("1", cards = mapOf("a" to "Stack")).copy(minAppVersion = "9.0.0"))
        val pack = client.loadIndex().index!!.pack(PACK_ID)!!
        assertIs<PackAvailability.NeedsNewerApp>(client.availability(pack))
        assertEquals(StoreFailureKind.Unsupported, assertFailsWith<StoreException> { client.install(pack) }.kind)
    }

    @Test
    fun exportsWithAndWithoutProgress() = runTest {
        writeIndex(publish("1", cards = mapOf("a" to "Stack", "b" to "Queue")))
        client.install(client.loadIndex().index!!.pack(PACK_ID)!!)
        AnkiCollection.open(PackLibraryLayout.collectionPath(library, PACK_ID)).use { collection ->
            val card = collection.cards().first()
            collection.addRevlogEntry(RevlogEntry(id = 1_700_000_000_000, cardId = card.id, ease = 3, interval = 1, lastInterval = 0, easeFactor = 2500, timeMs = 3000, kind = 0))
        }
        val exporter = PackExporter(library, "$root/work")
        val withProgress = exporter.export(PACK_ID, "$root/out", ExportFormat.Modern)
        assertEquals("Store Test Pack.apkg", withProgress.fileName)
        assertEquals(1, revlogCount(withProgress.path))
        val legacy = exporter.export(PACK_ID, "$root/out-legacy", ExportFormat.CompatibleWithOlderAnki, includeProgress = false)
        assertEquals(0, revlogCount(legacy.path))
        assertTrue(legacy.sizeBytes > 0)
    }

    private fun revlogCount(path: String): Int =
        PackageImport.open(path, "$root/work/read-${Random.nextInt().toUInt()}").use { it.collection.revlog().size }

    private fun manifestOf(collection: AnkiCollection) = collection.decks().filter { it.isTopLevel }.firstNotNullOf {
        (PackManifestCodec.readDeckDescription(it.description) as? ManifestReading.Found)?.manifest
    }

    private fun guid(key: String) = "store-test-$key"

    private fun publish(version: String, cards: Map<String, String>): StorePack {
        val source = "$root/source-$version"
        writeFile(
            "$source/pack.json",
            """{"packId":"$PACK_ID","version":"$version","official":true,"title":"Store Test Pack","subtitle":"Fixture",
               "category":"programming","subcategory":"dsa","accentColor":"#3E5C8A"}""",
        )
        val cardJson = cards.entries.joinToString(",") { (key, question) ->
            """{"guid":"${guid(key)}","noteType":"Bert Basic","fields":{"Question":"$question","Answer":"answer $key","Aliases":"","Hint":"","Fact":"","Source":"","BertMeta":""},"tags":[]}"""
        }
        writeFile("$source/cards.json", """{"cards":[$cardJson]}""")
        val key = StoreUrls.packKey(PACK_ID, version)
        val output = "$server/$key"
        LibraryFiles.createDirectories(Path(output).parent.toString())
        PackSourceBuilder.build(source, "$root/build-$version", output)
        return StorePack(
            packId = PACK_ID,
            version = version,
            title = "Store Test Pack",
            category = "programming",
            subcategory = "dsa",
            cardCount = cards.size.toLong(),
            sizeBytes = LibraryFiles.size(output),
            sha256 = Sha256.hexOfFile(output),
            url = key,
        )
    }

    private fun writeIndex(pack: StorePack) {
        val index = StoreIndex(categories = listOf(StoreCategory("programming", "Programming", subcategories = listOf(StoreSubcategory("dsa", "DSA")))), packs = listOf(pack))
        writeFile("$server/${StoreUrls.INDEX_KEY}", StoreIndexCodec.encode(index))
    }

    private fun writeFile(path: String, text: String) {
        Path(path).parent?.let { SystemFileSystem.createDirectories(it) }
        SystemFileSystem.sink(Path(path)).buffered().use { it.writeString(text) }
    }

    private class DirectoryTransport(private val directory: String) : StoreTransport {
        var offline = false

        override suspend fun fetchText(url: String): String = LibraryFiles.readText(fileFor(url))

        override suspend fun download(url: String, destinationPath: String, onBytes: (received: Long, total: Long?) -> Unit) {
            val file = fileFor(url)
            LibraryFiles.copy(file, destinationPath)
            onBytes(LibraryFiles.size(file), LibraryFiles.size(file))
        }

        private fun fileFor(url: String): String {
            if (offline) throw StoreException(StoreFailureKind.Network, "offline")
            val file = "$directory/${url.removePrefix("$BASE_URL/")}"
            if (!LibraryFiles.exists(file)) throw StoreException(StoreFailureKind.HttpStatus, "$url answered 404")
            return file
        }
    }

    private companion object {
        const val PACK_ID = "store-test"
        const val BASE_URL = "https://store.test"
    }
}
