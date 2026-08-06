package me.jordanfails.unify.menu.anvil

import me.jordanfails.unify.menu.Menu
import me.jordanfails.unify.nms.NMSHandlerFactory
import me.jordanfails.unify.utils.CC
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Fluent builder for an anvil text-input GUI.
 *
 * ```
 * AnvilInput()
 *     .title("Enter name")
 *     .text("default")
 *     .onComplete { player, text ->
 *         if (text.isBlank()) AnvilResult.retry("Required")
 *         else AnvilResult.close()
 *     }
 *     .open(player)
 * ```
 */
class AnvilInput {

    private var title: String = "Repair & Name"
    private var itemText: String? = null
    private var itemLeft: ItemStack? = null
    private var itemRight: ItemStack? = null
    private var itemOutput: ItemStack? = null
    private var preventClose: Boolean = false
    private var onComplete: ((Player, String) -> AnvilResult)? = null
    private var onClick: ((Int, AnvilState) -> AnvilResult)? = null
    private var onClose: ((Player, String) -> Unit)? = null

    fun title(title: String): AnvilInput {
        this.title = title
        return this
    }

    /** Initial text shown in the rename field (applied as the left item's display name). */
    fun text(text: String): AnvilInput {
        this.itemText = text
        return this
    }

    fun itemLeft(item: ItemStack): AnvilInput {
        this.itemLeft = item
        return this
    }

    fun itemRight(item: ItemStack): AnvilInput {
        this.itemRight = item
        return this
    }

    fun itemOutput(item: ItemStack): AnvilInput {
        this.itemOutput = item
        return this
    }

    /** Prevent the player from closing with ESC (reopens immediately). */
    fun preventClose(): AnvilInput {
        this.preventClose = true
        return this
    }

    /**
     * Called when the player clicks the **output** slot.
     * Return [AnvilResult.close], [AnvilResult.retry], etc.
     */
    fun onComplete(handler: (Player, String) -> AnvilResult): AnvilInput {
        this.onComplete = handler
        return this
    }

    /**
     * Advanced: handle any anvil slot click. When set, [onComplete] is only used
     * if this returns null-equivalent behavior is not needed — if [onClick] is set,
     * it is the primary handler for all slots (including output).
     */
    fun onClick(handler: (Int, AnvilState) -> AnvilResult): AnvilInput {
        this.onClick = handler
        return this
    }

    fun onClose(handler: (Player, String) -> Unit): AnvilInput {
        this.onClose = handler
        return this
    }

    /**
     * Opens the anvil GUI for [player].
     * @return the live session, or null if NMS is unavailable
     */
    fun open(player: Player): AnvilSession? {
        require(onComplete != null || onClick != null) {
            "AnvilInput requires onComplete() or onClick() before open()"
        }

        val nms = NMSHandlerFactory.getHandler()
            ?: error("No NMS handler available; cannot open AnvilInput")

        // Close existing anvil / menu for this player
        AnvilSession.closeIfOpen(player, sendClosePacket = true)
        Menu.currentlyOpenedMenus.remove(player.uniqueId)?.let { menu ->
            try {
                menu.onClose(player, false)
            } catch (_: Exception) {
            }
        }

        val translatedTitle = CC.translate(title)
        val handle = nms.openAnvil(player, translatedTitle)

        val left = prepareLeftItem()
        if (left != null) {
            handle.inventory.setItem(AnvilSlot.INPUT_LEFT, left)
        }
        itemRight?.let { handle.inventory.setItem(AnvilSlot.INPUT_RIGHT, it) }
        itemOutput?.let { handle.inventory.setItem(AnvilSlot.OUTPUT, it) }

        val session = AnvilSession(
            player = player,
            handle = handle,
            preventClose = preventClose,
            onComplete = onComplete,
            onClick = onClick,
            onClose = onClose,
            reopenIfPrevented = if (preventClose) {
                { this.open(player) }
            } else {
                null
            },
        )
        AnvilSession.sessions[player.uniqueId] = session
        return session
    }

    private fun prepareLeftItem(): ItemStack? {
        val text = itemText
        if (text == null && itemLeft == null) {
            // Always seed a paper so the rename field works
            val paper = ItemStack(Material.PAPER)
            val meta = paper.itemMeta
            if (meta != null) {
                meta.setDisplayName("")
                paper.itemMeta = meta
            }
            return paper
        }

        val base = itemLeft?.clone() ?: ItemStack(Material.PAPER)
        if (text != null) {
            val meta = base.itemMeta
            if (meta != null) {
                meta.setDisplayName(CC.translate(text))
                base.itemMeta = meta
            }
        }
        return base
    }
}
