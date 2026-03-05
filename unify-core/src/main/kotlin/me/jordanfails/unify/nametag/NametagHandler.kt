package me.jordanfails.unify.nametag

import com.google.common.primitives.Ints
import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.nametag.provider.OpNametagProvider
import me.jordanfails.unify.nametag.thread.NametagThread
import me.jordanfails.unify.nametag.update.NametagUpdate
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object NametagHandler {

    internal val teamMap = ConcurrentHashMap<UUID, HashMap<UUID, NametagInfo>>()

    private val registeredTeams = Collections.synchronizedList(ArrayList<NametagInfo>())
    private var teamCreateIndex = 1
    private var providers = ArrayList<NametagProvider>()

    private var isNametagRestrictionEnabled: Boolean = false
    private var nametagRestrictBypass: String = ""
    private var async: Boolean = true
    
    private var enabled: Boolean = false
    private var threadStarted: Boolean = false
    private var refreshTask: BukkitTask? = null

    var updateInterval: Int = 2

    fun initialLoad() {
        reloadAll()
    }

    fun reloadAll() {
        enabled = UnifyCore.instance.config.getBoolean("nametags.enabled", true)
        updateInterval = UnifyCore.instance.config
            .getInt("nametags.update-interval-ticks", updateInterval)
            .coerceAtLeast(1)
        if (!enabled) {
            UnifyCore.instance.logger.info("Auto-updating nametags are disabled by config")
            teamMap.clear()
            refreshTask?.cancel()
            refreshTask = null
            return
        }

        isNametagRestrictionEnabled = UnifyCore.instance.config.getBoolean("nametags.packet-restriction", false)
        nametagRestrictBypass = UnifyCore.instance.config.getString("nametags.packet-restriction-bypass-prefix") ?: ""

        if (!threadStarted) {
            NametagThread().start()
            threadStarted = true
        }
        refreshTask?.cancel()
        refreshTask = null
        providers.clear()
        teamMap.clear()
        
        // Periodic task to refresh all nametags (catches OP changes, rank changes, etc.)
        refreshTask = Bukkit.getScheduler().runTaskTimerAsynchronously(UnifyCore.instance, Runnable {
            for (player in Bukkit.getOnlinePlayers()) {
                reloadPlayer(player)
            }
        }, 40L, 40L)

        // Register providers (higher weight = higher priority)
        registerProvider(OpNametagProvider())
        registerProvider(NametagProvider.DefaultNametagProvider())
        Bukkit.getOnlinePlayers().forEach { player ->
            reloadPlayer(player)
            reloadOthersFor(player)
        }
    }

    fun registerProvider(newProvider: NametagProvider) {
        providers.add(newProvider)
        providers.sortWith { a, b -> Ints.compare(b.weight, a.weight) }
    }

    fun reloadPlayer(toRefresh: Player) {
        val update = NametagUpdate(toRefresh)

        if (async) {
            NametagThread.pendingUpdates[update] = true
        } else {
            applyUpdate(update)
        }
    }

    fun reloadOthersFor(refreshFor: Player) {
        for (toRefresh in Bukkit.getOnlinePlayers()) {
            if (refreshFor === toRefresh) {
                continue
            }
            reloadPlayer(toRefresh, refreshFor)
        }
    }

    fun reloadPlayer(toRefresh: Player, refreshFor: Player) {
        val update = NametagUpdate(toRefresh, refreshFor)

        if (async) {
            NametagThread.pendingUpdates[update] = true
        } else {
            applyUpdate(update)
        }
    }

    internal fun applyUpdate(nametagUpdate: NametagUpdate) {
        val toRefreshPlayer = Bukkit.getPlayerExact(nametagUpdate.toRefresh) ?: return

        if (nametagUpdate.refreshFor == null) {
            for (refreshFor in Bukkit.getOnlinePlayers()) {
                reloadPlayerInternal(toRefreshPlayer, refreshFor)
            }
        } else {
            val refreshForPlayer = Bukkit.getPlayerExact(nametagUpdate.refreshFor!!)
            if (refreshForPlayer != null) {
                reloadPlayerInternal(toRefreshPlayer, refreshForPlayer)
            }
        }
    }

    internal fun reloadPlayerInternal(toRefresh: Player, refreshFor: Player) {
        if (!refreshFor.hasMetadata("Nametag-Applied")) {
            return
        }

        var provided: NametagInfo? = null
        var providerIndex = 0

        while (provided == null && providerIndex < providers.size) {
            provided = providers[providerIndex++].fetchNametag(toRefresh, refreshFor)
        }

        if (provided == null) {
            return
        }

        if (isNametagRestrictionEnabled) {
            val prefix = provided.prefix
            if (!prefix.equals(nametagRestrictBypass, ignoreCase = true)) {
                return
            }
        }

        var teamInfoMap = HashMap<UUID, NametagInfo>()
        if (teamMap.containsKey(refreshFor.uniqueId)) {
            teamInfoMap = teamMap[refreshFor.uniqueId]!!
        }

        // Get dynamic limits from NMS handler
        val nms = UnifyCore.instance.nms
        val prefixLimit = nms?.getTeamPrefixLimit() ?: 16
        val nameLimit = 16 // Team names always have 16 char limit
        
        if (provided.prefix.length > prefixLimit) {
            provided.prefix = provided.prefix.substring(0, prefixLimit)
        }

        if (provided.name.length > nameLimit) {
            provided.name = provided.name.substring(0, nameLimit)
        }

        if (provided.suffix.length > prefixLimit) {
            provided.suffix = provided.suffix.substring(0, prefixLimit)
        }

        ScoreboardTeamPacketMod(provided.name, provided.prefix, provided.suffix, arrayListOf(toRefresh.name), 0).send(refreshFor)
        teamInfoMap[toRefresh.uniqueId] = provided
        teamMap[refreshFor.uniqueId] = teamInfoMap
    }

    internal fun initiatePlayer(player: Player) {
        for (teamInfo in registeredTeams) {
            teamInfo.teamAddPacket.send(player)
        }
    }

    internal fun getOrCreate(prefix: String, suffix: String): NametagInfo {
        for (teamInfo in registeredTeams) {
            if (teamInfo.prefix == prefix && teamInfo.suffix == suffix) {
                return teamInfo
            }
        }

        val newTeam = NametagInfo(teamCreateIndex++.toString(), prefix, suffix)
        registeredTeams.add(newTeam)

        val addPacket = newTeam.teamAddPacket
        for (player in Bukkit.getOnlinePlayers()) {
            addPacket.send(player)
        }

        return newTeam
    }

    fun isEnabled(): Boolean {
        return enabled
    }

    fun providerCount(): Int {
        return providers.size
    }
}
