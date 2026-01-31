package me.jordanfails.unify.utils

import java.sql.Timestamp
import java.util.*
import java.util.regex.Pattern
import kotlin.math.floor

enum class TimeUnits(
    val displayName: String,
    val millis: Long,
    val abbreviations: Array<String>
) {
    SECOND("Second", 1_000L, arrayOf("s", "sec", "second", "seconds")),
    MINUTE("Minute", 60_000L, arrayOf("m", "min", "minute", "minutes")),
    HOUR("Hour", 3_600_000L, arrayOf("h", "hr", "hour", "hours")),
    DAY("Day", 86_400_000L, arrayOf("d", "day", "days")),
    MONTH("Month", 2_592_000_000L, arrayOf("mon", "month", "months")),
    YEAR("Year", 31_536_000_000L, arrayOf("y", "year", "years"));

    companion object {
        private val timePattern = Pattern.compile(
            "(?:(\\d+)\\s*y[a-z]*[,\\s]*)?" +
                    "(?:(\\d+)\\s*mo[a-z]*[,\\s]*)?" +
                    "(?:(\\d+)\\s*w[a-z]*[,\\s]*)?" +
                    "(?:(\\d+)\\s*d[a-z]*[,\\s]*)?" +
                    "(?:(\\d+)\\s*h[a-z]*[,\\s]*)?" +
                    "(?:(\\d+)\\s*m[a-z]*[,\\s]*)?" +
                    "(?:(\\d+)\\s*s[a-z]*)?",
            Pattern.CASE_INSENSITIVE
        )

        fun multiply(unit: TimeUnits, multiple: Long): Long = unit.millis * multiple

        fun convertMillisToString(
            millis: Long,
            longFormat: Boolean = false,
            capitalizeUnits: Boolean = false,
            useCommas: Boolean = true
        ): String {
            if (millis <= 0) return "now"

            val units = listOf(YEAR, MONTH, DAY, HOUR, MINUTE, SECOND)
            var remaining = millis
            val timeParts = mutableListOf<String>()

            for (unit in units) {
                val count = floor(remaining / unit.millis.toDouble()).toInt()
                if (count > 0) {
                    remaining -= count * unit.millis
                    val formattedUnit = when {
                        longFormat -> {
                            val base = if (count == 1) unit.displayName else "${unit.displayName}s"
                            if (capitalizeUnits) base else base.lowercase()
                        }
                        else -> {
                            val short = unit.abbreviations.first()
                            if (capitalizeUnits) short.uppercase() else short.lowercase()
                        }
                    }
                    timeParts.add("$count $formattedUnit")
                }
            }

            if (timeParts.isEmpty()) return "now"
            return if (useCommas) timeParts.joinToString(", ") else timeParts.joinToString(" ")
        }

        fun formatDateDiff(from: Calendar, to: Calendar): String {
            val future = to.after(from)
            val sb = StringBuilder()

            val differenceMap = mutableListOf<Pair<Int, String>>()
            val calCopy = from.clone() as Calendar

            val fields = listOf(
                Calendar.YEAR to "year",
                Calendar.MONTH to "month",
                Calendar.DAY_OF_MONTH to "day",
                Calendar.HOUR_OF_DAY to "hour",
                Calendar.MINUTE to "minute",
                Calendar.SECOND to "second"
            )

            var count = 0
            for ((field, name) in fields) {
                val diff = dateDiff(field, calCopy, to, future)
                if (diff > 0) {
                    differenceMap.add(diff to name)
                    count++
                    if (count >= 2) break
                }
            }

            if (differenceMap.isEmpty()) return "now"

            differenceMap.forEachIndexed { i, (diff, name) ->
                sb.append(diff).append(' ')
                    .append(if (diff == 1) name else "${name}s")
                if (i == 0 && differenceMap.size > 1) sb.append(", ")
            }

            return sb.toString()
        }

        private fun dateDiff(field: Int, from: Calendar, to: Calendar, future: Boolean): Int {
            var diff = 0
            val saved = from.timeInMillis
            while ((future && !from.after(to)) || (!future && !from.before(to))) {
                from.add(field, if (future) 1 else -1)
                diff++
            }
            from.timeInMillis = saved
            return maxOf(0, diff - 1)
        }

        fun getTimeUntil(timestamp: Timestamp): String {
            val now = Calendar.getInstance()
            val then = Calendar.getInstance().apply { timeInMillis = timestamp.time }
            return formatDateDiff(now, then)
        }

        @Throws(IllegalArgumentException::class)
        fun timeFromString(input: String, future: Boolean): Long {
            val matcher = timePattern.matcher(input)
            if (!matcher.find()) throw IllegalArgumentException("Illegal date format: $input")

            val years = matcher.group(1)?.toIntOrNull() ?: 0
            val months = matcher.group(2)?.toIntOrNull() ?: 0
            val weeks = matcher.group(3)?.toIntOrNull() ?: 0
            val days = matcher.group(4)?.toIntOrNull() ?: 0
            val hours = matcher.group(5)?.toIntOrNull() ?: 0
            val minutes = matcher.group(6)?.toIntOrNull() ?: 0
            val seconds = matcher.group(7)?.toIntOrNull() ?: 0

            if (years > 50) throw IllegalArgumentException("Year value too large.")

            return Calendar.getInstance().apply {
                val sign = if (future) 1 else -1
                add(Calendar.YEAR, years * sign)
                add(Calendar.MONTH, months * sign)
                add(Calendar.WEEK_OF_YEAR, weeks * sign)
                add(Calendar.DAY_OF_MONTH, days * sign)
                add(Calendar.HOUR_OF_DAY, hours * sign)
                add(Calendar.MINUTE, minutes * sign)
                add(Calendar.SECOND, seconds * sign)
            }.timeInMillis
        }

        fun parse(name: String?): TimeUnits? =
            entries.find { it.displayName.equals(name, ignoreCase = true) }

        fun parseAbbreviation(input: String?): TimeUnits? =
            entries.find { unit ->
                unit.abbreviations.any { it.equals(input, ignoreCase = true) }
            }
    }
}