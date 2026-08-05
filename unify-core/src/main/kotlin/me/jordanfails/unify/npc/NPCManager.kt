package me.jordanfails.unify.npc

import me.jordanfails.unify.UnifyCore
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.world.ChunkLoadEvent
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object NPCManager : Listener {
    internal const val NPC_METADATA_KEY = "UNIFY_NPC_ID"

    private val npcs = ConcurrentHashMap<String, UnifyNPC>()
    private val entityLookup = ConcurrentHashMap<UUID, String>()
    private val runtimeActions = ConcurrentHashMap<String, NPCAction>()

    private lateinit var plugin: UnifyCore
    private lateinit var dataFile: File

    fun enable(plugin: UnifyCore) {
        this.plugin = plugin
        this.dataFile = File(plugin.dataFolder, "npcs.yml")
        Bukkit.getPluginManager().registerEvents(this, plugin)
        load()
    }

    fun disable() {
        save()
        npcs.values.forEach { it.despawn() }
        npcs.clear()
        entityLookup.clear()
        runtimeActions.clear()
    }

    fun create(id: String, location: Location, hologramLines: List<String> = emptyList(), actionCommand: String? = null): UnifyNPC? {
        val key = id.lowercase()
        if (npcs.containsKey(key)) {
            return null
        }

        val npc = UnifyNPC(key, location, hologramLines, actionCommand)
        npcs[key] = npc
        spawnNpc(npc)
        save()
        return npc
    }

    fun delete(id: String): Boolean {
        val key = id.lowercase()
        val npc = npcs.remove(key) ?: return false
        npc.despawn()
        runtimeActions.remove(key)
        entityLookup.entries.removeIf { it.value == key }
        save()
        return true
    }

    fun get(id: String): UnifyNPC? {
        return npcs[id.lowercase()]
    }

    fun exists(id: String): Boolean {
        return npcs.containsKey(id.lowercase())
    }

    fun getIds(): Set<String> {
        return npcs.keys.toSet()
    }

    fun getAll(): Map<String, UnifyNPC> {
        return npcs.toMap()
    }

    fun setActionCommand(id: String, command: String?): Boolean {
        val npc = get(id) ?: return false
        npc.setActionCommand(command)
        save()
        return true
    }

    fun setSkin(id: String, type: UnifyNPC.SkinType, value: String): Boolean {
        val npc = get(id) ?: return false
        return try {
            npc.setSkin(type, value)
            syncEntityLookup(npc)
            // Skin swaps despawn/respawn the body — only persist when it still exists.
            if (npc.isSpawned()) {
                save()
                true
            } else {
                // Rebuild once so a failed skin apply never leaves a permanent hole.
                spawnNpc(npc)
                save()
                npc.isSpawned()
            }
        } catch (e: Exception) {
            plugin.logger.warning("Failed to set skin on NPC '$id': ${e.message}")
            e.printStackTrace()
            spawnNpc(npc)
            save()
            false
        }
    }

    fun clearSkin(id: String): Boolean {
        val npc = get(id) ?: return false
        return try {
            npc.clearSkin()
            syncEntityLookup(npc)
            if (!npc.isSpawned()) spawnNpc(npc)
            save()
            true
        } catch (e: Exception) {
            plugin.logger.warning("Failed to clear skin on NPC '$id': ${e.message}")
            spawnNpc(npc)
            save()
            false
        }
    }

    /**
     * Ensure the NPC entity exists in a loaded world and is shown to nearby players.
     * Call after chunk loads or when a viewer walks back into range.
     */
    fun ensureSpawned(id: String): Boolean {
        val npc = get(id) ?: return false
        val location = npc.spawnLocation
        val world = location.world ?: return false
        if (!world.isChunkLoaded(location.blockX shr 4, location.blockZ shr 4)) {
            return false
        }
        val ok = npc.ensureSpawned(plugin)
        if (ok) syncEntityLookup(npc)
        return ok
    }

    /** Re-send the NPC body + hologram to [player] if they share a world. */
    fun refreshViewer(id: String, player: Player): Boolean {
        val npc = get(id) ?: return false
        if (!npc.isSpawned()) {
            if (!ensureSpawned(id)) return false
        }
        npc.refreshViewer(player)
        return true
    }

    fun setHologramLines(id: String, lines: List<String>): Boolean {
        val npc = get(id) ?: return false
        npc.setHologramLines(lines)
        save()
        return true
    }

    /**
     * Same as [setHologramLines] but without persisting to `npcs.yml`.
     *
     * For text that is regenerated on a timer — leaderboards, countdowns, live counters —
     * the saved copy is worthless (it is overwritten seconds later anyway) and saving would
     * rewrite the whole file on the main thread on every refresh.
     */
    fun setTransientHologramLines(id: String, lines: List<String>): Boolean {
        val npc = get(id) ?: return false
        npc.setHologramLines(lines)
        return true
    }

    fun teleport(id: String, location: Location): Boolean {
        val npc = get(id) ?: return false
        npc.teleport(location)
        syncEntityLookup(npc)
        save()
        return true
    }

    fun registerAction(id: String, action: NPCAction): Boolean {
        val key = id.lowercase()
        if (!npcs.containsKey(key)) {
            return false
        }
        runtimeActions[key] = action
        return true
    }

    fun unregisterAction(id: String) {
        runtimeActions.remove(id.lowercase())
    }

    private fun spawnNpc(npc: UnifyNPC) {
        npc.spawn(plugin) ?: return
        syncEntityLookup(npc)
    }

    private fun syncEntityLookup(npc: UnifyNPC) {
        entityLookup.entries.removeIf { it.value == npc.id }
        npc.getEntityUuid()?.let { entityLookup[it] = npc.id }
    }

    private fun handleNpcClick(player: Player, entityUuid: UUID) {
        val npcId = entityLookup[entityUuid] ?: return
        val npc = npcs[npcId] ?: return
        npc.handleClick(player, runtimeActions[npcId])
    }

    fun save() {
        try {
            if (!dataFile.parentFile.exists()) {
                dataFile.parentFile.mkdirs()
            }

            val config = YamlConfiguration()
            npcs.forEach { (id, npc) ->
                val path = "npcs.$id"
                config.set("$path.location.world", npc.spawnLocation.world?.name)
                config.set("$path.location.x", npc.spawnLocation.x)
                config.set("$path.location.y", npc.spawnLocation.y)
                config.set("$path.location.z", npc.spawnLocation.z)
                config.set("$path.location.yaw", npc.spawnLocation.yaw.toDouble())
                config.set("$path.location.pitch", npc.spawnLocation.pitch.toDouble())
                config.set("$path.hologram-lines", npc.hologramLines)
                config.set("$path.action-command", npc.actionCommand)
                config.set("$path.skin.type", npc.skinType?.name)
                config.set("$path.skin.value", npc.skinValue)
            }

            config.save(dataFile)
        } catch (e: Exception) {
            plugin.logger.warning("Failed to save NPCs: ${e.message}")
            e.printStackTrace()
        }
    }

    fun load() {
        npcs.values.forEach { it.despawn() }
        npcs.clear()
        entityLookup.clear()

        try {
            if (!dataFile.exists()) {
                return
            }

            val config = YamlConfiguration.loadConfiguration(dataFile)
            val npcsSection = config.getConfigurationSection("npcs") ?: return

            for (id in npcsSection.getKeys(false)) {
                val path = "npcs.$id"
                val worldName = config.getString("$path.location.world") ?: continue
                val world = Bukkit.getWorld(worldName) ?: continue
                val x = config.getDouble("$path.location.x")
                val y = config.getDouble("$path.location.y")
                val z = config.getDouble("$path.location.z")
                val yaw = config.getDouble("$path.location.yaw").toFloat()
                val pitch = config.getDouble("$path.location.pitch").toFloat()

                val location = Location(world, x, y, z, yaw, pitch)
                val hologramLines = config.getStringList("$path.hologram-lines")
                val actionCommand = config.getString("$path.action-command")
                val skinType = config.getString("$path.skin.type")
                    ?.uppercase()
                    ?.let { runCatching { UnifyNPC.SkinType.valueOf(it) }.getOrNull() }
                val skinValue = config.getString("$path.skin.value")

                val npc = UnifyNPC(id.lowercase(), location, hologramLines, actionCommand, skinType, skinValue)
                npcs[id.lowercase()] = npc
                spawnNpc(npc)
            }

            plugin.logger.info("Loaded ${npcs.size} NPCs from config")
        } catch (e: Exception) {
            plugin.logger.warning("Failed to load NPCs: ${e.message}")
            e.printStackTrace()
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onPlayerInteractEntity(event: PlayerInteractEntityEvent) {
        val entityUuid = event.rightClicked.uniqueId
        if (!entityLookup.containsKey(entityUuid)) {
            return
        }

        event.isCancelled = true
        handleNpcClick(event.player, entityUuid)
    }

    @EventHandler(ignoreCancelled = true)
    fun onPlayerInteractAtEntity(event: PlayerInteractAtEntityEvent) {
        val entityUuid = event.rightClicked.uniqueId
        if (!entityLookup.containsKey(entityUuid)) {
            return
        }

        event.isCancelled = true
        handleNpcClick(event.player, entityUuid)
    }

    @EventHandler(ignoreCancelled = true)
    fun onNpcDamage(event: EntityDamageEvent) {
        if (entityLookup.containsKey(event.entity.uniqueId)) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onNpcDamageByPlayer(event: EntityDamageByEntityEvent) {
        val entityUuid = event.entity.uniqueId
        if (!entityLookup.containsKey(entityUuid)) {
            return
        }

        event.isCancelled = true

        val player = event.damager as? Player ?: return
        handleNpcClick(player, entityUuid)
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            npcs.values.forEach { it.addViewer(event.player) }
        }, 5L)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        npcs.values.forEach { it.removeViewer(event.player) }
    }

    @EventHandler
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        npcs.values.forEach { npc ->
            npc.removeViewer(event.player)
            npc.addViewer(event.player)
        }
    }

    /**
     * NMS player NPCs are added to the level via `addNewPlayer`, so chunk unload discards
     * the entity. Rebuild any NPC that lives in the reloaded chunk.
     */
    @EventHandler
    fun onChunkLoad(event: ChunkLoadEvent) {
        val chunk = event.chunk
        for (npc in npcs.values) {
            val loc = npc.spawnLocation
            if (loc.world != chunk.world) continue
            if (loc.blockX shr 4 != chunk.x || loc.blockZ shr 4 != chunk.z) continue
            Bukkit.getScheduler().runTask(plugin, Runnable {
                if (!npc.isSpawned()) {
                    spawnNpc(npc)
                } else {
                    npc.refreshAllViewers()
                }
            })
        }
    }

    /**
     * When a player walks into range of an NPC that lost its body (chunk cycle without a
     * clean re-show), ensure + re-show them.
     */
    @EventHandler(ignoreCancelled = true)
    fun onMove(event: PlayerMoveEvent) {
        val from = event.from
        val to = event.to ?: return
        if (from.blockX == to.blockX && from.blockY == to.blockY && from.blockZ == to.blockZ) return

        val player = event.player
        val rangeSq = 48.0 * 48.0
        for (npc in npcs.values) {
            val loc = npc.spawnLocation
            if (loc.world != player.world) continue
            if (player.location.distanceSquared(loc) > rangeSq) continue
            if (!npc.isSpawned()) {
                spawnNpc(npc)
            } else {
                npc.refreshViewer(player)
            }
        }
    }
}
