package app.bertsatlas.domain.store

import app.bertsatlas.anki.collection.AnkiCollection
import app.bertsatlas.domain.editor.LibraryFiles
import app.bertsatlas.domain.pack.PackLibraryLayout
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.io.files.SystemTemporaryDirectory
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class StoreLiveServerTest {
    private val root = Path(SystemTemporaryDirectory, "store-live-test-${Random.nextLong().toULong()}").toString()

    @AfterTest
    fun removeFiles() = LibraryFiles.removeRecursively(root)

    @Test
    fun installsAndUpdatesFromALocalStoreServer() = runTest(timeout = 2.minutes) {
        if (!LibraryFiles.exists(BASE_URL_FILE)) return@runTest
        val baseUrl = LibraryFiles.readText(BASE_URL_FILE).trim()
        val library = "$root/library"
        val client = StoreClient(platformStoreTransport(), StoreCache("$root/cache"), library, "$root/work", appVersion = "1.0.0", baseUrl = baseUrl)

        val load = client.loadIndex()
        assertEquals(IndexSource.Network, load.source, load.failure?.message)
        val index = load.index!!
        assertTrue(index.packs.isNotEmpty())

        index.packs.forEach { pack ->
            val stages = mutableSetOf<DownloadStage>()
            var lastReceived = 0L
            val result = client.install(pack) { progress ->
                stages += progress.stage
                if (progress.stage == DownloadStage.Downloading) lastReceived = progress.completed
            }
            assertEquals(pack.packId, result.packId)
            assertEquals(pack.sizeBytes, lastReceived)
            assertTrue(DownloadStage.Verifying in stages)
            AnkiCollection.open(PackLibraryLayout.collectionPath(library, pack.packId)).use { collection ->
                assertEquals(pack.cardCount, collection.cards().size.toLong())
            }
            assertEquals(PackAvailability.UpToDate(pack.version), client.availability(pack))
        }

        val tampered = index.packs.first().let { it.copy(packId = it.packId, sha256 = it.sha256.reversed()) }
        LibraryFiles.removeRecursively(PackLibraryLayout.packDirectory(library, tampered.packId))
        assertEquals(StoreFailureKind.Tampered, assertFailsWith<StoreException> { client.install(tampered) }.kind)
        assertTrue(!LibraryFiles.exists(PackLibraryLayout.packDirectory(library, tampered.packId)))

        val second = index.packs.last()
        val mislabelled = second.copy(version = second.version + ".1")
        assertEquals(StoreFailureKind.InvalidPack, assertFailsWith<StoreException> { client.update(mislabelled) }.kind)
        assertEquals(PackAvailability.UpToDate(second.version), client.availability(second))
        AnkiCollection.open(PackLibraryLayout.collectionPath(library, second.packId)).use { collection ->
            assertEquals(second.cardCount, collection.cards().size.toLong())
        }

        val missing = second.copy(url = "packs/does-not-exist.apkg")
        LibraryFiles.removeRecursively(PackLibraryLayout.packDirectory(library, missing.packId))
        assertEquals(StoreFailureKind.HttpStatus, assertFailsWith<StoreException> { client.install(missing) }.kind)

        val offline = StoreClient(platformStoreTransport(), StoreCache("$root/cache"), library, "$root/work", appVersion = "1.0.0", baseUrl = "http://127.0.0.1:9")
        val cached = offline.loadIndex()
        assertEquals(IndexSource.Cache, cached.source)
        assertEquals(StoreFailureKind.Network, cached.failure?.kind)
        assertEquals(index, cached.index)
    }

    private companion object {
        const val BASE_URL_FILE = "/tmp/m5core/store-base-url"
    }
}
