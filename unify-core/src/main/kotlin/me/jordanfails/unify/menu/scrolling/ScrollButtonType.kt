package me.jordanfails.unify.menu.scrolling

/**
 * Visual style for [ScrollableMenu] navigation buttons.
 *
 * Mirrors [me.jordanfails.unify.menu.menus.PageButtonType] used by paginated menus.
 */
enum class ScrollButtonType {
    /** Textured player-head arrows (default, matches paginated HEAD style). */
    HEAD,

    /** Vanilla arrow items. */
    ARROW,

    /** Paper sheets labeled with direction. */
    PAPER
}
