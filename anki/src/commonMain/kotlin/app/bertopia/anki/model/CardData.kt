package app.bertopia.anki.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlin.math.pow
import kotlin.math.roundToLong

data class CardData(
    val originalPosition: Long? = null,
    val stability: Float? = null,
    val difficulty: Float? = null,
    val desiredRetention: Float? = null,
    val decay: Float? = null,
    val lastReviewSecs: Long? = null,
    val customData: String = "",
) {
    val hasMemoryState: Boolean get() = stability != null && difficulty != null

    fun encode(): String {
        val entries = buildMap<String, JsonElement> {
            originalPosition?.let { put("pos", JsonPrimitive(it)) }
            stability?.let { put("s", JsonPrimitive(it.roundedTo(4))) }
            difficulty?.let { put("d", JsonPrimitive(it.roundedTo(3))) }
            desiredRetention?.let { put("dr", JsonPrimitive(it.roundedTo(2))) }
            decay?.let { put("decay", JsonPrimitive(it.roundedTo(3))) }
            lastReviewSecs?.let { put("lrt", JsonPrimitive(it)) }
            if (customData.isNotEmpty() && customData != "{}") put("cd", JsonPrimitive(customData))
        }
        return JsonObject(entries).toString()
    }

    companion object {
        fun decode(text: String): CardData {
            val json = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return CardData()
            return CardData(
                originalPosition = json.primitive("pos")?.longOrNull?.takeIf { it >= 0 },
                stability = json.primitive("s")?.floatOrNull,
                difficulty = json.primitive("d")?.floatOrNull,
                desiredRetention = json.primitive("dr")?.floatOrNull,
                decay = json.primitive("decay")?.floatOrNull,
                lastReviewSecs = json.primitive("lrt")?.longOrNull,
                customData = json.primitive("cd")?.takeIf { it.isString }?.content.orEmpty(),
            )
        }

        private fun JsonObject.primitive(key: String): JsonPrimitive? = this[key] as? JsonPrimitive

        private fun Float.roundedTo(places: Int): Float {
            val factor = 10f.pow(places)
            return (this * factor).roundToLong() / factor
        }
    }
}
