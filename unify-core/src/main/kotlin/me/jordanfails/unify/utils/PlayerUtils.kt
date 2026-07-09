package me.jordanfails.unify.utils

import me.jordanfails.unify.UnifyCore
import org.bukkit.*
import org.bukkit.attribute.Attribute
import org.bukkit.entity.*
import org.bukkit.inventory.ItemStack
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Vector
import kotlin.apply
import kotlin.collections.any
import kotlin.collections.count
import kotlin.collections.distinct
import kotlin.collections.filter
import kotlin.collections.filterIsInstance
import kotlin.collections.filterNotNull
import kotlin.collections.forEach
import kotlin.collections.mapNotNull
import kotlin.collections.none
import kotlin.collections.sumOf
import kotlin.collections.toSet
import kotlin.math.sqrt

object PlayerUtils {
    @JvmStatic
    @Deprecated("Using old methods", level = DeprecationLevel.ERROR)
    fun messageStaff(message: String) {
        val colored = CC.translate(message)
        Bukkit.getOnlinePlayers()
            .filter { it.hasPermission("unify.staff") }
            .forEach { it.sendMessage(colored) }
    }

    @JvmStatic
    fun sendMessage(player: Player, message: String) {
        player.sendMessage(CC.translate(message))
    }

    @JvmStatic
    fun setMaxHealth(player: Player, health: Double) {
        player.getAttribute(Attribute.GENERIC_MAX_HEALTH)!!.baseValue = health
        player.maxHealth = health
        if (player.health > health) {
            player.health = health
        }
    }

    @JvmStatic
    fun sendMessage(player: Player, lines: List<String>) {
        lines.forEach { player.sendMessage(CC.translate(it)) }
    }

    @JvmStatic
    fun pushAwayEntity(center: LivingEntity, entity: Entity, speed: Double) =
        pushAwayEntity(center.location, entity, speed)

    @JvmStatic
    fun pushAwayEntity(center: Location, entity: Entity, speed: Double) {
        val unitVector = entity.location.toVector().subtract(center.toVector()).apply {
            if (length() != 0.0) normalize()
        }
        entity.velocity = unitVector.multiply(speed)
    }

    @JvmStatic
    fun isPlayerInventoryFull(player: Player): Boolean =
        player.inventory.contents.none { it == null }

    @JvmStatic
    fun getItemsInventory(player: Player): Int =
        player.inventory.contents
            .filterNotNull()
            .filter { it.type != Material.AIR }
            .sumOf { it.amount }

    @JvmStatic
    fun getEmptySpace(player: Player): Int =
        player.inventory.contents.count { it == null }

    @JvmStatic
    fun getContents(player: Player): List<ItemStack> =
        buildList {
            player.itemOnCursor.takeIf { it.type != Material.AIR }?.let(::add)
            player.inventory.contents.filterNotNull().filter { it.type != Material.AIR }.forEach(::add)
            player.inventory.armorContents.filterNotNull().filter { it.type != Material.AIR }.forEach(::add)
        }

    @JvmStatic
    fun getArmorContents(player: Player): List<ItemStack> =
        player.inventory.armorContents.filterNotNull().filter { it.type != Material.AIR }

    @JvmStatic
    fun getPlayerInventory(player: Player): List<ItemStack> =
        player.inventory.contents.filterNotNull().filter { it.type != Material.AIR }

    @JvmStatic
    fun neutralizeKnockback(entity: LivingEntity) {
        Bukkit.getScheduler().runTaskLater(UnifyCore.instance, Runnable {
            entity.velocity = Vector(0, 0, 0)
        }, 1L)
    }

    /**
     * Adds [item] to inventory or drops it at the player's feet as a protected item.
     *
     * @param protectionTimer seconds the item stays protected; -1 is infinite.
     */
    @JvmStatic
    fun giveOrDropProtectedItem(player: Player, item: ItemStack, protectionTimer: Int): Boolean {
        return if (player.inventory.firstEmpty() != -1) {
            player.inventory.addItem(item)
            true
        } else {
            dropProtectedItems(player, listOf(item), protectionTimer)
            false
        }
    }

    @JvmStatic
    fun giveOrDropProtectedItems(player: Player, items: List<ItemStack>, protectionTimer: Int): Boolean {
        return if (player.inventory.firstEmpty() == -1) {
            dropProtectedItems(player, items, protectionTimer)
            false
        } else {
            items.forEach { player.inventory.addItem(it) }
            true
        }
    }

    @JvmStatic
    private fun dropProtectedItems(player: Player, items: List<ItemStack>, timerSeconds: Int) {
        val expiryTime =
            if (timerSeconds == -1) -1L else System.currentTimeMillis() + timerSeconds * 1000L
        items.forEach { stack ->
            val dropped = player.world.dropItem(player.location, stack)
            dropped.setMetadata(
                "protectedItem",
                FixedMetadataValue(UnifyCore.instance, expiryTime)
            )
            dropped.setMetadata(
                "protectedOwner",
                FixedMetadataValue(UnifyCore.instance, player.uniqueId.toString())
            )
        }
        playSound(player, Sound.ENTITY_CHICKEN_EGG, 1.0f)
        player.sendMessage(CC.translate("&5&l(LOOT) &dProtected Items Dropped."))
        player.sendMessage(CC.translate("&7Since your inventory was full, your items were dropped."))
    }

    @JvmStatic
    fun getNearbyPlayers(loc: Location, rangeX: Double, rangeY: Double, rangeZ: Double): List<Player> {
        val world = loc.world ?: return emptyList()
        return world.getNearbyEntities(loc, rangeX, rangeY, rangeZ)
            .filterIsInstance<Player>()
    }

    @JvmStatic
    fun getNearbyPlayers(loc: Location, blockRadius: Int): MutableList<Player> {
        val world = loc.world ?: return mutableListOf()
        val rangeSq = blockRadius * blockRadius
        return world.players.filter { it.location.distanceSquared(loc) <= rangeSq }.toMutableList()
    }

    @JvmStatic
    fun getNearbyPlayersBlockBased(loc: Location, blockRadius: Int): List<Player> {
        val world = loc.world ?: return emptyList()
        return world.players.filter { getBlockDistance(loc, it) <= blockRadius }
    }

    @JvmStatic
    private fun getBlockDistance(loc: Location, player: Player): Double {
        val pl = player.location
        val dx = (pl.blockX - loc.blockX).toDouble()
        val dy = (pl.blockY - loc.blockY).toDouble()
        val dz = (pl.blockZ - loc.blockZ).toDouble()
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    @JvmStatic
    fun getNearbyPlayersEfficient(loc: Location, blockRadius: Int): List<Player> {
        val world = loc.world ?: return emptyList()
        return world.getNearbyEntities(loc, blockRadius.toDouble(), blockRadius.toDouble(), blockRadius.toDouble())
            .filterIsInstance<Player>()
    }

    @JvmStatic
    fun getNearbyPlayersSet(loc: Location, rangeX: Double, rangeY: Double, rangeZ: Double): Set<Player> {
        val world = loc.world ?: return emptySet()
        return world.getNearbyEntities(loc, rangeX, rangeY, rangeZ)
            .filterIsInstance<Player>()
            .toSet()
    }

    @JvmStatic
    fun addItems(player: Player, vararg itemStacks: ItemStack) {
        val leftovers = player.inventory.addItem(*itemStacks)
        leftovers.values.forEach { player.world.dropItem(player.location, it) }
    }

    @JvmStatic
    fun playSound(player: Player, sound: Sound, pitch: Float) {
        player.playSound(player.location, sound, 1.0f, pitch)
    }

    @JvmStatic
    fun clearInventory(player: Player) {
        with(player.inventory) {
            clear()
            armorContents = arrayOfNulls(4)
        }
    }

    @JvmStatic
    fun runTask(player: Player, runnable: BukkitRunnable): BukkitRunnable? =
        if (player.isOnline) runnable else null

    @JvmStatic
    fun getUniquePlayerCount(): Int =
        Bukkit.getOnlinePlayers().mapNotNull { it.address?.hostString }.distinct().size

    @JvmStatic
    fun isPlayerNearby(loc: Location, rangeX: Double, rangeY: Double, rangeZ: Double): Boolean {
        val world = loc.world ?: return false
        return world.getNearbyEntities(loc, rangeX, rangeY, rangeZ).any { it is Player }
    }
}