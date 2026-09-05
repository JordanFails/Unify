package me.jordanfails.unify.screen

import org.bukkit.inventory.ItemStack

/**
 * Content between the title and the footer / inputs.
 */
sealed class ScreenBody {
    data class Message(
        val text: String,
        val width: Int = DEFAULT_WIDTH,
    ) : ScreenBody()

    data class Item(
        val item: ItemStack,
        val description: String? = null,
        val descriptionWidth: Int = DEFAULT_WIDTH,
        val showDecorations: Boolean = true,
        val showTooltip: Boolean = true,
        val width: Int = 16,
        val height: Int = 16,
    ) : ScreenBody()

    companion object {
        const val DEFAULT_WIDTH = 200
    }
}
