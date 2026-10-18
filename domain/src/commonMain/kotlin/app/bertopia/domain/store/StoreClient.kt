package app.bertopia.domain.store

import app.bertopia.anki.io.Sha256
import app.bertopia.anki.pkg.ImportMonitor
import app.bertopia.domain.editor.LibraryFiles
import app.bertopia.domain.pack.OfficialInstallResult
import app.bertopia.domain.pack.OfficialPackInstaller
import app.bertopia.domain.taxonomy.BundledTaxonomy
import app.bertopia.domain.taxonomy.Taxonomy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString

class StoreCache(private val cacheDirectory: String) {
    private val indexPath get() = Path(cacheDirectory, INDEX_FILE).toString()

    fun readIndex(): StoreIndex? =
        if (!LibraryFiles.exists(indexPath)) null else runCatching { StoreIndexCodec.decode(LibraryFiles.readText(indexPath)) }.getOrNull()

    fun writeIndex(json: String) {
        LibraryFiles.createDirectories(cacheDirectory)
        val temporary = "$indexPath.tmp"
        SystemFileSystem.sink(Path(temporary)).buffered().use { it.writeString(json) }
        LibraryFiles.move(temporary, indexPath)
    }

    fun taxonomy(): Taxonomy = readIndex()?.taxonomy()?.takeIf { it.categories.isNotEmpty() } ?: BundledTaxonomy.taxonomy

    private companion object {
        const val INDEX_FILE = "store-index.json"
    }
}

enum class IndexSource { Network, Cache }

data class IndexLoad(val index: StoreIndex?, val source: IndexSource?, val failure: StoreException?)

sealed interface PackAvailability {
    data object NotInstalled : PackAvailability
    data class UpToDate(val installedVersion: String) : PackAvailability
    data class UpdateAvailable(val installedVersion: String, val availableVersion: String) : PackAvailability
    data class NeedsNewerApp(val minAppVersion: String) : PackAvailability
}

data class PackUpdate(val pack: StorePack, val installedVersion: String)

enum class DownloadStage { Downloading, Verifying, Installing }

data class DownloadProgress(val stage: DownloadStage, val completed: Long, val total: Long?)

class StoreClient(
    private val transport: StoreTransport,
    private val cache: StoreCache,
    private val libraryDirectory: String,
    private val workingDirectory: String,
    private val appVersion: String,
    private val baseUrl: String = StoreUrls.PUBLIC_BASE_URL,
) {
    private val installer = OfficialPackInstaller(libraryDirectory, Path(workingDirectory, "install").toString())

    fun cachedIndex(): StoreIndex? = cache.readIndex()

    fun taxonomy(): Taxonomy = cache.taxonomy()

    suspend fun loadIndex(): IndexLoad {
        val failure = try {
            val text = transport.fetchText(StoreUrls.indexUrl(baseUrl))
            val index = try {
                StoreIndexCodec.decode(text)
            } catch (invalid: Exception) {
                throw StoreException(StoreFailureKind.InvalidIndex, "The store sent something unreadable", invalid)
            }
            cache.writeIndex(text)
            return IndexLoad(index, IndexSource.Network, null)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: StoreException) {
            failure
        } catch (failure: Exception) {
            StoreException(StoreFailureKind.Network, failure.message ?: "Could not reach the store", failure)
        }
        val cached = cache.readIndex()
        return IndexLoad(cached, cached?.let { IndexSource.Cache }, failure)
    }

    fun availability(pack: StorePack): PackAvailability {
        val installedVersion = installer.installedVersion(pack.packId)
        return when {
            installedVersion != null && !PackVersions.isNewer(pack.version, installedVersion) -> PackAvailability.UpToDate(installedVersion)
            PackVersions.isNewer(pack.minAppVersion, appVersion) -> PackAvailability.NeedsNewerApp(pack.minAppVersion)
            installedVersion != null -> PackAvailability.UpdateAvailable(installedVersion, pack.version)
            else -> PackAvailability.NotInstalled
        }
    }

    fun availableUpdates(index: StoreIndex): List<PackUpdate> {
        val installed = installer.installed().associateBy { it.packId }
        return index.packs.mapNotNull { pack ->
            val current = installed[pack.packId] ?: return@mapNotNull null
            if (PackVersions.isNewer(pack.version, current.version) && !PackVersions.isNewer(pack.minAppVersion, appVersion)) PackUpdate(pack, current.version)
            else null
        }
    }

    suspend fun install(pack: StorePack, onProgress: (DownloadProgress) -> Unit = {}): OfficialInstallResult {
        when (val availability = availability(pack)) {
            PackAvailability.NotInstalled -> Unit
            is PackAvailability.NeedsNewerApp -> throw StoreException(StoreFailureKind.Unsupported, "${pack.title} needs a newer version of Bertopia")
            else -> throw StoreException(StoreFailureKind.AlreadyInstalled, "${pack.title} is already installed ($availability)")
        }
        return downloadAndInstall(pack, onProgress)
    }

    suspend fun update(pack: StorePack, onProgress: (DownloadProgress) -> Unit = {}): OfficialInstallResult {
        when (val availability = availability(pack)) {
            is PackAvailability.UpdateAvailable -> Unit
            PackAvailability.NotInstalled -> throw StoreException(StoreFailureKind.NotInstalled, "${pack.title} is not installed")
            is PackAvailability.NeedsNewerApp -> throw StoreException(StoreFailureKind.Unsupported, "${pack.title} needs a newer version of Bertopia")
            is PackAvailability.UpToDate -> throw StoreException(StoreFailureKind.AlreadyInstalled, "${pack.title} is already up to date")
        }
        return downloadAndInstall(pack, onProgress)
    }

    suspend fun download(pack: StorePack, onProgress: (DownloadProgress) -> Unit = {}): String {
        val downloads = Path(workingDirectory, "downloads").toString()
        LibraryFiles.createDirectories(downloads)
        val finalPath = Path(downloads, "${pack.packId}-${pack.version}.apkg").toString()
        val partialPath = "$finalPath.part"
        try {
            listOf(partialPath, finalPath).filter { LibraryFiles.exists(it) }.forEach { LibraryFiles.delete(it) }
            transport.download(StoreUrls.resolve(baseUrl, pack.url), partialPath) { received, total ->
                onProgress(DownloadProgress(DownloadStage.Downloading, received, total ?: pack.sizeBytes.takeIf { it > 0 }))
            }
            currentCoroutineContext().ensureActive()
            onProgress(DownloadProgress(DownloadStage.Verifying, 0, null))
            verify(pack, partialPath)
            LibraryFiles.move(partialPath, finalPath)
            return finalPath
        } catch (failure: Throwable) {
            if (LibraryFiles.exists(partialPath)) LibraryFiles.delete(partialPath)
            throw failure
        }
    }

    private suspend fun downloadAndInstall(pack: StorePack, onProgress: (DownloadProgress) -> Unit): OfficialInstallResult {
        val packagePath = download(pack, onProgress)
        try {
            val context = currentCoroutineContext()
            val monitor = ImportMonitor(
                onProgress = { progress -> onProgress(DownloadProgress(DownloadStage.Installing, progress.completed, progress.total)) },
                isCancelled = { !context.isActive },
            )
            val result = try {
                installer.install(packagePath, monitor, expectedPackId = pack.packId, expectedVersion = pack.version)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                throw StoreException(StoreFailureKind.InvalidPack, "${pack.title} could not be installed: ${failure.message}", failure)
            }
            return result
        } finally {
            if (LibraryFiles.exists(packagePath)) LibraryFiles.delete(packagePath)
        }
    }

    private fun verify(pack: StorePack, path: String) {
        val size = LibraryFiles.size(path)
        if (pack.sizeBytes > 0 && size != pack.sizeBytes) {
            throw StoreException(StoreFailureKind.Tampered, "${pack.title} arrived with the wrong size ($size bytes, expected ${pack.sizeBytes}). It was not installed.")
        }
        val digest = Sha256.hexOfFile(path)
        if (!digest.equals(pack.sha256, ignoreCase = true)) {
            throw StoreException(StoreFailureKind.Tampered, "${pack.title} did not match its checksum. It was not installed.")
        }
    }
}
