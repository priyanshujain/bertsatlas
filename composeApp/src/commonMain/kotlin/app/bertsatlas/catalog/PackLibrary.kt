package app.bertsatlas.catalog

import app.bertsatlas.anki.collection.AnkiCollection
import app.bertsatlas.anki.pkg.PackageImport
import app.bertsatlas.domain.geography.GeographyCatalog
import app.bertsatlas.domain.pack.OfficialPackInstaller
import app.bertsatlas.domain.store.PackVersions
import app.bertsatlas.domain.geography.GeographyPack
import app.bertsatlas.domain.geography.GeographyPackReader
import app.bertsatlas.domain.model.BuiltInPacks
import app.bertsatlas.domain.model.Catalog
import app.bertsatlas.domain.pack.PackStamp
import berts_atlas.composeapp.generated.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

class InstalledLibrary(
    val catalog: Catalog,
    val flagMediaDirectory: String,
    val geographyStamps: List<PackStamp>,
)

class PackLibrary(private val libraryDirectory: String) {
    suspend fun open(): InstalledLibrary {
        withContext(Dispatchers.Default) { BUNDLED_PACK_IDS.forEach { installBundledPackIfNewer(it) } }
        return readInstalled()
    }

    suspend fun readInstalled(): InstalledLibrary = withContext(Dispatchers.Default) {
        val flags = openInstalled(BuiltInPacks.FLAGS)
        val capitals = openInstalled(BuiltInPacks.CAPITALS)
        try {
            InstalledLibrary(
                catalog = GeographyCatalog.build(GeographyPackReader.read(flags, capitals)),
                flagMediaDirectory = mediaDirectory(BuiltInPacks.FLAGS),
                geographyStamps = flags.manifest.stamps,
            )
        } finally {
            flags.collection.close()
            capitals.collection.close()
        }
    }

    private suspend fun installBundledPackIfNewer(packId: String) {
        val bundledBytes = Res.readBytes("files/packs/$packId.apkg")
        val workingDirectory = Files.join(libraryDirectory, "incoming")
        Files.createDirectories(workingDirectory)
        val packagePath = Files.join(workingDirectory, "$packId.apkg")
        Files.write(packagePath, bundledBytes)
        try {
            val bundledVersion = PackageImport.open(packagePath, workingDirectory).use { opened ->
                GeographyPackReader.manifestOf(opened.collection)?.version
            } ?: return
            val installed = installedVersion(packId)
            if (installed != null && !PackVersions.isNewer(bundledVersion, installed)) return
            OfficialPackInstaller(libraryDirectory, workingDirectory).install(packagePath, expectedPackId = packId, expectedVersion = bundledVersion)
        } finally {
            Files.delete(packagePath)
        }
    }

    private fun installedVersion(packId: String): String? {
        if (!Files.exists(collectionPath(packId))) return null
        return runCatching {
            AnkiCollection.open(collectionPath(packId)).use { GeographyPackReader.manifestOf(it)?.version }
        }.getOrNull()
    }

    private fun openInstalled(packId: String): GeographyPack {
        val collection = AnkiCollection.open(collectionPath(packId))
        val manifest = requireNotNull(GeographyPackReader.manifestOf(collection)) { "Installed pack $packId has no manifest" }
        return GeographyPack(manifest, collection)
    }

    private object Files {
        fun join(directory: String, name: String) = Path(directory, name).toString()
        fun exists(path: String) = SystemFileSystem.exists(Path(path))
        fun createDirectories(path: String) = SystemFileSystem.createDirectories(Path(path))
        fun delete(path: String) = SystemFileSystem.delete(Path(path), mustExist = false)
        fun write(path: String, bytes: ByteArray) = SystemFileSystem.sink(Path(path)).buffered().use { it.write(bytes) }
    }

    private fun packDirectory(packId: String) = Files.join(libraryDirectory, packId)
    private fun collectionPath(packId: String) = Files.join(packDirectory(packId), COLLECTION_FILE)
    private fun mediaDirectory(packId: String) = Files.join(packDirectory(packId), MEDIA_DIRECTORY)

    private companion object {
        val BUNDLED_PACK_IDS = listOf(BuiltInPacks.FLAGS, BuiltInPacks.CAPITALS)
        const val COLLECTION_FILE = "collection.anki2"
        const val MEDIA_DIRECTORY = "media"
    }
}
