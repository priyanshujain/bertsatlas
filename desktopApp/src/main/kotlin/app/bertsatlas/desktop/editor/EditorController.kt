package app.bertsatlas.desktop.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.bertsatlas.domain.editor.BulkFieldEdit
import app.bertsatlas.domain.editor.CardChange
import app.bertsatlas.domain.editor.CardDraft
import app.bertsatlas.domain.editor.CardFacePreview
import app.bertsatlas.domain.editor.CardForm
import app.bertsatlas.domain.editor.CardForms
import app.bertsatlas.domain.editor.CardPreviews
import app.bertsatlas.domain.editor.CardQuery
import app.bertsatlas.domain.editor.CardRow
import app.bertsatlas.domain.editor.CardSort
import app.bertsatlas.domain.editor.CardStyles
import app.bertsatlas.domain.editor.EditorException
import app.bertsatlas.domain.editor.MediaNameClash
import app.bertsatlas.domain.editor.PackDraft
import app.bertsatlas.domain.editor.PackEditor
import app.bertsatlas.domain.editor.PackWorkspace
import app.bertsatlas.domain.editor.TableImport
import app.bertsatlas.domain.editor.TableImportPlan
import app.bertsatlas.domain.pack.BertNoteType
import app.bertsatlas.domain.pack.BertNoteTypes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher

data class EditorPack(val packId: String, val title: String, val official: Boolean, val cardCount: Long, val accentColor: String?)

sealed interface InspectorTarget {
    data class Existing(val noteId: Long) : InspectorTarget
    data class New(val style: BertNoteType) : InspectorTarget
}

data class Inspector(
    val target: InspectorTarget,
    val form: CardForm,
    val values: Map<String, String>,
    val savedValues: Map<String, String>,
    val tags: String,
    val savedTags: String,
    val section: String?,
) {
    val dirty: Boolean get() = values != savedValues || tags != savedTags
}

sealed interface EditorDialog {
    data object NewPack : EditorDialog
    data object ChooseStyle : EditorDialog
    data object TagCards : EditorDialog
    data object MoveCards : EditorDialog
    data object BulkEdit : EditorDialog
    data object AddSection : EditorDialog
    data class RenameSection(val section: String) : EditorDialog
    data class ImportTable(val text: String) : EditorDialog
}

class EditorController(libraryDirectory: File, workDirectory: File, private val scope: CoroutineScope) {
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "pack-editor").apply { isDaemon = true } }.asCoroutineDispatcher()
    val editor = PackEditor(PackWorkspace(libraryDirectory.absolutePath, workDirectory.apply { mkdirs() }.absolutePath))

    var packs by mutableStateOf<List<EditorPack>>(emptyList())
        private set
    var selectedPackId by mutableStateOf<String?>(null)
        private set
    var sections by mutableStateOf<List<String>>(emptyList())
        private set
    var selectedSection by mutableStateOf<String?>(null)
        private set
    var search by mutableStateOf("")
        private set
    var tagFilter by mutableStateOf<String?>(null)
        private set
    var sort by mutableStateOf(CardSort.Created)
        private set
    var descending by mutableStateOf(false)
        private set
    var rows by mutableStateOf<List<CardRow>>(emptyList())
        private set
    var totalRows by mutableStateOf(0)
        private set
    var tags by mutableStateOf<List<String>>(emptyList())
        private set
    var selection by mutableStateOf<Set<Long>>(emptySet())
        private set
    var inspector by mutableStateOf<Inspector?>(null)
        private set
    var previews by mutableStateOf<List<CardFacePreview>>(emptyList())
        private set
    var dialog by mutableStateOf<EditorDialog?>(null)
        private set
    var notice by mutableStateOf<String?>(null)
        private set
    var problems by mutableStateOf<List<String>>(emptyList())
        private set
    var tablePlan by mutableStateOf<TableImportPlan?>(null)
        private set
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set
    var searchFocusRequests by mutableStateOf(0)
        private set

    private var anchorNoteId: Long? = null
    private var queryJob: Job? = null
    private var previewJob: Job? = null

    val selectedPack: EditorPack? get() = packs.firstOrNull { it.packId == selectedPackId }
    val readOnly: Boolean get() = selectedPack?.official == true

    fun mediaDirectory(): String = selectedPackId?.let { editor.workspace.mediaDirectory(it) }.orEmpty()

    fun start(initialPackId: String?) = launchWork {
        val loaded = loadPacks()
        withMain {
            packs = loaded
            val first = initialPackId?.takeIf { id -> loaded.any { it.packId == id } } ?: loaded.firstOrNull { !it.official }?.packId ?: loaded.firstOrNull()?.packId
            first?.let { choosePack(it) }
        }
    }

    fun choosePack(packId: String) {
        if (!confirmLeavingInspector()) return
        selectedPackId = packId
        selectedSection = null
        tagFilter = null
        search = ""
        selection = emptySet()
        inspector = null
        previews = emptyList()
        refreshRows()
    }

    fun chooseSection(section: String?) {
        selectedSection = section
        refreshRows()
    }

    fun changeSearch(text: String) {
        search = text
        refreshRows(debounce = true)
    }

    fun chooseTag(tag: String?) {
        tagFilter = tag
        refreshRows()
    }

    fun chooseSort(newSort: CardSort) {
        if (sort == newSort) descending = !descending else {
            sort = newSort
            descending = false
        }
        refreshRows()
    }

    fun focusSearch() {
        searchFocusRequests++
    }

    fun loadMoreRows() {
        if (rows.size >= totalRows) return
        val packId = selectedPackId ?: return
        val query = currentQuery(offset = rows.size)
        launchWork {
            val page = editor.queryCards(packId, query)
            withMain { if (selectedPackId == packId) rows = rows + page.rows }
        }
    }

    fun clickRow(noteId: Long, toggle: Boolean, extend: Boolean) {
        val anchor = anchorNoteId
        selection = when {
            extend && anchor != null -> {
                val ids = rows.map { it.noteId }
                val from = ids.indexOf(anchor)
                val to = ids.indexOf(noteId)
                if (from < 0 || to < 0) setOf(noteId) else ids.subList(minOf(from, to), maxOf(from, to) + 1).toSet()
            }
            toggle -> if (noteId in selection) selection - noteId else selection + noteId
            else -> setOf(noteId)
        }
        if (!extend) anchorNoteId = noteId
        if (selection.size == 1) openCard(selection.single()) else if (selection.size > 1 && confirmLeavingInspector()) {
            inspector = null
            previews = emptyList()
        }
    }

    fun selectAdjacent(step: Int) {
        if (rows.isEmpty()) return
        val current = (inspector?.target as? InspectorTarget.Existing)?.noteId ?: selection.lastOrNull()
        val index = rows.indexOfFirst { it.noteId == current }
        val next = (if (index < 0) 0 else index + step).coerceIn(0, rows.lastIndex)
        if (inspector?.dirty == true) save { moveTo(rows[next].noteId) } else moveTo(rows[next].noteId)
        if (next >= rows.size - PAGE_SIZE / 4) loadMoreRows()
    }

    private fun moveTo(noteId: Long) {
        selection = setOf(noteId)
        anchorNoteId = noteId
        openCard(noteId)
    }

    fun openCard(noteId: Long) {
        val packId = selectedPackId ?: return
        launchWork {
            val card = editor.editableCard(packId, noteId.toString())
            val tagText = card.tags.joinToString(" ")
            withMain {
                inspector = Inspector(InspectorTarget.Existing(card.noteId), card.form, card.values, card.values, tagText, tagText, card.section)
                problems = emptyList()
                refreshPreview()
            }
        }
    }

    fun startNewCard(style: BertNoteType) {
        dialog = null
        if (readOnly) return
        val form = CardForms.forStyle(style)
        val empty = form.fields.associate { it.name to "" }
        selection = emptySet()
        inspector = Inspector(InspectorTarget.New(style), form, empty, empty, "", "", selectedSection)
        problems = emptyList()
        refreshPreview()
    }

    fun requestNewCard() {
        if (readOnly || selectedPackId == null) return
        dialog = EditorDialog.ChooseStyle
    }

    fun changeField(name: String, value: String) {
        val current = inspector ?: return
        inspector = current.copy(values = current.values + (name to value))
        refreshPreview()
    }

    fun changeTags(text: String) {
        val current = inspector ?: return
        inspector = current.copy(tags = text)
    }

    fun changeNewCardSection(section: String?) {
        val current = inspector ?: return
        inspector = current.copy(section = section)
    }

    fun save(afterSave: () -> Unit = {}) {
        val packId = selectedPackId ?: return
        val current = inspector ?: return
        if (readOnly) return
        val tagList = current.tags.split(Regex("\\s+")).filter { it.isNotBlank() }
        launchWork(onFailure = ::showFailure) {
            when (val target = current.target) {
                is InspectorTarget.New -> {
                    val result = editor.addCards(packId, listOf(CardDraft(target.style.name, current.values.filterValues { it.isNotEmpty() }, tagList, current.section)))
                    val noteId = result.added.single().noteId
                    withMain {
                        notice = "Card added"
                        problems = result.warnings.map { it.message }
                        selection = setOf(noteId)
                        anchorNoteId = noteId
                    }
                    reloadAfterChange()
                    withMain { openCard(noteId); afterSave() }
                }
                is InspectorTarget.Existing -> {
                    val changed = current.values.filter { (name, value) -> current.savedValues[name] != value }
                    val tagsChanged = current.tags != current.savedTags
                    if (changed.isNotEmpty() || tagsChanged) {
                        editor.editCard(packId, target.noteId.toString(), CardChange(changed, if (tagsChanged) tagList else null))
                    }
                    withMain {
                        inspector = current.copy(savedValues = current.values, savedTags = current.tags)
                        notice = "Saved"
                        problems = emptyList()
                    }
                    reloadAfterChange()
                    withMain { afterSave() }
                }
            }
        }
    }

    fun revertInspector() {
        val current = inspector ?: return
        if (current.target is InspectorTarget.New) {
            inspector = null
            previews = emptyList()
        } else {
            inspector = current.copy(values = current.savedValues, tags = current.savedTags)
            refreshPreview()
        }
        problems = emptyList()
    }

    fun deleteSelected() {
        val packId = selectedPackId ?: return
        if (readOnly) return
        val ids = selection.ifEmpty { setOfNotNull((inspector?.target as? InspectorTarget.Existing)?.noteId) }
        if (ids.isEmpty()) return
        launchWork(onFailure = ::showFailure) {
            val count = editor.deleteCards(packId, ids.map { it.toString() })
            withMain {
                selection = emptySet()
                inspector = null
                previews = emptyList()
                notice = if (count == 1) "Card deleted. Undo with ⌘Z" else "$count cards deleted. Undo with ⌘Z"
            }
            reloadAfterChange()
        }
    }

    fun tagSelected(add: List<String>, remove: List<String>) = withSelection { packId, refs ->
        val count = editor.tagCards(packId, refs, add, remove)
        "Tags updated on ${cardsLabel(count)}"
    }

    fun moveSelected(section: String?) = withSelection { packId, refs ->
        val count = editor.moveCards(packId, refs, section)
        "Moved ${cardsLabel(count)} to ${section ?: "the main section"}"
    }

    fun bulkEditSelected(edit: BulkFieldEdit) = withSelection { packId, refs ->
        val count = editor.bulkEdit(packId, refs, edit)
        "Changed ${cardsLabel(count)}"
    }

    private fun withSelection(action: (String, List<String>) -> String) {
        val packId = selectedPackId ?: return
        val refs = selection.map { it.toString() }
        if (refs.isEmpty() || readOnly) return
        dialog = null
        launchWork(onFailure = ::showFailure) {
            val message = action(packId, refs)
            withMain { notice = message }
            reloadAfterChange()
            withMain { (inspector?.target as? InspectorTarget.Existing)?.let { openCard(it.noteId) } }
        }
    }

    fun addMediaToField(field: String, file: File) {
        val packId = selectedPackId ?: return
        if (readOnly) return
        launchWork(onFailure = ::showFailure) {
            val added = editor.addMedia(packId, file.absolutePath, onNameClash = MediaNameClash.Rename)
            withMain {
                changeField(field, CardForms.imageValue(added.name))
                notice = if (added.reusedExisting) "Using ${added.name}, already in this pack" else "Added ${added.name}"
            }
        }
    }

    fun undo() = launchWork(onFailure = ::showFailure) {
        val label = editor.undo()
        withMain { notice = label?.let { "Undid: $it" } ?: "Nothing to undo" }
        afterHistoryChange()
    }

    fun redo() = launchWork(onFailure = ::showFailure) {
        val label = editor.redo()
        withMain { notice = label?.let { "Redid: $it" } ?: "Nothing to redo" }
        afterHistoryChange()
    }

    private suspend fun afterHistoryChange() {
        reloadAfterChange()
        withMain {
            val target = (inspector?.target as? InspectorTarget.Existing)?.noteId
            if (target != null && rows.none { it.noteId == target }) {
                inspector = null
                previews = emptyList()
            } else if (target != null) openCard(target)
        }
    }

    fun makeCopy() {
        val packId = selectedPackId ?: return
        launchWork(onFailure = ::showFailure) {
            val copyId = editor.makeCopy(packId)
            val loaded = loadPacks()
            withMain {
                packs = loaded
                notice = "Made your own copy. Your progress came with it"
                choosePack(copyId)
            }
        }
    }

    fun createPack(title: String, subtitle: String, category: String, subcategory: String, accentColor: String?) {
        launchWork(onFailure = ::showFailure) {
            val packId = editor.createPack(PackDraft(title, subtitle.ifBlank { null }, category, subcategory, accentColor))
            val loaded = loadPacks()
            withMain {
                dialog = null
                packs = loaded
                notice = "Pack created"
                choosePack(packId)
            }
        }
    }

    fun addSection(name: String) {
        val packId = selectedPackId ?: return
        launchWork(onFailure = ::showFailure) {
            editor.createSection(packId, name)
            withMain { dialog = null; selectedSection = name.trim() }
            reloadAfterChange()
        }
    }

    fun renameSection(from: String, to: String) {
        val packId = selectedPackId ?: return
        launchWork(onFailure = ::showFailure) {
            editor.renameSection(packId, from, to)
            withMain { dialog = null; selectedSection = to.trim() }
            reloadAfterChange()
        }
    }

    fun openTableImport(text: String) {
        if (readOnly || selectedPackId == null) return
        dialog = EditorDialog.ImportTable(text)
        planTable(text, defaultTableStyle(), null, null)
    }

    fun planTable(text: String, style: BertNoteType, hasHeader: Boolean?, columns: Map<Int, String>?) {
        val packId = selectedPackId ?: return
        launchWork(onFailure = { failure -> tablePlan = null; problems = listOf(failure.message ?: "That table could not be read") }) {
            val plan = editor.planTableImport(packId, text, style.name, hasHeader, columns)
            withMain { tablePlan = plan; problems = emptyList() }
        }
    }

    fun importTable() {
        val packId = selectedPackId ?: return
        val plan = tablePlan ?: return
        val drafts = plan.validDrafts.map { if (it.section == null && selectedSection != null) it.copy(section = selectedSection) else it }
        if (drafts.isEmpty()) return
        launchWork(onFailure = ::showFailure) {
            val result = editor.addCards(packId, drafts)
            withMain {
                dialog = null
                tablePlan = null
                val skipped = plan.invalidRows.size
                notice = "Added ${cardsLabel(result.added.size)}" + if (skipped > 0) ", skipped $skipped with problems" else ""
            }
            reloadAfterChange()
        }
    }

    fun showDialog(next: EditorDialog?) {
        dialog = next
        if (next == null) tablePlan = null
    }

    fun dismissNotice() {
        notice = null
    }

    fun refresh() = launchWork {
        val loaded = loadPacks()
        withMain {
            packs = loaded
            if (selectedPackId !in loaded.map { it.packId }) {
                selectedPackId = loaded.firstOrNull()?.packId
                selection = emptySet()
                inspector = null
            }
        }
        reloadAfterChange()
    }

    fun watchLibrary() = scope.launch {
        while (true) {
            delay(LIBRARY_POLL_MILLIS)
            val loaded = runCatching { withContext(worker) { loadPacks() } }.getOrNull() ?: continue
            if (loaded == packs) continue
            val selectedBefore = selectedPack
            packs = loaded
            val selectedNow = loaded.firstOrNull { it.packId == selectedPackId }
            when {
                selectedPackId == null || selectedNow == null -> loaded.firstOrNull { !it.official }?.packId ?: loaded.firstOrNull()?.packId
                else -> null
            }?.let { choosePack(it) }
            if (selectedNow != null && selectedNow != selectedBefore) refreshRows()
        }
    }

    fun availableStyles(): List<BertNoteType> {
        val general = listOf(BertNoteTypes.Basic, BertNoteTypes.Typed, BertNoteTypes.Cloze, BertNoteTypes.TwoWay, BertNoteTypes.Image, BertNoteTypes.Choice)
        val geography = if (rows.any { it.styleName == BertNoteTypes.Flag.name || it.styleName == BertNoteTypes.Capital.name }) listOf(BertNoteTypes.Flag, BertNoteTypes.Capital) else emptyList()
        return general + geography
    }

    fun defaultTableStyle(): BertNoteType = BertNoteTypes.Basic

    fun styleTitle(style: BertNoteType): String = CardStyles.title(style)

    fun styleDescription(style: BertNoteType): String = CardStyles.description(style)

    fun close() {
        worker.close()
    }

    private fun confirmLeavingInspector(): Boolean {
        val current = inspector ?: return true
        if (!current.dirty) return true
        save()
        return true
    }

    private fun refreshRows(debounce: Boolean = false) {
        val packId = selectedPackId ?: return
        queryJob?.cancel()
        queryJob = scope.launch {
            if (debounce) delay(SEARCH_DEBOUNCE_MILLIS)
            val query = currentQuery(offset = 0)
            runCatching {
                withContext(worker) {
                    val page = editor.queryCards(packId, query)
                    val packSections = editor.workspace.read(packId) { it.sections() }
                    page to packSections
                }
            }.onSuccess { (page, packSections) ->
                if (selectedPackId == packId) {
                    rows = page.rows
                    totalRows = page.total
                    tags = page.tags
                    sections = packSections
                }
            }.onFailure { showFailure(it) }
            updateHistoryFlags()
        }
    }

    private suspend fun reloadAfterChange() {
        val loaded = runCatching { loadPacks() }.getOrNull()
        withMain {
            if (loaded != null) packs = loaded
            refreshRows()
        }
    }

    private fun refreshPreview() {
        val current = inspector ?: return
        val packId = selectedPackId
        previewJob?.cancel()
        previewJob = scope.launch {
            delay(PREVIEW_DEBOUNCE_MILLIS)
            val faces = runCatching {
                withContext(worker) {
                    when (val target = current.target) {
                        is InspectorTarget.New -> CardPreviews.forDraft(target.style, current.values)
                        is InspectorTarget.Existing -> {
                            val style = BertNoteTypes.byName(current.form.styleName)
                            if (style != null) CardPreviews.forDraft(style, current.values)
                            else if (packId != null) editor.previewEdit(packId, target.noteId.toString(), current.values) else emptyList()
                        }
                    }
                }
            }.getOrDefault(emptyList())
            previews = faces
        }
    }

    private fun currentQuery(offset: Int) = CardQuery(
        search = search.ifBlank { null },
        tag = tagFilter,
        section = selectedSection,
        sort = sort,
        descending = descending,
        offset = offset,
        limit = PAGE_SIZE,
    )

    private fun loadPacks(): List<EditorPack> = editor.workspace.packIds().mapNotNull { packId ->
        runCatching {
            editor.workspace.read(packId) { pack ->
                EditorPack(packId, pack.manifest.title, pack.manifest.official, pack.collection.noteCount(), pack.manifest.accentColor)
            }
        }.getOrNull()
    }.sortedWith(compareBy({ it.official }, { it.title.lowercase() }))

    private fun updateHistoryFlags() {
        canUndo = editor.history.canUndo
        canRedo = editor.history.canRedo
    }

    private fun showFailure(failure: Throwable) {
        val editorFailure = failure as? EditorException
        notice = failure.message ?: "Something went wrong"
        problems = editorFailure?.problems?.map { problem ->
            val field = problem.field?.let { name -> inspector?.form?.field(name)?.label ?: name }
            listOfNotNull(field, problem.message).joinToString(": ")
        }.orEmpty()
    }

    private fun launchWork(onFailure: (Throwable) -> Unit = ::showFailure, block: suspend () -> Unit): Job = scope.launch {
        runCatching { withContext(worker) { block() } }.onFailure { failure -> onFailure(failure) }
        updateHistoryFlags()
    }

    private suspend fun withMain(block: () -> Unit) = withContext(Dispatchers.Main) { block() }

    private fun cardsLabel(count: Int) = if (count == 1) "1 card" else "$count cards"

    companion object {
        const val PAGE_SIZE = 200
        private const val SEARCH_DEBOUNCE_MILLIS = 180L
        private const val PREVIEW_DEBOUNCE_MILLIS = 120L
        private const val LIBRARY_POLL_MILLIS = 1500L
    }
}
