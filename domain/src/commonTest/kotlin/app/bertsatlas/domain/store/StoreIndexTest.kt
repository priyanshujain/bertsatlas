package app.bertsatlas.domain.store

import app.bertsatlas.domain.taxonomy.BundledTaxonomy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoreIndexTest {
    private val sample = """
        {
          "formatVersion": 1,
          "futureSection": {"anything": true},
          "categories": [
            {"id": "programming", "name": "Programming", "icon": "code", "accentColor": "#3E5C8A", "order": 1,
             "subcategories": [{"id": "databases", "name": "Databases", "order": 1}, {"id": "dsa", "name": "Data Structures and Algorithms", "order": 0}]},
            {"id": "geography", "name": "Geography", "order": 0, "subcategories": [{"id": "flags", "name": "Flags"}], "newField": 3}
          ],
          "featured": [{"item": {"kind": "pack", "id": "flags"}, "headline": "Every flag"}],
          "shelves": [
            {"id": "top", "title": "Top 2 this week", "scope": {"kind": "all"},
             "items": [{"kind": "pack", "id": "flags"}, {"kind": "mix", "id": "geography"}, {"kind": "podcast", "id": "x"}]},
            {"id": "geo", "title": "Top in Geography", "scope": {"kind": "category", "category": "geography"}, "items": []}
          ],
          "packs": [
            {"packId": "flags", "version": "1.0.0", "title": "Flags", "subtitle": "Every flag of the world",
             "category": "geography", "subcategory": "flags", "sha256": "ab", "url": "packs/flags-1.0.0.apkg",
             "sampleCards": [{"frontHtml": "<img src=\"fr.png\">", "backHtml": "France", "media": ["fr.png"]}],
             "rating": 5}
          ],
          "mixes": [{"mixId": "geography", "title": "Mixed Geography", "members": [{"packId": "flags"}, {"packId": "capitals"}]}]
        }
    """.trimIndent()

    @Test
    fun decodesForwardCompatibleIndexes() {
        val index = StoreIndexCodec.decode(sample)
        assertEquals(listOf("geography", "programming"), index.orderedCategories().map { it.id })
        assertEquals(listOf("dsa", "databases"), index.orderedCategories()[1].subcategories.map { it.id })
        val shelf = index.shelves.first()
        assertEquals(2, index.resolveItems(shelf).size)
        assertEquals("1.0.0", index.pack("flags")?.minAppVersion)
        assertEquals(listOf("flags", "capitals"), index.mix("geography")?.toMix()?.members?.map { it.packId })
        assertEquals(index.shelves.take(1), index.shelvesFor(ShelfScope.All))
        assertEquals(index.shelves.drop(1), index.shelvesFor(ShelfScope.category("geography")))
        assertNull(index.pack("missing"))
    }

    @Test
    fun roundTripsThroughTheCodec() {
        val index = StoreIndexCodec.decode(sample)
        assertEquals(index, StoreIndexCodec.decode(StoreIndexCodec.encode(index)))
    }

    @Test
    fun buildsTaxonomyAndSearches() {
        val index = StoreIndexCodec.decode(sample)
        val taxonomy = index.taxonomy()
        assertEquals(listOf("geography", "programming"), taxonomy.categories.map { it.id })
        assertNull(taxonomy.placementProblem("programming", "databases"))
        assertTrue(index.search("geography").any { it is StoreItem.Pack && it.pack.packId == "flags" })
        assertTrue(index.search("mixed geo").any { it is StoreItem.Mix })
        assertTrue(index.search("   ").isEmpty())
    }

    @Test
    fun bundledTaxonomyKeepsProgrammingSubcategories() {
        val programming = BundledTaxonomy.taxonomy.category("programming")!!
        assertEquals(
            listOf("dsa", "databases", "system-design", "networking", "operating-systems", "languages"),
            programming.subcategories.map { it.id },
        )
    }

    @Test
    fun comparesPackVersions() {
        assertTrue(PackVersions.isNewer("1.0.1", "1.0.0"))
        assertTrue(PackVersions.isNewer("1.0.0", "1.0.0-draft.2"))
        assertTrue(PackVersions.isNewer("1.0.0-draft.10", "1.0.0-draft.2"))
        assertTrue(PackVersions.isNewer("2", "1.9.9"))
        assertEquals(0, PackVersions.compare("1.0", "1.0.0"))
        assertEquals(0, PackVersions.compare("v1.2.0+build5", "1.2.0"))
    }

    @Test
    fun resolvesRelativeUrlsAgainstTheBase() {
        assertEquals("https://b.dev/packs/a.apkg", StoreUrls.resolve("https://b.dev/", "/packs/a.apkg"))
        assertEquals("https://x.dev/a.apkg", StoreUrls.resolve("https://b.dev", "https://x.dev/a.apkg"))
        assertEquals("https://b.dev/index.json", StoreUrls.indexUrl("https://b.dev/"))
    }
}
