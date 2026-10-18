package app.bertsatlas.domain.fsrs

import kotlinx.serialization.json.jsonObject
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals

class FsrsReferenceTest {
    @Test
    fun memoryStatesAndIntervalsMatchFsrsPy() {
        val vectors = readVectors("fsrs-py-6.3.2.json")
        var checked = 0
        vectors.arr("cases").forEach { element ->
            val case = element.jsonObject
            val fsrs = Fsrs(FsrsParameters.fromPreset(case.getValue("parameters").doubles()))
            val retention = case.double("desiredRetention").toFloat()
            var memory: MemoryState? = null
            case.arr("steps").forEachIndexed { index, stepElement ->
                val step = stepElement.jsonObject
                val elapsed = step.int("elapsedDays")
                val label = "case ${vectors.arr("cases").indexOf(element)} step $index"
                step.doubleOrNull("retrievabilityBefore")?.let { expected ->
                    assertClose(expected, fsrs.retrievability(memory!!, elapsed.toFloat()).toDouble(), 1e-4, "$label retrievability")
                }
                val rating = Rating.fromNumber(step.int("rating"))
                memory = fsrs.nextStates(memory, retention, elapsed).forRating(rating).memory
                assertClose(step.double("stability"), memory!!.stability.toDouble(), 2e-4, "$label stability")
                assertClose(step.double("difficulty"), memory!!.difficulty.toDouble(), 2e-4, "$label difficulty")
                val interval = fsrs.nextIntervalDays(memory!!.stability, retention).roundToInt().coerceIn(1, 36_500)
                assertEquals(step.int("intervalDays"), interval, "$label interval")
                checked++
            }
        }
        assertEquals(96, checked)
    }

    @Test
    fun fsrs5ParametersGetFsrs5Decay() {
        val parameters = FsrsParameters.fromPreset(List(19) { 0.5 })
        assertEquals(21, parameters.weights.size)
        assertEquals(FsrsParameters.FSRS5_DEFAULT_DECAY, parameters.decay)
    }

    @Test
    fun emptyParametersUseFsrs6Defaults() {
        assertEquals(FsrsParameters.FSRS6_DEFAULT, FsrsParameters.fromPreset(emptyList()).weights)
    }
}
