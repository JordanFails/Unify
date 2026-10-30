package me.jordanfails.unify.nametag

import com.google.common.primitives.Ints
import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.nametag.provider.OpNametagProvider
import me.jordanfails.unify.nametag.thread.NametagThread
import me.jordanfails.unify.nametag.update.NametagUpdate
import me.jordanfails.unify.tab.TabHandler
import me.jordanfails.unify.tab.TabSortProvider
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

object NametagHandler {

    internal val teamMap = ConcurrentHashMap<UUID, HashMap<UUID, NametagInfo>>()

    private val registeredTeams = Collections.synchronizedList(ArrayList<NametagInfo>())
    private var teamCreateIndex = 1
    private var providers = ArrayList<NametagProvider>()

    /** Plugins that want a player's plate gone entirely; see [hideNametagsWhen]. */
    private val hideRules = CopyOnWriteArrayList<(Player) -> Boolean>()

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

    /**
     * Hides the plate of any player [rule] matches, for everyone. A provider cannot do this: it only
     * picks a prefix, and the team it lands on is sent with `ALWAYS` on every refresh — so a plugin
     * that wants a name gone has to be asked here, before the team goes out.
     *
     * Rules survive [reloadAll]: they are wiring, not config.
     */
    fun hideNametagsWhen(rule: (Player) -> Boolean) {
        hideRules += rule
    }

    private fun isHidden(player: Player): Boolean =
        hideRules.any { runCatching { it(player) }.getOrDefault(false) }

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

        if (isHidden(toRefresh)) {
            UnifyCore.instance.nms?.sendHideNametagPacket(refreshFor, toRefresh)
            return
        }

        var provided: NametagInfo? = null
        var providerIndex = 0

        while (provided == null && providers.isNotEmpty()) {
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

        if (provided.prefix.length > prefixLimit) {
            provided.prefix = provided.prefix.substring(0, prefixLimit)
        }

        if (provided.suffix.length > prefixLimit) {
            provided.suffix = provided.suffix.substring(0, prefixLimit)
        }

        val sortWeight = TabHandler.sortWeight(toRefresh)
        val team = getOrCreate(provided.prefix, provided.suffix, sortWeight)

        ScoreboardTeamPacketMod(team.name, team.prefix, team.suffix, arrayListOf(toRefresh.name), 0).send(refreshFor)
        teamInfoMap[toRefresh.uniqueId] = team
        teamMap[refreshFor.uniqueId] = teamInfoMap
    }

    internal fun initiatePlayer(player: Player) {
        for (teamInfo in registeredTeams) {
            teamInfo.teamAddPacket.send(player)
        }
    }

    internal fun getOrCreate(prefix: String, suffix: String, sortWeight: Int = 0): NametagInfo {
        val clampedWeight = sortWeight.coerceIn(0, TabSortProvider.MAX_WEIGHT)
        for (teamInfo in registeredTeams) {
            if (teamInfo.prefix == prefix && teamInfo.suffix == suffix && teamInfo.sortWeight == clampedWeight) {
                return teamInfo
            }
        }

        val newTeam = NametagInfo(nextTeamName(clampedWeight), prefix, suffix, clampedWeight)
        registeredTeams.add(newTeam)

        val addPacket = newTeam.teamAddPacket
        for (player in Bukkit.getOnlinePlayers()) {
            addPacket.send(player)
        }

        return newTeam
    }

    /**
     * Minecraft sorts the tab list lexicographically by team name, then by player name.
     * Invert the sort weight so a higher rank (larger weight) becomes an earlier team name.
     */
    private fun nextTeamName(sortWeight: Int): String {
        val inverted = (TabSortProvider.MAX_WEIGHT - sortWeight).toString().padStart(4, '0')
        return (inverted + teamCreateIndex++).take(16)
    }

    fun isEnabled(): Boolean {
        return enabled
    }

    fun providerCount(): Int {
        return providers.size
    }
}
