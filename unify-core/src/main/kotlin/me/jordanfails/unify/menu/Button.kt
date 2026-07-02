package me.jordanfails.unify.menu

import com.cryptomorin.xseries.XMaterial
import com.cryptomorin.xseries.XSound
import com.google.common.base.Joiner
import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.utils.ItemBuilder
import me.jordanfails.unify.utils.XSupport
import me.jordanfails.unify.utils.get
import org.apache.commons.lang.StringUtils
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.conversations.ConversationContext
import org.bukkit.conversations.ConversationFactory
import org.bukkit.conversations.Prompt
import org.bukkit.conversations.StringPrompt
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.InventoryView
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.plugin.Plugin
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

abstract class Button {

    var preserveName: Boolean = false
    var lastAnimation: Long = System.currentTimeMillis()

    open fun getName(player: Player): String {
        return " "
    }

    open fun getDescription(player: Player): MutableList<String> {
        return mutableListOf()
    }

    open fun getMaterial(player: Player): Material {
        return Material.AIR
    }

    open fun getDamageValue(player: Player): Byte {
        return 0
    }

    open fun applyMetadata(player: Player, itemMeta: ItemMeta): ItemMeta? {
        return null
    }

    open fun clicked(player: Player, slot: Int, clickType: ClickType, view: InventoryView) {}

    open fun shouldCancel(player: Player, slot: Int, clickType: ClickType): Boolean {
        return true
    }

    open fun getAmount(player: Player): Int {
        return 1
    }

    /**
     * Primary method - builds the button item to display in inventory.
     * Override either this OR getItem() - they work the same way!
     */
    open fun getButtonItem(player: Player): ItemStack {
        // First, try calling getItem() to see if it was overridden
        val itemFromGetItem = getItem(player)

        // If getItem() returns something meaningful (not the default AIR),
        // use that instead (for backwards compatibility)
        if (itemFromGetItem.type != Material.AIR) {
            return itemFromGetItem
        }

        // Otherwise build from getName/getDescription/getMaterial
        val lore = getDescription(player) ?: mutableListOf()
        val buttonItem = ItemBuilder(getMaterial(player))
            .data(getDamageValue(player).toShort())
            .name(getName(player))
            .lore(lore)
            .amount(getAmount(player))
            .build()
        return buttonItem
    }

    /**
     * Alternate method - works the same as getButtonItem().
     * Override either this OR getButtonItem() - both work!
     */
    open fun getItem(player: Player): ItemStack {
        // Default returns AIR, which signals getButtonItem to build from other methods
        return ItemStack(Material.AIR)
    }

    open fun isAnimated(): Boolean {
        return false
    }

    open fun getAnimationInterval(): Long {
        return 500L
    }

    open fun preAnimationUpdate() {

    }

    companion object {
        val BAR = "${ChatColor.GRAY}${ChatColor.STRIKETHROUGH}${StringUtils.repeat("-", 32)}"

        internal val clickCooldown: MutableMap<UUID, Long> = ConcurrentHashMap()

        @JvmStatic
        fun cooldown(player: Player, duration: Long) {
            clickCooldown[player.uniqueId] = System.currentTimeMillis() + duration
        }

        @JvmStatic
        fun placeholder(material: Material, data: Byte, vararg title: String): Button {
            return placeholder(material, data, Joiner.on(" ").join(title))
        }

        @JvmStatic
        fun placeholder(material: Material): Button {
            return placeholder(material, " ")
        }

        @JvmStatic
        fun placeholder(xMaterial: XMaterial): Button {
            return placeholder(xMaterial.parseMaterial()!!, "")
        }

        @JvmStatic
        fun placeholder(material: Material, title: String): Button {
            return placeholder(material, 0.toByte(), title)
        }

        @JvmStatic
        fun placeholder(item: ItemStack): Button {
            return object : Button() {
                override fun getName(player: Player): String {
                    return if (item.hasItemMeta() && item.itemMeta!!.hasDisplayName()) {
                        item.itemMeta!!.displayName
                    } else {
                        ""
                    }
                }

                override fun getDescription(player: Player): MutableList<String> {
                    return if (item.hasItemMeta() && item.itemMeta!!.hasLore()) {
                        item.itemMeta!!.lore!!
                    } else {
                        mutableListOf()
                    }
                }

                override fun getMaterial(player: Player): Material {
                    return item.type
                }

                override fun getDamageValue(player: Player): Byte {
                    return item.durability.toByte()
                }
            }
        }

        @JvmStatic
        fun placeholder(material: Material, data: Byte, title: String): Button {
            return object : Button() {
                override fun getName(player: Player): String {
                    return title
                }

                override fun getDescription(player: Player): MutableList<String> {
                    return mutableListOf()
                }

                override fun getMaterial(player: Player): Material {
                    return material
                }

                override fun getDamageValue(player: Player): Byte {
                    return data
                }
            }
        }

        @JvmStatic
        fun placeholder(material: XMaterial, data: Byte, title: String): Button {
            return object : Button() {
                override fun getName(player: Player): String {
                    return title
                }

                override fun getDescription(player: Player): MutableList<String> {
                    return mutableListOf()
                }

                override fun getMaterial(player: Player): Material {
                    return material.get() ?: Material.BARREL
                }

                override fun getDamageValue(player: Player): Byte {
                    return data
                }
            }
        }

        @JvmStatic
        protected fun fromItem(item: ItemStack?): Button {
            return object : Button() {
                override fun getButtonItem(player: Player): ItemStack {
                    return item ?: ItemStack(Material.AIR)
                }
            }
        }

        @JvmStatic
        fun playFail(player: Player) {
            player.playSound(player.location, XSupport.resolveSound(XSound.BLOCK_GRASS_HIT), 20.0F, 0.1F)
        }

        @JvmStatic
        fun playSuccess(player: Player) {
            player.playSound(player.location, XSupport.resolveSound(XSound.BLOCK_NOTE_BLOCK_PLING), 20.0F, 15.0F)
        }

        @JvmStatic
        fun playNeutral(player: Player) {
            player.playSound(player.location, XSupport.resolveSound(XSound.UI_BUTTON_CLICK), 20.0F, 1.0F)
        }

        @JvmStatic
        fun playClick(player: Player) {
            player.playSound(player.location, XSupport.resolveSound(XSound.UI_BUTTON_CLICK), 20.0F, 1.0F)
        }

        @JvmStatic
        fun styleAction(color: ChatColor, action: String, text: String): String {
            return color.toString() + ChatColor.BOLD + action.uppercase() + ChatColor.RESET + color + " " + text
        }

        /**
         * Start a conversation with a player to get text input
         * @param player The player to converse with
         * @param message The prompt message to show the player
         * @param callback Lambda that receives the player's response (null if cancelled)
         */
        fun Button.conversate(
            player: Player,
            message: String,
            callback: (String?) -> Unit
        ) {
            conversate(player, message, Consumer { callback(it) })
        }

        /**
         * Start a conversation with a player to get text input (Java Consumer version)
         * @param player The player to converse with
         * @param message The prompt message to show the player
         * @param callback Consumer that receives the player's response (null if cancelled)
         */
        fun Button.conversate(
            player: Player,
            message: String,
            callback: Consumer<String?>
        ) {
            // Get the plugin instance - adjust this to match your plugin accessor
            val plugin = getPlugin()

            player.closeInventory()

            val factory = ConversationFactory(plugin)
                .withModality(true)
                .withFirstPrompt(object : StringPrompt() {
                    override fun getPromptText(context: ConversationContext): String {
                        return message
                    }

                    override fun acceptInput(context: ConversationContext, input: String?): Prompt? {
                        callback.accept(input)
                        return END_OF_CONVERSATION
                    }
                })
                .withEscapeSequence("cancel")
                .withLocalEcho(false)
                .withTimeout(60)
                .addConversationAbandonedListener { event ->
                    if (!event.gracefulExit()) {
                        // Conversation was canceled/timed out
                        callback.accept(null)
                    }
                }

            val conversation = factory.buildConversation(player)
            conversation.begin()
        }
    }

    open fun isMoveable(): Boolean = false
    open fun isRemovable(): Boolean = false

    /**
     * Helper function to get plugin instance
     * Adjust this to match how you access your plugin
     */
    private fun getPlugin(): Plugin {
        return UnifyCore.instance
    }

}
