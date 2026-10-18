package app.bertsatlas.anki.model

import kotlinx.serialization.json.JsonObject

enum class NotetypeKind { Standard, Cloze }

data class NotetypeField(
    val name: String,
    val ordinal: Int,
    val preserved: JsonObject = JsonObject(emptyMap()),
)

data class CardTemplate(
    val name: String,
    val ordinal: Int,
    val questionFormat: String,
    val answerFormat: String,
    val preserved: JsonObject = JsonObject(emptyMap()),
)

data class Notetype(
    val id: Long,
    val name: String,
    val kind: NotetypeKind,
    val fields: List<NotetypeField>,
    val templates: List<CardTemplate>,
    val css: String,
    val sortFieldIndex: Int = 0,
    val modifiedSecs: Long = 0,
    val preserved: JsonObject = JsonObject(emptyMap()),
) {
    val fieldNames: List<String> get() = fields.map { it.name }
}
