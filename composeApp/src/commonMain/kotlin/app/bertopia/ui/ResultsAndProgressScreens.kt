package app.bertopia.ui

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
    abandoned: Boolean = false,
) {
    BertopiaScreen(modifier) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BertIllustration(if (abandoned) BertPose.Hint else BertPose.Results, contentDescription = if (abandoned) "Bert keeps your practice progress" else "Bert celebrates completed trivia")
            BertopiaHeading(
                when {
                    abandoned -> "Trivia ended"
                    newPersonalBest -> "New personal best"
                    else -> "Trivia complete"
                },
                Modifier.fillMaxWidth(),
                centered = true,
            )
            if (abandoned) {
                BertopiaText(
                    "Your answers still shaped what you'll practise next and counted toward today's activity. This trivia run didn't count toward your personal best.",
                    Modifier.fillMaxWidth(),
                    color = BertopiaTheme.colors.mutedPlum,
                    textAlign = TextAlign.Center,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BertopiaText(score.toString(), style = BertopiaTheme.typography.display, color = BertopiaTheme.colors.bertopiaBlue)
                BertopiaText("points", color = BertopiaTheme.colors.mutedPlum)
            }
            StatRow(
                listOf(
                    StatUi("Correct", "$correctAnswers/$answeredQuestions"),
                    StatUi("Accuracy", accuracy),
                    StatUi("Best streak", bestCorrectStreak.toString()),
                ),
            )
            BertopiaCard {
                BertopiaText("Highlights", style = BertopiaTheme.typography.heading)
                BertopiaText("Average response · $averageResponseTime")
                BertopiaText("New or improved knowledge · $masteryImproved")
            }
            if (revisitCountries.isNotEmpty()) {
                BertopiaCard(contentDescription = "Worth another look: ${revisitCountries.joinToString()}") {
                    BertopiaText("Worth another look", style = BertopiaTheme.typography.heading)
                    BertopiaText(revisitCountries.joinToString(" · "), color = BertopiaTheme.colors.mutedPlum)
                }
            }
            BertopiaButton(if (abandoned) "Start new trivia" else "Play again", onPlayAgain, Modifier.fillMaxWidth())
            BertopiaButton("Home", onHome, Modifier.fillMaxWidth(), BertopiaButtonStyle.Secondary)
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
    BertopiaScreenScaffold("Your progress", onBack, modifier) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BertIllustration(BertPose.Streak, Modifier.weight(.8f), "Bert celebrates your curiosity streak")
                Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BertopiaText(
                        when (currentStreak) {
                            0 -> "Start your streak"
                            1 -> "1 day streak"
                            else -> "$currentStreak day streak"
                        },
                        style = BertopiaTheme.typography.heading,
                    )
                    BertopiaText(
                        when (bestStreak) {
                            0 -> "Answer a question to begin"
                            1 -> "Best · 1 day"
                            else -> "Best · $bestStreak days"
                        },
                        color = BertopiaTheme.colors.mutedPlum,
                    )
                }
            }
            StatRow(listOf(StatUi("Accuracy", overallAccuracy), StatUi("Questions", totalAnswered.toString())))
            BertopiaCard {
                BertopiaText("Things you're getting to know", style = BertopiaTheme.typography.heading)
                masteryGroups.forEach { BertopiaProgress(it.label, it.progress, it.detail) }
            }
            CountryNotes("Strong lately", strongCountries, BertopiaTheme.colors.success)
            CountryNotes("Worth revisiting", revisitCountries, BertopiaTheme.colors.plum)
            BertopiaText("Passport stamps", style = BertopiaTheme.typography.heading)
            stamps.forEach { StampCard(it) }
        }
    }
}

@Composable
private fun CountryNotes(title: String, countries: List<String>, accent: androidx.compose.ui.graphics.Color) {
    if (countries.isEmpty()) return
    BertopiaCard(contentDescription = "$title: ${countries.joinToString()}") {
        BertopiaText(title, style = BertopiaTheme.typography.label, color = accent)
        BertopiaText(countries.joinToString(" · "))
    }
}

@Composable
private fun StampCard(stamp: StampUi) {
    val accent = if (stamp.earned) BertopiaTheme.colors.bertopiaBlue else BertopiaTheme.colors.outline
    Row(
        Modifier.fillMaxWidth().heightIn(min = 76.dp)
            .background(BertopiaTheme.colors.paper, RoundedCornerShape(16.dp))
            .border(2.dp, accent, RoundedCornerShape(16.dp))
            .semantics(mergeDescendants = true) { contentDescription = "${if (stamp.earned) "Earned" else "Not yet earned"} stamp: ${stamp.title}. ${stamp.description}" }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BertopiaText(if (stamp.earned) "★" else "◇", style = BertopiaTheme.typography.title, color = accent, textAlign = TextAlign.Center)
        Column(Modifier.weight(1f)) {
            BertopiaText(stamp.title, style = BertopiaTheme.typography.label)
            BertopiaText(stamp.description, style = BertopiaTheme.typography.detail, color = BertopiaTheme.colors.mutedPlum)
        }
    }
}
