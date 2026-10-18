package app.bertsatlas.anki.model

import kotlinx.serialization.json.JsonObject

const val DECK_NAME_SEPARATOR = "::"

data class Deck(
    val id: Long,
    val name: String,
    val description: String = "",
    val configId: Long = 1,
    val isFiltered: Boolean = false,
    val modifiedSecs: Long = 0,
    val preserved: JsonObject = JsonObject(emptyMap()),
) {
    val nameParts: List<String> get() = name.split(DECK_NAME_SEPARATOR)
    val isTopLevel: Boolean get() = nameParts.size == 1
    val topLevelName: String get() = nameParts.first()
}
