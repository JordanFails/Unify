package me.jordanfails.unify.menu.anvil

import org.bukkit.inventory.Inventory

/**
 * Version-specific anvil container session created by [me.jordanfails.unify.nms.NMSHandler.openAnvil].
 */
interface AnvilHandle {
    val inventory: Inventory
    val containerId: Int

    /** Raw text currently in the rename field, or empty if unavailable. */
    fun getRenameText(): String

    /** Updates the left-slot item name (and rename field where supported). */
    fun setRenameText(text: String)

    /**
     * Tears down the NMS container.
     * @param sendClosePacket whether to send the close-window packet to the client
     */
    fun close(sendClosePacket: Boolean)

    /**
     * Updates the anvil title when the version supports custom titles (1.14+).
     * No-op on older versions.
     */
    fun updateTitle(title: String, preserveRenameText: Boolean)
}
