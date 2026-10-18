package app.bertsatlas.domain.geography

import app.bertsatlas.domain.model.AssetLicenseRecord
import app.bertsatlas.domain.model.SourceRecord

data class GeographyContent(
    val schemaVersion: Int,
    val contentVersion: String,
    val sources: List<SourceRecord>,
    val countries: List<GeographyCountry>,
    val facts: List<GeographyFact>,
    val curatedQuestions: List<GeographyCuratedQuestion>,
    val similarityGroups: List<GeographySimilarityGroup>,
    val assets: List<AssetLicenseRecord>,
)

data class GeographyCountry(
    val id: String,
    val displayName: String,
    val acceptedAliases: List<String>,
    val region: String,
    val capital: GeographyCapital,
    val flagAssetId: String,
    val flagClues: GeographyClues,
    val capitalClues: GeographyClues,
)

data class GeographyClues(
    val continent: String,
    val factualClue: String?,
)

data class GeographyCapital(
    val kind: String,
    val names: List<String>,
    val acceptedAnswer: String,
    val questionEligible: Boolean,
    val note: String?,
)

data class GeographyFact(
    val id: String,
    val countryIds: List<String>,
    val heading: String,
    val text: String,
    val sourceIds: List<String>,
    val asOf: String?,
    val reviewState: String,
)

data class GeographyCuratedQuestion(
    val id: String,
    val topic: String,
    val difficulty: String,
    val format: String,
    val prompt: String,
    val answer: String,
    val choices: List<String>,
    val countryIds: List<String>,
    val supportedHints: List<String>,
    val explanation: String,
)

data class GeographySimilarityGroup(
    val id: String,
    val countryIds: List<String>,
)
