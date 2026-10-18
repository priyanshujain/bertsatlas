package app.bertsatlas.cli

import app.bertsatlas.domain.editor.MediaNameClash
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object MediaCommands {
    fun run(context: Context): Output = when (val action = context.arguments.positional(0, "add|list|remove")) {
        "add" -> add(context, context.arguments.positional(1, "pack"), context.arguments.positionalsFrom(2, "file"))
        "list" -> list(context, context.arguments.positional(1, "pack"))
        "remove" -> remove(context, context.arguments.positional(1, "pack"), context.arguments.positionalsFrom(2, "name"))
        else -> throw CliFailure.usage("unknown media command '$action'")
    }

    private fun add(context: Context, packId: String, files: List<String>): Output {
        val rename = context.arguments.value("name")
        if (rename != null && files.size != 1) throw CliFailure.usage("--name works with a single file")
        val clash = if (context.arguments.has("force")) MediaNameClash.Replace else MediaNameClash.Fail
        val added = files.map { file ->
            val media = context.editor.addMedia(packId, context.absolute(file), rename, clash)
            buildJsonObject {
                put("name", media.name)
                put("bytes", media.bytes)
                put("reference", media.reference)
                put("reusedExisting", media.reusedExisting)
            }
        }
        return Output(buildJsonObject {
            put("pack", packId)
            put("media", JsonArray(added))
        }, added.joinToString("\n") { "Added ${it.string("name")}: use ${it.string("reference")}" })
    }

    private fun list(context: Context, packId: String): Output = context.library.read(packId) { pack ->
        val referenced = referencedMedia(context, pack)
        val media = pack.mediaFiles().entries.sortedBy { it.key }.map { (name, bytes) ->
            buildJsonObject {
                put("name", name)
                put("bytes", bytes)
                put("referenced", name in referenced)
            }
        }
        val missing = (referenced - pack.mediaFiles().keys).sorted()
        Output(buildJsonObject {
            put("pack", packId)
            put("media", JsonArray(media))
            put("missing", stringArray(missing))
        }, (media.map { "${it.string("name")}  ${it.long("bytes")} bytes" + if (it.boolean("referenced")) "" else "  unused" } +
            missing.map { "$it  missing" }).joinToString("\n").ifEmpty { "No media" })
    }

    private fun remove(context: Context, packId: String, names: List<String>): Output {
        val removed = context.editor.removeMedia(packId, names, evenIfUsed = context.arguments.has("force"))
        return Output(buildJsonObject {
            put("pack", packId)
            put("removed", stringArray(removed))
        }, "Removed ${removed.joinToString()}")
    }

    private fun referencedMedia(context: Context, pack: OpenPack): Set<String> = context.editor.referencedMedia(pack)
}
