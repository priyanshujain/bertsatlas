package app.bertsatlas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

@Composable
fun QuizScreen(
    prompt: String,
    imageDescription: String?,
    score: Int,
    questionTime: String,
    timerExpired: Boolean,
    choices: List<ChoiceUi> = emptyList(),
    letterSlots: List<LetterSlotUi> = emptyList(),
    letterTiles: List<LetterTileUi> = emptyList(),
    appliedHintMessages: List<String> = emptyList(),
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
    onOpenDebug: (() -> Unit)? = null,
    promptFlag: FlagUi? = null,
    runProgress: Float? = null,
    runProgressDescription: String = "Trivia progress",
    overtimeMessage: String = "Time's up. Keep going for half points",
    promptContent: (@Composable () -> Unit)? = null,
    flipAnswerContent: (@Composable () -> Unit)? = null,
    flipKey: String = prompt,
    flipRevealed: Boolean = false,
    onRevealFlip: () -> Unit = {},
    onSelfGrade: (Boolean) -> Unit = {},
) {
    val flipping = flipAnswerContent != null
    val keyboardPresent = hardwareKeyboardPresent()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(flipKey, flipRevealed) { runCatching { focusRequester.requestFocus() } }
    AtlasScreen(
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    flipping && !flipRevealed && (event.key == Key.Spacebar || event.key == Key.Enter) -> { onRevealFlip(); true }
                    flipping && flipRevealed && event.key == FORGOT_KEY -> { onSelfGrade(false); true }
                    flipping && flipRevealed && event.key == REMEMBERED_KEY -> { onSelfGrade(true); true }
                    choices.isNotEmpty() -> {
                        val choice = choices.getOrNull(CHOICE_KEYS.indexOf(event.key))?.takeIf { it.enabled }
                        choice?.let { onChoice(it.id) }
                        choice != null
                    }
                    else -> false
                }
            },
        scrollable = !flipping,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        header = {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                runProgress?.let { SubtleRunProgress(it, runProgressDescription) }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    QuizMetric(
                        "Score",
                        score.toString(),
                        Modifier.weight(1f).then(
                            if (onOpenDebug == null) Modifier else Modifier.combinedClickable(onClick = {}, onLongClick = onOpenDebug),
                        ),
                    )
                    QuizMetric("Question", questionTime, Modifier.weight(1f), warning = timerExpired)
                    AtlasExitButton(onLeave)
                }
                if (timerExpired) {
                    AtlasText(
                        overtimeMessage,
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                        color = AtlasTheme.colors.error,
                        style = AtlasTheme.typography.label,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        },
    ) {
        Column(if (flipping) Modifier.fillMaxSize() else Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
          Column(
              if (flipping) Modifier.fillMaxWidth().weight(1f).verticalScroll(remember(flipKey) { ScrollState(0) }) else Modifier.fillMaxWidth(),
              verticalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            if (promptFlag != null || imageDescription != null) {
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 148.dp)
                        .semantics { contentDescription = imageDescription ?: "Flag to identify" },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.width(230.dp).padding(8.dp), contentAlignment = Alignment.Center) {
                        if (promptFlag != null) {
                            FlagImage(promptFlag, Modifier.fillMaxWidth(), accessible = false, showFallbackLabel = false)
                        } else {
                            AtlasText("⚑", style = AtlasTheme.typography.display, color = AtlasTheme.colors.atlasBlue)
                        }
                    }
                }
            }
            if (promptContent != null) AtlasCard { promptContent() } else AtlasHeading(prompt, Modifier.fillMaxWidth(), centered = true)
            appliedHintMessages.forEach { message ->
                AtlasCard(contentDescription = "Applied hint: $message") {
                    AtlasText("Hint: $message", color = AtlasTheme.colors.atlasBlue)
                }
            }
            if (choices.isNotEmpty()) ResponsiveChoices(choices, onChoice, keyboardPresent)
            if (flipAnswerContent != null && flipRevealed) AtlasCard { flipAnswerContent() }
          }
            if (flipping) FlipAndGradeActions(flipRevealed, onRevealFlip, onSelfGrade, keyboardPresent)
            if (letterSlots.isNotEmpty() || letterTiles.isNotEmpty()) {
                LetterBank(letterSlots, letterTiles, onLetter, onRemoveLetter)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AtlasButton("Clear", onClearLetters, Modifier.weight(1f), AtlasButtonStyle.Secondary, enabled = letterSlots.any { it.character != null && !it.fixed })
                    AtlasButton("Submit", onSubmit, Modifier.weight(1f), enabled = canSubmit)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AtlasButton("Get a hint", onHint, Modifier.weight(1f), AtlasButtonStyle.Secondary, enabled = hintAvailable)
                if (unlimited) AtlasButton("Finish", onFinish, Modifier.weight(1f), AtlasButtonStyle.Quiet)
            }
        }
    }
}

@Composable
private fun FlipAndGradeActions(revealed: Boolean, onReveal: () -> Unit, onSelfGrade: (Boolean) -> Unit, keyboardPresent: Boolean) {
    if (!revealed) {
        AtlasButton("Show answer", onReveal, Modifier.fillMaxWidth())
        if (keyboardPresent) KeyboardHint("Press space to show the answer")
        return
    }
    AtlasText("Did you know it?", Modifier.fillMaxWidth(), style = AtlasTheme.typography.heading, textAlign = TextAlign.Center)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AtlasButton("Not this time", { onSelfGrade(false) }, Modifier.weight(1f), AtlasButtonStyle.Secondary)
        AtlasButton("I knew it", { onSelfGrade(true) }, Modifier.weight(1f))
    }
    if (keyboardPresent) KeyboardHint("Press 1 for not this time or 2 for I knew it")
}

@Composable
private fun KeyboardHint(text: String) {
    AtlasText(text, Modifier.fillMaxWidth(), style = AtlasTheme.typography.detail, color = AtlasTheme.colors.mutedPlum, textAlign = TextAlign.Center)
}

private val CHOICE_KEYS = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five)
private val FORGOT_KEY = Key.One
private val REMEMBERED_KEY = Key.Two

@Composable
private fun QuizMetric(label: String, value: String, modifier: Modifier, warning: Boolean = false) {
    Column(
        modifier.semantics(mergeDescendants = true) { contentDescription = "$label: $value" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AtlasText(
            value,
            style = AtlasTheme.typography.timer,
            color = if (warning) AtlasTheme.colors.error else AtlasTheme.colors.plum,
            textAlign = TextAlign.Center,
        )
        AtlasText(label, style = AtlasTheme.typography.detail, color = AtlasTheme.colors.mutedPlum, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SubtleRunProgress(progress: Float, description: String) {
    val value = progress.coerceIn(0f, 1f)
    Box(
        Modifier.fillMaxWidth().heightIn(min = 4.dp)
            .background(AtlasTheme.colors.outline.copy(alpha = .14f), RoundedCornerShape(4.dp))
            .semantics { contentDescription = description; stateDescription = "${(value * 100).toInt()} percent" },
    ) {
        Box(Modifier.fillMaxWidth(value).heightIn(min = 4.dp).background(AtlasTheme.colors.coral, RoundedCornerShape(4.dp)))
    }
}

@Composable
private fun ResponsiveChoices(choices: List<ChoiceUi>, onChoice: (String) -> Unit, keyboardPresent: Boolean) {
    fun shortcut(choice: ChoiceUi) = choices.indexOf(choice).takeIf { keyboardPresent && it < CHOICE_KEYS.size }?.let { (it + 1).toString() }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 560.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                choices.chunked(2).forEach { rowChoices ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowChoices.forEach { AtlasChoice(it, onChoice, Modifier.weight(1f), shortcut(it)) }
                        if (rowChoices.size == 1) Box(Modifier.weight(1f))
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { choices.forEach { AtlasChoice(it, onChoice, shortcut = shortcut(it)) } }
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
        AnswerSlotRows(slots) { index ->
            val slot = slots[index]
            if (slot.fixed) {
                FixedLetterSlot(slot)
                return@AnswerSlotRows
            }
            val label = slot.character ?: "blank"
            Box(
                Modifier.size(48.dp)
                    .semantics { contentDescription = "Answer position ${slot.position + 1}: $label" }
                    .clickable(enabled = slot.tileId != null && !slot.fixed) { slot.tileId?.let(onRemoveLetter) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(38.dp)
                        .background(AtlasTheme.colors.paper, RoundedCornerShape(10.dp))
                        .border(2.dp, AtlasTheme.colors.atlasBlue, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    AtlasText(slot.character ?: "_", style = AtlasTheme.typography.heading)
                }
            }
        }
        WrappingCells(tiles.size) { index ->
            val tile = tiles[index]
            val available = tile.enabled && tile.placedSlot == null
            Box(
                Modifier.size(48.dp)
                    .semantics {
                        role = Role.Button
                        val tileState = when {
                            available -> "Available"
                            !tile.enabled -> "Removed"
                            else -> "Placed"
                        }
                        contentDescription = if (available) "Letter ${tile.letter}" else "Letter ${tile.letter}, ${tileState.lowercase()}"
                        stateDescription = tileState
                        if (!available) disabled()
                    }
                    .clickable(enabled = available) { onLetter(tile.id) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(44.dp)
                        .background(
                            if (available) AtlasTheme.colors.mustard.copy(alpha = .3f)
                            else AtlasTheme.colors.outline.copy(alpha = .12f),
                            RoundedCornerShape(11.dp),
                        )
                        .border(1.dp, AtlasTheme.colors.outline, RoundedCornerShape(11.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    AtlasText(tile.letter, style = AtlasTheme.typography.label)
                }
            }
        }
    }
}

@Composable
private fun FixedLetterSlot(slot: LetterSlotUi) {
    val character = slot.character.orEmpty()
    val spoken = if (character.isBlank()) "space" else character
    Box(
        Modifier.size(48.dp).semantics { contentDescription = "Answer position ${slot.position + 1}: $spoken" },
        contentAlignment = Alignment.Center,
    ) {
        if (character.isNotBlank()) AtlasText(character, style = AtlasTheme.typography.heading)
    }
}

@Composable
private fun AnswerSlotRows(slots: List<LetterSlotUi>, content: @Composable (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = (maxWidth / 56.dp).toInt().coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            answerRowsKeepingWordsTogether(slots, columns).forEach { indices ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                    for (index in indices) content(index)
                }
            }
        }
    }
}

private fun answerRowsKeepingWordsTogether(slots: List<LetterSlotUi>, columns: Int): List<List<Int>> {
    val words = mutableListOf(mutableListOf<Int>())
    slots.forEachIndexed { index, slot ->
        if (slot.fixed && slot.character.isNullOrBlank()) words.add(mutableListOf()) else words.last().add(index)
    }
    val rows = mutableListOf<MutableList<Int>>()
    words.filter { it.isNotEmpty() }.forEach { word ->
        word.chunked(columns).forEach { piece ->
            val current = rows.lastOrNull()
            val spaceIndex = piece.first() - 1
            if (current != null && current.size + 1 + piece.size <= columns && slots.getOrNull(spaceIndex)?.fixed == true) {
                current.add(spaceIndex)
                current.addAll(piece)
            } else {
                rows.add(piece.toMutableList())
            }
        }
    }
    return rows
}

@Composable
private fun WrappingCells(count: Int, content: @Composable (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = (maxWidth / 56.dp).toInt().coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (0 until count).chunked(columns).forEach { indices ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
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
    introText: String = "Choose the clue that sounds most useful.",
    chargedHintLabel: String = "−25 points",
) {
    AtlasScreenScaffold("Choose a hint", onCancel, modifier) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BertIllustration(BertPose.Hint, Modifier.size(150.dp))
            AtlasHeading("A little nudge?", Modifier.fillMaxWidth(), centered = true)
            AtlasText(introText, textAlign = TextAlign.Center, color = AtlasTheme.colors.mutedPlum)
            if (score > 0) {
                AtlasText(chargedHintLabel, style = AtlasTheme.typography.label, color = AtlasTheme.colors.outline, textAlign = TextAlign.Center)
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
    continueLabel: String = "Continue",
) {
    val pose = when { overtime && correct -> BertPose.Overtime; correct -> BertPose.Correct; else -> BertPose.Incorrect }
    val status = when { overtime && correct -> "Overtime correct"; correct -> "Correct"; else -> "Incorrect" }
    AtlasScreen(modifier) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spacer(Modifier.heightIn(min = 8.dp))
            BertIllustration(pose, Modifier.size(190.dp), contentDescription = "Bert reacts: $status")
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
            AtlasButton(continueLabel, onContinue, Modifier.fillMaxWidth())
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
    kicker: String = "Fun fact about",
    continueLabel: String = "Continue",
) {
    AtlasScreen(modifier) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BertIllustration(BertPose.Fact, contentDescription = "Bert has found an oddly specific fact")
            AtlasText("${kicker} $relatedTo", style = AtlasTheme.typography.label, color = AtlasTheme.colors.atlasBlue)
            AtlasHeading(heading, Modifier.fillMaxWidth(), centered = true)
            AtlasCard(contentDescription = artDescription) { AtlasText(fact, style = AtlasTheme.typography.heading, textAlign = TextAlign.Center) }
            AtlasButton(continueLabel, onContinue, Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun LeaveQuizConfirmationScreen(
    onCancel: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Leave this trivia run?",
    body: String = "Your answered questions will stay in your practice progress. They still count toward today's streak. Ending now won't count toward your personal best.",
) {
    AtlasScreen(modifier, scrollable = false) {
        Column(
            Modifier.fillMaxWidth().align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BertIllustration(BertPose.Hint, Modifier.size(160.dp), contentDescription = "Bert keeps your practice progress safe")
            AtlasHeading(title, Modifier.fillMaxWidth(), centered = true)
            AtlasText(body, Modifier.fillMaxWidth(), color = AtlasTheme.colors.mutedPlum, textAlign = TextAlign.Center)
            AtlasButton("Keep playing", onCancel, Modifier.fillMaxWidth())
            AtlasButton("Leave trivia", onLeave, Modifier.fillMaxWidth(), AtlasButtonStyle.Destructive)
        }
    }
}
