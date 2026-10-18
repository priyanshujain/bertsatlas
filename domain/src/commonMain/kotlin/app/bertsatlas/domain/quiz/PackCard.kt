package app.bertsatlas.domain.quiz

import app.bertsatlas.anki.model.DECK_NAME_SEPARATOR
import app.bertsatlas.anki.model.Note
import app.bertsatlas.anki.model.Notetype
import app.bertsatlas.anki.model.NotetypeKind
import app.bertsatlas.anki.template.CardRenderContext
import app.bertsatlas.anki.template.CardTemplateRenderer
import app.bertsatlas.anki.template.Cloze
import app.bertsatlas.anki.template.clozeFieldNames
import app.bertsatlas.domain.pack.BertNoteTypes
import app.bertsatlas.domain.pack.BertTags
import app.bertsatlas.domain.pack.EditorialStatus

data class PackCard(
    val packId: String,
    val itemId: String,
    val noteGuid: String,
    val section: String?,
    val frontHtml: String,
    val backHtml: String,
    val frontText: String,
    val backText: String,
    val shortAnswer: String?,
    val aliases: List<String>,
    val hint: String?,
    val packClue: String?,
    val fact: String?,
    val sources: List<String>,
    val similarGroupIds: List<String>,
    val editorialStatus: EditorialStatus?,
)

object PackCardReader {
    const val MAX_UNKNOWN_ANSWER_LENGTH = 40
    const val MAX_BERT_ANSWER_LENGTH = 100

    private val specialFields = setOf(CardTemplateRenderer.FRONT_SIDE, "Tags", "Type", "Deck", "Subdeck", "Card")
    private val multiLineMarkup = Regex("(?i)<(br|div|p|li|ul|ol|table|tr|hr)\\b|\\n")
    private val mediaMarkup = Regex("(?i)<(img|audio|video|object|svg)\\b|\\[sound:|\\\\\\(|\\\\\\[|\\$\\$")
    private val answerDivider = Regex("(?i)<hr[^>]*id\\s*=\\s*[\"']?answer[\"']?[^>]*>")
    private val listSeparator = Regex("(?i)\\n|<br\\s*/?>|</div>|</p>")

    fun read(packId: String, notetype: Notetype, note: Note, ordinal: Int, deckName: String): PackCard {
        val fields = notetype.fieldNames.zip(note.fields + List(maxOf(0, notetype.fields.size - note.fields.size)) { "" }).toMap()
        val template = if (notetype.kind == NotetypeKind.Cloze) notetype.templates.first() else notetype.templates[ordinal]
        val clozeOrdinal = ordinal + 1
        val rendered = CardTemplateRenderer.render(
            template.questionFormat,
            template.answerFormat,
            CardRenderContext(
                fields = fields,
                clozeOrdinal = clozeOrdinal,
                tags = note.tags,
                deckName = deckName,
                notetypeName = notetype.name,
                cardTemplateName = template.name,
            ),
        )
        val isBert = BertNoteTypes.byName(notetype.name) != null
        val shortAnswer = if (isBert) {
            bertShortAnswer(notetype.name, fields, ordinal, clozeOrdinal)
        } else {
            unknownShortAnswer(notetype, template.questionFormat, template.answerFormat, fields, clozeOrdinal)
        }
        return PackCard(
            packId = packId,
            itemId = "${note.guid}:$ordinal",
            noteGuid = note.guid,
            section = deckName.split(DECK_NAME_SEPARATOR).drop(1).joinToString(DECK_NAME_SEPARATOR).ifEmpty { null },
            frontHtml = rendered.question,
            backHtml = rendered.answer,
            frontText = plainText(rendered.question),
            backText = answerSideText(rendered.answer, rendered.question),
            shortAnswer = shortAnswer,
            aliases = if (isBert) listLines(fields["Aliases"]) else emptyList(),
            hint = fieldText(fields, "Hint"),
            packClue = fieldText(fields, "Continent"),
            fact = fieldText(fields, "Fact"),
            sources = listLines(fields["Source"]),
            similarGroupIds = BertTags.similarGroupIds(note.tags),
            editorialStatus = BertTags.editorialStatus(note.tags),
        )
    }

    fun plainText(html: String): String =
        CardTemplateRenderer.renderQuestion("{{text:Value}}", CardRenderContext(mapOf("Value" to html.replace(hiddenElement, "")))).trim()

    private val hiddenElement = Regex("""<(\w+)[^>]*style\s*=\s*["'][^"']*display\s*:\s*none[^"']*["'][^>]*>.*?</\1\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    private fun answerSideText(answerHtml: String, questionHtml: String): String {
        val afterDivider = answerDivider.split(answerHtml, limit = 2).getOrNull(1)
        if (afterDivider != null) return plainText(afterDivider)
        val answerText = plainText(answerHtml)
        val questionText = plainText(questionHtml)
        return answerText.removePrefix(questionText).trim().ifEmpty { answerText }
    }

    private fun bertShortAnswer(notetypeName: String, fields: Map<String, String>, ordinal: Int, clozeOrdinal: Int): String? {
        val answerField = when (notetypeName) {
            BertNoteTypes.Basic.name, BertNoteTypes.Typed.name, BertNoteTypes.Image.name -> "Answer"
            BertNoteTypes.TwoWay.name -> if (ordinal == 0) "Side B" else "Side A"
            BertNoteTypes.Flag.name -> "Country"
            BertNoteTypes.Capital.name -> "Capitals"
            BertNoteTypes.Cloze.name -> return clozeAnswer(fields["Text"].orEmpty(), clozeOrdinal, MAX_BERT_ANSWER_LENGTH)
            else -> return null
        }
        return shortAnswerFrom(fields[answerField].orEmpty(), MAX_BERT_ANSWER_LENGTH)
    }

    private fun unknownShortAnswer(
        notetype: Notetype,
        questionFormat: String,
        answerFormat: String,
        fields: Map<String, String>,
        clozeOrdinal: Int,
    ): String? {
        if (notetype.kind == NotetypeKind.Cloze) {
            val clozeField = CardTemplateRenderer.clozeFieldNames(questionFormat).singleOrNull() ?: return null
            return clozeAnswer(fields[clozeField].orEmpty(), clozeOrdinal, MAX_UNKNOWN_ANSWER_LENGTH)
        }
        val answerOnlyFields = CardTemplateRenderer.referencedFieldNames(answerFormat) -
            CardTemplateRenderer.referencedFieldNames(questionFormat) -
            specialFields
        val nonEmptyAnswerFields = answerOnlyFields.filter { name -> !fields[name].isNullOrBlank() && name in fields }
        val answerField = nonEmptyAnswerFields.singleOrNull() ?: return null
        return shortAnswerFrom(fields.getValue(answerField), MAX_UNKNOWN_ANSWER_LENGTH)
    }

    private fun clozeAnswer(text: String, clozeOrdinal: Int, maxLength: Int): String? {
        val answers = Cloze.activeAnswers(text, clozeOrdinal).map { plainText(it) }.distinct()
        val answer = answers.singleOrNull() ?: return null
        return answer.takeIf { it.isNotEmpty() && it.length <= maxLength && !multiLineMarkup.containsMatchIn(it) }
    }

    private fun shortAnswerFrom(html: String, maxLength: Int): String? {
        val trimmed = html.trim().removeSuffix("<br>").trim()
        if (multiLineMarkup.containsMatchIn(trimmed) || mediaMarkup.containsMatchIn(trimmed)) return null
        val text = plainText(trimmed)
        return text.takeIf { it.isNotEmpty() && it.length <= maxLength }
    }

    private fun fieldText(fields: Map<String, String>, name: String): String? {
        val value = fields.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value ?: return null
        return plainText(value).ifBlank { null }
    }

    private fun listLines(html: String?): List<String> =
        html.orEmpty().split(listSeparator).map { plainText(it) }.filter { it.isNotBlank() }
}
