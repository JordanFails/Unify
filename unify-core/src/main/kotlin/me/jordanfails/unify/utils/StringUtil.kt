package me.jordanfails.unify.utils

import org.bukkit.ChatColor
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.min

object StringUtil {

    private val DECIMAL_FORMAT = DecimalFormat("#.##")
    private val CURRENCY_FORMAT: NumberFormat =
        NumberFormat.getCurrencyInstance(Locale.US)

    @JvmStatic
    @Deprecated("Using old methods", level = DeprecationLevel.ERROR)
    fun color(string: String): String =
        ChatColor.translateAlternateColorCodes('&', string)

    @JvmStatic
    @Deprecated("Using old methods", level = DeprecationLevel.ERROR)
    fun color(strings: List<String>): List<String> =
        strings.map { CC.translate(it) }

    @JvmStatic

    fun strip(string: String): String = ChatColor.stripColor(string) ?: string

    @JvmStatic
    fun getChatColors(string: String): String {
        val sb = StringBuilder()
        val length = string.length

        for (i in 0 until length) {
            val section = string[i]
            if ((section == '&' || section == '§') && i < length - 1) {
                val c = string[i + 1]
                ChatColor.getByChar(c)?.let { sb.append(it) }
            }
        }
        return sb.toString()
    }

    @JvmStatic
    fun repeat(string: String, amount: Int): String =
        buildString {
            repeat(amount) { append(string) }
        }

    @JvmStatic
    fun center(textInput: String): String {
        val text = CC.translate(textInput)

        var messagePxSize = 0
        var previousCode = false
        var isBold = false
        for (c in text.toCharArray()) {
            if (c == '§') {
                previousCode = true
            } else if (previousCode) {
                previousCode = false
                isBold = c == 'l' || c == 'L'
            } else {
                val dFI = DefaultFontInfo.getDefaultFontInfo(c)
                messagePxSize += if (isBold) dFI.boldLength else dFI.length
                messagePxSize++
            }
        }

        val halvedMessageSize = messagePxSize / 2
        val toCompensate = 154 - halvedMessageSize
        val spaceLength = DefaultFontInfo.SPACE.length + 1
        var compensated = 0

        val sb = StringBuilder()
        while (compensated < toCompensate) {
            sb.append(" ")
            compensated += spaceLength
        }
        return sb.toString() + text
    }

    @JvmStatic
    fun getCenterSpaceCount(textInput: String): Int {
        val text = CC.translate(textInput)

        var messagePxSize = 0
        var previousCode = false
        var isBold = false
        for (c in text.toCharArray()) {
            if (c == '§') {
                previousCode = true
            } else if (previousCode) {
                previousCode = false
                isBold = c == 'l' || c == 'L'
            } else {
                val dFI = DefaultFontInfo.getDefaultFontInfo(c)
                messagePxSize += if (isBold) dFI.boldLength else dFI.length
                messagePxSize++
            }
        }

        val halvedMessageSize = messagePxSize / 2
        val toCompensate = 154 - halvedMessageSize
        val spaceLength = DefaultFontInfo.SPACE.length + 1
        var compensated = 0

        var spaceCount = 0
        while (compensated < toCompensate) {
            spaceCount++
            compensated += spaceLength
        }
        return spaceCount
    }

    // ---------- PARSING UTILS ----------
    @JvmStatic
    fun isInt(string: String) = string.toIntOrNull() != null
    @JvmStatic
    fun tryParseInt(string: String): Int = string.toIntOrNull() ?: -1
    @JvmStatic
    fun isLong(string: String) = string.toLongOrNull() != null
    @JvmStatic
    fun tryParseLong(string: String): Long = string.toLongOrNull() ?: -1
    @JvmStatic
    fun isDouble(string: String) = string.toDoubleOrNull() != null
    @JvmStatic
    fun tryParseDouble(string: String): Double = string.toDoubleOrNull() ?: -1.0
    @JvmStatic
    fun stringToInt(string: String?): Int {
        if (string == null) return -1
        val numeric = strip(string.replace("[^0-9]+".toRegex(), ""))
        if (numeric.isEmpty()) return -1
        return tryParseInt(numeric)
    }

    // ---------- FORMATTING ----------
    @JvmStatic
    fun format(template: String?, vararg args: Any?): String {
        if (template.isNullOrEmpty()) return ""
        return args.foldIndexed(template) { i, acc, arg ->
            acc.replace("{$i}", arg?.toString() ?: "")
        }
    }

    @JvmStatic
    fun colorFormat(string: String, vararg arguments: Any?): String =
        CC.translate(format(string, *arguments))
//    @JvmStatic
//    fun formatLore(lore: String): List<String> {
//        val messages = mutableListOf<String>()
//        val translated = CC.translate(lore)
//        var currentLine = StringBuilder()
//        var lastColorCode = ""
//
//        val pattern = Pattern.compile("(§[x][§a-fA-F0-9]{11}|§[a-fA-Fk-oK-OrR]|§r|.)")
//        val matcher = pattern.matcher(translated)
//        var visibleCount = 0
//
//        while (matcher.find()) {
//            val token = matcher.group()
//            if (token.startsWith("§x")) {
//                lastColorCode = token
//                matcher.appendReplacement(currentLine, Matcher.quoteReplacement(token))
//            } else if (token == "§r") {
//                lastColorCode = ""
//                matcher.appendReplacement(currentLine, Matcher.quoteReplacement(token))
//            } else if (token == " ") {
//                matcher.appendReplacement(currentLine, " ")
//                visibleCount++
//            } else if (token.startsWith("§")) {
//                lastColorCode = token
//                matcher.appendReplacement(currentLine, Matcher.quoteReplacement(token))
//            } else {
//                if (visibleCount >= 40 && currentLine.isNotEmpty()) {
//                    messages.add(currentLine.toString())
//                    currentLine = StringBuilder(lastColorCode)
//                    visibleCount = 0
//                }
//                matcher.appendReplacement(currentLine, Matcher.quoteReplacement(token))
//                visibleCount++
//            }
//        }
//        if (currentLine.isNotEmpty()) {
//            messages.add(currentLine.toString())
//        }
//
//        return messages
//    }
    @JvmStatic
    fun formatMoney(number: Double): String =
        CURRENCY_FORMAT.format(number)
    @JvmStatic
    fun formatMoneySymbol(amount: Double): String {
        if (amount < 1000.0) return DECIMAL_FORMAT.format(amount)

        var symbol = "K"
        var divisorOne = 100.0
        var divisorTwo = 10.0

        when {
            amount >= 1_000_000_000.0 -> {
                symbol = "B"
                divisorOne = 1_000_000.0
                divisorTwo = 1_000.0
            }
            amount >= 1_000_000.0 -> {
                symbol = "M"
                divisorOne = 10_000.0
                divisorTwo = 100.0
            }
        }

        val clean = (amount / divisorOne) / divisorTwo
        return "${DECIMAL_FORMAT.format(clean)}$symbol"
    }

//    fun insufficientBalanceMessage(player: Player, amount: Double, action: String): String {
//        val balance = Core.getInstance().econ.getBalance(player)
//        return if (balance < 0) {
//            format(
//                "&cSadly, you're -{0} in debt. You need {1} {2}.",
//                formatMoney(abs(balance)),
//                formatMoney(amount),
//                action
//            )
//        } else {
//            format(
//                "&cYou only have {0}. You need {1} more {2}.",
//                formatMoney(balance),
//                formatMoney(amount - balance),
//                action
//            )
//        }
//    }

    // ---------- PROGRESS BARS ----------
    @JvmStatic
    fun getProgressBar(current: Int, max: Int, totalBars: Int): String =
        getProgressBar(current, max, totalBars, "|", ChatColor.GREEN, ChatColor.RED)
    @JvmStatic
    fun getProgressBar(
        current: Int,
        max: Int,
        totalBars: Int,
        bar: String,
        progressColor: ChatColor,
        leftColor: ChatColor
    ): String {
        val percent = current.toFloat() / max
        val progressBars = (totalBars * percent).toInt()
        val leftOver = totalBars - progressBars

        return buildString {
            append(progressColor)
            repeat(progressBars) { append(bar) }
            append(leftColor)
            repeat(leftOver) { append(bar) }
        }
    }

    // ---------- STRING FORMS ----------
    @JvmStatic
    fun fixPlural(str: String): String =
        if (str.endsWith("s")) "$str'" else "$str's"
    @JvmStatic
    fun toTitleCase(input: String?): String? {
        if (input.isNullOrEmpty()) return input
        return input.split("_").joinToString(" ") {
            it.lowercase().replaceFirstChar(Char::uppercase)
        }
    }
    @JvmStatic
    fun convertToLowerCamelCase(input: String?): String? =
        input?.replace("_", "")?.lowercase()

    // ---------- SIMILARITY ----------
    @JvmStatic
    fun jaroSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        val len1 = s1.length
        val len2 = s2.length
        if (len1 == 0 || len2 == 0) return 0.0

        val matchDistance = max(len1, len2) / 2 - 1
        val s1Matches = BooleanArray(len1)
        val s2Matches = BooleanArray(len2)

        var matches = 0
        for (i in 0 until len1) {
            val start = max(0, i - matchDistance)
            val end = min(i + matchDistance + 1, len2)
            for (j in start until end) {
                if (s2Matches[j]) continue
                if (s1[i] != s2[j]) continue
                s1Matches[i] = true
                s2Matches[j] = true
                matches++
                break
            }
        }

        if (matches == 0) return 0.0

        var t = 0
        var point = 0
        for (i in 0 until len1) {
            if (!s1Matches[i]) continue
            while (!s2Matches[point]) point++
            if (s1[i] != s2[point]) t++
            point++
        }

        return ((matches / len1.toDouble()) +
                (matches / len2.toDouble()) +
                ((matches - t / 2.0) / matches)) / 3.0
    }

    fun isSimilarToMaterial(input: String, materialName: String, threshold: Double): Boolean {
        val similarity = jaroSimilarity(input.lowercase(), materialName.lowercase())
        return similarity >= threshold
    }

    // ---------- ENUM ----------

    enum class DefaultFontInfo(val character: Char, val length: Int) {
        A('A', 5), a('a', 5), B('B', 5), b('b', 5), C('C', 5), c('c', 5),
        D('D', 5), d('d', 5), E('E', 5), e('e', 5), F('F', 5), f('f', 4),
        G('G', 5), g('g', 5), H('H', 5), h('h', 5), I('I', 3), i('i', 1),
        J('J', 5), j('j', 5), K('K', 5), k('k', 4), L('L', 5), l('l', 1),
        M('M', 5), m('m', 5), N('N', 5), n('n', 5), O('O', 5), o('o', 5),
        P('P', 5), p('p', 5), Q('Q', 5), q('q', 5), R('R', 5), r('r', 5),
        S('S', 5), s('s', 5), T('T', 5), t('t', 4), U('U', 5), u('u', 5),
        V('V', 5), v('v', 5), W('W', 5), w('w', 5), X('X', 5), x('x', 5),
        Y('Y', 5), y('y', 5), Z('Z', 5), z('z', 5),
        NUM_1('1', 5), NUM_2('2', 5), NUM_3('3', 5), NUM_4('4', 5),
        NUM_5('5', 5), NUM_6('6', 5), NUM_7('7', 5), NUM_8('8', 5),
        NUM_9('9', 5), NUM_0('0', 5),
        EXCLAMATION_POINT('!', 1), AT_SYMBOL('@', 6), NUM_SIGN('#', 5),
        DOLLAR_SIGN('$', 5), PERCENT('%', 5), UP_ARROW('^', 5),
        AMPERSAND('&', 5), ASTERISK('*', 5), LEFT_PARENTHESIS('(', 4),
        RIGHT_PERENTHESIS(')', 4), MINUS('-', 5), UNDERSCORE('_', 5),
        PLUS_SIGN('+', 5), EQUALS_SIGN('=', 5), LEFT_CURL_BRACE('{', 4),
        RIGHT_CURL_BRACE('}', 4), LEFT_BRACKET('[', 3), RIGHT_BRACKET(']', 3),
        COLON(':', 1), SEMI_COLON(';', 1), DOUBLE_QUOTE('"', 3),
        SINGLE_QUOTE('\'', 1), LEFT_ARROW('<', 4), RIGHT_ARROW('>', 4),
        QUESTION_MARK('?', 5), SLASH('/', 5), BACK_SLASH('\\', 5),
        LINE('|', 1), TILDE('~', 5), TICK('`', 2), PERIOD('.', 1),
        COMMA(',', 1), SPACE(' ', 3), DEFAULT('a', 4);

        companion object {
            fun getDefaultFontInfo(c: Char): DefaultFontInfo =
                entries.find { it.character == c } ?: DEFAULT
        }

        val boldLength: Int
            get() = if (this == SPACE) length else length + 1
    }
}