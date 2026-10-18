package app.bertopia.cli

import app.bertopia.anki.collection.AnkiCollection
import app.bertopia.anki.model.DECK_NAME_SEPARATOR
import app.bertopia.anki.model.Deck
import app.bertopia.anki.pkg.PackageImport
import app.bertopia.domain.pack.IssueCode
import app.bertopia.domain.pack.IssueSeverity
import app.bertopia.domain.pack.PackBuildException
import app.bertopia.domain.pack.PackContents
import app.bertopia.domain.pack.PackManifestCodec
import app.bertopia.domain.pack.PackNote
import app.bertopia.domain.pack.PackSourceBuilder
import app.bertopia.domain.pack.PackValidator
import app.bertopia.domain.pack.ValidationIssue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object ValidateCommand {
    fun run(context: Context): Output {
        val target = context.arguments.positional(0, "pack | file.apkg | source dir")
        val path = context.absolute(target)
        val reports = when {
            Files.isDirectory(path) -> listOf(validateSource(path))
            Files.exists(path) -> validatePackage(context, path)
            else -> listOf(validateInstalled(context, target))
        }
        val errors = reports.sumOf { report -> report.issues.count { it.severity == IssueSeverity.Error } }
        val warnings = reports.sumOf { report -> report.issues.count { it.severity == IssueSeverity.Warning } }
        val json = buildJsonObject {
            put("target", target)
            put("valid", errors == 0)
            put("errors", errors)
            put("warnings", warnings)
            put("reports", JsonArray(reports.map(Report::json)))
        }
        val text = reports.joinToString("\n") { report ->
            val counts = "${report.notes} notes, ${report.issues.count { it.severity == IssueSeverity.Error }} errors, ${report.issues.count { it.severity == IssueSeverity.Warning }} warnings"
            (listOf("${report.name}: $counts") + report.issues.map { "  " + issueLine(it) }).joinToString("\n")
        }
        return Output(json, text, if (errors > 0) ExitCode.Invalid else ExitCode.Ok)
    }

    private class Report(val name: String, val notes: Int, val issues: List<ValidationIssue>) {
        fun json(): JsonObject = buildJsonObject {
            put("name", name)
            put("notes", notes)
            put("issues", issuesJson(issues))
        }
    }

    private fun validateSource(directory: String): Report {
        val contents = try {
            PackSourceBuilder.read(directory)
        } catch (failure: PackBuildException) {
            throw CliFailure.invalid(failure.message.orEmpty())
        } catch (failure: Exception) {
            throw CliFailure.invalid("could not read pack source: ${failure.message}")
        }
        val issues = PackSourceBuilder.validate(contents).map { issue ->
            if (issue.code == IssueCode.MediaMissing) issue.copy(severity = IssueSeverity.Error) else issue
        }
        return Report(contents.manifest.packId, contents.cards.size, issues)
    }

    private fun validatePackage(context: Context, path: String): List<Report> {
        val workingDirectory = context.library.newWorkingDirectory("validate")
        try {
            return PackageImport.open(path, workingDirectory).use { opened ->
                val mediaNames = opened.media.map { it.name }.toSet()
                opened.topLevelDecks().map { summary ->
                    val deck = opened.collection.deck(summary.deckId)!!
                    validateDeckTree(opened.collection, deck, mediaNames, deck.name)
                }
            }
        } catch (failure: CliFailure) {
            throw failure
        } catch (failure: Exception) {
            throw CliFailure.invalid("$path is not a readable Anki package: ${failure.message}")
        } finally {
            Files.removeRecursively(workingDirectory)
        }
    }

    private fun validateInstalled(context: Context, packId: String): Report = context.library.read(packId) { pack ->
        validateDeckTree(pack.collection, pack.rootDeck, pack.mediaFiles().keys, packId)
    }

    private fun validateDeckTree(collection: AnkiCollection, root: Deck, mediaFiles: Set<String>, name: String): Report {
        val deckIds = collection.decks().filter { it.id == root.id || it.name.startsWith(root.name + DECK_NAME_SEPARATOR) }.map { it.id }
        val noteIds = collection.cardsInDecks(deckIds).map { it.noteId }.toSet()
        val notes = noteIds.sorted().mapNotNull { collection.note(it) }.map { note ->
            val notetype = collection.notetype(note.notetypeId)
            PackNote(note.guid, notetype?.name.orEmpty(), notetype?.fieldNames.orEmpty().zip(note.fields).toMap(), note.tags)
        }
        val issues = PackValidator.validate(PackContents(PackManifestCodec.readDeckDescription(root.description), notes, mediaFiles))
        return Report(name, notes.size, issues)
    }
}
