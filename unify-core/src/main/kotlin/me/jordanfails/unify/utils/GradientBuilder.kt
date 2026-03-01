package me.jordanfails.unify.utils

import java.awt.Color
import kotlin.math.roundToInt
import kotlin.text.iterator

/**
 * Reimplementation of AtlantisMC's GradientBuilder.
 * Builds a Minecraft §x hex gradient string from a Gradient definition.
 */
class GradientBuilder(
    private val from: Color,
    private val to: Color,
    private val content: String,
    private val bold: Boolean = true,
    private val italic: Boolean = false
) {

    // Overloaded constructor like in the original stub
    constructor(from: Color, to: Color, content: String) : this(from, to, content, true, false)

    // The original class had a private "gradient" field of type net.atlantismc.bukkit.color.Gradient
    private val gradient = Gradient(from, to, content, bold, italic)

    fun build(): String {
        if (content.isEmpty()) return content

        val sb = StringBuilder()
        if (bold) sb.append("§l")
        if (italic) sb.append("§o")

        if (content.length == 1) {
            sb.append(toSectionHex(from)).append(content).append("§r")
            return sb.toString()
        }

        val steps = content.length - 1
        for (i in content.indices) {
            val ratio = i.toDouble() / steps
            val color = Color(
                interpolate(from.red, to.red, ratio),
                interpolate(from.green, to.green, ratio),
                interpolate(from.blue, to.blue, ratio)
            )
            sb.append(toSectionHex(color))
            sb.append(content[i])
        }

        sb.append("§r")
        return sb.toString()
    }

    private fun interpolate(from: Int, to: Int, ratio: Double): Int {
        return (from + (to - from) * ratio).roundToInt().coerceIn(0, 255)
    }

    private fun toSectionHex(c: Color): String {
        val hex = "%02X%02X%02X".format(c.red, c.green, c.blue)
        return buildString(14) {
            append('§').append('x')
            for (ch in hex) {
                append('§').append(ch)
            }
        }
    }
}
