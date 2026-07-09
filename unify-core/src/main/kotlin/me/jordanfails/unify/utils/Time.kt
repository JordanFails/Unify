package me.jordanfails.unify.utils

import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

object Time {

    /** Sentinel for permanent / never-expires durations. */
    const val PERMANENT: Long = Long.MAX_VALUE

    private const val SECOND_MS = 1000L
    private const val MINUTE_MS = 60 * SECOND_MS
    private const val HOUR_MS = 60 * MINUTE_MS
    private const val DAY_MS = 24 * HOUR_MS

    private val fullDateFormat = ThreadLocal.withInitial {
        SimpleDateFormat("MM/dd/yyyy hh:mm:ss a", Locale.US)
    }

    private val shortDateFormat = ThreadLocal.withInitial {
        SimpleDateFormat("MM/dd/yy", Locale.US)
    }

    private val shortTimeFormat = ThreadLocal.withInitial {
        SimpleDateFormat("hh:mm a", Locale.US)
    }

    private val SECONDS_FORMAT = ThreadLocal.withInitial { DecimalFormat("0") }
    private val TRAILING_FORMAT = ThreadLocal.withInitial { DecimalFormat("0.0") }

    /** Current epoch millis. Returns e.g. `1710000000000`. */
    fun now(): Long = System.currentTimeMillis()

    /**
     * Short calendar date.
     * Returns e.g. `"07/09/26"`.
     */
    fun shortDate(millis: Long = now()): String = shortDateFormat.get().format(Date(millis))

    /**
     * Full calendar date + time (U.S. 12-hour).
     * Returns e.g. `"07/09/2026 11:05:00 AM"`.
     */
    fun fullDate(millis: Long = now()): String = fullDateFormat.get().format(Date(millis))

    /**
     * Clock time only (U.S. 12-hour).
     * Returns e.g. `"11:05 AM"`.
     */
    fun shortTime(millis: Long = now()): String = shortTimeFormat.get().format(Date(millis))

    /**
     * Alias for [fullDate].
     * Returns e.g. `"07/09/2026 11:05:00 AM"`.
     */
    fun formatDate(value: Long): String = fullDate(value)

    /**
     * Alias for [fullDate] using the current time.
     * Returns e.g. `"07/09/2026 11:05:00 AM"`.
     */
    fun currentDate(): String = fullDate()

    /**
     * Alias for [fullDate].
     * Returns e.g. `"07/09/2026 11:05:00 AM"`.
     */
    fun getDate(date: Long): String = fullDate(date)

    /**
     * Long human-readable duration (all non-zero units).
     * Returns e.g. `"1 day 2 hrs 5 mins 3 secs"`, `"Permanent"`, or `"0s"`.
     */
    fun formatDuration(millis: Long): String {
        if (millis == PERMANENT) return "Permanent"
        if (millis <= 0L) return "0s"

        var time = millis
        val days = TimeUnit.MILLISECONDS.toDays(time).also { time -= TimeUnit.DAYS.toMillis(it) }
        val hours = TimeUnit.MILLISECONDS.toHours(time).also { time -= TimeUnit.HOURS.toMillis(it) }
        val minutes = TimeUnit.MILLISECONDS.toMinutes(time).also { time -= TimeUnit.MINUTES.toMillis(it) }
        val seconds = TimeUnit.MILLISECONDS.toSeconds(time)

        val parts = mutableListOf<String>()
        if (days > 0) parts += "$days day${if (days != 1L) "s" else ""}"
        if (hours > 0) parts += "$hours hr${if (hours != 1L) "s" else ""}"
        if (minutes > 0) parts += "$minutes min${if (minutes != 1L) "s" else ""}"
        if (seconds > 0 || parts.isEmpty()) parts += "$seconds sec${if (seconds != 1L) "s" else ""}"

        return parts.joinToString(" ")
    }

    /**
     * Alias for [formatDuration].
     * Returns e.g. `"1 day 2 hrs 5 mins 3 secs"`.
     */
    fun getDuration(input: Long): String = formatDuration(input)

    /**
     * Alias for [formatDuration].
     * Returns e.g. `"1 day 2 hrs 5 mins 3 secs"`.
     */
    fun formatMillis(millis: Long): String = formatDuration(millis)

    /**
     * Compact duration, at most ~2 units (`d`/`h`/`m`/`s`).
     * Returns e.g. `"1d 2h"`, `"5m 3s"`, or `"0s"`.
     */
    fun formatCompact(millis: Long): String {
        if (millis <= 0L) return "0s"
        var time = millis
        val days = time / DAY_MS; time %= DAY_MS
        val hours = time / HOUR_MS; time %= HOUR_MS
        val mins = time / MINUTE_MS; time %= MINUTE_MS
        val secs = time / SECOND_MS

        val parts = mutableListOf<String>()
        if (days > 0) parts += "${days}d"
        if (hours > 0) parts += "${hours}h"
        if (mins > 0) parts += "${mins}m"
        if ((secs > 0 && parts.size < 2) || parts.isEmpty()) parts += "${secs}s"

        return parts.joinToString(" ")
    }

    /**
     * Digital timer from millis (Int overload).
     * Returns e.g. `"01:05"` or `"01:02:03"` when hours > 0.
     */
    fun formatDigital(i: Int): String = formatDigital(i.toLong())

    /**
     * Digital timer from millis.
     * Returns e.g. `"01:05"`, `"01:02:03"`, or with [showMillis] `"01:05.50"`.
     */
    fun formatDigital(millis: Long, showMillis: Boolean = false): String =
        formatDigital(millis, showMillis, false)

    /**
     * Digital timer from millis.
     * Returns e.g. `"01:05"` / `"01:02:03"`; with [milliseconds] appends a fraction —
     * `"01:05.0"` ([trail]=false) or `"01:05.5"` ([trail]=true, one decimal).
     */
    fun formatDigital(duration: Long, milliseconds: Boolean, trail: Boolean): String {
        var remaining = duration
        val hours = TimeUnit.MILLISECONDS.toHours(remaining)
        remaining -= TimeUnit.HOURS.toMillis(hours)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(remaining)
        remaining -= TimeUnit.MINUTES.toMillis(minutes)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(remaining)
        remaining -= TimeUnit.SECONDS.toMillis(seconds)

        val secondsFmt = if (trail) TRAILING_FORMAT.get() else SECONDS_FORMAT.get()

        val base = if (hours > 0)
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        else
            String.format("%02d:%02d", minutes, seconds)

        return if (milliseconds)
            "$base.${secondsFmt.format(remaining / 1000.0)}"
        else
            base
    }

    /**
     * Digital timer without fractional seconds (alias for [formatDigital] with no millis).
     * Returns e.g. `"01:05"` or `"01:02:03"`.
     */
    fun formatMillisBackdrop(millis: Long): String = formatDigital(millis, false)

    /**
     * Relative compact time from now to [targetMillis].
     * Returns e.g. `"5m 3s ago"` or `"in 1h 2m"`.
     */
    fun timeAgo(targetMillis: Long): String {
        val diff = now() - targetMillis
        return if (diff < 0) "in ${formatCompact(abs(diff))}" else "${formatCompact(diff)} ago"
    }

    /**
     * Relative long duration from now to [date].
     * Returns e.g. `"1 day 2 hrs ago"` or `"5 mins from now"`.
     */
    fun formatDateDifference(date: Long): String {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = date }
        return formatDateDifference(now, then)
    }

    /**
     * Relative long duration between two calendars.
     * Returns e.g. `"1 day 2 hrs ago"` or `"5 mins from now"`.
     */
    fun formatDateDifference(fromDate: Calendar, toDate: Calendar): String {
        val future = toDate.after(fromDate)
        val label = formatDuration(abs(toDate.timeInMillis - fromDate.timeInMillis))
        return if (future) "$label from now" else "$label ago"
    }

    /**
     * Day count between calendars (`to - from`). [type]/[future] unused (API compat).
     * Returns e.g. `3` or `-1`.
     */
    @Suppress("UNUSED_PARAMETER")
    fun formatDateDifference(
        type: Int,
        fromDate: Calendar,
        toDate: Calendar?,
        future: Boolean
    ): Int {
        val target = toDate ?: Calendar.getInstance()
        return TimeUnit.MILLISECONDS.toDays(target.timeInMillis - fromDate.timeInMillis).toInt()
    }

    /**
     * Single rounded unit for a duration.
     * Returns e.g. `"1h"`, `"45m"`, `"12s"` (nearest unit).
     */
    fun rounded(millis: Long): String {
        val s = millis / SECOND_MS.toDouble()
        val m = s / 60
        val h = m / 60
        val d = h / 24
        return when {
            d >= 1 -> "${d.roundToInt()}d"
            h >= 1 -> "${h.roundToInt()}h"
            m >= 1 -> "${m.roundToInt()}m"
            else -> "${s.roundToInt()}s"
        }
    }

    /**
     * Alias for [rounded].
     * Returns e.g. `"1h"`, `"45m"`, `"12s"`.
     */
    fun roundedTime(millis: Long): String = rounded(millis)

    /**
     * Single floored unit for a duration (no rounding up).
     * Returns e.g. `"1d"`, `"3h"`, `"0s"`.
     */
    fun smallRoundedTime(millis: Long): String = when {
        millis >= DAY_MS -> "${millis / DAY_MS}d"
        millis >= HOUR_MS -> "${millis / HOUR_MS}h"
        millis >= MINUTE_MS -> "${millis / MINUTE_MS}m"
        else -> "${millis / SECOND_MS}s"
    }

    /**
     * Parses a compact duration string into millis.
     * `"1d 2h 30m"` → `95400000`; blank / unmatched → `0`.
     */
    fun parseDuration(input: String): Long {
        if (input.isBlank()) return 0L
        val regex = "(\\d+)\\s*([dhms])".toRegex()
        var total = 0L
        for (match in regex.findAll(input.lowercase(Locale.US))) {
            val (value, unit) = match.destructured
            total += convert(value.toInt(), unit.first())
        }
        return total
    }

    /**
     * Alias for [parseDuration].
     * `"1h30m"` → `5400000`.
     */
    fun getDuration(input: String): Long = parseDuration(input)

    /**
     * Converts a count + unit char into millis.
     * `convert(5, 'm')` → `300000`; unknown unit → `0`.
     */
    fun convert(value: Int, charType: Char): Long = when (charType.lowercaseChar()) {
        's' -> value * SECOND_MS
        'm' -> value * MINUTE_MS
        'h' -> value * HOUR_MS
        'd' -> value * DAY_MS
        else -> 0L
    }

    /**
     * Alias for [toSeconds].
     * `65000` → `65`.
     */
    fun convert(value: Long): Int = toSeconds(value)

    /**
     * Millis → whole seconds (truncated).
     * `1500` → `1`.
     */
    fun toSeconds(millis: Long): Int = (millis / SECOND_MS).toInt()

    /**
     * Whole seconds → millis.
     * `5` → `5000`.
     */
    fun toMillis(seconds: Int): Long = seconds * SECOND_MS

    /**
     * Splits fractional seconds into whole seconds + leftover millis.
     * `0.95` → `(0, 950)`; `1.095` → `(1, 95)`.
     */
    fun seconds(value: Double): Pair<Int, Int> {
        val totalMillis = (abs(value) * SECOND_MS).roundToInt()
        return (totalMillis / SECOND_MS.toInt()) to (totalMillis % SECOND_MS.toInt())
    }

    /**
     * Whether [target] epoch millis is at or before now.
     * Returns `true` / `false`.
     */
    fun hasPassed(target: Long): Boolean = now() >= target

    /**
     * Whether `now - startTime >= duration`.
     * Returns `true` / `false`.
     */
    fun isExpired(startTime: Long, duration: Long): Boolean = now() - startTime >= duration

    /**
     * Millis until [targetMillis] (can be negative if past).
     * Returns e.g. `5000` or `-200`.
     */
    fun timeUntil(targetMillis: Long): Long = targetMillis - now()

    /**
     * Non-negative millis left until [targetMillis] (clamped at 0).
     * Returns e.g. `5000` or `0`.
     */
    fun remaining(targetMillis: Long): Long = (targetMillis - now()).coerceAtLeast(0)

    /**
     * Prefixed remaining duration label.
     * Returns e.g. `"Time left: 5 mins 3 secs"` or `"Time left: Expired"`.
     */
    fun timeLeftLabel(target: Long, prefix: String = "Time left: "): String {
        val remain = remaining(target)
        return if (remain <= 0) prefix + "Expired" else prefix + formatDuration(remain)
    }

    /**
     * Epoch millis that is [addMillis] after now.
     * Returns e.g. `now() + 60000`.
     */
    fun future(addMillis: Long): Long = now() + addMillis

    /**
     * Random future epoch between now+[min] and now+[max] (exclusive max offset).
     * Returns e.g. a millis timestamp in that range.
     */
    fun randomDuration(min: Long, max: Long): Long = now() + ThreadLocalRandom.current().nextLong(min, max)

    /**
     * Absolute difference of two timestamps as a long duration label.
     * Returns e.g. `"5 mins 3 secs"`.
     */
    fun differenceLabel(first: Long, second: Long): String = formatDuration(abs(first - second))

    /** Sleeps [millis], swallowing [InterruptedException]. Returns nothing. */
    fun safeSleep(millis: Long) {
        try {
            Thread.sleep(millis)
        } catch (_: InterruptedException) {
        }
    }
}
