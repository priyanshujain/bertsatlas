package app.bertopia.cli

import app.bertopia.anki.collection.ReadOnlyCollectionException
import app.bertopia.domain.editor.EditorException
import app.bertopia.domain.editor.EditorFailureKind
import app.bertopia.domain.editor.PackEditor
import app.bertopia.domain.pack.DesktopLibraryLocation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

const val BERT_VERSION = "1.0.0"

class Environment(
    val homeDirectory: String,
    val currentDirectory: String,
    val temporaryDirectory: String,
    val libraryOverride: String?,
    val readStandardInput: () -> String,
    val nowMillis: () -> Long,
) {
    fun defaultLibrary(): String = libraryOverride ?: DesktopLibraryLocation.libraryDirectory(homeDirectory)
}

class Context(val arguments: Arguments, val library: Library, val environment: Environment) {
    val editor: PackEditor by lazy { PackEditor(library) }

    fun inputText(): String {
        val file = arguments.value("file")
        if (file == null || file == "-") return environment.readStandardInput()
        val path = absolute(file)
        if (!Files.exists(path)) throw CliFailure.notFound("no file at $path")
        return Files.readText(path)
    }

    fun absolute(path: String): String = Files.absolute(path, environment.currentDirectory)

    fun shifted(count: Int): Context = Context(arguments.shifted(count), library, environment)
}

class Bert(private val environment: Environment) {
    fun run(rawArguments: List<String>): CliResult {
        val wantsJson = "--json" in rawArguments
        return try {
            val arguments = Arguments.parse(rawArguments)
            val output = dispatch(arguments)
            CliResult(output.exitCode, if (arguments.has("json")) output.json.compact() else output.text, "")
        } catch (failure: CliFailure) {
            failureResult(failure, wantsJson)
        } catch (failure: EditorException) {
            failureResult(failure.toCliFailure(), wantsJson)
        } catch (failure: ReadOnlyCollectionException) {
            failureResult(CliFailure(ExitCode.ReadOnly, failure.message.orEmpty()), wantsJson)
        } catch (failure: Throwable) {
            val message = failure.message.orEmpty().ifEmpty { failure::class.simpleName.orEmpty() }
            val exitCode = if ("database is locked" in message || "SQLITE_BUSY" in message) ExitCode.Conflict else ExitCode.Failure
            failureResult(CliFailure(exitCode, message), wantsJson)
        }
    }

    private fun dispatch(arguments: Arguments): Output {
        if (arguments.has("version")) return Output(buildJsonObject { put("version", BERT_VERSION) }, "bert $BERT_VERSION")
        val command = arguments.positionals.firstOrNull()
        if (command == null || command == "help") return help(arguments.positionals.getOrNull(1))
        if (arguments.has("help")) return help(command)
        val library = Library(
            directory = arguments.value("library")?.let { Files.absolute(it, environment.currentDirectory) } ?: environment.defaultLibrary(),
            workingDirectory = "${environment.temporaryDirectory.trimEnd('/')}/bert-cli",
        )
        val context = Context(arguments.shifted(1), library, environment)
        return when (command) {
            "packs" -> PacksCommands.run(context)
            "cards" -> CardsCommands.run(context)
            "media" -> MediaCommands.run(context)
            "styles" -> StylesCommands.run(context)
            "validate" -> ValidateCommand.run(context)
            "stats" -> StatsCommands.stats(context)
            "weak" -> StatsCommands.weak(context)
            "import", "export", "build" -> IoCommands.run(context, command)
            "io" -> IoCommands.run(context.shifted(1), context.arguments.positional(0, "import|export|build"))
            else -> throw CliFailure.usage("unknown command '$command'. Run `bert help`.")
        }
    }

    private fun help(topic: String?): Output {
        val text = Help.text(topic)
        return Output(buildJsonObject { put("help", text) }, text)
    }

    private fun failureResult(failure: CliFailure, json: Boolean): CliResult =
        if (json) {
            CliResult(failure.exitCode, errorJson(failure).compact(), "")
        } else {
            val details = failure.details?.let { "\n" + prettyJson.encodeToString(JsonElement.serializer(), it) }.orEmpty()
            CliResult(failure.exitCode, "", "bert: ${failure.message}$details")
        }
}

private val prettyJson = Json { prettyPrint = true }

fun JsonElement.compact(): String = Json.encodeToString(JsonElement.serializer(), this)

fun JsonElement.pretty(): String = prettyJson.encodeToString(JsonElement.serializer(), this)

fun EditorException.toCliFailure(): CliFailure {
    val exitCode = when (kind) {
        EditorFailureKind.NotFound -> ExitCode.NotFound
        EditorFailureKind.Invalid -> ExitCode.Invalid
        EditorFailureKind.ReadOnly -> ExitCode.ReadOnly
        EditorFailureKind.Conflict -> ExitCode.Conflict
        EditorFailureKind.Failure -> ExitCode.Failure
    }
    if (kind == EditorFailureKind.ReadOnly) packId?.let { return CliFailure.readOnly(it) }
    return CliFailure(exitCode, message.orEmpty(), problems.takeIf { it.isNotEmpty() }?.let(::problemsJson))
}
