package com.dime.app.domain.model

import java.time.LocalDate
import java.time.ZoneId

/**
 * Mirror of iOS TimePeriod / TimeFrame enumeration.
 * Used throughout the app to specify which window of data to display.
 */
enum class TimePeriod {
    TODAY,    // iOS "day"
    WEEK,     // iOS "week"
    MONTH,    // iOS "month"
    YEAR,     // iOS "year"
    ALL_TIME  // iOS type == 5
}

/**
 * Convert a [TimePeriod] into epoch-millisecond [start, end) bounds
 * suitable for a Room date range query.
 */
fun TimePeriod.toDateRange(
    firstDayOfWeek: Int = 2, // 1=Sun, 2=Mon (matches iOS UserDefaults "firstWeekday")
    firstDayOfMonth: Int = 1
): Pair<Long, Long> {
    val zone = ZoneId.systemDefault()
    val now = LocalDate.now()

    return when (this) {
        TimePeriod.TODAY -> {
            val start = now.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = now.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            start to end
        }
        TimePeriod.WEEK -> {
            // Find the most recent Monday (or Sunday if firstDayOfWeek==1)
            val targetDow = if (firstDayOfWeek == 1)
                java.time.DayOfWeek.SUNDAY
            else
                java.time.DayOfWeek.MONDAY
            val weekStart = now.with(java.time.temporal.TemporalAdjusters.previousOrSame(targetDow))
            val start = weekStart.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = weekStart.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
            start to end
        }
        TimePeriod.MONTH -> {
            val monthStart = if (firstDayOfMonth <= 1) {
                now.withDayOfMonth(1)
            } else {
                val candidate = now.withDayOfMonth(firstDayOfMonth.coerceAtMost(now.month.length(now.isLeapYear)))
                if (candidate > now) candidate.minusMonths(1) else candidate
            }
            val start = monthStart.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = monthStart.plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli()
            start to end
        }
        TimePeriod.YEAR -> {
            val yearStart = now.withDayOfYear(1)
            val start = yearStart.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = yearStart.plusYears(1).atStartOfDay(zone).toInstant().toEpochMilli()
            start to end
        }
        TimePeriod.ALL_TIME -> {
            0L to Long.MAX_VALUE
        }
    }
}

/**
 * Display labels matching the iOS Localizable.strings wording.
 */
val TimePeriod.label: String
    get() = when (this) {
        TimePeriod.TODAY -> "Today"
        TimePeriod.WEEK -> "This Week"
        TimePeriod.MONTH -> "This Month"
        TimePeriod.YEAR -> "This Year"
        TimePeriod.ALL_TIME -> "All Time"
    }
