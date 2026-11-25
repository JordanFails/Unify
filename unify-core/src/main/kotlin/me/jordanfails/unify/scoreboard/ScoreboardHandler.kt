package me.jordanfails.unify.scoreboard

import com.google.common.primitives.Ints
import me.jordanfails.unify.module.UnifyModule
import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.config.UnifyConfig
import me.jordanfails.unify.npc.NPCRegistry
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import me.jordanfails.unify.plugin.ProviderRegistry
import org.bukkit.scheduler.BukkitTask
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object ScoreboardHandler {

    private val playerBoards = ConcurrentHashMap<UUID, ScoreboardInfo>()
    private val providers = ProviderRegistry<ScoreboardProvider> { it.weight }
    
    @Volatile
    private var enabled: Boolean = false
    private var refreshTask: BukkitTask? = null

    // Bumped on every restart; a display queued before it is dropped instead of reappearing after cleanup.
    @Volatile
    private var generation = 0


    var updateInterval: Int = 20

    internal fun initialLoad() {
        reloadAll()
    }

    fun reloadAll() {
        updateInterval = UnifyConfig.Scoreboard.updateInterval.get().coerceAtLeast(1)
        restart()
    }

    fun registerProvider(newProvider: ScoreboardProvider) {
        if (!UnifyModule.SCOREBOARD.allowsRegistration(newProvider)) return
        providers.add(newProvider)
        restart()
    }

    internal fun forgetPlugin(plugin: Plugin) {
        if (providers.removeOwnedBy(plugin)) restart()
    }

    // Unify ships no scoreboard of its own, so this only runs while some plugin provides one.
    private fun restart() {
        generation++
        refreshTask?.cancel()
        refreshTask = null
        enabled = UnifyModule.SCOREBOARD.isEnabled && !providers.isEmpty()

        if (!enabled) {
            playerBoards.keys.mapNotNull(Bukkit::getPlayer).forEach(ScoreboardPacket::removePacketSidebar)
            playerBoards.clear()
            return
        }


        refreshTask = Bukkit.getScheduler().runTaskTimer(UnifyCore.instance, Runnable {
            for (player in NPCRegistry.realOnlinePlayers()) {
                reloadPlayer(player)
            }
        }, 20L, updateInterval.toLong())

        NPCRegistry.realOnlinePlayers().forEach { reloadPlayer(it) }
    }

    fun reloadPlayer(player: Player) {
        if (!enabled || !player.isOnline) return
        if (NPCRegistry.isNpc(player)) return

        val queuedIn = generation
        val provided = providers.firstOf { it.fetchScoreboard(player) }

        if (provided == null) {
            val hadBoard = playerBoards.remove(player.uniqueId)
            if (hadBoard != null) {
                Bukkit.getScheduler().runTask(UnifyCore.instance, Runnable {
                    if (!player.isOnline) return@Runnable
                    ScoreboardPacket.removePacketSidebar(player)
                })
            }
            return
        }

        val previousBoard = playerBoards[player.uniqueId]
        if (previousBoard != null && previousBoard == provided) {
            return
        }

        playerBoards[player.uniqueId] = provided

        Bukkit.getScheduler().runTask(UnifyCore.instance, Runnable {
            if (!player.isOnline || queuedIn != generation) return@Runnable

            val packet = ScoreboardPacket.createFromInfo(provided)
            packet.send(player)
        })
    }

    internal fun disable() {
        generation++
        playerBoards.keys.mapNotNull(Bukkit::getPlayer).forEach(ScoreboardPacket::removePacketSidebar)
        playerBoards.clear()
        refreshTask?.cancel()
        refreshTask = null
        enabled = false
    }

    fun initiatePlayer(player: Player) {
        reloadPlayer(player)
    }

    fun removePlayer(player: Player) {
        playerBoards.remove(player.uniqueId)
        ScoreboardPacket.clearSidebarState(player.uniqueId)
    }

    fun isEnabled(): Boolean {
        return enabled
    }

    fun providerCount(): Int {
        return providers.size
    }

    fun providers(): List<ScoreboardProvider> {
        return providers.sorted.toList()
    }
}
