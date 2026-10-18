package app.bertsatlas.desktop.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.bertsatlas.domain.editor.BulkFieldEdit
import app.bertsatlas.domain.editor.CardForm
import app.bertsatlas.domain.editor.CardForms
import androidx.compose.foundation.focusable
import app.bertsatlas.domain.editor.TableImport
import app.bertsatlas.domain.pack.BertNoteType
import app.bertsatlas.domain.pack.BertNoteTypes
import app.bertsatlas.domain.taxonomy.BundledTaxonomy
import app.bertsatlas.ui.AtlasText
import app.bertsatlas.ui.AtlasTheme

@Composable
fun EditorDialogHost(controller: EditorController, dialog: EditorDialog) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x66000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { controller.showDialog(null) }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    controller.showDialog(null)
                    true
                } else false
            },
        contentAlignment = Alignment.Center,
    ) {
        when (dialog) {
            EditorDialog.NewPack -> NewPackDialog(controller)
            EditorDialog.ChooseStyle -> ChooseStyleDialog(controller)
            EditorDialog.TagCards -> TagDialog(controller)
            EditorDialog.MoveCards -> MoveDialog(controller)
            EditorDialog.BulkEdit -> BulkEditDialog(controller)
            EditorDialog.AddSection -> SectionNameDialog(controller, "Add a section", "", "Add") { controller.addSection(it) }
            is EditorDialog.RenameSection -> SectionNameDialog(controller, "Rename section", dialog.section, "Rename") { controller.renameSection(dialog.section, it) }
            is EditorDialog.ImportTable -> TableImportDialog(controller, dialog.text)
        }
    }
}

@Composable
private fun DialogPanel(title: String, width: Int = 520, onEnter: (() -> Unit)? = null, enterNeedsCommand: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val panelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { panelFocus.requestFocus() } }
    Column(
        Modifier
            .focusRequester(panelFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                val confirm = onEnter
                if (confirm != null && event.type == KeyEventType.KeyDown && event.key == Key.Enter && (!enterNeedsCommand || event.isMetaPressed)) {
                    confirm()
                    true
                } else false
            }
            .width(width.dp)
            .heightIn(max = 760.dp)
            .background(AtlasTheme.colors.parchment, EditorPanelShape)
            .border(1.dp, AtlasTheme.colors.outline.copy(alpha = .4f), EditorPanelShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AtlasText(title, style = AtlasTheme.typography.heading)
        content()
    }
}

@Composable
private fun DialogActions(confirm: String, enabled: Boolean, onCancel: () -> Unit, onConfirm: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        EditorButton("Cancel", onCancel, style = EditorButtonStyle.Quiet)
        EditorButton(confirm, onConfirm, style = EditorButtonStyle.Primary, enabled = enabled)
    }
}

@Composable
private fun Labeled(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AtlasText(label, style = EditorText.strong)
        content()
    }
}

@Composable
private fun NewPackDialog(controller: EditorController) {
    var title by remember { mutableStateOf("") }
    var subtitle by remember { mutableStateOf("") }
    val categories = BundledTaxonomy.taxonomy.categories
    var categoryId by remember { mutableStateOf(categories.first().id) }
    val category = categories.first { it.id == categoryId }
    var subcategoryId by remember(categoryId) { mutableStateOf(category.subcategories.first().id) }
    var accent by remember(categoryId) { mutableStateOf(category.accentColor.ifBlank { accentPalette.first() }) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val createPack = { if (title.isNotBlank()) controller.createPack(title, subtitle, categoryId, subcategoryId, accent) }
    DialogPanel("New pack", width = 560, onEnter = createPack) {
        Labeled("Title") { EditorField(title, { title = it }, Modifier.fillMaxWidth().focusRequester(focus), placeholder = "For example, Spanish verbs", label = "Pack title") }
        Labeled("Subtitle") { EditorField(subtitle, { subtitle = it }, Modifier.fillMaxWidth(), placeholder = "A line about what's inside", label = "Pack subtitle") }
        Labeled("Shelf") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                categories.forEach { EditorChip(it.name, it.id == categoryId, { categoryId = it.id }) }
            }
        }
        Labeled("Topic") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                category.subcategories.forEach { EditorChip(it.name, it.id == subcategoryId, { subcategoryId = it.id }) }
            }
        }
        Labeled("Colour") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf(category.accentColor).filter { it.isNotBlank() } + accentPalette).distinct().forEach { hex ->
                    val selected = hex.equals(accent, ignoreCase = true)
                    Box(
                        Modifier
                            .size(30.dp)
                            .border(if (selected) 3.dp else 0.dp, AtlasTheme.colors.plum, CircleShape)
                            .padding(if (selected) 4.dp else 0.dp)
                            .background(hex.toAccent(AtlasTheme.colors.atlasBlue), CircleShape)
                            .semantics { contentDescription = "Colour $hex" }
                            .clickable { accent = hex },
                    )
                }
            }
        }
        DialogActions("Create pack", title.isNotBlank(), { controller.showDialog(null) }) { createPack() }
    }
}

@Composable
private fun ChooseStyleDialog(controller: EditorController) {
    val styles = controller.availableStyles()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    DialogPanel("What kind of card?", width = 520) {
        Column(
            Modifier.focusRequester(focus).focusable().onPreviewKeyEvent { event ->
                val index = digitKeys.indexOf(event.key)
                if (event.type == KeyEventType.KeyDown && index in styles.indices) {
                    controller.startNewCard(styles[index])
                    true
                } else false
            },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
        styles.forEachIndexed { number, style ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(AtlasTheme.colors.paper, EditorControlShape)
                    .border(1.dp, AtlasTheme.colors.outline.copy(alpha = .4f), EditorControlShape)
                    .clickable { controller.startNewCard(style) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                AtlasText("${number + 1}  ${controller.styleTitle(style)}", style = EditorText.strong)
                AtlasText(styleSummary(style), style = EditorText.small, color = AtlasTheme.colors.mutedPlum.copy(alpha = .8f))
            }
        }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            EditorButton("Cancel", { controller.showDialog(null) }, style = EditorButtonStyle.Quiet)
        }
    }
}

private val digitKeys = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)

fun styleSummary(form: CardForm): String = BertNoteTypes.byName(form.styleName)?.let(::styleSummary) ?: form.description

fun styleSummary(style: BertNoteType): String = when (style) {
    BertNoteTypes.Basic -> "Ask a question, reveal a short answer"
    BertNoteTypes.Typed -> "Ask a question, type the exact answer"
    BertNoteTypes.Cloze -> "Hide words in a sentence and fill them in"
    BertNoteTypes.TwoWay -> "Learn a pair both ways round"
    BertNoteTypes.Image -> "Show a picture, name what it is"
    BertNoteTypes.Flag -> "Show a flag, name the country"
    BertNoteTypes.Capital -> "Name the capital of a country"
    BertNoteTypes.Choice -> "Ask a question, pick from your own options"
    else -> CardForms.forStyle(style).description
}

@Composable
private fun TagDialog(controller: EditorController) {
    var add by remember { mutableStateOf("") }
    var remove by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val apply = { if (add.isNotBlank() || remove.isNotBlank()) controller.tagSelected(add.splitTags(), remove.splitTags()) }
    DialogPanel("Tag ${controller.selection.size} cards", onEnter = apply) {
        Labeled("Add tags") { EditorField(add, { add = it }, Modifier.fillMaxWidth().focusRequester(focus), placeholder = "Separate tags with spaces", label = "Tags to add") }
        Labeled("Remove tags") { EditorField(remove, { remove = it }, Modifier.fillMaxWidth(), placeholder = "Separate tags with spaces", label = "Tags to remove") }
        if (controller.tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                controller.tags.filterNot { it.startsWith("bert::") }.take(24).forEach { tag ->
                    EditorChip(tag, false, { add = (add.split(" ").filter { it.isNotBlank() } + tag).distinct().joinToString(" ") })
                }
            }
        }
        DialogActions("Apply", add.isNotBlank() || remove.isNotBlank(), { controller.showDialog(null) }) { apply() }
    }
}

@Composable
private fun MoveDialog(controller: EditorController) {
    var choice by remember { mutableStateOf<String?>(null) }
    var newSection by remember { mutableStateOf("") }
    val sectionFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { sectionFocus.requestFocus() }
    val move = { controller.moveSelected(newSection.trim().ifBlank { choice }) }
    DialogPanel("Move ${controller.selection.size} cards", onEnter = move) {
        Labeled("To section") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                EditorChip("Main", choice == null && newSection.isBlank(), { choice = null; newSection = "" })
                controller.sections.forEach { section -> EditorChip(section, choice == section, { choice = section; newSection = "" }) }
            }
        }
        Labeled("Or a new section") { EditorField(newSection, { newSection = it; if (it.isNotBlank()) choice = null }, Modifier.fillMaxWidth().focusRequester(sectionFocus), placeholder = "Section name", label = "New section") }
        DialogActions("Move", true, { controller.showDialog(null) }) { move() }
    }
}

@Composable
private fun BulkEditDialog(controller: EditorController) {
    val fields = controller.rows.filter { it.noteId in controller.selection }.map { it.styleName }.distinct()
        .mapNotNull { BertNoteTypes.byName(it) }.flatMap { CardForms.forStyle(it).fields }
        .filter { it.kind != app.bertsatlas.domain.editor.FormFieldKind.Image && it.kind != app.bertsatlas.domain.editor.FormFieldKind.Masks }
        .distinctBy { it.name }
    var fieldName by remember { mutableStateOf<String?>(null) }
    var replaceMode by remember { mutableStateOf(false) }
    var find by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    DialogPanel("Change ${controller.selection.size} cards") {
        Labeled("Field") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                fields.forEach { field -> EditorChip(field.label, fieldName == field.name, { fieldName = field.name }) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EditorChip("Set to", !replaceMode, { replaceMode = false })
            EditorChip("Find and replace", replaceMode, { replaceMode = true })
        }
        if (replaceMode) Labeled("Find") { EditorField(find, { find = it }, Modifier.fillMaxWidth(), label = "Find text") }
        Labeled(if (replaceMode) "Replace with" else "New value") { EditorField(value, { value = it }, Modifier.fillMaxWidth(), label = "Value") }
        DialogActions("Apply", fieldName != null && (!replaceMode || find.isNotEmpty()), { controller.showDialog(null) }) {
            val name = fieldName ?: return@DialogActions
            controller.bulkEditSelected(if (replaceMode) BulkFieldEdit.ReplaceText(name, find, value) else BulkFieldEdit.SetValue(name, value))
        }
    }
}

@Composable
private fun SectionNameDialog(controller: EditorController, title: String, initial: String, confirm: String, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val confirmName = { if (name.isNotBlank() && name.trim() != initial) onConfirm(name) }
    DialogPanel(title, width = 440, onEnter = confirmName) {
        EditorField(name, { name = it }, Modifier.fillMaxWidth().focusRequester(focus), placeholder = "Section name", label = "Section name")
        AtlasText("Use :: to nest, for example Verbs::Irregular", style = EditorText.small, color = AtlasTheme.colors.mutedPlum.copy(alpha = .75f))
        DialogActions(confirm, name.isNotBlank() && name.trim() != initial, { controller.showDialog(null) }) { confirmName() }
    }
}

@Composable
private fun TableImportDialog(controller: EditorController, initialText: String) {
    var text by remember { mutableStateOf(initialText) }
    var style by remember { mutableStateOf(controller.defaultTableStyle()) }
    var header by remember { mutableStateOf<Boolean?>(null) }
    var columns by remember { mutableStateOf<Map<Int, String>?>(null) }
    LaunchedEffect(text, style, header, columns) { controller.planTable(text, style, header, columns) }
    val plan = controller.tablePlan
    val table = remember(text) { runCatching { TableImport.parse(text) }.getOrNull() }
    val form = CardForms.forStyle(style)
    val fieldOptions = form.fields.filter { it.kind != app.bertsatlas.domain.editor.FormFieldKind.Masks }.map { it.name to it.label } +
        listOf(TableImport.TAGS_COLUMN to "Tags", TableImport.SECTION_COLUMN to "Section")
    DialogPanel("Add cards from a table", width = 860, onEnter = { controller.importTable() }, enterNeedsCommand = true) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Labeled("Paste rows from a spreadsheet or CSV") {
                EditorField(text, { text = it; columns = null }, Modifier.fillMaxWidth().heightIn(max = 150.dp), singleLine = false, minLines = 4, monospace = true, label = "Table text")
            }
            Labeled("Card style") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    controller.availableStyles().forEach { option -> EditorChip(controller.styleTitle(option), option == style, { style = option; columns = null }) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                AtlasText("First row is", style = EditorText.strong)
                EditorChip("Detect", header == null, { header = null; columns = null })
                EditorChip("Column names", header == true, { header = true; columns = null })
                EditorChip("A card", header == false, { header = false; columns = null })
            }
            if (plan != null && table != null) {
                Labeled("Columns") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        (0 until table.columnCount).forEach { column ->
                            val sample = table.rows.getOrNull(if (plan.hasHeader) 1 else 0)?.getOrNull(column).orEmpty()
                            val heading = if (plan.hasHeader) table.rows.first().getOrNull(column).orEmpty() else "Column ${column + 1}"
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AtlasText(heading.ifBlank { "Column ${column + 1}" }, Modifier.width(150.dp), style = EditorText.strong)
                                AtlasText(sample.take(28), Modifier.width(180.dp), style = EditorText.small, color = AtlasTheme.colors.mutedPlum)
                                FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    EditorChip("Skip", plan.columnFields[column] == null, { columns = plan.columnFields - column })
                                    fieldOptions.forEach { (name, label) ->
                                        EditorChip(label, plan.columnFields[column] == name, { columns = (plan.columnFields - column) + (column to name) })
                                    }
                                }
                            }
                        }
                    }
                }
                Labeled("${plan.validDrafts.size} ready, ${plan.invalidRows.size} with problems") {
                    Column(
                        Modifier.fillMaxWidth().background(AtlasTheme.colors.white.copy(alpha = .7f), EditorControlShape).padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        val shown = (plan.invalidRows + plan.rows.filter { it.isValid }.take(8)).sortedBy { it.rowNumber }
                        shown.forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                AtlasText("Row ${row.rowNumber}", Modifier.width(70.dp), style = EditorText.small, color = AtlasTheme.colors.mutedPlum)
                                AtlasText(
                                    row.draft.fields.values.joinToString("  ·  ").take(90).ifBlank { "(empty)" },
                                    Modifier.weight(1f),
                                    style = EditorText.small,
                                )
                                if (!row.isValid) AtlasText(row.problems.joinToString { it.message }, style = EditorText.small, color = AtlasTheme.colors.orangeAccent)
                            }
                        }
                        if (plan.rows.count { it.isValid } > 8) AtlasText("and ${plan.rows.count { it.isValid } - 8} more ready", style = EditorText.small, color = AtlasTheme.colors.mutedPlum)
                    }
                }
            } else if (controller.problems.isNotEmpty()) {
                controller.problems.forEach { AtlasText(it, style = EditorText.small, color = AtlasTheme.colors.orangeAccent) }
            }
        }
        Spacer(Modifier.widthIn(min = 1.dp))
        val count = plan?.validDrafts?.size ?: 0
        DialogActions(if (count == 1) "Add 1 card  ⌘↩" else "Add $count cards  ⌘↩", count > 0, { controller.showDialog(null) }) { controller.importTable() }
    }
}

private fun String.splitTags(): List<String> = split(Regex("[\\s,]+")).filter { it.isNotBlank() }

private val accentPalette = listOf("#27635B", "#3E5C8A", "#B4532A", "#7A4E8C", "#C08A1E", "#2E7D9A", "#A23B5B")
