package app.bertsatlas.ui.editor.mobile

import app.bertsatlas.ImagePicker
import app.bertsatlas.data.PackStore
import app.bertsatlas.domain.editor.CardChange
import app.bertsatlas.domain.editor.CardDraft
import app.bertsatlas.domain.editor.CardFacePreview
import app.bertsatlas.domain.editor.CardForm
import app.bertsatlas.domain.editor.CardForms
import app.bertsatlas.domain.editor.CardPreviews
import app.bertsatlas.domain.editor.CardQuery
import app.bertsatlas.domain.editor.CardRow
import app.bertsatlas.domain.editor.CardStyles
import app.bertsatlas.domain.editor.EditorException
import app.bertsatlas.domain.editor.EditorFailureKind
import app.bertsatlas.domain.editor.FormFieldKind
import app.bertsatlas.domain.editor.PackAppearance
import app.bertsatlas.domain.editor.PackDraft
import app.bertsatlas.domain.editor.PackEditor
import app.bertsatlas.domain.editor.PackPlacement
import app.bertsatlas.domain.editor.PackWorkspace
import app.bertsatlas.domain.pack.BertNoteType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.io.files.Path

data class PackForm(
    val title: String = "",
    val subtitle: String = "",
    val categoryId: String = "",
    val subcategoryId: String = "",
    val accentColor: String = PackAccents.all.first(),
    val coverMedia: String? = null,
    val pickedCoverPath: String? = null,
) {
    val ready: Boolean get() = title.isNotBlank() && categoryId.isNotBlank() && subcategoryId.isNotBlank()
}

data class CardEditorState(
    val packId: String,
    val reference: String?,
    val form: CardForm,
    val values: Map<String, String>,
    val section: String?,
    val originalSection: String?,
    val sections: List<String>,
    val previews: List<CardFacePreview>,
    val confirmingDelete: Boolean = false,
) {
    val isNew: Boolean get() = reference == null
    val missing: List<String> get() = form.missingRequiredFields(storedValues()).map { it.label }

    fun storedValues(): Map<String, String> = form.fields.associate { field -> field.name to FieldText.toStored(field.kind, values[field.name].orEmpty()) }
}

sealed interface EditorPage {
    data class CreatePack(val form: PackForm) : EditorPage
    data class PackDetails(val packId: String, val form: PackForm, val confirmingDelete: Boolean = false) : EditorPage
    data class Cards(val packId: String, val title: String, val search: String = "", val rows: List<CardRow> = emptyList(), val total: Int = 0, val loading: Boolean = true) : EditorPage
    data class ChooseStyle(val packId: String, val section: String?) : EditorPage
    data class EditCard(val card: CardEditorState) : EditorPage
}

data class EditorState(
    val pages: List<EditorPage>,
    val busy: Boolean = false,
    val problem: String? = null,
) {
    val page: EditorPage get() = pages.last()
}

object PackAccents {
    val all = listOf("#27635B", "#3E5C8A", "#B4532A", "#7A4E8C", "#C0843A", "#2F7E9E", "#A23B5C", "#5B7F3A")
}

object FieldText {
    fun toStored(kind: FormFieldKind, text: String): String = when (kind) {
        FormFieldKind.RichText, FormFieldKind.Cloze -> text.trim().replace("\n", "<br>")
        FormFieldKind.List -> text.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
        else -> text.trim()
    }

    fun toEditable(kind: FormFieldKind, stored: String): String = when (kind) {
        FormFieldKind.RichText, FormFieldKind.Cloze -> stored.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        else -> stored
    }
}

class PackEditingController(
    private val packStore: PackStore,
    private val scope: CoroutineScope,
    private val imagePicker: ImagePicker,
    private val onLibraryChanged: suspend () -> Unit,
    private val onOpenPack: (String) -> Unit,
    private val onLeave: () -> Unit,
) {
    private val editor = PackEditor(PackWorkspace(packStore.libraryDirectory, Path(packStore.libraryDirectory, "incoming").toString()))
    private val _state = MutableStateFlow<EditorState?>(null)
    val state: StateFlow<EditorState?> = _state.asStateFlow()
    private var searchJob: Job? = null
    private var previewJob: Job? = null

    fun startCreatingPack() {
        _state.value = EditorState(listOf(EditorPage.CreatePack(PackForm())))
    }

    fun openPackDetails(packId: String) {
        _state.value = EditorState(emptyList())
        runEdit("Opening the pack") {
            val form = io { editor.workspace.read(packId) { pack ->
                val manifest = pack.manifest
                PackForm(
                    title = manifest.title,
                    subtitle = manifest.subtitle.orEmpty(),
                    categoryId = manifest.category.orEmpty(),
                    subcategoryId = manifest.subcategory.orEmpty(),
                    accentColor = manifest.accentColor ?: PackAccents.all.first(),
                    coverMedia = manifest.coverMedia,
                )
            } }
            push(EditorPage.PackDetails(packId, form))
        }
    }

    fun openCards(packId: String, title: String) {
        _state.value = EditorState(listOf(EditorPage.Cards(packId, title)))
        reloadCards()
    }

    fun back(): Boolean {
        val current = _state.value ?: return false
        if (current.busy) return true
        when (val page = current.page) {
            is EditorPage.PackDetails -> if (page.confirmingDelete) return replace(page.copy(confirmingDelete = false))
            is EditorPage.EditCard -> if (page.card.confirmingDelete) return replace(EditorPage.EditCard(page.card.copy(confirmingDelete = false)))
            else -> Unit
        }
        if (current.pages.size <= 1) {
            _state.value = null
            onLeave()
            return false
        }
        _state.value = current.copy(pages = current.pages.dropLast(1), problem = null)
        if (_state.value?.page is EditorPage.Cards) reloadCards()
        return true
    }

    fun dismissProblem() = update { it.copy(problem = null) }

    fun changePackForm(change: (PackForm) -> PackForm) = updatePage { page ->
        when (page) {
            is EditorPage.CreatePack -> page.copy(form = change(page.form))
            is EditorPage.PackDetails -> page.copy(form = change(page.form))
            else -> page
        }
    }

    fun pickCover() {
        imagePicker.pickImage { path -> if (path != null) changePackForm { it.copy(pickedCoverPath = path) } }
    }

    fun savePack() {
        when (val page = _state.value?.page) {
            is EditorPage.CreatePack -> createPack(page.form)
            is EditorPage.PackDetails -> savePackDetails(page.packId, page.form)
            else -> Unit
        }
    }

    private fun createPack(form: PackForm) {
        if (!form.ready) return
        runEdit("Making your pack") {
            val packId = io {
                val packId = editor.createPack(PackDraft(form.title, form.subtitle, form.categoryId, form.subcategoryId, form.accentColor))
                form.pickedCoverPath?.let { path ->
                    val cover = editor.addMedia(packId, path).name
                    editor.updateAppearance(packId, PackAppearance(form.title, form.subtitle, form.accentColor, cover))
                }
                editor.history.clear()
                packId
            }
            onLibraryChanged()
            _state.value = EditorState(listOf(EditorPage.Cards(packId, form.title.trim())))
            reloadCards()
        }
    }

    private fun savePackDetails(packId: String, form: PackForm) {
        if (!form.ready) return
        runEdit("Saving") {
            io {
                val cover = form.pickedCoverPath?.let { editor.addMedia(packId, it).name } ?: form.coverMedia
                editor.updateAppearance(packId, PackAppearance(form.title, form.subtitle, form.accentColor, cover))
                editor.updatePlacement(packId, PackPlacement(form.categoryId, form.subcategoryId))
            }
            onLibraryChanged()
            popOrLeave()
        }
    }

    fun requestPackDeletion() = updatePage { if (it is EditorPage.PackDetails) it.copy(confirmingDelete = true) else it }

    fun deletePack() {
        val page = _state.value?.page as? EditorPage.PackDetails ?: return
        runEdit("Deleting") {
            io { editor.deletePack(page.packId) }
            onLibraryChanged()
            _state.value = null
            onLeave()
        }
    }

    fun makeCopy(packId: String) {
        _state.value = EditorState(emptyList())
        runEdit("Making your copy") {
            val copyId = io { editor.makeCopy(packId) }
            onLibraryChanged()
            _state.value = null
            onOpenPack(copyId)
        }
    }

    fun search(text: String) {
        updatePage { if (it is EditorPage.Cards) it.copy(search = text) else it }
        searchJob?.cancel()
        searchJob = scope.launch {
            delay(250)
            reloadCards()
        }
    }

    private fun reloadCards() {
        val page = _state.value?.page as? EditorPage.Cards ?: return
        scope.launch {
            val result = runCatching {
                packStore.edit { editor.queryCards(page.packId, CardQuery(search = page.search.ifBlank { null }, limit = CARD_LIST_LIMIT)) }
            }
            updatePage { current ->
                if (current !is EditorPage.Cards || current.packId != page.packId) current
                else result.fold(
                    { cards -> current.copy(rows = cards.rows, total = cards.total, loading = false) },
                    { current.copy(loading = false) },
                )
            }
        }
    }

    fun chooseNewCardStyle(packId: String) = push(EditorPage.ChooseStyle(packId, null))

    fun startNewCard(packId: String, style: BertNoteType) {
        runEdit(null) {
            val sections = io { editor.workspace.read(packId) { it.sections() } }
            val form = CardForms.forStyle(style)
            val card = CardEditorState(packId, null, form, emptyMap(), null, null, sections, emptyList())
            push(EditorPage.EditCard(card.withPreviews()))
        }
    }

    fun openCard(packId: String, reference: String) {
        runEdit(null) {
            val (editable, sections) = io { editor.editableCard(packId, reference) to editor.workspace.read(packId) { it.sections() } }
            val values = editable.form.fields.associate { field -> field.name to FieldText.toEditable(field.kind, editable.values[field.name].orEmpty()) }
            val card = CardEditorState(packId, editable.ref, editable.form, values, editable.section, editable.section, sections, emptyList())
            push(EditorPage.EditCard(card))
            refreshPreview()
        }
    }

    fun changeField(name: String, value: String) {
        updateCard { it.copy(values = it.values + (name to value)) }
        refreshPreview()
    }

    fun chooseSection(section: String?) = updateCard { card ->
        val cleaned = section?.trim()?.ifEmpty { null }
        card.copy(section = cleaned, sections = if (cleaned != null && cleaned !in card.sections) card.sections + cleaned else card.sections)
    }

    fun attachImage(fieldName: String) {
        val card = currentCard() ?: return
        imagePicker.pickImage { path ->
            if (path == null) return@pickImage
            runEdit("Adding the picture") {
                val media = io { editor.addMedia(card.packId, path) }
                changeField(fieldName, CardForms.imageValue(media.name))
            }
        }
    }

    fun saveCard() {
        val card = currentCard() ?: return
        if (card.missing.isNotEmpty()) {
            update { it.copy(problem = "Fill in ${card.missing.joinToString(" and ")} first.") }
            return
        }
        runEdit("Saving") {
            val stored = card.storedValues()
            io {
                if (card.reference == null) {
                    editor.addCards(card.packId, listOf(CardDraft(card.form.styleName, stored, section = card.section)))
                } else {
                    editor.editCard(card.packId, card.reference, CardChange(fields = stored))
                    if (card.section != card.originalSection) editor.moveCards(card.packId, listOf(card.reference), card.section)
                }
            }
            onLibraryChanged()
            popOrLeave()
        }
    }

    fun requestCardDeletion() = updateCard { it.copy(confirmingDelete = true) }

    fun deleteCard() {
        val card = currentCard() ?: return
        val reference = card.reference ?: return
        runEdit("Deleting") {
            io { editor.deleteCards(card.packId, listOf(reference)) }
            onLibraryChanged()
            popOrLeave()
        }
    }

    fun mediaDirectory(packId: String): String = packStore.mediaDirectory(packId)

    private fun refreshPreview() {
        val card = currentCard() ?: return
        val style = CardStyles.resolve(card.form.styleName)
        if (style != null && !card.form.isGeneric) {
            updateCard { it.withPreviews() }
            return
        }
        val reference = card.reference ?: return
        previewJob?.cancel()
        previewJob = scope.launch {
            delay(300)
            val previews = runCatching { packStore.edit { editor.previewEdit(card.packId, reference, card.storedValues()) } }.getOrDefault(emptyList())
            updateCard { it.copy(previews = previews) }
        }
    }

    private fun CardEditorState.withPreviews(): CardEditorState {
        val style = CardStyles.resolve(form.styleName) ?: return this
        return copy(previews = runCatching { CardPreviews.forDraft(style, storedValues()) }.getOrDefault(emptyList()))
    }

    private fun currentCard(): CardEditorState? = (_state.value?.page as? EditorPage.EditCard)?.card

    private fun updateCard(change: (CardEditorState) -> CardEditorState) = updatePage { if (it is EditorPage.EditCard) EditorPage.EditCard(change(it.card)) else it }

    private fun popOrLeave() {
        val current = _state.value ?: return
        if (current.pages.size <= 1) {
            _state.value = null
            onLeave()
            return
        }
        _state.value = current.copy(pages = current.pages.dropLast(1).let { pages ->
            if (pages.lastOrNull() is EditorPage.ChooseStyle && pages.size > 1) pages.dropLast(1) else pages
        }, busy = false, problem = null)
        reloadCards()
    }

    private fun push(page: EditorPage) = update { it.copy(pages = it.pages + page, problem = null) }

    private fun replace(page: EditorPage): Boolean {
        update { it.copy(pages = it.pages.dropLast(1) + page) }
        return true
    }

    private fun updatePage(change: (EditorPage) -> EditorPage) = update { state ->
        if (state.pages.isEmpty()) state else state.copy(pages = state.pages.dropLast(1) + change(state.pages.last()))
    }

    private fun update(change: (EditorState) -> EditorState) {
        _state.value = _state.value?.let(change)
    }

    private fun runEdit(busyLabel: String?, block: suspend () -> Unit) {
        update { it.copy(busy = busyLabel != null, problem = null) }
        scope.launch {
            try {
                block()
            } catch (failure: EditorException) {
                update { it.copy(problem = failure.friendlyMessage()) }
            } catch (failure: Throwable) {
                update { it.copy(problem = "Something went wrong. Nothing was changed.") }
            } finally {
                update { it.copy(busy = false) }
                if (_state.value?.pages?.isEmpty() == true) {
                    _state.value = null
                    onLeave()
                }
            }
        }
    }

    private suspend fun <T> io(block: () -> T): T = packStore.edit(block)

    private fun EditorException.friendlyMessage(): String = when (kind) {
        EditorFailureKind.ReadOnly -> "This is an official pack. Make your own copy to change it."
        EditorFailureKind.Invalid -> problems.firstOrNull()?.message?.replaceFirstChar { it.uppercase() } ?: "Check the card and try again."
        EditorFailureKind.Conflict -> "A pack with that name already exists."
        else -> "Something went wrong. Nothing was changed."
    }

    private companion object {
        const val CARD_LIST_LIMIT = 300
    }
}
