package me.jordanfails.unify.menu.menus.menus

import com.cryptomorin.xseries.XMaterial
import me.jordanfails.unify.menu.Button
import me.jordanfails.unify.menu.Menu
import me.jordanfails.unify.nms.LegacyColorDataType
import me.jordanfails.unify.nms.LegacyItemColor
import me.jordanfails.unify.nms.NMSHandlerFactory
import org.bukkit.Material
import org.bukkit.entity.Player
import kotlin.math.ceil

abstract class BorderedMenu(
    private val emptyBorderSlots: Set<Int> = emptySet()
) : Menu() {

    init {
        // You can enable auto-update for all bordered menus, useful for animations or data refresh.
        autoUpdate = true
    }

    /**
     * Override this to fill the *content* (inner area) of your menu with actual buttons.
     * This should not include border buttons.
     */
    abstract fun getContentButtons(player: Player): MutableMap<Int, Button>

    /**
     * Whether to render a border around the menu.
     * Override to `false` if you want a full-screen layout.
     */
    open fun hasBorder(): Boolean = true

    /**
     * The placeholder (visual filler) button used for the border slots.
     */
    open fun getPlaceholderButton(): Button =
        Button.placeholder(
            XMaterial.GRAY_STAINED_GLASS_PANE,
            NMSHandlerFactory.getHandler()?.getLegacyColorData(LegacyItemColor.GRAY, LegacyColorDataType.BLOCK)
                ?: LegacyItemColor.GRAY.blockData,
            " "
        )

    /**
     * Composes the menu layout with both borders and content.
     * Priority order: Border (lowest) -> Content (highest)
     */
    final override fun getButtons(player: Player): MutableMap<Int, Button> {
        val buttons = mutableMapOf<Int, Button>()

        // ── ① Get content buttons FIRST to determine size
        val contentButtons = getContentButtons(player)

        // ── ② Border (LOWEST PRIORITY - pure decoration)
        if (hasBorder()) {
            fillBorder(buttons, contentButtons)
        }

        // ── ③ Content buttons (HIGHEST PRIORITY - overwrites border if slots overlap)
        buttons.putAll(contentButtons)

        return buttons
    }

    /**
     * Creates the border by filling all outer slots (top, bottom, left, right).
     * Uses the content buttons to determine the total size.
     */
    protected fun fillBorder(buttons: MutableMap<Int, Button>, contentButtons: Map<Int, Button>) {
        val placeholder = getPlaceholderButton()

        // Calculate size from content buttons (just like base CubedMenu does)
        val totalSlots = calculateSize(contentButtons)
        val rows = totalSlots / 9

        for (slot in 0 until totalSlots) {
            val row = slot / 9
            val col = slot % 9

            if (row == 0 || row == rows - 1 || col == 0 || col == 8) {
                if (!emptyBorderSlots.contains(slot)) {
                    buttons[slot] = placeholder
                }
            }
        }
    }

    /**
     * Calculate menu size from button positions (same logic as base CubedMenu.size())
     */
    private fun calculateSize(buttons: Map<Int, Button>): Int {
        if (buttons.isEmpty()) return getMinSize().takeIf { it > 0 } ?: 27 // Default to 3 rows minimum

        var highest = 0
        for (slot in buttons.keys) {
            if (slot > highest) {
                highest = slot
            }
        }

        val calculatedSize = (ceil((highest + 1) / 9.0) * 9.0).toInt()
        val minSize = getMinSize()

        return if (minSize > 0 && minSize > calculatedSize) {
            minSize
        } else {
            calculatedSize
        }
    }

    /**
     * Returns all *inner* slot indexes (excluding the border) —
     * very handy for placing dynamic items programmatically.
     * Automatically excludes slots occupied by content buttons.
     */
    protected fun getInnerSlots(player: Player): List<Int> {
        // Get content buttons to determine which slots are used
        val contentButtons = getContentButtons(player)
        val totalSlots = calculateSize(contentButtons)
        val rows = totalSlots / 9
        val inner = mutableListOf<Int>()

        // Get reserved slots (border + content)
        val reservedSlots = mutableSetOf<Int>()
        reservedSlots.addAll(contentButtons.keys)

        // Add border slots
        for (slot in 0 until totalSlots) {
            val row = slot / 9
            val col = slot % 9
            if (row == 0 || row == rows - 1 || col == 0 || col == 8) {
                reservedSlots.add(slot)
            }
        }

        // All non-reserved slots are inner/available slots
        for (slot in 0 until totalSlots) {
            if (slot !in reservedSlots) {
                inner.add(slot)
            }
        }

        return inner
    }

    /**
     * Get the total menu size for this player.
     * Useful for calculating positions programmatically.
     */
    protected fun getMenuSize(player: Player): Int {
        val contentButtons = getContentButtons(player)
        return calculateSize(contentButtons)
    }
}
