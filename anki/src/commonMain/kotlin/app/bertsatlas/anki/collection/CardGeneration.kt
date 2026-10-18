package app.bertsatlas.anki.collection

import app.bertsatlas.anki.model.Notetype
import app.bertsatlas.anki.model.NotetypeKind
import app.bertsatlas.anki.template.CardRenderContext
import app.bertsatlas.anki.template.CardTemplateRenderer
import app.bertsatlas.anki.template.Cloze
import app.bertsatlas.anki.template.clozeFieldNames

object CardGeneration {
    fun ordinalsWithContent(notetype: Notetype, fields: List<String>): List<Int> {
        val values = notetype.fieldNames.zip(fields.padded(notetype.fields.size)).toMap()
        val ordinals = when (notetype.kind) {
            NotetypeKind.Cloze -> clozeOrdinals(notetype, values)
            NotetypeKind.Standard -> notetype.templates.filter { template -> hasQuestionContent(template.questionFormat, values) }.map { it.ordinal }
        }
        return ordinals.ifEmpty { listOf(0) }
    }

    private fun clozeOrdinals(notetype: Notetype, values: Map<String, String>): List<Int> {
        val template = notetype.templates.firstOrNull() ?: return emptyList()
        return CardTemplateRenderer.clozeFieldNames(template.questionFormat)
            .flatMap { name -> Cloze.ordinals(values[name].orEmpty()) }
            .filter { it >= 1 }
            .map { it - 1 }
            .distinct()
            .sorted()
    }

    private fun hasQuestionContent(questionFormat: String, values: Map<String, String>): Boolean {
        val withContent = CardTemplateRenderer.renderQuestion(questionFormat, CardRenderContext(values))
        val withoutContent = CardTemplateRenderer.renderQuestion(questionFormat, CardRenderContext(values.mapValues { "" }))
        return withContent != withoutContent
    }
}

internal fun List<String>.padded(size: Int): List<String> = take(size) + List(maxOf(0, size - this.size)) { "" }
