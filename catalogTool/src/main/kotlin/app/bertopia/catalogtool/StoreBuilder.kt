package app.bertopia.catalogtool

import app.bertopia.anki.collection.renderCard
import app.bertopia.anki.io.Sha256
import app.bertopia.anki.pkg.PackageImport
import app.bertopia.domain.pack.PackBuildException
import app.bertopia.domain.pack.BertTags
import app.bertopia.domain.pack.EditorialStatus
import app.bertopia.domain.pack.PackSourceBuilder
import app.bertopia.domain.store.FeaturedItem
import app.bertopia.domain.store.SampleCard
import app.bertopia.domain.store.StoreCategory
import app.bertopia.domain.store.StoreIndex
import app.bertopia.domain.store.StoreIndexCodec
import app.bertopia.domain.store.StoreItemRef
import app.bertopia.domain.store.StoreMix
import app.bertopia.domain.store.StorePack
import app.bertopia.domain.store.StoreShelf
import app.bertopia.domain.store.StoreUrls
import app.bertopia.domain.taxonomy.BundledTaxonomy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.fileSize
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.writeText
import kotlin.system.exitProcess

private const val PACK_SOURCES = "content/packs"
private const val STORE_CURATION = "content/store.json"
private const val STORE_OUTPUT = "build/store"
private const val STORE_WORKING_DIRECTORY = "catalogTool/build/store-work"
private const val INDEX_CACHE_CONTROL = "public, max-age=300"
private const val IMMUTABLE_CACHE_CONTROL = "public, max-age=31536000, immutable"

@Serializable
private data class StoreCuration(
    val formatVersion: Int = StoreIndex.CURRENT_FORMAT_VERSION,
    val categories: List<StoreCategory>,
    val featured: List<FeaturedItem> = emptyList(),
    val shelves: List<StoreShelf> = emptyList(),
    val mixes: List<StoreMix> = emptyList(),
    val packs: Map<String, PackCuration> = emptyMap(),
)

@Serializable
private data class PackCuration(
    val description: String,
    val whatsNew: String = "",
    val minAppVersion: String = "1.0.0",
    val sampleGuids: List<String> = emptyList(),
)

private class PublishEntry(val key: String, val file: Path, val contentType: String, val cacheControl: String)

@OptIn(kotlin.io.path.ExperimentalPathApi::class)
fun main(arguments: Array<String>) {
    val baseUrl = arguments.toList().windowed(2).firstOrNull { it[0] == "--base-url" }?.get(1) ?: StoreUrls.PUBLIC_BASE_URL
    val projectRoot = findStoreProjectRoot(Path.of("").toAbsolutePath())
    val curation = Json { ignoreUnknownKeys = true }.decodeFromString(StoreCuration.serializer(), projectRoot.resolve(STORE_CURATION).readText())
    val output = projectRoot.resolve(STORE_OUTPUT)
    output.deleteRecursively()
    output.createDirectories()
    val working = projectRoot.resolve(STORE_WORKING_DIRECTORY)
    val problems = mutableListOf<String>()

    val categories = curation.categories
    val storeTaxonomy = StoreIndex(categories = categories).taxonomy()
    BundledTaxonomy.taxonomy.categories.forEach { bundled ->
        val published = storeTaxonomy.category(bundled.id)
        if (published == null) {
            problems += "content/store.json is missing the bundled category ${bundled.id}. Packs placed there offline would lose their shelf."
        } else {
            val missing = bundled.subcategories.map { it.id } - published.subcategories.map { it.id }.toSet()
            if (missing.isNotEmpty()) problems += "content/store.json category ${bundled.id} is missing bundled subcategories ${missing.joinToString()}."
        }
    }
    categories.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach { problems += "content/store.json lists category $it more than once." }
    categories.forEach { category ->
        category.subcategories.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach { problems += "content/store.json category ${category.id} lists subcategory $it more than once." }
    }
    val bundledIds = BundledTaxonomy.taxonomy.categories.flatMap { category -> listOf(category.id) + category.subcategories.map { "${category.id}/${it.id}" } }.toSet()
    val addedIds = storeTaxonomy.categories.flatMap { category -> listOf(category.id) + category.subcategories.map { "${category.id}/${it.id}" } }.filterNot { it in bundledIds }
    if (addedIds.isNotEmpty()) println("Store adds categories not yet bundled in the app: ${addedIds.joinToString()}")

    val publish = mutableListOf<PublishEntry>()
    val draftCounts = mutableMapOf<String, Int>()
    val packs = projectRoot.resolve(PACK_SOURCES).listDirectoryEntries()
        .filter { it.resolve(PackSourceBuilder.MANIFEST_FILE).exists() }
        .sorted()
        .mapNotNull { sourceDirectory ->
            val contents = PackSourceBuilder.read(sourceDirectory.toString())
            val manifest = contents.manifest
            if (!manifest.official) return@mapNotNull null
            draftCounts[manifest.packId] = contents.cards.count { card -> BertTags.editorialStatus(card.tags) == EditorialStatus.Draft }
            val packCuration = curation.packs[manifest.packId]
            if (packCuration == null) {
                problems += "${manifest.packId}: no entry in content/store.json packs"
                return@mapNotNull null
            }
            val category = manifest.category.orEmpty()
            val subcategory = manifest.subcategory.orEmpty()
            storeTaxonomy.placementProblem(category, subcategory)?.let { problems += "${manifest.packId}: $it" }
            try {
                buildStorePack(sourceDirectory, contents.mediaDirectory, contents.coverFile, manifest.packId, manifest.version, packCuration, output, working.resolve(manifest.packId), baseUrl, publish)
                    .copy(
                        title = manifest.title,
                        subtitle = manifest.subtitle.orEmpty(),
                        category = category,
                        subcategory = subcategory,
                        accentColor = manifest.accentColor.orEmpty(),
                    )
            } catch (failure: PackBuildException) {
                problems += "${manifest.packId}: ${failure.message}"
                failure.issues.forEach { problems += "${manifest.packId}: ${it.severity} ${it.code} ${it.noteGuid.orEmpty()} ${it.message}" }
                null
            }
        }

    val index = StoreIndex(
        formatVersion = curation.formatVersion,
        categories = categories,
        featured = curation.featured,
        shelves = curation.shelves,
        packs = packs,
        mixes = curation.mixes,
    )
    problems += referenceProblems(index)
    curation.packs.keys.filter { id -> packs.none { it.packId == id } && problems.none { it.startsWith("$id:") } }
        .forEach { problems += "$it: listed in content/store.json but no official pack source exists" }

    if (problems.isNotEmpty()) {
        problems.forEach { System.err.println(it) }
        exitProcess(1)
    }

    val indexFile = output.resolve(StoreUrls.INDEX_KEY)
    indexFile.writeText(StoreIndexCodec.encode(index) + "\n")
    publish += PublishEntry(StoreUrls.INDEX_KEY, indexFile, "application/json; charset=utf-8", INDEX_CACHE_CONTROL)
    output.resolve("publish-plan.tsv").writeText(
        publish.joinToString("\n", postfix = "\n") { listOf(it.key, it.file.toString(), it.contentType, it.cacheControl).joinToString("\t") },
    )
    packs.forEach { println("${it.packId} ${it.version}: ${it.cardCount} cards, ${it.sizeBytes} bytes, sha256 ${it.sha256}") }
    draftCounts.filterValues { it > 0 }.forEach { (packId, drafts) ->
        println("$packId: $drafts notes are still bert::status::draft and need editorial review before publishing")
    }
    println("index: ${index.categories.size} categories, ${index.shelves.size} shelves, ${index.packs.size} packs, ${index.mixes.size} mixes -> $indexFile")
}

private fun buildStorePack(
    sourceDirectory: Path,
    sourceMediaDirectory: String?,
    coverFile: String?,
    packId: String,
    version: String,
    curation: PackCuration,
    output: Path,
    working: Path,
    baseUrl: String,
    publish: MutableList<PublishEntry>,
): StorePack {
    val packKey = StoreUrls.packKey(packId, version)
    val packFile = output.resolve(packKey).also { it.parent.createDirectories() }
    PackSourceBuilder.build(sourceDirectory.toString(), working.resolve("build").toString(), packFile.toString())
    publish += PublishEntry(packKey, packFile, "application/octet-stream", IMMUTABLE_CACHE_CONTROL)

    val samplesKey = StoreUrls.samplesKey(packId, version)
    val samplesDirectory = output.resolve(samplesKey)
    val (cardCount, samples) = PackageImport.open(packFile.toString(), working.resolve("read").toString()).use { opened ->
        val collection = opened.collection
        val samples = curation.sampleGuids.map { guid ->
            val note = collection.noteByGuid(guid) ?: throw PackBuildException("sample card $guid is not in the pack")
            val card = collection.cardsOfNote(note.id).firstOrNull() ?: throw PackBuildException("sample card $guid has no cards")
            val rendered = collection.renderCard(card) ?: throw PackBuildException("sample card $guid did not render")
            val media = (PackageImport.referencedMedia(rendered.question) + PackageImport.referencedMedia(rendered.answer)).distinct()
            SampleCard(rendered.question, rendered.answer, media)
        }
        collection.cards().size.toLong() to samples
    }
    samples.flatMap { it.media }.distinct().forEach { name ->
        val source = sourceMediaDirectory?.let { Path.of(it, name) }?.takeIf { it.exists() } ?: throw PackBuildException("sample media $name is missing")
        val target = samplesDirectory.resolve(name).also { it.parent.createDirectories() }
        source.copyTo(target, overwrite = true)
        publish += PublishEntry("$samplesKey/$name", target, contentTypeOf(target), IMMUTABLE_CACHE_CONTROL)
    }
    val coverUrl = coverFile?.let { Path.of(it) }?.let { cover ->
        val coverKey = "${StoreUrls.COVERS_PREFIX}/$packId-$version.${cover.extension}"
        val target = output.resolve(coverKey).also { it.parent.createDirectories() }
        cover.copyTo(target, overwrite = true)
        publish += PublishEntry(coverKey, target, contentTypeOf(target), IMMUTABLE_CACHE_CONTROL)
        StoreUrls.resolve(baseUrl, coverKey)
    }
    return StorePack(
        packId = packId,
        version = version,
        title = packId,
        category = "",
        subcategory = "",
        coverUrl = coverUrl,
        sampleCards = samples,
        sampleMediaBaseUrl = if (samples.any { it.media.isNotEmpty() }) StoreUrls.resolve(baseUrl, samplesKey) else null,
        description = curation.description,
        whatsNew = curation.whatsNew,
        cardCount = cardCount,
        sizeBytes = packFile.fileSize(),
        sha256 = Sha256.hexOfFile(packFile.toString()),
        url = StoreUrls.resolve(baseUrl, packFile.relativeTo(output).toString()),
        minAppVersion = curation.minAppVersion,
    )
}

private fun referenceProblems(index: StoreIndex): List<String> {
    val problems = mutableListOf<String>()
    fun check(where: String, reference: StoreItemRef) {
        if (index.resolve(reference) == null) problems += "$where refers to unknown ${reference.kind} ${reference.id}"
    }
    index.featured.forEach { check("featured", it.item) }
    index.shelves.forEach { shelf ->
        shelf.items.forEach { check("shelf ${shelf.id}", it) }
        val categoryId = shelf.scope.category
        val subcategoryId = shelf.scope.subcategory
        if (categoryId != null && index.category(categoryId) == null) problems += "shelf ${shelf.id} scope has unknown category $categoryId"
        if (categoryId != null && subcategoryId != null && index.category(categoryId)?.subcategories?.none { it.id == subcategoryId } == true) {
            problems += "shelf ${shelf.id} scope has unknown subcategory $subcategoryId"
        }
    }
    index.mixes.forEach { mix ->
        mix.members.filter { index.pack(it.packId) == null }.forEach { problems += "mix ${mix.mixId} needs pack ${it.packId}, which is not in the store" }
    }
    val duplicateShelves = index.shelves.groupBy { it.id }.filter { it.value.size > 1 }.keys
    duplicateShelves.forEach { problems += "shelf id $it is used more than once" }
    return problems
}

private fun contentTypeOf(path: Path): String = when (path.extension.lowercase()) {
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "svg" -> "image/svg+xml"
    "mp3" -> "audio/mpeg"
    "m4a" -> "audio/mp4"
    "ogg" -> "audio/ogg"
    "wav" -> "audio/wav"
    else -> "application/octet-stream"
}

private fun findStoreProjectRoot(start: Path): Path =
    generateSequence(start.normalize()) { it.parent }.firstOrNull { it.resolve("settings.gradle.kts").exists() && it.resolve(STORE_CURATION).exists() }
        ?: error("could not locate project root from $start")
