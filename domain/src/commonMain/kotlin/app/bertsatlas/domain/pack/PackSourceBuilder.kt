package app.bertsatlas.domain.pack

import app.bertsatlas.anki.collection.AnkiCollection
import app.bertsatlas.anki.model.Deck
import app.bertsatlas.anki.model.Note
import app.bertsatlas.anki.pkg.AnkiPackage
import app.bertsatlas.anki.pkg.PackageExport
import app.bertsatlas.anki.pkg.PackageFormat
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.readString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class SourceCard(val guid: String, val noteTypeName: String, val fields: Map<String, String>, val tags: List<String>, val section: String? = null)

data class PackSourceContents(
    val manifest: PackManifest,
    val cards: List<SourceCard>,
    val mediaDirectory: String?,
    val sectionTitles: Map<String, String> = emptyMap(),
    val bundled: Boolean = false,
)

data class BuiltPack(val outputPath: String, val notes: Int, val mediaFiles: Int, val sizeBytes: Long, val issues: List<ValidationIssue>)

class PackBuildException(message: String, val issues: List<ValidationIssue> = emptyList()) : IllegalStateException(message)

object PackSourceBuilder {
    const val MANIFEST_FILE = "pack.json"
    const val CARDS_FILE = "cards.json"
    const val BUILD_EPOCH_MILLIS = 1_767_225_600_000L

    fun read(sourceDirectory: String): PackSourceContents {
        val manifestPath = Path(sourceDirectory, MANIFEST_FILE)
        if (!SystemFileSystem.exists(manifestPath)) throw PackBuildException("$sourceDirectory has no $MANIFEST_FILE")
        val manifestText = readText(manifestPath)
        val sourceManifest = PackManifestCodec.decodeJson(manifestText)
        val buildKeys = Json.parseToJsonElement(manifestText).jsonObject
        val mediaDirectory = sourceManifest.mediaDir?.let { Path(sourceDirectory, it).toString() }
        val cardsPath = Path(sourceDirectory, CARDS_FILE)
        val cards = if (SystemFileSystem.exists(cardsPath)) readCards(readText(cardsPath)) else emptyList()
        return PackSourceContents(
            manifest = sourceManifest.copy(mediaDir = null),
            cards = cards,
            mediaDirectory = mediaDirectory,
            sectionTitles = (buildKeys["sections"] as? JsonObject).orEmpty().mapValues { (_, title) -> title.jsonPrimitive.content },
            bundled = (buildKeys["bundled"] as? JsonPrimitive)?.content == "true",
        )
    }

    fun readCards(json: String): List<SourceCard> =
        Json.parseToJsonElement(json).jsonObject.getValue("cards").jsonArray.map { element ->
            val card = element.jsonObject
            SourceCard(
                guid = card.getValue("guid").jsonPrimitive.content,
                noteTypeName = card.getValue("noteType").jsonPrimitive.content,
                fields = card.getValue("fields").jsonObject.mapValues { (_, value) -> fieldText(value) },
                tags = (card["tags"] as? JsonArray).orEmpty().map { it.jsonPrimitive.content },
                section = (card["section"] as? JsonPrimitive)?.content,
            )
        }

    fun fieldText(value: JsonElement): String = when (value) {
        is JsonPrimitive -> value.content
        is JsonObject -> value.toString()
        else -> throw PackBuildException("unsupported field value $value")
    }

    fun referencedMedia(cards: List<SourceCard>): Set<String> =
        cards.flatMap { card -> card.fields.values.flatMap(PackValidator::mediaReferences) }.distinct().sorted().toSet()

    fun validate(contents: PackSourceContents): List<ValidationIssue> {
        val referenced = referencedMedia(contents.cards)
        val present = referenced.filter { name -> contents.mediaDirectory?.let { SystemFileSystem.exists(Path(it, name)) } == true }.toSet()
        return PackValidator.validate(
            PackContents(
                manifest = ManifestReading.Found(contents.manifest, contents.manifest.subtitle.orEmpty()),
                notes = contents.cards.map { PackNote(it.guid, it.noteTypeName, it.fields, it.tags) },
                mediaFiles = present,
            ),
        )
    }

    fun build(
        sourceDirectory: String,
        workingDirectory: String,
        outputPath: String,
        format: PackageFormat = PackageFormat.Modern,
        nowMillis: () -> Long = { BUILD_EPOCH_MILLIS },
    ): BuiltPack {
        val contents = read(sourceDirectory)
        val referenced = referencedMedia(contents.cards)
        val missing = referenced.filterNot { name -> contents.mediaDirectory?.let { SystemFileSystem.exists(Path(it, name)) } == true }
        if (missing.isNotEmpty()) throw PackBuildException("missing media: ${missing.joinToString()}")
        val issues = validate(contents)
        if (issues.any { it.severity == IssueSeverity.Error }) throw PackBuildException("validation failed", issues)

        removeRecursively(Path(workingDirectory))
        val mediaDirectory = Path(workingDirectory, "media").also { SystemFileSystem.createDirectories(it) }
        referenced.forEach { name -> copyFile(Path(contents.mediaDirectory!!, name), Path(mediaDirectory, name)) }
        val collectionPath = Path(workingDirectory, "collection.anki2").toString()
        writeCollection(collectionPath, contents.manifest, contents.cards, nowMillis, contents.sectionTitles)
        if (SystemFileSystem.exists(Path(outputPath))) SystemFileSystem.delete(Path(outputPath))
        PackageExport.export(
            collectionPath = collectionPath,
            mediaDirectory = mediaDirectory.toString(),
            outputPath = outputPath,
            workingDirectory = Path(workingDirectory, "export").toString(),
            format = format,
        )
        verifyBuiltPack(outputPath, Path(workingDirectory, "verify").toString(), contents.cards.size)
        return BuiltPack(outputPath, contents.cards.size, referenced.size, SystemFileSystem.metadataOrNull(Path(outputPath))?.size ?: 0, issues)
    }

    fun sectionDeckName(packTitle: String, section: String, sectionTitles: Map<String, String>): String {
        val segments = section.split("::")
        if (segments.any { it.isBlank() }) throw PackBuildException("section \"$section\" has an empty part")
        val titles = segments.indices.map { depth ->
            val path = segments.take(depth + 1).joinToString("::")
            sectionTitles[path] ?: sectionTitles[segments[depth]] ?: segments[depth]
        }
        return (listOf(packTitle) + titles).joinToString("::")
    }

    fun writeCollection(
        collectionPath: String,
        manifest: PackManifest,
        cards: List<SourceCard>,
        nowMillis: () -> Long,
        sectionTitles: Map<String, String> = emptyMap(),
    ) {
        AnkiCollection.create(collectionPath, nowMillis = nowMillis).use { collection ->
            collection.transaction {
                val notetypes = cards.map { it.noteTypeName }.distinct().associateWith { name ->
                    val bertNoteType = BertNoteTypes.byName(name) ?: throw PackBuildException("unknown note type $name")
                    collection.saveNotetype(bertNoteType.toAnkiNotetype())
                }
                val deck = collection.saveDeck(
                    Deck(id = 0, name = manifest.title, description = PackManifestCodec.writeDeckDescription(manifest, manifest.subtitle.orEmpty())),
                )
                val sectionDecks = mutableMapOf<String, Long>()
                fun deckIdFor(section: String?): Long {
                    if (section == null) return deck.id
                    val parts = sectionDeckName(manifest.title, section, sectionTitles).split("::")
                    return (2..parts.size).map { size -> parts.take(size).joinToString("::") }
                        .map { path -> sectionDecks.getOrPut(path) { collection.saveDeck(Deck(id = 0, name = path, description = "")).id } }
                        .last()
                }
                cards.forEach { card ->
                    val notetype = notetypes.getValue(card.noteTypeName)
                    val unknownFields = card.fields.keys - notetype.fieldNames.toSet()
                    if (unknownFields.isNotEmpty()) throw PackBuildException("${card.guid} has unknown fields $unknownFields")
                    collection.addNote(
                        Note(
                            id = 0,
                            guid = card.guid,
                            notetypeId = notetype.id,
                            fields = notetype.fieldNames.map { card.fields[it].orEmpty() },
                            tags = card.tags,
                        ),
                        deckId = deckIdFor(card.section),
                    )
                }
            }
        }
    }

    private fun verifyBuiltPack(outputPath: String, workingDirectory: String, expectedNotes: Int) {
        val contents = AnkiPackage.read(SystemFileSystem.source(Path(outputPath)).buffered().use { it.readByteArray() })
        SystemFileSystem.createDirectories(Path(workingDirectory))
        val collectionPath = Path(workingDirectory, "built.anki2")
        SystemFileSystem.sink(collectionPath).buffered().use { it.write(contents.collection) }
        AnkiCollection.open(collectionPath.toString()).use { collection ->
            val deck = collection.decks().single { it.isTopLevel && it.name != "Default" }
            val notes = collection.notes()
            if (notes.size != expectedNotes) throw PackBuildException("built pack has ${notes.size} notes, expected $expectedNotes")
            val issues = PackValidator.validate(
                PackContents(
                    manifest = PackManifestCodec.readDeckDescription(deck.description),
                    notes = notes.map { note ->
                        val notetype = collection.notetype(note.notetypeId)!!
                        PackNote(note.guid, notetype.name, notetype.fieldNames.zip(note.fields).toMap(), note.tags)
                    },
                    mediaFiles = contents.media.map { it.name }.toSet(),
                ),
            )
            if (issues.isNotEmpty()) throw PackBuildException("built pack has issues: ${issues.joinToString { "${it.code} ${it.message}" }}", issues)
        }
    }

    private fun readText(path: Path): String = SystemFileSystem.source(path).buffered().use { it.readString() }

    private fun copyFile(from: Path, to: Path) {
        SystemFileSystem.source(from).buffered().use { source ->
            SystemFileSystem.sink(to).buffered().use { sink -> sink.transferFrom(source) }
        }
    }

    private fun removeRecursively(path: Path) {
        if (!SystemFileSystem.exists(path)) return
        if (SystemFileSystem.metadataOrNull(path)?.isDirectory == true) SystemFileSystem.list(path).forEach(::removeRecursively)
        SystemFileSystem.delete(path)
    }
}
