package app.bertopia.anki.model

data class Note(
    val id: Long,
    val guid: String,
    val notetypeId: Long,
    val fields: List<String>,
    val tags: List<String>,
    val modifiedSecs: Long = 0,
    val usn: Int = -1,
    val flags: Int = 0,
    val data: String = "",
)
