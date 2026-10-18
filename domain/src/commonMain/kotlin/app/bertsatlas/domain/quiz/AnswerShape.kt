package app.bertsatlas.domain.quiz

enum class AnswerKind { Number, Date, Word, Phrase }

enum class AnswerScript { Latin, Greek, Cyrillic, Arabic, Hebrew, Devanagari, Han, Kana, Hangul, Thai, None, Other }

data class AnswerShape(
    val kind: AnswerKind,
    val script: AnswerScript,
    val lengthBand: Int,
) {
    fun sameKindAndScript(other: AnswerShape): Boolean = kind == other.kind && script == other.script
}

object AnswerShapes {
    private val number = Regex("^[-+]?[0-9][0-9,. ]*%?$")
    private val numericDate = Regex("^[0-9]{1,4}[-/.][0-9]{1,2}([-/.][0-9]{1,4})?$")
    private val monthName = Regex(
        "(?i)\\b(jan(uary)?|feb(ruary)?|mar(ch)?|apr(il)?|may|june?|july?|aug(ust)?|sep(t(ember)?)?|oct(ober)?|nov(ember)?|dec(ember)?)\\b",
    )
    private val wordSeparators = Regex("\\s+")
    private val numberWordSeparators = Regex("[\\s-]+")
    private val numberWords = setOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
        "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety",
        "hundred", "thousand", "million", "billion", "trillion",
        "first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth", "tenth",
        "eleventh", "twelfth", "thirteenth", "fourteenth", "fifteenth", "sixteenth", "seventeenth", "eighteenth", "nineteenth",
        "twentieth", "thirtieth", "fortieth", "fiftieth", "sixtieth", "seventieth", "eightieth", "ninetieth",
        "hundredth", "thousandth", "millionth",
    )
    private val numberJoiners = setOf("and", "a")

    fun of(answer: String): AnswerShape {
        val text = answer.trim()
        val kind = when {
            numericDate.matches(text) -> AnswerKind.Date
            number.matches(text) -> AnswerKind.Number
            isSpelledNumber(text) -> AnswerKind.Number
            monthName.containsMatchIn(text) && text.any { it.isDigit() } -> AnswerKind.Date
            text.split(wordSeparators).size == 1 -> AnswerKind.Word
            else -> AnswerKind.Phrase
        }
        return AnswerShape(kind, scriptOf(text), lengthBand(text.length))
    }

    private fun isSpelledNumber(text: String): Boolean {
        val words = text.lowercase().trimEnd('.').split(numberWordSeparators).filter { it.isNotEmpty() }
        return words.any { it in numberWords } && words.all { it in numberWords || it in numberJoiners }
    }

    fun scriptOf(text: String): AnswerScript {
        val scripts = text.filter { it.isLetter() }.map { scriptOfLetter(it) }
        if (scripts.isEmpty()) return AnswerScript.None
        return scripts.groupingBy { it }.eachCount().maxBy { it.value }.key
    }

    fun isSingleScript(text: String): Boolean =
        text.filter { it.isLetter() }.map { scriptOfLetter(it) }.distinct().size == 1

    private fun lengthBand(length: Int): Int = when {
        length <= 4 -> 0
        length <= 9 -> 1
        length <= 16 -> 2
        length <= 28 -> 3
        else -> 4
    }

    private fun scriptOfLetter(letter: Char): AnswerScript {
        val code = letter.code
        return when {
            code < 0x0250 || code in 0x1E00..0x1EFF -> AnswerScript.Latin
            code in 0x0370..0x03FF || code in 0x1F00..0x1FFF -> AnswerScript.Greek
            code in 0x0400..0x052F -> AnswerScript.Cyrillic
            code in 0x0590..0x05FF -> AnswerScript.Hebrew
            code in 0x0600..0x06FF || code in 0x0750..0x077F -> AnswerScript.Arabic
            code in 0x0900..0x097F -> AnswerScript.Devanagari
            code in 0x0E00..0x0E7F -> AnswerScript.Thai
            code in 0x3040..0x30FF -> AnswerScript.Kana
            code in 0x4E00..0x9FFF || code in 0x3400..0x4DBF -> AnswerScript.Han
            code in 0xAC00..0xD7AF || code in 0x1100..0x11FF -> AnswerScript.Hangul
            else -> AnswerScript.Other
        }
    }
}
