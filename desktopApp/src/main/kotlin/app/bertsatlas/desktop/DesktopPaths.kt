package app.bertsatlas.desktop

import app.bertsatlas.domain.pack.DesktopLibraryLocation
import java.io.File

class DesktopPaths(homeDirectory: String) {
    val applicationSupport = File(DesktopLibraryLocation.applicationSupportDirectory(homeDirectory))
    val libraryDirectory = File(DesktopLibraryLocation.libraryDirectory(homeDirectory))
    val databaseFile = File(applicationSupport, "berts-atlas.db")
    val preferencesFile = File(applicationSupport, "berts-atlas.preferences_pb")

    init {
        libraryDirectory.mkdirs()
    }

    companion object {
        fun forCurrentUser() = DesktopPaths(System.getProperty("bertsatlas.home") ?: System.getProperty("user.home"))
    }
}
