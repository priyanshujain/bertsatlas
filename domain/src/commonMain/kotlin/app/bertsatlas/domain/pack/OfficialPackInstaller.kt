package app.bertsatlas.domain.pack

import app.bertsatlas.anki.collection.AnkiCollection
import app.bertsatlas.anki.model.Deck
import app.bertsatlas.anki.pkg.ImportMonitor
import app.bertsatlas.anki.pkg.OpenedPackage
import app.bertsatlas.anki.pkg.PackageImport
import app.bertsatlas.domain.editor.LibraryFiles
import app.bertsatlas.domain.fsrs.CardScheduler
import kotlinx.io.files.Path

data class InstalledOfficialPack(val packId: String, val version: String, val title: String)

data class OfficialInstallResult(
    val packId: String,
    val version: String,
    val previousVersion: String?,
    val addedNotes: Int,
    val updatedNotes: Int,
    val retiredNotes: Int,
    val restoredNotes: Int,
    val mediaFiles: Int,
)

class OfficialPackInstaller(private val libraryDirectory: String, private val workingDirectory: String) {
    fun installed(): List<InstalledOfficialPack> = LibraryFiles.list(libraryDirectory).sorted().mapNotNull { packId ->
        val collectionPath = PackLibraryLayout.collectionPath(libraryDirectory, packId)
        if (!LibraryFiles.exists(collectionPath)) return@mapNotNull null
        runCatching {
            AnkiCollection.open(collectionPath).use { collection ->
                packDeck(collection)?.second?.takeIf { it.official }?.let { InstalledOfficialPack(packId, it.version, it.title) }
            }
        }.getOrNull()
    }

    fun installedVersion(packId: String): String? = installed().firstOrNull { it.packId == packId }?.version

    fun install(
        packagePath: String,
        monitor: ImportMonitor = ImportMonitor.None,
        expectedPackId: String? = null,
        expectedVersion: String? = null,
    ): OfficialInstallResult =
        PackageImport.open(packagePath, Path(workingDirectory, "official").toString(), monitor).use { opened ->
            val (deck, manifest) = packDeck(opened.collection) ?: throw IllegalArgumentException("This file is not a Bert's Atlas pack")
            require(PackIds.isValid(manifest.packId)) { "Pack id \"${manifest.packId}\" is not valid" }
            require(expectedPackId == null || manifest.packId == expectedPackId) { "The file contains ${manifest.packId} instead of $expectedPackId" }
            require(expectedVersion == null || manifest.version == expectedVersion) { "The file contains version ${manifest.version} instead of $expectedVersion" }
            val officialManifest = manifest.copy(official = true, mediaDir = null)
            if (LibraryFiles.exists(PackLibraryLayout.collectionPath(libraryDirectory, manifest.packId))) {
                update(opened, deck, officialManifest, monitor)
            } else {
                installFresh(opened, deck, officialManifest, monitor)
            }
        }

    private fun installFresh(opened: OpenedPackage, deck: Deck, manifest: PackManifest, monitor: ImportMonitor): OfficialInstallResult {
        val staging = Path(workingDirectory, "staging-${manifest.packId}").toString()
        LibraryFiles.removeRecursively(staging)
        LibraryFiles.createDirectories(staging)
        try {
            val result = AnkiCollection.create(Path(staging, PackLibraryLayout.COLLECTION_FILE).toString()).use { target ->
                val result = PackageImport.importDeck(
                    opened, deck.id, target, Path(staging, PackLibraryLayout.MEDIA_DIRECTORY).toString(), monitor,
                    packMedia = setOfNotNull(manifest.coverMedia),
                )
                writeManifest(target, result.targetDeckId, manifest)
                result
            }
            monitor.checkCancelled()
            LibraryFiles.createDirectories(libraryDirectory)
            LibraryFiles.move(staging, PackLibraryLayout.packDirectory(libraryDirectory, manifest.packId))
            return OfficialInstallResult(manifest.packId, manifest.version, null, result.addedNotes, result.updatedNotes, 0, 0, result.mediaFiles)
        } catch (failure: Throwable) {
            LibraryFiles.removeRecursively(staging)
            throw failure
        }
    }

    private fun update(opened: OpenedPackage, deck: Deck, manifest: PackManifest, monitor: ImportMonitor): OfficialInstallResult {
        val collectionPath = PackLibraryLayout.collectionPath(libraryDirectory, manifest.packId)
        val backupPath = Path(workingDirectory, "backup-${manifest.packId}.anki2").toString()
        if (LibraryFiles.exists(backupPath)) LibraryFiles.delete(backupPath)
        LibraryFiles.createDirectories(workingDirectory)
        AnkiCollection.open(collectionPath).use { it.copyConsistentSnapshotTo(backupPath) }
        try {
            val result = AnkiCollection.open(collectionPath).use { target ->
                val (installedDeck, installedManifest) = packDeck(target) ?: throw IllegalStateException("Installed pack ${manifest.packId} has no manifest")
                val treeIds = (listOf(installedDeck) + target.subdecksOf(installedDeck)).map { it.id }
                val retiredBefore = target.notes().filter { BertTags.RETIRED in it.tags }.map { it.guid }.toSet()
                val imported = PackageImport.importDeck(
                    source = opened,
                    topLevelDeckId = deck.id,
                    target = target,
                    targetMediaDirectory = PackLibraryLayout.mediaDirectory(libraryDirectory, manifest.packId),
                    monitor = monitor,
                    intoTopLevelDeckId = installedDeck.id,
                    updateExistingContent = true,
                    keepExistingScheduling = true,
                    packMedia = setOfNotNull(manifest.coverMedia),
                )
                val incomingGuids = opened.noteGuidsOf(deck.id)
                val (retired, restored) = target.transaction {
                    val packGuids = target.noteGuidsInDecks(treeIds)
                    val retired = (packGuids - incomingGuids).count { guid -> retire(target, guid) }
                    val restored = (retiredBefore intersect incomingGuids).count { guid -> restore(target, guid) }
                    writeManifest(target, installedDeck.id, manifest)
                    retired to restored
                }
                OfficialInstallResult(
                    manifest.packId, manifest.version, installedManifest.version,
                    imported.addedNotes, imported.updatedNotes, retired, restored, imported.mediaFiles,
                )
            }
            LibraryFiles.delete(backupPath)
            return result
        } catch (failure: Throwable) {
            restoreBackup(backupPath, collectionPath)
            throw failure
        }
    }

    private fun retire(collection: AnkiCollection, guid: String): Boolean {
        val note = collection.noteByGuid(guid) ?: return false
        val cards = collection.cardsOfNote(note.id)
        val alreadyRetired = BertTags.RETIRED in note.tags && cards.all { it.scheduling.queue == CardScheduler.QUEUE_SUSPENDED }
        if (alreadyRetired) return false
        if (BertTags.RETIRED !in note.tags) collection.saveNote(note.copy(tags = note.tags + BertTags.RETIRED))
        cards.filter { it.scheduling.queue != CardScheduler.QUEUE_SUSPENDED }
            .forEach { card -> collection.saveCard(card.copy(scheduling = card.scheduling.copy(queue = CardScheduler.QUEUE_SUSPENDED))) }
        return true
    }

    private fun restore(collection: AnkiCollection, guid: String): Boolean {
        val note = collection.noteByGuid(guid) ?: return false
        if (BertTags.RETIRED in note.tags) collection.saveNote(note.copy(tags = note.tags - BertTags.RETIRED))
        collection.cardsOfNote(note.id).filter { it.scheduling.queue == CardScheduler.QUEUE_SUSPENDED }.forEach { card ->
            collection.saveCard(card.copy(scheduling = card.scheduling.copy(queue = queueForType(card.scheduling.type, card.scheduling.due))))
        }
        return true
    }

    private fun queueForType(type: Int, due: Long): Int = when (type) {
        CardScheduler.TYPE_NEW -> CardScheduler.QUEUE_NEW
        CardScheduler.TYPE_REVIEW -> CardScheduler.QUEUE_REVIEW
        else -> if (due > INTRADAY_DUE_THRESHOLD) CardScheduler.QUEUE_LEARN else CardScheduler.QUEUE_DAY_LEARN
    }

    private fun writeManifest(collection: AnkiCollection, deckId: Long, manifest: PackManifest) {
        val deck = collection.deck(deckId) ?: throw IllegalStateException("Pack deck is missing")
        val visible = when (val reading = PackManifestCodec.readDeckDescription(deck.description)) {
            is ManifestReading.Found -> reading.visibleDescription
            is ManifestReading.Missing -> reading.visibleDescription
            is ManifestReading.Unreadable -> reading.visibleDescription
        }
        collection.saveDeck(deck.copy(description = PackManifestCodec.writeDeckDescription(manifest, visible.ifBlank { manifest.subtitle.orEmpty() })))
    }

    private fun restoreBackup(backupPath: String, collectionPath: String) {
        if (!LibraryFiles.exists(backupPath)) return
        listOf("$collectionPath-wal", "$collectionPath-shm", collectionPath).filter { LibraryFiles.exists(it) }.forEach { LibraryFiles.delete(it) }
        LibraryFiles.move(backupPath, collectionPath)
    }

    private fun packDeck(collection: AnkiCollection): Pair<Deck, PackManifest>? =
        collection.decks().filter { it.isTopLevel }.firstNotNullOfOrNull { deck ->
            (PackManifestCodec.readDeckDescription(deck.description) as? ManifestReading.Found)?.let { deck to it.manifest }
        }

    private companion object {
        const val INTRADAY_DUE_THRESHOLD = 1_000_000_000L
    }
}
