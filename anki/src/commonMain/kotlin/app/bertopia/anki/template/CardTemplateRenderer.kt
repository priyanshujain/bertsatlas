package app.bertopia.anki.template

data class CardRenderContext(
    val fields: Map<String, String>,
    val clozeOrdinal: Int = 1,
    val tags: List<String> = emptyList(),
    val deckName: String = "",
    val notetypeName: String = "",
    val cardTemplateName: String = "",
)

data class RenderedCard(val question: String, val answer: String)

object CardTemplateRenderer {
    const val FRONT_SIDE = "FrontSide"

    fun render(questionTemplate: String, answerTemplate: String, context: CardRenderContext): RenderedCard {
        val question = renderQuestion(questionTemplate, context)
        return RenderedCard(question, renderAnswer(answerTemplate, context, question))
    }

    fun renderQuestion(template: String, context: CardRenderContext): String =
        Rendering(context, Side.Question, frontSide = "").render(TemplateParser.parse(template))

    fun renderAnswer(template: String, context: CardRenderContext, renderedQuestion: String): String =
        Rendering(context, Side.Answer, frontSide = renderedQuestion).render(TemplateParser.parse(template))

    fun referencedFieldNames(template: String): Set<String> {
        val names = mutableSetOf<String>()
        fun collect(nodes: List<TemplateNode>): Unit = nodes.forEach { node ->
            when (node) {
                is TemplateNode.Text -> Unit
                is TemplateNode.Replacement -> names += node.fieldName
                is TemplateNode.Conditional -> { names += node.fieldName; collect(node.children) }
                is TemplateNode.NegatedConditional -> { names += node.fieldName; collect(node.children) }
            }
        }
        collect(TemplateParser.parse(template))
        return names
    }

    private enum class Side { Question, Answer }

    private class Rendering(context: CardRenderContext, private val side: Side, frontSide: String) {
        private val clozeOrdinal = context.clozeOrdinal
        private val values: Map<String, String> = context.fields + mapOf(
            FRONT_SIDE to frontSide,
            "Tags" to context.tags.joinToString(" "),
            "Type" to context.notetypeName,
            "Deck" to context.deckName,
            "Subdeck" to context.deckName.substringAfterLast("::"),
            "Card" to context.cardTemplateName,
        )

        fun render(nodes: List<TemplateNode>): String = buildString {
            nodes.forEach { node ->
                when (node) {
                    is TemplateNode.Text -> append(node.text)
                    is TemplateNode.Replacement -> append(renderReplacement(node))
                    is TemplateNode.Conditional -> if (isNonEmpty(node.fieldName)) append(render(node.children))
                    is TemplateNode.NegatedConditional -> if (!isNonEmpty(node.fieldName)) append(render(node.children))
                }
            }
        }

        private fun isNonEmpty(fieldName: String): Boolean =
            values[fieldName]?.let { !HtmlText.isEmptyField(it) } ?: false

        private fun renderReplacement(node: TemplateNode.Replacement): String {
            val typeFilterIndex = node.filters.lastIndexOf(TYPE_FILTER)
            if (typeFilterIndex >= 0) return typeAnswerMarker(node, typeFilterIndex)
            if (node.fieldName == FRONT_SIDE && side == Side.Question) return ""
            val fieldValue = values[node.fieldName] ?: return "{unknown field ${node.fieldName}}"
            return node.filters.fold(fieldValue) { text, filter -> applyFilter(filter, text, node.fieldName) }
        }

        private fun typeAnswerMarker(node: TemplateNode.Replacement, typeFilterIndex: Int): String {
            val innerFilters = node.filters.subList(0, typeFilterIndex).reversed()
            return (listOf(TYPE_FILTER) + innerFilters + node.fieldName).joinToString(":", prefix = "[[", postfix = "]]")
        }

        private fun applyFilter(filter: String, text: String, fieldName: String): String = when (filter) {
            "text" -> HtmlText.strip(text)
            "cloze" -> if (side == Side.Question) Cloze.renderQuestion(text, clozeOrdinal) else Cloze.renderAnswer(text, clozeOrdinal)
            "cloze-only" -> Cloze.activeAnswers(text, clozeOrdinal).joinToString(", ")
            "hint" -> hint(text, fieldName)
            else -> text
        }

        private fun hint(text: String, fieldName: String): String {
            if (HtmlText.isEmptyField(text)) return ""
            return "<a class=\"hint\" data-field=\"${HtmlText.escape(fieldName)}\">$fieldName</a>" +
                "<div class=\"hint\" style=\"display: none\">$text</div>"
        }
    }

    private const val TYPE_FILTER = "type"
}

fun CardTemplateRenderer.clozeFieldNames(template: String): Set<String> {
    val names = mutableSetOf<String>()
    fun collect(nodes: List<TemplateNode>): Unit = nodes.forEach { node ->
        when (node) {
            is TemplateNode.Text -> Unit
            is TemplateNode.Replacement -> if ("cloze" in node.filters) names += node.fieldName
            is TemplateNode.Conditional -> collect(node.children)
            is TemplateNode.NegatedConditional -> collect(node.children)
        }
    }
    collect(TemplateParser.parse(template))
    return names
}
