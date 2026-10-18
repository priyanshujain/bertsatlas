package app.bertopia.desktop

import app.bertopia.PackFilePicker
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

class DesktopPackFilePicker(private val window: () -> Frame?) : PackFilePicker {
    override fun pickPackFile(onPicked: (String?) -> Unit) {
        val dialog = FileDialog(window(), "Import a pack", FileDialog.LOAD).apply {
            setFilenameFilter { _, name -> isPackFileName(name) }
            isVisible = true
        }
        val directory = dialog.directory
        val name = dialog.file
        onPicked(if (directory != null && name != null) File(directory, name).absolutePath else null)
    }
}

fun isPackFileName(name: String): Boolean = name.endsWith(".apkg", ignoreCase = true) || name.endsWith(".colpkg", ignoreCase = true)
