package app.bertopia.anki.collection

import androidx.sqlite.SQLiteConnection
import app.bertopia.anki.io.ProtoMessage
import app.bertopia.anki.model.CardTemplate
import app.bertopia.anki.model.DECK_NAME_SEPARATOR
import app.bertopia.anki.model.Deck
import app.bertopia.anki.model.DeckConfig
import app.bertopia.anki.model.Notetype
import app.bertopia.anki.model.NotetypeField
import app.bertopia.anki.model.NotetypeKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal class Schema18Reader(private val connection: SQLiteConnection) {
    fun notetypes(): List<Notetype> {
        val fieldsByNotetype = connection.rows("select ntid, ord, name, config from fields") { statement ->
            val config = ProtoMessage.parse(statement.getBlob(3))
            val json = otherJson(config).withDefaults(
                "sticky" to JsonPrimitive(config.bool(1)),
                "rtl" to JsonPrimitive(config.bool(2)),
                "font" to JsonPrimitive(config.string(3)),
                "size" to JsonPrimitive(config.int(4)),
                "description" to JsonPrimitive(config.string(5)),
                "plainText" to JsonPrimitive(config.bool(6)),
                "collapsed" to JsonPrimitive(config.bool(7)),
                "excludeFromSearch" to JsonPrimitive(config.bool(8)),
                "media" to JsonArray(emptyList()),
            ).with("name" to JsonPrimitive(statement.getText(2)), "ord" to JsonPrimitive(statement.getLong(1)))
            statement.getLong(0) to NotetypeField(statement.getText(2), statement.getLong(1).toInt(), json)
        }.groupBy({ it.first }, { it.second })
        val templatesByNotetype = connection.rows("select ntid, ord, name, config from templates") { statement ->
            val config = ProtoMessage.parse(statement.getBlob(3))
            val targetDeck = config.long(5)
            val json = otherJson(config).withDefaults(
                "bqfmt" to JsonPrimitive(config.string(3)),
                "bafmt" to JsonPrimitive(config.string(4)),
                "did" to if (targetDeck == 0L) JsonNull else JsonPrimitive(targetDeck),
                "bfont" to JsonPrimitive(config.string(6)),
                "bsize" to JsonPrimitive(config.int(7)),
            )
            statement.getLong(0) to CardTemplate(statement.getText(2), statement.getLong(1).toInt(), config.string(1), config.string(2), json)
        }.groupBy({ it.first }, { it.second })
        return connection.rows("select id, name, mtime_secs, config from notetypes") { statement ->
            val id = statement.getLong(0)
            val config = ProtoMessage.parse(statement.getBlob(3))
            val preserved = otherJson(config).withDefaults(
                "latexPre" to JsonPrimitive(config.string(5)),
                "latexPost" to JsonPrimitive(config.string(6)),
                "latexsvg" to JsonPrimitive(config.bool(7)),
            )
            Notetype(
                id = id,
                name = statement.getText(1),
                kind = if (config.int(1) == 1) NotetypeKind.Cloze else NotetypeKind.Standard,
                fields = fieldsByNotetype[id].orEmpty().sortedBy { it.ordinal },
                templates = templatesByNotetype[id].orEmpty().sortedBy { it.ordinal },
                css = config.string(3),
                sortFieldIndex = config.int(2),
                modifiedSecs = statement.getLong(2),
                preserved = preserved,
            )
        }
    }

    fun decks(): List<Deck> = connection.rows("select id, name, mtime_secs, common, kind from decks") { statement ->
        val common = ProtoMessage.parse(statement.getBlob(3))
        val kind = ProtoMessage.parse(statement.getBlob(4))
        val normal = kind.message(1)
        val preserved = otherJson(common).withDefaults(
            "collapsed" to JsonPrimitive(common.bool(1)),
            "browserCollapsed" to JsonPrimitive(common.bool(2)),
            "extendNew" to JsonPrimitive(normal?.int(2) ?: 0),
            "extendRev" to JsonPrimitive(normal?.int(3) ?: 0),
        )
        Deck(
            id = statement.getLong(0),
            name = statement.getText(1).replace(SCHEMA18_DECK_SEPARATOR, DECK_NAME_SEPARATOR),
            description = normal?.string(4).orEmpty(),
            configId = normal?.long(1)?.takeIf { it != 0L } ?: 1,
            isFiltered = normal == null,
            modifiedSecs = statement.getLong(2),
            preserved = preserved,
        )
    }

    fun deckConfigs(): List<DeckConfig> = connection.rows("select id, name, mtime_secs, config from deck_config") { statement ->
        val config = ProtoMessage.parse(statement.getBlob(3))
        val defaults = DeckConfig(id = 0, name = "")
        val parameters = listOf(6, 5, 3).map { field -> config.floats(field) }.firstOrNull { it.isNotEmpty() }.orEmpty()
        val preserved = otherJson(config).withDefaults(
            "new" to JsonObject(
                mapOf(
                    "bury" to JsonPrimitive(config.bool(27)),
                    "initialFactor" to JsonPrimitive(((config.float(11) ?: 2.5f) * 1000).toInt()),
                    "ints" to JsonArray(listOf(config.int(18).takeIf { it > 0 } ?: 1, config.int(19).takeIf { it > 0 } ?: 4, 0).map { JsonPrimitive(it) }),
                    "order" to JsonPrimitive(1),
                ),
            ),
            "rev" to JsonObject(
                mapOf(
                    "bury" to JsonPrimitive(config.bool(28)),
                    "ease4" to JsonPrimitive((config.float(12) ?: 1.3f).toRoundedDouble()),
                    "ivlFct" to JsonPrimitive((config.float(15) ?: 1.0f).toRoundedDouble()),
                    "maxIvl" to JsonPrimitive(config.int(16).takeIf { it > 0 } ?: 36500),
                    "hardFactor" to JsonPrimitive((config.float(13) ?: 1.2f).toRoundedDouble()),
                ),
            ),
            "lapse" to JsonObject(
                mapOf(
                    "leechAction" to JsonPrimitive(config.int(21)),
                    "leechFails" to JsonPrimitive(config.int(22).takeIf { it > 0 } ?: 8),
                    "minInt" to JsonPrimitive(config.int(17).takeIf { it > 0 } ?: 1),
                    "mult" to JsonPrimitive((config.float(14) ?: 0f).toRoundedDouble()),
                ),
            ),
            "autoplay" to JsonPrimitive(!config.bool(23)),
            "maxTaken" to JsonPrimitive(config.int(24).takeIf { it > 0 } ?: 60),
            "timer" to JsonPrimitive(if (config.bool(25)) 1 else 0),
        )
        DeckConfig(
            id = statement.getLong(0),
            name = statement.getText(1),
            newCardsPerDay = config.int(9),
            reviewsPerDay = config.int(10),
            learningStepsMinutes = config.floats(1).map { it.toRoundedDouble() },
            relearningStepsMinutes = config.floats(2).map { it.toRoundedDouble() },
            desiredRetention = config.float(37)?.toRoundedDouble() ?: defaults.desiredRetention,
            fsrsParameters = parameters.map { it.toRoundedDouble() },
            modifiedSecs = statement.getLong(2),
            preserved = preserved,
        )
    }

    fun configuration(): JsonObject = JsonObject(
        connection.rows("select key, val from config") { statement ->
            statement.getText(0) to runCatching { Json.parseToJsonElement(statement.getBlob(1).decodeToString()) }.getOrDefault(JsonNull)
        }.toMap(),
    )

    private fun otherJson(message: ProtoMessage): JsonObject =
        message.bytes(OTHER_FIELD).takeIf { it.isNotEmpty() }
            ?.let { runCatching { Json.parseToJsonElement(it.decodeToString()) as? JsonObject }.getOrNull() }
            ?: JsonObject(emptyMap<String, JsonElement>())

    private companion object {
        const val OTHER_FIELD = 255
        const val SCHEMA18_DECK_SEPARATOR = "\u001f"
    }
}

private fun Float.toRoundedDouble(): Double = toString().toDouble()
