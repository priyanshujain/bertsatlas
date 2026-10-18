package app.bertsatlas.cardrender

import app.bertsatlas.anki.template.CardRenderContext
import app.bertsatlas.anki.template.CardTemplateRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AnkiTemplateConsistencyTest {
    private val clozeTemplate = "{{cloze:Text}}"
    private val occlusionQuestion = "<div class=occlusion>{{cloze:Masks}}</div>\n{{Image}}"
    private val occlusionAnswer = "<div class=occlusion>{{cloze:Masks}}</div>\n{{Image}}\n{{#Extra}}<div class=extra>{{Extra}}</div>{{/Extra}}"

    @Test
    fun gallerySamplesMatchTemplateRendererOutput() {
        val cloze = mapOf("Text" to GallerySamples.CLOZE_TEXT)
        assertEquals(GallerySamples.CLOZE_QUESTION_ONE, CardTemplateRenderer.renderQuestion(clozeTemplate, CardRenderContext(cloze, clozeOrdinal = 1)))
        assertEquals(GallerySamples.CLOZE_QUESTION_TWO, CardTemplateRenderer.renderQuestion(clozeTemplate, CardRenderContext(cloze, clozeOrdinal = 2)))
        assertEquals(GallerySamples.CLOZE_ANSWER_ONE, CardTemplateRenderer.renderAnswer(clozeTemplate, CardRenderContext(cloze, clozeOrdinal = 1), ""))

        val occlusion = mapOf("Masks" to GallerySamples.OCCLUSION_MASKS, "Image" to "<img src=\"cell.png\">", "Extra" to "Nucleus")
        assertEquals(GallerySamples.OCCLUSION_QUESTION_ONE, CardTemplateRenderer.renderQuestion(occlusionQuestion, CardRenderContext(occlusion, clozeOrdinal = 1)))
        assertEquals(GallerySamples.OCCLUSION_ANSWER_ONE, CardTemplateRenderer.renderAnswer(occlusionAnswer, CardRenderContext(occlusion, clozeOrdinal = 1), ""))
    }

    @Test
    fun renderedClozeCardBecomesStyledDocument() {
        val html = CardTemplateRenderer.renderQuestion(clozeTemplate, CardRenderContext(mapOf("Text" to "A {{c1::cat::animal}} sat"), clozeOrdinal = 1))
        val text = (CardDocumentBuilder.build(html).blocks.single() as CardBlock.Paragraph).inlines.filterIsInstance<CardInline.Text>()
        assertEquals(CardInline.Text("[animal]", InlineStyle(cloze = ClozeRole.Hidden)), text[1])
    }

    @Test
    fun renderedOcclusionCardHasOneHiddenShapePerOrdinal() {
        val occlusion = mapOf("Masks" to GallerySamples.OCCLUSION_MASKS, "Image" to "<img src=\"cell.png\">")
        (1..4).forEach { ordinal ->
            val html = CardTemplateRenderer.renderQuestion(occlusionQuestion, CardRenderContext(occlusion, clozeOrdinal = ordinal))
            val block = assertIs<CardBlock.Occlusion>(CardDocumentBuilder.build(html).blocks.single())
            assertEquals(listOf(ordinal), block.shapes.filter { it.state == OcclusionState.Hidden }.map { it.ordinal })
            assertEquals(3, block.shapes.count { it.state == OcclusionState.InactiveMasked })
        }
    }
}
