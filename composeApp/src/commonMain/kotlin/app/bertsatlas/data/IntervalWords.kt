package app.bertsatlas.data

import app.bertsatlas.domain.fsrs.IntervalKind

object IntervalWords {
    fun describe(interval: IntervalKind): String = when (interval) {
        is IntervalKind.InSeconds -> minutes(interval.seconds)
        is IntervalKind.InDays -> days(interval.days)
    }

    private fun minutes(seconds: Int): String {
        val minutes = (seconds + 59) / 60
        return when {
            minutes < 1 -> "now"
            minutes < 60 -> "$minutes min"
            else -> plural((minutes + 30) / 60, "hour")
        }
    }

    private fun days(days: Int): String = when {
        days < 1 -> "today"
        days < 14 -> plural(days, "day")
        days < 60 -> plural((days + 3) / 7, "week")
        days < 365 -> plural((days + 15) / 30, "month")
        else -> {
            val tenths = (days * 10 + 182) / 365
            if (tenths % 10 == 0) plural(tenths / 10, "year") else "${tenths / 10}.${tenths % 10} years"
        }
    }

    private fun plural(count: Int, unit: String) = if (count == 1) "1 $unit" else "$count ${unit}s"
}
