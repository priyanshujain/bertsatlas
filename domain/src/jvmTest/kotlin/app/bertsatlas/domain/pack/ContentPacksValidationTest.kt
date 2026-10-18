package app.bertsatlas.domain.pack

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ContentPacksValidationTest {
    private val packsDir = File("../content/packs")

    @Test
    fun bundledPackSourcesValidateClean() {
        listOf("flags", "capitals").forEach { packId ->
            val packDir = File(packsDir, packId)
            val manifest = PackManifestCodec.decodeJson(File(packDir, "pack.json").readText())
            val notes = readNotes(File(packDir, "cards.json"))
            val media = manifest.mediaDir?.let { File(packDir, it).list()?.toSet() }.orEmpty()

            val issues = PackValidator.validate(PackContents(ManifestReading.Found(manifest, ""), notes, media))

            assertEquals(emptyList(), issues, packId)
            assertEquals(195, notes.size, packId)
            assertTrue(manifest.stamps.all { it.decodeRule() != null }, packId)
            notes.forEach { note ->
                val reading = BertMetaCodec.readFor(BertNoteTypes.byName(note.noteTypeName)!!, note.fields.getValue("BertMeta"))
                assertIs<BertMetaReading.Valid<*>>(reading, note.guid)
            }
        }
    }

    private fun readNotes(file: File): List<PackNote> =
        Json.parseToJsonElement(file.readText()).jsonObject.getValue("cards").jsonArray.map { element ->
            val card = element.jsonObject
            PackNote(
                guid = card.getValue("guid").jsonPrimitive.content,
                noteTypeName = card.getValue("noteType").jsonPrimitive.content,
                fields = card.getValue("fields").jsonObject.mapValues { (_, value) ->
                    if (value is JsonObject) value.toString() else (value as JsonPrimitive).content
                },
                tags = card.getValue("tags").jsonArray.map { it.jsonPrimitive.content },
            )
        }
}
