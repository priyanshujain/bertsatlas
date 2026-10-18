package app.bertopia.cardrender

enum class TypedSegmentKind { Correct, Wrong, Missing }

data class TypedSegment(val text: String, val kind: TypedSegmentKind)

data class TypedAnswerResult(
    val typedSegments: List<TypedSegment>,
    val expected: String,
    val isExactMatch: Boolean,
)

object TypedAnswerComparison {
    fun compare(typed: String, expected: String): TypedAnswerResult {
        val typedText = normalize(typed)
        val expectedText = normalize(expected)
        if (typedText.equals(expectedText, ignoreCase = true)) {
            return TypedAnswerResult(listOf(TypedSegment(typedText, TypedSegmentKind.Correct)).filter { it.text.isNotEmpty() }, expectedText, isExactMatch = true)
        }
        return TypedAnswerResult(diff(typedText, expectedText), expectedText, isExactMatch = false)
    }

    private fun normalize(text: String): String =
        HtmlEntities.decode(text).replace('\u00A0', ' ').trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")

    private fun diff(typed: String, expected: String): List<TypedSegment> {
        val rows = typed.length + 1
        val columns = expected.length + 1
        val lengths = Array(rows) { IntArray(columns) }
        for (i in typed.indices.reversed()) {
            for (j in expected.indices.reversed()) {
                lengths[i][j] = if (typed[i].equals(expected[j], ignoreCase = true)) lengths[i + 1][j + 1] + 1 else maxOf(lengths[i + 1][j], lengths[i][j + 1])
            }
        }
        val segments = mutableListOf<TypedSegment>()
        fun add(character: Char, kind: TypedSegmentKind) {
            val last = segments.lastOrNull()
            if (last != null && last.kind == kind) segments[segments.lastIndex] = TypedSegment(last.text + character, kind)
            else segments += TypedSegment(character.toString(), kind)
        }
        var i = 0
        var j = 0
        while (i < typed.length && j < expected.length) {
            when {
                typed[i].equals(expected[j], ignoreCase = true) -> {
                    add(typed[i], TypedSegmentKind.Correct)
                    i++
                    j++
                }
                lengths[i + 1][j] >= lengths[i][j + 1] -> {
                    add(typed[i], TypedSegmentKind.Wrong)
                    i++
                }
                else -> {
                    add('-', TypedSegmentKind.Missing)
                    j++
                }
            }
        }
        while (i < typed.length) add(typed[i++], TypedSegmentKind.Wrong)
        while (j < expected.length) {
            add('-', TypedSegmentKind.Missing)
            j++
        }
        return segments
    }
}
