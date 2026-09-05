package me.jordanfails.unify.menu.menus.menus

import com.cryptomorin.xseries.XMaterial
import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.menu.Button
import me.jordanfails.unify.menu.pagination.PaginatedBorderedMenu
import me.jordanfails.unify.utils.CC
import me.jordanfails.unify.utils.ItemBuilder
import me.jordanfails.unify.utils.Tasks
import me.jordanfails.unify.utils.prompt.InputPrompt
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.InventoryView
import org.bukkit.inventory.ItemStack
import java.util.LinkedList

/**
 * Paginated editor for an ordered list of strings — one button per line.
 *
 * Left-click rewrites a line via chat [InputPrompt], right-click (or middle-click)
 * removes it, shift-left/right reorders it. Changes are written through [onSave]
 * as they happen rather than sitting behind a draft/save step.
 */
abstract class TextEditorMenu(
    lines: Collection<String> = emptyList(),
) : PaginatedBorderedMenu() {

    protected val lines: LinkedList<String> = LinkedList(lines)

    /** When true, `&` / hex colour codes in input are translated before storing. */
    protected var supportsColors: Boolean = true

    /** Soft cap on a single line. `<= 0` means no limit. */
    protected var maxLineLength: Int = 256

    init {
        updateAfterClick = true
    }

    override fun getPrePaginatedTitle(player: Player): String = "&8Text Editor"

    override fun getMinSize(): Int = 9 * 5

    override fun getAllPagesButtons(player: Player): Map<Int, Button> {
        val buttons = LinkedHashMap<Int, Button>()
        lines.forEachIndexed { index, _ ->
            buttons[index] = LineButton(index)
        }
        return buttons
    }

    override fun getGlobalButtons(player: Player): Map<Int, Button> = mapOf(
        2 to AddLineButton(),
        6 to ClearLinesButton(),
    )

    /**
     * Persist the current [lines]. Called after every add / edit / move / delete.
     */
    abstract fun onSave(player: Player, lines: List<String>)

    /**
     * The player left the editor (ESC / close), not because an anvil or confirm
     * temporarily replaced it.
     */
    open fun onClosed(player: Player) {}

    override fun onClose(player: Player, manualClose: Boolean) {
        super.onClose(player, manualClose)
        if (manualClose) onClosed(player)
    }

    /** Hint shown when creating a blank-ish new line. */
    protected open fun newLinePlaceholder(): String = "&7"

    protected open fun formatLine(input: String): String {
        val trimmed = if (maxLineLength > 0) input.take(maxLineLength) else input
        return if (supportsColors) CC.translate(trimmed) else trimmed
    }

    protected open fun toEditable(line: String): String = line.replace('§', '&')

    protected open fun promptText(index: Int, current: String): String {
        return if (index < 0) {
            "&aEnter the new line. &7Type &ecancel &7to go back."
        } else {
            "&aEnter the new text for line ${index + 1}. " +
                "&7Current: &f${current.ifBlank { "(blank)" }} " +
                "&8(&7cancel to go back&8)"
        }
    }

    protected fun persist(player: Player) {
        onSave(player, lines.toList())
    }

    protected fun reopen(player: Player) {
        Tasks.delayed(UnifyCore.instance, 1L) {
            if (!player.isOnline) return@delayed
            manualClose = true
            openMenu(player)
        }
    }

    private fun promptLine(player: Player, index: Int) {
        val adding = index < 0
        val current = if (adding) "" else toEditable(lines.getOrNull(index).orEmpty())

        manualClose = false
        InputPrompt()
            .withText(promptText(index, current))
            .withLimit(maxLineLength)
            .acceptInput { input ->
                val formatted = formatLine(input)
                if (adding) {
                    lines.add(formatted)
                } else if (index in lines.indices) {
                    lines[index] = formatted
                }
                persist(player)
                Button.playSuccess(player)
                reopen(player)
            }
            .onCancel {
                player.sendMessage(CC.translate("&eCancelled."))
                reopen(player)
            }
            .start(player)
    }

    private inner class AddLineButton : Button() {
        override fun getItem(player: Player): ItemStack {
            return ItemBuilder(XMaterial.LIME_DYE)
                .name("&a&lAdd Line")
                .lore(
                    "",
                    "&7Adds a line at the bottom.",
                    "&7Colour codes with &e&&7 work.",
                    "",
                    "&eClick to add",
                    "",
                )
                .build()
        }

        override fun clicked(player: Player, slot: Int, clickType: ClickType, view: InventoryView) {
            playNeutral(player)
            promptLine(player, -1)
        }
    }

    private inner class ClearLinesButton : Button() {
        override fun getItem(player: Player): ItemStack {
            return ItemBuilder(XMaterial.RED_DYE)
                .name("&c&lClear All")
                .lore(
                    "",
                    "&7Removes every line.",
                    "",
                    "&cClick to clear",
                    "",
                )
                .build()
        }

        override fun clicked(player: Player, slot: Int, clickType: ClickType, view: InventoryView) {
            if (lines.isEmpty()) {
                playFail(player)
                return
            }

            playNeutral(player)
            manualClose = false
            ConfirmMenu(
                titleText = "&cClear all lines?",
                extraInfo = mutableListOf("&7This cannot be undone from this menu."),
            ) { confirmed ->
                if (confirmed) {
                    lines.clear()
                    persist(player)
                    playSuccess(player)
                }
                reopen(player)
            }.openMenu(player)
        }
    }

    private inner class LineButton(private val index: Int) : Button() {
        override fun getItem(player: Player): ItemStack {
            val text = lines.getOrNull(index).orEmpty()
            val preview = if (text.isBlank()) "&c&oBlank" else text
            val canMoveUp = index > 0
            val canMoveDown = index < lines.lastIndex
            val left = if (canMoveUp) "&9" else "&8"
            val right = if (canMoveDown) "&9" else "&8"

            return ItemBuilder(XMaterial.PAPER)
                .amount((index + 1).coerceIn(1, 64))
                .name("&f&lLine ${index + 1}")
                .lore(
                    "",
                    preview,
                    "",
                    "&a&lLEFT-CLICK &ato edit this line",
                    "&c&lRIGHT-CLICK &cto remove it",
                    "",
                    "$left&l⬅ &e&lSHIFT LEFT/RIGHT $right&l➡",
                    "",
                )
                .build()
        }

        override fun clicked(player: Player, slot: Int, clickType: ClickType, view: InventoryView) {
            if (index !in lines.indices) return

            if (clickType.isShiftClick && (clickType.isLeftClick || clickType.isRightClick)) {
                val delta = when {
                    clickType.isLeftClick && index > 0 -> -1
                    clickType.isRightClick && index < lines.lastIndex -> 1
                    else -> 0
                }
                if (delta == 0) {
                    playFail(player)
                    return
                }
                val line = lines.removeAt(index)
                lines.add(index + delta, line)
                persist(player)
                playSuccess(player)
                return
            }

            if (clickType.isRightClick || clickType == ClickType.MIDDLE) {
                lines.removeAt(index)
                persist(player)
                playSuccess(player)
                return
            }

            if (clickType.isLeftClick) {
                playNeutral(player)
                promptLine(player, index)
            }
        }
    }

    companion object {
        /**
         * Build an editor without subclassing.
         */
        @JvmStatic
        @JvmOverloads
        fun create(
            lines: Collection<String> = emptyList(),
            supportsColors: Boolean = true,
            save: (Player, List<String>) -> Unit,
            closed: (Player) -> Unit = {},
        ): TextEditorMenu = object : TextEditorMenu(lines) {
            init {
                this.supportsColors = supportsColors
            }

            override fun onSave(player: Player, lines: List<String>) = save(player, lines)

            override fun onClosed(player: Player) = closed(player)
        }
    }
}
