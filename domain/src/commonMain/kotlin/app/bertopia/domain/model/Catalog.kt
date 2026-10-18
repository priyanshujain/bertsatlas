package app.bertopia.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Catalog(
    val schemaVersion: Int,
    val contentVersion: String,
    val countries: List<CountryRecord>,
    val questions: List<Question>,
    val facts: List<FactRecord>,
    val sources: List<SourceRecord>,
    val assetLicenses: List<AssetLicenseRecord>,
)

@Serializable
data class CountryRecord(
    val id: String,
    val displayName: String,
    val acceptedAliases: List<String> = emptyList(),
    val continentIds: List<Continent>,
    val flagAssetId: String,
    val capitalArrangement: CapitalArrangement,
    val similarityGroupIds: List<String> = emptyList(),
)

@Serializable
sealed interface CapitalArrangement {
    @Serializable
    @SerialName("single")
    data class Single(val capital: String) : CapitalArrangement

    @Serializable
    @SerialName("multiple")
    data class Multiple(
        val capitals: List<String>,
        val explanation: String,
    ) : CapitalArrangement

    @Serializable
    @SerialName("exception")
    data class Exception(val explanation: String) : CapitalArrangement
}

@Serializable
data class FactRecord(
    val id: String,
    val heading: String,
    val body: String,
    val countryIds: List<String>,
    val sourceIds: List<String>,
    val asOf: String? = null,
    val assetId: String? = null,
    val reviewStatus: EditorialReviewStatus,
)

@Serializable
enum class EditorialReviewStatus {
    Draft,
    FactChecked,
    CopyChecked,
    Approved,
}

@Serializable
data class SourceRecord(
    val id: String,
    val title: String,
    val url: String,
    val revision: String? = null,
    val accessedOn: String? = null,
)

@Serializable
data class AssetLicenseRecord(
    val assetId: String,
    val sourceId: String? = null,
    val licenseName: String,
    val licenseUrl: String? = null,
    val attribution: String? = null,
    val modified: Boolean,
    val checksum: String,
)

@Serializable
data class CuratedQuestion(
    val question: Question,
    val sourceIds: List<String>,
    val requiredAssetIds: List<String> = emptyList(),
)
