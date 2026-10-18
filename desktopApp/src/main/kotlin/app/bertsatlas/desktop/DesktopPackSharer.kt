package app.bertsatlas.desktop

import app.bertsatlas.PackSharer
import app.bertsatlas.store.ShareOutcome
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

class DesktopPackSharer(private val window: () -> Frame?) : PackSharer {
    override fun share(path: String, fileName: String, onDone: (ShareOutcome) -> Unit) {
        val dialog = FileDialog(window(), "Save pack", FileDialog.SAVE).apply {
            file = fileName
            isVisible = true
        }
        val directory = dialog.directory
        val chosen = dialog.file
        if (directory == null || chosen == null) {
            onDone(ShareOutcome.Dismissed)
            return
        }
        val target = File(directory, if (chosen.endsWith(".apkg", ignoreCase = true)) chosen else "$chosen.apkg")
        val copied = runCatching { File(path).copyTo(target, overwrite = true) }.isSuccess
        onDone(if (copied) ShareOutcome.Shared else ShareOutcome.Failed)
    }
}
