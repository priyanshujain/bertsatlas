package app.bertopia.ui.store

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
import app.bertopia.domain.store.DownloadStage
import app.bertopia.store.StoreController
import app.bertopia.store.StoreState
import app.bertopia.ui.BertopiaButton
import app.bertopia.ui.BertopiaButtonStyle
import app.bertopia.ui.BertopiaCard
import app.bertopia.ui.BertopiaText
import app.bertopia.ui.BertopiaTheme
import app.bertopia.ui.BertopiaToggle
import app.bertopia.ui.SettingToggleUi

@Composable
fun PackUpdateBanner(store: StoreController, state: StoreState, packId: String, packTitle: String) {
    val update = state.updateFor(packId)
    val download = state.downloads[packId]
    val message = state.packMessages[packId]
    DisposableEffect(packId) { onDispose { store.clearPackMessage(packId) } }
    if (update != null || download != null || message != null) {
        BertopiaCard(contentDescription = null) {
            when {
                download != null -> {
                    BertopiaText(
                        when (download.stage) {
                            DownloadStage.Downloading -> "Downloading the update"
                            DownloadStage.Verifying -> "Checking the file"
                            DownloadStage.Installing -> "Updating your pack"
                        },
                        style = BertopiaTheme.typography.label,
                    )
                    BertopiaText("Your progress stays put.", style = BertopiaTheme.typography.detail, color = BertopiaTheme.colors.mutedPlum)
                    BertopiaButton("Cancel", { store.cancelDownload(packId) }, style = BertopiaButtonStyle.Quiet, contentDescription = "Cancel the update")
                }
                update != null -> {
                    BertopiaText("A new version of $packTitle is ready", style = BertopiaTheme.typography.label)
                    BertopiaText(
                        listOf("Version ${update.version}.", update.whatsNew, "Updating keeps your progress.").filter { it.isNotBlank() }.joinToString(" "),
                        style = BertopiaTheme.typography.detail,
                        color = BertopiaTheme.colors.mutedPlum,
                    )
                    message?.let { BertopiaText(it, style = BertopiaTheme.typography.detail) }
                    BertopiaButton("Update now", { store.update(packId) }, Modifier.fillMaxWidth(), contentDescription = "Update $packTitle to version ${update.version}")
                }
                message != null -> BertopiaText(message, style = BertopiaTheme.typography.detail)
            }
        }
    }
}

@Composable
fun PackShareButton(store: StoreController, state: StoreState, packId: String, packTitle: String) {
    BertopiaButton(
        "Share this pack",
        { store.requestExport(packId, packTitle) },
        Modifier.fillMaxWidth(),
        BertopiaButtonStyle.Secondary,
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
                .background(BertopiaTheme.colors.parchment, RoundedCornerShape(26.dp))
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BertopiaText("Share ${request.title}", style = BertopiaTheme.typography.heading)
            BertopiaText(
                "Bert saves the pack as an Anki file, so it opens in Anki, AnkiDroid and AnkiMobile too.",
                style = BertopiaTheme.typography.detail,
                color = BertopiaTheme.colors.mutedPlum,
            )
            BertopiaToggle(
                SettingToggleUi("compatible", "Compatible with older Anki apps", "For Anki versions from before 2023.", compatible),
            ) { _, enabled -> compatible = enabled }
            BertopiaToggle(
                SettingToggleUi("no-progress", "Share without my progress", "Leaves out your reviews and schedule.", withoutProgress),
            ) { _, enabled -> withoutProgress = enabled }
            state.exportMessage?.let { BertopiaText(it, style = BertopiaTheme.typography.detail) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                BertopiaButton("Not now", store::dismissExport, Modifier.weight(1f), BertopiaButtonStyle.Quiet, enabled = !state.exporting)
                BertopiaButton(
                    if (state.exporting) "Preparing…" else "Share",
                    { store.export(compatibleWithOlderAnki = compatible, includeProgress = !withoutProgress) },
                    Modifier.weight(1f).semantics { contentDescription = "Share the file" },
                    enabled = !state.exporting,
                )
            }
        }
    }
}
