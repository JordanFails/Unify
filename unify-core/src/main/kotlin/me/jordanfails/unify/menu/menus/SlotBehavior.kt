package me.jordanfails.unify.menu.menus

enum class SlotBehavior {
    /** Button only, no player interaction allowed */
    FIXED,
    /** Players can both place and take items */
    INTERACTIVE,
    /** Players can only place items (deposit) */
    INPUT_ONLY,
    /** Players can only take items (withdraw) */
    OUTPUT_ONLY
}