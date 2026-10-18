package app.bertopia.cardrender

data class CardDocument(val blocks: List<CardBlock>) {
    val isEmpty: Boolean get() = blocks.isEmpty()
}

sealed interface CardBlock {
    data class Paragraph(val inlines: List<CardInline>, val role: ParagraphRole = ParagraphRole.Body) : CardBlock
    data class ListBlock(val ordered: Boolean, val items: List<List<CardBlock>>) : CardBlock
    data class Table(val rows: List<List<TableCell>>) : CardBlock
    data class Quote(val blocks: List<CardBlock>) : CardBlock
    data object Rule : CardBlock
    data class Picture(val source: String, val description: String?) : CardBlock
    data class CodeBlock(val language: String?, val code: String) : CardBlock
    data class DisplayMath(val tex: String) : CardBlock
    data class Occlusion(val imageSource: String, val shapes: List<OcclusionShape>) : CardBlock
    data class TypeAnswer(val fieldName: String, val isCloze: Boolean) : CardBlock
    data class Hint(val label: String, val blocks: List<CardBlock>) : CardBlock
}

enum class ParagraphRole { Body, Heading, Muted }

data class TableCell(val inlines: List<CardInline>, val isHeader: Boolean)

sealed interface CardInline {
    data class Text(val text: String, val style: InlineStyle = InlineStyle.Plain) : CardInline
    data object LineBreak : CardInline
    data class Sound(val file: String) : CardInline
    data class Math(val tex: String) : CardInline
}

enum class Baseline { Normal, Subscript, Superscript }

enum class ClozeRole { Hidden, Revealed, Inactive }

data class InlineStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val code: Boolean = false,
    val colorArgb: Long? = null,
    val highlightArgb: Long? = null,
    val sizeScale: Float? = null,
    val baseline: Baseline = Baseline.Normal,
    val cloze: ClozeRole? = null,
) {
    companion object {
        val Plain = InlineStyle()
    }
}
