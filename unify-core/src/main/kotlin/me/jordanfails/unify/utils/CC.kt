package me.jordanfails.unify.utils

import org.apache.commons.lang.StringEscapeUtils
import org.apache.commons.lang.StringUtils
import org.bukkit.ChatColor
import java.util.regex.Pattern

object CC {

    // ------------------------------------------------------------------------
    // Unicode Symbols
    // ------------------------------------------------------------------------
    val VERTICAL_LINE = "⎜"
    val DOUBLE_ARROW = "»"
    val CHECK_MARK = "✓"
    val X_MARK = "✗"
    val PIN = "📍"
    val GEM = "❁"
    val HEART = "❤"
    val SWORDS = "⚔"
    val SHIELD = "⛊"
    val SKULL = "☠"
    val PICKAXE = "⛏"
    val ARROW = "➠"
    val ARROW_NEXT = "→"
    val ARROW_LAST = "←"
    val STAR = "✫"
    val STAR_FILLED = "★"

    // ------------------------------------------------------------------------
    // Common Bars
    // ------------------------------------------------------------------------
    val MENU_BAR = "${ChatColor.GRAY}${ChatColor.STRIKETHROUGH}------------------------"
    val CHAT_BAR = "${ChatColor.GRAY}${ChatColor.STRIKETHROUGH}${StringUtils.repeat("-", 35)}"
    val CHAT_BAR_BLUE = "${ChatColor.BLUE}${ChatColor.STRIKETHROUGH}${StringUtils.repeat("-", 35)}"
    val SCOREBOARD_BAR = "${ChatColor.GRAY}${ChatColor.STRIKETHROUGH}----------------------"

    // ------------------------------------------------------------------------
    // Translation Utilities
    // ------------------------------------------------------------------------

    /**
     * Translates color codes including custom hex (#RRGGBB).
     */
    fun translate(input: String): String {
        var msg = input
        val hexPattern = Pattern.compile("#[a-fA-F0-9]{6}")
        var matcher = hexPattern.matcher(msg)

        while (matcher.find()) {
            val hex = msg.substring(matcher.start(), matcher.end())
            val replaced = hex.replace('#', 'x').toCharArray().joinToString("") { "&$it" }
            msg = msg.replace(hex, replaced)
            matcher = hexPattern.matcher(msg)
        }

        return ChatColor.translateAlternateColorCodes('&', msg)
    }

    /**
     * Translates color codes using only legacy (&x) formatting.
     */
    fun translateNoHex(msg: String): String =
        ChatColor.translateAlternateColorCodes('&', msg)

    /**
     * Translates all strings in a list, ignoring null values.
     */
    fun translate(list: List<String?>): List<String> =
        list.filterNotNull().map { translate(it) }

    /**
     * Translates varargs of strings.
     */
    fun translate(vararg strings: String?): Array<String> =
        translate(strings.toList()).toTypedArray()
    /**
     * Creates a stylized clickable text label like "[USE] Command Info".
     */
    fun styleAction(color: ChatColor, clickType: String, text: String): String =
        "${color}${ChatColor.BOLD}$clickType${ChatColor.RESET}$color $text"

    /**
     * Returns a valid ChatColor by name or null if not found.
     */
    fun getValidChatColor(name: String): ChatColor? =
        runCatching { ChatColor.valueOf(name.uppercase()) }.getOrNull()

    /**
     * Formats common color names like "RED" or "GOLD" into Minecraft color codes.
     */
    fun formatNamedColors(input: String): String {
        val colorMap = mapOf(
            "BLACK" to "&0", "DARK_BLUE" to "&1", "DARK_GREEN" to "&2",
            "DARK_AQUA" to "&3", "DARK_RED" to "&4", "DARK_PURPLE" to "&5",
            "GOLD" to "&6", "GRAY" to "&7", "DARK_GRAY" to "&8", "BLUE" to "&9",
            "GREEN" to "&a", "AQUA" to "&b", "RED" to "&c", "LIGHT_PURPLE" to "&d",
            "YELLOW" to "&e", "WHITE" to "&f", "CYAN" to "&3", "LIGHT_BLUE" to "&b"
        )

        return colorMap.entries.fold(input) { acc, (name, code) ->
            acc.replace(name, code, ignoreCase = true)
        }
    }

    /**
     * Checks if string contains only color codes (&, §) or hex codes.
     */
    fun isOnlyColorCodes(input: String): Boolean {
        val standard = "(&|§)[0-9a-fk-orA-FK-OR]"
        val hex = "(&|§)#([0-9a-fA-F]{6})"
        return Pattern.compile("^($standard|$hex)+$").matcher(input).matches()
    }

    /**
     * Returns a colorized boolean — green for true, red for false.
     */
    fun colorBoolean(value: Boolean, trueText: String, falseText: String): String =
        if (value) translate("&a$trueText") else translate("&c$falseText")

    /**
     * Checks if the [ChatColor] is a formatting code (bold, underline, etc.)
     */
    fun isFormattingColor(color: ChatColor): Boolean =
        color in listOf(
            ChatColor.BOLD, ChatColor.ITALIC, ChatColor.UNDERLINE,
            ChatColor.STRIKETHROUGH, ChatColor.MAGIC, ChatColor.RESET
        )

    // ------------------------------------------------------------------------
    // High-Quality Modern HEX Colors
    // ------------------------------------------------------------------------

    val HEX_BLACK = translate("#101010")
    val HEX_DARK_BLUE = translate("#0B3D91")
    val HEX_DARK_GREEN = translate("#006E2E")
    val HEX_DARK_AQUA = translate("#008B8B")
    val HEX_DARK_RED = translate("#8B0000")
    val HEX_DARK_PURPLE = translate("#6A0DAD")
    val HEX_GOLD = translate("#FFB400")
    val HEX_GRAY = translate("#BFBFBF")
    val HEX_DARK_GRAY = translate("#555555")
    val HEX_BLUE = translate("#3C75FF")
    val HEX_GREEN = translate("#00FF66")
    val HEX_AQUA = translate("#00FFFF")
    val HEX_RED = translate("#FF4C4C")
    val HEX_LIGHT_PURPLE = translate("#FF55FF")
    val HEX_YELLOW = translate("#FFFF55")
    val HEX_WHITE = translate("#FFFFFF")

    // Accents
    val HEX_ORANGE = translate("#FFA500")
    val HEX_PINK = translate("#FF77B4")
    val HEX_LIGHT_BLUE = translate("#5AC8FA")
    val HEX_LIME = translate("#B6FF00")
    val HEX_TEAL = translate("#00CACA")
    val HEX_PURPLE = translate("#C77DFF")
    val HEX_NAVY = translate("#172B6C")
    val HEX_BROWN = translate("#9C661F")
    val HEX_ROSE = translate("#FF4F81")
    val HEX_SILVER = translate("#D9D9D9")
    val HEX_LIGHT_GRAY = translate("#CDCDCD")
    val HEX_DARK_GOLD = translate("#B8860B")

    // ------------------------------------------------------------------------
    // Additional Utility Functions
    // ------------------------------------------------------------------------

    /**
     * Strips all Minecraft color codes and hex formatting from a string.
     */
    fun stripColors(input: String): String {
        var s = input.replace(Regex("(?i)&[0-9A-FK-ORX]"), "")
        s = s.replace(Regex("(?i)§[0-9A-FK-ORX]"), "")
        s = s.replace(Regex("#[a-fA-F0-9]{6}"), "")
        return s
    }

    /**
     * Builds a simple color gradient between two hex colors as a list of colorized strings.
     * Perfect for fancy title text or UI menus.
     */
    fun gradient(fromHex: String, toHex: String, steps: Int, text: String): List<String> {
        fun hexToRGB(hex: String): Triple<Int, Int, Int> {
            val clean = hex.removePrefix("#")
            val r = clean.substring(0, 2).toInt(16)
            val g = clean.substring(2, 4).toInt(16)
            val b = clean.substring(4, 6).toInt(16)
            return Triple(r, g, b)
        }

        val (r1, g1, b1) = hexToRGB(fromHex)
        val (r2, g2, b2) = hexToRGB(toHex)
        val chars = text.toCharArray()
        val stepR = (r2 - r1).toDouble() / (steps - 1)
        val stepG = (g2 - g1).toDouble() / (steps - 1)
        val stepB = (b2 - b1).toDouble() / (steps - 1)

        return chars.mapIndexed { i, c ->
            val r = (r1 + stepR * i).toInt()
            val g = (g1 + stepG * i).toInt()
            val b = (b1 + stepB * i).toInt()
            translate(String.format("#%02X%02X%02X%s", r, g, b, c))
        }
    }
}