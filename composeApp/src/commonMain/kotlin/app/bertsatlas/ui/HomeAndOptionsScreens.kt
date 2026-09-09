package app.bertsatlas.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    isFirstLaunch: Boolean,
    streakDays: Int = 0,
    progressSummary: String = "",
    totalPoints: Int = 0,
    hasStartedTrivia: Boolean = !isFirstLaunch,
    onStart: () -> Unit,
    onOptions: () -> Unit = {},
    onProgress: () -> Unit = {},
    onSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    AtlasScreen(
        modifier = modifier,
        headerPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp),
        header = if (hasStartedTrivia) {
            {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    PointsBadge(totalPoints, onProgress, Modifier.weight(1f))
                    SettingsIconButton(onSettings)
                }
            }
        } else null,
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(if (hasStartedTrivia) 8.dp else 24.dp))
            HomeBertIllustration(large = !hasStartedTrivia)
            AtlasText(
                "Bert's Atlas",
                Modifier.semantics { heading() },
                AtlasTheme.typography.display,
                textAlign = TextAlign.Center,
            )
            AtlasText("Getting nerdier every day.", color = AtlasTheme.colors.mutedPlum, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            AtlasButton("Start", onStart, Modifier.fillMaxWidth().widthIn(max = 420.dp))
            if (hasStartedTrivia) {
                AtlasButton("Options", onOptions, Modifier.fillMaxWidth().widthIn(max = 420.dp), AtlasButtonStyle.Secondary)
                SectionGap()
                val streakSummary = when (streakDays) {
                    0 -> "Your curiosity streak starts with your first answer"
                    1 -> "1 curious day"
                    else -> "$streakDays curious days"
                }
                AtlasCard(Modifier.widthIn(max = 420.dp), "$streakSummary. $progressSummary") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        AtlasText("✦", style = AtlasTheme.typography.title, color = AtlasTheme.colors.coral)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            AtlasText(streakSummary, style = AtlasTheme.typography.label)
                            AtlasText(progressSummary, style = AtlasTheme.typography.detail, color = AtlasTheme.colors.mutedPlum)
                        }
                    }
                }
                AtlasButton("See your progress", onProgress, style = AtlasButtonStyle.Quiet)
            }
        }
    }
}

@Composable
private fun HomeBertIllustration(large: Boolean) {
    val size = if (large) 270.dp else 200.dp
    val colors = AtlasTheme.colors
    androidx.compose.foundation.layout.Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        BertIllustration(BertPose.Idle, Modifier.size(size), contentDescription = "Bert smiles with a bright idea and a folded map")
        Canvas(Modifier.fillMaxWidth().size(size)) {
            val w = this.size.width
            val h = this.size.height
            val outline = 4.dp.toPx()
            val map = Path().apply {
                moveTo(w * .01f, h * .68f)
                lineTo(w * .11f, h * .63f)
                lineTo(w * .22f, h * .68f)
                lineTo(w * .33f, h * .63f)
                lineTo(w * .33f, h * .88f)
                lineTo(w * .22f, h * .93f)
                lineTo(w * .11f, h * .88f)
                lineTo(w * .01f, h * .93f)
                close()
            }
            drawPath(map, colors.paper)
            drawPath(map, colors.plum, style = Stroke(outline, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            drawLine(colors.plum, Offset(w * .11f, h * .63f), Offset(w * .11f, h * .88f), outline)
            drawLine(colors.plum, Offset(w * .22f, h * .68f), Offset(w * .22f, h * .93f), outline)
            drawLine(colors.orangeAccent, Offset(w * .03f, h * .83f), Offset(w * .31f, h * .76f), outline * .7f, StrokeCap.Round)
        }
    }
}

@Composable
private fun PointsBadge(points: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AtlasTheme.colors
    Row(
        modifier
            .heightIn(min = 48.dp)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "View progress, $points total points"
            }
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(Modifier.size(24.dp)) {
            drawCircle(colors.mustard, size.minDimension * .40f)
            drawCircle(colors.plum, size.minDimension * .40f, style = Stroke(2.4.dp.toPx()))
            drawCircle(colors.plum, size.minDimension * .10f)
        }
        AtlasText(points.toString(), style = AtlasTheme.typography.heading, color = colors.plum)
    }
}

@Composable
private fun SettingsIconButton(onClick: () -> Unit) {
    val color = AtlasTheme.colors.plum
    androidx.compose.foundation.layout.Box(
        Modifier.size(48.dp).semantics { role = Role.Button; contentDescription = "Open settings" }.clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(26.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            drawCircle(color, size.minDimension * .27f, center, style = Stroke(3.dp.toPx()))
            drawCircle(color, size.minDimension * .07f, center)
            repeat(8) { index ->
                val angle = index * kotlin.math.PI.toFloat() / 4f
                val start = Offset(center.x + kotlin.math.cos(angle) * size.width * .31f, center.y + kotlin.math.sin(angle) * size.height * .31f)
                val end = Offset(center.x + kotlin.math.cos(angle) * size.width * .43f, center.y + kotlin.math.sin(angle) * size.height * .43f)
                drawLine(color, start, end, 3.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}

@Composable
fun OptionsScreen(
    step: Int,
    choices: List<ChoiceUi>,
    durationSummary: String,
    topicSummary: String,
    onChoose: (String) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isTimeStep = step <= 1
    val title = when (step) {
        1 -> "Options · 1 of 2"
        2 -> "Options · 2 of 2"
        else -> "Options saved"
    }
    AtlasScreenScaffold(title, onBack, modifier) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (step <= 2) {
                BertIllustration(if (isTimeStep) BertPose.Idle else BertPose.Fact, Modifier.align(Alignment.CenterHorizontally))
                AtlasHeading(
                    if (isTimeStep) "How much time have you got?" else "What are we nerding out about?",
                    Modifier.fillMaxWidth(),
                    centered = true,
                )
                AtlasText(
                    if (isTimeStep) "The clock only runs while you're answering." else "Difficulty adapts quietly as you play.",
                    Modifier.fillMaxWidth(),
                    color = AtlasTheme.colors.mutedPlum,
                    textAlign = TextAlign.Center,
                )
                choices.forEach { AtlasChoice(it, onChoose) }
            } else {
                SectionGap()
                BertIllustration(BertPose.Correct, Modifier.align(Alignment.CenterHorizontally))
                AtlasHeading("Saved and ready", Modifier.fillMaxWidth(), centered = true)
                AtlasText(
                    "Your next trivia run is set up.",
                    Modifier.fillMaxWidth(),
                    color = AtlasTheme.colors.mutedPlum,
                    textAlign = TextAlign.Center,
                )
                AtlasCard(contentDescription = "Saved options. Duration: $durationSummary. Topic: $topicSummary") {
                    StatRow(listOf(StatUi("Duration", durationSummary), StatUi("Topic", topicSummary)))
                }
                AtlasButton("Start", onStart, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun BootstrapScreen(message: String = "Opening the atlas…", modifier: Modifier = Modifier) {
    AtlasScreen(modifier, scrollable = false) {
        Column(Modifier.fillMaxWidth().align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            BertIllustration(BertPose.Idle)
            AtlasText(message, style = AtlasTheme.typography.heading, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun RecoveryScreen(
    questionSummary: String,
    score: Int,
    timeRemaining: String,
    onResume: () -> Unit,
    onAbandon: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AtlasScreen(modifier) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BertIllustration(BertPose.Hint, contentDescription = "Bert holds your place in the atlas")
            AtlasHeading("Your place is still marked", Modifier.fillMaxWidth(), centered = true)
            AtlasText("Pick up on the exact question where you left off.", textAlign = TextAlign.Center)
            AtlasCard(contentDescription = "Saved run. Score $score. Time remaining $timeRemaining. $questionSummary") {
                StatRow(listOf(StatUi("Score", score.toString()), StatUi("Time left", timeRemaining)))
                AtlasText(questionSummary, style = AtlasTheme.typography.detail, color = AtlasTheme.colors.mutedPlum)
            }
            AtlasButton("Resume run", onResume, Modifier.fillMaxWidth())
            AtlasButton("Leave this trivia run", onAbandon, Modifier.fillMaxWidth(), AtlasButtonStyle.Quiet)
            AtlasText("Answered questions still count toward what Bert revisits next.", style = AtlasTheme.typography.detail, color = AtlasTheme.colors.mutedPlum, textAlign = TextAlign.Center)
        }
    }
}
