package app.bertopia.domain.editor

import app.bertopia.anki.model.Notetype
import app.bertopia.anki.model.NotetypeField
import app.bertopia.anki.model.NotetypeKind
import app.bertopia.domain.pack.BertNoteTypes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CardFormsTest {
    private val ankiWords = listOf("note type", "notetype", "field", "template", "deck", "preset", "anki")

    @Test
    fun everyStyleHasAFriendlyFormMatchingItsFields() {
        CardStyles.all.forEach { style ->
            val form = CardForms.forStyle(style)
            assertEquals(style.fields - BertNoteTypes.BERT_META_FIELD, form.fields.map { it.name }, style.name)
            assertEquals(style.requiredFields, form.fields.filter { it.required }.map { it.name }.toSet(), style.name)
            (listOf(form.title, form.description) + form.fields.flatMap { listOfNotNull(it.label, it.help) }).forEach { text ->
                ankiWords.forEach { word -> assertTrue(word !in text.lowercase(), "$text mentions $word") }
            }
        }
    }

    @Test
    fun choicesCoverTheBuiltInValues() {
        assertEquals(6, CardForms.continents.size)
        assertEquals(7, CardForms.capitalArrangements.size)
    }

    @Test
    fun unknownCardTypesGetAGenericForm() {
        val notetype = Notetype(
            id = 1, name = "Japanese (recognition)", kind = NotetypeKind.Standard,
            fields = listOf("Expression", "MeaningEnglish", "kana_reading", "SRS ID").mapIndexed { index, name -> NotetypeField(name, index) },
            templates = emptyList(), css = "",
        )
        val form = CardForms.forNotetype(notetype)
        assertTrue(form.isGeneric)
        assertEquals(listOf("Expression", "Meaning english", "Kana reading", "SRS ID"), form.fields.map { it.label })
        assertEquals(listOf(true, false, false, false), form.fields.map { it.required })
    }

    @Test
    fun imageValuesRoundTrip() {
        assertEquals("cell.png", CardForms.imageName(CardForms.imageValue("cell.png")))
    }
}
