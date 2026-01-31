package me.jordanfails.unify.utils

import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

object Time {

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

    fun now(): Long = System.currentTimeMillis()

    fun shortDate(millis: Long = now()): String = shortDateFormat.get().format(Date(millis))

    fun fullDate(millis: Long = now()): String = fullDateFormat.get().format(Date(millis))

    fun shortTime(millis: Long = now()): String = shortTimeFormat.get().format(Date(millis))

    fun formatDuration(millis: Long): String {
        if (millis <= 0L) return "0s"
        if (millis == PERMANENT) return "Permanent"

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

    fun formatDigital(millis: Long, showMillis: Boolean = false): String {
        var remaining = millis
        val hours = TimeUnit.MILLISECONDS.toHours(remaining)
        remaining -= TimeUnit.HOURS.toMillis(hours)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(remaining)
        remaining -= TimeUnit.MINUTES.toMillis(minutes)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(remaining)
        remaining -= TimeUnit.SECONDS.toMillis(seconds)

        val fraction = if (showMillis) ".${(remaining / 10).toString().padStart(2, '0')}" else ""

        return if (hours > 0)
            String.format("%02d:%02d:%02d%s", hours, minutes, seconds, fraction)
        else
            String.format("%02d:%02d%s", minutes, seconds, fraction)
    }

    fun timeAgo(targetMillis: Long): String {
        val diff = now() - targetMillis
        return if (diff < 0) "in ${formatCompact(abs(diff))}" else "${formatCompact(diff)} ago"
    }

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

    fun parseDuration(input: String): Long {
        if (input.isBlank()) return 0L
        val regex = "(\\d+)\\s*([dhms])".toRegex()
        var total = 0L
        for (match in regex.findAll(input.lowercase(Locale.US))) {
            val (value, unit) = match.destructured
            total += when (unit.first()) {
                'd' -> value.toLong() * DAY_MS
                'h' -> value.toLong() * HOUR_MS
                'm' -> value.toLong() * MINUTE_MS
                's' -> value.toLong() * SECOND_MS
                else -> 0L
            }
        }
        return total
    }

    fun toSeconds(millis: Long): Int = (millis / SECOND_MS).toInt()

    fun toMillis(seconds: Int): Long = seconds * SECOND_MS

    fun hasPassed(target: Long): Boolean = now() >= target

    fun isExpired(startTime: Long, duration: Long): Boolean = now() - startTime >= duration

    fun timeUntil(targetMillis: Long): Long = targetMillis - now()

    fun remaining(targetMillis: Long): Long = (targetMillis - now()).coerceAtLeast(0)

    fun timeLeftLabel(target: Long, prefix: String = "Time left: "): String {
        val remain = remaining(target)
        return if (remain <= 0) prefix + "Expired" else prefix + formatDuration(remain)
    }

    fun future(addMillis: Long): Long = now() + addMillis

    fun randomDuration(min: Long, max: Long): Long = now() + ThreadLocalRandom.current().nextLong(min, max)

    fun differenceLabel(first: Long, second: Long): String = formatDuration(abs(first - second))

    fun safeSleep(millis: Long) {
        try {
            Thread.sleep(millis)
        } catch (_: InterruptedException) {
        }
    }
}