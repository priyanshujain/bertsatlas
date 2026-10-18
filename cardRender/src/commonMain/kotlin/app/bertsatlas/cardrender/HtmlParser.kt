package app.bertsatlas.cardrender

sealed interface HtmlNode {
    data class Text(val text: String) : HtmlNode
    data class Element(
        val tag: String,
        val attributes: Map<String, String>,
        val children: List<HtmlNode>,
    ) : HtmlNode {
        fun attribute(name: String): String? = attributes[name]
        val classes: Set<String> get() = attribute("class")?.split(' ')?.filter { it.isNotBlank() }?.toSet().orEmpty()
        val inlineStyle: Map<String, String> get() = parseInlineStyle(attribute("style").orEmpty())
    }
}

object HtmlParser {
    private val voidTags = setOf("br", "hr", "img", "input", "meta", "link", "source", "area", "col", "wbr", "embed", "param", "track")
    private val rawTextTags = setOf("script", "style", "textarea", "title")
    private val discardedTags = setOf("script", "style", "head", "title", "meta", "link", "template", "noscript")
    private val implicitlyClosedBy = mapOf(
        "p" to setOf("p", "div", "ul", "ol", "table", "pre", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "hr"),
        "li" to setOf("li"),
        "td" to setOf("td", "th", "tr"),
        "th" to setOf("td", "th", "tr"),
        "tr" to setOf("tr"),
    )

    fun parse(html: String): List<HtmlNode> {
        val root = MutableElement("#root", emptyMap())
        val stack = ArrayDeque<MutableElement>().apply { addLast(root) }
        var position = 0

        fun appendText(text: String) {
            if (text.isNotEmpty()) stack.last().children += HtmlNode.Text(HtmlEntities.decode(text))
        }

        while (position < html.length) {
            val tagStart = html.indexOf('<', position)
            if (tagStart < 0) {
                appendText(html.substring(position))
                break
            }
            appendText(html.substring(position, tagStart))
            when {
                html.startsWith("<!--", tagStart) -> {
                    val end = html.indexOf("-->", tagStart + 4)
                    position = if (end < 0) html.length else end + 3
                }
                html.startsWith("<!", tagStart) || html.startsWith("<?", tagStart) -> {
                    val end = html.indexOf('>', tagStart)
                    position = if (end < 0) html.length else end + 1
                }
                html.startsWith("</", tagStart) -> {
                    val end = html.indexOf('>', tagStart)
                    if (end < 0) {
                        appendText(html.substring(tagStart))
                        position = html.length
                    } else {
                        val name = html.substring(tagStart + 2, end).trim().lowercase()
                        closeElement(stack, name)
                        position = end + 1
                    }
                }
                tagStart + 1 < html.length && html[tagStart + 1].isLetter() -> {
                    val tag = readStartTag(html, tagStart)
                    if (tag == null) {
                        appendText(html.substring(tagStart))
                        position = html.length
                    } else {
                        position = tag.end
                        closeImplicitly(stack, tag.name)
                        val element = MutableElement(tag.name, tag.attributes)
                        if (tag.name in rawTextTags) {
                            val closing = html.indexOf("</${tag.name}", position, ignoreCase = true)
                            val rawEnd = if (closing < 0) html.length else closing
                            if (tag.name !in discardedTags) element.children += HtmlNode.Text(HtmlEntities.decode(html.substring(position, rawEnd)))
                            if (tag.name !in discardedTags) stack.last().children += element.toNode()
                            val closeEnd = if (closing < 0) html.length else html.indexOf('>', closing).let { if (it < 0) html.length else it + 1 }
                            position = closeEnd
                        } else if (tag.name in voidTags || tag.selfClosing) {
                            stack.last().children += element.toNode()
                        } else {
                            stack.last().children += element
                            stack.addLast(element)
                        }
                    }
                }
                else -> {
                    appendText("<")
                    position = tagStart + 1
                }
            }
        }
        return root.toNode().children.filterNot { it is HtmlNode.Element && it.tag in discardedTags }
    }

    private fun closeElement(stack: ArrayDeque<MutableElement>, name: String) {
        val index = stack.indexOfLast { it.tag == name }
        if (index <= 0) return
        while (stack.size > index) stack.removeLast()
    }

    private fun closeImplicitly(stack: ArrayDeque<MutableElement>, openingTag: String) {
        while (stack.size > 1) {
            val current = stack.last().tag
            val closers = implicitlyClosedBy[current] ?: return
            if (openingTag !in closers) return
            stack.removeLast()
        }
    }

    private class StartTag(val name: String, val attributes: Map<String, String>, val selfClosing: Boolean, val end: Int)

    private fun readStartTag(html: String, start: Int): StartTag? {
        var position = start + 1
        val nameStart = position
        while (position < html.length && (html[position].isLetterOrDigit() || html[position] == '-' || html[position] == ':')) position++
        val name = html.substring(nameStart, position).lowercase()
        val attributes = linkedMapOf<String, String>()
        var selfClosing = false
        while (position < html.length) {
            while (position < html.length && html[position].isWhitespace()) position++
            if (position >= html.length) return null
            val current = html[position]
            if (current == '>') return StartTag(name, attributes, selfClosing, position + 1)
            if (current == '/') {
                selfClosing = true
                position++
                continue
            }
            val attributeStart = position
            while (position < html.length && !html[position].isWhitespace() && html[position] != '=' && html[position] != '>' && html[position] != '/') position++
            val attributeName = html.substring(attributeStart, position).lowercase()
            while (position < html.length && html[position].isWhitespace()) position++
            if (position < html.length && html[position] == '=') {
                position++
                while (position < html.length && html[position].isWhitespace()) position++
                if (position >= html.length) return null
                val quote = html[position]
                val value: String
                if (quote == '"' || quote == '\'') {
                    val valueEnd = html.indexOf(quote, position + 1)
                    if (valueEnd < 0) return null
                    value = html.substring(position + 1, valueEnd)
                    position = valueEnd + 1
                } else {
                    val valueStart = position
                    while (position < html.length && !html[position].isWhitespace() && html[position] != '>') position++
                    value = html.substring(valueStart, position)
                }
                if (attributeName.isNotEmpty()) attributes[attributeName] = HtmlEntities.decode(value)
            } else if (attributeName.isNotEmpty()) {
                attributes[attributeName] = ""
            } else {
                position++
            }
        }
        return null
    }

    private class MutableElement(val tag: String, val attributes: Map<String, String>) {
        val children = mutableListOf<Any>()

        fun toNode(): HtmlNode.Element = HtmlNode.Element(
            tag,
            attributes,
            children.map { if (it is MutableElement) it.toNode() else it as HtmlNode },
        )
    }
}

internal fun parseInlineStyle(style: String): Map<String, String> =
    style.split(';').mapNotNull { declaration ->
        val separator = declaration.indexOf(':')
        if (separator <= 0) null
        else declaration.substring(0, separator).trim().lowercase() to declaration.substring(separator + 1).trim().removeSuffix("!important").trim()
    }.toMap()

object HtmlEntities {
    private val named = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to "\u00A0",
        "ndash" to "\u2013", "mdash" to "\u2014", "hellip" to "…", "laquo" to "«", "raquo" to "»",
        "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”", "bull" to "•",
        "middot" to "·", "times" to "×", "divide" to "÷", "deg" to "°", "plusmn" to "±",
        "copy" to "©", "reg" to "®", "trade" to "™", "euro" to "€", "pound" to "£",
        "yen" to "¥", "cent" to "¢", "sect" to "§", "para" to "¶", "rarr" to "→",
        "larr" to "←", "uarr" to "↑", "darr" to "↓", "harr" to "↔", "rArr" to "⇒",
        "lArr" to "⇐", "hArr" to "⇔", "le" to "≤", "ge" to "≥", "ne" to "≠",
        "asymp" to "≈", "infin" to "∞", "sum" to "∑", "minus" to "−", "radic" to "√",
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ", "epsilon" to "ε",
        "theta" to "θ", "lambda" to "λ", "mu" to "μ", "pi" to "π", "sigma" to "σ",
        "omega" to "ω", "Delta" to "Δ", "Sigma" to "Σ", "Omega" to "Ω", "shy" to "­",
        "zwj" to "‍", "zwnj" to "‌", "ensp" to " ", "emsp" to " ", "thinsp" to " ",
        "iexcl" to "¡", "iquest" to "¿", "acute" to "´", "frac12" to "½", "frac14" to "¼",
        "frac34" to "¾", "sup2" to "²", "sup3" to "³", "micro" to "µ",
    )

    fun decode(text: String): String {
        if ('&' !in text) return text
        val result = StringBuilder(text.length)
        var position = 0
        while (position < text.length) {
            val ampersand = text.indexOf('&', position)
            if (ampersand < 0) {
                result.append(text, position, text.length)
                break
            }
            result.append(text, position, ampersand)
            val semicolon = text.indexOf(';', ampersand)
            val entity = if (semicolon in (ampersand + 2)..(ampersand + 12)) text.substring(ampersand + 1, semicolon) else null
            val decoded = entity?.let(::decodeEntity)
            if (decoded != null) {
                result.append(decoded)
                position = semicolon + 1
            } else {
                result.append('&')
                position = ampersand + 1
            }
        }
        return result.toString()
    }

    private fun decodeEntity(entity: String): String? = when {
        entity.startsWith("#x") || entity.startsWith("#X") -> entity.substring(2).toIntOrNull(16)?.let(::codePointToString)
        entity.startsWith("#") -> entity.substring(1).toIntOrNull()?.let(::codePointToString)
        else -> named[entity]
    }

    private fun codePointToString(codePoint: Int): String? = when {
        codePoint < 0 || codePoint > 0x10FFFF -> null
        codePoint < 0x10000 -> codePoint.toChar().toString()
        else -> {
            val offset = codePoint - 0x10000
            charArrayOf((0xD800 + (offset shr 10)).toChar(), (0xDC00 + (offset and 0x3FF)).toChar()).concatToString()
        }
    }
}
