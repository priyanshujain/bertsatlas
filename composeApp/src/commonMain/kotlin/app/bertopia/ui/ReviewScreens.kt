package app.bertopia.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.bertopia.cardrender.CardDocumentBuilder
import app.bertopia.cardrender.CardView
import app.bertopia.cardrender.TypeAnswerState

enum class ReviewGrade(val label: String, val key: Key, val number: Int) {
    Forgot("Forgot", Key.One, 1),
    Tough("Tough", Key.Two, 2),
    GotIt("Got it", Key.Three, 3),
    Easy("Easy", Key.Four, 4),
}

data class ReviewCardUi(
    val questionHtml: String,
    val answerHtml: String,
    val mediaDirectory: String,
    val typedAnswerExpected: String?,
    val nextIntervals: Map<ReviewGrade, String>,
)

@Composable
fun ReviewScreen(
    packTitle: String,
    accent: Color,
    card: ReviewCardUi?,
    loading: Boolean,
    revealed: Boolean,
    typedAnswer: String,
    remaining: Int,
    reviewedCount: Int,
    gradeCounts: Map<ReviewGrade, Int>,
    onTypedAnswerChange: (String) -> Unit,
    onReveal: () -> Unit,
    onGrade: (ReviewGrade) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(card?.questionHtml, revealed) { runCatching { focusRequester.requestFocus() } }
    val typing = card?.typedAnswerExpected != null && !revealed
    BertopiaScreenScaffold(
        packTitle,
        onDone,
        modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || card == null || loading) return@onPreviewKeyEvent false
                when {
                    !revealed && (event.key == Key.Enter || (!typing && event.key == Key.Spacebar)) -> { onReveal(); true }
                    revealed && event.key == Key.Spacebar -> { onGrade(ReviewGrade.GotIt); true }
                    revealed -> ReviewGrade.entries.firstOrNull { it.key == event.key }?.let { onGrade(it); true } ?: false
                    else -> false
                }
            },
        scrollable = false,
        maxContentWidth = WIDE_CONTENT_WIDTH,
        trailing = {
            if (card != null) BertopiaText("$remaining left", Modifier.padding(end = 12.dp), style = BertopiaTheme.typography.label, color = BertopiaTheme.colors.mutedPlum)
        },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= WIDE_LAYOUT_MIN_WIDTH && card != null
            Column(
                Modifier.widthIn(max = if (wide) WIDE_CONTENT_WIDTH else 560.dp).fillMaxSize().align(Alignment.TopCenter),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when {
                    card == null && loading -> BertopiaText("Finding your next card…", Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    card == null -> Column(Modifier.verticalScroll(rememberScrollState())) { ReviewSummary(reviewedCount, gradeCounts, onDone) }
                    wide -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        Column(Modifier.weight(1.4f).verticalScroll(remember(card.questionHtml) { ScrollState(0) })) { ReviewCardFace(card, accent, revealed, typedAnswer, onTypedAnswerChange) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ReviewActions(card, revealed, loading, onReveal, onGrade, gradesPerRow = 1)
                        }
                    }
                    else -> {
                        Column(Modifier.weight(1f).verticalScroll(remember(card.questionHtml) { ScrollState(0) })) {
                            ReviewCardFace(card, accent, revealed, typedAnswer, onTypedAnswerChange)
                        }
                        ReviewActions(card, revealed, loading, onReveal, onGrade, gradesPerRow = 2)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewCardFace(
    card: ReviewCardUi,
    accent: Color,
    revealed: Boolean,
    typedAnswer: String,
    onTypedAnswerChange: (String) -> Unit,
) {
    val style = bertopiaCardRenderStyle(accent)
    val html = if (revealed) card.answerHtml else card.questionHtml
    val document = remember(html) { CardDocumentBuilder.build(html) }
    BertopiaCard {
        CardView(
            document = document,
            style = style,
            mediaDirectory = card.mediaDirectory,
            modifier = Modifier.fillMaxWidth(),
            typeAnswer = card.typedAnswerExpected?.let { expected ->
                TypeAnswerState(typedAnswer, onTypedAnswerChange, expected, revealed)
            },
        )
    }
}

@Composable
private fun ReviewActions(
    card: ReviewCardUi,
    revealed: Boolean,
    loading: Boolean,
    onReveal: () -> Unit,
    onGrade: (ReviewGrade) -> Unit,
    gradesPerRow: Int,
) {
    if (!revealed) {
        BertopiaButton("Show answer", onReveal, Modifier.fillMaxWidth(), enabled = !loading)
        BertopiaText(
            "Think of the answer first, then flip the card.",
            Modifier.fillMaxWidth(),
            style = BertopiaTheme.typography.detail,
            color = BertopiaTheme.colors.mutedPlum,
            textAlign = TextAlign.Center,
        )
    } else {
        BertopiaText("How well did you know it?", Modifier.semantics { heading() }, BertopiaTheme.typography.heading)
        val keyboardPresent = hardwareKeyboardPresent()
        EqualHeightGrid(ReviewGrade.entries, gradesPerRow) { grade, cell ->
            val interval = card.nextIntervals[grade].orEmpty()
            BertopiaChoice(
                ChoiceUi(
                    id = grade.name,
                    label = grade.label,
                    supportingText = if (interval.isEmpty()) null else "Again in $interval",
                    enabled = !loading,
                ),
                { onGrade(grade) },
                cell,
                if (keyboardPresent) grade.number.toString() else null,
            )
        }
    }
}

@Composable
private fun ReviewSummary(reviewedCount: Int, gradeCounts: Map<ReviewGrade, Int>, onDone: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BertIllustration(
            if (reviewedCount == 0) BertPose.Idle else BertPose.Results,
            Modifier.size(170.dp),
            contentDescription = if (reviewedCount == 0) "Bert relaxes with nothing to review" else "Bert celebrates your review session",
        )
        BertopiaHeading(if (reviewedCount == 0) "All caught up" else "Nice session", Modifier.fillMaxWidth(), centered = true)
        BertopiaText(
            when (reviewedCount) {
                0 -> "Nothing is due right now. Play a quick round or come back tomorrow."
                1 -> "You reviewed 1 card. Bert will bring it back when it's time."
                else -> "You reviewed $reviewedCount cards. Bert will bring each one back when it's time."
            },
            textAlign = TextAlign.Center,
        )
        if (reviewedCount > 0) {
            ReviewGrade.entries.chunked(2).forEach { pair ->
                StatRow(pair.map { StatUi(it.label, (gradeCounts[it] ?: 0).toString()) })
            }
        }
        BertopiaButton("Back to the pack", onDone, Modifier.fillMaxWidth().semantics { contentDescription = "Back to the pack" })
    }
}
