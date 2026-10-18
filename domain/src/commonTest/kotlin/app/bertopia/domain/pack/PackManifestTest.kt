package app.bertopia.domain.pack

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class PackManifestTest {
    private val manifestJson = """
        {"packId":"flags","version":"1.0.0","official":true,"title":"Flags","futureKey":{"x":1},
         "stamps":[
           {"id":"world","title":"Around the world","rule":{"kind":"correctAcrossFieldValues","field":"Continent","values":["Asia"]}},
           {"id":"later","title":"Later","rule":{"kind":"somethingNew"}}
         ]}
    """.trimIndent()

    @Test
    fun parsesIgnoringUnknownKeys() {
        val manifest = PackManifestCodec.decodeJson(manifestJson)
        assertEquals("flags", manifest.packId)
        assertEquals(StampRule.CorrectAcrossFieldValues("Continent", listOf("Asia")), manifest.stamps[0].decodeRule())
        assertNull(manifest.stamps[1].decodeRule())
    }

    @Test
    fun deckDescriptionRoundTripKeepsVisibleText() {
        val manifest = PackManifestCodec.decodeJson(manifestJson)
        val description = PackManifestCodec.writeDeckDescription(manifest, "All the world's flags.")
        val reading = assertIs<ManifestReading.Found>(PackManifestCodec.readDeckDescription(description))
        assertEquals(manifest, reading.manifest)
        assertEquals("All the world's flags.", reading.visibleDescription)
    }

    @Test
    fun brokenOrMissingManifestDegrades() {
        assertIs<ManifestReading.Missing>(PackManifestCodec.readDeckDescription("Just a deck"))
        val broken = assertIs<ManifestReading.Unreadable>(PackManifestCodec.readDeckDescription("Hi\n<!--bert\n{\"packId\": -->"))
        assertEquals("Hi", broken.visibleDescription)
    }
}
