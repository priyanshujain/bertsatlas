package app.bertsatlas.cli

import app.bertsatlas.domain.editor.CardStyles

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object StylesCommands {
    fun run(context: Context): Output = when (val action = context.arguments.positional(0, "list|schema")) {
        "list" -> list()
        "schema" -> schema(context.arguments.positionals.getOrNull(1))
        else -> throw CliFailure.usage("unknown styles command '$action'")
    }

    private fun list(): Output {
        val styles = StyleSchemas.all.map(StyleSchemas::summary)
        val text = styles.joinToString("\n\n") { style ->
            "${style.string("style")}  ${style.string("description")}\n  fields: " +
                (style["fields"] as JsonArray).joinToString { field -> field.toString().trim('"') + if ((style["requiredFields"] as JsonArray).contains(field)) "*" else "" }
        } + "\n\n* required"
        return Output(buildJsonObject { put("styles", JsonArray(styles)) }, text)
    }

    private fun schema(name: String?): Output {
        if (name != null) {
            val schema = StyleSchemas.schema(CardStyles.require(name))
            return Output(schema, schema.pretty())
        }
        val schema = buildJsonObject {
            put("\$schema", "https://json-schema.org/draft/2020-12/schema")
            put("title", "Bert's Atlas card input")
            put("description", "A card, an array of cards, or {\"cards\": [...]}. Each card picks one style.")
            put("oneOf", JsonArray(StyleSchemas.all.map(StyleSchemas::schema).map { JsonObject(it - "\$schema") }))
        }
        return Output(schema, schema.pretty())
    }
}
