package app.bertopia.ui.editor.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.bertopia.cardrender.CardDocumentBuilder
import app.bertopia.cardrender.CardView
import app.bertopia.cardrender.MediaPainterState
import app.bertopia.cardrender.rememberMediaPainter
import app.bertopia.domain.editor.CardRow
import app.bertopia.domain.editor.CardStyles
import app.bertopia.domain.editor.FormField
import app.bertopia.domain.editor.FormFieldKind
import app.bertopia.domain.taxonomy.BundledTaxonomy
import app.bertopia.ui.BertopiaButton
import app.bertopia.ui.BertopiaButtonStyle
import app.bertopia.ui.BertopiaCard
import app.bertopia.ui.BertopiaHeading
import app.bertopia.ui.BertopiaScreenScaffold
import app.bertopia.ui.BertopiaText
import app.bertopia.ui.BertopiaTheme
import app.bertopia.ui.BootstrapScreen
import app.bertopia.ui.bertopiaCardRenderStyle
import kotlinx.io.files.Path

@Composable
fun PackEditorDestination(controller: PackEditingController, state: EditorState?) {
    if (state == null || state.pages.isEmpty()) {
        BootstrapScreen("Opening the editor…")
        return
    }
    Box(Modifier.fillMaxSize()) {
        when (val page = state.page) {
            is EditorPage.CreatePack -> PackFormScreen("New pack", page.form, null, controller, isNew = true)
            is EditorPage.PackDetails -> PackFormScreen("Edit pack", page.form, page.packId, controller, isNew = false, confirmingDelete = page.confirmingDelete)
            is EditorPage.Cards -> CardListScreen(page, controller)
            is EditorPage.ChooseStyle -> StylePickerScreen(page, controller)
            is EditorPage.EditCard -> CardEditorScreen(page.card, controller)
        }
        state.problem?.let { problem -> ProblemBanner(problem, controller::dismissProblem, Modifier.align(Alignment.BottomCenter)) }
        if (state.busy) BusyOverlay()
    }
}

@Composable
private fun PackFormScreen(
    title: String,
    form: PackForm,
    packId: String?,
    controller: PackEditingController,
    isNew: Boolean,
    confirmingDelete: Boolean = false,
) {
    BertopiaScreenScaffold(title, { controller.back() }) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().align(Alignment.TopCenter), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (isNew) BertopiaHeading("What will you learn?")
            EditorLabel("Name", required = true)
            EditorTextField(form.title, { text -> controller.changePackForm { it.copy(title = text) } }, "Pack name", placeholder = "Spanish verbs")
            EditorLabel("Short description")
            EditorTextField(form.subtitle, { text -> controller.changePackForm { it.copy(subtitle = text) } }, "Short description", placeholder = "The 50 verbs I keep forgetting")
            EditorLabel("Shelf", "Where the pack sits in your library", required = true)
            ShelfChooser(form) { category, subcategory -> controller.changePackForm { it.copy(categoryId = category, subcategoryId = subcategory) } }
            EditorLabel("Colour", required = true)
            AccentPicker(form.accentColor) { hex -> controller.changePackForm { it.copy(accentColor = hex) } }
            EditorLabel("Cover picture")
            CoverPreview(form, packId?.let(controller::mediaDirectory))
            BertopiaButton(if (form.pickedCoverPath != null || form.coverMedia != null) "Choose a different picture" else "Choose a picture", controller::pickCover, Modifier.fillMaxWidth(), BertopiaButtonStyle.Secondary)
            BertopiaButton(
                when {
                    !form.ready -> "Add a name and a shelf"
                    isNew -> "Make the pack"
                    else -> "Save"
                },
                controller::savePack,
                Modifier.fillMaxWidth(),
                enabled = form.ready,
            )
            if (!isNew) {
                if (confirmingDelete) {
                    BertopiaCard {
                        BertopiaText("Delete ${form.title} and all its cards? This can't be undone.", style = BertopiaTheme.typography.label)
                        BertopiaButton("Delete for good", controller::deletePack, Modifier.fillMaxWidth(), BertopiaButtonStyle.Destructive)
                        BertopiaButton("Keep it", { controller.back() }, Modifier.fillMaxWidth(), BertopiaButtonStyle.Quiet)
                    }
                } else {
                    BertopiaButton("Delete this pack", controller::requestPackDeletion, Modifier.fillMaxWidth(), BertopiaButtonStyle.Quiet)
                }
            }
        }
    }
}

@Composable
private fun ShelfChooser(form: PackForm, onChoose: (String, String) -> Unit) {
    val categories = BundledTaxonomy.taxonomy.categories
    ChipRow(categories.map { it.id to it.name }, form.categoryId) { id ->
        val category = categories.first { it.id == id }
        onChoose(id, category.subcategories.singleOrNull()?.id ?: "")
    }
    categories.firstOrNull { it.id == form.categoryId }?.let { category ->
        if (category.subcategories.size > 1) {
            BertopiaText("Which part of ${category.name}?", style = BertopiaTheme.typography.detail, color = BertopiaTheme.colors.mutedPlum)
            ChipRow(category.subcategories.map { it.id to it.name }, form.subcategoryId) { id -> onChoose(category.id, id) }
        }
    }
}

@Composable
private fun CoverPreview(form: PackForm, mediaDirectory: String?) {
    val picked = form.pickedCoverPath?.let { Path(it) }
    val (directory, name) = when {
        picked != null -> (picked.parent?.toString() ?: "") to picked.name
        form.coverMedia != null && mediaDirectory != null -> mediaDirectory to form.coverMedia
        else -> return
    }
    val painter = rememberMediaPainter(directory, name)
    if (painter is MediaPainterState.Loaded) {
        androidx.compose.foundation.Image(
            painter.painter,
            contentDescription = "Cover picture",
            modifier = Modifier.fillMaxWidth().aspectRatio(2f).clip(RoundedCornerShape(20.dp)),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun CardListScreen(page: EditorPage.Cards, controller: PackEditingController) {
    BertopiaScreenScaffold(page.title, { controller.back() }, scrollable = false) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxSize().align(Alignment.TopCenter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BertopiaButton("Add a card", { controller.chooseNewCardStyle(page.packId) }, Modifier.fillMaxWidth())
            EditorTextField(page.search, controller::search, "Search cards", placeholder = "Search cards")
            BertopiaText(
                when {
                    page.loading -> "Looking…"
                    page.total == 0 && page.search.isBlank() -> "No cards yet. Add your first one."
                    page.total == 0 -> "Nothing matches."
                    page.total == 1 -> "1 card"
                    else -> "${page.total} cards"
                },
                style = BertopiaTheme.typography.detail,
                color = BertopiaTheme.colors.mutedPlum,
            )
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                page.rows.forEach { row -> CardRowItem(row) { controller.openCard(page.packId, row.ref) } }
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}

@Composable
private fun CardRowItem(row: CardRow, onOpen: () -> Unit) {
    val colors = BertopiaTheme.colors
    val readablePreview = row.preview.replace(CLOZE_MARKER) { it.groupValues[1] }.ifBlank { "Empty card" }
    val description = listOfNotNull(readablePreview, row.styleTitle, row.section).joinToString(", ")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.paper, RoundedCornerShape(16.dp))
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = description
            }
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BertopiaText(readablePreview.take(120), style = BertopiaTheme.typography.label)
        BertopiaText(listOfNotNull(row.styleTitle, row.section).joinToString(" · "), style = BertopiaTheme.typography.detail, color = colors.mutedPlum)
    }
}

@Composable
private fun StylePickerScreen(page: EditorPage.ChooseStyle, controller: PackEditingController) {
    BertopiaScreenScaffold("New card", { controller.back() }) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().align(Alignment.TopCenter), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BertopiaHeading("What kind of card?")
            CardStyles.all.forEach { style ->
                val title = CardStyles.title(style)
                val summary = STYLE_SUMMARIES[CardStyles.key(style)].orEmpty()
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(BertopiaTheme.colors.paper, RoundedCornerShape(16.dp))
                        .border(1.dp, BertopiaTheme.colors.outline.copy(alpha = .4f), RoundedCornerShape(16.dp))
                        .semantics(mergeDescendants = true) {
                            role = Role.Button
                            contentDescription = "$title. $summary"
                        }
                        .clickable { controller.startNewCard(page.packId, style) }
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    BertopiaText(title, style = BertopiaTheme.typography.label)
                    BertopiaText(summary, style = BertopiaTheme.typography.detail, color = BertopiaTheme.colors.mutedPlum)
                }
            }
        }
    }
}

private val CLOZE_MARKER = Regex("""\{\{c\d+::(.*?)(::[^}]*)?\}\}""")

private const val CLOZE_HELP = "Write the sentence, select the words to hide, then tap the button below"

private val STYLE_SUMMARIES = mapOf(
    "basic" to "A question with a short answer",
    "two-way" to "Two sides, asked both ways round",
    "cloze" to "A sentence with words to fill in",
    "typed" to "A question you answer by typing",
    "image" to "A picture to name",
    "occlusion" to "A picture with parts hidden",
    "flag" to "A flag and its country",
    "capital" to "A country and its capital",
    "choice" to "A question with your own options",
)

@Composable
private fun CardEditorScreen(card: CardEditorState, controller: PackEditingController) {
    val mediaDirectory = controller.mediaDirectory(card.packId)
    BertopiaScreenScaffold(if (card.isNew) card.form.title else "Edit card", { controller.back() }) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().align(Alignment.TopCenter), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            card.form.fields.forEach { field ->
                FieldEditor(field, card, mediaDirectory, controller)
            }
            SectionChooser(card, controller)
            if (card.previews.isNotEmpty()) {
                BertopiaText("Preview", Modifier.semantics { heading() }, BertopiaTheme.typography.heading)
                card.previews.take(2).forEach { preview ->
                    PreviewCard("Front", preview.frontHtml, mediaDirectory)
                    PreviewCard("Back", preview.backHtml, mediaDirectory)
                }
            }
            BertopiaButton(if (card.isNew) "Add card" else "Save card", controller::saveCard, Modifier.fillMaxWidth())
            if (!card.isNew) {
                if (card.confirmingDelete) {
                    BertopiaCard {
                        BertopiaText("Delete this card? Its progress goes with it.", style = BertopiaTheme.typography.label)
                        BertopiaButton("Delete card", controller::deleteCard, Modifier.fillMaxWidth(), BertopiaButtonStyle.Destructive)
                        BertopiaButton("Keep it", { controller.back() }, Modifier.fillMaxWidth(), BertopiaButtonStyle.Quiet)
                    }
                } else {
                    BertopiaButton("Delete card", controller::requestCardDeletion, Modifier.fillMaxWidth(), BertopiaButtonStyle.Quiet)
                }
            }
        }
    }
}

@Composable
private fun FieldEditor(field: FormField, card: CardEditorState, mediaDirectory: String, controller: PackEditingController) {
    val value = card.values[field.name].orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EditorLabel(
            field.label,
            when (field.kind) {
                FormFieldKind.Cloze -> CLOZE_HELP
                FormFieldKind.Masks -> null
                else -> field.help
            },
            field.required,
        )
        when (field.kind) {
            FormFieldKind.Text -> EditorTextField(value, { controller.changeField(field.name, it) }, field.label)
            FormFieldKind.RichText, FormFieldKind.List -> EditorTextField(value, { controller.changeField(field.name, it) }, field.label, singleLine = false)
            FormFieldKind.Cloze -> ClozeField(value, field.label) { controller.changeField(field.name, it) }
            FormFieldKind.Choice -> ChipRow(field.choices.map { it.value to it.label }, value.ifBlank { null }) { chosen ->
                controller.changeField(field.name, if (chosen == value) "" else chosen)
            }
            FormFieldKind.Image -> ImageField(value, mediaDirectory, showPicture = card.form.fields.none { it.kind == FormFieldKind.Masks }) { controller.attachImage(field.name) }
            FormFieldKind.Masks -> {
                val imageName = card.form.fields.firstOrNull { it.kind == FormFieldKind.Image }?.let { card.values[it.name] }?.let(app.bertopia.domain.editor.CardForms::imageName)
                if (imageName == null) BertopiaText("Choose the picture first, then draw boxes over the parts to hide.", color = BertopiaTheme.colors.mutedPlum)
                else MaskEditor(mediaDirectory, imageName, value) { controller.changeField(field.name, it) }
            }
        }
    }
}

@Composable
private fun ImageField(value: String, mediaDirectory: String, showPicture: Boolean, onPick: () -> Unit) {
    val name = app.bertopia.domain.editor.CardForms.imageName(value)
    if (name != null && showPicture) {
        val painter = rememberMediaPainter(mediaDirectory, name)
        if (painter is MediaPainterState.Loaded) {
            androidx.compose.foundation.Image(
                painter.painter,
                contentDescription = "Chosen picture",
                modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Fit,
            )
        }
    }
    BertopiaButton(if (name == null) "Choose a picture" else "Choose a different picture", onPick, Modifier.fillMaxWidth(), BertopiaButtonStyle.Secondary)
}

@Composable
private fun SectionChooser(card: CardEditorState, controller: PackEditingController) {
    var adding by remember { mutableStateOf(false) }
    var newSection by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EditorLabel("Section", "Group cards inside the pack")
        val options = listOf("" to "No section") + card.sections.map { it to it } + ("+" to "New section")
        ChipRow(options, if (adding) "+" else card.section ?: "") { id ->
            when (id) {
                "+" -> adding = true
                "" -> { adding = false; controller.chooseSection(null) }
                else -> { adding = false; controller.chooseSection(id) }
            }
        }
        if (adding) {
            EditorTextField(newSection, { newSection = it }, "New section name", placeholder = "Section name")
            BertopiaButton("Use this section", {
                if (newSection.isNotBlank()) {
                    controller.chooseSection(newSection)
                    adding = false
                    newSection = ""
                }
            }, Modifier.fillMaxWidth(), BertopiaButtonStyle.Secondary, enabled = newSection.isNotBlank())
        }
    }
}

@Composable
private fun PreviewCard(side: String, html: String, mediaDirectory: String) {
    val document = remember(html) { CardDocumentBuilder.build(html) }
    BertopiaCard {
        BertopiaText(side, style = BertopiaTheme.typography.detail, color = BertopiaTheme.colors.mutedPlum)
        CardView(document, bertopiaCardRenderStyle(), mediaDirectory, Modifier.fillMaxWidth())
    }
}

@Composable
private fun ProblemBanner(message: String, onDismiss: () -> Unit, modifier: Modifier) {
    Row(
        modifier
            .navigationBarsPadding()
            .padding(16.dp)
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .background(BertopiaTheme.colors.plum, RoundedCornerShape(16.dp))
            .semantics(mergeDescendants = true) { contentDescription = message }
            .clickable(onClick = onDismiss)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BertopiaText(message, Modifier.weight(1f), color = BertopiaTheme.colors.white)
        BertopiaText("OK", style = BertopiaTheme.typography.label, color = BertopiaTheme.colors.white)
    }
}

@Composable
private fun BusyOverlay() {
    Box(
        Modifier.fillMaxSize().background(BertopiaTheme.colors.parchment.copy(alpha = .6f)).clickable(enabled = true, onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        BertopiaText("One moment…", style = BertopiaTheme.typography.heading, textAlign = TextAlign.Center)
    }
}
