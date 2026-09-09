package app.bertsatlas.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    isFirstLaunch: Boolean,
    streakDays: Int = 0,
    progressSummary: String = "",
    onStart: () -> Unit,
    onOptions: () -> Unit = {},
    onProgress: () -> Unit = {},
    onSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    AtlasScreen(modifier) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BertIllustration(BertPose.Idle, contentDescription = "Bert smiles behind oversized glasses")
            AtlasText(
                "Bert's Atlas",
                Modifier.semantics { heading() },
                AtlasTheme.typography.display,
                textAlign = TextAlign.Center,
            )
            AtlasText("Getting nerdier every day.", color = AtlasTheme.colors.mutedPlum, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            AtlasButton("Start", onStart, Modifier.fillMaxWidth().widthIn(max = 420.dp))
            if (!isFirstLaunch) {
                AtlasButton("Options", onOptions, Modifier.fillMaxWidth().widthIn(max = 420.dp), AtlasButtonStyle.Secondary)
                AtlasCard(Modifier.widthIn(max = 420.dp), "Current streak: $streakDays days. $progressSummary") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        AtlasText("$streakDays", style = AtlasTheme.typography.title, color = AtlasTheme.colors.coral)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            AtlasText(if (streakDays == 1) "day curious" else "days curious", style = AtlasTheme.typography.label)
                            AtlasText(progressSummary, style = AtlasTheme.typography.detail, color = AtlasTheme.colors.mutedPlum)
                        }
                        AtlasButton("Progress", onProgress, style = AtlasButtonStyle.Quiet)
                    }
                }
                AtlasButton("Settings", onSettings, style = AtlasButtonStyle.Quiet)
            }
        }
    }
}

@Composable
fun OptionsScreen(
    step: Int,
    choices: List<ChoiceUi>,
    onChoose: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isTimeStep = step <= 1
    AtlasScreen(modifier) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ScreenTopBar(if (isTimeStep) "Options · 1 of 2" else "Options · 2 of 2", onBack)
            SectionGap()
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
            AtlasButton("Leave this run", onAbandon, Modifier.fillMaxWidth(), AtlasButtonStyle.Quiet)
            AtlasText("Answered questions still count toward what Bert revisits next.", style = AtlasTheme.typography.detail, color = AtlasTheme.colors.mutedPlum, textAlign = TextAlign.Center)
        }
    }
}
