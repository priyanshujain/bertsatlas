package app.bertopia.domain.taxonomy

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Taxonomy(val categories: List<TaxonomyCategory>) {
    fun category(id: String): TaxonomyCategory? = categories.firstOrNull { it.id == id }

    fun placementProblem(categoryId: String, subcategoryId: String): String? {
        val category = category(categoryId)
            ?: return "unknown category \"$categoryId\". Valid categories: ${categories.joinToString(", ") { it.id }}"
        if (category.subcategory(subcategoryId) == null) {
            return "unknown subcategory \"$subcategoryId\" in $categoryId. Valid subcategories: ${category.subcategories.joinToString(", ") { it.id }}"
        }
        return null
    }

    fun encodeJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun decodeJson(text: String): Taxonomy = json.decodeFromString(serializer(), text)
    }
}

@Serializable
data class TaxonomyCategory(
    val id: String,
    val name: String,
    val subcategories: List<TaxonomySubcategory>,
    val icon: String = "",
    val accentColor: String = "",
) {
    fun subcategory(id: String): TaxonomySubcategory? = subcategories.firstOrNull { it.id == id }
}

@Serializable
data class TaxonomySubcategory(val id: String, val name: String, val icon: String = "")

object BundledTaxonomy {
    val taxonomy = Taxonomy(
        listOf(
        TaxonomyCategory(
            "geography", "Geography",
            listOf(
            TaxonomySubcategory("flags", "Flags"),
            TaxonomySubcategory("capitals", "Capitals"),
            TaxonomySubcategory("maps", "Maps and places"),
            ),
            icon = "globe",
            accentColor = "#27635B",
        ),
        TaxonomyCategory(
            "languages", "Languages",
            listOf(
            TaxonomySubcategory("vocabulary", "Vocabulary"),
            TaxonomySubcategory("phrases", "Phrases and sentences"),
            TaxonomySubcategory("grammar", "Grammar"),
            TaxonomySubcategory("writing-systems", "Writing systems"),
            ),
            icon = "speech",
            accentColor = "#B5654A",
        ),
        TaxonomyCategory(
            "programming", "Programming",
            listOf(
            TaxonomySubcategory("dsa", "Data Structures and Algorithms"),
            TaxonomySubcategory("databases", "Databases"),
            TaxonomySubcategory("system-design", "System Design"),
            TaxonomySubcategory("networking", "Networking"),
            TaxonomySubcategory("operating-systems", "Operating Systems"),
            TaxonomySubcategory("languages", "Programming languages"),
            ),
            icon = "code",
            accentColor = "#3E5C8A",
        ),
        TaxonomyCategory(
            "science", "Science",
            listOf(
            TaxonomySubcategory("biology", "Biology"),
            TaxonomySubcategory("chemistry", "Chemistry"),
            TaxonomySubcategory("physics", "Physics"),
            TaxonomySubcategory("medicine", "Medicine"),
            ),
            icon = "flask",
            accentColor = "#4E8A6B",
        ),
        TaxonomyCategory(
            "mathematics", "Mathematics",
            listOf(
            TaxonomySubcategory("arithmetic", "Arithmetic"),
            TaxonomySubcategory("algebra", "Algebra"),
            TaxonomySubcategory("calculus", "Calculus"),
            TaxonomySubcategory("statistics", "Statistics"),
            ),
            icon = "sigma",
            accentColor = "#7A5BA6",
        ),
        TaxonomyCategory(
            "history", "History",
            listOf(
            TaxonomySubcategory("world-history", "World history"),
            TaxonomySubcategory("people", "People"),
            TaxonomySubcategory("dates", "Dates and events"),
            ),
            icon = "scroll",
            accentColor = "#A07A3C",
        ),
        TaxonomyCategory(
            "trivia", "Trivia",
            listOf(
            TaxonomySubcategory("pop-culture", "Pop culture"),
            TaxonomySubcategory("games", "Games"),
            TaxonomySubcategory("general", "General knowledge"),
            ),
            icon = "star",
            accentColor = "#C2505E",
        ),
        TaxonomyCategory(
            "other", "Other",
            listOf(
            TaxonomySubcategory("general", "General"),
            ),
            icon = "shapes",
            accentColor = "#6B7280",
        ),
        ),
    )
}
