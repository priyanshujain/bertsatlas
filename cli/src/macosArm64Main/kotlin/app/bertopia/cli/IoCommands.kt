package app.bertopia.cli

import app.bertopia.anki.pkg.PackageExport
import app.bertopia.anki.pkg.PackageFormat
import app.bertopia.domain.pack.DeckSelection
import app.bertopia.domain.pack.PackBuildException
import app.bertopia.domain.pack.PackImporter
import app.bertopia.domain.pack.PackSourceBuilder
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object IoCommands {
    fun run(context: Context, command: String): Output = when (command) {
        "import" -> import(context, context.arguments.positional(0, "file"))
        "export" -> export(context, context.arguments.positional(0, "pack"))
        "build" -> build(context, context.arguments.positional(0, "source dir"))
        else -> throw CliFailure.usage("unknown io command '$command'")
    }

    private fun import(context: Context, file: String): Output {
        val path = context.absolute(file)
        if (!Files.exists(path) || Files.isDirectory(path)) throw CliFailure.notFound("no file at $path")
        val arguments = context.arguments
        val workingDirectory = context.library.newWorkingDirectory("import")
        SystemFileSystem.createDirectories(Path(context.library.directory))
        try {
            val importer = PackImporter(context.library.directory, workingDirectory)
            val plan = try {
                importer.plan(path)
            } catch (failure: Exception) {
                throw CliFailure.invalid("$path is not a readable Anki package: ${failure.message}")
            }
            return plan.use {
                val requested = arguments.values("deck").map { value -> value.toLongOrNull() ?: throw CliFailure.usage("--deck expects a deck id") }
                val decks = if (requested.isEmpty()) plan.decks else requested.map { id ->
                    plan.decks.firstOrNull { it.deckId == id } ?: throw CliFailure.notFound("no top-level deck $id in $path")
                }
                if (decks.isEmpty()) throw CliFailure.invalid("$path has no decks with cards")
                val title = arguments.value("title")?.trim()?.ifEmpty { null }
                if (title != null && decks.size != 1) throw CliFailure.usage("--title works when importing a single deck; pick one with --deck")
                val newDecks = decks.filter { it.existingPack == null }
                val category = arguments.value("category")?.trim().orEmpty()
                val subcategory = arguments.value("subcategory")?.trim().orEmpty()
                if (newDecks.isNotEmpty() && (category.isEmpty() || subcategory.isEmpty())) {
                    throw CliFailure.usage(
                        "new packs need --category and --subcategory. Decks: " +
                            decks.joinToString("; ") { "${it.deckId} ${it.title} (${it.cardCount} cards)" + (it.existingPack?.let { pack -> " merges into ${pack.packId}" } ?: "") },
                    )
                }
                if (newDecks.isNotEmpty()) PacksCommands.requireKnownPlacement(category, subcategory)
                val imported = importer.import(plan, decks.map { DeckSelection(it.deckId, title ?: it.title, category, subcategory) })
                imported.forEach { pack -> context.library.prepareForSharedAccess(pack.packId) }
                val packs = imported.map { pack ->
                    buildJsonObject {
                        put("packId", pack.packId)
                        put("title", pack.title)
                        put("merged", pack.mergedIntoExisting)
                        put("addedNotes", pack.addedNotes)
                        put("updatedNotes", pack.updatedNotes)
                        put("addedCards", pack.addedCards)
                        put("mediaFiles", pack.mediaFiles)
                    }
                }
                Output(buildJsonObject { put("imported", JsonArray(packs)) }, imported.joinToString("\n") { pack ->
                    (if (pack.mergedIntoExisting) "Merged into ${pack.packId}" else "Imported ${pack.title} as ${pack.packId}") +
                        ": ${pack.addedNotes} new notes, ${pack.updatedNotes} updated, ${pack.addedCards} new cards, ${pack.mediaFiles} media"
                })
            }
        } finally {
            Files.removeRecursively(workingDirectory)
        }
    }

    private fun export(context: Context, packId: String): Output {
        if (!context.library.contains(packId)) throw CliFailure.notFound("no pack named $packId in ${context.library.directory}")
        val format = if (context.arguments.has("legacy")) PackageFormat.Legacy else PackageFormat.Modern
        val includeProgress = !context.arguments.has("no-progress")
        val output = context.absolute(context.arguments.value("out") ?: "$packId.apkg")
        if (Files.isDirectory(output)) throw CliFailure.usage("--out must be a file path")
        val workingDirectory = context.library.newWorkingDirectory("export")
        try {
            PackageExport.export(
                collectionPath = context.library.collectionPath(packId),
                mediaDirectory = context.library.mediaDirectory(packId),
                outputPath = output,
                workingDirectory = workingDirectory,
                format = format,
                includeScheduling = includeProgress,
            )
        } finally {
            Files.removeRecursively(workingDirectory)
        }
        return Output(buildJsonObject {
            put("pack", packId)
            put("path", output)
            put("format", format.name.lowercase())
            put("includesProgress", includeProgress)
            put("bytes", Files.size(output))
        }, "Exported $packId to $output (${format.name.lowercase()}${if (includeProgress) "" else ", without progress"})")
    }

    private fun build(context: Context, source: String): Output {
        val sourceDirectory = context.absolute(source)
        if (!Files.isDirectory(sourceDirectory)) throw CliFailure.notFound("no directory at $sourceDirectory")
        val packId = try {
            PackSourceBuilder.read(sourceDirectory).manifest.packId
        } catch (failure: Exception) {
            throw CliFailure.invalid("could not read pack source: ${failure.message}")
        }
        val output = context.absolute(context.arguments.value("out") ?: "$packId.apkg")
        val format = if (context.arguments.has("legacy")) PackageFormat.Legacy else PackageFormat.Modern
        val workingDirectory = context.library.newWorkingDirectory("build")
        val built = try {
            PackSourceBuilder.build(sourceDirectory, workingDirectory, output, format)
        } catch (failure: PackBuildException) {
            throw CliFailure.invalid(failure.message.orEmpty(), failure.issues.takeIf { it.isNotEmpty() }?.let(::issuesJson))
        } finally {
            Files.removeRecursively(workingDirectory)
        }
        return Output(buildJsonObject {
            put("pack", packId)
            put("path", built.outputPath)
            put("notes", built.notes)
            put("mediaFiles", built.mediaFiles)
            put("bytes", built.sizeBytes)
            put("warnings", issuesJson(built.issues))
        }, "Built $packId: ${built.notes} notes, ${built.mediaFiles} media, ${built.sizeBytes} bytes -> ${built.outputPath}" +
            built.issues.joinToString("") { "\n" + issueLine(it) })
    }
}
