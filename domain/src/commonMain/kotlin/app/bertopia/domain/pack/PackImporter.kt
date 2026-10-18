package app.bertopia.domain.pack

import app.bertopia.anki.collection.AnkiCollection
import app.bertopia.anki.model.Deck
import app.bertopia.anki.pkg.ImportMonitor
import app.bertopia.anki.pkg.OpenedPackage
import app.bertopia.anki.pkg.PackageFormat
import app.bertopia.anki.pkg.PackageImport
import app.bertopia.anki.template.RenderedCard
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

object PackLibraryLayout {
    const val COLLECTION_FILE = "collection.anki2"
    const val MEDIA_DIRECTORY = "media"

    fun packDirectory(libraryDirectory: String, packId: String): String = Path(libraryDirectory, packId).toString()

    fun collectionPath(libraryDirectory: String, packId: String): String = Path(packDirectory(libraryDirectory, packId), COLLECTION_FILE).toString()

    fun mediaDirectory(libraryDirectory: String, packId: String): String = Path(packDirectory(libraryDirectory, packId), MEDIA_DIRECTORY).toString()
}

object DesktopLibraryLocation {
    const val APPLICATION_SUPPORT_FOLDER = "Bertopia"
    const val PACKS_FOLDER = "packs"

    fun applicationSupportDirectory(homeDirectory: String): String =
        Path(homeDirectory, "Library", "Application Support", APPLICATION_SUPPORT_FOLDER).toString()

    fun libraryDirectory(homeDirectory: String): String = Path(applicationSupportDirectory(homeDirectory), PACKS_FOLDER).toString()
}

data class InstalledPackRef(val packId: String, val title: String, val official: Boolean)

data class ImportableDeck(
    val deckId: Long,
    val title: String,
    val cardCount: Long,
    val sections: List<String>,
    val preview: RenderedCard?,
    val existingPack: InstalledPackRef?,
)

class ImportPlan internal constructor(
    internal val opened: OpenedPackage,
    val format: PackageFormat,
    val decks: List<ImportableDeck>,
) : AutoCloseable {
    val needsDeckPicker: Boolean get() = decks.size > 1

    override fun close() = opened.close()
}

data class DeckSelection(val deckId: Long, val title: String, val category: String, val subcategory: String)

data class ImportedPack(
    val packId: String,
    val title: String,
    val mergedIntoExisting: Boolean,
    val addedNotes: Int,
    val updatedNotes: Int,
    val addedCards: Int,
    val mediaFiles: Int,
)

class PackImporter(private val libraryDirectory: String, private val workingDirectory: String) {
    fun plan(packagePath: String, monitor: ImportMonitor = ImportMonitor.None): ImportPlan {
        val opened = PackageImport.open(packagePath, workingDirectory, monitor)
        return try {
            val installed = installedPackGuids()
            val decks = opened.topLevelDecks().map { deck ->
                val guids = opened.noteGuidsOf(deck.deckId)
                ImportableDeck(
                    deckId = deck.deckId,
                    title = deck.name,
                    cardCount = deck.cardCount,
                    sections = deck.sectionNames,
                    preview = deck.preview,
                    existingPack = installed
                        .map { (pack, packGuids) -> pack to guids.count { it in packGuids } }
                        .filter { it.second > 0 }
                        .maxByOrNull { it.second }
                        ?.first,
                )
            }
            ImportPlan(opened, opened.format, decks)
        } catch (failure: Throwable) {
            opened.close()
            throw failure
        }
    }

    fun import(plan: ImportPlan, selections: List<DeckSelection>, monitor: ImportMonitor = ImportMonitor.None): List<ImportedPack> =
        selections.map { selection ->
            val deck = plan.decks.firstOrNull { it.deckId == selection.deckId } ?: error("Deck ${selection.deckId} is not in this package")
            val existing = deck.existingPack
            if (existing != null) mergeIntoExistingPack(plan.opened, deck, existing, monitor)
            else importAsNewPack(plan.opened, deck, selection, monitor)
        }

    fun installedPacks(): List<InstalledPackRef> = installedPackDirectories().mapNotNull { packId ->
        runCatching {
            AnkiCollection.open(PackLibraryLayout.collectionPath(libraryDirectory, packId)).use { collection ->
                packDeck(collection)?.let { (_, manifest) -> InstalledPackRef(packId, manifest.title, manifest.official) }
            }
        }.getOrNull()
    }

    private fun importAsNewPack(opened: OpenedPackage, deck: ImportableDeck, selection: DeckSelection, monitor: ImportMonitor): ImportedPack {
        require(selection.category.isNotBlank() && selection.subcategory.isNotBlank()) { "Imported packs need a category and subcategory" }
        val packId = uniquePackId(selection.title)
        val staging = Path(workingDirectory, "staging-$packId").toString()
        removeDirectory(staging)
        SystemFileSystem.createDirectories(Path(staging))
        try {
            val result = AnkiCollection.create(Path(staging, PackLibraryLayout.COLLECTION_FILE).toString()).use { target ->
                val result = PackageImport.importDeck(opened, deck.deckId, target, Path(staging, PackLibraryLayout.MEDIA_DIRECTORY).toString(), monitor)
                val topLevel = target.deck(result.targetDeckId) ?: error("Imported deck is missing")
                val manifest = manifestFor(topLevel, packId, selection)
                val visible = when (val reading = PackManifestCodec.readDeckDescription(topLevel.description)) {
                    is ManifestReading.Found -> reading.visibleDescription
                    is ManifestReading.Missing -> reading.visibleDescription
                    is ManifestReading.Unreadable -> reading.visibleDescription
                }
                target.saveDeck(topLevel.copy(description = PackManifestCodec.writeDeckDescription(manifest, visible)))
                result
            }
            monitor.checkCancelled()
            SystemFileSystem.createDirectories(Path(libraryDirectory))
            SystemFileSystem.atomicMove(Path(staging), Path(PackLibraryLayout.packDirectory(libraryDirectory, packId)))
            return ImportedPack(packId, selection.title, false, result.addedNotes, result.updatedNotes, result.addedCards, result.mediaFiles)
        } catch (failure: Throwable) {
            removeDirectory(staging)
            throw failure
        }
    }

    private fun mergeIntoExistingPack(opened: OpenedPackage, deck: ImportableDeck, pack: InstalledPackRef, monitor: ImportMonitor): ImportedPack =
        AnkiCollection.open(PackLibraryLayout.collectionPath(libraryDirectory, pack.packId)).use { target ->
            val (packDeck, _) = packDeck(target) ?: error("Pack ${pack.packId} has no manifest")
            val result = PackageImport.importDeck(
                source = opened,
                topLevelDeckId = deck.deckId,
                target = target,
                targetMediaDirectory = PackLibraryLayout.mediaDirectory(libraryDirectory, pack.packId),
                monitor = monitor,
                intoTopLevelDeckId = packDeck.id,
                updateExistingContent = !pack.official,
            )
            ImportedPack(pack.packId, pack.title, true, result.addedNotes, result.updatedNotes, result.addedCards, result.mediaFiles)
        }

    private fun manifestFor(topLevel: Deck, packId: String, selection: DeckSelection): PackManifest {
        val carried = (PackManifestCodec.readDeckDescription(topLevel.description) as? ManifestReading.Found)?.manifest
        return (carried ?: PackManifest(packId = packId, version = "1", title = selection.title)).copy(
            packId = packId,
            official = false,
            title = selection.title,
            category = selection.category,
            subcategory = selection.subcategory,
            mediaDir = null,
        )
    }

    private fun packDeck(collection: AnkiCollection): Pair<Deck, PackManifest>? =
        collection.decks().filter { it.isTopLevel }.firstNotNullOfOrNull { deck ->
            (PackManifestCodec.readDeckDescription(deck.description) as? ManifestReading.Found)?.let { deck to it.manifest }
        }

    private fun installedPackGuids(): List<Pair<InstalledPackRef, Set<String>>> = installedPackDirectories().mapNotNull { packId ->
        runCatching {
            AnkiCollection.open(PackLibraryLayout.collectionPath(libraryDirectory, packId)).use { collection ->
                packDeck(collection)?.let { (_, manifest) ->
                    InstalledPackRef(packId, manifest.title, manifest.official) to collection.noteIdsByGuid().keys
                }
            }
        }.getOrNull()
    }

    private fun installedPackDirectories(): List<String> {
        val library = Path(libraryDirectory)
        if (!SystemFileSystem.exists(library)) return emptyList()
        return SystemFileSystem.list(library)
            .filter { SystemFileSystem.exists(Path(it, PackLibraryLayout.COLLECTION_FILE)) }
            .map { it.name }
            .sorted()
    }

    private fun uniquePackId(title: String): String {
        val taken = installedPackDirectories().toSet()
        return PackIds.uniqueId(title) { it in taken }
    }

    private fun removeDirectory(path: String) {
        val directory = Path(path)
        if (!SystemFileSystem.exists(directory)) return
        if (SystemFileSystem.metadataOrNull(directory)?.isDirectory == true) {
            SystemFileSystem.list(directory).forEach { removeDirectory(it.toString()) }
        }
        SystemFileSystem.delete(directory)
    }
}
