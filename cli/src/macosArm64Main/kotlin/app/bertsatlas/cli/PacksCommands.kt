package app.bertsatlas.cli

import app.bertsatlas.domain.editor.PackAppearance
import app.bertsatlas.domain.editor.PackDraft
import app.bertsatlas.domain.pack.PackManifestCodec
import app.bertsatlas.domain.taxonomy.BundledTaxonomy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object PacksCommands {
    fun run(context: Context): Output = when (val action = context.arguments.positional(0, "list|show|create|rename|delete|copy")) {
        "list" -> list(context)
        "show" -> show(context, context.arguments.positional(1, "pack"))
        "create" -> create(context)
        "rename" -> rename(context, context.arguments.positional(1, "pack"))
        "delete" -> delete(context, context.arguments.positional(1, "pack"))
        "copy" -> copy(context, context.arguments.positional(1, "pack"))
        else -> throw CliFailure.usage("unknown packs command '$action'")
    }

    private fun list(context: Context): Output {
        val packs = context.library.packIds().map { packId ->
            runCatching { context.library.read(packId) { packSummary(it) } }.getOrElse { failure ->
                buildJsonObject {
                    put("packId", packId)
                    put("error", failure.message.orEmpty())
                }
            }
        }
        val text = if (packs.isEmpty()) {
            "No packs in ${context.library.directory}"
        } else {
            packs.joinToString("\n") { pack ->
                val id = pack.string("packId")
                pack.string("error")?.let { "$id  (unreadable: $it)" }
                    ?: "$id  ${pack.string("title")}  ${pack.long("cards")} cards" + if (pack.boolean("official")) "  official" else ""
            }
        }
        return Output(buildJsonObject { put("packs", JsonArray(packs)) }, text)
    }

    private fun show(context: Context, packId: String): Output = context.library.read(packId) { pack ->
        val summary = packSummary(pack)
        val styles = pack.collection.notes().groupingBy { note -> pack.collection.notetype(note.notetypeId)?.name ?: "unknown" }.eachCount()
        val json = JsonObject(
            summary.toMap() + mapOf(
                "manifest" to Json.parseToJsonElement(PackManifestCodec.encodeJson(pack.manifest)),
                "styles" to JsonObject(styles.mapValues { JsonPrimitive(it.value) }),
                "mediaFiles" to JsonPrimitive(pack.mediaFiles().size),
                "path" to JsonPrimitive(context.library.packDirectory(packId)),
            ),
        )
        val text = buildString {
            appendLine("${pack.manifest.title} ($packId)")
            pack.manifest.subtitle?.let { appendLine(it) }
            appendLine("${summary.long("notes")} notes, ${summary.long("cards")} cards, ${pack.mediaFiles().size} media files")
            appendLine("Category: ${pack.manifest.category.orEmpty()} / ${pack.manifest.subcategory.orEmpty()}")
            appendLine(if (pack.manifest.official) "Official, read-only" else "Editable")
            if (pack.sections().isNotEmpty()) appendLine("Sections: ${pack.sections().joinToString()}")
            styles.forEach { (style, count) -> appendLine("  $style: $count") }
            append("Path: ${context.library.packDirectory(packId)}")
        }
        Output(json, text)
    }

    private fun create(context: Context): Output {
        val arguments = context.arguments
        val packId = context.editor.createPack(
            PackDraft(
                title = arguments.required("title"),
                subtitle = arguments.value("subtitle"),
                category = arguments.required("category").trim(),
                subcategory = arguments.required("subcategory").trim(),
                accentColor = arguments.value("accent"),
                packId = arguments.value("id"),
            ),
        )
        return context.library.read(packId) { pack ->
            Output(packSummary(pack), "Created ${pack.manifest.title} ($packId) at ${context.library.packDirectory(packId)}")
        }
    }

    private fun rename(context: Context, packId: String): Output {
        val title = context.arguments.required("title")
        val manifest = context.library.read(packId) { it.manifest }
        context.editor.updateAppearance(
            packId,
            PackAppearance(title, context.arguments.value("subtitle")?.trim()?.ifEmpty { null } ?: manifest.subtitle, manifest.accentColor, manifest.coverMedia),
        )
        return context.library.read(packId) { pack -> Output(packSummary(pack), "Renamed $packId to ${pack.manifest.title}") }
    }

    private fun delete(context: Context, packId: String): Output {
        if (!context.library.contains(packId)) throw CliFailure.notFound("no pack named $packId in ${context.library.directory}")
        if (!context.arguments.has("yes")) throw CliFailure.usage("deleting $packId removes its cards, media and progress. Re-run with --yes")
        context.editor.deletePack(packId)
        return Output(buildJsonObject {
            put("packId", packId)
            put("deleted", true)
        }, "Deleted $packId")
    }

    private fun copy(context: Context, packId: String): Output {
        val newId = context.editor.makeCopy(packId, context.arguments.value("title"), context.arguments.value("id"))
        return context.library.read(newId) { pack -> Output(packSummary(pack), "Copied $packId to ${pack.manifest.title} ($newId)") }
    }

    fun packSummary(pack: OpenPack): JsonObject {
        val deckIds = pack.deckIds(null)
        val cards = pack.collection.cardCountInDecks(deckIds)
        val notes = pack.collection.noteGuidsInDecks(deckIds).size
        return buildJsonObject {
            put("packId", pack.packId)
            put("title", pack.manifest.title)
            pack.manifest.subtitle?.let { put("subtitle", it) }
            put("official", pack.manifest.official)
            put("version", pack.manifest.version)
            pack.manifest.category?.let { put("category", it) }
            pack.manifest.subcategory?.let { put("subcategory", it) }
            pack.manifest.accentColor?.let { put("accentColor", it) }
            put("notes", notes)
            put("cards", cards)
            put("sections", stringArray(pack.sections()))
        }
    }

    fun requireKnownPlacement(category: String, subcategory: String) {
        BundledTaxonomy.taxonomy.placementProblem(category, subcategory)?.let { throw CliFailure.invalid(it) }
    }
}
