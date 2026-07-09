package me.jordanfails.unify.utils

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * Parses human-friendly shorthand amounts like "1k", "1.5m", "2.3b" into exact
 * numeric types. Safe to feed straight into money/economy code since everything
 * is done with BigDecimal (no float/double rounding).
 *
 * Supported suffixes (case-insensitive):
 *   k / K -> thousand   (1e3)
 *   m / M -> million    (1e6)
 *   b / B -> billion    (1e9)
 *   t / T -> trillion   (1e12)
 *
 * Plain numbers with no suffix are parsed as-is. Commas, surrounding
 * whitespace, and a leading "-" are all tolerated.
 *
 * Examples:
 *   ShortHandParser.parse("1k")     -> 1000
 *   ShortHandParser.parse("1.5k")   -> 1500
 *   ShortHandParser.parse("2.3b")   -> 2300000000
 *   ShortHandParser.parse("500")    -> 500
 *   ShortHandParser.parse("1,250")  -> 1250
 *   ShortHandParser.parse("-4.2m")  -> -4200000
 *   ShortHandParser.parse("abc")    -> null
 */
object ShortHandParser {

    private val SUFFIX_MULTIPLIERS: Map<Char, BigDecimal> = mapOf(
        'k' to BigDecimal("1E3"),
        'm' to BigDecimal("1E6"),
        'b' to BigDecimal("1E9"),
        't' to BigDecimal("1E12")
    )

    private val PATTERN = Regex("^(-?\\d+(?:\\.\\d+)?)([kmbtKMBT]?)$")

    /**
     * Parses [input] into a BigDecimal, or returns null if it isn't a
     * recognizable number/shorthand.
     */
    fun parse(input: String): BigDecimal? {
        val cleaned = input.trim().replace(",", "")
        if (cleaned.isEmpty()) return null

        val match = PATTERN.matchEntire(cleaned) ?: return null
        val (numberPart, suffixPart) = match.destructured

        val base = numberPart.toBigDecimalOrNull() ?: return null
        if (suffixPart.isEmpty()) return base

        val multiplier = SUFFIX_MULTIPLIERS[suffixPart.lowercase()[0]] ?: return null
        return base.multiply(multiplier)
    }

    /**
     * Same as [parse], but rounds/truncates to a whole-number BigInteger.
     * Useful when the caller's balance type is an integer amount rather
     * than a decimal currency value.
     */
    fun parseToBigInteger(input: String): BigInteger? =
        parse(input)?.setScale(0, RoundingMode.HALF_UP)?.toBigInteger()

    /**
     * Convenience wrapper that also rejects negative results, since most
     * "amount to offer/pay/withdraw" fields shouldn't accept negatives.
     */
    fun parsePositive(input: String): BigDecimal? {
        val value = parse(input) ?: return null
        return if (value.signum() < 0) null else value
    }

    private fun String.toBigDecimalOrNull(): BigDecimal? =
        try {
            BigDecimal(this)
        } catch (e: NumberFormatException) {
            null
        }
}