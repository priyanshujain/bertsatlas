package app.bertopia.anki.model

data class ReviewRecord(
    val card: Card,
    val revlog: RevlogEntry,
    val today: Int,
    val countsAsNewCard: Boolean,
    val countsAsReview: Boolean,
    val leeched: Boolean,
)

data class StudiedToday(
    val newCards: Int,
    val reviews: Int,
)
