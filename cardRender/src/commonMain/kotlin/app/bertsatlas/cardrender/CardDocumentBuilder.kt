package app.bertsatlas.cardrender

object CardDocumentBuilder {
    private val paragraphTags = setOf(
        "p", "div", "section", "article", "center", "header", "footer", "main", "aside", "nav",
        "figure", "figcaption", "details", "summary", "address", "dl", "dt", "dd", "form", "fieldset", "legend", "tbody", "thead",
    )
    private val headingScales = mapOf("h1" to 1.5f, "h2" to 1.32f, "h3" to 1.18f, "h4" to 1.08f, "h5" to 1f, "h6" to 0.92f)
    private val ignoredTags = setOf("input", "button", "select", "option", "textarea", "iframe", "video", "object", "embed", "svg", "canvas", "map", "rp", "noscript")
    private val sideBySideTags = setOf("span", "strong", "b", "em", "i", "small", "label", "abbr", "mark")
    private val mutedClasses = setOf("prompt", "hint", "extra", "fact", "source")
    private val preLineClasses = setOf("list")
    private val fontSizeScales = listOf(0.63f, 0.82f, 1f, 1.13f, 1.5f, 2f, 3f)
    private val markerPattern = Regex(
        "\\[sound:([^\\]]+)]" +
            "|\\[\\[type:([^\\]]+)]]" +
            "|\\\\\\((.+?)\\\\\\)" +
            "|\\\\\\[(.+?)\\\\]" +
            "|\\[\\$\\$](.+?)\\[/\\$\\$]" +
            "|\\[\\$](.+?)\\[/\\$]" +
            "|\\[latex](.+?)\\[/latex]",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )

    fun build(html: String): CardDocument {
        val build = BuildState()
        val emitter = Emitter()
        visitAll(HtmlParser.parse(html), Context(), emitter, build)
        val blocks = emitter.finish()
        return CardDocument(attachOcclusion(blocks, build.occlusionShapes))
    }

    private data class Context(
        val style: InlineStyle = InlineStyle.Plain,
        val whitespace: Whitespace = Whitespace.Collapse,
    )

    private enum class Whitespace { Collapse, PreLine, Preserve }

    private class BuildState {
        val occlusionShapes = mutableListOf<OcclusionShape>()
        var pendingHintLabel: String? = null
    }

    private class Emitter {
        val blocks = mutableListOf<CardBlock>()
        private val inlines = mutableListOf<CardInline>()
        var role: ParagraphRole = ParagraphRole.Body

        fun inline(inline: CardInline) {
            inlines += inline
        }

        fun block(block: CardBlock) {
            flush()
            blocks += block
        }

        fun flush() {
            val normalized = normalizeInlines(inlines)
            inlines.clear()
            if (normalized.isNotEmpty()) blocks += CardBlock.Paragraph(normalized, role)
        }

        fun finish(): List<CardBlock> {
            flush()
            return blocks.toList()
        }
    }

    private fun visitAll(nodes: List<HtmlNode>, context: Context, emitter: Emitter, build: BuildState) {
        nodes.forEach { visit(it, context, emitter, build) }
    }

    private fun visit(node: HtmlNode, context: Context, emitter: Emitter, build: BuildState) {
        when (node) {
            is HtmlNode.Text -> appendText(node.text, context, emitter)
            is HtmlNode.Element -> visitElement(node, context, emitter, build)
        }
    }

    private fun visitElement(element: HtmlNode.Element, context: Context, emitter: Emitter, build: BuildState) {
        val tag = element.tag
        val inlineStyle = element.inlineStyle
        if (inlineStyle["display"]?.lowercase() == "none" || element.attributes.containsKey("hidden")) {
            visitHidden(element, context, emitter, build)
            return
        }
        val styled = context.copy(
            style = applyElementStyle(element, context.style),
            whitespace = whitespaceFor(element, context.whitespace),
        )
        when {
            tag == "br" -> emitter.inline(CardInline.LineBreak)
            tag == "hr" -> emitter.block(CardBlock.Rule)
            tag == "img" -> element.attribute("src")?.takeIf { it.isNotBlank() }?.let {
                emitter.block(CardBlock.Picture(it.trim(), element.attribute("alt")?.takeIf { alt -> alt.isNotBlank() }))
            }
            tag == "audio" || tag == "source" -> element.attribute("src")?.takeIf { it.isNotBlank() }?.let { emitter.inline(CardInline.Sound(it)) }
                ?: visitAll(element.children, styled, emitter, build)
            tag in ignoredTags -> Unit
            tag == "pre" -> emitter.block(codeBlock(element))
            tag == "code" || tag == "kbd" || tag == "samp" || tag == "tt" ->
                emitter.inline(CardInline.Text(textContent(element), styled.style.copy(code = true)))
            tag == "ul" || tag == "ol" -> emitter.block(listBlock(element, styled, build))
            tag == "table" -> emitter.block(tableBlock(element, styled, build))
            tag == "blockquote" -> emitter.block(CardBlock.Quote(containerBlocks(element.children, styled, build)))
            tag == "a" && "hint" in element.classes -> build.pendingHintLabel = textContent(element).trim().ifBlank { null }
            tag == "span" && clozeRole(element) != null -> visitCloze(element, styled, emitter, build)
            tag == "rt" -> emitter.inline(CardInline.Text(" (${textContent(element).trim()})", styled.style.copy(sizeScale = 0.75f)))
            tag in headingScales || tag in paragraphTags || tag == "li" -> visitParagraphBlock(element, styled, emitter, build)
            else -> visitAll(element.children, styled, emitter, build)
        }
    }

    private fun visitParagraphBlock(element: HtmlNode.Element, context: Context, emitter: Emitter, build: BuildState) {
        emitter.flush()
        val previousRole = emitter.role
        emitter.role = when {
            element.tag in headingScales -> ParagraphRole.Heading
            element.classes.any { it in mutedClasses } -> ParagraphRole.Muted
            else -> previousRole
        }
        if (isSideBySideLayout(element)) visitSpaced(element.children, context, emitter, build)
        else visitAll(element.children, context, emitter, build)
        emitter.flush()
        emitter.role = previousRole
    }

    private fun isSideBySideLayout(element: HtmlNode.Element): Boolean {
        val children = element.children.filterNot { it is HtmlNode.Text && it.text.isBlank() }
        return children.size >= 2 && children.all { child ->
            child is HtmlNode.Element && child.tag in sideBySideTags && clozeRole(child) == null
        }
    }

    private fun visitSpaced(nodes: List<HtmlNode>, context: Context, emitter: Emitter, build: BuildState) {
        var previousWasElement = false
        nodes.forEach { node ->
            if (node is HtmlNode.Element && previousWasElement) emitter.inline(CardInline.Text(" ", context.style))
            visit(node, context, emitter, build)
            previousWasElement = node is HtmlNode.Element
        }
    }

    private fun visitHidden(element: HtmlNode.Element, context: Context, emitter: Emitter, build: BuildState) {
        val hidden = Emitter()
        visitAll(element.children, context, hidden, build)
        val hiddenBlocks = hidden.finish()
        if ("hint" in element.classes && hiddenBlocks.isNotEmpty()) {
            emitter.block(CardBlock.Hint(build.pendingHintLabel ?: "Hint", hiddenBlocks))
            build.pendingHintLabel = null
        }
    }

    private fun clozeRole(element: HtmlNode.Element): ClozeRole? {
        val classes = element.classes
        return when {
            "cloze-inactive" in classes -> ClozeRole.Inactive
            "cloze" in classes && element.attributes.containsKey("data-cloze") -> ClozeRole.Hidden
            "cloze" in classes -> ClozeRole.Revealed
            else -> null
        }
    }

    private fun visitCloze(element: HtmlNode.Element, context: Context, emitter: Emitter, build: BuildState) {
        val role = clozeRole(element) ?: return
        val ordinal = element.attribute("data-ordinal")?.toIntOrNull() ?: 0
        val shapeText = if (role == ClozeRole.Hidden) element.attribute("data-cloze").orEmpty() else textContent(element)
        if (shapeText.trim().startsWith(OcclusionShapes.PREFIX)) {
            OcclusionShapes.parse(shapeText, ordinal, role)?.let { build.occlusionShapes += it }
            return
        }
        visitAll(element.children, context.copy(style = context.style.copy(cloze = role)), emitter, build)
    }

    private fun appendText(text: String, context: Context, emitter: Emitter) {
        var position = 0
        markerPattern.findAll(text).forEach { match ->
            if (match.range.first > position) appendPlainText(text.substring(position, match.range.first), context, emitter)
            val groups = match.groupValues
            when {
                groups[1].isNotEmpty() -> emitter.inline(CardInline.Sound(groups[1].trim()))
                groups[2].isNotEmpty() -> {
                    val segments = groups[2].split(':')
                    emitter.block(CardBlock.TypeAnswer(segments.last(), isCloze = "cloze" in segments.dropLast(1)))
                }
                groups[3].isNotEmpty() -> emitter.inline(CardInline.Math(groups[3].trim()))
                groups[4].isNotEmpty() -> emitter.block(CardBlock.DisplayMath(groups[4].trim()))
                groups[5].isNotEmpty() -> emitter.block(CardBlock.DisplayMath(groups[5].trim()))
                groups[6].isNotEmpty() -> emitter.inline(CardInline.Math(groups[6].trim()))
                groups[7].isNotEmpty() -> emitter.block(CardBlock.DisplayMath(groups[7].trim()))
            }
            position = match.range.last + 1
        }
        if (position < text.length) appendPlainText(text.substring(position), context, emitter)
    }

    private fun appendPlainText(text: String, context: Context, emitter: Emitter) {
        when (context.whitespace) {
            Whitespace.Collapse -> emitter.inline(CardInline.Text(collapseWhitespace(text), context.style))
            Whitespace.PreLine, Whitespace.Preserve -> text.replace("\r\n", "\n").split('\n').forEachIndexed { index, line ->
                if (index > 0) emitter.inline(CardInline.LineBreak)
                val content = if (context.whitespace == Whitespace.PreLine) collapseWhitespace(line) else line
                if (content.isNotEmpty()) emitter.inline(CardInline.Text(content, context.style))
            }
        }
    }

    private fun codeBlock(element: HtmlNode.Element): CardBlock.CodeBlock {
        val codeChild = element.children.filterIsInstance<HtmlNode.Element>().firstOrNull { it.tag == "code" }
        val language = (codeChild?.let(::languageOf) ?: languageOf(element))
        val code = textContent(element).replace("\r\n", "\n").trim('\n')
        return CardBlock.CodeBlock(language, code)
    }

    private fun languageOf(element: HtmlNode.Element): String? =
        element.attribute("data-lang")?.lowercase()
            ?: element.classes.firstNotNullOfOrNull { name ->
                when {
                    name.startsWith("language-") -> name.removePrefix("language-")
                    name.startsWith("lang-") -> name.removePrefix("lang-")
                    else -> null
                }
            }?.lowercase()

    private fun listBlock(element: HtmlNode.Element, context: Context, build: BuildState): CardBlock.ListBlock {
        val items = mutableListOf<List<CardBlock>>()
        val loose = mutableListOf<HtmlNode>()
        fun flushLoose() {
            if (loose.isNotEmpty()) {
                val blocks = containerBlocks(loose.toList(), context, build)
                if (blocks.isNotEmpty()) items += blocks
                loose.clear()
            }
        }
        element.children.forEach { child ->
            if (child is HtmlNode.Element && child.tag == "li") {
                flushLoose()
                items += containerBlocks(child.children, context, build)
            } else {
                loose += child
            }
        }
        flushLoose()
        return CardBlock.ListBlock(ordered = element.tag == "ol", items = items)
    }

    private fun tableBlock(element: HtmlNode.Element, context: Context, build: BuildState): CardBlock.Table {
        val rows = mutableListOf<List<TableCell>>()
        fun collectRows(nodes: List<HtmlNode>) {
            nodes.filterIsInstance<HtmlNode.Element>().forEach { child ->
                when (child.tag) {
                    "tr" -> rows += child.children.filterIsInstance<HtmlNode.Element>()
                        .filter { it.tag == "td" || it.tag == "th" }
                        .map { cell -> TableCell(flattenToInlines(containerBlocks(cell.children, context.copy(style = applyElementStyle(cell, context.style)), build)), cell.tag == "th") }
                    "thead", "tbody", "tfoot" -> collectRows(child.children)
                }
            }
        }
        collectRows(element.children)
        return CardBlock.Table(rows.filter { it.isNotEmpty() })
    }

    private fun containerBlocks(nodes: List<HtmlNode>, context: Context, build: BuildState): List<CardBlock> {
        val emitter = Emitter()
        visitAll(nodes, context, emitter, build)
        return emitter.finish()
    }

    private fun flattenToInlines(blocks: List<CardBlock>): List<CardInline> {
        val result = mutableListOf<CardInline>()
        blocks.forEach { block ->
            if (result.isNotEmpty()) result += CardInline.LineBreak
            when (block) {
                is CardBlock.Paragraph -> result += block.inlines
                is CardBlock.CodeBlock -> result += CardInline.Text(block.code, InlineStyle(code = true))
                is CardBlock.DisplayMath -> result += CardInline.Math(block.tex)
                is CardBlock.ListBlock -> result += flattenToInlines(block.items.flatten())
                is CardBlock.Quote -> result += flattenToInlines(block.blocks)
                is CardBlock.Picture -> block.description?.let { result += CardInline.Text(it) }
                else -> Unit
            }
        }
        return normalizeInlines(result)
    }

    private fun attachOcclusion(blocks: List<CardBlock>, shapes: List<OcclusionShape>): List<CardBlock> {
        if (shapes.isEmpty()) return blocks
        var attached = false
        return blocks.map { block ->
            if (!attached && block is CardBlock.Picture) {
                attached = true
                CardBlock.Occlusion(block.source, shapes)
            } else {
                block
            }
        }
    }

    private fun whitespaceFor(element: HtmlNode.Element, inherited: Whitespace): Whitespace {
        val declared = element.inlineStyle["white-space"]?.lowercase()
        return when {
            declared == "pre" || declared == "pre-wrap" -> Whitespace.Preserve
            declared == "pre-line" -> Whitespace.PreLine
            declared == "normal" || declared == "nowrap" -> Whitespace.Collapse
            element.classes.any { it in preLineClasses } -> Whitespace.PreLine
            else -> inherited
        }
    }

    private fun applyElementStyle(element: HtmlNode.Element, inherited: InlineStyle): InlineStyle {
        var style = when (element.tag) {
            "b", "strong" -> inherited.copy(bold = true)
            "i", "em", "cite", "var", "dfn" -> inherited.copy(italic = true)
            "u", "ins" -> inherited.copy(underline = true)
            "s", "strike", "del" -> inherited.copy(strikethrough = true)
            "sub" -> inherited.copy(baseline = Baseline.Subscript)
            "sup" -> inherited.copy(baseline = Baseline.Superscript)
            "mark" -> inherited.copy(highlightArgb = 0xFFFFE58AL)
            "small" -> inherited.copy(sizeScale = (inherited.sizeScale ?: 1f) * 0.85f)
            "big" -> inherited.copy(sizeScale = (inherited.sizeScale ?: 1f) * 1.2f)
            "th" -> inherited.copy(bold = true)
            "font" -> inherited.copy(
                colorArgb = element.attribute("color")?.let(CssColors::parse) ?: inherited.colorArgb,
                sizeScale = element.attribute("size")?.let(::fontTagScale) ?: inherited.sizeScale,
            )
            else -> headingScales[element.tag]?.let { inherited.copy(bold = true, sizeScale = it) } ?: inherited
        }
        val declarations = element.inlineStyle
        declarations["font-weight"]?.let { weight ->
            val numeric = weight.toIntOrNull()
            style = style.copy(bold = weight == "bold" || weight == "bolder" || (numeric != null && numeric >= 600))
        }
        declarations["font-style"]?.let { style = style.copy(italic = it == "italic" || it == "oblique") }
        declarations["text-decoration"]?.let { decoration ->
            style = style.copy(
                underline = "underline" in decoration || style.underline,
                strikethrough = "line-through" in decoration || style.strikethrough,
            )
        }
        declarations["text-decoration-line"]?.let { decoration ->
            style = style.copy(underline = "underline" in decoration || style.underline, strikethrough = "line-through" in decoration || style.strikethrough)
        }
        declarations["color"]?.let(CssColors::parse)?.let { style = style.copy(colorArgb = it) }
        (declarations["background-color"] ?: declarations["background"])?.let(CssColors::parse)?.let { style = style.copy(highlightArgb = it) }
        declarations["font-size"]?.let(::cssFontScale)?.let { style = style.copy(sizeScale = it) }
        declarations["vertical-align"]?.let {
            when (it) {
                "sub" -> style = style.copy(baseline = Baseline.Subscript)
                "super" -> style = style.copy(baseline = Baseline.Superscript)
            }
        }
        return style
    }

    private fun fontTagScale(size: String): Float? {
        val trimmed = size.trim()
        val value = trimmed.trimStart('+', '-').toIntOrNull() ?: return null
        val index = when {
            trimmed.startsWith("+") -> 2 + value
            trimmed.startsWith("-") -> 2 - value
            else -> value - 1
        }.coerceIn(0, fontSizeScales.lastIndex)
        return fontSizeScales[index]
    }

    private fun cssFontScale(size: String): Float? {
        val value = size.trim().lowercase()
        val scale = when {
            value.endsWith("px") -> value.removeSuffix("px").toFloatOrNull()?.div(20f)
            value.endsWith("pt") -> value.removeSuffix("pt").toFloatOrNull()?.div(15f)
            value.endsWith("rem") -> value.removeSuffix("rem").toFloatOrNull()
            value.endsWith("em") -> value.removeSuffix("em").toFloatOrNull()
            value.endsWith("%") -> value.removeSuffix("%").toFloatOrNull()?.div(100f)
            value == "xx-small" -> 0.6f
            value == "x-small" -> 0.7f
            value == "small" || value == "smaller" -> 0.85f
            value == "medium" -> 1f
            value == "large" || value == "larger" -> 1.2f
            value == "x-large" -> 1.5f
            value == "xx-large" -> 2f
            else -> null
        }
        return scale?.coerceIn(0.6f, 2.5f)
    }

    internal fun textContent(node: HtmlNode): String = when (node) {
        is HtmlNode.Text -> node.text
        is HtmlNode.Element -> if (node.tag == "br") "\n" else node.children.joinToString("") { textContent(it) }
    }

    private fun collapseWhitespace(text: String): String {
        val builder = StringBuilder(text.length)
        var previousWasSpace = false
        text.forEach { character ->
            val isSpace = character == ' ' || character == '\n' || character == '\t' || character == '\r' || character == '\u000C'
            if (isSpace) {
                if (!previousWasSpace) builder.append(' ')
                previousWasSpace = true
            } else {
                builder.append(character)
                previousWasSpace = false
            }
        }
        return builder.toString()
    }

    internal fun normalizeInlines(inlines: List<CardInline>): List<CardInline> {
        val merged = mutableListOf<CardInline>()
        inlines.forEach { inline ->
            val last = merged.lastOrNull()
            if (inline is CardInline.Text && last is CardInline.Text && last.style == inline.style) {
                merged[merged.lastIndex] = CardInline.Text(last.text + inline.text, last.style)
            } else if (inline !is CardInline.Text || inline.text.isNotEmpty()) {
                merged += inline
            }
        }
        val trimmed = mutableListOf<CardInline>()
        merged.forEachIndexed { index, inline ->
            if (inline !is CardInline.Text || inline.style.code) {
                trimmed += inline
                return@forEachIndexed
            }
            var text = inline.text
            val previous = trimmed.lastOrNull()
            val atLineStart = previous == null || previous == CardInline.LineBreak ||
                (previous is CardInline.Text && previous.text.endsWith(' ') && !previous.style.code)
            if (atLineStart) text = text.trimStart(' ')
            val next = merged.getOrNull(index + 1)
            if (next == null || next == CardInline.LineBreak) text = text.trimEnd(' ')
            if (text.isNotEmpty()) trimmed += CardInline.Text(text, inline.style)
        }
        while (trimmed.firstOrNull() == CardInline.LineBreak) trimmed.removeAt(0)
        while (trimmed.lastOrNull() == CardInline.LineBreak) trimmed.removeAt(trimmed.lastIndex)
        val hasContent = trimmed.any { it !is CardInline.LineBreak && !(it is CardInline.Text && it.text.isBlank()) }
        return if (hasContent) trimmed else emptyList()
    }
}
