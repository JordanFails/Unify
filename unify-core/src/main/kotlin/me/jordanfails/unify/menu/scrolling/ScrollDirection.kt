package me.jordanfails.unify.menu.scrolling

/**
 * Cardinal direction used by [ScrollableMenu] navigation.
 */
enum class ScrollDirection(
    val deltaX: Int,
    val deltaY: Int,
    val label: String
) {
    UP(0, -1, "Up"),
    DOWN(0, 1, "Down"),
    LEFT(-1, 0, "Left"),
    RIGHT(1, 0, "Right");

    val isHorizontal: Boolean get() = deltaY == 0
    val isVertical: Boolean get() = deltaX == 0
}
