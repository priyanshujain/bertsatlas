package app.bertopia.domain.editor

import app.bertopia.anki.model.Notetype
import app.bertopia.domain.pack.BertNoteType
import app.bertopia.domain.pack.BertNoteTypes

enum class FormFieldKind { Text, RichText, Cloze, Image, List, Choice, Masks }

data class FormChoice(val value: String, val label: String)

data class FormField(
    val name: String,
    val label: String,
    val kind: FormFieldKind,
    val required: Boolean,
    val help: String? = null,
    val choices: List<FormChoice> = emptyList(),
)

data class CardForm(
    val styleKey: String?,
    val styleName: String,
    val title: String,
    val description: String,
    val fields: List<FormField>,
) {
    val isGeneric: Boolean get() = styleKey == null

    fun field(name: String): FormField? = fields.firstOrNull { it.name == name }

    fun missingRequiredFields(values: Map<String, String>): List<FormField> =
        fields.filter { it.required && values[it.name].isNullOrBlank() }
}

object CardStyles {
    private val keys = linkedMapOf(
        BertNoteTypes.Basic to "basic",
        BertNoteTypes.TwoWay to "two-way",
        BertNoteTypes.Cloze to "cloze",
        BertNoteTypes.Typed to "typed",
        BertNoteTypes.Image to "image",
        BertNoteTypes.Occlusion to "occlusion",
        BertNoteTypes.Flag to "flag",
        BertNoteTypes.Capital to "capital",
        BertNoteTypes.Choice to "choice",
    )

    private val titles = mapOf(
        "basic" to "Question and answer",
        "two-way" to "Two way",
        "cloze" to "Fill the blank",
        "typed" to "Type the answer",
        "image" to "Image quiz",
        "occlusion" to "Image occlusion",
        "flag" to "Flag",
        "capital" to "Capital",
        "choice" to "Multiple choice",
    )

    private val descriptions = mapOf(
        "basic" to "Question and answer",
        "two-way" to "Two way: asks Side A for Side B and Side B for Side A",
        "cloze" to "Fill the blank: wrap answers as {{c1::answer}} or {{c1::answer::hint}} in Text",
        "typed" to "Type the answer",
        "image" to "Image quiz: Image holds <img src=\"file.png\"> from the pack media",
        "occlusion" to "Image occlusion: Image plus Masks shapes",
        "flag" to "Flag: Flag holds <img src=\"xx.png\">, Country is the answer",
        "capital" to "Capital: Capitals is a newline separated list",
        "choice" to "Multiple choice: Choices holds 2 to 5 lines, Correct is the exact text of the right one",
    )

    val all: List<BertNoteType> = keys.keys.toList()

    fun key(style: BertNoteType): String = keys.getValue(style)

    fun title(style: BertNoteType): String = titles.getValue(key(style))

    fun description(style: BertNoteType): String = descriptions.getValue(key(style))

    fun resolve(name: String): BertNoteType? =
        keys.entries.firstOrNull { it.value == name.lowercase() }?.key ?: BertNoteTypes.byName(name)

    fun require(name: String): BertNoteType = resolve(name)
        ?: throw EditorException.invalid("unknown card style '$name'. Styles: ${keys.values.joinToString()}")
}

object CardForms {
    val continents = listOf("Africa", "Asia", "Europe", "North America", "Oceania", "South America").map { FormChoice(it, it) }

    val capitalArrangements = listOf(
        FormChoice("single", "One capital"),
        FormChoice("multiple", "Several capitals"),
        FormChoice("official_and_seat", "Official capital and seat of government"),
        FormChoice("constitutional_and_seat", "Constitutional capital and seat of government"),
        FormChoice("claimed_and_administrative", "Claimed and administrative capitals"),
        FormChoice("disputed", "Disputed"),
        FormChoice("transition", "Moving to a new capital"),
    )

    private val aliases = optional("Aliases", "Also accepted", FormFieldKind.List, "Other answers that count as correct, one per line")
    private val hint = optional("Hint", "Hint", FormFieldKind.Text, "A clue shown when asked for")
    private val fact = optional("Fact", "Fun fact", FormFieldKind.RichText, "Shown after answering")
    private val source = optional("Source", "Sources", FormFieldKind.List, "Where the fact comes from, one per line")
    private val continent = FormField("Continent", "Continent", FormFieldKind.Choice, required = false, help = "Used as a clue", choices = continents)

    private val styleFields: Map<BertNoteType, List<FormField>> = mapOf(
        BertNoteTypes.Basic to listOf(
            required("Question", "Question", FormFieldKind.RichText),
            required("Answer", "Answer", FormFieldKind.Text, "Keep it short so it works in quizzes"),
            aliases, hint, fact, source,
        ),
        BertNoteTypes.TwoWay to listOf(
            required("Side A", "Side A", FormFieldKind.Text, "Asked for Side B, and the other way round"),
            required("Side B", "Side B", FormFieldKind.Text),
            aliases, hint, fact, source,
        ),
        BertNoteTypes.Cloze to listOf(
            required("Text", "Text with blanks", FormFieldKind.Cloze, "Wrap each blank as {{c1::answer}}. Add a hint with {{c1::answer::hint}}"),
            optional("Extra", "Extra", FormFieldKind.RichText, "Shown after answering"),
            hint, fact, source,
        ),
        BertNoteTypes.Typed to listOf(
            required("Question", "Question", FormFieldKind.RichText),
            required("Answer", "Answer to type", FormFieldKind.Text),
            aliases, hint, fact, source,
        ),
        BertNoteTypes.Image to listOf(
            required("Image", "Image", FormFieldKind.Image),
            required("Answer", "Answer", FormFieldKind.Text),
            aliases, hint, fact, source,
        ),
        BertNoteTypes.Occlusion to listOf(
            required("Image", "Image", FormFieldKind.Image),
            required("Masks", "Hidden areas", FormFieldKind.Masks, "Shapes drawn over the image, one card per numbered area"),
            optional("Header", "Title", FormFieldKind.Text),
            optional("Extra", "Extra", FormFieldKind.RichText, "Shown after answering"),
        ),
        BertNoteTypes.Flag to listOf(
            required("Country", "Country", FormFieldKind.Text),
            required("Flag", "Flag", FormFieldKind.Image),
            aliases, continent, hint, fact, source,
        ),
        BertNoteTypes.Capital to listOf(
            required("Country", "Country", FormFieldKind.Text),
            required("Capitals", "Capitals", FormFieldKind.List, "One per line"),
            FormField("CapitalArrangement", "How the capitals work", FormFieldKind.Choice, required = false, choices = capitalArrangements),
            aliases, continent, hint, fact, source,
        ),
        BertNoteTypes.Choice to listOf(
            required("Question", "Question", FormFieldKind.RichText),
            required("Choices", "Choices", FormFieldKind.List, "2 to 5 options, one per line"),
            required("Correct", "Correct choice", FormFieldKind.Text, "Exactly the text of the right option"),
            optional("Explanation", "Explanation", FormFieldKind.RichText, "Shown after answering"),
            hint, source,
        ),
    )

    fun forStyle(style: BertNoteType): CardForm = CardForm(
        styleKey = CardStyles.key(style),
        styleName = style.name,
        title = CardStyles.title(style),
        description = CardStyles.description(style),
        fields = styleFields.getValue(style),
    )

    fun forNotetype(notetype: Notetype): CardForm =
        BertNoteTypes.byName(notetype.name)?.let(::forStyle) ?: generic(notetype)

    fun generic(notetype: Notetype): CardForm = CardForm(
        styleKey = null,
        styleName = notetype.name,
        title = "Card",
        description = "Fields from the original deck",
        fields = notetype.fieldNames.mapIndexed { index, name ->
            FormField(name, friendlyLabel(name), FormFieldKind.RichText, required = index == 0)
        },
    )

    fun friendlyLabel(fieldName: String): String {
        val words = fieldName
            .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
            .replace(Regex("[_\\-]+"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
        if (words.isEmpty()) return fieldName
        return words.mapIndexed { index, word ->
            when {
                word.length > 1 && word.all { it.isUpperCase() || it.isDigit() } -> word
                index == 0 -> word.lowercase().replaceFirstChar { it.uppercase() }
                else -> word.lowercase()
            }
        }.joinToString(" ")
    }

    fun imageValue(mediaName: String): String = "<img src=\"$mediaName\">"

    fun imageName(fieldValue: String): String? = Regex("""<img[^>]*src\s*=\s*["']?([^"'>]+)""", RegexOption.IGNORE_CASE).find(fieldValue)?.groupValues?.get(1)

    private fun required(name: String, label: String, kind: FormFieldKind, help: String? = null) = FormField(name, label, kind, required = true, help = help)

    private fun optional(name: String, label: String, kind: FormFieldKind, help: String? = null) = FormField(name, label, kind, required = false, help = help)
}
