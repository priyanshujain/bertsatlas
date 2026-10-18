package app.bertopia.domain.editor

import app.bertopia.anki.collection.AnkiCollection
import app.bertopia.anki.model.DECK_NAME_SEPARATOR
import app.bertopia.anki.model.Deck
import app.bertopia.anki.model.Note
import app.bertopia.anki.model.Notetype
import app.bertopia.domain.pack.BertNoteTypes
import app.bertopia.domain.pack.IssueSeverity
import app.bertopia.domain.pack.PackIds
import app.bertopia.domain.pack.PackLibraryLayout
import app.bertopia.domain.pack.PackManifest
import app.bertopia.domain.pack.PackManifestCodec
import app.bertopia.domain.pack.PackNote
import app.bertopia.domain.pack.PackValidator
import app.bertopia.domain.pack.ValidationIssue
import app.bertopia.domain.pack.toAnkiNotetype
import app.bertopia.domain.taxonomy.BundledTaxonomy
import app.bertopia.domain.taxonomy.Taxonomy
import kotlinx.io.files.Path

enum class MediaNameClash { Fail, Replace, Rename }

data class EditableCard(val noteId: Long, val guid: String, val ref: String, val form: CardForm, val values: Map<String, String>, val tags: List<String>, val section: String?)

class PackEditor(
    val workspace: PackWorkspace,
    val history: EditHistory = EditHistory(),
    private val taxonomy: Taxonomy = BundledTaxonomy.taxonomy,
) {
    fun createPack(draft: PackDraft): String {
        val title = draft.title.trim()
        requireUsableTitle(title)
        val packId = draft.packId?.trim()?.also { if (workspace.contains(it)) throw EditorException.conflict("a pack named $it already exists") }
            ?: PackIds.uniqueId(title) { workspace.contains(it) }
        val manifest = PackManifest(
            packId = packId,
            version = "1",
            official = false,
            title = title,
            subtitle = draft.subtitle?.trim()?.ifEmpty { null },
            category = draft.category.trim(),
            subcategory = draft.subcategory.trim(),
            accentColor = draft.accentColor?.trim()?.ifEmpty { null },
            coverMedia = draft.coverMedia?.trim()?.ifEmpty { null },
        )
        requireValidManifest(manifest)
        requireKnownPlacement(manifest.category.orEmpty(), manifest.subcategory.orEmpty())
        val packDirectory = workspace.packDirectory(packId)
        LibraryFiles.createDirectories(workspace.mediaDirectory(packId))
        try {
            AnkiCollection.create(workspace.collectionPath(packId)).use { collection ->
                collection.useWriteAheadLog()
                collection.saveDeck(Deck(id = 0, name = title, description = PackManifestCodec.writeDeckDescription(manifest, manifest.subtitle.orEmpty())))
            }
        } catch (failure: Throwable) {
            LibraryFiles.removeRecursively(packDirectory)
            throw failure
        }
        return packId
    }

    fun updateAppearance(packId: String, appearance: PackAppearance) = mutate(packId, "Change pack look", null) { pack, recorder ->
        val title = appearance.title.trim()
        requireUsableTitle(title)
        appearance.coverMedia?.takeIf { it.isNotBlank() }?.let { cover ->
            if (cover !in pack.mediaFiles()) throw EditorException.invalid("cover $cover is not in the pack media", listOf(EditorProblem("Pick an image from this pack", field = "coverMedia")))
        }
        renameRootDeck(pack, recorder, title)
        val updated = pack.manifest.copy(
            title = title,
            subtitle = appearance.subtitle?.trim()?.ifEmpty { null },
            accentColor = appearance.accentColor?.trim()?.ifEmpty { null },
            coverMedia = appearance.coverMedia?.trim()?.ifEmpty { null },
        )
        requireValidManifest(updated)
        recorder.touchDeck(pack.rootDeck.id)
        pack.saveManifest(updated, deckName = title)
    }

    fun updatePlacement(packId: String, placement: PackPlacement) = mutate(packId, "Move pack to another shelf", null) { pack, recorder ->
        requireKnownPlacement(placement.category, placement.subcategory)
        recorder.touchDeck(pack.rootDeck.id)
        pack.saveManifest(pack.manifest.copy(category = placement.category, subcategory = placement.subcategory))
    }

    fun deletePack(packId: String) {
        if (!workspace.contains(packId)) throw EditorException.notFound("no pack named $packId in ${workspace.directory}")
        LibraryFiles.removeRecursively(workspace.packDirectory(packId))
        history.clear()
    }

    fun makeCopy(packId: String, title: String? = null, newPackId: String? = null): String {
        val staging = workspace.stagingDirectory()
        val (copyId, copyTitle) = workspace.read(packId) { source ->
            val copyTitle = title?.trim()?.ifEmpty { null } ?: "${source.manifest.title} copy"
            requireUsableTitle(copyTitle)
            val copyId = newPackId?.also { if (workspace.contains(it)) throw EditorException.conflict("a pack named $it already exists") }
                ?: PackIds.uniqueId(copyTitle) { workspace.contains(it) }
            val stagingPack = Path(staging, copyId).toString()
            LibraryFiles.removeRecursively(stagingPack)
            LibraryFiles.createDirectories(stagingPack)
            source.collection.copyConsistentSnapshotTo(Path(stagingPack, PackLibraryLayout.COLLECTION_FILE).toString())
            LibraryFiles.copyDirectory(source.mediaDirectory, Path(stagingPack, PackLibraryLayout.MEDIA_DIRECTORY).toString())
            copyId to copyTitle
        }
        val stagingPack = Path(staging, copyId).toString()
        try {
            val collection = AnkiCollection.open(Path(stagingPack, PackLibraryLayout.COLLECTION_FILE).toString())
            OpenedPack(copyId, collection, Path(stagingPack, PackLibraryLayout.MEDIA_DIRECTORY).toString()).use { copy ->
                copy.collection.useWriteAheadLog()
                copy.collection.transaction {
                    val oldName = copy.rootDeck.name
                    copy.collection.decks().filter { it.name.startsWith(oldName + DECK_NAME_SEPARATOR) }.forEach { deck ->
                        copy.collection.saveDeck(deck.copy(name = copyTitle + deck.name.removePrefix(oldName)))
                    }
                    copy.saveManifest(copy.manifest.copy(packId = copyId, official = false, title = copyTitle), deckName = copyTitle)
                }
            }
            LibraryFiles.move(stagingPack, workspace.packDirectory(copyId))
        } catch (failure: Throwable) {
            LibraryFiles.removeRecursively(stagingPack)
            throw failure
        }
        return copyId
    }

    fun addCards(packId: String, drafts: List<CardDraft>): AddCardsResult {
        if (drafts.isEmpty()) throw EditorException.invalid("there are no cards to add")
        var result: AddCardsResult? = null
        mutate(packId, if (drafts.size == 1) "Add card" else "Add ${drafts.size} cards", null) { pack, recorder ->
            val existingGuids = pack.collection.noteIdsByGuid().keys
            val mediaFiles = pack.mediaFiles().keys
            val knownGroups = pack.manifest.similarGroups.map { it.id }.toSet()
            val resolved = drafts.map { draft ->
                CardStyles.resolve(draft.styleName)?.let { style -> pack.collection.notetypeByName(style.name) ?: style.toAnkiNotetype() }
                    ?: pack.collection.notetypeByName(draft.styleName)
            }
            val guids = drafts.map { it.guid ?: Guids.random() }
            val problems = mutableListOf<EditorProblem>()
            val warnings = mutableListOf<EditorProblem>()
            guids.forEachIndexed { index, guid ->
                if (guid in existingGuids) problems += EditorProblem("guid $guid already exists in $packId; edit that card instead", index)
                if (guids.indexOf(guid) != index) problems += EditorProblem("guid $guid appears more than once", index)
            }
            drafts.forEachIndexed { index, draft ->
                val notetype = resolved[index]
                if (notetype == null) {
                    problems += EditorProblem("unknown card style '${draft.styleName}'. Styles: ${CardStyles.all.joinToString { CardStyles.key(it) }}", index)
                    return@forEachIndexed
                }
                val unknown = draft.fields.keys - notetype.fieldNames.toSet()
                if (unknown.isNotEmpty()) problems += EditorProblem("${notetype.name} has no field ${unknown.joinToString()}. Fields: ${notetype.fieldNames.joinToString()}", index)
                validate(notetype, guids[index], draft.fields, draft.tags, knownGroups, mediaFiles).forEach { issue ->
                    val problem = EditorProblem(issue.message, index, issue.field)
                    if (issue.severity == IssueSeverity.Error) problems += problem else warnings += problem
                }
                if (BertNoteTypes.byName(notetype.name) == null && notetype.fieldNames.all { draft.fields[it].isNullOrBlank() }) {
                    problems += EditorProblem("card is empty", index)
                }
            }
            if (problems.isNotEmpty()) throw EditorException.invalid("${problems.size} problem(s) in the cards; nothing was added", problems)
            val saved = mutableMapOf<String, Notetype>()
            val added = drafts.mapIndexed { index, draft ->
                val wanted = resolved[index]!!
                val notetype = saved.getOrPut(wanted.name) { pack.collection.notetypeByName(wanted.name) ?: pack.collection.saveNotetype(wanted) }
                val deck = pack.ensureSectionDeck(draft.section)
                val note = pack.collection.addNote(
                    Note(id = 0, guid = guids[index], notetypeId = notetype.id, fields = notetype.fieldNames.map { draft.fields[it].orEmpty() }, tags = cleanTags(draft.tags)),
                    deckId = deck.id,
                )
                recorder.touchNewNote(note.id)
                AddedCard(index, note.id, note.guid, CardRefs.of(note.guid), pack.collection.cardsOfNote(note.id).size)
            }
            result = AddCardsResult(added, warnings)
        }
        return result!!
    }

    fun editableCard(packId: String, reference: String): EditableCard = workspace.read(packId) { pack ->
        val note = pack.resolveNote(reference)
        val notetype = notetypeOf(pack, note)
        val section = pack.collection.cardsOfNote(note.id).firstOrNull()?.let { pack.sectionOf(it.deckId) }
        EditableCard(note.id, note.guid, CardRefs.of(note.guid), CardForms.forNotetype(notetype), notetype.fieldNames.zip(note.fields).toMap(), note.tags, section)
    }

    fun editCard(packId: String, reference: String, change: CardChange, coalesceKey: String? = null): EditedCard {
        if (change.isEmpty) throw EditorException.invalid("nothing to change")
        var result: EditedCard? = null
        mutate(packId, "Edit card", coalesceKey) { pack, recorder ->
            val note = pack.resolveNote(reference)
            val notetype = notetypeOf(pack, note)
            val unknown = change.fields.keys - notetype.fieldNames.toSet()
            if (unknown.isNotEmpty()) throw EditorException.invalid("${notetype.name} has no field ${unknown.joinToString()}. Fields: ${notetype.fieldNames.joinToString()}")
            val fields = notetype.fieldNames.zip(note.fields).associate { (name, value) -> name to (change.fields[name] ?: value) }
            val tags = change.tags?.let(::cleanTags) ?: note.tags
            val errors = validate(notetype, note.guid, fields, tags, pack.manifest.similarGroups.map { it.id }.toSet(), pack.mediaFiles().keys)
                .filter { it.severity == IssueSeverity.Error }
            if (errors.isNotEmpty()) throw EditorException.invalid("the edit is not valid", errors.map { EditorProblem(it.message, field = it.field) })
            recorder.touchNote(note.id)
            val saved = pack.collection.saveNote(note.copy(fields = notetype.fieldNames.map { fields.getValue(it) }, tags = tags, modifiedSecs = 0, usn = -1))
            val deckId = pack.collection.cardsOfNote(note.id).firstOrNull()?.deckId ?: pack.rootDeck.id
            val newCards = pack.collection.generateMissingCards(saved.id, deckId)
            result = EditedCard(saved.id, newCards.size)
        }
        return result!!
    }

    fun deleteCards(packId: String, references: List<String>): Int {
        var count = 0
        mutate(packId, if (references.size == 1) "Delete card" else "Delete ${references.size} cards", null) { pack, recorder ->
            val notes = references.map { pack.resolveNote(it) }.distinctBy { it.id }
            notes.forEach { note ->
                recorder.touchNote(note.id)
                pack.collection.deleteNote(note.id)
            }
            count = notes.size
        }
        return count
    }

    fun tagCards(packId: String, references: List<String>, add: List<String>, remove: List<String>): Int {
        if (add.isEmpty() && remove.isEmpty()) throw EditorException.invalid("give tags to add or remove")
        (add + remove).firstOrNull { it.isBlank() || it.any(Char::isWhitespace) }?.let { throw EditorException.invalid("tag '$it' must not contain spaces") }
        var count = 0
        mutate(packId, "Tag cards", null) { pack, recorder ->
            val notes = references.map { pack.resolveNote(it) }.distinctBy { it.id }
            notes.forEach { note ->
                val tags = (note.tags.filterNot { tag -> remove.any { it.equals(tag, ignoreCase = true) } } + add).distinctBy { it.lowercase() }
                recorder.touchNote(note.id)
                pack.collection.saveNote(note.copy(tags = tags, modifiedSecs = 0, usn = -1))
            }
            count = notes.size
        }
        return count
    }

    fun moveCards(packId: String, references: List<String>, section: String?): Int {
        var count = 0
        mutate(packId, "Move cards", null) { pack, recorder ->
            val deck = pack.ensureSectionDeck(section)
            val notes = references.map { pack.resolveNote(it) }.distinctBy { it.id }
            notes.forEach { note ->
                recorder.touchNote(note.id)
                pack.collection.cardsOfNote(note.id).forEach { card -> pack.collection.saveCard(card.copy(deckId = deck.id, modifiedSecs = 0, usn = -1)) }
            }
            count = notes.size
        }
        return count
    }

    fun bulkEdit(packId: String, references: List<String>, edit: BulkFieldEdit): Int {
        var count = 0
        mutate(packId, "Edit ${references.size} cards", null) { pack, recorder ->
            val notes = references.map { pack.resolveNote(it) }.distinctBy { it.id }
            val knownGroups = pack.manifest.similarGroups.map { it.id }.toSet()
            val mediaFiles = pack.mediaFiles().keys
            val problems = mutableListOf<EditorProblem>()
            val updates = notes.mapIndexedNotNull { index, note ->
                val notetype = notetypeOf(pack, note)
                val position = notetype.fieldNames.indexOf(edit.fieldName)
                if (position < 0) {
                    problems += EditorProblem("${notetype.name} has no field ${edit.fieldName}", index, edit.fieldName)
                    return@mapIndexedNotNull null
                }
                val current = note.fields.getOrElse(position) { "" }
                val value = when (edit) {
                    is BulkFieldEdit.SetValue -> edit.value
                    is BulkFieldEdit.ReplaceText -> if (edit.find.isEmpty()) current else current.replace(edit.find, edit.replacement, edit.ignoreCase)
                }
                if (value == current) return@mapIndexedNotNull null
                val fields = note.fields.toMutableList().also { while (it.size <= position) it += ""; it[position] = value }
                validate(notetype, note.guid, notetype.fieldNames.zip(fields).toMap(), note.tags, knownGroups, mediaFiles)
                    .filter { it.severity == IssueSeverity.Error }
                    .forEach { problems += EditorProblem(it.message, index, it.field) }
                note.copy(fields = fields, modifiedSecs = 0, usn = -1)
            }
            if (problems.isNotEmpty()) throw EditorException.invalid("${problems.size} problem(s); nothing was changed", problems)
            updates.forEach { note ->
                recorder.touchNote(note.id)
                pack.collection.saveNote(note)
            }
            count = updates.size
        }
        return count
    }

    fun createSection(packId: String, name: String) = mutate(packId, "Add section", null) { pack, _ ->
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw EditorException.invalid("section name must not be empty")
        if (pack.collection.deckByName(pack.sectionDeckName(trimmed)) != null) throw EditorException.conflict("section $trimmed already exists")
        pack.ensureSectionDeck(trimmed)
    }

    fun renameSection(packId: String, from: String, to: String) = mutate(packId, "Rename section", null) { pack, recorder ->
        val target = to.trim()
        if (target.isEmpty() || target.split(DECK_NAME_SEPARATOR).any { it.isBlank() }) throw EditorException.invalid("section name must not be empty")
        val oldName = pack.sectionDeckName(from)
        val newName = pack.sectionDeckName(target)
        if (pack.collection.deckByName(oldName) == null) throw EditorException.notFound("pack $packId has no section named $from")
        if (pack.collection.deckByName(newName) != null) throw EditorException.conflict("section $target already exists")
        pack.collection.decks().filter { it.name == oldName || it.name.startsWith(oldName + DECK_NAME_SEPARATOR) }.forEach { deck ->
            recorder.touchDeck(deck.id)
            pack.collection.saveDeck(deck.copy(name = newName + deck.name.removePrefix(oldName)))
        }
    }

    fun addMedia(packId: String, sourcePath: String, preferredName: String? = null, onNameClash: MediaNameClash = MediaNameClash.Rename): MediaAdded {
        if (!LibraryFiles.exists(sourcePath) || LibraryFiles.isDirectory(sourcePath)) throw EditorException.notFound("no file at $sourcePath")
        val name = MediaNames.safe(preferredName ?: Path(sourcePath).name)
        return workspace.write(packId) { pack ->
            LibraryFiles.createDirectories(pack.mediaDirectory)
            val sourceHash = LibraryFiles.sha1(sourcePath)
            val existing = pack.mediaFiles()
            existing.keys.firstOrNull { existingName ->
                existing[existingName] == LibraryFiles.size(sourcePath) && LibraryFiles.sha1(Path(pack.mediaDirectory, existingName).toString()) == sourceHash
            }?.let { same ->
                return@write MediaAdded(same, existing.getValue(same), MediaNames.reference(same), reusedExisting = true)
            }
            val target = when {
                name !in existing -> name
                onNameClash == MediaNameClash.Replace -> name
                onNameClash == MediaNameClash.Rename -> MediaNames.unique(name, existing.keys)
                else -> throw EditorException.conflict("$name already exists in $packId with different content")
            }
            val targetPath = Path(pack.mediaDirectory, target).toString()
            LibraryFiles.copy(sourcePath, targetPath)
            MediaAdded(target, LibraryFiles.size(targetPath), MediaNames.reference(target), reusedExisting = false)
        }
    }

    fun removeMedia(packId: String, names: List<String>, evenIfUsed: Boolean = false): List<String> = workspace.write(packId) { pack ->
        val referenced = referencedMedia(pack)
        val present = pack.mediaFiles().keys
        names.forEach { name ->
            if (name !in present) throw EditorException.notFound("no media named $name in $packId")
            if (name in referenced && !evenIfUsed) throw EditorException.conflict("$name is used by cards in $packId")
        }
        names.forEach { LibraryFiles.delete(Path(pack.mediaDirectory, it).toString()) }
        names
    }

    fun referencedMedia(pack: OpenedPack): Set<String> =
        pack.collection.notes().flatMap { note -> note.fields.flatMap(PackValidator::mediaReferences) }.toSet() +
            listOfNotNull(pack.manifest.coverMedia)

    fun previewEdit(packId: String, reference: String, fields: Map<String, String>): List<CardFacePreview> = workspace.read(packId) { pack ->
        val note = pack.resolveNote(reference)
        val notetype = notetypeOf(pack, note)
        val values = notetype.fieldNames.zip(note.fields).toMap() + fields
        CardPreviews.forNotetype(notetype, values, note.tags)
    }

    fun queryCards(packId: String, query: CardQuery): CardPage = workspace.read(packId) { CardTable.query(it, query) }

    fun planTableImport(packId: String, text: String, styleName: String, hasHeader: Boolean? = null, columnFields: Map<Int, String>? = null): TableImportPlan {
        val style = CardStyles.require(styleName)
        val table = TableImport.parse(text)
        if (table.rows.isEmpty()) throw EditorException.invalid("the table is empty")
        val plan = TableImport.plan(table, style, hasHeader, columnFields)
        return workspace.read(packId) { pack ->
            val knownGroups = pack.manifest.similarGroups.map { it.id }.toSet()
            val mediaFiles = pack.mediaFiles().keys
            val notetype = style.toAnkiNotetype()
            plan.copy(rows = plan.rows.mapIndexed { index, row ->
                val extra = validate(notetype, "row-$index", row.draft.fields, row.draft.tags, knownGroups, mediaFiles)
                    .filter { it.severity == IssueSeverity.Error }
                    .map { EditorProblem(it.message, index, it.field) }
                    .filterNot { problem -> row.problems.any { it.field == problem.field } }
                row.copy(problems = row.problems + extra)
            })
        }
    }

    fun undo(): String? {
        val entry = history.takeUndo() ?: return null
        workspace.write(entry.packId) { pack -> PackChangeApplier.restore(pack.collection, entry.change.notesBefore, entry.change.decksBefore) }
        return entry.label
    }

    fun redo(): String? {
        val entry = history.takeRedo() ?: return null
        workspace.write(entry.packId) { pack -> PackChangeApplier.restore(pack.collection, entry.change.notesAfter, entry.change.decksAfter) }
        return entry.label
    }

    private fun mutate(packId: String, label: String, coalesceKey: String?, block: (OpenedPack, ChangeRecorder) -> Unit) {
        val change = workspace.write(packId) { pack ->
            val recorder = ChangeRecorder(pack.collection)
            block(pack, recorder)
            recorder.finish()
        }
        history.record(EditEntry(packId, label, coalesceKey, change))
    }

    private fun renameRootDeck(pack: OpenedPack, recorder: ChangeRecorder, title: String) {
        val oldName = pack.rootDeck.name
        if (oldName == title) return
        if (pack.collection.deckByName(title) != null) throw EditorException.conflict("another deck is already called $title")
        pack.collection.decks().filter { it.name.startsWith(oldName + DECK_NAME_SEPARATOR) }.forEach { deck ->
            recorder.touchDeck(deck.id)
            pack.collection.saveDeck(deck.copy(name = title + deck.name.removePrefix(oldName)))
        }
    }

    private fun notetypeOf(pack: OpenedPack, note: Note): Notetype =
        pack.collection.notetype(note.notetypeId) ?: throw EditorException.failure("note ${note.id} has no card style")

    private fun validate(notetype: Notetype, guid: String, fields: Map<String, String>, tags: List<String>, knownGroups: Set<String>, mediaFiles: Set<String>): List<ValidationIssue> {
        if (BertNoteTypes.byName(notetype.name) == null) return emptyList()
        val complete = notetype.fieldNames.associateWith { fields[it].orEmpty() }
        return PackValidator.validateNote(PackNote(guid, notetype.name, complete, tags), knownGroups, mediaFiles)
    }

    private fun cleanTags(tags: List<String>): List<String> = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }

    private fun requireUsableTitle(title: String) {
        if (title.isEmpty()) throw EditorException.invalid("title must not be empty", listOf(EditorProblem("Give the pack a title", field = "title")))
        if (DECK_NAME_SEPARATOR in title) throw EditorException.invalid("title must not contain ::", listOf(EditorProblem("Titles can't contain ::", field = "title")))
    }

    private fun requireValidManifest(manifest: PackManifest) {
        val errors = PackValidator.validateManifest(manifest).filter { it.severity == IssueSeverity.Error }
        if (errors.isNotEmpty()) throw EditorException.invalid(errors.joinToString("; ") { it.message }, errors.map { EditorProblem(it.message, field = it.field) })
    }

    private fun requireKnownPlacement(category: String, subcategory: String) {
        taxonomy.placementProblem(category, subcategory)?.let { throw EditorException.invalid(it, listOf(EditorProblem(it, field = "category"))) }
    }
}

object MediaNames {
    private val audioExtensions = setOf("mp3", "m4a", "aac", "wav", "ogg", "oga", "opus", "flac", "aiff")

    fun safe(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isBlank() || trimmed.startsWith(".") || '/' in trimmed || '\\' in trimmed || trimmed.contains("..")) {
            throw EditorException.invalid("'$name' is not a usable media file name")
        }
        return trimmed
    }

    fun unique(name: String, taken: Set<String>): String {
        val stem = name.substringBeforeLast('.')
        val extension = name.substringAfterLast('.', "").let { if (it.isEmpty() || it == name) "" else ".$it" }
        return generateSequence(2) { it + 1 }.map { "$stem-$it$extension" }.first { it !in taken }
    }

    fun isAudio(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in audioExtensions

    fun reference(name: String): String = if (isAudio(name)) "[sound:$name]" else "<img src=\"$name\">"
}
