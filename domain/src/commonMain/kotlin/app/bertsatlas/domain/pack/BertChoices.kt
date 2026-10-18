package app.bertsatlas.domain.pack

import app.bertsatlas.anki.template.CardRenderContext
import app.bertsatlas.anki.template.CardTemplateRenderer

object BertChoices {
    const val MIN_CHOICES = 2
    const val MAX_CHOICES = 5
    private val lineSeparator = Regex("(?i)\\n|<br\\s*/?>|</div>|</p>")

    fun choices(field: String): List<String> = field.split(lineSeparator).map(::text).filter { it.isNotEmpty() }

    fun text(html: String): String =
        CardTemplateRenderer.renderQuestion("{{text:Value}}", CardRenderContext(mapOf("Value" to html))).trim()

    fun problems(choicesField: String, correctField: String): List<String> = buildList {
        val choices = choices(choicesField)
        if (choices.size !in MIN_CHOICES..MAX_CHOICES) add("Choices has ${choices.size} lines, needs $MIN_CHOICES to $MAX_CHOICES")
        if (choices.size != choices.distinct().size) add("Choices has duplicate lines")
        val correct = text(correctField)
        if (choices.count { it == correct } != 1) add("Correct must match exactly one line of Choices")
    }
}
