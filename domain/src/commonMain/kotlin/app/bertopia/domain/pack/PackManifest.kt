package app.bertopia.domain.pack

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

const val CURRENT_BERT_SCHEMA_VERSION = 1

@Serializable
data class PackManifest(
    val packId: String,
    val version: String,
    val official: Boolean = false,
    val title: String,
    val subtitle: String? = null,
    val category: String? = null,
    val subcategory: String? = null,
    val accentColor: String? = null,
    val coverMedia: String? = null,
    val languages: List<String> = emptyList(),
    val sources: List<PackSource> = emptyList(),
    val assetLicenses: List<AssetLicense> = emptyList(),
    val stamps: List<PackStamp> = emptyList(),
    val bertSchemaVersion: Int = CURRENT_BERT_SCHEMA_VERSION,
    val mediaDir: String? = null,
    val similarGroups: List<SimilarGroup> = emptyList(),
    val upstream: JsonObject? = null,
)

@Serializable
data class PackSource(
    val id: String,
    val title: String,
    val url: String? = null,
    val publisher: String? = null,
    val retrievedAt: String? = null,
    val revision: String? = null,
)

@Serializable
data class AssetLicense(
    val assetId: String,
    val media: String? = null,
    val svg: String? = null,
    val sourceId: String? = null,
    val license: String,
    val modified: Boolean = false,
    val sha256: String? = null,
    val manualAudit: String? = null,
)

@Serializable
data class PackStamp(
    val id: String,
    val title: String,
    val description: String? = null,
    val rule: JsonObject,
)

@Serializable
sealed interface StampRule {
    @Serializable
    @SerialName("correctAcrossFieldValues")
    data class CorrectAcrossFieldValues(val field: String, val values: List<String>) : StampRule

    @Serializable
    @SerialName("masterTaggedCards")
    data class MasterTaggedCards(val tagPrefix: String) : StampRule
}

@Serializable
data class SimilarGroup(
    val id: String,
    val kind: String? = null,
    val countryIds: List<String> = emptyList(),
    val explanation: SimilarGroupExplanation? = null,
)

@Serializable
data class SimilarGroupExplanation(
    val id: String,
    val text: String,
    val sourceIds: List<String> = emptyList(),
    val review: ReviewRecord? = null,
)

@Serializable
data class ReviewRecord(
    val state: String,
    val reviewer: String? = null,
    val reviewedAt: String? = null,
    val note: String? = null,
)

fun PackStamp.decodeRule(): StampRule? =
    runCatching { PackJson.stampRules.decodeFromJsonElement<StampRule>(rule) }.getOrNull()

internal object PackJson {
    val lenient = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    val stampRules = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "kind"
    }
}

sealed interface ManifestReading {
    data class Found(val manifest: PackManifest, val visibleDescription: String) : ManifestReading
    data class Missing(val visibleDescription: String) : ManifestReading
    data class Unreadable(val visibleDescription: String, val reason: String) : ManifestReading
}

object PackManifestCodec {
    private const val BLOCK_START = "<!--bert"
    private const val BLOCK_END = "-->"
    private val manifestBlock = Regex("""\s*<!--bert\s*(.*?)\s*-->\s*""", RegexOption.DOT_MATCHES_ALL)

    fun encodeJson(manifest: PackManifest): String = PackJson.lenient.encodeToString(PackManifest.serializer(), manifest)

    fun decodeJson(json: String): PackManifest = PackJson.lenient.decodeFromString(PackManifest.serializer(), json)

    fun writeDeckDescription(manifest: PackManifest, visibleDescription: String): String {
        val visible = visibleDescription.trim()
        val block = "$BLOCK_START\n${encodeJson(manifest)}\n$BLOCK_END"
        return if (visible.isEmpty()) block else "$visible\n$block"
    }

    fun readDeckDescription(description: String): ManifestReading {
        val match = manifestBlock.find(description)
            ?: return ManifestReading.Missing(description.trim())
        val visible = description.removeRange(match.range).trim()
        return runCatching { decodeJson(match.groupValues[1]) }
            .fold(
                onSuccess = { ManifestReading.Found(it, visible) },
                onFailure = { ManifestReading.Unreadable(visible, it.message ?: "invalid manifest JSON") },
            )
    }
}
