package app.bertopia.domain.pack

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class FactMeta(
    val id: String,
    val heading: String? = null,
    val topic: String? = null,
    val difficulty: String? = null,
    val asOf: String? = null,
    val sourceIds: List<String> = emptyList(),
    val review: ReviewRecord? = null,
)

@Serializable
data class FlagAssetMeta(
    val id: String,
    val svg: String? = null,
    val sha256: String? = null,
    val manualAudit: String? = null,
)

@Serializable
data class CapitalMeta(
    val acceptedAnswer: String? = null,
    val questionEligible: Boolean = true,
    val note: String? = null,
    val sourceIds: List<String> = emptyList(),
    val review: ReviewRecord? = null,
)

@Serializable
data class CuratedQuestionMeta(
    val id: String,
    val schemaVersion: Int = 1,
    val topic: String? = null,
    val difficulty: String? = null,
    val format: String? = null,
    val prompt: String,
    val answer: String,
    val choices: List<String> = emptyList(),
    val sourceIds: List<String> = emptyList(),
    val supportedHints: List<String> = emptyList(),
    val explanation: String? = null,
    val review: ReviewRecord? = null,
)

@Serializable
data class FlagBertMeta(
    val countryId: String,
    val alpha2: String? = null,
    val alpha3: String? = null,
    val region: String? = null,
    val unM49Subregion: String? = null,
    val transcontinentalNote: String? = null,
    val difficulty: String? = null,
    val fact: FactMeta? = null,
    val formats: List<String> = emptyList(),
    val flagAsset: FlagAssetMeta? = null,
    val review: ReviewRecord? = null,
)

@Serializable
data class CapitalBertMeta(
    val countryId: String,
    val alpha2: String? = null,
    val alpha3: String? = null,
    val region: String? = null,
    val unM49Subregion: String? = null,
    val transcontinentalNote: String? = null,
    val difficulty: String? = null,
    val fact: FactMeta? = null,
    val capital: CapitalMeta? = null,
    val curatedQuestion: CuratedQuestionMeta? = null,
)

@Serializable
data class CardBertMeta(
    val flipOnly: Boolean = false,
    val review: ReviewRecord? = null,
)

sealed interface BertMetaReading<out T> {
    data object Empty : BertMetaReading<Nothing>
    data class Valid<T>(val meta: T) : BertMetaReading<T>
    data class Invalid(val reason: String) : BertMetaReading<Nothing>
}

object BertMetaCodec {
    fun readFlag(field: String): BertMetaReading<FlagBertMeta> = read(field, FlagBertMeta.serializer())

    fun readCapital(field: String): BertMetaReading<CapitalBertMeta> = read(field, CapitalBertMeta.serializer())

    fun readCard(field: String): BertMetaReading<CardBertMeta> = read(field, CardBertMeta.serializer())

    fun readObject(field: String): BertMetaReading<JsonObject> = read(field, JsonObject.serializer())

    fun readFor(noteType: BertNoteType, field: String): BertMetaReading<Any> = when (noteType) {
        BertNoteTypes.Flag -> readFlag(field)
        BertNoteTypes.Capital -> readCapital(field)
        BertNoteTypes.Basic, BertNoteTypes.Typed, BertNoteTypes.Cloze, BertNoteTypes.TwoWay, BertNoteTypes.Image, BertNoteTypes.Choice -> readCard(field)
        else -> readObject(field)
    }

    fun flipOnly(field: String): Boolean = (readCard(field) as? BertMetaReading.Valid)?.meta?.flipOnly ?: false

    fun writeFlag(meta: FlagBertMeta): String = PackJson.lenient.encodeToString(FlagBertMeta.serializer(), meta)

    fun writeCapital(meta: CapitalBertMeta): String = PackJson.lenient.encodeToString(CapitalBertMeta.serializer(), meta)

    private fun <T> read(field: String, serializer: KSerializer<T>): BertMetaReading<T> {
        val json = unwrapFieldHtml(field)
        if (json.isEmpty()) return BertMetaReading.Empty
        return runCatching { PackJson.lenient.decodeFromString(serializer, json) }
            .fold(
                onSuccess = { BertMetaReading.Valid(it) },
                onFailure = { BertMetaReading.Invalid(it.message ?: "invalid BertMeta JSON") },
            )
    }

    private fun unwrapFieldHtml(field: String): String = field
        .replace("&quot;", "\"")
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .trim()
}
