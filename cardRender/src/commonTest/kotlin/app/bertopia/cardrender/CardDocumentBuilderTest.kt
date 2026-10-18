package app.bertopia.cardrender

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CardDocumentBuilderTest {
    private fun build(html: String) = CardDocumentBuilder.build(html).blocks

    private fun texts(block: CardBlock): List<CardInline.Text> =
        (block as CardBlock.Paragraph).inlines.filterIsInstance<CardInline.Text>()

    @Test
    fun collapsesWhitespaceAndMergesPlainText() {
        val blocks = build("  Hello \n   <b>big</b>   world  ")
        assertEquals(1, blocks.size)
        val inlines = (blocks.single() as CardBlock.Paragraph).inlines
        assertEquals(
            listOf(
                CardInline.Text("Hello "),
                CardInline.Text("big", InlineStyle(bold = true)),
                CardInline.Text(" world"),
            ),
            inlines,
        )
    }

    @Test
    fun divsAndParagraphsBecomeSeparateParagraphs() {
        val blocks = build("<div>One</div><div>Two<br>Three</div><p>Four</p>")
        assertEquals(3, blocks.size)
        assertEquals(
            listOf(CardInline.Text("Two"), CardInline.LineBreak, CardInline.Text("Three")),
            (blocks[1] as CardBlock.Paragraph).inlines,
        )
    }

    @Test
    fun nestedInlineStylesCombine() {
        val text = texts(build("<i>a<u>b<s>c</s></u></i><sub>2</sub><sup>n</sup>").single())
        assertEquals(InlineStyle(italic = true), text[0].style)
        assertEquals(InlineStyle(italic = true, underline = true), text[1].style)
        assertEquals(InlineStyle(italic = true, underline = true, strikethrough = true), text[2].style)
        assertEquals(Baseline.Subscript, text[3].style.baseline)
        assertEquals(Baseline.Superscript, text[4].style.baseline)
    }

    @Test
    fun colorsAndSizesFromFontTagsAndInlineStyles() {
        val text = texts(build("<font color=\"#f00\" size=5>big red</font><span style=\"color: rgb(0, 128, 0); font-size: 30px; font-weight: bold\">green</span>").single())
        assertEquals(0xFFFF0000L, text[0].style.colorArgb)
        assertEquals(1.5f, text[0].style.sizeScale)
        assertEquals(0xFF008000L, text[1].style.colorArgb)
        assertEquals(1.5f, text[1].style.sizeScale)
        assertTrue(text[1].style.bold)
    }

    @Test
    fun decodesEntities() {
        val text = texts(build("Fish &amp; chips &lt;3 &#x1F600; &eacute;x").single())
        assertEquals("Fish & chips <3 😀 &eacute;x", text.single().text)
    }

    @Test
    fun listsAndNestedLists() {
        val list = assertIs<CardBlock.ListBlock>(build("<ol><li>First</li><li>Second<ul><li>Inner</li></ul></li></ol>").single())
        assertTrue(list.ordered)
        assertEquals(2, list.items.size)
        val nested = assertIs<CardBlock.ListBlock>(list.items[1][1])
        assertEquals(1, nested.items.size)
    }

    @Test
    fun unclosedListItemsAndParagraphsAreClosedImplicitly() {
        val list = assertIs<CardBlock.ListBlock>(build("<ul><li>One<li>Two</ul>").single())
        assertEquals(2, list.items.size)
    }

    @Test
    fun simpleTables() {
        val table = assertIs<CardBlock.Table>(build("<table><thead><tr><th>Word</th><th>Meaning</th></tr></thead><tr><td>chat</td><td><i>cat</i></td></tr></table>").single())
        assertEquals(2, table.rows.size)
        assertTrue(table.rows[0][0].isHeader)
        assertEquals(CardInline.Text("Word", InlineStyle(bold = true)), table.rows[0][0].inlines.single())
        assertEquals(CardInline.Text("cat", InlineStyle(italic = true)), table.rows[1][1].inlines.single())
    }

    @Test
    fun imagesBecomePictureBlocks() {
        val blocks = build("Before <img src=\"paris.jpg\" alt=\"Paris\"> after")
        assertEquals(3, blocks.size)
        assertEquals(CardBlock.Picture("paris.jpg", "Paris"), blocks[1])
    }

    @Test
    fun soundTagsBecomeSoundInlines() {
        val inlines = (build("Listen [sound:hola.mp3]").single() as CardBlock.Paragraph).inlines
        assertEquals(listOf(CardInline.Text("Listen "), CardInline.Sound("hola.mp3")), inlines)
    }

    @Test
    fun mathDelimiters() {
        val blocks = build("Inline \\(x^2\\) and [\$]y[/\$]<br>\\[\\frac{a}{b}\\] then [\$\$]z[/\$\$] and [latex]\\alpha[/latex]")
        val first = blocks[0] as CardBlock.Paragraph
        assertTrue(CardInline.Math("x^2") in first.inlines)
        assertTrue(CardInline.Math("y") in first.inlines)
        assertTrue(CardBlock.DisplayMath("\\frac{a}{b}") in blocks)
        assertTrue(CardBlock.DisplayMath("z") in blocks)
        assertTrue(CardBlock.DisplayMath("\\alpha") in blocks)
    }

    @Test
    fun typeAnswerMarkers() {
        val blocks = build("Capital of France?<br>[[type:Answer]]")
        assertEquals(CardBlock.TypeAnswer("Answer", isCloze = false), blocks.last())
        assertEquals(CardBlock.TypeAnswer("Text", isCloze = true), build("[[type:cloze:Text]]").single())
        assertEquals(CardBlock.TypeAnswer("Answer", isCloze = false), build("[[type:nc:Answer]]").single())
    }

    @Test
    fun clozeSpansFromAnkiTemplateOutput() {
        val question = texts(build("Paris is the <span class=\"cloze\" data-cloze=\"capital\" data-ordinal=\"1\">[...]</span> of <span class=\"cloze-inactive\" data-ordinal=\"2\">France</span>").single())
        assertEquals(ClozeRole.Hidden, question.first { it.text == "[...]" }.style.cloze)
        assertEquals(ClozeRole.Inactive, question.first { it.text == "France" }.style.cloze)
        val answer = texts(build("Paris is the <span class=\"cloze\" data-ordinal=\"1\">capital</span>").single())
        assertEquals(ClozeRole.Revealed, answer.last().style.cloze)
    }

    @Test
    fun codeBlocksKeepWhitespaceAndLanguage() {
        val code = assertIs<CardBlock.CodeBlock>(build("<pre><code class=\"language-kotlin\">fun main() {\n    println(1)\n}</code></pre>").single())
        assertEquals("kotlin", code.language)
        assertEquals("fun main() {\n    println(1)\n}", code.code)
        val inline = texts(build("Use <code>  map  </code> here").single())
        assertEquals(CardInline.Text("  map  ", InlineStyle(code = true)), inline[1])
    }

    @Test
    fun scriptsStylesAndCommentsAreDropped() {
        val blocks = build("<style>.card{color:red}</style><script>alert(1)</script><!-- note -->Visible")
        assertEquals(listOf(CardInline.Text("Visible")), (blocks.single() as CardBlock.Paragraph).inlines)
    }

    @Test
    fun unsupportedTagsDegradeToText() {
        val blocks = build("<custom-widget><blink>Still</blink> <marquee>here</marquee></custom-widget>")
        assertEquals(listOf(CardInline.Text("Still here")), (blocks.single() as CardBlock.Paragraph).inlines)
    }

    @Test
    fun brokenMarkupDoesNotThrow() {
        val blocks = build("<b>open <i>never closed</b> stray </div> <p unterminated=\"")
        assertTrue(blocks.isNotEmpty())
    }

    @Test
    fun bertHintBecomesHintBlock() {
        val blocks = build("Q<div class=hint><a class=\"hint\" data-field=\"Hint\">Hint</a><div class=\"hint\" style=\"display: none\">Starts with P</div></div>")
        val hint = assertIs<CardBlock.Hint>(blocks.last())
        assertEquals("Hint", hint.label)
        assertEquals(listOf(CardInline.Text("Starts with P")), (hint.blocks.single() as CardBlock.Paragraph).inlines)
    }

    @Test
    fun hiddenContentIsNotShown() {
        val blocks = build("Shown<div style=\"display:none\">Secret</div>")
        assertEquals(1, blocks.size)
    }

    @Test
    fun bertMutedAndListClasses() {
        val blocks = build("<div class=prompt>Which country?</div><div class=list>Sucre\nLa Paz</div>")
        assertEquals(ParagraphRole.Muted, (blocks[0] as CardBlock.Paragraph).role)
        assertEquals(
            listOf(CardInline.Text("Sucre"), CardInline.LineBreak, CardInline.Text("La Paz")),
            (blocks[1] as CardBlock.Paragraph).inlines,
        )
    }

    @Test
    fun imageOcclusionShapesAttachToImage() {
        val html = "<div class=occlusion>" +
            "<span class=\"cloze\" data-cloze=\"image-occlusion:rect:left=.1:top=.2:width=.3:height=.1:oi=1\" data-ordinal=\"1\">[...]</span>" +
            "<span class=\"cloze-inactive\" data-ordinal=\"2\">image-occlusion:ellipse:left=.5:top=.5:rx=.1:ry=.05:oi=1</span>" +
            "<span class=\"cloze-inactive\" data-ordinal=\"3\">image-occlusion:polygon:points=.1,.1 .2,.1 .15,.2</span>" +
            "</div><img src=\"heart.png\">"
        val occlusion = assertIs<CardBlock.Occlusion>(build(html).single())
        assertEquals("heart.png", occlusion.imageSource)
        assertEquals(3, occlusion.shapes.size)
        assertEquals(OcclusionState.Hidden, occlusion.shapes[0].state)
        assertEquals(OcclusionGeometry.Rect(.1f, .2f, .3f, .1f), occlusion.shapes[0].geometry)
        assertEquals(OcclusionState.InactiveMasked, occlusion.shapes[1].state)
        assertEquals(OcclusionState.InactiveVisible, occlusion.shapes[2].state)
        assertEquals(3, (occlusion.shapes[2].geometry as OcclusionGeometry.Polygon).points.size)
    }

    @Test
    fun ankiImageOcclusionInsideHiddenDivStillAttaches() {
        val html = "<div style=\"display: none\"><span class=\"cloze\" data-ordinal=\"1\">image-occlusion:rect:left=10:top=20:width=30:height=40</span></div>" +
            "<div id=\"image-occlusion-container\"><img src=\"map.png\"><canvas id=\"image-occlusion-canvas\"></canvas></div>"
        val occlusion = assertIs<CardBlock.Occlusion>(build(html).single())
        assertEquals(OcclusionState.Revealed, occlusion.shapes.single().state)
        assertTrue(occlusion.shapes.single().usesPixelCoordinates)
    }
}
