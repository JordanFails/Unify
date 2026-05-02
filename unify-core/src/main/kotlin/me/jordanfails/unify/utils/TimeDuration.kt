package me.jordanfails.unify.utils

import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Utility class for parsing time duration strings into milliseconds.
 *
 * Supports formats like "10s", "5m", "2h", "1d", or combined "1h30m".
 */
class TimeDuration(val input: String?) {

    var millis: Long = 0

    init {
        if (input != null) {
            millis = parseDuration(input)
        }
    }

    /**
     * Returns the stored duration in milliseconds.
     */
    fun transform(): Long = millis

    companion object {
        // Matches things like "10s", "5m", "2h", "1d"
        private val pattern: Pattern =
            Pattern.compile("(\\d+)([a-zA-Z]+)")

        private fun parseDuration(input: String): Long {
            var total: Long = 0
            val matcher = pattern.matcher(input.replace(" ", ""))

            while (matcher.find()) {
                val value = matcher.group(1)?.toLongOrNull() ?: continue
                val unit = matcher.group(2).lowercase()

                total += when (unit) {
                    "s", "sec", "secs", "second", "seconds" ->
                        TimeUnit.SECONDS.toMillis(value)
                    "m", "min", "mins", "minute", "minutes" ->
                        TimeUnit.MINUTES.toMillis(value)
                    "h", "hr", "hrs", "hour", "hours" ->
                        TimeUnit.HOURS.toMillis(value)
                    "d", "day", "days" ->
                        TimeUnit.DAYS.toMillis(value)
                    else -> 0
                }
            }

            return total
        }

        /**
         * Format a millis duration into a human-readable string.
         * Example: 90061 ms -> "1m30s"
         */
        fun format(millis: Long): String {
            var seconds = millis / 1000
            val days = seconds / 86400
            seconds %= 86400
            val hours = seconds / 3600
            seconds %= 3600
            val minutes = seconds / 60
            seconds %= 60

            val sb = StringBuilder()
            if (days > 0) sb.append("${days}d")
            if (hours > 0) sb.append("${hours}h")
            if (minutes > 0) sb.append("${minutes}m")
            if (seconds > 0) sb.append("${seconds}s")
            if (sb.isEmpty()) sb.append("0s")
            return sb.toString()
        }
    }
}