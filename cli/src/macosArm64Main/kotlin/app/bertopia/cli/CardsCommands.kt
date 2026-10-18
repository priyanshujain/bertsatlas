package app.bertopia.cli

import app.bertopia.anki.collection.renderCard
import app.bertopia.anki.model.Card
import app.bertopia.anki.model.CardData
import app.bertopia.anki.model.Note
import app.bertopia.anki.model.Notetype
import app.bertopia.domain.editor.CardChange
import app.bertopia.domain.editor.CardRefs
import app.bertopia.domain.editor.CardStyles
import app.bertopia.domain.quiz.PackCardReader
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object CardsCommands {
    fun run(context: Context): Output = when (val action = context.arguments.positional(0, "list|show|add|import-table|edit|delete|tag|move")) {
        "list" -> list(context, context.arguments.positional(1, "pack"))
        "show" -> show(context, context.arguments.positional(1, "pack"), context.arguments.positional(2, "note"))
        "add" -> add(context, context.arguments.positional(1, "pack"))
        "import-table" -> importTable(context, context.arguments.positional(1, "pack"))
        "edit" -> edit(context, context.arguments.positional(1, "pack"), context.arguments.positional(2, "note"))
        "delete" -> delete(context, context.arguments.positional(1, "pack"), context.arguments.positionalsFrom(2, "note"))
        "tag" -> tag(context, context.arguments.positional(1, "pack"), context.arguments.positionalsFrom(2, "note"))
        "move" -> move(context, context.arguments.positional(1, "pack"), context.arguments.positionalsFrom(2, "note"))
        else -> throw CliFailure.usage("unknown cards command '$action'")
    }

    private fun list(context: Context, packId: String): Output = context.library.read(packId) { pack ->
        val arguments = context.arguments
        val cards = pack.collection.cardsInDecks(pack.deckIds(arguments.value("section")))
        val cardsByNote = cards.groupBy { it.noteId }
        val tag = arguments.value("tag")
        val search = arguments.value("search")?.lowercase()
        val limit = arguments.value("limit")?.let { arguments.intValue("limit", Int.MAX_VALUE) } ?: Int.MAX_VALUE
        val notes = cardsByNote.keys.sorted().asSequence()
            .mapNotNull { pack.collection.note(it) }
            .filter { note -> tag == null || note.tags.any { it.equals(tag, ignoreCase = true) || it.startsWith("$tag::", ignoreCase = true) } }
            .filter { note -> search == null || note.fields.any { PackCardReader.plainText(it).lowercase().contains(search) } }
            .take(limit)
            .map { note -> noteSummary(pack, note, cardsByNote[note.id].orEmpty()) }
            .toList()
        val text = notes.joinToString("\n") { note ->
            "${note.long("noteId")}  ${note.string("ref")}  ${note.string("style")}  ${note.string("preview")}" + (note.string("section")?.let { "  [$it]" } ?: "")
        }.ifEmpty { "No cards" }
        Output(buildJsonObject {
            put("pack", packId)
            put("count", notes.size)
            put("notes", JsonArray(notes))
        }, text)
    }

    private fun show(context: Context, packId: String, reference: String): Output = context.library.read(packId) { pack ->
        val note = pack.resolveNote(reference)
        val notetype = pack.collection.notetype(note.notetypeId) ?: throw CliFailure(ExitCode.Failure, "note ${note.id} has no note type")
        val cards = pack.collection.cardsOfNote(note.id)
        val json = JsonObject(
            noteSummary(pack, note, cards).toMap() + mapOf(
                "fields" to JsonObject(notetype.fieldNames.zip(note.fields).associate { (name, value) -> name to JsonPrimitive(value) }),
                "cards" to JsonArray(cards.map { cardJson(pack, notetype, it) }),
            ),
        )
        val text = buildString {
            appendLine("${notetype.name}  note ${note.id}  ref ${CardRefs.of(note.guid)}  guid ${note.guid}")
            notetype.fieldNames.zip(note.fields).filter { it.second.isNotBlank() }.forEach { (name, value) -> appendLine("$name: $value") }
            if (note.tags.isNotEmpty()) appendLine("Tags: ${note.tags.joinToString(" ")}")
            cards.forEach { card ->
                val rendered = pack.collection.renderCard(card)
                append("Card ${card.ordinal + 1} (${cardState(card)}): ")
                appendLine(rendered?.let { readableText(it.question) }.orEmpty())
            }
        }.trimEnd()
        Output(json, text)
    }

    private fun add(context: Context, packId: String): Output {
        val drafts = CardInput.parseAll(context.inputText()).map { it.toDraft() }
        val result = context.editor.addCards(packId, drafts)
        val warnings = problemsJson(result.warnings).map { (it as JsonObject).let { warning -> JsonObject(warning + ("severity" to JsonPrimitive("warning"))) } }
        return Output(buildJsonObject {
            put("pack", packId)
            put("added", result.added.size)
            put("cards", result.cardCount)
            put("notes", JsonArray(result.added.map { card ->
                buildJsonObject {
                    put("index", card.index)
                    put("noteId", card.noteId)
                    put("guid", card.guid)
                    put("ref", card.ref)
                    put("cards", card.cards)
                }
            }))
            put("warnings", JsonArray(warnings))
        }, "Added ${result.added.size} note(s), ${result.cardCount} card(s) to $packId" + result.warnings.joinToString("") { "\nwarning: ${it.message}" })
    }

    private fun importTable(context: Context, packId: String): Output {
        val arguments = context.arguments
        val header = when {
            arguments.has("header") -> true
            arguments.has("no-header") -> false
            else -> null
        }
        val plan = context.editor.planTableImport(packId, context.inputText(), arguments.required("style"), header)
        val rejected = JsonArray(plan.invalidRows.map { row ->
            buildJsonObject {
                put("row", row.rowNumber)
                put("problems", problemsJson(row.problems))
            }
        })
        val mapping = JsonObject(plan.columnFields.entries.associate { (column, field) -> (column + 1).toString() to JsonPrimitive(field) })
        if (arguments.has("dry-run") || plan.validDrafts.isEmpty()) {
            return Output(buildJsonObject {
                put("pack", packId)
                put("header", plan.hasHeader)
                put("columns", mapping)
                put("ready", plan.validDrafts.size)
                put("rejected", rejected)
            }, "${plan.validDrafts.size} row(s) ready, ${plan.invalidRows.size} rejected" + plan.invalidRows.joinToString("") { row ->
                "\nrow ${row.rowNumber}: ${row.problems.joinToString("; ") { it.message }}"
            })
        }
        val result = context.editor.addCards(packId, plan.validDrafts)
        return Output(buildJsonObject {
            put("pack", packId)
            put("header", plan.hasHeader)
            put("columns", mapping)
            put("added", result.added.size)
            put("cards", result.cardCount)
            put("rejected", rejected)
        }, "Added ${result.added.size} note(s), ${result.cardCount} card(s) to $packId; ${plan.invalidRows.size} row(s) skipped" + plan.invalidRows.joinToString("") { row ->
            "\nrow ${row.rowNumber}: ${row.problems.joinToString("; ") { it.message }}"
        })
    }

    private fun edit(context: Context, packId: String, reference: String): Output {
        val fieldArguments = context.arguments.values("field").associate { assignment ->
            val name = assignment.substringBefore('=', missingDelimiterValue = "")
            if (name.isEmpty()) throw CliFailure.usage("--field expects Name=Value")
            name to assignment.substringAfter('=')
        }
        val change = if (fieldArguments.isEmpty()) parseEdit(context.inputText()) else CardChange(fieldArguments, null)
        val edited = context.editor.editCard(packId, reference, change)
        return context.library.read(packId) { pack ->
            val saved = pack.collection.note(edited.noteId) ?: throw CliFailure.notFound("no note $reference in $packId")
            Output(JsonObject(noteSummary(pack, saved, pack.collection.cardsOfNote(saved.id)).toMap() + ("newCards" to JsonPrimitive(edited.newCards))),
                "Updated note ${saved.id}" + if (edited.newCards > 0) " (${edited.newCards} new card(s))" else "")
        }
    }

    private fun delete(context: Context, packId: String, references: List<String>): Output {
        val notes = context.library.read(packId) { pack -> references.map { pack.resolveNote(it) }.distinctBy { it.id } }
        context.editor.deleteCards(packId, notes.map { it.id.toString() })
        return Output(buildJsonObject {
            put("pack", packId)
            put("deleted", JsonArray(notes.map { note -> buildJsonObject { put("noteId", note.id); put("guid", note.guid) } }))
        }, "Deleted ${notes.size} note(s) from $packId")
    }

    private fun tag(context: Context, packId: String, references: List<String>): Output {
        val add = context.arguments.values("add")
        val remove = context.arguments.values("remove")
        if (add.isEmpty() && remove.isEmpty()) throw CliFailure.usage("give --add and/or --remove")
        context.editor.tagCards(packId, references, add, remove)
        return context.library.read(packId) { pack ->
            val notes = references.map { pack.resolveNote(it) }.distinctBy { it.id }
            Output(buildJsonObject {
                put("pack", packId)
                put("notes", JsonArray(notes.map { note -> buildJsonObject { put("noteId", note.id); put("tags", stringArray(note.tags)) } }))
            }, "Tagged ${notes.size} note(s)")
        }
    }

    private fun move(context: Context, packId: String, references: List<String>): Output {
        val section = context.arguments.value("section") ?: throw CliFailure.usage("--section is required; use --section \"\" for the top of the pack")
        val moved = context.editor.moveCards(packId, references, section)
        return Output(buildJsonObject {
            put("pack", packId)
            put("moved", moved)
            put("section", section)
        }, "Moved $moved note(s) to ${section.ifBlank { "the top of $packId" }}")
    }

    private fun parseEdit(text: String): CardChange {
        val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject
            ?: throw CliFailure.invalid("edit input must be a JSON object like {\"fields\": {...}, \"tags\": [...]}")
        val unknownKeys = root.keys - setOf("fields", "tags")
        if (unknownKeys.isNotEmpty()) throw CliFailure.invalid("unknown keys ${unknownKeys.joinToString()}")
        val fields = (root["fields"] as? JsonObject)?.mapValues { (name, value) ->
            when (value) {
                is JsonPrimitive -> value.content
                is JsonObject -> value.toString()
                else -> throw CliFailure.invalid("field $name must be text")
            }
        }.orEmpty()
        val tags = (root["tags"] as? JsonArray)?.map { (it as? JsonPrimitive)?.content ?: throw CliFailure.invalid("tags must be strings") }
        if (fields.isEmpty() && tags == null) throw CliFailure.invalid("nothing to change")
        return CardChange(fields, tags)
    }

    fun noteSummary(pack: OpenPack, note: Note, cards: List<Card>): JsonObject {
        val notetype = pack.collection.notetype(note.notetypeId)
        val sortIndex = notetype?.sortFieldIndex ?: 0
        return buildJsonObject {
            put("noteId", note.id)
            put("ref", CardRefs.of(note.guid))
            put("guid", note.guid)
            put("style", notetype?.let { CardStyles.resolve(it.name)?.let(CardStyles::key) ?: it.name } ?: "unknown")
            put("preview", readableText(note.fields.getOrElse(sortIndex) { "" }).take(PREVIEW_LENGTH))
            cards.firstOrNull()?.let { card -> pack.sectionOf(card.deckId)?.let { put("section", it) } }
            put("tags", stringArray(note.tags))
            put("cardCount", cards.size)
        }
    }

    private fun cardJson(pack: OpenPack, notetype: Notetype, card: Card): JsonObject {
        val data = CardData.decode(card.scheduling.data)
        val rendered = pack.collection.renderCard(card)
        return buildJsonObject {
            put("cardId", card.id)
            put("ordinal", card.ordinal + 1)
            put("template", notetype.templates.firstOrNull { it.ordinal == card.ordinal }?.name ?: notetype.templates.firstOrNull()?.name.orEmpty())
            put("state", cardState(card))
            put("due", card.scheduling.due)
            put("intervalDays", card.scheduling.interval)
            put("reps", card.scheduling.reps)
            put("lapses", card.scheduling.lapses)
            data.stability?.let { put("stability", it) }
            data.difficulty?.let { put("difficulty", it) }
            rendered?.let {
                put("question", readableText(it.question))
                put("answer", answerSideText(it.answer))
            }
        }
    }

    fun cardState(card: Card): String = when (card.scheduling.queue) {
        -1 -> "suspended"
        -2, -3 -> "buried"
        0 -> "new"
        2 -> "review"
        else -> if (card.scheduling.type == 3) "relearning" else "learning"
    }

    private const val PREVIEW_LENGTH = 80
}

