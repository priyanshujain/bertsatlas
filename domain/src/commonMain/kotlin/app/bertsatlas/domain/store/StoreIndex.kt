package app.bertsatlas.domain.store

import app.bertsatlas.domain.pack.Mix
import app.bertsatlas.domain.pack.MixMember
import app.bertsatlas.domain.taxonomy.Taxonomy
import app.bertsatlas.domain.taxonomy.TaxonomyCategory
import app.bertsatlas.domain.taxonomy.TaxonomySubcategory
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object StoreUrls {
    const val PUBLIC_BASE_URL = "https://pub-1df4b858dc274750aaef7c75b67e271d.r2.dev"
    const val INDEX_KEY = "index.json"
    const val PACKS_PREFIX = "packs"
    const val SAMPLES_PREFIX = "samples"
    const val COVERS_PREFIX = "covers"

    fun indexUrl(baseUrl: String = PUBLIC_BASE_URL): String = "${baseUrl.trimEnd('/')}/$INDEX_KEY"

    fun packKey(packId: String, version: String): String = "$PACKS_PREFIX/$packId-$version.apkg"

    fun samplesKey(packId: String, version: String): String = "$SAMPLES_PREFIX/$packId-$version"

    fun resolve(baseUrl: String, url: String): String = when {
        url.startsWith("https://") || url.startsWith("http://") -> url
        else -> "${baseUrl.trimEnd('/')}/${url.trimStart('/')}"
    }
}

@Serializable
data class StoreIndex(
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    val categories: List<StoreCategory> = emptyList(),
    val featured: List<FeaturedItem> = emptyList(),
    val shelves: List<StoreShelf> = emptyList(),
    val packs: List<StorePack> = emptyList(),
    val mixes: List<StoreMix> = emptyList(),
) {
    fun pack(packId: String): StorePack? = packs.firstOrNull { it.packId == packId }

    fun mix(mixId: String): StoreMix? = mixes.firstOrNull { it.mixId == mixId }

    fun category(categoryId: String): StoreCategory? = categories.firstOrNull { it.id == categoryId }

    fun orderedCategories(): List<StoreCategory> =
        categories.sortedBy { it.order }.map { category -> category.copy(subcategories = category.subcategories.sortedBy { it.order }) }

    fun taxonomy(): Taxonomy = Taxonomy(
        orderedCategories().map { category ->
            TaxonomyCategory(
                id = category.id,
                name = category.name,
                subcategories = category.subcategories.map { TaxonomySubcategory(it.id, it.name, it.icon) },
                icon = category.icon,
                accentColor = category.accentColor,
            )
        },
    )

    fun shelvesFor(scope: ShelfScope): List<StoreShelf> = shelves.filter { it.scope == scope }

    fun packsIn(categoryId: String, subcategoryId: String? = null): List<StorePack> =
        packs.filter { it.category == categoryId && (subcategoryId == null || it.subcategory == subcategoryId) }

    fun resolveItems(shelf: StoreShelf): List<StoreItem> = shelf.items.mapNotNull(::resolve)

    fun resolve(reference: StoreItemRef): StoreItem? = when (reference.kind) {
        StoreItemRef.PACK -> pack(reference.id)?.let { StoreItem.Pack(it) }
        StoreItemRef.MIX -> mix(reference.id)?.let { StoreItem.Mix(it) }
        else -> null
    }

    fun search(query: String): List<StoreItem> {
        val terms = query.lowercase().split(' ').filter { it.isNotBlank() }
        if (terms.isEmpty()) return emptyList()
        fun placementText(categoryId: String?, subcategoryId: String?): String {
            val category = categoryId?.let(::category)
            val subcategory = category?.subcategories?.firstOrNull { it.id == subcategoryId }
            return listOfNotNull(category?.name, subcategory?.name).joinToString(" ")
        }
        fun matches(text: String) = text.lowercase().let { haystack -> terms.all { it in haystack } }
        val matchingPacks = packs.filter { matches("${it.title} ${it.subtitle} ${placementText(it.category, it.subcategory)}") }.map { StoreItem.Pack(it) }
        val matchingMixes = mixes.filter { matches("${it.title} ${it.subtitle}") }.map { StoreItem.Mix(it) }
        return matchingPacks + matchingMixes
    }

    companion object {
        const val CURRENT_FORMAT_VERSION = 1
    }
}

@Serializable
data class StoreCategory(
    val id: String,
    val name: String,
    val icon: String = "",
    val accentColor: String = "",
    val order: Int = 0,
    val subcategories: List<StoreSubcategory> = emptyList(),
)

@Serializable
data class StoreSubcategory(
    val id: String,
    val name: String,
    val icon: String = "",
    val order: Int = 0,
)

@Serializable
data class StoreItemRef(val kind: String = PACK, val id: String) {
    companion object {
        const val PACK = "pack"
        const val MIX = "mix"
    }
}

sealed interface StoreItem {
    data class Pack(val pack: StorePack) : StoreItem
    data class Mix(val mix: StoreMix) : StoreItem
}

@Serializable
data class FeaturedItem(
    val item: StoreItemRef,
    val headline: String,
    val blurb: String = "",
)

@Serializable
data class ShelfScope(
    val kind: String = ALL,
    val category: String? = null,
    val subcategory: String? = null,
) {
    companion object {
        const val ALL = "all"
        const val CATEGORY = "category"
        const val SUBCATEGORY = "subcategory"

        val All = ShelfScope()

        fun category(categoryId: String) = ShelfScope(CATEGORY, categoryId)

        fun subcategory(categoryId: String, subcategoryId: String) = ShelfScope(SUBCATEGORY, categoryId, subcategoryId)
    }
}

@Serializable
data class StoreShelf(
    val id: String,
    val title: String,
    val scope: ShelfScope = ShelfScope.All,
    val items: List<StoreItemRef> = emptyList(),
)

@Serializable
data class SampleCard(
    val frontHtml: String,
    val backHtml: String,
    val media: List<String> = emptyList(),
)

@Serializable
data class StorePack(
    val packId: String,
    val version: String,
    val title: String,
    val subtitle: String = "",
    val category: String,
    val subcategory: String,
    val accentColor: String = "",
    val coverUrl: String? = null,
    val sampleCards: List<SampleCard> = emptyList(),
    val sampleMediaBaseUrl: String? = null,
    val description: String = "",
    val whatsNew: String = "",
    val cardCount: Long = 0,
    val sizeBytes: Long = 0,
    val sha256: String,
    val url: String,
    val minAppVersion: String = "1.0.0",
)

@Serializable
data class StoreMixMember(val packId: String, val sections: List<String> = emptyList())

@Serializable
data class StoreMix(
    val mixId: String,
    val title: String,
    val subtitle: String = "",
    val accentColor: String = "",
    val description: String = "",
    val members: List<StoreMixMember> = emptyList(),
) {
    fun toMix(): Mix = Mix(mixId, title, subtitle, accentColor, members.map { MixMember(it.packId, it.sections) })
}

object StoreIndexCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        prettyPrint = true
        coerceInputValues = true
    }

    fun decode(text: String): StoreIndex = json.decodeFromString(StoreIndex.serializer(), text)

    fun encode(index: StoreIndex): String = json.encodeToString(StoreIndex.serializer(), index)
}
