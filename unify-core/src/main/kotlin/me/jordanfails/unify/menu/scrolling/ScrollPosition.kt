package me.jordanfails.unify.menu.scrolling

/**
 * Absolute coordinate on a [ScrollableMenu] virtual content grid.
 *
 * `(0, 0)` is the top-left of the content. Positive [x] moves right,
 * positive [y] moves down — matching inventory slot math.
 */
data class ScrollPosition(
    val x: Int,
    val y: Int
) {
    operator fun plus(other: ScrollPosition): ScrollPosition =
        ScrollPosition(x + other.x, y + other.y)

    operator fun minus(other: ScrollPosition): ScrollPosition =
        ScrollPosition(x - other.x, y - other.y)

    fun offset(dx: Int, dy: Int): ScrollPosition =
        ScrollPosition(x + dx, y + dy)

    companion object {
        val ORIGIN = ScrollPosition(0, 0)

        fun of(x: Int, y: Int): ScrollPosition = ScrollPosition(x, y)
    }
}
