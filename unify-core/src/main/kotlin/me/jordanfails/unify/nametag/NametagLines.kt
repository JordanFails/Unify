package me.jordanfails.unify.nametag

import java.util.logging.Level
import me.jordanfails.unify.UnifyCore
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Extra lines ride the player on the viewer's client (1.20.5+), so they never lag behind.
 * Older versions can't do that and get no extra lines at all.
 *
 * Provider results arrive asynchronously; everything else stays on the main thread.
 */
internal object NametagLines {
    private data class View(val target: UUID, val viewer: UUID)

    private val pending = ConcurrentHashMap<View, List<String>>()
    private val wanted = mutableMapOf<View, List<String>>()
    private var task: BukkitTask? = null

    fun enable() {
        if (UnifyCore.instance.nms?.supportsRidingNametagLines() != true) {
            if (UnifyCore.instance.config.getStringList("nametags.lines").isNotEmpty()) {
                UnifyCore.instance.logger.info("Multi-line nametags need 1.20.5+; nametags.lines is ignored on this version")
            }
            return
        }
        task = Bukkit.getScheduler().runTaskTimer(UnifyCore.instance, Runnable { tick() }, 1L, 1L)
    }

    fun update(target: Player, viewer: Player, lines: List<String>) {
        if (task == null || target.uniqueId == viewer.uniqueId) return
        pending[View(target.uniqueId, viewer.uniqueId)] = lines.toList()
    }

    fun removePlayer(player: Player) {
        pending.keys.removeAll { it.target == player.uniqueId || it.viewer == player.uniqueId }
        val iterator = wanted.keys.iterator()
        while (iterator.hasNext()) {
            val view = iterator.next()
            if (view.target == player.uniqueId || view.viewer == player.uniqueId) {
                hide(view)
                iterator.remove()
            }
        }
    }

    fun disable() {
        task?.cancel()
        task = null
        pending.clear()
        wanted.keys.forEach { hide(it) }
        wanted.clear()
    }

    private fun tick() {
        for ((view, lines) in pending.entries.toList()) {
            if (!pending.remove(view, lines)) continue
            if (lines.isEmpty()) {
                if (wanted.remove(view) != null) hide(view)
            } else {
                wanted[view] = lines
            }
        }

        val nms = UnifyCore.instance.nms ?: return
        val iterator = wanted.entries.iterator()
        while (iterator.hasNext()) {
            val (view, lines) = iterator.next()
            val target = Bukkit.getPlayer(view.target)
            val viewer = Bukkit.getPlayer(view.viewer)
            if (target == null || viewer == null || !NametagHandler.isEnabled()) {
                hide(view)
                iterator.remove()
                continue
            }
            try {
                if (canSee(target, viewer)) nms.showNametagLines(viewer, target, lines) else hide(view)
            } catch (error: Exception) {
                // One bad pair (e.g. a player mid tracking change) shouldn't stop everyone else's lines.
                UnifyCore.instance.logger.log(Level.FINE, "Nametag lines failed for ${viewer.name} -> ${target.name}", error)
            }
        }
    }

    private fun hide(view: View) {
        val viewer = Bukkit.getPlayer(view.viewer) ?: return
        UnifyCore.instance.nms?.hideNametagLines(viewer, view.target)
    }

    private fun canSee(target: Player, viewer: Player): Boolean =
        target.isOnline && viewer.isOnline && !target.isDead &&
            target.gameMode != GameMode.SPECTATOR && !target.hasPotionEffect(PotionEffectType.INVISIBILITY) &&
            target.world == viewer.world && viewer.canSee(target) && !NametagHandler.isHidden(target)
}
