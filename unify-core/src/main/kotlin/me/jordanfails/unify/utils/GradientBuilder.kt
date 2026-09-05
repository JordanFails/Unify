package me.jordanfails.unify.utils

import java.awt.Color
import kotlin.math.roundToInt
import kotlin.text.iterator

class GradientBuilder(
    private val from: Color,
    private val to: Color,
    private val content: String,
    private val bold: Boolean = true,
    private val italic: Boolean = false
) {

    constructor(from: Color, to: Color, content: String) : this(from, to, content, true, false)

    private val gradient = Gradient(from, to, content, bold, italic)

    fun build(): String {
        if (content.isEmpty()) return content

        // Color codes (including §x hex) reset formatting, so bold/italic must be
        // re-applied after every color — a single leading §l/§o is wiped immediately.
        val formatting = buildString {
            if (bold) append("§l")
            if (italic) append("§o")
        }

        val sb = StringBuilder()

        if (content.length == 1) {
            sb.append(toSectionHex(from)).append(formatting).append(content).append("§r")
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
            sb.append(formatting)
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
