package app.bertsatlas.domain.editor

import app.bertsatlas.anki.collection.CardGeneration
import app.bertsatlas.anki.model.Notetype
import app.bertsatlas.anki.model.NotetypeKind
import app.bertsatlas.anki.template.CardRenderContext
import app.bertsatlas.anki.template.CardTemplateRenderer
import app.bertsatlas.anki.template.TemplateException
import app.bertsatlas.domain.pack.BertNoteType
import app.bertsatlas.domain.pack.toAnkiNotetype

data class CardFacePreview(val ordinal: Int, val name: String, val frontHtml: String, val backHtml: String)

object CardPreviews {
    fun forDraft(style: BertNoteType, fields: Map<String, String>, tags: List<String> = emptyList()): List<CardFacePreview> =
        forNotetype(style.toAnkiNotetype(), fields, tags)

    fun forNotetype(notetype: Notetype, fields: Map<String, String>, tags: List<String> = emptyList()): List<CardFacePreview> {
        val values = notetype.fieldNames.map { fields[it].orEmpty() }
        return CardGeneration.ordinalsWithContent(notetype, values).mapNotNull { ordinal ->
            val template = when (notetype.kind) {
                NotetypeKind.Cloze -> notetype.templates.firstOrNull()
                NotetypeKind.Standard -> notetype.templates.firstOrNull { it.ordinal == ordinal }
            } ?: return@mapNotNull null
            val context = CardRenderContext(
                fields = notetype.fieldNames.zip(values).toMap(),
                clozeOrdinal = ordinal + 1,
                tags = tags,
                notetypeName = notetype.name,
                cardTemplateName = template.name,
            )
            val rendered = try {
                CardTemplateRenderer.render(template.questionFormat, template.answerFormat, context)
            } catch (failure: TemplateException) {
                return@mapNotNull null
            }
            val name = if (notetype.kind == NotetypeKind.Cloze) "Blank ${ordinal + 1}" else template.name
            CardFacePreview(ordinal, name, rendered.question, rendered.answer)
        }
    }
}
