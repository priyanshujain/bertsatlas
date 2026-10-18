package app.bertopia.anki.pkg

import app.bertopia.anki.collection.AnkiCollection
import app.bertopia.anki.io.Files
import app.bertopia.anki.model.CardScheduling
import app.bertopia.anki.model.CardTemplate
import app.bertopia.anki.model.Note
import app.bertopia.anki.model.Notetype
import app.bertopia.anki.model.NotetypeField
import app.bertopia.anki.model.NotetypeKind

object PackageExport {
    private const val PLACEHOLDER_MESSAGE =
        "Please update to the latest Anki version, then import the .colpkg/.apkg file again."
    private const val PLACEHOLDER_CREATED_MILLIS = 1_767_225_600_000L

    fun export(
        collectionPath: String,
        mediaDirectory: String,
        outputPath: String,
        workingDirectory: String,
        format: PackageFormat = PackageFormat.Modern,
        includeScheduling: Boolean = true,
    ) {
        Files.createDirectories(workingDirectory)
        val collection = exportedCollection(collectionPath, workingDirectory, includeScheduling)
        val media = Files.list(mediaDirectory).sorted().map { name -> PackageMedia(name, Files.read(Files.join(mediaDirectory, name))) }
        Files.write(outputPath, AnkiPackage.write(collection, placeholderCollection(workingDirectory), media, format))
    }

    private fun exportedCollection(collectionPath: String, workingDirectory: String, includeScheduling: Boolean): ByteArray {
        val copyPath = Files.join(workingDirectory, "export-collection.anki2")
        Files.delete(copyPath)
        AnkiCollection.open(collectionPath).use { source ->
            if (source.isWritable) source.copyConsistentSnapshotTo(copyPath) else Files.write(copyPath, Files.read(collectionPath))
        }
        AnkiCollection.open(copyPath).use { copy ->
            if (copy.isWritable) {
                copy.useRollbackJournal()
                copy.enableFsrsScheduling()
            }
            if (!includeScheduling) {
                copy.transaction {
                    copy.cards().forEachIndexed { index, card ->
                        copy.saveCard(card.copy(scheduling = CardScheduling(due = index + 1L), flags = 0))
                    }
                    copy.deleteRevlog()
                }
            }
        }
        return Files.read(copyPath).also { Files.delete(copyPath) }
    }

    private fun placeholderCollection(workingDirectory: String): ByteArray {
        val path = Files.join(workingDirectory, "export-placeholder.anki2")
        Files.delete(path)
        AnkiCollection.create(path, nowMillis = { PLACEHOLDER_CREATED_MILLIS }).use { placeholder ->
            val notetype = placeholder.saveNotetype(
                Notetype(
                    id = 0,
                    name = "Basic",
                    kind = NotetypeKind.Standard,
                    fields = listOf(NotetypeField("Front", 0), NotetypeField("Back", 1)),
                    templates = listOf(CardTemplate("Card 1", 0, "{{Front}}", "{{FrontSide}}\n\n<hr id=answer>\n\n{{Back}}")),
                    css = ".card { font-family: arial; font-size: 20px; text-align: center; }",
                ),
            )
            placeholder.addNote(Note(id = 0, guid = "bert-placeholder", notetypeId = notetype.id, fields = listOf(PLACEHOLDER_MESSAGE, ""), tags = emptyList()), deckId = 1)
        }
        return Files.read(path).also { Files.delete(path) }
    }
}
