package me.jordanfails.unify.utils

object RomanUtil {

    private val romanToIntMap = mapOf(
        'I' to 1,
        'V' to 5,
        'X' to 10,
        'L' to 50,
        'C' to 100,
        'D' to 500,
        'M' to 1000
    )

    private val intToRomanMap = listOf(
        1000 to "M",
        900 to "CM",
        500 to "D",
        400 to "CD",
        100 to "C",
        90 to "XC",
        50 to "L",
        40 to "XL",
        10 to "X",
        9 to "IX",
        5 to "V",
        4 to "IV",
        1 to "I"
    )

    fun romanToInt(roman: String): Int {
        var sum = 0
        var i = 0
        while (i < roman.length) {
            val current = romanToIntMap[roman[i]] ?: 0
            val next = if (i + 1 < roman.length) romanToIntMap[roman[i + 1]] ?: 0 else 0
            if (current < next) {
                sum += next - current
                i += 2
            } else {
                sum += current
                i += 1
            }
        }
        return sum
    }

    fun intToRoman(number: Int): String {
        if (number !in 1..3999 && number != 0) {
            throw IllegalArgumentException("Number must be between 1 and 3999")
        }
        if (number == 0) {
            return "0"
        }
        var num = number
        val result = StringBuilder()
        for ((value, symbol) in intToRomanMap) {
            while (num >= value) {
                result.append(symbol)
                num -= value
            }
        }
        return result.toString()
    }
}