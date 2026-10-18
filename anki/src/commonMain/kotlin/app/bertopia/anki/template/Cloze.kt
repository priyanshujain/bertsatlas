package app.bertopia.anki.template

object Cloze {
    private val openMarker = Regex("\\{\\{c(\\d+)::")
    private const val CLOSE_MARKER = "}}"
    private const val HINT_SEPARATOR = "::"
    private const val HIDDEN_TEXT = "[...]"

    fun ordinals(text: String): Set<Int> = mutableSetOf<Int>().also { collectOrdinals(parse(text), it) }

    fun renderQuestion(text: String, activeOrdinal: Int): String = render(text, activeOrdinal, Side.Question)

    fun renderAnswer(text: String, activeOrdinal: Int): String = render(text, activeOrdinal, Side.Answer)

    fun activeAnswers(text: String, activeOrdinal: Int): List<String> =
        mutableListOf<String>().also { collectActive(parse(text), activeOrdinal, it) }

    private fun render(text: String, activeOrdinal: Int, side: Side): String {
        if (activeOrdinal !in ordinals(text)) return ""
        return renderNodes(parse(text), activeOrdinal, side)
    }

    private fun renderNodes(nodes: List<ClozeNode>, activeOrdinal: Int, side: Side): String = buildString {
        nodes.forEach { node ->
            when (node) {
                is ClozeNode.Text -> append(node.text)
                is ClozeNode.Deletion -> append(renderDeletion(node, activeOrdinal, side))
            }
        }
    }

    private fun renderDeletion(deletion: ClozeNode.Deletion, activeOrdinal: Int, side: Side): String {
        val content = renderNodes(deletion.children, activeOrdinal, side)
        return when {
            deletion.ordinal != activeOrdinal ->
                "<span class=\"cloze-inactive\" data-ordinal=\"${deletion.ordinal}\">$content</span>"
            side == Side.Question -> {
                val placeholder = deletion.hint?.let { "[$it]" } ?: HIDDEN_TEXT
                val revealed = HtmlText.escape(renderNodes(deletion.children, activeOrdinal, Side.Answer))
                "<span class=\"cloze\" data-cloze=\"$revealed\" data-ordinal=\"${deletion.ordinal}\">$placeholder</span>"
            }
            else -> "<span class=\"cloze\" data-ordinal=\"${deletion.ordinal}\">$content</span>"
        }
    }

    private fun plainText(nodes: List<ClozeNode>): String = buildString {
        nodes.forEach { node ->
            when (node) {
                is ClozeNode.Text -> append(node.text)
                is ClozeNode.Deletion -> append(plainText(node.children))
            }
        }
    }

    private fun collectOrdinals(nodes: List<ClozeNode>, found: MutableSet<Int>) {
        nodes.filterIsInstance<ClozeNode.Deletion>().forEach {
            found += it.ordinal
            collectOrdinals(it.children, found)
        }
    }

    private fun collectActive(nodes: List<ClozeNode>, activeOrdinal: Int, answers: MutableList<String>) {
        nodes.filterIsInstance<ClozeNode.Deletion>().forEach {
            if (it.ordinal == activeOrdinal) answers += plainText(it.children)
            else collectActive(it.children, activeOrdinal, answers)
        }
    }

    private fun parse(text: String): List<ClozeNode> {
        val root = mutableListOf<ClozeNode>()
        val open = ArrayDeque<OpenDeletion>()
        var position = 0

        fun currentNodes(): MutableList<ClozeNode> = open.lastOrNull()?.children ?: root

        while (position < text.length) {
            val nextOpen = openMarker.find(text, position)
            val nextClose = if (open.isEmpty()) -1 else text.indexOf(CLOSE_MARKER, position)
            val openStart = nextOpen?.range?.first ?: -1

            if (openStart >= 0 && (nextClose < 0 || openStart < nextClose)) {
                if (openStart > position) currentNodes() += ClozeNode.Text(text.substring(position, openStart))
                open.addLast(OpenDeletion(nextOpen!!.groupValues[1].toInt(), nextOpen.value))
                position = nextOpen.range.last + 1
            } else if (nextClose >= 0) {
                if (nextClose > position) currentNodes() += ClozeNode.Text(text.substring(position, nextClose))
                val finished = open.removeLast()
                currentNodes() += finished.toDeletion()
                position = nextClose + CLOSE_MARKER.length
            } else {
                currentNodes() += ClozeNode.Text(text.substring(position))
                position = text.length
            }
        }

        while (open.isNotEmpty()) {
            val unclosed = open.removeLast()
            val parentNodes = currentNodes()
            parentNodes += ClozeNode.Text(unclosed.openMarkerText)
            parentNodes += unclosed.children
        }
        return root
    }

    private enum class Side { Question, Answer }

    private sealed interface ClozeNode {
        data class Text(val text: String) : ClozeNode
        data class Deletion(val ordinal: Int, val children: List<ClozeNode>, val hint: String?) : ClozeNode
    }

    private class OpenDeletion(val ordinal: Int, val openMarkerText: String) {
        val children = mutableListOf<ClozeNode>()

        fun toDeletion(): ClozeNode.Deletion {
            val last = children.lastOrNull()
            if (last is ClozeNode.Text && last.text.contains(HINT_SEPARATOR)) {
                val answerPart = last.text.substringBefore(HINT_SEPARATOR)
                val hint = last.text.substringAfter(HINT_SEPARATOR)
                val withoutHint = children.dropLast(1) + listOfNotNull(answerPart.takeIf { it.isNotEmpty() }?.let { ClozeNode.Text(it) })
                return ClozeNode.Deletion(ordinal, withoutHint, hint)
            }
            return ClozeNode.Deletion(ordinal, children.toList(), null)
        }
    }
}
