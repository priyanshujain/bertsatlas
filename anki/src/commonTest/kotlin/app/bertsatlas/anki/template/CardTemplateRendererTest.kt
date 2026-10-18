package app.bertsatlas.anki.template

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CardTemplateRendererTest {
    private val basic = CardRenderContext(fields = mapOf("Front" to "Paris", "Back" to "<b>France</b>", "Empty" to "<br> <div></div>"))

    @Test
    fun replacesFieldsAndTrimsKeys() {
        assertEquals("Paris is in <b>France</b>", CardTemplateRenderer.renderQuestion("{{Front}} is in {{ Back }}", basic))
    }

    @Test
    fun unknownFieldIsReported() {
        assertEquals("{unknown field Nope}", CardTemplateRenderer.renderQuestion("{{Nope}}", basic))
    }

    @Test
    fun conditionalsTreatHtmlOnlyFieldsAsEmpty() {
        val template = "{{#Front}}A{{/Front}}{{#Empty}}B{{/Empty}}{{^Empty}}C{{/Empty}}{{^Front}}D{{/Front}}{{#Missing}}E{{/Missing}}"
        assertEquals("AC", CardTemplateRenderer.renderQuestion(template, basic))
    }

    @Test
    fun nestedSections() {
        val template = "{{#Front}}[{{#Back}}{{Back}}{{/Back}}{{^Empty}}!{{/Empty}}]{{/Front}}"
        assertEquals("[<b>France</b>!]", CardTemplateRenderer.renderQuestion(template, basic))
    }

    @Test
    fun mismatchedSectionsThrow() {
        assertFailsWith<TemplateException> { CardTemplateRenderer.renderQuestion("{{#Front}}x", basic) }
        assertFailsWith<TemplateException> { CardTemplateRenderer.renderQuestion("{{#Front}}x{{/Back}}", basic) }
        assertFailsWith<TemplateException> { CardTemplateRenderer.renderQuestion("x{{/Back}}", basic) }
    }

    @Test
    fun frontSideIsQuestionOnAnswerAndEmptyOnQuestion() {
        val card = CardTemplateRenderer.render("{{FrontSide}}{{Front}}", "{{FrontSide}}<hr id=answer>{{Back}}", basic)
        assertEquals("Paris", card.question)
        assertEquals("Paris<hr id=answer><b>France</b>", card.answer)
    }

    @Test
    fun textFilterStripsHtmlAndDecodesEntities() {
        val context = CardRenderContext(fields = mapOf("F" to "<style>x{}</style><i>a</i>&nbsp;&amp;&#65;&#x42;<!-- c -->"))
        assertEquals("a &AB", CardTemplateRenderer.renderQuestion("{{text:F}}", context))
    }

    @Test
    fun unknownFiltersPassThrough() {
        assertEquals("Paris", CardTemplateRenderer.renderQuestion("{{furigana:kana:Front}}", basic))
    }

    @Test
    fun typeFilterEmitsMarker() {
        assertEquals("[[type:Back]]", CardTemplateRenderer.renderQuestion("{{type:Back}}", basic))
        assertEquals("[[type:cloze:Text]]", CardTemplateRenderer.renderQuestion("{{type:cloze:Text}}", basic))
        assertEquals("[[type:nc:Back]]", CardTemplateRenderer.renderAnswer("{{type:nc:Back}}", basic, ""))
    }

    @Test
    fun hintFilterWrapsNonEmptyField() {
        val rendered = CardTemplateRenderer.renderQuestion("{{hint:Front}}", basic)
        assertTrue(rendered.startsWith("<a class=\"hint\""))
        assertTrue(rendered.contains("<div class=\"hint\" style=\"display: none\">Paris</div>"))
        assertEquals("", CardTemplateRenderer.renderQuestion("{{hint:Empty}}", basic))
    }

    @Test
    fun specialFields() {
        val context = CardRenderContext(
            fields = emptyMap(),
            tags = listOf("geo", "bert::similar::nordic"),
            deckName = "Flags::Europe",
            notetypeName = "Bert Flag",
            cardTemplateName = "Card 1",
        )
        assertEquals(
            "geo bert::similar::nordic|Flags::Europe|Europe|Bert Flag|Card 1",
            CardTemplateRenderer.renderQuestion("{{Tags}}|{{Deck}}|{{Subdeck}}|{{Type}}|{{Card}}", context),
        )
    }

    @Test
    fun textOfClozeChain() {
        val context = CardRenderContext(fields = mapOf("Text" to "The {{c1::<b>Seine</b>}} flows"), clozeOrdinal = 1)
        assertEquals("The Seine flows", CardTemplateRenderer.renderAnswer("{{text:cloze:Text}}", context, ""))
        assertEquals("The [...] flows", CardTemplateRenderer.renderQuestion("{{text:cloze:Text}}", context))
    }

    @Test
    fun clozeOnlyListsActiveAnswers() {
        val context = CardRenderContext(fields = mapOf("Text" to "{{c1::a}} {{c2::b}} {{c1::c}}"), clozeOrdinal = 1)
        assertEquals("a, c", CardTemplateRenderer.renderQuestion("{{cloze-only:Text}}", context))
    }

    @Test
    fun referencedFieldNamesIncludeSectionsAndFilters() {
        assertEquals(
            setOf("FrontSide", "Front", "Back", "Text"),
            CardTemplateRenderer.referencedFieldNames("{{FrontSide}}{{#Front}}{{text:Back}}{{/Front}}{{^Text}}{{/Text}}"),
        )
    }
}
