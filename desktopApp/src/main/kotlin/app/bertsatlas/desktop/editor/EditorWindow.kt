package app.bertsatlas.desktop.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.foundation.text.BasicText
import app.bertsatlas.cardrender.CardDocumentBuilder
import app.bertsatlas.cardrender.CardView
import app.bertsatlas.domain.editor.CardForms
import app.bertsatlas.domain.editor.CardRow
import app.bertsatlas.domain.editor.CardSort
import app.bertsatlas.domain.editor.FormField
import app.bertsatlas.domain.editor.FormFieldKind
import app.bertsatlas.ui.AtlasText
import app.bertsatlas.ui.AtlasTheme
import app.bertsatlas.ui.BertsAtlasTheme
import app.bertsatlas.ui.atlasCardRenderStyle
import java.awt.Dimension
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.io.File

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun EditorWindow(libraryDirectory: File, workDirectory: File, initialPackId: String?, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val controller = remember { EditorController(libraryDirectory, workDirectory, scope) }
    LaunchedEffect(Unit) {
        controller.start(initialPackId)
        controller.watchLibrary()
    }
    val state = rememberWindowState(size = DpSize(1320.dp, 860.dp))
    Window(
        onCloseRequest = {
            if (controller.inspector?.dirty == true) controller.save { onClose() } else onClose()
        },
        title = "Pack Editor",
        state = state,
    ) {
        window.minimumSize = Dimension(980, 640)
        val frame = window
        MenuBar {
            Menu("File", mnemonic = 'F') {
                Item("New Pack…", shortcut = KeyShortcut(Key.N, meta = true, shift = true)) { controller.showDialog(EditorDialog.NewPack) }
                Item("New Card…", shortcut = KeyShortcut(Key.N, meta = true), enabled = !controller.readOnly) { controller.requestNewCard() }
                Item("Save Card", shortcut = KeyShortcut(Key.S, meta = true), enabled = controller.inspector?.dirty == true) { controller.save() }
                Separator()
                Item("Make My Copy", enabled = controller.readOnly) { controller.makeCopy() }
                Separator()
                Item("Close Editor", shortcut = KeyShortcut(Key.W, meta = true)) { onClose() }
            }
            Menu("Edit", mnemonic = 'E') {
                Item("Undo", shortcut = KeyShortcut(Key.Z, meta = true), enabled = controller.canUndo) { controller.undo() }
                Item("Redo", shortcut = KeyShortcut(Key.Z, meta = true, shift = true), enabled = controller.canRedo) { controller.redo() }
                Separator()
                Item("Find Cards", shortcut = KeyShortcut(Key.F, meta = true)) { controller.focusSearch() }
                Item("Paste Cards from Table…", shortcut = KeyShortcut(Key.V, meta = true, shift = true), enabled = !controller.readOnly) {
                    controller.openTableImport(clipboardText())
                }
                Separator()
                Item("Delete Cards", shortcut = KeyShortcut(Key.Backspace, meta = true), enabled = !controller.readOnly) { controller.deleteSelected() }
            }
            Menu("Card", mnemonic = 'C') {
                Item("Next Card", shortcut = KeyShortcut(Key.DirectionDown, meta = true)) { controller.selectAdjacent(1) }
                Item("Previous Card", shortcut = KeyShortcut(Key.DirectionUp, meta = true)) { controller.selectAdjacent(-1) }
                Separator()
                Item("Tag Cards…", shortcut = KeyShortcut(Key.T, meta = true, shift = true), enabled = !controller.readOnly && controller.selection.isNotEmpty()) { controller.showDialog(EditorDialog.TagCards) }
                Item("Move to Section…", shortcut = KeyShortcut(Key.M, meta = true, shift = true), enabled = !controller.readOnly && controller.selection.isNotEmpty()) { controller.showDialog(EditorDialog.MoveCards) }
                Item("Change Field on Cards…", enabled = !controller.readOnly && controller.selection.size > 1) { controller.showDialog(EditorDialog.BulkEdit) }
                Separator()
                Item("Add Section…", enabled = !controller.readOnly) { controller.showDialog(EditorDialog.AddSection) }
            }
        }
        BertsAtlasTheme {
            Box(Modifier.fillMaxSize().background(AtlasTheme.colors.parchment)) {
                Row(Modifier.fillMaxSize()) {
                    Sidebar(controller, Modifier.width(250.dp).fillMaxHeight())
                    Box(Modifier.width(1.dp).fillMaxHeight().background(AtlasTheme.colors.outline.copy(alpha = .25f)))
                    CardTablePane(controller, Modifier.weight(1f).fillMaxHeight())
                    Box(Modifier.width(1.dp).fillMaxHeight().background(AtlasTheme.colors.outline.copy(alpha = .25f)))
                    InspectorPane(controller, frame, Modifier.width(420.dp).fillMaxHeight())
                }
                controller.notice?.let { NoticeBar(it, controller.problems, controller::dismissNotice, Modifier.align(Alignment.BottomCenter).padding(16.dp)) }
                controller.dialog?.let { EditorDialogHost(controller, it) }
            }
        }
    }
}

@Composable
private fun Sidebar(controller: EditorController, modifier: Modifier) {
    val colors = AtlasTheme.colors
    Column(modifier.background(colors.paper.copy(alpha = .45f)).padding(10.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 6.dp, top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            AtlasText("Packs", Modifier.weight(1f), style = EditorText.heading)
            EditorButton("New pack", { controller.showDialog(EditorDialog.NewPack) }, style = EditorButtonStyle.Quiet)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            val (official, mine) = controller.packs.partition { it.official }
            if (mine.isNotEmpty()) EditorSectionLabel("My packs")
            mine.forEach { PackRow(controller, it) }
            if (official.isNotEmpty()) EditorSectionLabel("Official")
            official.forEach { PackRow(controller, it) }
        }
    }
}

@Composable
private fun PackRow(controller: EditorController, pack: EditorPack) {
    val selected = pack.packId == controller.selectedPackId
    SidebarRow(
        pack.title,
        pack.cardCount.toString(),
        selected,
        { controller.choosePack(pack.packId) },
        marker = pack.accentColor.toAccent(AtlasTheme.colors.atlasBlue),
        filled = false,
    )
    if (selected) {
        SidebarRow("All cards", null, controller.selectedSection == null, { controller.chooseSection(null) }, indent = 1)
        controller.sections.forEach { section ->
            val depth = section.count { it == ':' } / 2
            SidebarRow(section.substringAfterLast("::"), null, controller.selectedSection == section, { controller.chooseSection(section) }, indent = 1 + depth)
        }
        if (!pack.official) {
            Row(Modifier.padding(start = 18.dp, top = 2.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                EditorButton("Add section", { controller.showDialog(EditorDialog.AddSection) }, style = EditorButtonStyle.Quiet)
                controller.selectedSection?.let { section ->
                    EditorButton("Rename", { controller.showDialog(EditorDialog.RenameSection(section)) }, style = EditorButtonStyle.Quiet)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun CardTablePane(controller: EditorController, modifier: Modifier) {
    val colors = AtlasTheme.colors
    val searchFocus = remember { FocusRequester() }
    val tableFocus = remember { FocusRequester() }
    LaunchedEffect(controller.searchFocusRequests) { if (controller.searchFocusRequests > 0) searchFocus.requestFocus() }
    var tableDropActive by remember { mutableStateOf(false) }
    val tableDrop = remember(controller) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) { tableDropActive = true }
            override fun onExited(event: DragAndDropEvent) { tableDropActive = false }
            override fun onEnded(event: DragAndDropEvent) { tableDropActive = false }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                tableDropActive = false
                val transferable = event.awtTransferable
                val file = droppedFiles(event).firstOrNull { it.extension.lowercase() in tableExtensions }
                val text = file?.readText() ?: runCatching { transferable.getTransferData(DataFlavor.stringFlavor) as String }.getOrNull()
                if (text.isNullOrBlank()) return false
                controller.openTableImport(text)
                return true
            }
        }
    }
    Column(modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val pack = controller.selectedPack
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                AtlasText(pack?.title ?: "No pack selected", style = AtlasTheme.typography.heading)
                AtlasText(
                    listOfNotNull(
                        "${controller.totalRows} ${if (controller.totalRows == 1) "card" else "cards"}",
                        controller.selectedSection?.let { "in $it" },
                        "read only".takeIf { pack?.official == true },
                    ).joinToString(" · "),
                    style = EditorText.small,
                    color = colors.mutedPlum.copy(alpha = .8f),
                )
            }
            EditorButton("Paste table", { controller.openTableImport(clipboardText()) }, enabled = !controller.readOnly && pack != null)
            EditorButton("New card", controller::requestNewCard, style = EditorButtonStyle.Primary, enabled = !controller.readOnly && pack != null)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EditorField(
                controller.search,
                controller::changeSearch,
                Modifier.weight(1f).focusRequester(searchFocus).onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && (event.key == Key.DirectionDown || event.key == Key.Enter)) {
                        tableFocus.requestFocus()
                        controller.selectAdjacent(0)
                        true
                    } else false
                },
                placeholder = "Search cards  ⌘F",
                label = "Search cards",
            )
            if (controller.tags.isNotEmpty()) TagFilter(controller)
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(colors.white.copy(alpha = .6f), EditorPanelShape)
                .border(if (tableDropActive) 2.dp else 1.dp, if (tableDropActive) colors.atlasBlue else colors.outline.copy(alpha = .3f), EditorPanelShape)
                .dragAndDropTarget(shouldStartDragAndDrop = { true }, target = tableDrop),
        ) {
            TableHeader(controller)
            Box(Modifier.weight(1f)) {
                val listState = rememberLazyListState()
                LaunchedEffect(listState, controller.rows.size) {
                    snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
                        if (last >= controller.rows.size - 20) controller.loadMoreRows()
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().focusRequester(tableFocus).focusable().onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown || event.isMetaPressed) return@onKeyEvent false
                        when (event.key) {
                            Key.DirectionDown -> { controller.selectAdjacent(1); true }
                            Key.DirectionUp -> { controller.selectAdjacent(-1); true }
                            else -> false
                        }
                    },
                ) {
                    itemsIndexed(controller.rows, key = { _, row -> row.noteId }) { index, row ->
                        CardTableRow(row, index, row.noteId in controller.selection) { toggle, extend ->
                            tableFocus.requestFocus()
                            controller.clickRow(row.noteId, toggle, extend)
                        }
                    }
                }
                VerticalScrollbar(rememberScrollbarAdapter(listState), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                if (controller.rows.isEmpty() && controller.selectedPack != null) {
                    Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AtlasText(if (controller.search.isNotBlank() || controller.tagFilter != null) "No cards match" else "No cards yet", style = AtlasTheme.typography.label)
                        if (!controller.readOnly && controller.search.isBlank()) {
                            AtlasText("Add one with New card, or drop a CSV file here", style = EditorText.body, color = colors.mutedPlum.copy(alpha = .8f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TagFilter(controller: EditorController) {
    var open by remember { mutableStateOf(false) }
    Box {
        EditorButton(controller.tagFilter?.let { "Tag: $it" } ?: "All tags", { open = !open })
        if (open) {
            androidx.compose.ui.window.Popup(onDismissRequest = { open = false }, offset = androidx.compose.ui.unit.IntOffset(0, 40)) {
                Column(
                    Modifier.width(240.dp).background(AtlasTheme.colors.white, EditorControlShape).border(1.dp, AtlasTheme.colors.outline.copy(alpha = .4f), EditorControlShape)
                        .padding(6.dp).height(320.dp).verticalScroll(rememberScrollState()),
                ) {
                    SidebarRow("All tags", null, controller.tagFilter == null, { controller.chooseTag(null); open = false })
                    controller.tags.forEach { tag -> SidebarRow(tag, null, controller.tagFilter == tag, { controller.chooseTag(tag); open = false }) }
                }
            }
        }
    }
}

@Composable
private fun BulkActions(controller: EditorController) {
    EditorPanel(Modifier.fillMaxWidth()) {
        AtlasText("${controller.selection.size} cards selected", style = EditorText.heading)
        AtlasText("⌘-click to add or remove cards, ⇧-click to select a range", style = EditorText.small, color = AtlasTheme.colors.mutedPlum.copy(alpha = .8f))
        if (controller.readOnly) return@EditorPanel
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EditorButton("Tag cards  ⇧⌘T", { controller.showDialog(EditorDialog.TagCards) }, Modifier.fillMaxWidth())
            EditorButton("Move to section  ⇧⌘M", { controller.showDialog(EditorDialog.MoveCards) }, Modifier.fillMaxWidth())
            EditorButton("Change a field", { controller.showDialog(EditorDialog.BulkEdit) }, Modifier.fillMaxWidth())
            EditorButton("Delete cards  ⌘⌫", controller::deleteSelected, Modifier.fillMaxWidth(), style = EditorButtonStyle.Danger)
        }
    }
}

@Composable
private fun TableHeader(controller: EditorController) {
    Row(
        Modifier.fillMaxWidth().background(AtlasTheme.colors.paper.copy(alpha = .8f)).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HeaderCell("Card", CardSort.Alphabetical, controller, Modifier.weight(3f))
        HeaderCell("Style", CardSort.Style, controller, Modifier.weight(1.6f))
        AtlasText("SECTION", Modifier.weight(1.3f), style = EditorText.overline, color = AtlasTheme.colors.mutedPlum)
        AtlasText("TAGS", Modifier.weight(1.6f), style = EditorText.overline, color = AtlasTheme.colors.mutedPlum)
        HeaderCell("Edited", CardSort.Modified, controller, Modifier.width(78.dp))
    }
}

@Composable
private fun HeaderCell(title: String, sort: CardSort, controller: EditorController, modifier: Modifier) {
    val arrow = if (controller.sort == sort) (if (controller.descending) " ↓" else " ↑") else ""
    AtlasText(
        (title + arrow).uppercase(),
        modifier.clickable { controller.chooseSort(sort) }.semantics { contentDescription = "Sort by $title" },
        style = EditorText.overline,
        color = if (controller.sort == sort) AtlasTheme.colors.plum else AtlasTheme.colors.mutedPlum,
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CardTableRow(row: CardRow, index: Int, selected: Boolean, onClick: (toggle: Boolean, extend: Boolean) -> Unit) {
    val colors = AtlasTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                when {
                    selected -> colors.seaGlass
                    index % 2 == 1 -> colors.paper.copy(alpha = .35f)
                    else -> Color.Transparent
                },
            )
            .onPointerEvent(PointerEventType.Press) { event ->
                onClick(event.keyboardModifiers.isMetaPressed, event.keyboardModifiers.isShiftPressed)
            }
            .semantics { contentDescription = "${row.styleTitle}: ${row.preview}" }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(readablePreview(row.preview).ifBlank { "(empty)" }, Modifier.weight(3f), style = EditorText.body.copy(color = colors.plum), maxLines = 1, overflow = TextOverflow.Ellipsis)
        BasicText(row.styleTitle, Modifier.weight(1.6f), style = EditorText.small.copy(color = colors.mutedPlum), maxLines = 1, overflow = TextOverflow.Ellipsis)
        BasicText(row.section?.substringAfterLast("::").orEmpty(), Modifier.weight(1.3f), style = EditorText.small.copy(color = colors.mutedPlum), maxLines = 1, overflow = TextOverflow.Ellipsis)
        BasicText(row.tags.filterNot { it.startsWith("bert::") }.joinToString(" "), Modifier.weight(1.6f), style = EditorText.small.copy(color = colors.mutedPlum), maxLines = 1, overflow = TextOverflow.Ellipsis)
        BasicText(relativeTime(row.modifiedSecs), Modifier.width(78.dp), style = EditorText.small.copy(color = colors.mutedPlum.copy(alpha = .75f)), maxLines = 1)
    }
}

@Composable
private fun InspectorPane(controller: EditorController, frame: Frame, modifier: Modifier) {
    val colors = AtlasTheme.colors
    val inspector = controller.inspector
    Column(modifier.background(colors.paper.copy(alpha = .3f))) {
        if (controller.readOnly) {
            Column(
                Modifier.fillMaxWidth().padding(14.dp).background(colors.mustard.copy(alpha = .35f), EditorControlShape).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AtlasText("Official packs can't be changed", style = EditorText.strong)
                AtlasText("Make your own copy to edit it. Your progress comes with it.", style = EditorText.body)
                EditorButton("Make my copy", controller::makeCopy, style = EditorButtonStyle.Primary)
            }
        }
        if (inspector == null) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                if (controller.selection.size > 1) BulkActions(controller)
                else AtlasText("Pick a card to edit it", style = EditorText.body, color = colors.mutedPlum.copy(alpha = .8f), textAlign = TextAlign.Center)
            }
            return@Column
        }
        val editable = !controller.readOnly
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    AtlasText(if (inspector.target is InspectorTarget.New) "New ${inspector.form.title.lowercase()} card" else inspector.form.title, style = EditorText.heading)
                    AtlasText(styleSummary(inspector.form), style = EditorText.small, color = colors.mutedPlum.copy(alpha = .8f))
                }
                if (inspector.dirty) AtlasText("Unsaved", style = EditorText.small, color = colors.orangeAccent)
            }
            PreviewPanel(controller)
            val firstField = remember(inspector.target) { FocusRequester() }
            LaunchedEffect(inspector.target) { if (inspector.target is InspectorTarget.New) runCatching { firstField.requestFocus() } }
            inspector.form.fields.forEachIndexed { index, field ->
                FieldEditor(field, inspector.values[field.name].orEmpty(), editable, frame, controller, if (index == 0) firstField else null)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AtlasText("Tags", style = EditorText.strong)
                EditorField(inspector.tags, controller::changeTags, Modifier.fillMaxWidth(), placeholder = "Separate tags with spaces", enabled = editable, label = "Tags")
            }
            if (inspector.target is InspectorTarget.New && controller.sections.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AtlasText("Section", style = EditorText.strong)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        EditorChip("Main", inspector.section == null, { controller.changeNewCardSection(null) })
                        controller.sections.forEach { section -> EditorChip(section.substringAfterLast("::"), inspector.section == section, { controller.changeNewCardSection(section) }) }
                    }
                }
            }
            if (editable) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EditorButton(if (inspector.target is InspectorTarget.New) "Add card  ⌘S" else "Save  ⌘S", { controller.save() }, style = EditorButtonStyle.Primary, enabled = inspector.dirty)
                    EditorButton(if (inspector.target is InspectorTarget.New) "Discard" else "Revert", controller::revertInspector, style = EditorButtonStyle.Quiet, enabled = inspector.dirty || inspector.target is InspectorTarget.New)
                    Spacer(Modifier.weight(1f))
                    if (inspector.target is InspectorTarget.Existing) EditorButton("Delete", controller::deleteSelected, style = EditorButtonStyle.Danger)
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun FieldEditor(field: FormField, value: String, editable: Boolean, frame: Frame, controller: EditorController, focusRequester: FocusRequester?) {
    val colors = AtlasTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AtlasText(field.label, style = EditorText.strong)
            if (field.required) AtlasText("  required", style = EditorText.small, color = colors.mutedPlum.copy(alpha = .7f))
        }
        when (field.kind) {
            FormFieldKind.Choice -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                field.choices.forEach { choice ->
                    EditorChip(choice.label, value == choice.value, { if (editable) controller.changeField(field.name, if (value == choice.value) "" else choice.value) })
                }
            }
            FormFieldKind.Image -> ImageFieldEditor(field, value, editable, frame, controller)
            else -> EditorField(
                value,
                { controller.changeField(field.name, it) },
                Modifier.fillMaxWidth(),
                singleLine = field.kind == FormFieldKind.Text,
                minLines = when (field.kind) {
                    FormFieldKind.Cloze, FormFieldKind.Masks -> 4
                    FormFieldKind.RichText, FormFieldKind.List -> 2
                    else -> 1
                },
                enabled = editable,
                monospace = field.kind == FormFieldKind.Masks,
                label = field.label,
                focusRequester = focusRequester,
            )
        }
        field.help?.let { AtlasText(it, style = EditorText.small, color = colors.mutedPlum.copy(alpha = .75f)) }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun ImageFieldEditor(field: FormField, value: String, editable: Boolean, frame: Frame, controller: EditorController) {
    val colors = AtlasTheme.colors
    var active by remember { mutableStateOf(false) }
    val target = remember(field.name, controller) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) { active = true }
            override fun onExited(event: DragAndDropEvent) { active = false }
            override fun onEnded(event: DragAndDropEvent) { active = false }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                active = false
                val image = droppedFiles(event).firstOrNull { it.extension.lowercase() in imageExtensions } ?: return false
                controller.addMediaToField(field.name, image)
                return true
            }
        }
    }
    val name = CardForms.imageName(value)
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (active) colors.seaGlass.copy(alpha = .6f) else colors.white, EditorControlShape)
            .border(if (active) 2.dp else 1.dp, if (active) colors.atlasBlue else colors.outline.copy(alpha = .6f), EditorControlShape)
            .then(if (editable) Modifier.dragAndDropTarget(shouldStartDragAndDrop = { true }, target = target) else Modifier)
            .semantics { contentDescription = "${field.label} drop zone" }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (name != null) {
            Box(Modifier.size(width = 72.dp, height = 48.dp)) {
                CardView(CardDocumentBuilder.build(value), atlasCardRenderStyle(), controller.mediaDirectory(), Modifier.fillMaxSize())
            }
        }
        Column(Modifier.weight(1f)) {
            AtlasText(name ?: "No image yet", style = EditorText.body)
            if (editable) AtlasText("Drop an image here", style = EditorText.small, color = colors.mutedPlum.copy(alpha = .75f))
        }
        if (editable) EditorButton("Choose…", {
            chooseImage(frame)?.let { controller.addMediaToField(field.name, it) }
        })
    }
}

@Composable
private fun PreviewPanel(controller: EditorController) {
    val previews = controller.previews
    var showBack by remember { mutableStateOf(false) }
    var faceIndex by remember { mutableStateOf(0) }
    val accent = controller.selectedPack?.accentColor.toAccent(AtlasTheme.colors.atlasBlue)
    EditorPanel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AtlasText("Preview", Modifier.weight(1f), style = EditorText.strong)
            EditorChip("Front", !showBack, { showBack = false })
            EditorChip("Back", showBack, { showBack = true })
        }
        if (previews.size > 1) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                previews.forEachIndexed { index, face -> EditorChip(face.name, faceIndex == index, { faceIndex = index }) }
            }
        }
        val face = previews.getOrNull(faceIndex.coerceAtMost(previews.lastIndex.coerceAtLeast(0)))
        if (face == null) {
            AtlasText("Fill in the required fields to see the card", style = EditorText.body, color = AtlasTheme.colors.mutedPlum.copy(alpha = .75f))
        } else {
            val html = if (showBack) face.backHtml else face.frontHtml
            Box(Modifier.fillMaxWidth().background(AtlasTheme.colors.parchment, EditorPanelShape).padding(18.dp)) {
                CardView(CardDocumentBuilder.build(html), atlasCardRenderStyle(accent), controller.mediaDirectory(), Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun NoticeBar(message: String, problems: List<String>, onDismiss: () -> Unit, modifier: Modifier) {
    LaunchedEffect(message, problems) {
        if (problems.isEmpty()) {
            kotlinx.coroutines.delay(4000)
            onDismiss()
        }
    }
    Column(
        modifier
            .width(560.dp)
            .background(AtlasTheme.colors.plum, EditorControlShape)
            .clickable(onClick = onDismiss)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AtlasText(message.replaceFirstChar { it.uppercase() }, style = EditorText.strong, color = AtlasTheme.colors.white)
        problems.take(6).forEach { AtlasText("• $it", style = EditorText.small, color = AtlasTheme.colors.white) }
        if (problems.size > 6) AtlasText("and ${problems.size - 6} more", style = EditorText.small, color = AtlasTheme.colors.white)
    }
}

private val clozePattern = Regex("""\{\{c\d+::(.*?)(::[^}]*)?\}\}""")

fun readablePreview(text: String): String = text.replace(clozePattern, "$1")

fun String?.toAccent(fallback: Color): Color {
    val hex = this?.trim()?.removePrefix("#") ?: return fallback
    val value = hex.toLongOrNull(16) ?: return fallback
    return when (hex.length) {
        6 -> Color(0xFF000000 or value)
        8 -> Color(value)
        else -> fallback
    }
}

@OptIn(ExperimentalComposeUiApi::class)
fun droppedFiles(event: DragAndDropEvent): List<File> =
    runCatching { (event.awtTransferable.getTransferData(DataFlavor.javaFileListFlavor) as List<*>).filterIsInstance<File>() }.getOrDefault(emptyList())

fun clipboardText(): String =
    runCatching { Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as String }.getOrDefault("")

private fun chooseImage(frame: Frame): File? {
    val dialog = FileDialog(frame, "Choose an image", FileDialog.LOAD).apply {
        setFilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase() in imageExtensions }
        isVisible = true
    }
    val directory = dialog.directory ?: return null
    val name = dialog.file ?: return null
    return File(directory, name)
}

private fun relativeTime(seconds: Long): String {
    if (seconds <= 0) return ""
    val elapsed = System.currentTimeMillis() / 1000 - seconds
    return when {
        elapsed < 60 -> "just now"
        elapsed < 3600 -> "${elapsed / 60}m ago"
        elapsed < 86_400 -> "${elapsed / 3600}h ago"
        elapsed < 86_400 * 30 -> "${elapsed / 86_400}d ago"
        else -> java.time.LocalDate.ofEpochDay(seconds / 86_400).toString()
    }
}

private val imageExtensions = setOf("png", "jpg", "jpeg", "gif", "webp", "svg", "bmp")
private val tableExtensions = setOf("csv", "tsv", "txt")
