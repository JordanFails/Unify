package me.jordanfails.unify.nametag

import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.hologram.HologramLine
import me.jordanfails.unify.hologram.UnifyHologram
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Provider results arrive asynchronously; holograms and player tracking stay on the main thread. */
internal object NametagLines {
    private data class View(val target: UUID, val viewer: UUID)

    private val pending = ConcurrentHashMap<View, List<String>>()
    private val holograms = mutableMapOf<View, UnifyHologram>()
    private var task: BukkitTask? = null

    fun enable() {
        task = Bukkit.getScheduler().runTaskTimer(UnifyCore.instance, Runnable { tick() }, 1L, 2L)
    }

    fun update(target: Player, viewer: Player, lines: List<String>) {
        if (target.uniqueId == viewer.uniqueId) return
        pending[View(target.uniqueId, viewer.uniqueId)] = lines.toList()
    }

    fun removePlayer(player: Player) {
        pending.keys.removeAll { it.target == player.uniqueId || it.viewer == player.uniqueId }
        val iterator = holograms.iterator()
        while (iterator.hasNext()) {
            val (view, hologram) = iterator.next()
            if (view.target == player.uniqueId || view.viewer == player.uniqueId) {
                hologram.close()
                iterator.remove()
            }
        }
    }

    fun disable() {
        task?.cancel()
        task = null
        pending.clear()
        holograms.values.forEach { it.close() }
        holograms.clear()
    }

    private fun tick() {
        for ((view, lines) in pending.entries.toList()) {
            if (!pending.remove(view, lines)) continue
            if (lines.isEmpty()) {
                holograms.remove(view)?.close()
                continue
            }
            val target = Bukkit.getPlayer(view.target) ?: continue
            val viewer = Bukkit.getPlayer(view.viewer) ?: continue
            val hologram = holograms.getOrPut(view) { UnifyHologram(target.location) }
            // Viewer membership is reconciled below before anything is spawned.
            if (!canSee(target, viewer)) hologram.removeViewer(viewer)
            hologram.lines = lines.map { HologramLine.Text(it) }
        }

        val iterator = holograms.iterator()
        while (iterator.hasNext()) {
            val (view, hologram) = iterator.next()
            val target = Bukkit.getPlayer(view.target)
            val viewer = Bukkit.getPlayer(view.viewer)
            if (target == null || viewer == null || !NametagHandler.isEnabled()) {
                hologram.close()
                iterator.remove()
                continue
            }
            if (!canSee(target, viewer)) {
                hologram.removeViewer(viewer)
                continue
            }
            val height = target.eyeHeight + 0.1 + (hologram.lines.size - 1) * 0.25
            val location = target.location.add(0.0, height, 0.0)
            location.yaw = 0f
            location.pitch = 0f
            hologram.teleport(location)
            if (!hologram.hasViewer(viewer)) hologram.addViewer(viewer)
        }
    }

    private fun canSee(target: Player, viewer: Player): Boolean =
        target.isOnline && viewer.isOnline && !target.isDead && !target.isSneaking &&
            target.gameMode != GameMode.SPECTATOR && !target.hasPotionEffect(PotionEffectType.INVISIBILITY) &&
            target.world == viewer.world && viewer.canSee(target) && !NametagHandler.isHidden(target) &&
            target.location.distanceSquared(viewer.location) <= 64.0 * 64.0
}
