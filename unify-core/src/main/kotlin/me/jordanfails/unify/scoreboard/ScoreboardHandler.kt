package me.jordanfails.unify.scoreboard

import com.google.common.primitives.Ints
import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.config.UnifyConfig
import me.jordanfails.unify.scoreboard.thread.ScoreboardThread
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object ScoreboardHandler {

    private val playerBoards = ConcurrentHashMap<UUID, ScoreboardInfo>()
    private val providers = ArrayList<ScoreboardProvider>()
    
    private var enabled: Boolean = false
    private var async: Boolean = true
    private var threadStarted: Boolean = false
    private var refreshTask: BukkitTask? = null

    var updateInterval: Int = 20

    fun initialLoad() {
        reloadAll()
    }

    fun reloadAll() {
        enabled = UnifyConfig.Scoreboard.enabled.get()
        updateInterval = UnifyConfig.Scoreboard.updateInterval.get().coerceAtLeast(1)

        refreshTask?.cancel()
        refreshTask = null
        providers.clear()

        if (!enabled) {
            UnifyCore.instance.logger.info("Auto-updating scoreboards are disabled by config")
            playerBoards.clear()
            return
        }

        if (!threadStarted) {
            ScoreboardThread().start()
            threadStarted = true
        }

        refreshTask = Bukkit.getScheduler().runTaskTimerAsynchronously(UnifyCore.instance, Runnable {
            for (player in Bukkit.getOnlinePlayers()) {
                reloadPlayer(player)
            }
        }, 20L, updateInterval.toLong())

        Bukkit.getOnlinePlayers().forEach { reloadPlayer(it) }
    }

    fun registerProvider(newProvider: ScoreboardProvider) {
        providers.add(newProvider)
        providers.sortWith { a, b -> Ints.compare(b.weight, a.weight) }
    }

    fun reloadPlayer(player: Player) {
        val update = ScoreboardUpdate(player)

        if (async) {
            ScoreboardThread.pendingUpdates[update] = true
        } else {
            applyUpdate(update)
        }
    }

    internal fun applyUpdate(scoreboardUpdate: ScoreboardUpdate) {
        val player = Bukkit.getPlayerExact(scoreboardUpdate.player.name) ?: return

        var provided: ScoreboardInfo? = null
        var providerIndex = 0

        while (provided == null && providerIndex < providers.size) {
            provided = providers[providerIndex++].fetchScoreboard(player)
        }

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
            if (!player.isOnline) return@Runnable

            val packet = ScoreboardPacket.createFromInfo(provided)
            packet.send(player)
        })
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
        return providers
    }
}
