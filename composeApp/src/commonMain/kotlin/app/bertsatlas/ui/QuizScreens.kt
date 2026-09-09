package app.bertsatlas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun QuizScreen(
    prompt: String,
    imageDescription: String?,
    score: Int,
    sessionTime: String,
    questionTime: String,
    timerExpired: Boolean,
    choices: List<ChoiceUi> = emptyList(),
    letterSlots: List<LetterSlotUi> = emptyList(),
    letterTiles: List<LetterTileUi> = emptyList(),
    hintAvailable: Boolean,
    unlimited: Boolean,
    canSubmit: Boolean = false,
    onChoice: (String) -> Unit = {},
    onLetter: (String) -> Unit = {},
    onRemoveLetter: (String) -> Unit = {},
    onClearLetters: () -> Unit = {},
    onSubmit: () -> Unit = {},
    onHint: () -> Unit,
    onFinish: () -> Unit = {},
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
    promptFlag: FlagUi? = null,
) {
    AtlasScreen(modifier) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth < 480.dp) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AtlasButton("Leave", onLeave, style = AtlasButtonStyle.Quiet)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            QuizMetric("Score", score.toString(), Modifier.weight(1f))
                            QuizMetric(if (unlimited) "Elapsed" else "Run", sessionTime, Modifier.weight(1f))
                            QuizMetric("Question", questionTime, Modifier.weight(1f), warning = timerExpired)
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AtlasButton("Leave", onLeave, style = AtlasButtonStyle.Quiet)
                        QuizMetric("Score", score.toString(), Modifier.weight(1f))
                        QuizMetric(if (unlimited) "Elapsed" else "Run", sessionTime, Modifier.weight(1f))
                        QuizMetric("Question", questionTime, Modifier.weight(1f), warning = timerExpired)
                    }
                }
            }
            if (timerExpired) {
                AtlasText("Overtime · you can still answer for half points", color = AtlasTheme.colors.error, style = AtlasTheme.typography.label)
            }
            if (promptFlag != null || imageDescription != null) {
                AtlasCard(contentDescription = imageDescription ?: "Flag of ${promptFlag?.countryName}") {
                    Box(Modifier.fillMaxWidth().heightIn(min = 116.dp), contentAlignment = Alignment.Center) {
                        if (promptFlag != null) {
                            FlagImage(promptFlag, Modifier.fillMaxWidth().heightIn(min = 116.dp))
                        } else {
                            AtlasText("⚑", style = AtlasTheme.typography.display, color = AtlasTheme.colors.atlasBlue)
                        }
                    }
                    imageDescription?.let {
                        AtlasText(it, Modifier.fillMaxWidth(), style = AtlasTheme.typography.detail, color = AtlasTheme.colors.mutedPlum, textAlign = TextAlign.Center)
                    }
                }
            }
            AtlasHeading(prompt)
            if (choices.isNotEmpty()) ResponsiveChoices(choices, onChoice)
            if (letterSlots.isNotEmpty() || letterTiles.isNotEmpty()) {
                LetterBank(letterSlots, letterTiles, onLetter, onRemoveLetter)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AtlasButton("Clear", onClearLetters, Modifier.weight(1f), AtlasButtonStyle.Secondary, enabled = letterSlots.any { it.character != null && !it.fixed })
                    AtlasButton("Submit", onSubmit, Modifier.weight(1f), enabled = canSubmit)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AtlasButton("Hint", onHint, Modifier.weight(1f), AtlasButtonStyle.Secondary, enabled = hintAvailable)
                if (unlimited) AtlasButton("Finish", onFinish, Modifier.weight(1f), AtlasButtonStyle.Quiet)
            }
        }
    }
}

@Composable
private fun QuizMetric(label: String, value: String, modifier: Modifier, warning: Boolean = false) {
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = "$label: $value" }, horizontalAlignment = Alignment.CenterHorizontally) {
        AtlasText(value, style = AtlasTheme.typography.timer, color = if (warning) AtlasTheme.colors.error else AtlasTheme.colors.plum, textAlign = TextAlign.Center)
        AtlasText(label, style = AtlasTheme.typography.detail, color = AtlasTheme.colors.mutedPlum, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ResponsiveChoices(choices: List<ChoiceUi>, onChoice: (String) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 560.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                choices.chunked(2).forEach { rowChoices ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowChoices.forEach { AtlasChoice(it, onChoice, Modifier.weight(1f)) }
                        if (rowChoices.size == 1) Box(Modifier.weight(1f))
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { choices.forEach { AtlasChoice(it, onChoice) } }
        }
    }
}

@Composable
private fun LetterBank(
    slots: List<LetterSlotUi>,
    tiles: List<LetterTileUi>,
    onLetter: (String) -> Unit,
    onRemoveLetter: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        WrappingCells(slots.size) { index ->
            val slot = slots[index]
            val label = slot.character ?: "blank"
            Box(
                Modifier.size(52.dp).background(AtlasTheme.colors.paper, RoundedCornerShape(12.dp))
                    .border(2.dp, AtlasTheme.colors.atlasBlue, RoundedCornerShape(12.dp))
                    .semantics { contentDescription = "Answer position ${slot.position + 1}: $label" }
                    .clickable(enabled = slot.tileId != null && !slot.fixed) { slot.tileId?.let(onRemoveLetter) },
                contentAlignment = Alignment.Center,
            ) { AtlasText(slot.character ?: "_", style = AtlasTheme.typography.heading) }
        }
        WrappingCells(tiles.size) { index ->
            val tile = tiles[index]
            val available = tile.enabled && tile.placedSlot == null
            Box(
                Modifier.size(52.dp).background(if (available) AtlasTheme.colors.mustard.copy(alpha = .3f) else AtlasTheme.colors.outline.copy(alpha = .12f), RoundedCornerShape(12.dp))
                    .border(1.dp, AtlasTheme.colors.outline, RoundedCornerShape(12.dp))
                    .semantics {
                        role = Role.Button
                        contentDescription = "Letter ${tile.letter}"
                        stateDescription = if (available) "Available" else "Placed"
                    }
                    .clickable(enabled = available) { onLetter(tile.id) },
                contentAlignment = Alignment.Center,
            ) { AtlasText(tile.letter, style = AtlasTheme.typography.label) }
        }
    }
}

@Composable
private fun WrappingCells(count: Int, content: @Composable (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = (maxWidth / 60.dp).toInt().coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (0 until count).chunked(columns).forEach { indices ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (index in indices) content(index)
                }
            }
        }
    }
}

@Composable
fun HintChooserScreen(
    score: Int,
    hints: List<ChoiceUi>,
    onChooseHint: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AtlasScreen(modifier) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ScreenTopBar("Choose a hint", onCancel)
            BertIllustration(BertPose.Hint)
            AtlasHeading("A little nudge?", Modifier.fillMaxWidth(), centered = true)
            AtlasText("Every hint costs up to 25 points. Your score can never drop below zero.", textAlign = TextAlign.Center)
            AtlasCard(contentDescription = "Current score $score. Score after hint ${maxOf(0, score - 25)}") {
                StatRow(listOf(StatUi("Now", score.toString()), StatUi("After hint", maxOf(0, score - 25).toString())))
            }
            hints.forEach { AtlasChoice(it, onChooseHint) }
        }
    }
}

@Composable
fun FeedbackScreen(
    correct: Boolean,
    overtime: Boolean,
    heading: String,
    selectedAnswer: String?,
    correctAnswer: String,
    pointsAwarded: Int,
    explanation: String?,
    autoAdvanceMessage: String?,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pose = when { overtime && correct -> BertPose.Overtime; correct -> BertPose.Correct; else -> BertPose.Incorrect }
    val status = when { overtime && correct -> "Overtime correct"; correct -> "Correct"; else -> "Incorrect" }
    AtlasScreen(modifier) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            BertIllustration(pose, contentDescription = "Bert reacts: $status")
            AtlasHeading(heading, Modifier.fillMaxWidth(), centered = true)
            AtlasText(
                if (correct) "+$pointsAwarded points" else "No points this time",
                style = AtlasTheme.typography.heading,
                color = if (correct) AtlasTheme.colors.success else AtlasTheme.colors.error,
            )
            AtlasCard(contentDescription = "$status. Correct answer: $correctAnswer") {
                if (!correct && selectedAnswer != null) AtlasText("Your answer · $selectedAnswer", color = AtlasTheme.colors.error, style = AtlasTheme.typography.label)
                AtlasText("Correct answer · $correctAnswer", color = AtlasTheme.colors.success, style = AtlasTheme.typography.label)
                explanation?.let { AtlasText(it) }
            }
            if (overtime && correct) AtlasText("The clock gave up. You didn't. Half points.", textAlign = TextAlign.Center)
            AtlasButton("Continue", onContinue, Modifier.fillMaxWidth())
            autoAdvanceMessage?.let { AtlasText(it, style = AtlasTheme.typography.detail, color = AtlasTheme.colors.mutedPlum, textAlign = TextAlign.Center) }
        }
    }
}

@Composable
fun FunFactScreen(
    heading: String,
    fact: String,
    relatedTo: String,
    artDescription: String? = null,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AtlasScreen(modifier) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BertIllustration(BertPose.Fact, contentDescription = "Bert has found an oddly specific fact")
            AtlasText("FIELD NOTE · $relatedTo", style = AtlasTheme.typography.label, color = AtlasTheme.colors.atlasBlue)
            AtlasHeading(heading, Modifier.fillMaxWidth(), centered = true)
            AtlasCard(contentDescription = artDescription) { AtlasText(fact, style = AtlasTheme.typography.heading, textAlign = TextAlign.Center) }
            AtlasButton("Back to the atlas", onContinue, Modifier.fillMaxWidth())
        }
    }
}
