package me.jordanfails.unify.menu

/**
 * Where a bordered menu paints its decorative filler.
 *
 * The frame is the outer edge (top row, bottom row, first/last column); "inner" is everything else,
 * i.e. the content region. Filler never covers a real button — it only lands on slots that are still
 * empty once content and frame controls have been placed.
 *
 * - [BORDER] — frame only (the classic look, and the default)
 * - [INNER] — non-border slots only; the frame is left bare
 * - [BOTH] — frame *and* the leftover inner slots
 * - [NONE] — no filler at all
 *
 * The border and inner fills use separate materials, so [BOTH] can paint a gray frame around a black
 * background. See `BorderedMenu.borderMaterial` / `BorderedMenu.fillMaterial`.
 */
enum class MenuFillMode(
    /** Whether the outer frame gets glass. */
    val fillsBorder: Boolean,
    /** Whether leftover non-border slots get glass. */
    val fillsInner: Boolean,
) {
    BORDER(fillsBorder = true, fillsInner = false),
    INNER(fillsBorder = false, fillsInner = true),
    BOTH(fillsBorder = true, fillsInner = true),
    NONE(fillsBorder = false, fillsInner = false),
}
