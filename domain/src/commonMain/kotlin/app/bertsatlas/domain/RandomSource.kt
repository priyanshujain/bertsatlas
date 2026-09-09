package app.bertsatlas.domain

data class RandomValue(
    val value: Int,
    val nextState: Long,
)

fun interface RandomSource {
    fun nextInt(state: Long, bound: Int): RandomValue
}

object SeededRandomSource : RandomSource {
    override fun nextInt(state: Long, bound: Int): RandomValue {
        require(bound > 0)
        var next = state
        next = next xor (next shl 13)
        next = next xor (next ushr 7)
        next = next xor (next shl 17)
        if (next == 0L) next = NON_ZERO_FALLBACK
        val positive = next ushr 1
        return RandomValue((positive % bound.toLong()).toInt(), next)
    }

    private const val NON_ZERO_FALLBACK = -7046029254386353131L
}

internal fun <T> List<T>.shuffled(
    randomSource: RandomSource,
    initialState: Long,
): Pair<List<T>, Long> {
    val result = toMutableList()
    var state = initialState
    for (index in result.lastIndex downTo 1) {
        val random = randomSource.nextInt(state, index + 1)
        state = random.nextState
        val other = random.value
        val item = result[index]
        result[index] = result[other]
        result[other] = item
    }
    return result to state
}
