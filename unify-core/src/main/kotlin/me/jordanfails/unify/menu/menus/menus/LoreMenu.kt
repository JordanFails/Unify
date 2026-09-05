package me.jordanfails.unify.menu.menus.menus

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Line-by-line lore editor for a live [ItemStack].
 *
 * Each add / edit / move / delete writes straight back onto [item], so the
 * held item updates as you go.
 */
class LoreMenu(
    private val item: ItemStack,
    lines: Collection<String> = item.itemMeta?.lore.orEmpty(),
) : TextEditorMenu(lines) {

    init {
        supportsColors = true
    }

    override fun getPrePaginatedTitle(player: Player): String = "&8Item Lore"

    override fun newLinePlaceholder(): String = "&f"

    override fun formatLine(input: String): String {
        val prefixed = when {
            input.startsWith("&") || input.startsWith("#") || input.startsWith("§") -> input
            input.isBlank() -> input
            else -> "&f$input"
        }
        return super.formatLine(prefixed)
    }

    override fun onSave(player: Player, lines: List<String>) {
        writeLore(item, lines)
    }

    companion object {
        @JvmStatic
        @JvmOverloads
        @Suppress("DEPRECATION")
        fun edit(player: Player, item: ItemStack = player.inventory.itemInHand) {
            LoreMenu(item).openMenu(player)
        }

        @JvmStatic
        fun writeLore(item: ItemStack, lines: List<String>) {
            val meta = item.itemMeta ?: return
            meta.lore = lines.ifEmpty { null }
            item.itemMeta = meta
        }
    }
}

/** Open a line-by-line lore editor for [item] (defaults to the item in hand). */
@Suppress("DEPRECATION")
fun Player.editLore(item: ItemStack = inventory.itemInHand) {
    LoreMenu.edit(this, item)
}