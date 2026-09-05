package me.jordanfails.unify.menu.dsl

import me.jordanfails.unify.menu.Button

/**
 * ASCII layout mask for chest menus — the feature power-users expect.
 *
 * ```kotlin
 * val mask = layoutMask(
 *     "#########",
 *     "#.......#",
 *     "#.......#",
 *     "#.......#",
 *     "#.......#",
 *     "##B###C##",
 * )
 *
 * // `.`  → content slots (in reading order)
 * // `#`  → decorative / ignored by content fill
 * // `B`  → named slot you bind manually
 * // `C`  → named slot you bind manually
 *
 * mask.attach {
 *     content(items)           // fills `.` slots in order
 *     bind('B', backButton)
 *     bind('C', closeButton)
 *     fill('#', glass)         // optional
 * }
 * ```
 *
 * Each row must be exactly 9 characters. Up to 6 rows.
 */
class LayoutMask private constructor(private val rows: List<String>) {

    val rowCount: Int get() = rows.size
    val size: Int get() = rowCount * 9

    init {
        require(rows.isNotEmpty()) { "mask needs at least one row" }
        require(rows.size <= 6) { "mask supports at most 6 rows" }
        rows.forEachIndexed { i, row ->
            require(row.length == 9) {
                "mask row $i must be 9 characters, got ${row.length}: \"$row\""
            }
        }
    }

    /** All slots whose character equals [char], in reading order. */
    fun slots(char: Char): List<Int> {
        val result = mutableListOf<Int>()
        rows.forEachIndexed { row, line ->
            line.forEachIndexed { col, c ->
                if (c == char) result += row * 9 + col
            }
        }
        return result
    }

    /** Content slots — prefers `.`, then `o` / `O`. */
    fun contentSlots(): List<Int> {
        val dots = slots('.')
        if (dots.isNotEmpty()) return dots
        val lower = slots('o')
        if (lower.isNotEmpty()) return lower
        return slots('O')
    }

    /** First slot for [char], or null. */
    fun first(char: Char): Int? = slots(char).firstOrNull()

    /**
     * Apply this mask into a button map via [MaskAttachment].
     */
    fun attach(
        target: MutableMap<Int, Button> = mutableMapOf(),
        block: MaskAttachment.() -> Unit
    ): MutableMap<Int, Button> {
        MaskAttachment(this, target).apply(block)
        return target
    }

    companion object {
        fun of(vararg rows: String): LayoutMask = LayoutMask(rows.toList())

        fun of(pattern: String): LayoutMask {
            val lines = pattern.trimIndent()
                .lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            return LayoutMask(lines)
        }
    }
}

/** Create a [LayoutMask] from row strings. */
fun layoutMask(vararg rows: String): LayoutMask = LayoutMask.of(*rows)

/** Create a [LayoutMask] from a multiline string. */
fun layoutMask(pattern: String): LayoutMask = LayoutMask.of(pattern)

/**
 * Receiver used by [LayoutMask.attach].
 */
@MenuDslMarker
class MaskAttachment(
    private val mask: LayoutMask,
    private val target: MutableMap<Int, Button>
) {
    /** Fill every slot matching [char] with [button]. */
    fun fill(char: Char, button: Button) {
        for (slot in mask.slots(char)) {
            target[slot] = button
        }
    }

    /** Bind a single button to the first slot of [char]. */
    fun bind(char: Char, button: Button) {
        val slot = mask.first(char)
            ?: error("mask has no slots for character '$char'")
        target[slot] = button
    }

    /** Bind buttons to all slots of [char] in order. */
    fun bindAll(char: Char, buttons: List<Button>) {
        val slots = mask.slots(char)
        buttons.zip(slots).forEach { (button, slot) ->
            target[slot] = button
        }
    }

    /**
     * Fill content characters (`.`) with [buttons] in reading order.
     * Extra buttons are ignored; extra slots are left empty.
     */
    fun content(buttons: List<Button>) {
        val slots = mask.contentSlots()
        buttons.zip(slots).forEach { (button, slot) ->
            target[slot] = button
        }
    }

    fun content(vararg buttons: Button) = content(buttons.toList())
}
