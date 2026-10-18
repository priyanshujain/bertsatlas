package app.bertsatlas.domain.pack

import app.bertsatlas.domain.taxonomy.BundledTaxonomy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PackIdsTest {
    private val validPackId = Regex("^[a-z0-9][a-z0-9-]*$")

    @Test
    fun foldsLatinAccentsIntoAsciiSlugs() {
        assertEquals("unicode-strasse", PackIds.slugFor("Ünïcödé Straße 토픽"))
        assertEquals("topik-i", PackIds.slugFor("TOPIK-I"))
    }

    @Test
    fun titlesWithoutLatinLettersGetAStableHashedId() {
        val id = PackIds.slugFor("토픽")
        assertTrue(validPackId.matches(id), id)
        assertTrue(id.startsWith("pack-"))
        assertEquals(id, PackIds.slugFor("토픽"))
        assertTrue(id != PackIds.slugFor("日本語"))
    }

    @Test
    fun uniqueIdSkipsTakenIds() {
        val taken = setOf("spanish", "spanish-2")
        assertEquals("spanish-3", PackIds.uniqueId("Spanish") { it in taken })
    }

    @Test
    fun taxonomyExplainsUnknownPlacements() {
        val taxonomy = BundledTaxonomy.taxonomy
        assertNull(taxonomy.placementProblem("programming", "dsa"))
        assertNotNull(taxonomy.placementProblem("programming", "nope")).let { assertTrue("databases" in it) }
        assertNotNull(taxonomy.placementProblem("nope", "dsa")).let { assertTrue("geography" in it) }
    }

    @Test
    fun mediaReferencesKeepSpacesInQuotedNames() {
        assertEquals(
            listOf("마음에 들다_auto_img.jpg", "a b.png", "plain.png", "x y.mp3"),
            PackValidator.mediaReferences("""<img src="마음에 들다_auto_img.jpg"><img src='a b.png'><img src=plain.png>[sound:x y.mp3]"""),
        )
    }
}
