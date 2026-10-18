package app.bertopia.domain.pack

enum class EditorialStatus(val tagValue: String) {
    Draft("draft"),
    FactChecked("factchecked"),
    CopyChecked("copychecked"),
    Approved("approved"),
}

enum class LevelSetting(val tagValue: String?) {
    Mid("mid"),
    Senior("senior"),
    All(null);

    fun admits(tags: Collection<String>): Boolean {
        if (this == All) return true
        val levels = BertTags.levels(tags)
        return levels.isEmpty() || this in levels
    }
}

object BertTags {
    const val LEVEL_PREFIX = "bert::level::"
    const val SIMILAR_PREFIX = "bert::similar::"
    const val STATUS_PREFIX = "bert::status::"
    const val RETIRED = "bert::retired"
    private const val BERT_PREFIX = "bert::"
    private val groupIdPattern = Regex("^[A-Za-z0-9][A-Za-z0-9_.-]*$")

    fun similarTag(groupId: String): String = SIMILAR_PREFIX + groupId

    fun statusTag(status: EditorialStatus): String = STATUS_PREFIX + status.tagValue

    fun similarGroupIds(tags: Collection<String>): List<String> =
        tags.filter { it.startsWith(SIMILAR_PREFIX) }.map { it.removePrefix(SIMILAR_PREFIX) }.filter { it.isNotEmpty() }

    fun editorialStatus(tags: Collection<String>): EditorialStatus? = tags
        .firstOrNull { it.startsWith(STATUS_PREFIX) }
        ?.let { tag -> EditorialStatus.entries.firstOrNull { it.tagValue == tag.removePrefix(STATUS_PREFIX) } }

    fun levelTag(level: LevelSetting): String? = level.tagValue?.let { LEVEL_PREFIX + it }

    fun levels(tags: Collection<String>): Set<LevelSetting> = tags
        .filter { it.startsWith(LEVEL_PREFIX) }
        .mapNotNullTo(linkedSetOf()) { tag -> taggedLevel(tag.removePrefix(LEVEL_PREFIX)) }

    private fun taggedLevel(value: String): LevelSetting? =
        LevelSetting.entries.firstOrNull { it.tagValue != null && it.tagValue == value }

    fun isWellFormed(tag: String): Boolean = tag.isNotEmpty() && tag.none { it.isWhitespace() }

    fun bertTagProblem(tag: String): String? = when {
        !tag.startsWith(BERT_PREFIX) -> null
        tag == RETIRED -> null
        tag.startsWith(SIMILAR_PREFIX) ->
            if (groupIdPattern.matches(tag.removePrefix(SIMILAR_PREFIX))) null else "similar group id is malformed"
        tag.startsWith(STATUS_PREFIX) ->
            if (EditorialStatus.entries.any { it.tagValue == tag.removePrefix(STATUS_PREFIX) }) null else "unknown editorial status"
        tag.startsWith(LEVEL_PREFIX) ->
            if (taggedLevel(tag.removePrefix(LEVEL_PREFIX)) != null) null else "level must be mid or senior"
        else -> "unknown bert tag namespace"
    }

    fun parseAnkiTags(tags: String): List<String> = tags.split(' ').filter { it.isNotEmpty() }
}
