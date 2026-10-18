package app.bertsatlas.anki.collection

import app.bertsatlas.anki.model.Card
import app.bertsatlas.anki.model.NotetypeKind
import app.bertsatlas.anki.template.CardRenderContext
import app.bertsatlas.anki.template.CardTemplateRenderer
import app.bertsatlas.anki.template.RenderedCard
import app.bertsatlas.anki.template.TemplateException

fun AnkiCollection.renderCard(card: Card): RenderedCard? {
    val note = note(card.noteId) ?: return null
    val notetype = notetype(note.notetypeId) ?: return null
    val template = when (notetype.kind) {
        NotetypeKind.Cloze -> notetype.templates.firstOrNull()
        NotetypeKind.Standard -> notetype.templates.firstOrNull { it.ordinal == card.ordinal }
    } ?: return null
    val context = CardRenderContext(
        fields = notetype.fieldNames.zip(note.fields).toMap(),
        clozeOrdinal = card.ordinal + 1,
        tags = note.tags,
        deckName = deck(card.deckId)?.name.orEmpty(),
        notetypeName = notetype.name,
        cardTemplateName = template.name,
    )
    return try {
        CardTemplateRenderer.render(template.questionFormat, template.answerFormat, context)
    } catch (failure: TemplateException) {
        null
    }
}
