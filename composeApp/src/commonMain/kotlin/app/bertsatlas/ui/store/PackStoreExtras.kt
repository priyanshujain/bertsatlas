package app.bertsatlas.ui.store

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.bertsatlas.domain.store.DownloadStage
import app.bertsatlas.store.StoreController
import app.bertsatlas.store.StoreState
import app.bertsatlas.ui.AtlasButton
import app.bertsatlas.ui.AtlasButtonStyle
import app.bertsatlas.ui.AtlasCard
import app.bertsatlas.ui.AtlasText
import app.bertsatlas.ui.AtlasTheme
import app.bertsatlas.ui.AtlasToggle
import app.bertsatlas.ui.SettingToggleUi

@Composable
fun PackUpdateBanner(store: StoreController, state: StoreState, packId: String, packTitle: String) {
    val update = state.updateFor(packId)
    val download = state.downloads[packId]
    val message = state.packMessages[packId]
    DisposableEffect(packId) { onDispose { store.clearPackMessage(packId) } }
    if (update != null || download != null || message != null) {
        AtlasCard(contentDescription = null) {
            when {
                download != null -> {
                    AtlasText(
                        when (download.stage) {
                            DownloadStage.Downloading -> "Downloading the update"
                            DownloadStage.Verifying -> "Checking the file"
                            DownloadStage.Installing -> "Updating your pack"
                        },
                        style = AtlasTheme.typography.label,
                    )
                    AtlasText("Your progress stays put.", style = AtlasTheme.typography.detail, color = AtlasTheme.colors.mutedPlum)
                    AtlasButton("Cancel", { store.cancelDownload(packId) }, style = AtlasButtonStyle.Quiet, contentDescription = "Cancel the update")
                }
                update != null -> {
                    AtlasText("A new version of $packTitle is ready", style = AtlasTheme.typography.label)
                    AtlasText(
                        listOf("Version ${update.version}.", update.whatsNew, "Updating keeps your progress.").filter { it.isNotBlank() }.joinToString(" "),
                        style = AtlasTheme.typography.detail,
                        color = AtlasTheme.colors.mutedPlum,
                    )
                    message?.let { AtlasText(it, style = AtlasTheme.typography.detail) }
                    AtlasButton("Update now", { store.update(packId) }, Modifier.fillMaxWidth(), contentDescription = "Update $packTitle to version ${update.version}")
                }
                message != null -> AtlasText(message, style = AtlasTheme.typography.detail)
            }
        }
    }
}

@Composable
fun PackShareButton(store: StoreController, state: StoreState, packId: String, packTitle: String) {
    AtlasButton(
        "Share this pack",
        { store.requestExport(packId, packTitle) },
        Modifier.fillMaxWidth(),
        AtlasButtonStyle.Secondary,
        contentDescription = "Share $packTitle as an Anki file",
    )
    state.export?.takeIf { it.packId == packId }?.let { ExportDialog(store, state) }
}

@Composable
private fun ExportDialog(store: StoreController, state: StoreState) {
    val request = state.export ?: return
    var compatible by remember(request) { mutableStateOf(false) }
    var withoutProgress by remember(request) { mutableStateOf(false) }
    Dialog(onDismissRequest = store::dismissExport) {
        Column(
            Modifier.widthIn(max = 460.dp).fillMaxWidth()
                .background(AtlasTheme.colors.parchment, RoundedCornerShape(26.dp))
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AtlasText("Share ${request.title}", style = AtlasTheme.typography.heading)
            AtlasText(
                "Bert saves the pack as an Anki file, so it opens in Anki, AnkiDroid and AnkiMobile too.",
                style = AtlasTheme.typography.detail,
                color = AtlasTheme.colors.mutedPlum,
            )
            AtlasToggle(
                SettingToggleUi("compatible", "Compatible with older Anki apps", "For Anki versions from before 2023.", compatible),
            ) { _, enabled -> compatible = enabled }
            AtlasToggle(
                SettingToggleUi("no-progress", "Share without my progress", "Leaves out your reviews and schedule.", withoutProgress),
            ) { _, enabled -> withoutProgress = enabled }
            state.exportMessage?.let { AtlasText(it, style = AtlasTheme.typography.detail) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                AtlasButton("Not now", store::dismissExport, Modifier.weight(1f), AtlasButtonStyle.Quiet, enabled = !state.exporting)
                AtlasButton(
                    if (state.exporting) "Preparing…" else "Share",
                    { store.export(compatibleWithOlderAnki = compatible, includeProgress = !withoutProgress) },
                    Modifier.weight(1f).semantics { contentDescription = "Share the file" },
                    enabled = !state.exporting,
                )
            }
        }
    }
}
