package app.bertopia.anki.collection

import app.bertopia.anki.model.CardTemplate
import app.bertopia.anki.model.Deck
import app.bertopia.anki.model.DeckConfig
import app.bertopia.anki.model.Notetype
import app.bertopia.anki.model.NotetypeField
import app.bertopia.anki.model.NotetypeKind
import app.bertopia.anki.template.CardTemplateRenderer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

internal object Schema11Json {
    fun notetypeFrom(json: JsonObject): Notetype = Notetype(
        id = json.long("id"),
        name = json.string("name"),
        kind = if (json.int("type") == 1) NotetypeKind.Cloze else NotetypeKind.Standard,
        fields = json.array("flds").map { it.jsonObject }.map { field ->
            NotetypeField(field.string("name"), field.int("ord"), field)
        }.sortedBy { it.ordinal },
        templates = json.array("tmpls").map { it.jsonObject }.map { template ->
            CardTemplate(template.string("name"), template.int("ord"), template.string("qfmt"), template.string("afmt"), template)
        }.sortedBy { it.ordinal },
        css = json.string("css"),
        sortFieldIndex = json.int("sortf"),
        modifiedSecs = json.long("mod"),
        preserved = json,
    )

    fun notetypeTo(notetype: Notetype): JsonObject {
        val fields = notetype.fields.mapIndexed { index, field ->
            field.preserved.withDefaults(
                "sticky" to JsonPrimitive(false),
                "rtl" to JsonPrimitive(false),
                "font" to JsonPrimitive("Arial"),
                "size" to JsonPrimitive(20),
                "media" to JsonArray(emptyList()),
            ).with("name" to JsonPrimitive(field.name), "ord" to JsonPrimitive(index))
        }
        val templates = notetype.templates.mapIndexed { index, template ->
            template.preserved.withDefaults(
                "bqfmt" to JsonPrimitive(""),
                "bafmt" to JsonPrimitive(""),
                "did" to JsonNull,
                "bfont" to JsonPrimitive(""),
                "bsize" to JsonPrimitive(0),
            ).with(
                "name" to JsonPrimitive(template.name),
                "ord" to JsonPrimitive(index),
                "qfmt" to JsonPrimitive(template.questionFormat),
                "afmt" to JsonPrimitive(template.answerFormat),
            )
        }
        return notetype.preserved.withDefaults(
            "usn" to JsonPrimitive(-1),
            "did" to JsonNull,
            "latexPre" to JsonPrimitive(DEFAULT_LATEX_PRE),
            "latexPost" to JsonPrimitive("\\end{document}"),
            "latexsvg" to JsonPrimitive(false),
            "tags" to JsonArray(emptyList()),
            "vers" to JsonArray(emptyList()),
        ).with(
            "id" to JsonPrimitive(notetype.id),
            "name" to JsonPrimitive(notetype.name),
            "type" to JsonPrimitive(if (notetype.kind == NotetypeKind.Cloze) 1 else 0),
            "mod" to JsonPrimitive(notetype.modifiedSecs),
            "sortf" to JsonPrimitive(notetype.sortFieldIndex),
            "css" to JsonPrimitive(notetype.css),
            "flds" to JsonArray(fields),
            "tmpls" to JsonArray(templates),
            "req" to cardRequirements(notetype),
        )
    }

    private fun cardRequirements(notetype: Notetype): JsonArray = JsonArray(
        notetype.templates.mapIndexed { index, template ->
            val referenced = CardTemplateRenderer.referencedFieldNames(template.questionFormat)
            val fieldIndexes = notetype.fields.mapIndexedNotNull { fieldIndex, field -> fieldIndex.takeIf { field.name in referenced } }
            JsonArray(listOf(JsonPrimitive(index), JsonPrimitive("any"), JsonArray(fieldIndexes.map { JsonPrimitive(it) })))
        },
    )

    fun deckFrom(json: JsonObject): Deck = Deck(
        id = json.long("id"),
        name = json.string("name"),
        description = json.string("desc"),
        configId = json["conf"]?.jsonPrimitive?.longOrNull ?: 1,
        isFiltered = json.int("dyn") != 0,
        modifiedSecs = json.long("mod"),
        preserved = json,
    )

    fun deckTo(deck: Deck): JsonObject {
        val dayCounts = JsonArray(listOf(JsonPrimitive(0), JsonPrimitive(0)))
        val withDefaults = deck.preserved.withDefaults(
            "usn" to JsonPrimitive(-1),
            "collapsed" to JsonPrimitive(false),
            "browserCollapsed" to JsonPrimitive(false),
            "newToday" to dayCounts,
            "revToday" to dayCounts,
            "lrnToday" to dayCounts,
            "timeToday" to dayCounts,
            "extendNew" to JsonPrimitive(0),
            "extendRev" to JsonPrimitive(0),
        ).with(
            "id" to JsonPrimitive(deck.id),
            "name" to JsonPrimitive(deck.name),
            "desc" to JsonPrimitive(deck.description),
            "mod" to JsonPrimitive(deck.modifiedSecs),
            "dyn" to JsonPrimitive(if (deck.isFiltered) 1 else 0),
        )
        return if (deck.isFiltered) withDefaults else withDefaults.with("conf" to JsonPrimitive(deck.configId))
    }

    fun deckConfigFrom(json: JsonObject): DeckConfig {
        val defaults = DeckConfig(id = 0, name = "")
        val new = json["new"]?.jsonObject
        val rev = json["rev"]?.jsonObject
        val lapse = json["lapse"]?.jsonObject
        val parameters = fsrsParameterKeys
            .firstNotNullOfOrNull { key -> json[key]?.doubles()?.takeIf { it.isNotEmpty() } }
        return DeckConfig(
            id = json.long("id"),
            name = json.string("name"),
            newCardsPerDay = new?.get("perDay")?.jsonPrimitive?.intOrNull ?: defaults.newCardsPerDay,
            reviewsPerDay = rev?.get("perDay")?.jsonPrimitive?.intOrNull ?: defaults.reviewsPerDay,
            learningStepsMinutes = new?.get("delays")?.doubles() ?: defaults.learningStepsMinutes,
            relearningStepsMinutes = lapse?.get("delays")?.doubles() ?: defaults.relearningStepsMinutes,
            desiredRetention = json["desiredRetention"]?.jsonPrimitive?.doubleOrNull ?: defaults.desiredRetention,
            fsrsParameters = parameters.orEmpty(),
            modifiedSecs = json.long("mod"),
            preserved = json,
        )
    }

    fun deckConfigTo(config: DeckConfig): JsonObject {
        val preserved = config.preserved
        val new = (preserved["new"] as? JsonObject ?: JsonObject(emptyMap())).withDefaults(
            "bury" to JsonPrimitive(false),
            "initialFactor" to JsonPrimitive(2500),
            "ints" to JsonArray(listOf(1, 4, 0).map { JsonPrimitive(it) }),
            "order" to JsonPrimitive(1),
        ).with("perDay" to JsonPrimitive(config.newCardsPerDay), "delays" to config.learningStepsMinutes.toJson())
        val rev = (preserved["rev"] as? JsonObject ?: JsonObject(emptyMap())).withDefaults(
            "bury" to JsonPrimitive(false),
            "ease4" to JsonPrimitive(1.3),
            "ivlFct" to JsonPrimitive(1.0),
            "maxIvl" to JsonPrimitive(36500),
            "hardFactor" to JsonPrimitive(1.2),
        ).with("perDay" to JsonPrimitive(config.reviewsPerDay))
        val lapse = (preserved["lapse"] as? JsonObject ?: JsonObject(emptyMap())).withDefaults(
            "leechAction" to JsonPrimitive(1),
            "leechFails" to JsonPrimitive(8),
            "minInt" to JsonPrimitive(1),
            "mult" to JsonPrimitive(0.0),
        ).with("delays" to config.relearningStepsMinutes.toJson())
        val written = preserved.withDefaults(
            "usn" to JsonPrimitive(-1),
            "maxTaken" to JsonPrimitive(60),
            "autoplay" to JsonPrimitive(true),
            "timer" to JsonPrimitive(0),
            "replayq" to JsonPrimitive(true),
            "dyn" to JsonPrimitive(false),
        ).with(
            "id" to JsonPrimitive(config.id),
            "name" to JsonPrimitive(config.name),
            "mod" to JsonPrimitive(config.modifiedSecs),
            "new" to new,
            "rev" to rev,
            "lapse" to lapse,
            "desiredRetention" to JsonPrimitive(config.desiredRetention),
        )
        return written.with(*fsrsParameterKeys.map { key -> key to emptyList<Double>().toJson() }.toTypedArray())
            .with(fsrsParameterKeyFor(config.fsrsParameters) to config.fsrsParameters.toJson())
    }

    private val fsrsParameterKeys = listOf("fsrsParams6", "fsrsParams5", "fsrsWeights")

    private fun fsrsParameterKeyFor(parameters: List<Double>): String = when (parameters.size) {
        FSRS6_PARAMETER_COUNT -> "fsrsParams6"
        FSRS5_PARAMETER_COUNT -> "fsrsParams5"
        else -> "fsrsWeights"
    }

    private const val FSRS6_PARAMETER_COUNT = 21
    private const val FSRS5_PARAMETER_COUNT = 19

    private const val DEFAULT_LATEX_PRE = "\\documentclass[12pt]{article}\n\\special{papersize=3in,5in}\n" +
        "\\usepackage[utf8]{inputenc}\n\\usepackage{amssymb,amsmath}\n\\pagestyle{empty}\n" +
        "\\setlength{\\parindent}{0in}\n\\begin{document}\n"
}

internal fun JsonObject.withDefaults(vararg defaults: Pair<String, JsonElement>): JsonObject =
    JsonObject(defaults.toMap() + this)

internal fun JsonObject.with(vararg values: Pair<String, JsonElement>): JsonObject = JsonObject(this + values.toMap())

private fun JsonObject.string(key: String): String = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: ""

private fun JsonObject.long(key: String): Long = (this[key] as? JsonPrimitive)?.longOrNull ?: 0

private fun JsonObject.int(key: String): Int = (this[key] as? JsonPrimitive)?.let { it.intOrNull ?: it.booleanOrNull?.let { flag -> if (flag) 1 else 0 } } ?: 0

private fun JsonObject.array(key: String): JsonArray = this[key] as? JsonArray ?: JsonArray(emptyList())

private fun JsonElement.doubles(): List<Double>? = (this as? JsonArray)?.mapNotNull { it.jsonPrimitive.doubleOrNull }

private fun List<Double>.toJson(): JsonArray = JsonArray(map { JsonPrimitive(it) })
