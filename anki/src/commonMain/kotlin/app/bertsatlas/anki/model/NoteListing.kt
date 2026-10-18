package app.bertsatlas.anki.model

data class NoteListing(
    val noteId: Long,
    val guid: String,
    val notetypeId: Long,
    val modifiedSecs: Long,
    val tags: List<String>,
    val sortField: String,
    val fields: List<String>,
    val deckId: Long,
    val cardCount: Int,
)
