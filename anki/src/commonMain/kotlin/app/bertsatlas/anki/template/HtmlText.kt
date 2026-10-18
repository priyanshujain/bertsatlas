package app.bertsatlas.anki.template

internal object HtmlText {
    private val styleOrScript = Regex("(?is)<(style|script)\\b.*?</\\1\\s*>")
    private val comment = Regex("(?s)<!--.*?-->")
    private val tag = Regex("(?s)<[^>]*>")
    private val numericEntity = Regex("&#(x[0-9a-fA-F]+|[0-9]+);")
    private val namedEntities = mapOf(
        "&nbsp;" to " ",
        "&lt;" to "<",
        "&gt;" to ">",
        "&quot;" to "\"",
        "&apos;" to "'",
        "&#39;" to "'",
        "&amp;" to "&",
    )
    private val emptyFieldPattern = Regex("(?is)^(?:\\s|</?(?:br|div)\\s?/?>)*$")

    fun strip(html: String): String {
        val withoutTags = html
            .replace(styleOrScript, "")
            .replace(comment, "")
            .replace(tag, "")
        return decodeEntities(withoutTags).trim()
    }

    fun decodeEntities(text: String): String {
        var decoded = numericEntity.replace(text) { match ->
            val value = match.groupValues[1]
            val codePoint = if (value.startsWith("x")) value.substring(1).toIntOrNull(16) else value.toIntOrNull()
            codePoint?.let { codePointToString(it) } ?: match.value
        }
        namedEntities.forEach { (entity, replacement) -> decoded = decoded.replace(entity, replacement) }
        return decoded
    }

    fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    fun isEmptyField(html: String): Boolean = emptyFieldPattern.matches(html)

    private fun codePointToString(codePoint: Int): String =
        if (codePoint < 0x10000) {
            codePoint.toChar().toString()
        } else {
            val offset = codePoint - 0x10000
            charArrayOf((0xD800 + (offset shr 10)).toChar(), (0xDC00 + (offset and 0x3FF)).toChar()).concatToString()
        }
}
