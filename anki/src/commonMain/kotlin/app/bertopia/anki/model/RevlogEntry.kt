package app.bertopia.anki.model

enum class ReviewKind(val code: Int) { Learning(0), Review(1), Relearning(2), Filtered(3), Manual(4) }

data class RevlogEntry(
    val id: Long,
    val cardId: Long,
    val ease: Int,
    val interval: Int,
    val lastInterval: Int,
    val easeFactor: Int,
    val timeMs: Int,
    val kind: Int,
    val usn: Int = -1,
)
