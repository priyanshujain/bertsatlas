package app.bertopia.ui

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
import app.bertopia.cardrender.CardDocumentBuilder
import app.bertopia.cardrender.CardView

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
    BertopiaScreen(modifier) {
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxWidth().align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            BertIllustration(BertPose.Fact, Modifier.size(170.dp), contentDescription = "Bert carries a stack of cards")
            BertopiaHeading(title, Modifier.fillMaxWidth(), centered = true)
            if (fraction == null) {
                BertopiaText(label, textAlign = TextAlign.Center, color = BertopiaTheme.colors.mutedPlum)
            } else {
                BertopiaProgress(label, fraction, "${(fraction * 100).toInt()}%", Modifier.fillMaxWidth())
            }
            BertopiaButton("Cancel", onCancel, Modifier.fillMaxWidth(), BertopiaButtonStyle.Secondary)
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
    BertopiaScreenScaffold("Add decks", onCancel, modifier) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BertopiaHeading("Pick what to bring in")
            BertopiaText("$fileName has ${decks.size} decks. Each one becomes its own pack.", color = BertopiaTheme.colors.mutedPlum)
            decks.forEach { deck -> DeckChoice(deck, onToggle) }
            val selected = decks.count { it.selected }
            BertopiaButton(
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
    val style = bertopiaCardRenderStyle()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BertopiaChoice(
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
            BertopiaCard {
                BertopiaText("A card from this deck", style = BertopiaTheme.typography.detail, color = BertopiaTheme.colors.mutedPlum)
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
    BertopiaScreenScaffold("Add decks", onCancel, modifier) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BertopiaHeading(if (rows.size == 1) "Where does it belong?" else "Where do they belong?")
            BertopiaText("Pick a shelf so your library stays tidy.", color = BertopiaTheme.colors.mutedPlum)
            rows.forEach { row -> CategoryChooser(row, categories, onChoose) }
            val ready = rows.all { it.categoryId != null && it.subcategoryId != null }
            BertopiaButton(if (ready) "Add to my library" else "Pick a shelf for each pack", onImport, Modifier.fillMaxWidth(), enabled = ready)
        }
    }
}

@Composable
private fun CategoryChooser(row: ImportCategoryRowUi, categories: List<CategoryOptionUi>, onChoose: (Long, String, String) -> Unit) {
    BertopiaCard {
        BertopiaText(row.title, Modifier.semantics { heading() }, BertopiaTheme.typography.heading)
        val chosen = categories.firstOrNull { it.id == row.categoryId }
        if (chosen == null) {
            ChoiceGrid(categories.map { ChoiceUi(it.id, it.name) }) { id ->
                val category = categories.first { it.id == id }
                onChoose(row.deckId, category.id, category.subcategories.singleOrNull()?.first ?: "")
            }
        } else {
            BertopiaChoice(ChoiceUi(chosen.id, chosen.name, "Tap to change", AnswerState.Selected), { onChoose(row.deckId, "", "") }, Modifier.fillMaxWidth())
            BertopiaText("Which part of ${chosen.name}?", style = BertopiaTheme.typography.label)
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
    EqualHeightGrid(choices, 2) { choice, cell -> BertopiaChoice(choice, onChoose, cell) }
}

@Composable
fun ImportDoneScreen(results: List<ImportResultUi>, onOpen: (String) -> Unit, onLibrary: () -> Unit, modifier: Modifier = Modifier) {
    BertopiaScreen(modifier) {
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxWidth().align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BertIllustration(BertPose.Stamp, Modifier.size(170.dp), contentDescription = "Bert stamps your new pack")
            BertopiaHeading(if (results.size == 1) "Ready to learn" else "Your packs are ready", Modifier.fillMaxWidth(), centered = true)
            results.forEach { result ->
                BertopiaCard {
                    BertopiaText(result.title, style = BertopiaTheme.typography.heading)
                    BertopiaText(result.detail, style = BertopiaTheme.typography.detail, color = BertopiaTheme.colors.mutedPlum)
                    BertopiaButton("Open pack", { onOpen(result.packId) }, Modifier.fillMaxWidth(), contentDescription = "Open ${result.title}")
                }
            }
            BertopiaButton("Back to the library", onLibrary, Modifier.fillMaxWidth(), BertopiaButtonStyle.Quiet)
        }
    }
}

@Composable
fun ImportFailedScreen(message: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    BertopiaScreen(modifier) {
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxWidth().align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BertIllustration(BertPose.Incorrect, Modifier.size(170.dp), contentDescription = "Bert looks puzzled")
            BertopiaHeading("That didn't work", Modifier.fillMaxWidth(), centered = true)
            BertopiaText(message, textAlign = TextAlign.Center)
            BertopiaButton("Back to the library", onBack, Modifier.fillMaxWidth())
        }
    }
}
