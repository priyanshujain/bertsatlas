package app.bertopia.desktop

import app.bertopia.domain.pack.DesktopLibraryLocation
import java.io.File

class DesktopPaths(homeDirectory: String) {
    val applicationSupport = File(DesktopLibraryLocation.applicationSupportDirectory(homeDirectory))
    val libraryDirectory = File(DesktopLibraryLocation.libraryDirectory(homeDirectory))
    val databaseFile = File(applicationSupport, "bertopia.db")
    val preferencesFile = File(applicationSupport, "bertopia.preferences_pb")

    init {
        libraryDirectory.mkdirs()
    }

    companion object {
        fun forCurrentUser() = DesktopPaths(System.getProperty("bertopia.home") ?: System.getProperty("user.home"))
    }
}
