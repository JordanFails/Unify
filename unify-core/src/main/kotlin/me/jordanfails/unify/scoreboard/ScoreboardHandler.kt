package me.jordanfails.unify.scoreboard

import com.google.common.primitives.Ints
import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.scoreboard.thread.ScoreboardThread
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object ScoreboardHandler {

    private val playerBoards = ConcurrentHashMap<UUID, ScoreboardInfo>()
    private var providers = ArrayList<ScoreboardProvider>()
    
    private var enabled: Boolean = false
    private var async: Boolean = true

    var updateInterval: Int = 20

    fun initialLoad() {
        enabled = UnifyCore.instance.config.getBoolean("scoreboards.enabled", true)
        if (!enabled) {
            UnifyCore.instance.logger.info("Auto-updating scoreboards are disabled by config")
            return
        }

        updateInterval = UnifyCore.instance.config.getInt("scoreboards.update-interval-ticks", updateInterval)
            .coerceAtLeast(1)

        ScoreboardThread().start()
        
        Bukkit.getScheduler().runTaskTimerAsynchronously(UnifyCore.instance, Runnable {
            for (player in Bukkit.getOnlinePlayers()) {
                reloadPlayer(player)
            }
        }, 20L, updateInterval.toLong())

        registerProvider(ScoreboardProvider.DefaultScoreboardProvider())
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
            return
        }

        val currentBoard = playerBoards[player.uniqueId]
        if (currentBoard != null && currentBoard == provided) {
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
    }
}
