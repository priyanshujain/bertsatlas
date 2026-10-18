package app.bertsatlas.cli

import app.bertsatlas.domain.pack.BertNoteType
import app.bertsatlas.domain.pack.BertNoteTypes
import app.bertsatlas.domain.pack.NoteTypeKind
import app.bertsatlas.domain.editor.CardDraft
import app.bertsatlas.domain.editor.CardStyles
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object StyleSchemas {
    val all: List<BertNoteType> = CardStyles.all

    fun summary(style: BertNoteType): JsonObject = buildJsonObject {
        put("style", CardStyles.key(style))
        put("name", style.name)
        put("description", CardStyles.description(style))
        putJsonArray("fields") { style.fields.forEach { add(it) } }
        putJsonArray("requiredFields") { style.fields.filter { it in style.requiredFields }.forEach { add(it) } }
        put("cardsPerNote", if (style.kind == NoteTypeKind.Cloze) "one per cloze number" else style.templates.size.toString())
    }

    fun schema(style: BertNoteType): JsonObject = buildJsonObject {
        put("\$schema", "https://json-schema.org/draft/2020-12/schema")
        put("title", "${style.name} card")
        put("description", CardStyles.description(style))
        put("type", "object")
        putJsonArray("required") {
            add("style")
            add("fields")
        }
        put("additionalProperties", false)
        putJsonObject("properties") {
            putJsonObject("style") {
                putJsonArray("enum") {
                    add(CardStyles.key(style))
                    add(style.name)
                }
            }
            putJsonObject("guid") {
                put("type", "string")
                put("description", "Stable id. Generated when omitted.")
            }
            putJsonObject("section") {
                put("type", "string")
                put("description", "Optional section inside the pack; nest with ::")
            }
            putJsonObject("tags") {
                put("type", "array")
                putJsonObject("items") {
                    put("type", "string")
                    put("pattern", "^\\S+$")
                }
            }
            putJsonObject("fields") {
                put("type", "object")
                put("additionalProperties", false)
                putJsonArray("required") { style.fields.filter { it in style.requiredFields }.forEach { add(it) } }
                putJsonObject("properties") {
                    style.fields.forEach { field ->
                        putJsonObject(field) {
                            if (field == BertNoteTypes.BERT_META_FIELD) {
                                putJsonArray("type") {
                                    add("object")
                                    add("string")
                                }
                            } else {
                                put("type", "string")
                                if (field in style.requiredFields) put("minLength", 1)
                            }
                            fieldDescription(field)?.let { put("description", it) }
                        }
                    }
                }
            }
        }
    }

    private fun fieldDescription(field: String): String? = when (field) {
        "Aliases" -> "Newline separated accepted alternative answers"
        "Hint" -> "Optional clue shown on request"
        "Fact" -> "Shown after answering"
        "Source" -> "Where the fact comes from, one per line"
        "Capitals" -> "Newline separated list"
        "Choices" -> "Newline separated options, 2 to 5, no duplicates"
        "Correct" -> "Exact text of the one right line in Choices"
        "Explanation" -> "Shown after answering"
        BertNoteTypes.BERT_META_FIELD -> "Versioned JSON for data with no natural field. For general styles {\"flipOnly\": true} keeps the card a flip and grade prompt instead of a generated multiple choice"
        else -> null
    }
}

class CardInput(val style: BertNoteType, val fields: Map<String, String>, val tags: List<String>, val section: String?, val guid: String?) {
    fun toDraft(): CardDraft = CardDraft(style.name, fields, tags, section, guid)

    companion object {
        fun parseAll(text: String): List<CardInput> {
            val root = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(text) }
                .getOrElse { throw CliFailure.invalid("input is not valid JSON: ${it.message}") }
            val items = when (root) {
                is JsonArray -> root
                is JsonObject -> (root["cards"] as? JsonArray) ?: JsonArray(listOf(root))
                else -> throw CliFailure.invalid("input must be a card object, an array of cards, or {\"cards\": [...]}")
            }
            if (items.isEmpty()) throw CliFailure.invalid("input has no cards")
            val problems = mutableListOf<JsonObject>()
            val cards = items.mapIndexedNotNull { index, item ->
                runCatching { parse(item) }.getOrElse { failure ->
                    problems += buildJsonObject {
                        put("index", index)
                        put("message", failure.message.orEmpty())
                    }
                    null
                }
            }
            if (problems.isNotEmpty()) throw CliFailure.invalid("${problems.size} card(s) could not be read", JsonArray(problems))
            return cards
        }

        fun parse(item: JsonElement): CardInput {
            val card = item as? JsonObject ?: throw CliFailure.invalid("each card must be a JSON object")
            val unknownKeys = card.keys - setOf("style", "noteType", "fields", "tags", "section", "guid")
            if (unknownKeys.isNotEmpty()) throw CliFailure.invalid("unknown keys ${unknownKeys.joinToString()}")
            val styleName = (card["style"] ?: card["noteType"])?.let(::string)
                ?: throw CliFailure.invalid("card needs a style")
            val style = CardStyles.require(styleName)
            val fields = (card["fields"] as? JsonObject ?: throw CliFailure.invalid("card needs a fields object")).mapValues { (name, value) ->
                when (value) {
                    is JsonNull -> ""
                    is JsonPrimitive -> value.content
                    is JsonObject -> if (name == BertNoteTypes.BERT_META_FIELD) value.toString() else throw CliFailure.invalid("field $name must be text")
                    else -> throw CliFailure.invalid("field $name must be text")
                }
            }
            val unknownFields = fields.keys - style.fields.toSet()
            if (unknownFields.isNotEmpty()) {
                throw CliFailure.invalid("${style.name} has no field ${unknownFields.joinToString()}. Fields: ${style.fields.joinToString()}")
            }
            val tags = when (val raw = card["tags"]) {
                null, is JsonNull -> emptyList()
                is JsonArray -> raw.map { string(it) ?: throw CliFailure.invalid("tags must be strings") }
                is JsonPrimitive -> raw.content.split(' ').filter { it.isNotBlank() }
                else -> throw CliFailure.invalid("tags must be an array of strings")
            }
            return CardInput(style, fields, tags, card["section"]?.let(::string)?.takeIf { it.isNotBlank() }, card["guid"]?.let(::string)?.takeIf { it.isNotBlank() })
        }

        private fun string(element: JsonElement): String? = (element as? JsonPrimitive)?.takeIf { it.isString }?.content
    }
}

fun stringArray(values: Iterable<String>): JsonArray = buildJsonArray { values.forEach { add(it) } }
