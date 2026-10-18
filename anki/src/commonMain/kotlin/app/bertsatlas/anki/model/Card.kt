package app.bertsatlas.anki.model

enum class CardType(val code: Int) { New(0), Learning(1), Review(2), Relearning(3) }

data class CardScheduling(
    val type: Int = 0,
    val queue: Int = 0,
    val due: Long = 0,
    val interval: Int = 0,
    val easeFactor: Int = 0,
    val reps: Int = 0,
    val lapses: Int = 0,
    val left: Int = 0,
    val originalDue: Long = 0,
    val originalDeckId: Long = 0,
    val data: String = "",
)

data class Card(
    val id: Long,
    val noteId: Long,
    val deckId: Long,
    val ordinal: Int,
    val scheduling: CardScheduling = CardScheduling(),
    val modifiedSecs: Long = 0,
    val usn: Int = -1,
    val flags: Int = 0,
)
