package app.bertopia.domain.pack

object PackIds {
    private const val MAX_LENGTH = 48
    private val latinFolds = mapOf(
        "àáâãäåāăą" to "a", "çćĉċč" to "c", "ďđ" to "d", "èéêëēĕėęě" to "e", "ĝğġģ" to "g", "ĥħ" to "h",
        "ìíîïĩīĭįı" to "i", "ĵ" to "j", "ķ" to "k", "ĺļľŀł" to "l", "ñńņňŉ" to "n", "òóôõöøōŏő" to "o",
        "ŕŗř" to "r", "śŝşšș" to "s", "ţťŧț" to "t", "ùúûüũūŭůűų" to "u", "ŵ" to "w", "ýÿŷ" to "y", "źżž" to "z",
    ).flatMap { (letters, ascii) -> letters.map { it to ascii } }.toMap()
    private val ligatures = mapOf('ß' to "ss", 'æ' to "ae", 'œ' to "oe", 'þ' to "th", 'ð' to "d")

    private val validPattern = Regex("^[a-z0-9][a-z0-9-]*$")

    fun isValid(packId: String): Boolean = validPattern.matches(packId)

    fun slugFor(title: String): String {
        val folded = title.lowercase().map { character ->
            when {
                character in 'a'..'z' || character in '0'..'9' -> character.toString()
                else -> latinFolds[character] ?: ligatures[character] ?: "-"
            }
        }.joinToString("")
        val slug = folded.split('-').filter { it.isNotEmpty() }.joinToString("-").take(MAX_LENGTH).trimEnd('-')
        return slug.ifEmpty { "pack-${shortHash(title)}" }
    }

    fun uniqueId(title: String, isTaken: (String) -> Boolean): String {
        val base = slugFor(title)
        return generateSequence(1) { it + 1 }.map { if (it == 1) base else "$base-$it" }.first { !isTaken(it) }
    }

    private fun shortHash(text: String): String {
        var hash = 0x811c9dc5u
        text.encodeToByteArray().forEach { byte ->
            hash = hash xor byte.toUByte().toUInt()
            hash *= 0x01000193u
        }
        return hash.toString(16).padStart(8, '0')
    }
}
