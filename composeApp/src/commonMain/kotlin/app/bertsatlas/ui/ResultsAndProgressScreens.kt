package app.bertsatlas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun ResultsScreen(
    score: Int,
    correctAnswers: Int,
    answeredQuestions: Int,
    accuracy: String,
    averageResponseTime: String,
    bestCorrectStreak: Int,
    masteryImproved: Int,
    revisitCountries: List<String>,
    newPersonalBest: Boolean,
    onPlayAgain: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AtlasScreen(modifier) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BertIllustration(BertPose.Results, contentDescription = "Bert celebrates a completed atlas run")
            AtlasHeading(if (newPersonalBest) "New personal best" else "Run complete", Modifier.fillMaxWidth(), centered = true)
            AtlasText(score.toString(), style = AtlasTheme.typography.display, color = AtlasTheme.colors.atlasBlue)
            AtlasText("points", color = AtlasTheme.colors.mutedPlum)
            StatRow(
                listOf(
                    StatUi("Correct", "$correctAnswers/$answeredQuestions"),
                    StatUi("Accuracy", accuracy),
                    StatUi("Best streak", bestCorrectStreak.toString()),
                ),
            )
            AtlasCard {
                AtlasText("Run notes", style = AtlasTheme.typography.heading)
                AtlasText("Average response · $averageResponseTime")
                AtlasText("New or improved knowledge · $masteryImproved")
            }
            if (revisitCountries.isNotEmpty()) {
                AtlasCard(contentDescription = "Worth another look: ${revisitCountries.joinToString()}") {
                    AtlasText("Worth another look", style = AtlasTheme.typography.heading)
                    AtlasText(revisitCountries.joinToString(" · "), color = AtlasTheme.colors.mutedPlum)
                }
            }
            AtlasButton("Play again", onPlayAgain, Modifier.fillMaxWidth())
            AtlasButton("Home", onHome, Modifier.fillMaxWidth(), AtlasButtonStyle.Secondary)
        }
    }
}

@Composable
fun ProgressScreen(
    currentStreak: Int,
    bestStreak: Int,
    overallAccuracy: String,
    totalAnswered: Int,
    masteryGroups: List<ProgressGroupUi>,
    strongCountries: List<String>,
    revisitCountries: List<String>,
    stamps: List<StampUi>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AtlasScreen(modifier) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ScreenTopBar("Your atlas", onBack)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BertIllustration(BertPose.Streak, Modifier.weight(.8f), "Bert celebrates your curiosity streak")
                Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AtlasText("$currentStreak day streak", style = AtlasTheme.typography.heading, color = AtlasTheme.colors.coral)
                    AtlasText("Best · $bestStreak days", color = AtlasTheme.colors.mutedPlum)
                }
            }
            StatRow(listOf(StatUi("Accuracy", overallAccuracy), StatUi("Questions", totalAnswered.toString())))
            AtlasCard {
                AtlasText("Things you're getting to know", style = AtlasTheme.typography.heading)
                masteryGroups.forEach { AtlasProgress(it.label, it.progress, it.detail) }
            }
            CountryNotes("Strong lately", strongCountries, AtlasTheme.colors.success)
            CountryNotes("Worth revisiting", revisitCountries, AtlasTheme.colors.mustard)
            AtlasText("Passport stamps", style = AtlasTheme.typography.heading)
            stamps.forEach { StampCard(it) }
        }
    }
}

@Composable
private fun CountryNotes(title: String, countries: List<String>, accent: androidx.compose.ui.graphics.Color) {
    if (countries.isEmpty()) return
    AtlasCard(contentDescription = "$title: ${countries.joinToString()}") {
        AtlasText(title, style = AtlasTheme.typography.label, color = accent)
        AtlasText(countries.joinToString(" · "))
    }
}

@Composable
private fun StampCard(stamp: StampUi) {
    val accent = if (stamp.earned) AtlasTheme.colors.atlasBlue else AtlasTheme.colors.outline
    Row(
        Modifier.fillMaxWidth().heightIn(min = 76.dp)
            .background(AtlasTheme.colors.paper, RoundedCornerShape(16.dp))
            .border(2.dp, accent, RoundedCornerShape(16.dp))
            .semantics(mergeDescendants = true) { contentDescription = "${if (stamp.earned) "Earned" else "Not yet earned"} stamp: ${stamp.title}. ${stamp.description}" }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AtlasText(if (stamp.earned) "★" else "◇", style = AtlasTheme.typography.title, color = accent, textAlign = TextAlign.Center)
        Column(Modifier.weight(1f)) {
            AtlasText(stamp.title, style = AtlasTheme.typography.label)
            AtlasText(stamp.description, style = AtlasTheme.typography.detail, color = AtlasTheme.colors.mutedPlum)
        }
    }
}
