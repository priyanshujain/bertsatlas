package app.bertopia.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    settings: List<SettingToggleUi>,
    version: String,
    debugEntryEnabled: Boolean,
    onToggle: (String, Boolean) -> Unit,
    onPrivacy: () -> Unit,
    onCredits: () -> Unit,
    onReset: () -> Unit,
    onOpenDebug: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BertopiaScreenScaffold("Settings", onBack, modifier) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BertopiaCard {
                settings.forEach { BertopiaToggle(it, onToggle) }
            }
            BertopiaCard {
                BertopiaLinkRow("Privacy", onPrivacy)
                BertopiaLinkRow("Credits and licenses", onCredits)
                BertopiaLinkRow("Reset local data", onReset, destructive = true)
            }
            BertopiaText(
                "Bertopia $version",
                Modifier.fillMaxWidth()
                    .semantics {
                        contentDescription = "Bertopia version $version"
                        if (debugEntryEnabled) role = Role.Button
                    }
                    .combinedClickable(
                        enabled = debugEntryEnabled,
                        onClick = {},
                        onLongClick = onOpenDebug,
                    )
                    .padding(16.dp),
                style = BertopiaTheme.typography.detail,
                color = BertopiaTheme.colors.mutedPlum,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun ResetConfirmationScreen(
    onReset: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BertopiaScreenScaffold("Reset local data", onCancel, modifier) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BertIllustration(BertPose.Incorrect, Modifier.fillMaxWidth())
            BertopiaHeading("Clear your progress?")
            BertopiaText("This removes your progress, streaks, stamps, settings, and any saved run from this device. It cannot be undone.")
            BertopiaButton("Reset everything", onReset, Modifier.fillMaxWidth(), BertopiaButtonStyle.Destructive)
            BertopiaButton("Keep my progress", onCancel, Modifier.fillMaxWidth(), BertopiaButtonStyle.Secondary)
        }
    }
}

@Composable
fun PrivacyScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    InformationScreen(
        title = "Privacy",
        paragraphs = listOf(
            "Bertopia works entirely offline. It does not ask for an account or personal information.",
            "Your trivia history, progress, streaks, stamps, and preferences stay on this device. The app has no advertising, analytics, remote configuration, or product telemetry.",
            "Clearing app data or deleting the app may permanently remove your progress. Operating-system and store diagnostics are controlled by your platform settings.",
        ),
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun CreditsScreen(
    catalogVersion: String,
    credits: List<String>,
    licenses: List<String>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BertopiaScreenScaffold("Credits and licenses", onBack, modifier) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BertopiaText("Bert, the writing, and the interface artwork are original to Bertopia.")
            BertopiaCard {
                BertopiaText("Content", style = BertopiaTheme.typography.heading)
                BertopiaText("Catalog version $catalogVersion")
                credits.forEach { BertopiaText(it, style = BertopiaTheme.typography.detail) }
            }
            BertopiaCard {
                BertopiaText("Open-source licenses", style = BertopiaTheme.typography.heading)
                BertopiaText("Atkinson Hyperlegible Next · SIL Open Font License 1.1", style = BertopiaTheme.typography.detail)
                licenses.forEach { BertopiaText(it, style = BertopiaTheme.typography.detail) }
            }
        }
    }
}

@Composable
private fun InformationScreen(title: String, paragraphs: List<String>, onBack: () -> Unit, modifier: Modifier = Modifier) {
    BertopiaScreenScaffold(title, onBack, modifier) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            paragraphs.forEach { BertopiaText(it) }
        }
    }
}

@Composable
fun DebugPanelScreen(
    buildVersion: String,
    catalogVersion: String,
    activeSnapshot: String,
    actions: List<DebugActionUi>,
    onAction: (String) -> Unit,
    onOpenCardGallery: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BertopiaScreenScaffold("Debug QA", onClose, modifier) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BertopiaCard {
                BertopiaText("Build $buildVersion", style = BertopiaTheme.typography.label)
                BertopiaText("Catalog $catalogVersion")
                BertopiaText(activeSnapshot, style = BertopiaTheme.typography.detail, color = BertopiaTheme.colors.mutedPlum)
            }
            BertopiaButton(
                text = "Open card gallery",
                onClick = onOpenCardGallery,
                modifier = Modifier.fillMaxWidth(),
                style = BertopiaButtonStyle.Secondary,
            )
            actions.forEach { action ->
                BertopiaButton(
                    text = action.title + (action.detail?.let { " · $it" } ?: ""),
                    onClick = { onAction(action.id) },
                    modifier = Modifier.fillMaxWidth(),
                    style = if (action.destructive) BertopiaButtonStyle.Destructive else BertopiaButtonStyle.Secondary,
                )
            }
        }
    }
}
