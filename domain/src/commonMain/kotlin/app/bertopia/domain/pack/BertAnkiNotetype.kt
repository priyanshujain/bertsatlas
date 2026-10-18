package app.bertopia.domain.pack

import app.bertopia.anki.model.CardTemplate
import app.bertopia.anki.model.Notetype
import app.bertopia.anki.model.NotetypeField
import app.bertopia.anki.model.NotetypeKind

fun BertNoteType.toAnkiNotetype(): Notetype = Notetype(
    id = id,
    name = name,
    kind = when (kind) {
        NoteTypeKind.Standard -> NotetypeKind.Standard
        NoteTypeKind.Cloze -> NotetypeKind.Cloze
    },
    fields = fields.mapIndexed { ordinal, field -> NotetypeField(field, ordinal) },
    templates = templates.mapIndexed { ordinal, template ->
        CardTemplate(template.name, ordinal, template.questionFormat, template.answerFormat)
    },
    css = css,
    sortFieldIndex = sortFieldIndex,
)
