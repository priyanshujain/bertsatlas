package app.bertopia.domain.editor

import app.bertopia.anki.collection.AnkiCollection
import app.bertopia.anki.model.DECK_NAME_SEPARATOR
import app.bertopia.anki.model.Deck
import app.bertopia.anki.model.Note
import app.bertopia.domain.pack.ManifestReading
import app.bertopia.domain.pack.PackLibraryLayout
import app.bertopia.domain.pack.PackManifest
import app.bertopia.domain.pack.PackManifestCodec
import kotlinx.io.files.Path

class PackWorkspace(val directory: String, val workingDirectory: String) {
    fun packIds(): List<String> =
        LibraryFiles.list(directory)
            .filter { !it.startsWith(".") && LibraryFiles.exists(Path(directory, it, PackLibraryLayout.COLLECTION_FILE).toString()) }
            .sorted()

    fun contains(packId: String): Boolean = LibraryFiles.exists(collectionPath(packId))

    fun collectionPath(packId: String): String = PackLibraryLayout.collectionPath(directory, packId)

    fun mediaDirectory(packId: String): String = PackLibraryLayout.mediaDirectory(directory, packId)

    fun packDirectory(packId: String): String = PackLibraryLayout.packDirectory(directory, packId)

    fun stagingDirectory(): String = Path(directory, STAGING_DIRECTORY).toString()

    fun <T> read(packId: String, block: (OpenedPack) -> T): T = open(packId).use(block)

    fun <T> write(packId: String, block: (OpenedPack) -> T): T = open(packId).use { pack ->
        if (pack.manifest.official) throw EditorException.readOnly(packId)
        pack.collection.transaction {
            pack.refreshRootDeck()
            block(pack)
        }
    }

    fun open(packId: String): OpenedPack {
        if (!contains(packId)) throw EditorException.notFound("no pack named $packId in $directory")
        val collection = AnkiCollection.open(collectionPath(packId))
        try {
            collection.useWriteAheadLog()
            return OpenedPack(packId, collection, mediaDirectory(packId))
        } catch (failure: Throwable) {
            collection.close()
            throw failure
        }
    }

    fun prepareForSharedAccess(packId: String) = open(packId).close()

    fun newWorkingDirectory(label: String): String {
        val path = Path(workingDirectory, label).toString()
        LibraryFiles.removeRecursively(path)
        LibraryFiles.createDirectories(path)
        return path
    }

    companion object {
        const val STAGING_DIRECTORY = ".staging"
    }
}

class OpenedPack(val packId: String, val collection: AnkiCollection, val mediaDirectory: String) : AutoCloseable {
    lateinit var rootDeck: Deck
        private set
    lateinit var manifest: PackManifest
        private set
    var visibleDescription: String = ""
        private set

    init {
        refreshRootDeck()
    }

    fun refreshRootDeck() {
        val (deck, reading) = collection.decks().filter { it.isTopLevel }.firstNotNullOfOrNull { deck ->
            (PackManifestCodec.readDeckDescription(deck.description) as? ManifestReading.Found)?.let { deck to it }
        } ?: throw EditorException.failure("pack $packId has no Bert manifest")
        rootDeck = deck
        manifest = reading.manifest
        visibleDescription = reading.visibleDescription
    }

    fun saveManifest(updated: PackManifest, deckName: String = rootDeck.name) {
        rootDeck = collection.saveDeck(rootDeck.copy(name = deckName, description = PackManifestCodec.writeDeckDescription(updated, visibleDescription)))
        manifest = updated
    }

    fun packDecks(): List<Deck> = collection.decks().filter { it.id == rootDeck.id || it.name.startsWith(rootDeck.name + DECK_NAME_SEPARATOR) }

    fun deckIds(section: String?): List<Long> {
        if (section == null) return packDecks().map { it.id }
        val name = sectionDeckName(section)
        val ids = packDecks().filter { it.name == name || it.name.startsWith(name + DECK_NAME_SEPARATOR) }.map { it.id }
        return ids.ifEmpty { throw EditorException.notFound("pack $packId has no section named $section") }
    }

    fun sections(): List<String> = packDecks().filter { it.id != rootDeck.id }.map { sectionOf(it.id).orEmpty() }.sorted()

    fun sectionOf(deckId: Long): String? {
        val deck = collection.deck(deckId) ?: return null
        if (deck.id == rootDeck.id) return null
        return deck.name.removePrefix(rootDeck.name + DECK_NAME_SEPARATOR)
    }

    fun sectionDeckName(section: String): String = rootDeck.name + DECK_NAME_SEPARATOR + section.trim()

    fun ensureSectionDeck(section: String?): Deck {
        if (section.isNullOrBlank()) return rootDeck
        val parts = section.split(DECK_NAME_SEPARATOR).map { it.trim() }
        if (parts.any { it.isEmpty() }) throw EditorException.invalid("section '$section' has an empty part")
        var deck = rootDeck
        parts.forEach { part ->
            val name = deck.name + DECK_NAME_SEPARATOR + part
            deck = collection.deckByName(name) ?: collection.saveDeck(Deck(id = 0, name = name, configId = rootDeck.configId))
        }
        return deck
    }

    fun mediaFiles(): Map<String, Long> =
        LibraryFiles.list(mediaDirectory).associateWith { LibraryFiles.size(Path(mediaDirectory, it).toString()) }

    fun resolveNote(reference: String): Note {
        reference.toLongOrNull()?.let { id -> collection.note(id)?.let { return it } }
        collection.noteByGuid(reference)?.let { return it }
        if (CardRefs.looksLikeRef(reference)) {
            val matches = collection.noteIdsByGuid().filterKeys { CardRefs.of(it) == reference }.values
            when (matches.size) {
                1 -> collection.note(matches.single())?.let { return it }
                0 -> Unit
                else -> throw EditorException.conflict("ref $reference matches ${matches.size} notes in $packId; use the note id")
            }
        }
        throw EditorException.notFound("no note $reference in $packId")
    }

    override fun close() = collection.close()
}

object CardRefs {
    private const val PREFIX = "c-"
    private val pattern = Regex("^c-[0-9a-f]{8}$")

    fun of(guid: String): String {
        var hash = 0x811c9dc5u
        guid.encodeToByteArray().forEach { byte ->
            hash = hash xor byte.toUByte().toUInt()
            hash *= 0x01000193u
        }
        return PREFIX + hash.toString(16).padStart(8, '0')
    }

    fun looksLikeRef(reference: String): Boolean = pattern.matches(reference)
}

object Guids {
    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!#$%&()*+,-./:;<=>?@[]^_`{|}~"

    fun random(random: kotlin.random.Random = kotlin.random.Random.Default): String =
        buildString { repeat(10) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }
}
