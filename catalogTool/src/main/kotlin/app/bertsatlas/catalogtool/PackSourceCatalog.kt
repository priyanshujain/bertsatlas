package app.bertsatlas.catalogtool

import java.nio.file.Path
import kotlin.io.path.readText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

object PackSourceCatalog {
    fun assemble(packSources: Path): JsonObject {
        val flagsManifest = readObject(packSources.resolve("flags/pack.json"))
        val flagCards = cards(packSources.resolve("flags/cards.json"))
        val capitalCardsByCountry = cards(packSources.resolve("capitals/cards.json")).associateBy { meta(it).text("countryId") }
        val upstream = flagsManifest.obj("upstream")
        val groups = flagsManifest.array("similarGroups").map { it.jsonObject }

        val countries = flagCards.map { card ->
            val meta = meta(card)
            val countryId = meta.text("countryId")
            val capitalCard = requireNotNull(capitalCardsByCountry[countryId]) { "capitals pack has no card for $countryId" }
            val capitalMeta = meta(capitalCard).obj("capital")
            buildJsonObject {
                put("id", countryId)
                put("alpha2", meta["alpha2"] ?: JsonNull)
                put("alpha3", meta["alpha3"] ?: JsonNull)
                put("displayName", card.field("Country"))
                put("acceptedAliases", lines(card.field("Aliases")))
                put("region", meta["region"] ?: JsonNull)
                put("unM49Subregion", meta["unM49Subregion"] ?: JsonNull)
                put("transcontinentalNote", meta["transcontinentalNote"] ?: JsonNull)
                put("capital", buildJsonObject {
                    put("kind", capitalCard.field("CapitalArrangement"))
                    put("names", lines(capitalCard.field("Capitals")))
                    capitalMeta.forEach { (key, value) -> put(key, value) }
                })
                put("flagAssetId", meta.obj("flagAsset")["id"] ?: JsonNull)
                put("supportedQuestionFormats", meta["formats"] ?: JsonArray(emptyList()))
                put("factIds", JsonArray(listOfNotNull(meta.objOrNull("fact")?.get("id"))))
                put("review", meta["review"] ?: JsonNull)
            }
        }
        val facts = flagCards.mapNotNull { card ->
            val meta = meta(card)
            val fact = meta.objOrNull("fact") ?: return@mapNotNull null
            buildJsonObject {
                put("id", fact["id"] ?: JsonNull)
                put("schemaVersion", 1)
                put("countryIds", JsonArray(listOf(JsonPrimitive(meta.text("countryId")))))
                put("topic", fact["topic"] ?: JsonNull)
                put("difficulty", fact["difficulty"] ?: JsonNull)
                put("heading", fact["heading"] ?: JsonNull)
                put("text", card.field("Fact"))
                put("sourceIds", fact["sourceIds"] ?: JsonArray(emptyList()))
                put("asOf", fact["asOf"] ?: JsonNull)
                put("review", fact["review"] ?: JsonNull)
            }
        }
        val curatedQuestions = capitalCardsByCountry.values.mapNotNull { card ->
            val meta = meta(card)
            val question = meta.objOrNull("curatedQuestion") ?: return@mapNotNull null
            JsonObject(question + ("countryIds" to JsonArray(listOf(JsonPrimitive(meta.text("countryId"))))))
        }
        val similarityGroups = groups.map { group ->
            buildJsonObject {
                put("id", group["id"] ?: JsonNull)
                put("kind", group["kind"] ?: JsonNull)
                put("countryIds", group["countryIds"] ?: JsonArray(emptyList()))
                put("confusionExplanationId", group.obj("explanation")["id"] ?: JsonNull)
            }
        }
        val confusionExplanations = groups.map { group ->
            val explanation = group.obj("explanation")
            JsonObject(explanation + ("countryIds" to (group["countryIds"] ?: JsonArray(emptyList()))))
        }
        val assets = flagsManifest.array("assetLicenses").map { element ->
            val asset = element.jsonObject
            buildJsonObject {
                put("id", asset["assetId"] ?: JsonNull)
                put("path", asset["svg"] ?: JsonNull)
                put("kind", "flag")
                put("sourceId", asset["sourceId"] ?: JsonNull)
                put("license", asset["license"] ?: JsonNull)
                put("sha256", asset["sha256"] ?: JsonNull)
                put("modified", asset["modified"] ?: JsonNull)
                put("manualAudit", asset["manualAudit"] ?: JsonNull)
            }
        }
        return buildJsonObject {
            put("schemaVersion", upstream["catalogSchemaVersion"] ?: JsonNull)
            put("catalogVersion", flagsManifest["version"] ?: JsonNull)
            put("countryCanon", upstream["countryCanon"] ?: JsonNull)
            put("editorialPolicyVersion", upstream["editorialPolicyVersion"] ?: JsonNull)
            put("generatedFromPinnedDataAt", upstream["generatedFromPinnedDataAt"] ?: JsonNull)
            put("sources", flagsManifest["sources"] ?: JsonArray(emptyList()))
            put("countries", JsonArray(countries))
            put("facts", JsonArray(facts))
            put("curatedQuestions", JsonArray(curatedQuestions))
            put("similarityGroups", JsonArray(similarityGroups))
            put("confusionExplanations", JsonArray(confusionExplanations))
            put("assets", JsonArray(assets))
            put("catalogReview", upstream["review"] ?: JsonNull)
        }
    }

    private fun readObject(path: Path): JsonObject = Json.parseToJsonElement(path.readText()).jsonObject

    private fun cards(path: Path): List<JsonObject> = readObject(path).array("cards").map { it.jsonObject }

    private fun meta(card: JsonObject): JsonObject = card.obj("fields").obj("BertMeta")

    private fun JsonObject.field(name: String): String = obj("fields")[name]?.jsonPrimitive?.content.orEmpty()

    private fun JsonObject.text(key: String): String = this[key]?.jsonPrimitive?.content.orEmpty()

    private fun JsonObject.obj(key: String): JsonObject = objOrNull(key) ?: JsonObject(emptyMap())

    private fun JsonObject.objOrNull(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonObject.array(key: String): JsonArray = this[key]?.jsonArray ?: JsonArray(emptyList())

    private fun lines(text: String): JsonElement = JsonArray(text.lines().map(String::trim).filter(String::isNotEmpty).map(::JsonPrimitive))
}
