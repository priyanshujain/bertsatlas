package app.bertsatlas.store

import app.bertsatlas.PackSharer
import app.bertsatlas.anki.pkg.ImportMonitor
import app.bertsatlas.data.PackStore
import app.bertsatlas.domain.editor.LibraryFiles
import app.bertsatlas.domain.pack.ExportFormat
import app.bertsatlas.domain.pack.Mix
import app.bertsatlas.domain.pack.OfficialPackInstaller
import app.bertsatlas.domain.pack.PackExporter
import app.bertsatlas.domain.store.DownloadProgress
import app.bertsatlas.domain.store.DownloadStage
import app.bertsatlas.domain.store.IndexSource
import app.bertsatlas.domain.store.PackAvailability
import app.bertsatlas.domain.store.PackVersions
import app.bertsatlas.domain.store.StoreCache
import app.bertsatlas.domain.store.StoreClient
import app.bertsatlas.domain.store.StoreException
import app.bertsatlas.domain.store.StoreIndex
import app.bertsatlas.domain.store.StorePack
import app.bertsatlas.domain.store.StoreUrls
import app.bertsatlas.domain.store.platformStoreTransport
import app.bertsatlas.domain.taxonomy.Taxonomy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path

sealed interface StorePage {
    data object Home : StorePage
    data class Category(val categoryId: String) : StorePage
    data class Subcategory(val categoryId: String, val subcategoryId: String) : StorePage
    data class PackDetail(val packId: String) : StorePage
    data object Search : StorePage
}

data class PackDownload(val stage: DownloadStage, val fraction: Float?)

data class PackExportRequest(val packId: String, val title: String)

data class StoreState(
    val pages: List<StorePage> = listOf(StorePage.Home),
    val index: StoreIndex? = null,
    val loading: Boolean = false,
    val showingSavedCopy: Boolean = false,
    val unreachable: Boolean = false,
    val query: String = "",
    val installedVersions: Map<String, String> = emptyMap(),
    val downloads: Map<String, PackDownload> = emptyMap(),
    val packMessages: Map<String, String> = emptyMap(),
    val sampleMediaDirectories: Map<String, String> = emptyMap(),
    val export: PackExportRequest? = null,
    val exporting: Boolean = false,
    val exportMessage: String? = null,
) {
    val page: StorePage get() = pages.last()

    fun availability(pack: StorePack): PackAvailability {
        val installed = installedVersions[pack.packId]
        return when {
            installed != null && !PackVersions.isNewer(pack.version, installed) -> PackAvailability.UpToDate(installed)
            PackVersions.isNewer(pack.minAppVersion, APP_VERSION) -> PackAvailability.NeedsNewerApp(pack.minAppVersion)
            installed != null -> PackAvailability.UpdateAvailable(installed, pack.version)
            else -> PackAvailability.NotInstalled
        }
    }

    fun updateFor(packId: String): StorePack? = index?.pack(packId)?.takeIf { availability(it) is PackAvailability.UpdateAvailable }

    companion object {
        const val APP_VERSION = "1.0.0"
    }
}

class StoreController(
    private val packStore: PackStore,
    private val scope: CoroutineScope,
    debugBuild: Boolean,
    private val packSharer: PackSharer,
    private val onPackInstalled: suspend (String) -> Unit,
    private val onIndexChanged: (Taxonomy, List<Mix>) -> Unit,
) {
    private val libraryDirectory = packStore.libraryDirectory
    private val storeDirectory = Path(Path(libraryDirectory).parent ?: Path(libraryDirectory), STORE_DIRECTORY).toString()
    private val workingDirectory = Path(storeDirectory, "work").toString()
    private val baseUrl = debugBaseUrlOverride(debugBuild) ?: StoreUrls.PUBLIC_BASE_URL
    private val transport = platformStoreTransport()
    private val cache = StoreCache(storeDirectory)
    private val client = StoreClient(transport, cache, libraryDirectory, workingDirectory, StoreState.APP_VERSION, baseUrl)
    private val installer = OfficialPackInstaller(libraryDirectory, Path(workingDirectory, "install").toString())
    private val exporter = PackExporter(libraryDirectory, Path(workingDirectory, "export").toString())
    private val downloadJobs = mutableMapOf<String, Job>()
    private val _state = MutableStateFlow(StoreState(index = runCatching { cache.readIndex() }.getOrNull()))
    val state: StateFlow<StoreState> = _state.asStateFlow()

    init {
        _state.value.index?.let(::publishIndex)
        scope.launch { refreshInstalledVersions() }
    }

    fun open(page: StorePage = StorePage.Home) {
        _state.value = _state.value.copy(
            pages = listOf(StorePage.Home) + listOfNotNull(page.takeIf { it != StorePage.Home }),
            packMessages = emptyMap(),
        )
        scope.launch {
            refreshInstalledVersions()
            loadIndex(quiet = false)
        }
        if (page is StorePage.PackDetail) prepareSamples(page.packId)
    }

    fun back(): Boolean {
        val pages = _state.value.pages
        if (pages.size <= 1) return false
        _state.value = _state.value.copy(pages = pages.dropLast(1))
        return true
    }

    fun openCategory(categoryId: String) = push(StorePage.Category(categoryId))

    fun openSubcategory(categoryId: String, subcategoryId: String) = push(StorePage.Subcategory(categoryId, subcategoryId))

    fun openPack(packId: String) {
        push(StorePage.PackDetail(packId))
        prepareSamples(packId)
    }

    fun openSearch() = push(StorePage.Search)

    fun search(query: String) {
        _state.value = _state.value.copy(query = query)
    }

    fun retry() {
        scope.launch { loadIndex(quiet = false) }
    }

    fun clearPackMessage(packId: String) {
        _state.value = _state.value.copy(packMessages = _state.value.packMessages - packId)
    }

    fun get(packId: String) = startDownload(packId, update = false)

    fun update(packId: String) = startDownload(packId, update = true)

    fun cancelDownload(packId: String) {
        downloadJobs.remove(packId)?.cancel()
        _state.value = _state.value.copy(downloads = _state.value.downloads - packId)
    }

    fun requestExport(packId: String, title: String) {
        _state.value = _state.value.copy(export = PackExportRequest(packId, title), exportMessage = null)
    }

    fun dismissExport() {
        if (_state.value.exporting) return
        _state.value = _state.value.copy(export = null, exportMessage = null)
    }

    fun export(compatibleWithOlderAnki: Boolean, includeProgress: Boolean) {
        val request = _state.value.export ?: return
        _state.value = _state.value.copy(exporting = true, exportMessage = null)
        scope.launch {
            val exported = runCatching {
                withContext(Dispatchers.Default) {
                    val outputDirectory = Path(storeDirectory, "exports").toString()
                    LibraryFiles.removeRecursively(outputDirectory)
                    exporter.export(
                        packId = request.packId,
                        outputDirectory = outputDirectory,
                        format = if (compatibleWithOlderAnki) ExportFormat.CompatibleWithOlderAnki else ExportFormat.Modern,
                        includeProgress = includeProgress,
                    )
                }
            }
            exported.onSuccess { pack ->
                packSharer.share(pack.path, pack.fileName) { outcome ->
                    _state.value = _state.value.copy(
                        exporting = false,
                        export = if (outcome == ShareOutcome.Failed) request else null,
                        exportMessage = if (outcome == ShareOutcome.Failed) "Bert couldn't hand the file over. Try again." else null,
                    )
                }
            }.onFailure { failure ->
                _state.value = _state.value.copy(exporting = false, exportMessage = "Bert couldn't export this pack. ${failure.message.orEmpty()}".trim())
            }
        }
    }

    suspend fun packsChangedOutsideStore() = refreshInstalledVersions()

    private fun push(page: StorePage) {
        _state.value = _state.value.copy(pages = _state.value.pages + page)
    }

    private suspend fun loadIndex(quiet: Boolean) {
        if (!quiet) _state.value = _state.value.copy(loading = true)
        val load = client.loadIndex()
        val index = load.index
        _state.value = _state.value.copy(
            index = index ?: _state.value.index,
            loading = false,
            showingSavedCopy = load.source == IndexSource.Cache,
            unreachable = index == null && load.failure != null,
        )
        index?.let(::publishIndex)
    }

    private fun publishIndex(index: StoreIndex) {
        val taxonomy = index.taxonomy()
        onIndexChanged(taxonomy, index.mixes.map { it.toMix() })
    }

    private suspend fun refreshInstalledVersions() {
        val versions = withContext(Dispatchers.Default) {
            runCatching { installer.installed().associate { it.packId to it.version } }.getOrDefault(emptyMap())
        }
        _state.value = _state.value.copy(installedVersions = versions)
    }

    private fun startDownload(packId: String, update: Boolean) {
        val pack = _state.value.index?.pack(packId) ?: return
        if (downloadJobs[packId]?.isActive == true) return
        _state.value = _state.value.copy(
            downloads = _state.value.downloads + (packId to PackDownload(DownloadStage.Downloading, 0f)),
            packMessages = _state.value.packMessages - packId,
        )
        downloadJobs[packId] = scope.launch {
            try {
                val packagePath = client.download(pack) { progress -> showProgress(packId, progress) }
                showProgress(packId, DownloadProgress(DownloadStage.Installing, 0, null))
                val context = coroutineContext
                val result = try {
                    packStore.withPacksClosed {
                        installer.install(
                            packagePath = packagePath,
                            monitor = ImportMonitor(
                                onProgress = { progress -> showProgress(packId, DownloadProgress(DownloadStage.Installing, progress.completed, progress.total)) },
                                isCancelled = { !context.isActive },
                            ),
                            expectedPackId = pack.packId,
                            expectedVersion = pack.version,
                        )
                    }
                } finally {
                    if (LibraryFiles.exists(packagePath)) LibraryFiles.delete(packagePath)
                }
                refreshInstalledVersions()
                onPackInstalled(packId)
                val message = when {
                    !update -> null
                    result.retiredNotes > 0 -> "Updated to ${pack.version}. Your progress is kept and ${result.retiredNotes} retired ${if (result.retiredNotes == 1) "card is" else "cards are"} tucked away."
                    else -> "Updated to ${pack.version}. Your progress is kept."
                }
                _state.value = _state.value.copy(
                    downloads = _state.value.downloads - packId,
                    packMessages = if (message == null) _state.value.packMessages - packId else _state.value.packMessages + (packId to message),
                )
            } catch (cancelled: CancellationException) {
                _state.value = _state.value.copy(downloads = _state.value.downloads - packId)
                throw cancelled
            } catch (failure: Exception) {
                val message = (failure as? StoreException)?.message ?: "${pack.title} could not be installed. ${failure.message.orEmpty()}".trim()
                val current = _state.value
                val showDetail = !update && current.page !is StorePage.PackDetail
                _state.value = current.copy(
                    downloads = current.downloads - packId,
                    packMessages = current.packMessages + (packId to message),
                    pages = if (showDetail) current.pages + StorePage.PackDetail(packId) else current.pages,
                )
            } finally {
                downloadJobs.remove(packId)
            }
        }
    }

    private fun showProgress(packId: String, progress: DownloadProgress) {
        val fraction = progress.total?.takeIf { it > 0 }?.let { (progress.completed.toFloat() / it).coerceIn(0f, 1f) }
        val current = _state.value
        if (packId !in current.downloads) return
        current.downloads[packId]?.let { if (it.stage == progress.stage && it.fraction == fraction) return }
        _state.value = current.copy(downloads = current.downloads + (packId to PackDownload(progress.stage, fraction)))
    }

    private fun prepareSamples(packId: String) {
        val pack = _state.value.index?.pack(packId) ?: return
        val directory = Path(storeDirectory, "samples", "${pack.packId}-${pack.version}").toString()
        val media = pack.sampleCards.flatMap { it.media }.distinct()
        val base = pack.sampleMediaBaseUrl
        if (media.isEmpty() || base == null) {
            _state.value = _state.value.copy(sampleMediaDirectories = _state.value.sampleMediaDirectories + (packId to directory))
            return
        }
        scope.launch {
            withContext(Dispatchers.Default) {
                LibraryFiles.createDirectories(directory)
                media.filter { name -> isSafeMediaName(name) && !LibraryFiles.exists(Path(directory, name).toString()) }.forEach { name ->
                    runCatching { transport.download(StoreUrls.resolve(baseUrl, "${base.trimEnd('/')}/$name"), Path(directory, name).toString()) { _, _ -> } }
                }
            }
            _state.value = _state.value.copy(sampleMediaDirectories = _state.value.sampleMediaDirectories + (packId to directory))
        }
    }

    private fun isSafeMediaName(name: String) = name.isNotBlank() && '/' !in name && '\\' !in name && ".." !in name

    private fun debugBaseUrlOverride(debugBuild: Boolean): String? {
        if (!debugBuild) return null
        val overrideFile = Path(storeDirectory, DEBUG_BASE_URL_FILE).toString()
        if (!LibraryFiles.exists(overrideFile)) return null
        return runCatching { LibraryFiles.readText(overrideFile).trim() }.getOrNull()?.takeIf { it.startsWith("http") }
    }

    private companion object {
        const val STORE_DIRECTORY = "store"
        const val DEBUG_BASE_URL_FILE = "debug-base-url.txt"
    }
}

enum class ShareOutcome { Shared, Dismissed, Failed }
