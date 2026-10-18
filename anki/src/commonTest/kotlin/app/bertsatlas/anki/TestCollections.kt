package app.bertsatlas.anki

import app.bertsatlas.anki.collection.AnkiCollection
import app.bertsatlas.anki.model.CardTemplate
import app.bertsatlas.anki.model.Notetype
import app.bertsatlas.anki.model.NotetypeField
import app.bertsatlas.anki.model.NotetypeKind
import kotlin.random.Random

fun freshDirectory(label: String): String = temporaryDirectory().trimEnd('/') + "/anki-$label-${Random.nextLong().toULong()}"

fun fixture(name: String): String = "$FIXTURES_DIRECTORY/$name"

fun createCollection(directory: String): AnkiCollection = AnkiCollection.create("$directory/collection.anki2".also { app.bertsatlas.anki.io.Files.createDirectories(directory) })

fun basicNotetype() = Notetype(
    id = 0,
    name = "Bert Basic",
    kind = NotetypeKind.Standard,
    fields = listOf(NotetypeField("Question", 0), NotetypeField("Answer", 1), NotetypeField("Extra", 2)),
    templates = listOf(
        CardTemplate("Forward", 0, "{{Question}}", "{{FrontSide}}<hr id=answer>{{Answer}}"),
        CardTemplate("Reverse", 1, "{{#Extra}}{{Answer}}{{/Extra}}", "{{FrontSide}}<hr id=answer>{{Question}}"),
    ),
    css = ".card { color: black; }",
)

fun clozeNotetype() = Notetype(
    id = 0,
    name = "Bert Cloze",
    kind = NotetypeKind.Cloze,
    fields = listOf(NotetypeField("Text", 0), NotetypeField("Extra", 1)),
    templates = listOf(CardTemplate("Cloze", 0, "{{cloze:Text}}", "{{cloze:Text}}<br>{{Extra}}")),
    css = "",
)
