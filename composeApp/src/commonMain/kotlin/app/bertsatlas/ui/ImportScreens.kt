package app.bertsatlas.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.bertsatlas.cardrender.CardDocumentBuilder
import app.bertsatlas.cardrender.CardView

data class ImportDeckUi(
    val id: Long,
    val title: String,
    val detail: String,
    val previewHtml: String?,
    val updatesPackTitle: String?,
    val selected: Boolean,
)

data class CategoryOptionUi(val id: String, val name: String, val subcategories: List<Pair<String, String>>)

data class ImportCategoryRowUi(
    val deckId: Long,
    val title: String,
    val categoryId: String?,
    val subcategoryId: String?,
)

data class ImportResultUi(val packId: String, val title: String, val detail: String)

@Composable
fun ImportProgressScreen(title: String, label: String, fraction: Float?, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    AtlasScreen(modifier) {
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxWidth().align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            BertIllustration(BertPose.Fact, Modifier.size(170.dp), contentDescription = "Bert carries a stack of cards")
            AtlasHeading(title, Modifier.fillMaxWidth(), centered = true)
            if (fraction == null) {
                AtlasText(label, textAlign = TextAlign.Center, color = AtlasTheme.colors.mutedPlum)
            } else {
                AtlasProgress(label, fraction, "${(fraction * 100).toInt()}%", Modifier.fillMaxWidth())
            }
            AtlasButton("Cancel", onCancel, Modifier.fillMaxWidth(), AtlasButtonStyle.Secondary)
        }
    }
}

@Composable
fun ImportDeckPickerScreen(
    fileName: String,
    decks: List<ImportDeckUi>,
    onToggle: (Long) -> Unit,
    onContinue: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AtlasScreenScaffold("Add decks", onCancel, modifier) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AtlasHeading("Pick what to bring in")
            AtlasText("$fileName has ${decks.size} decks. Each one becomes its own pack.", color = AtlasTheme.colors.mutedPlum)
            decks.forEach { deck -> DeckChoice(deck, onToggle) }
            val selected = decks.count { it.selected }
            AtlasButton(
                when (selected) {
                    0 -> "Pick at least one deck"
                    1 -> "Continue with 1 deck"
                    else -> "Continue with $selected decks"
                },
                onContinue,
                Modifier.fillMaxWidth(),
                enabled = selected > 0,
            )
        }
    }
}

@Composable
private fun DeckChoice(deck: ImportDeckUi, onToggle: (Long) -> Unit) {
    val style = atlasCardRenderStyle()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AtlasChoice(
            ChoiceUi(
                id = deck.id.toString(),
                label = (if (deck.selected) "✓ " else "") + deck.title,
                supportingText = listOfNotNull(deck.detail, deck.updatesPackTitle?.let { "Updates your ${it} pack" }).joinToString(". "),
                state = if (deck.selected) AnswerState.Selected else AnswerState.Available,
            ),
            { onToggle(deck.id) },
            Modifier.fillMaxWidth(),
        )
        deck.previewHtml?.takeIf { deck.selected }?.let { html ->
            val document = remember(html) { CardDocumentBuilder.build(html) }
            AtlasCard {
                AtlasText("A card from this deck", style = AtlasTheme.typography.detail, color = AtlasTheme.colors.mutedPlum)
                Column(Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
                    CardView(document, style, mediaDirectory = "", modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
fun ImportCategoryScreen(
    rows: List<ImportCategoryRowUi>,
    categories: List<CategoryOptionUi>,
    onChoose: (deckId: Long, categoryId: String, subcategoryId: String) -> Unit,
    onImport: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AtlasScreenScaffold("Add decks", onCancel, modifier) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AtlasHeading(if (rows.size == 1) "Where does it belong?" else "Where do they belong?")
            AtlasText("Pick a shelf so your library stays tidy.", color = AtlasTheme.colors.mutedPlum)
            rows.forEach { row -> CategoryChooser(row, categories, onChoose) }
            val ready = rows.all { it.categoryId != null && it.subcategoryId != null }
            AtlasButton(if (ready) "Add to my library" else "Pick a shelf for each pack", onImport, Modifier.fillMaxWidth(), enabled = ready)
        }
    }
}

@Composable
private fun CategoryChooser(row: ImportCategoryRowUi, categories: List<CategoryOptionUi>, onChoose: (Long, String, String) -> Unit) {
    AtlasCard {
        AtlasText(row.title, Modifier.semantics { heading() }, AtlasTheme.typography.heading)
        val chosen = categories.firstOrNull { it.id == row.categoryId }
        if (chosen == null) {
            ChoiceGrid(categories.map { ChoiceUi(it.id, it.name) }) { id ->
                val category = categories.first { it.id == id }
                onChoose(row.deckId, category.id, category.subcategories.singleOrNull()?.first ?: "")
            }
        } else {
            AtlasChoice(ChoiceUi(chosen.id, chosen.name, "Tap to change", AnswerState.Selected), { onChoose(row.deckId, "", "") }, Modifier.fillMaxWidth())
            AtlasText("Which part of ${chosen.name}?", style = AtlasTheme.typography.label)
            ChoiceGrid(
                chosen.subcategories.map { (id, name) ->
                    ChoiceUi(id, name, state = if (id == row.subcategoryId) AnswerState.Selected else AnswerState.Available)
                },
            ) { id -> onChoose(row.deckId, chosen.id, id) }
        }
    }
}

@Composable
private fun ChoiceGrid(choices: List<ChoiceUi>, onChoose: (String) -> Unit) {
    choices.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            pair.forEach { AtlasChoice(it, onChoose, Modifier.weight(1f)) }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
fun ImportDoneScreen(results: List<ImportResultUi>, onOpen: (String) -> Unit, onLibrary: () -> Unit, modifier: Modifier = Modifier) {
    AtlasScreen(modifier) {
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxWidth().align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BertIllustration(BertPose.Stamp, Modifier.size(170.dp), contentDescription = "Bert stamps your new pack")
            AtlasHeading(if (results.size == 1) "Ready to learn" else "Your packs are ready", Modifier.fillMaxWidth(), centered = true)
            results.forEach { result ->
                AtlasCard {
                    AtlasText(result.title, style = AtlasTheme.typography.heading)
                    AtlasText(result.detail, style = AtlasTheme.typography.detail, color = AtlasTheme.colors.mutedPlum)
                    AtlasButton("Open ${result.title}", { onOpen(result.packId) }, Modifier.fillMaxWidth())
                }
            }
            AtlasButton("Back to the library", onLibrary, Modifier.fillMaxWidth(), AtlasButtonStyle.Quiet)
        }
    }
}

@Composable
fun ImportFailedScreen(message: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    AtlasScreen(modifier) {
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxWidth().align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BertIllustration(BertPose.Incorrect, Modifier.size(170.dp), contentDescription = "Bert looks puzzled")
            AtlasHeading("That didn't work", Modifier.fillMaxWidth(), centered = true)
            AtlasText(message, textAlign = TextAlign.Center)
            AtlasButton("Back to the library", onBack, Modifier.fillMaxWidth())
        }
    }
}
