package app.bertsatlas.domain.pack

import app.bertsatlas.anki.collection.AnkiCollection
import app.bertsatlas.anki.pkg.PackageExport
import app.bertsatlas.anki.pkg.PackageFormat
import app.bertsatlas.domain.editor.LibraryFiles
import kotlinx.io.files.Path

enum class ExportFormat { Modern, CompatibleWithOlderAnki }

data class ExportedPack(val path: String, val fileName: String, val sizeBytes: Long)

class PackExporter(private val libraryDirectory: String, private val workingDirectory: String) {
    fun export(packId: String, outputDirectory: String, format: ExportFormat = ExportFormat.Modern, includeProgress: Boolean = true): ExportedPack {
        val collectionPath = PackLibraryLayout.collectionPath(libraryDirectory, packId)
        require(LibraryFiles.exists(collectionPath)) { "Pack $packId is not installed" }
        val title = AnkiCollection.open(collectionPath).use { collection ->
            collection.decks().filter { it.isTopLevel }.firstNotNullOfOrNull { deck ->
                (PackManifestCodec.readDeckDescription(deck.description) as? ManifestReading.Found)?.manifest?.title
            } ?: packId
        }
        LibraryFiles.createDirectories(outputDirectory)
        val fileName = fileNameFor(title)
        val outputPath = Path(outputDirectory, fileName).toString()
        if (LibraryFiles.exists(outputPath)) LibraryFiles.delete(outputPath)
        val exportWorkingDirectory = Path(workingDirectory, "export-$packId").toString()
        try {
            PackageExport.export(
                collectionPath = collectionPath,
                mediaDirectory = PackLibraryLayout.mediaDirectory(libraryDirectory, packId),
                outputPath = outputPath,
                workingDirectory = exportWorkingDirectory,
                format = when (format) {
                    ExportFormat.Modern -> PackageFormat.Modern
                    ExportFormat.CompatibleWithOlderAnki -> PackageFormat.Legacy
                },
                includeScheduling = includeProgress,
            )
        } finally {
            LibraryFiles.removeRecursively(exportWorkingDirectory)
        }
        return ExportedPack(outputPath, fileName, LibraryFiles.size(outputPath))
    }

    companion object {
        fun fileNameFor(title: String): String {
            val cleaned = title.map { if (it in UNSAFE_FILE_NAME_CHARACTERS || it.isISOControl()) ' ' else it }
                .joinToString("").trim().replace(Regex("\\s+"), " ").take(80).ifBlank { "Pack" }
            return "$cleaned.apkg"
        }

        private const val UNSAFE_FILE_NAME_CHARACTERS = "/\\:*?\"<>|"
    }
}
