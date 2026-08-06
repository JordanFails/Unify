package me.jordanfails.unify.menu.anvil

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Runtime state for an open anvil GUI.
 */
class AnvilSession internal constructor(
    val player: Player,
    val handle: AnvilHandle,
    val preventClose: Boolean,
    val onComplete: ((Player, String) -> AnvilResult)?,
    val onClick: ((Int, AnvilState) -> AnvilResult)?,
    val onClose: ((Player, String) -> Unit)?,
    /** When [preventClose] is true, re-open the anvil after the client closes it. */
    internal val reopenIfPrevented: (() -> Unit)? = null,
) {
    @Volatile
    var open: Boolean = true

    /** True when close was requested by us (complete/result), not by the client. */
    @Volatile
    internal var closingByApi: Boolean = false

    fun snapshot(): AnvilState = AnvilState.from(this)

    fun applyResult(result: AnvilResult) {
        when (result) {
            is AnvilResult.Close -> close(sendClosePacket = true, notifyClose = false)
            is AnvilResult.KeepOpen -> Unit
            is AnvilResult.Retry -> replaceInputText(result.text)
            is AnvilResult.Run -> result.block()
            is AnvilResult.Composite -> result.actions.forEach { applyResult(it) }
        }
    }

    fun replaceInputText(text: String) {
        val inv = handle.inventory
        var item = inv.getItem(AnvilSlot.OUTPUT)
        if (item == null || item.type == Material.AIR) {
            item = inv.getItem(AnvilSlot.INPUT_LEFT)
        }
        if (item == null || item.type == Material.AIR) {
            item = ItemStack(Material.PAPER)
        }

        val cloned = item.clone()
        val meta = cloned.itemMeta
        if (meta != null) {
            meta.setDisplayName(text)
            cloned.itemMeta = meta
        }
        inv.setItem(AnvilSlot.INPUT_LEFT, cloned)
        handle.setRenameText(text)
    }

    /**
     * Closes this session.
     * @param sendClosePacket whether to send the client close packet
     * @param notifyClose whether to invoke [onClose]
     */
    fun close(sendClosePacket: Boolean = true, notifyClose: Boolean = true) {
        if (!open) return
        open = false
        closingByApi = true

        val text = try {
            AnvilState.resolveText(handle)
        } catch (_: Exception) {
            ""
        }

        sessions.remove(player.uniqueId, this)
        try {
            handle.close(sendClosePacket)
        } catch (_: Exception) {
        }

        if (notifyClose) {
            try {
                onClose?.invoke(player, text)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    companion object {
        internal val sessions: MutableMap<UUID, AnvilSession> = ConcurrentHashMap()

        fun get(player: Player): AnvilSession? = sessions[player.uniqueId]

        fun get(uuid: UUID): AnvilSession? = sessions[uuid]

        fun closeIfOpen(player: Player, sendClosePacket: Boolean = true) {
            sessions[player.uniqueId]?.close(sendClosePacket = sendClosePacket, notifyClose = true)
        }
    }
}
