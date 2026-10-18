package app.bertsatlas.domain.pack

import app.bertsatlas.anki.template.CardRenderContext
import app.bertsatlas.anki.template.CardTemplateRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BertNoteTypesTest {
    @Test
    fun idsAndNamesAreUnique() {
        assertEquals(8, BertNoteTypes.all.map { it.id }.toSet().size)
        assertEquals(8, BertNoteTypes.all.map { it.name }.toSet().size)
    }

    @Test
    fun everyTemplateReferencesOnlyItsFieldsAndFrontUsesAField() {
        BertNoteTypes.all.forEach { type ->
            assertTrue(type.fields.last() == BertNoteTypes.BERT_META_FIELD, type.name)
            assertTrue(type.requiredFields.all { it in type.fields }, type.name)
            type.templates.forEach { template ->
                val front = CardTemplateRenderer.referencedFieldNames(template.questionFormat)
                val back = CardTemplateRenderer.referencedFieldNames(template.answerFormat)
                assertTrue(front.isNotEmpty(), "${type.name} front")
                assertTrue((front + back).all { it in type.fields || it == "FrontSide" }, "${type.name} ${front + back}")
            }
        }
    }

    @Test
    fun flagCardShowsFlagThenCountry() {
        val template = BertNoteTypes.Flag.templates.single()
        val fields = BertNoteTypes.Flag.fields.associateWith { "" } +
            mapOf("Country" to "Japan", "Flag" to "<img src=\"jp.png\">", "Fact" to "Islands.")
        val card = CardTemplateRenderer.render(template.questionFormat, template.answerFormat, CardRenderContext(fields = fields))
        assertTrue("jp.png" in card.question)
        assertFalse("Japan" in card.question)
        assertTrue("Japan" in card.answer && "Islands." in card.answer)
    }

    @Test
    fun capitalCardAsksForTheCapitalOfTheCountry() {
        val template = BertNoteTypes.Capital.templates.single()
        val fields = BertNoteTypes.Capital.fields.associateWith { "" } +
            mapOf("Country" to "Bolivia", "Capitals" to "La Paz\nSucre")
        val card = CardTemplateRenderer.render(template.questionFormat, template.answerFormat, CardRenderContext(fields = fields))
        assertTrue("capital of" in card.question && "Bolivia" in card.question)
        assertFalse("Sucre" in card.question)
        assertTrue("La Paz\nSucre" in card.answer)
    }

    @Test
    fun twoWayMakesTwoCardsAndClozeTypesAreCloze() {
        assertEquals(2, BertNoteTypes.TwoWay.templates.size)
        assertEquals(NoteTypeKind.Cloze, BertNoteTypes.Cloze.kind)
        assertEquals(NoteTypeKind.Cloze, BertNoteTypes.Occlusion.kind)
        assertTrue("{{type:Answer}}" in BertNoteTypes.Typed.templates.single().questionFormat)
    }

    @Test
    fun clozeCardHidesTheDeletion() {
        val template = BertNoteTypes.Cloze.templates.single()
        val fields = BertNoteTypes.Cloze.fields.associateWith { "" } + mapOf("Text" to "The capital of Peru is {{c1::Lima}}.")
        val card = CardTemplateRenderer.render(template.questionFormat, template.answerFormat, CardRenderContext(fields = fields, clozeOrdinal = 1))
        assertFalse(">Lima<" in card.question)
        assertTrue("[...]" in card.question)
        assertTrue(">Lima<" in card.answer)
    }
}
