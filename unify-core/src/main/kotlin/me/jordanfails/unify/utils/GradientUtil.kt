package me.jordanfails.unify.utils

import me.jordanfails.unify.utils.GradientBuilder
import java.awt.Color

object GradientUtil {

    @JvmStatic
    fun build(
        content: String,
        first: String,
        second: String,
        bold: Boolean = false,
        italic: Boolean = false,
    ): String {
        return GradientBuilder(
            Color.decode(first),
            Color.decode(second),
            content,
            bold,
            italic
        ).build()
    }

    @JvmStatic
    fun buildWrapper(
        vararg hexColors: String,
        bold: Boolean = false,
        italic: Boolean = false,
    ): GradientWrapper {
        require(hexColors.size >= 2) { "At least two colors are required for a gradient." }

        return GradientWrapper(
            colors = hexColors.map(Color::decode),
            bold = bold,
            italic = italic
        )
    }

    class GradientWrapper(
        private val colors: List<Color>,
        private val bold: Boolean,
        private val italic: Boolean,
    ) {

        fun apply(content: String): String {
            require(colors.size >= 2) { "At least two colors are required for a gradient." }
            if (content.isEmpty()) return content

            if (colors.size == 2) {
                return GradientBuilder(
                    colors[0],
                    colors[1],
                    content,
                    bold,
                    italic
                ).build()
            }

            val segmentCount = colors.size - 1
            val segmentLength = content.length.toDouble() / segmentCount
            val result = StringBuilder()

            for (i in 0 until segmentCount) {
                val start = (i * segmentLength).toInt()
                val end = ((i + 1) * segmentLength).toInt().coerceAtMost(content.length)
                if (start >= end) continue

                result.append(
                    GradientBuilder(
                        colors[i],
                        colors[i + 1],
                        content.substring(start, end),
                        bold,
                        italic
                    ).build()
                )
            }

            return result.toString()
        }
    }
}