package app.bertopia.domain.fsrs

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlin.time.Instant

data class SchedulerTiming(
    val nowMillis: Long,
    val today: Int,
    val nextDayAtSecs: Long,
) {
    val nowSecs: Long get() = nowMillis / 1000

    val secondsUntilRollover: Int get() = (nextDayAtSecs - nowSecs).coerceAtLeast(0).toInt()

    fun daysSince(epochSecs: Long): Int = ((nextDayAtSecs - epochSecs).coerceAtLeast(0) / SECONDS_PER_DAY).toInt()

    companion object {
        const val DEFAULT_ROLLOVER_HOUR = 4

        fun forCollection(creationSecs: Long, configuration: JsonObject, nowMillis: Long, timeZone: TimeZone): SchedulerTiming {
            val rolloverHour = ((configuration["rollover"] as? JsonPrimitive)?.intOrNull ?: DEFAULT_ROLLOVER_HOUR).mod(24)
            val nowSecs = nowMillis / 1000
            val currentOffsetSecs = timeZone.offsetAt(Instant.fromEpochSeconds(nowSecs)).totalSeconds.toLong()
            val creationMinutesWest = (configuration["creationOffset"] as? JsonPrimitive)?.intOrNull
            return if (creationMinutesWest != null) {
                withCreationOffset(creationSecs, -creationMinutesWest.coerceIn(-MAX_OFFSET_MINUTES, MAX_OFFSET_MINUTES) * 60L, nowMillis, currentOffsetSecs, rolloverHour)
            } else {
                withLegacyCutoff(creationSecs, nowMillis, currentOffsetSecs, rolloverHour)
            }
        }

        fun withCreationOffset(creationSecs: Long, creationOffsetSecs: Long, nowMillis: Long, currentOffsetSecs: Long, rolloverHour: Int): SchedulerTiming {
            val nowSecs = nowMillis / 1000
            val nowLocalDay = (nowSecs + currentOffsetSecs).floorDiv(SECONDS_PER_DAY.toLong())
            val createdLocalDay = (creationSecs + creationOffsetSecs).floorDiv(SECONDS_PER_DAY.toLong())
            val rolloverToday = nowLocalDay * SECONDS_PER_DAY + rolloverHour * 3600L - currentOffsetSecs
            val rolloverPassed = rolloverToday <= nowSecs
            val nextDayAt = if (rolloverPassed) rolloverToday + SECONDS_PER_DAY else rolloverToday
            val days = (nowLocalDay - createdLocalDay).let { if (rolloverPassed) it else it - 1 }
            return SchedulerTiming(nowMillis, days.coerceAtLeast(0).toInt(), nextDayAt)
        }

        fun withLegacyCutoff(creationSecs: Long, nowMillis: Long, currentOffsetSecs: Long, rolloverHour: Int): SchedulerTiming {
            val nowSecs = nowMillis / 1000
            val creationLocalDay = (creationSecs + currentOffsetSecs).floorDiv(SECONDS_PER_DAY.toLong())
            val creationAtRollover = creationLocalDay * SECONDS_PER_DAY + rolloverHour * 3600L - currentOffsetSecs
            val daysElapsed = (nowSecs - creationAtRollover) / SECONDS_PER_DAY
            val nowLocalDay = (nowSecs + currentOffsetSecs).floorDiv(SECONDS_PER_DAY.toLong())
            var nextDayAt = nowLocalDay * SECONDS_PER_DAY + rolloverHour * 3600L - currentOffsetSecs
            if (nextDayAt < nowSecs) nextDayAt += SECONDS_PER_DAY
            return SchedulerTiming(nowMillis, daysElapsed.coerceAtLeast(0).toInt(), nextDayAt)
        }

        private const val MAX_OFFSET_MINUTES = 23 * 60
    }
}
