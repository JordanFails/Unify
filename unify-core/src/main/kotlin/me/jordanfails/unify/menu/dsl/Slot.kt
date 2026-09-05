package me.jordanfails.unify.menu.dsl

/**
 * Inventory slot helpers. Rows and columns are **0-indexed**.
 *
 * ```kotlin
 * slot(1, 4)  // row 1, col 4 → inventory slot 13
 * center(6)   // center of a 6-row chest → 31
 * row(2)      // slots 18..26
 * ```
 */
object Slots {

    /** Convert 0-based row/col to a chest slot index. */
    fun of(row: Int, col: Int): Int {
        require(row in 0..5) { "row must be 0..5, got $row" }
        require(col in 0..8) { "col must be 0..8, got $col" }
        return row * 9 + col
    }

    /** All slots in a 0-based row. */
    fun row(row: Int): IntRange {
        require(row in 0..5) { "row must be 0..5, got $row" }
        val start = row * 9
        return start until (start + 9)
    }

    /** All slots in a 0-based column across [rows] rows. */
    fun column(col: Int, rows: Int = 6): List<Int> {
        require(col in 0..8) { "col must be 0..8, got $col" }
        require(rows in 1..6) { "rows must be 1..6, got $rows" }
        return (0 until rows).map { row -> of(row, col) }
    }

    /** Center slot of a chest with the given row count. */
    fun center(rows: Int = 6): Int {
        require(rows in 1..6)
        return of(rows / 2, 4)
    }

    /** Bottom-center slot (common for back buttons). */
    fun bottomCenter(rows: Int = 6): Int = of(rows - 1, 4)

    /** Corners of a chest: top-left, top-right, bottom-left, bottom-right. */
    fun corners(rows: Int = 6): List<Int> = listOf(
        of(0, 0),
        of(0, 8),
        of(rows - 1, 0),
        of(rows - 1, 8)
    )

    /**
     * Inner (non-border) slots for a bordered chest of [rows] rows.
     * Matches [me.jordanfails.unify.menu.menus.menus.BorderedMenu] geometry.
     */
    fun inner(rows: Int = 6): List<Int> {
        require(rows in 3..6) { "bordered inner needs at least 3 rows" }
        val result = mutableListOf<Int>()
        for (row in 1 until rows - 1) {
            for (col in 1..7) {
                result += of(row, col)
            }
        }
        return result
    }
}

/** Convenience: `slot(1, 4)` → 13. */
fun slot(row: Int, col: Int): Int = Slots.of(row, col)
