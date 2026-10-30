package me.jordanfails.unify.hologram

import me.jordanfails.unify.UnifyCore
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.logging.Level

internal object HologramViews : Listener {
    private val live = mutableMapOf<UUID, WeakReference<UnifyHologram>>()
    private val owned = mutableMapOf<UUID, Pair<Plugin, UnifyHologram>>()
    private var task: BukkitTask? = null

    fun track(hologram: UnifyHologram) {
        live[hologram.uuid] = WeakReference(hologram)
    }

    fun own(owner: Plugin, hologram: UnifyHologram) {
        owned[hologram.uuid] = owner to hologram
    }

    fun forget(hologram: UnifyHologram) {
        live.remove(hologram.uuid)
        owned.remove(hologram.uuid)
    }

    private fun all(): List<UnifyHologram> {
        live.entries.removeAll { it.value.get() == null }
        return live.values.mapNotNull { it.get() }
    }

    fun enable(plugin: UnifyCore) {
        plugin.server.pluginManager.registerEvents(this, plugin)
        var tick = 0L
        task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            tick++
            all().filter { it.updateIntervalTicks > 0L && tick % it.updateIntervalTicks == 0L }
                .forEach { hologram ->
                    try {
                        hologram.refresh()
                    } catch (error: Exception) {
                        plugin.logger.log(Level.WARNING, "Hologram ${hologram.uuid} refresh failed", error)
                    }
                }
        }, 1L, 1L)
    }

    fun disable() {
        task?.cancel()
        task = null
        all().forEach { it.close() }
        live.clear()
        owned.clear()
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        all().forEach { it.removeViewer(event.player) }
    }

    @EventHandler
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        all().forEach { it.refresh(event.player, force = true) }
    }

    @EventHandler
    fun onRespawn(event: PlayerRespawnEvent) {
        UnifyCore.instance.server.scheduler.runTaskLater(UnifyCore.instance, Runnable {
            if (event.player.isOnline) all().forEach { it.resetView(event.player) }
        }, 2L)
    }

    @EventHandler
    fun onPluginDisable(event: PluginDisableEvent) {
        owned.values.toList().filter { it.first === event.plugin }.forEach { it.second.close() }
    }
}
