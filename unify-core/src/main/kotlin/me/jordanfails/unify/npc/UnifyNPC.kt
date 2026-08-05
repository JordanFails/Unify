package me.jordanfails.unify.npc

import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.hologram.HologramLine
import me.jordanfails.unify.hologram.UnifyHologram
import me.jordanfails.unify.nms.NMSHandlerFactory
import me.jordanfails.unify.utils.SkullBuilder
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class UnifyNPC internal constructor(
    val id: String,
    location: Location,
    hologramLines: List<String> = emptyList(),
    actionCommand: String? = null,
    skinType: SkinType? = null,
    skinValue: String? = null
) {
    var spawnLocation: Location = location.clone()
        private set

    var hologramLines: List<String> = hologramLines.map { it.trim() }.filter { it.isNotEmpty() }
        private set

    var actionCommand: String? = cleanCommand(actionCommand)
        private set

    var skinType: SkinType? = skinType
        private set

    var skinValue: String? = cleanSkinValue(skinValue)
        private set

    private var entityUuid: UUID? = null
    private var nmsPlayerNpc = false
    private var hologram: UnifyHologram? = null
    private val clickCooldowns = ConcurrentHashMap<UUID, Long>()

    internal fun spawn(plugin: UnifyCore): UUID? {
        despawn()

        val nms = NMSHandlerFactory.getHandler()
        val npcUuid = nms?.spawnPlayerNpc(id, spawnLocation, skinType, skinValue)
        if (npcUuid != null) {
            entityUuid = npcUuid
            nmsPlayerNpc = true
            getEntity()?.let { configureEntity(plugin, it) }
            updateHologram()
            return npcUuid
        }

        nmsPlayerNpc = false
        plugin.logger.warning("Failed to spawn player NPC '$id' at ${spawnLocation.world?.name} ${spawnLocation.x}, ${spawnLocation.y}, ${spawnLocation.z}")
        return null
    }

    internal fun despawn() {
        val uuid = entityUuid
        if (nmsPlayerNpc && uuid != null) {
            NMSHandlerFactory.getHandler()?.despawnPlayerNpc(uuid)
        } else {
            getEntity()?.remove()
        }
        entityUuid = null
        nmsPlayerNpc = false
        clickCooldowns.clear()
        hologram?.removeAll()
        hologram = null
    }

    internal fun isEntity(entity: Entity): Boolean {
        return entity.uniqueId == entityUuid
    }

    internal fun getEntityUuid(): UUID? {
        return entityUuid
    }

    fun setActionCommand(command: String?) {
        actionCommand = cleanCommand(command)
    }

    fun setSkin(type: SkinType, value: String) {
        val cleanedValue = cleanSkinValue(value) ?: return
        skinType = type
        skinValue = cleanedValue
        if (nmsPlayerNpc) {
            respawnNmsEntity()
        } else {
            applySkin(getEntity())
        }
    }

    fun clearSkin() {
        skinType = null
        skinValue = null
        if (nmsPlayerNpc) {
            respawnNmsEntity()
        } else {
            applySkin(getEntity())
        }
    }

    /**
     * Despawn + re-spawn the NMS player body (e.g. after a skin change) and re-show it to
     * every online player in the same world. Callers must update entity lookups themselves
     * when going through [NPCManager].
     */
    private fun respawnNmsEntity() {
        val location = spawnLocation.clone()
        val nms = NMSHandlerFactory.getHandler()
        val oldUuid = entityUuid
        if (oldUuid != null) nms?.despawnPlayerNpc(oldUuid)

        val newUuid = nms?.spawnPlayerNpc(id, location, skinType, skinValue)
        entityUuid = newUuid
        if (newUuid == null) {
            nmsPlayerNpc = false
            return
        }
        nmsPlayerNpc = true

        // spawnPlayerNpc already sends packets, but re-run addViewer so holograms and any
        // post-spawn bookkeeping stay in sync for players already in range.
        Bukkit.getOnlinePlayers().forEach { viewer ->
            if (viewer.world == location.world) {
                addViewer(viewer)
            }
        }
    }

    /** True when the backing entity still exists in a loaded world (false after chunk unload). */
    fun isSpawned(): Boolean {
        val uuid = entityUuid ?: return false
        return Bukkit.getEntity(uuid) != null
    }

    /** Re-send spawn packets / hologram lines to [player] if they can see this NPC. */
    fun refreshViewer(player: Player) {
        addViewer(player)
    }

    /** Re-send spawn packets to every online player in this NPC's world. */
    fun refreshAllViewers() {
        val world = spawnLocation.world ?: return
        Bukkit.getOnlinePlayers().forEach { viewer ->
            if (viewer.world == world) addViewer(viewer)
        }
    }

    /**
     * If the entity was discarded (chunk unload, failed skin, etc.), spawn it again.
     * @return true when an entity is present afterwards
     */
    fun ensureSpawned(plugin: UnifyCore): Boolean {
        if (isSpawned()) {
            refreshAllViewers()
            return true
        }
        return spawn(plugin) != null
    }

    fun setHologramLines(lines: List<String>) {
        hologramLines = lines.map { it.trim() }.filter { it.isNotEmpty() }
        updateHologram()
    }

    fun teleport(location: Location) {
        spawnLocation = location.clone()
        val uuid = entityUuid
        if (nmsPlayerNpc && uuid != null) {
            NMSHandlerFactory.getHandler()?.teleportPlayerNpc(uuid, spawnLocation)
        } else {
            getEntity()?.teleport(spawnLocation)
        }
        updateHologram()
    }

    internal fun handleClick(player: Player, runtimeAction: NPCAction?) {
        if (!allowClick(player.uniqueId)) {
            return
        }

        runtimeAction?.execute(player, this)

        val command = actionCommand ?: return
        val parsed = command
            .replace("{player}", player.name, ignoreCase = true)
            .replace("{npc}", id, ignoreCase = true)
            .replace("{npc_id}", id, ignoreCase = true)
            .trim()
            .removePrefix("/")
        if (parsed.isNotEmpty()) {
            player.performCommand(parsed)
        }
    }

    internal fun addViewer(player: Player) {
        val uuid = entityUuid
        if (nmsPlayerNpc && uuid != null) {
            NMSHandlerFactory.getHandler()?.showPlayerNpcToViewer(player, uuid)
        }
        val npcHologram = hologram ?: return
        if (spawnLocation.world == player.world) {
            npcHologram.addViewer(player)
        }
    }

    internal fun removeViewer(player: Player) {
        hologram?.removeViewer(player)
    }

    private fun updateHologram() {
        val world = spawnLocation.world
        if (world == null || hologramLines.isEmpty()) {
            hologram?.removeAll()
            hologram = null
            return
        }

        val lineObjects = hologramLines.map { HologramLine.Text(it) }
        // Lines stack downwards from the anchor, so the anchor has to rise with the line
        // count to keep the *bottom* line sitting just above the NPC's head. Anchoring the
        // top line at a fixed height instead made every line past the second overlap the
        // NPC, which is very visible on tall holograms such as leaderboards.
        val stackHeight = (lineObjects.size - 1).coerceAtLeast(0) * LINE_SPACING
        val hologramLocation = spawnLocation.clone().add(0.0, HOLOGRAM_HEIGHT + stackHeight, 0.0)

        val npcHologram = hologram
        if (npcHologram == null) {
            hologram = UnifyHologram(hologramLocation, lineObjects).also { created ->
                Bukkit.getOnlinePlayers().forEach { viewer ->
                    if (viewer.world == world) {
                        created.addViewer(viewer)
                    }
                }
            }
            return
        }

        // teleport() persists the hologram file; only pay that when the anchor really moved.
        if (npcHologram.location != hologramLocation) {
            npcHologram.teleport(hologramLocation)
        }
        npcHologram.lines = lineObjects
        Bukkit.getOnlinePlayers().forEach { viewer ->
            if (viewer.world == world) {
                npcHologram.addViewer(viewer)
            } else {
                npcHologram.removeViewer(viewer)
            }
        }
    }

    private fun allowClick(playerUuid: UUID): Boolean {
        val now = System.currentTimeMillis()
        val nextAllowedAt = clickCooldowns[playerUuid] ?: 0L
        if (now < nextAllowedAt) {
            return false
        }

        clickCooldowns[playerUuid] = now + CLICK_COOLDOWN_MS
        return true
    }

    private fun getEntity(): LivingEntity? {
        val uuid = entityUuid ?: return null
        return Bukkit.getEntity(uuid) as? LivingEntity
    }

    private fun applySkin(entity: LivingEntity?) {
        val equipment = entity?.equipment ?: return
        equipment.helmet = createSkinHead()
        try {
            equipment.helmetDropChance = 0f
        } catch (_: Exception) {
        }
    }

    private fun createSkinHead(): ItemStack? {
        val type = skinType ?: return null
        val value = skinValue ?: return null
        val builder = SkullBuilder()
        return when (type) {
            SkinType.NAME -> builder.usePlayer(value).build()
            SkinType.URL -> builder.useURL(value).build()
            SkinType.BASE64 -> builder.useBase64(value).build()
        }
    }

    private fun configureEntity(plugin: UnifyCore, entity: LivingEntity) {
        entity.setMetadata(NPCManager.NPC_METADATA_KEY, FixedMetadataValue(plugin, id))
        runBooleanSetter(entity, "setCanPickupItems", false)
        runBooleanSetter(entity, "setRemoveWhenFarAway", false)
        runBooleanSetter(entity, "setAI", false)
        runBooleanSetter(entity, "setSilent", true)
        runBooleanSetter(entity, "setInvulnerable", true)
        runBooleanSetter(entity, "setCollidable", false)

        if (!hasMethod(entity, "setAI")) {
            entity.addPotionEffect(PotionEffect(PotionEffectType.SLOW, Int.MAX_VALUE, 10, true, false))
        }
    }

    private fun runBooleanSetter(target: Any, methodName: String, value: Boolean) {
        try {
            val method = target.javaClass.getMethod(methodName, java.lang.Boolean.TYPE)
            method.invoke(target, value)
        } catch (_: Exception) {
        }
    }

    private fun hasMethod(target: Any, methodName: String): Boolean {
        return try {
            target.javaClass.getMethod(methodName, java.lang.Boolean.TYPE)
            true
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private const val CLICK_COOLDOWN_MS = 200L
        private const val HOLOGRAM_HEIGHT = 2.35
        /** Must match the text-line spacing the NMS hologram renderer uses. */
        private const val LINE_SPACING = 0.25

        private fun cleanSkinValue(value: String?): String? {
            return value?.trim()?.takeIf { it.isNotEmpty() }
        }

        private fun cleanCommand(command: String?): String? {
            return command?.trim()?.takeIf { it.isNotEmpty() }
        }
    }

    enum class SkinType {
        NAME,
        URL,
        BASE64,
    }
}
