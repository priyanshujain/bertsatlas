package app.bertopia.anki.template

class TemplateException(message: String) : Exception(message)

internal sealed interface TemplateNode {
    data class Text(val text: String) : TemplateNode
    data class Replacement(val fieldName: String, val filters: List<String>) : TemplateNode
    data class Conditional(val fieldName: String, val children: List<TemplateNode>) : TemplateNode
    data class NegatedConditional(val fieldName: String, val children: List<TemplateNode>) : TemplateNode
}

internal object TemplateParser {
    private const val OPEN = "{{"
    private const val CLOSE = "}}"

    fun parse(template: String): List<TemplateNode> {
        val root = mutableListOf<TemplateNode>()
        val openSections = ArrayDeque<OpenSection>()
        var current = root
        var position = 0

        while (position < template.length) {
            val tagStart = template.indexOf(OPEN, position)
            if (tagStart < 0) {
                current += TemplateNode.Text(template.substring(position))
                break
            }
            val tagEnd = template.indexOf(CLOSE, tagStart + OPEN.length)
            if (tagEnd < 0) {
                current += TemplateNode.Text(template.substring(position))
                break
            }
            if (tagStart > position) current += TemplateNode.Text(template.substring(position, tagStart))
            val tag = template.substring(tagStart + OPEN.length, tagEnd).trim()
            position = tagEnd + CLOSE.length

            when {
                tag.startsWith("#") || tag.startsWith("^") -> {
                    val section = OpenSection(tag.substring(1).trim(), negated = tag.startsWith("^"), parent = current)
                    openSections.addLast(section)
                    current = section.children
                }
                tag.startsWith("/") -> {
                    val fieldName = tag.substring(1).trim()
                    val section = openSections.removeLastOrNull()
                        ?: throw TemplateException("Found {{/$fieldName}} without a matching opening section.")
                    if (section.fieldName != fieldName) {
                        throw TemplateException("Found {{/$fieldName}} but expected {{/${section.fieldName}}}.")
                    }
                    current = section.parent
                    current += section.toNode()
                }
                else -> current += parseReplacement(tag)
            }
        }

        openSections.lastOrNull()?.let {
            throw TemplateException("Missing {{/${it.fieldName}}} for section {{${if (it.negated) "^" else "#"}${it.fieldName}}}.")
        }
        return root
    }

    private fun parseReplacement(tag: String): TemplateNode.Replacement {
        val parts = tag.split(":")
        return TemplateNode.Replacement(
            fieldName = parts.last().trim(),
            filters = parts.dropLast(1).map { it.trim() }.reversed(),
        )
    }

    private class OpenSection(
        val fieldName: String,
        val negated: Boolean,
        val parent: MutableList<TemplateNode>,
    ) {
        val children = mutableListOf<TemplateNode>()

        fun toNode(): TemplateNode =
            if (negated) TemplateNode.NegatedConditional(fieldName, children)
            else TemplateNode.Conditional(fieldName, children)
    }
}
