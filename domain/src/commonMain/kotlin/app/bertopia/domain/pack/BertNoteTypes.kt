package app.bertopia.domain.pack

enum class NoteTypeKind { Standard, Cloze }

data class CardTemplateDefinition(
    val name: String,
    val questionFormat: String,
    val answerFormat: String,
)

data class BertNoteType(
    val id: Long,
    val name: String,
    val version: Int,
    val kind: NoteTypeKind,
    val fields: List<String>,
    val sortFieldIndex: Int,
    val requiredFields: Set<String>,
    val templates: List<CardTemplateDefinition>,
    val css: String = BertNoteTypes.CARD_CSS,
)

object BertNoteTypes {
    const val BERT_META_FIELD = "BertMeta"

    const val CARD_CSS = ".card { font-family: -apple-system, Roboto, sans-serif; font-size: 22px; text-align: center; color: #1d2b2a; background: #fbf8f1; }\n" +
        ".prompt { font-size: 18px; color: #4f5e5c; margin-top: 12px; }\n" +
        ".list { white-space: pre-line; }\n" +
        ".hint, .extra, .fact { font-size: 16px; color: #4f5e5c; margin-top: 16px; }\n" +
        ".cloze { font-weight: bold; color: #27635b; }\n" +
        "img { max-width: 100%; max-height: 320px; }"

    private const val ANSWER_DIVIDER = "{{FrontSide}}\n<hr id=answer>\n"
    private const val FACT_BLOCK = "{{#Fact}}<div class=fact>{{Fact}}</div>{{/Fact}}"
    private const val HINT_BLOCK = "{{#Hint}}<div class=hint>{{hint:Hint}}</div>{{/Hint}}"

    val Basic = BertNoteType(
        id = 1_760_000_000_001,
        name = "Bert Basic",
        version = 1,
        kind = NoteTypeKind.Standard,
        fields = listOf("Question", "Answer", "Aliases", "Hint", "Fact", "Source", BERT_META_FIELD),
        sortFieldIndex = 0,
        requiredFields = setOf("Question", "Answer"),
        templates = listOf(
            CardTemplateDefinition(
                name = "Question",
                questionFormat = "{{Question}}\n$HINT_BLOCK",
                answerFormat = "$ANSWER_DIVIDER{{Answer}}\n$FACT_BLOCK",
            ),
        ),
    )

    val TwoWay = BertNoteType(
        id = 1_760_000_000_002,
        name = "Bert Two Way",
        version = 1,
        kind = NoteTypeKind.Standard,
        fields = listOf("Side A", "Side B", "Aliases", "Hint", "Fact", "Source", BERT_META_FIELD),
        sortFieldIndex = 0,
        requiredFields = setOf("Side A", "Side B"),
        templates = listOf(
            CardTemplateDefinition(
                name = "A to B",
                questionFormat = "{{Side A}}\n$HINT_BLOCK",
                answerFormat = "$ANSWER_DIVIDER{{Side B}}\n$FACT_BLOCK",
            ),
            CardTemplateDefinition(
                name = "B to A",
                questionFormat = "{{Side B}}",
                answerFormat = "$ANSWER_DIVIDER{{Side A}}\n$FACT_BLOCK",
            ),
        ),
    )

    val Cloze = BertNoteType(
        id = 1_760_000_000_003,
        name = "Bert Cloze",
        version = 1,
        kind = NoteTypeKind.Cloze,
        fields = listOf("Text", "Extra", "Hint", "Fact", "Source", BERT_META_FIELD),
        sortFieldIndex = 0,
        requiredFields = setOf("Text"),
        templates = listOf(
            CardTemplateDefinition(
                name = "Cloze",
                questionFormat = "{{cloze:Text}}\n$HINT_BLOCK",
                answerFormat = "{{cloze:Text}}\n{{#Extra}}<div class=extra>{{Extra}}</div>{{/Extra}}\n$FACT_BLOCK",
            ),
        ),
    )

    val Typed = BertNoteType(
        id = 1_760_000_000_004,
        name = "Bert Typed",
        version = 1,
        kind = NoteTypeKind.Standard,
        fields = listOf("Question", "Answer", "Aliases", "Hint", "Fact", "Source", BERT_META_FIELD),
        sortFieldIndex = 0,
        requiredFields = setOf("Question", "Answer"),
        templates = listOf(
            CardTemplateDefinition(
                name = "Type the answer",
                questionFormat = "{{Question}}\n$HINT_BLOCK\n{{type:Answer}}",
                answerFormat = "{{Question}}\n<hr id=answer>\n{{type:Answer}}\n$FACT_BLOCK",
            ),
        ),
    )

    val Image = BertNoteType(
        id = 1_760_000_000_005,
        name = "Bert Image",
        version = 1,
        kind = NoteTypeKind.Standard,
        fields = listOf("Image", "Answer", "Aliases", "Hint", "Fact", "Source", BERT_META_FIELD),
        sortFieldIndex = 1,
        requiredFields = setOf("Image", "Answer"),
        templates = listOf(
            CardTemplateDefinition(
                name = "Image",
                questionFormat = "{{Image}}\n$HINT_BLOCK",
                answerFormat = "$ANSWER_DIVIDER{{Answer}}\n$FACT_BLOCK",
            ),
        ),
    )

    val Occlusion = BertNoteType(
        id = 1_760_000_000_006,
        name = "Bert Occlusion",
        version = 1,
        kind = NoteTypeKind.Cloze,
        fields = listOf("Image", "Masks", "Header", "Extra", BERT_META_FIELD),
        sortFieldIndex = 2,
        requiredFields = setOf("Image", "Masks"),
        templates = listOf(
            CardTemplateDefinition(
                name = "Occlusion",
                questionFormat = "{{#Header}}<div>{{Header}}</div>{{/Header}}\n<div class=occlusion>{{cloze:Masks}}</div>\n{{Image}}",
                answerFormat = "{{#Header}}<div>{{Header}}</div>{{/Header}}\n<div class=occlusion>{{cloze:Masks}}</div>\n{{Image}}\n{{#Extra}}<div class=extra>{{Extra}}</div>{{/Extra}}",
            ),
        ),
    )

    val Flag = BertNoteType(
        id = 1_760_000_000_007,
        name = "Bert Flag",
        version = 1,
        kind = NoteTypeKind.Standard,
        fields = listOf("Country", "Flag", "Aliases", "Continent", "Hint", "Fact", "Source", BERT_META_FIELD),
        sortFieldIndex = 0,
        requiredFields = setOf("Country", "Flag"),
        templates = listOf(
            CardTemplateDefinition(
                name = "Flag to country",
                questionFormat = "{{Flag}}\n<div class=prompt>Which country flies this flag?</div>\n$HINT_BLOCK",
                answerFormat = "$ANSWER_DIVIDER{{Country}}\n$FACT_BLOCK",
            ),
        ),
    )

    val Capital = BertNoteType(
        id = 1_760_000_000_008,
        name = "Bert Capital",
        version = 1,
        kind = NoteTypeKind.Standard,
        fields = listOf("Country", "Capitals", "CapitalArrangement", "Aliases", "Continent", "Hint", "Fact", "Source", BERT_META_FIELD),
        sortFieldIndex = 0,
        requiredFields = setOf("Country", "Capitals"),
        templates = listOf(
            CardTemplateDefinition(
                name = "Country to capital",
                questionFormat = "<div class=prompt>What is the capital of</div>\n{{Country}}\n$HINT_BLOCK",
                answerFormat = "$ANSWER_DIVIDER<div class=list>{{Capitals}}</div>\n$FACT_BLOCK",
            ),
        ),
    )

    val Choice = BertNoteType(
        id = 1_760_000_000_009,
        name = "Bert Choice",
        version = 1,
        kind = NoteTypeKind.Standard,
        fields = listOf("Question", "Choices", "Correct", "Explanation", "Hint", "Source", BERT_META_FIELD),
        sortFieldIndex = 0,
        requiredFields = setOf("Question", "Choices", "Correct"),
        templates = listOf(
            CardTemplateDefinition(
                name = "Choice",
                questionFormat = "{{Question}}\n<div class=list>{{Choices}}</div>\n$HINT_BLOCK",
                answerFormat = "$ANSWER_DIVIDER{{Correct}}\n{{#Explanation}}<div class=extra>{{Explanation}}</div>{{/Explanation}}",
            ),
        ),
    )

    val all: List<BertNoteType> = listOf(Basic, TwoWay, Cloze, Typed, Image, Occlusion, Flag, Capital, Choice)

    fun byName(name: String): BertNoteType? = all.firstOrNull { it.name == name }

    fun byId(id: Long): BertNoteType? = all.firstOrNull { it.id == id }
}
