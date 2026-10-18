package app.bertsatlas.anki.model

import kotlinx.serialization.json.JsonObject

data class DeckConfig(
    val id: Long,
    val name: String,
    val newCardsPerDay: Int = 20,
    val reviewsPerDay: Int = 200,
    val learningStepsMinutes: List<Double> = listOf(1.0, 10.0),
    val relearningStepsMinutes: List<Double> = listOf(10.0),
    val desiredRetention: Double = 0.9,
    val fsrsParameters: List<Double> = emptyList(),
    val modifiedSecs: Long = 0,
    val preserved: JsonObject = JsonObject(emptyMap()),
)
