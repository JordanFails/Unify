package me.jordanfails.unify.utils

object FontUtils {

    private val mapper: Map<Char, Char> = mapOf(
        'A' to 'ᴀ', 'B' to 'ʙ', 'C' to 'ᴄ', 'D' to 'ᴅ', 'E' to 'ᴇ',
        'F' to 'ꜰ', 'G' to 'ɢ', 'H' to 'ʜ', 'I' to 'ɪ', 'J' to 'ᴊ',
        'K' to 'ᴋ', 'L' to 'ʟ', 'M' to 'ᴍ', 'N' to 'ɴ', 'O' to 'ᴏ',
        'P' to 'ᴘ', 'Q' to 'ǫ', 'R' to 'ʀ', 'S' to 'ѕ', 'T' to 'ᴛ',
        'U' to 'ᴜ', 'V' to 'ᴠ', 'W' to 'ᴡ', 'X' to 'х', 'Y' to 'ʏ',
        'Z' to 'ᴢ',
        'a' to 'ᴀ', 'b' to 'ʙ', 'c' to 'ᴄ', 'd' to 'ᴅ', 'e' to 'ᴇ',
        'f' to 'ꜰ', 'g' to 'ɢ', 'h' to 'ʜ', 'i' to 'ɪ', 'j' to 'ᴊ',
        'k' to 'ᴋ', 'l' to 'ʟ', 'm' to 'ᴍ', 'n' to 'ɴ', 'o' to 'ᴏ',
        'p' to 'ᴘ', 'q' to 'ǫ', 'r' to 'ʀ', 's' to 'ѕ', 't' to 'ᴛ',
        'u' to 'ᴜ', 'v' to 'ᴠ', 'w' to 'ᴡ', 'x' to 'х', 'y' to 'ʏ',
        'z' to 'ᴢ',
        '0' to '0', '1' to '1', '2' to '2', '3' to '3', '4' to '4',
        '5' to '5', '6' to '6', '7' to '7', '8' to '8', '9' to '9'
    )

    fun translate(s: String): String {
        val sb = StringBuilder()
        for (char in s) {
            sb.append(mapper[char] ?: char)
        }
        return sb.toString()
    }

    fun translate(s: List<String>): List<String> {
        val toReturn: MutableList<String> = mutableListOf()

        s.forEach {
            toReturn.add(translate(it))
        }

        return toReturn
    }

}