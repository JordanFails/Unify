package me.jordanfails.unify.nms.v1_21_R1

import me.jordanfails.unify.nms.NMSHandler
import org.bukkit.Bukkit
import org.bukkit.block.BlockState
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.*

class NMSHandler_v1_21_R1 : NMSHandler {
    
    // Reflection cache
    private val craftPlayerClass: Class<*>? = try { Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer") } catch (e: Exception) { null }
    private val serverPlayerClass: Class<*>? = try { Class.forName("net.minecraft.server.level.ServerPlayer") } catch (e: Exception) { null }
    private val connectionClass: Class<*>? = try { Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl") } catch (e: Exception) { null }
    private val packetClass: Class<*>? = try { Class.forName("net.minecraft.network.protocol.Packet") } catch (e: Exception) { null }
    private val teamPacketClass: Class<*>? = try { Class.forName("net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket") } catch (e: Exception) { null }
    private val playerTeamClass: Class<*>? = try { Class.forName("net.minecraft.world.scores.PlayerTeam") } catch (e: Exception) { null }
    private val scoreboardClass: Class<*>? = try { Class.forName("net.minecraft.world.scores.Scoreboard") } catch (e: Exception) { null }
    private val componentClass: Class<*>? = try { Class.forName("net.minecraft.network.chat.Component") } catch (e: Exception) { null }
    private val chatFormattingClass: Class<*>? = try { Class.forName("net.minecraft.ChatFormatting") } catch (e: Exception) { null }
    private val teamVisibilityClass: Class<*>? = try { Class.forName("net.minecraft.world.scores.Team\$Visibility") } catch (e: Exception) { null }
    private val teamCollisionClass: Class<*>? = try { Class.forName("net.minecraft.world.scores.Team\$CollisionRule") } catch (e: Exception) { null }
    
    override fun sendTitle(
        player: Player,
        title: String,
        subtitle: String?,
        fadeIn: Int,
        stay: Int,
        fadeOut: Int
    ) {
        player.sendTitle(title, subtitle ?: "", fadeIn, stay, fadeOut)
    }

    override fun getServerVersion(): String {
        return Bukkit.getBukkitVersion().split("-").first()
    }

    override fun getPing(player: Player): Int {
        return player.ping
    }

    override fun getTPS(): DoubleArray {
        return try {
            val serverClass = Bukkit.getServer().javaClass
            val getServerMethod = serverClass.getMethod("getServer")
            val minecraftServer = getServerMethod.invoke(Bukkit.getServer())
            val tpsField = minecraftServer.javaClass.getField("recentTps")
            tpsField.get(minecraftServer) as DoubleArray
        } catch (e: Exception) {
            doubleArrayOf(20.0, 20.0, 20.0)
        }
    }

    override fun setItemDurability(item: ItemStack, durability: Int) {
        val meta = item.itemMeta
        if (meta is Damageable) {
            meta.damage = durability
            item.itemMeta = meta
        }
    }

    override fun setItemData(item: ItemStack, data: Short) { }

    override fun setItemUnbreakable(item: ItemStack, unbreakable: Boolean) {
        val meta = item.itemMeta ?: return
        meta.isUnbreakable = unbreakable
        item.itemMeta = meta
    }

    override fun openMenuInventory(player: Player, inventory: Inventory, title: String) {
        player.openInventory(inventory)
    }

    override fun updateMenuTitle(player: Player, title: String) {
        try {
            val top = player.openInventory.topInventory
            val contents = top.contents
            val newInv = player.server.createInventory(null, top.size, title.take(32))
            newInv.contents = contents
            player.openInventory(newInv)
        } catch (_: Throwable) { }
    }

    override fun refreshMenuInventory(player: Player) {
        player.updateInventory()
    }

    override fun isCustomInventory(inventory: Inventory): Boolean {
        val holder = inventory.holder
        return holder == null || holder is Player || holder !is BlockState
    }

    private fun getTeamName(target: Player): String {
        return "nt_${target.name.take(12)}"
    }

    override fun sendHideNametagPacket(viewer: Player, target: Player) {
        sendTeamPacket(viewer, target, getTeamName(target), "", "", "never")
    }

    override fun sendShowNametagPacket(viewer: Player, target: Player) {
        sendTeamPacket(viewer, target, getTeamName(target), "", "", "always")
    }

    override fun sendRemoveNametagTeamPacket(viewer: Player, target: Player) {
        try {
            sendTeamRemovePacket(viewer, getTeamName(target))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    override fun sendNametagPacket(viewer: Player, target: Player, teamName: String, prefix: String, suffix: String) {
        sendTeamPacket(viewer, target, teamName.take(16), prefix, suffix, "always")
    }
    
    private fun sendTeamPacket(viewer: Player, target: Player, teamName: String, prefix: String, suffix: String, visibility: String) {
        try {
            // Create a fake scoreboard and team using NMS
            val scoreboard = scoreboardClass?.getDeclaredConstructor()?.newInstance() ?: return
            
            // Create PlayerTeam
            val teamConstructor = playerTeamClass?.getConstructor(scoreboardClass, String::class.java) ?: return
            val team = teamConstructor.newInstance(scoreboard, teamName)
            
            // Set team properties using reflection
            setTeamDisplayName(team, teamName)
            setTeamPrefix(team, prefix)
            setTeamSuffix(team, suffix)
            setTeamColor(team, prefix)
            setTeamVisibility(team, visibility)
            setTeamCollision(team, "never")
            
            // Add player to team's player list
            addPlayerToTeam(team, target.name)
            
            // Create and send the packet (mode 0 = create team)
            val packet = createTeamPacket(team, 0) ?: return
            sendPacket(viewer, packet)
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun sendTeamRemovePacket(viewer: Player, teamName: String) {
        try {
            val scoreboard = scoreboardClass?.getDeclaredConstructor()?.newInstance() ?: return
            val teamConstructor = playerTeamClass?.getConstructor(scoreboardClass, String::class.java) ?: return
            val team = teamConstructor.newInstance(scoreboard, teamName)
            
            // Create remove packet (mode 1)
            val packet = createTeamPacket(team, 1) ?: return
            sendPacket(viewer, packet)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun setTeamDisplayName(team: Any, name: String) {
        try {
            val component = createComponent(name)
            val method = playerTeamClass?.getMethod("setDisplayName", componentClass) ?: return
            method.invoke(team, component)
        } catch (_: Exception) { }
    }
    
    private fun setTeamPrefix(team: Any, prefix: String) {
        try {
            val component = createComponent(prefix)
            val method = playerTeamClass?.getMethod("setPlayerPrefix", componentClass) ?: return
            method.invoke(team, component)
        } catch (_: Exception) { }
    }
    
    private fun setTeamSuffix(team: Any, suffix: String) {
        try {
            val component = createComponent(suffix)
            val method = playerTeamClass?.getMethod("setPlayerSuffix", componentClass) ?: return
            method.invoke(team, component)
        } catch (_: Exception) { }
    }
    
    private fun setTeamColor(team: Any, prefix: String) {
        try {
            val colorCode = extractColorCode(prefix)
            val chatFormatting = getChatFormatting(colorCode) ?: return
            val method = playerTeamClass?.getMethod("setColor", chatFormattingClass) ?: return
            method.invoke(team, chatFormatting)
        } catch (_: Exception) { }
    }
    
    private fun setTeamVisibility(team: Any, visibility: String) {
        try {
            val visibilityEnum = getVisibilityEnum(visibility) ?: return
            val method = playerTeamClass?.getMethod("setNameTagVisibility", teamVisibilityClass) ?: return
            method.invoke(team, visibilityEnum)
        } catch (_: Exception) { }
    }
    
    private fun setTeamCollision(team: Any, collision: String) {
        try {
            val collisionEnum = getCollisionEnum(collision) ?: return
            val method = playerTeamClass?.getMethod("setCollisionRule", teamCollisionClass) ?: return
            method.invoke(team, collisionEnum)
        } catch (_: Exception) { }
    }
    
    private fun addPlayerToTeam(team: Any, playerName: String) {
        try {
            val getPlayersMethod = playerTeamClass?.getMethod("getPlayers") ?: return
            @Suppress("UNCHECKED_CAST")
            val players = getPlayersMethod.invoke(team) as MutableCollection<String>
            players.add(playerName)
        } catch (_: Exception) { }
    }
    
    private fun createTeamPacket(team: Any, mode: Int): Any? {
        return try {
            when (mode) {
                0 -> { // Create team with players
                    val method = teamPacketClass?.getMethod("createAddOrModifyPacket", playerTeamClass, Boolean::class.java)
                    method?.invoke(null, team, true)
                }
                1 -> { // Remove team
                    val method = teamPacketClass?.getMethod("createRemovePacket", playerTeamClass)
                    method?.invoke(null, team)
                }
                else -> null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun createComponent(text: String): Any? {
        return try {
            val literalMethod = componentClass?.getMethod("literal", String::class.java)
            literalMethod?.invoke(null, text)
        } catch (e: Exception) {
            null
        }
    }
    
    private fun extractColorCode(text: String): Char {
        val colorChars = "0123456789abcdefABCDEF"
        for (i in 0 until text.length - 1) {
            if ((text[i] == '\u00A7' || text[i] == '&') && colorChars.contains(text[i + 1])) {
                return text[i + 1].lowercaseChar()
            }
        }
        return 'f' // Default white
    }
    
    private fun getChatFormatting(colorCode: Char): Any? {
        val colorMap = mapOf(
            '0' to "BLACK", '1' to "DARK_BLUE", '2' to "DARK_GREEN", '3' to "DARK_AQUA",
            '4' to "DARK_RED", '5' to "DARK_PURPLE", '6' to "GOLD", '7' to "GRAY",
            '8' to "DARK_GRAY", '9' to "BLUE", 'a' to "GREEN", 'b' to "AQUA",
            'c' to "RED", 'd' to "LIGHT_PURPLE", 'e' to "YELLOW", 'f' to "WHITE"
        )
        val enumName = colorMap[colorCode] ?: "WHITE"
        return try {
            val enumConstants = chatFormattingClass?.enumConstants ?: return null
            enumConstants.firstOrNull { (it as Enum<*>).name == enumName }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun getVisibilityEnum(visibility: String): Any? {
        val enumName = when (visibility.lowercase()) {
            "always" -> "ALWAYS"
            "never" -> "NEVER"
            "hideforotherteams" -> "HIDE_FOR_OTHER_TEAMS"
            "hideforownteam" -> "HIDE_FOR_OWN_TEAM"
            else -> "ALWAYS"
        }
        return try {
            val enumConstants = teamVisibilityClass?.enumConstants ?: return null
            enumConstants.firstOrNull { (it as Enum<*>).name == enumName }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun getCollisionEnum(collision: String): Any? {
        val enumName = when (collision.lowercase()) {
            "always" -> "ALWAYS"
            "never" -> "NEVER"
            "pushownteam" -> "PUSH_OWN_TEAM"
            "pushotherteams" -> "PUSH_OTHER_TEAMS"
            else -> "ALWAYS"
        }
        return try {
            val enumConstants = teamCollisionClass?.enumConstants ?: return null
            enumConstants.firstOrNull { (it as Enum<*>).name == enumName }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun sendPacket(player: Player, packet: Any) {
        try {
            val craftPlayer = craftPlayerClass?.cast(player) ?: return
            val getHandle = craftPlayerClass?.getMethod("getHandle")
            val serverPlayer = getHandle?.invoke(craftPlayer) ?: return
            
            // Get connection field
            val connectionField = serverPlayerClass?.getField("connection") ?: 
                                  serverPlayerClass?.getDeclaredField("connection")?.apply { isAccessible = true }
            val connection = connectionField?.get(serverPlayer) ?: return
            
            // Send packet
            val sendMethod = connectionClass?.getMethod("send", packetClass)
                ?: connectionClass?.getMethod("sendPacket", packetClass)
            sendMethod?.invoke(connection, packet)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
