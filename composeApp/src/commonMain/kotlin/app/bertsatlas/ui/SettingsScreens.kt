package app.bertsatlas.ui

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
    AtlasScreen(modifier) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ScreenTopBar("Settings", onBack)
            AtlasCard {
                settings.forEach { AtlasToggle(it, onToggle) }
            }
            AtlasButton("Privacy", onPrivacy, Modifier.fillMaxWidth(), AtlasButtonStyle.Secondary)
            AtlasButton("Credits and licenses", onCredits, Modifier.fillMaxWidth(), AtlasButtonStyle.Secondary)
            AtlasButton("Reset local data", onReset, Modifier.fillMaxWidth(), AtlasButtonStyle.Destructive)
            AtlasText(
                "Bert's Atlas $version",
                Modifier.fillMaxWidth()
                    .semantics {
                        contentDescription = "Bert's Atlas version $version"
                        if (debugEntryEnabled) role = Role.Button
                    }
                    .combinedClickable(
                        enabled = debugEntryEnabled,
                        onClick = {},
                        onLongClick = onOpenDebug,
                    )
                    .padding(16.dp),
                style = AtlasTheme.typography.detail,
                color = AtlasTheme.colors.mutedPlum,
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
    AtlasScreen(modifier) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ScreenTopBar("Reset local data", onCancel)
            BertIllustration(BertPose.Incorrect, Modifier.fillMaxWidth())
            AtlasHeading("Clear this atlas?")
            AtlasText("This removes your progress, streaks, stamps, settings, and any saved run from this device. It cannot be undone.")
            AtlasButton("Reset everything", onReset, Modifier.fillMaxWidth(), AtlasButtonStyle.Destructive)
            AtlasButton("Keep my atlas", onCancel, Modifier.fillMaxWidth(), AtlasButtonStyle.Secondary)
        }
    }
}

@Composable
fun PrivacyScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    InformationScreen(
        title = "Privacy",
        paragraphs = listOf(
            "Bert's Atlas works entirely offline. It does not ask for an account or personal information.",
            "Your quiz history, progress, streaks, stamps, and preferences stay on this device. The app has no advertising, analytics, remote configuration, or product telemetry.",
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
    AtlasScreen(modifier) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ScreenTopBar("Credits and licenses", onBack)
            AtlasText("Bert, the writing, and the interface artwork are original to Bert's Atlas.")
            AtlasCard {
                AtlasText("Content", style = AtlasTheme.typography.heading)
                AtlasText("Catalog version $catalogVersion")
                credits.forEach { AtlasText(it, style = AtlasTheme.typography.detail) }
            }
            AtlasCard {
                AtlasText("Open-source licenses", style = AtlasTheme.typography.heading)
                AtlasText("Atkinson Hyperlegible Next · SIL Open Font License 1.1", style = AtlasTheme.typography.detail)
                licenses.forEach { AtlasText(it, style = AtlasTheme.typography.detail) }
            }
        }
    }
}

@Composable
private fun InformationScreen(title: String, paragraphs: List<String>, onBack: () -> Unit, modifier: Modifier = Modifier) {
    AtlasScreen(modifier) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ScreenTopBar(title, onBack)
            paragraphs.forEach { AtlasText(it) }
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
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AtlasScreen(modifier) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ScreenTopBar("Debug QA", onClose)
            AtlasCard {
                AtlasText("Build $buildVersion", style = AtlasTheme.typography.label)
                AtlasText("Catalog $catalogVersion")
                AtlasText(activeSnapshot, style = AtlasTheme.typography.detail, color = AtlasTheme.colors.mutedPlum)
            }
            actions.forEach { action ->
                AtlasButton(
                    text = action.title + (action.detail?.let { " · $it" } ?: ""),
                    onClick = { onAction(action.id) },
                    modifier = Modifier.fillMaxWidth(),
                    style = if (action.destructive) AtlasButtonStyle.Destructive else AtlasButtonStyle.Secondary,
                )
            }
        }
    }
}
