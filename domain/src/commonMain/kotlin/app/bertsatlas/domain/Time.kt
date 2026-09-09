package app.bertsatlas.domain

fun interface MonotonicClock {
    fun nowMillis(): Long
}

fun interface WallClock {
    fun nowEpochMillis(): Long
}

fun interface LocalDateProvider {
    fun currentEpochDay(): Int
}
