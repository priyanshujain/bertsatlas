package app.bertopia.domain.quiz

object ChoiceLabels {
    fun key(label: String): String = label.lowercase().filterNot { it.isWhitespace() }

    fun same(first: String, second: String): Boolean = key(first) == key(second)
}
