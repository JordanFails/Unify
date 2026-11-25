package me.jordanfails.unify.nametag

import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.module.UnifyModule
import me.jordanfails.unify.nametag.provider.OpNametagProvider
import me.jordanfails.unify.nametag.thread.NametagThread
import me.jordanfails.unify.nametag.update.NametagUpdate
import me.jordanfails.unify.plugin.ProviderRegistry
import me.jordanfails.unify.plugin.owningPlugin
import me.jordanfails.unify.tab.TabHandler
import me.jordanfails.unify.tab.TabSortProvider
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Nametag teams per viewer. Providers run on [NametagThread] because the work grows with
 * players x viewers; everything the thread reads is kept in thread-safe maps updated from the main
 * thread, and every packet goes out under [sendLock] so a restart can't race a send.
 */
object NametagHandler {

    internal val teamMap = ConcurrentHashMap<UUID, MutableMap<UUID, NametagInfo>>()

    // Targets whose plate is hidden per viewer; they sit in a separate hide team that needs its own cleanup.
    private val hiddenFor = ConcurrentHashMap<UUID, MutableSet<UUID>>()

    // Online players, maintained on join/quit so the nametag thread never reads Bukkit's live player list.
    private val viewers = ConcurrentHashMap<UUID, Player>()

    // A set, so the same refresh queued twice before the thread gets to it runs once.
    internal val pending: MutableSet<NametagUpdate> = ConcurrentHashMap.newKeySet()

    private val registeredTeams = ArrayList<NametagInfo>()
    private var teamCreateIndex = 1
    private val providers = ProviderRegistry<NametagProvider> { it.weight }
    private val sendLock = Any()

    /** Plugins that want a player's plate gone entirely; see [hideNametagsWhen]. */
    private val hideRules = CopyOnWriteArrayList<(Player) -> Boolean>()

    private var isNametagRestrictionEnabled: Boolean = false
    private var nametagRestrictBypass: String = ""

    @Volatile
    private var enabled: Boolean = false

    // Bumped (under sendLock) on every restart; work started before it is dropped instead of sent.
    @Volatile
    private var generation = 0
    private var thread: NametagThread? = null
    private var refreshTask: BukkitTask? = null

    var updateInterval: Int = 2

    internal fun initialLoad() {
        reloadAll()
    }

    fun reloadAll() {
        val config = UnifyCore.instance.config
        updateInterval = config.getInt("nametags.update-interval-ticks", updateInterval).coerceAtLeast(1)
        isNametagRestrictionEnabled = config.getBoolean("nametags.packet-restriction", false)
        nametagRestrictBypass = config.getString("nametags.packet-restriction-bypass-prefix") ?: ""
        // nametags.enabled only controls Unify's own plates from config; plugin providers work regardless.
        providers.setBuiltIns(
            if (config.getBoolean("nametags.enabled", false)) {
                listOf(OpNametagProvider(), NametagProvider.DefaultNametagProvider())
            } else emptyList()
        )
        restart()
    }

    fun registerProvider(newProvider: NametagProvider) {
        if (!UnifyModule.NAMETAGS.allowsRegistration(newProvider)) return
        providers.add(newProvider)
        restart()
    }

    internal fun forgetPlugin(plugin: Plugin) {
        val rulesRemoved = hideRules.removeAll { owningPlugin(it) === plugin }
        when {
            providers.removeOwnedBy(plugin) -> restart()
            rulesRemoved -> refreshAll()
        }
    }

    private fun restart() {
        NametagLines.disable()
        refreshTask?.cancel()
        refreshTask = null
        pending.clear()
        synchronized(sendLock) {
            generation++
            // Teams outlive the server's maps on the client, and the maps are rebuilt below, so drop
            // every team we sent; otherwise old prefixes stay visible and old teams pile up.
            if (enabled) clearClientTeams()
            enabled = UnifyModule.NAMETAGS.isEnabled && !providers.isEmpty()
            teamMap.clear()
            hiddenFor.clear()
            registeredTeams.clear()
        }
        if (!enabled) return

        if (thread == null) {
            thread = NametagThread().also { it.start() }
        }
        NametagLines.enable()
        viewers.clear()
        Bukkit.getOnlinePlayers().forEach { viewers[it.uniqueId] = it }

        // Periodic refresh (catches OP changes, rank changes, etc.).
        refreshTask = Bukkit.getScheduler().runTaskTimer(UnifyCore.instance, Runnable { refreshAll() }, 40L, 40L)
        refreshAll()
    }

    /**
     * Hides the plate of any player [rule] matches, for everyone. A provider cannot do this: it only
     * picks a prefix, and the team it lands on is sent with `ALWAYS` on every refresh — so a plugin
     * that wants a name gone has to be asked here, before the team goes out.
     *
     * Rules run on the nametag thread like providers do, and survive [reloadAll]: they are wiring, not config.
     */
    fun hideNametagsWhen(rule: (Player) -> Boolean) {
        if (!UnifyModule.NAMETAGS.allowsRegistration(rule)) return
        hideRules += rule
    }

    internal fun isHidden(player: Player): Boolean =
        hideRules.any { runCatching { it(player) }.getOrDefault(false) }

    internal fun addViewer(player: Player) {
        viewers[player.uniqueId] = player
    }

    internal fun removeViewer(player: Player) {
        viewers.remove(player.uniqueId)
        teamMap.remove(player.uniqueId)
        hiddenFor.remove(player.uniqueId)
        // Forget them as a target too, so their plate is sent fresh when they rejoin.
        teamMap.values.forEach { it.remove(player.uniqueId) }
        hiddenFor.values.forEach { it.remove(player.uniqueId) }
    }

    private fun refreshAll() {
        if (!enabled) return
        viewers.keys.forEach { pending += NametagUpdate(it, null) }
    }

    /** Refreshes [toRefresh]'s plate for every viewer. Safe to call from any thread. */
    fun reloadPlayer(toRefresh: Player) {
        if (enabled) pending += NametagUpdate(toRefresh.uniqueId, null)
    }

    /** Refreshes every other player's plate as [refreshFor] sees them. */
    fun reloadOthersFor(refreshFor: Player) {
        if (!enabled) return
        viewers.keys.filter { it != refreshFor.uniqueId }.forEach { pending += NametagUpdate(it, refreshFor.uniqueId) }
    }

    fun reloadPlayer(toRefresh: Player, refreshFor: Player) {
        if (enabled) pending += NametagUpdate(toRefresh.uniqueId, refreshFor.uniqueId)
    }

    internal fun applyUpdate(update: NametagUpdate) {
        if (!enabled) return
        val target = viewers[update.target] ?: return
        if (update.viewer == null) {
            viewers.values.forEach { reloadPlayerInternal(target, it) }
        } else {
            viewers[update.viewer]?.let { reloadPlayerInternal(target, it) }
        }
    }

    private fun reloadPlayerInternal(toRefresh: Player, refreshFor: Player) {
        val startedIn = generation

        if (isHidden(toRefresh)) {
            synchronized(sendLock) {
                if (startedIn != generation) return
                NametagLines.update(toRefresh, refreshFor, emptyList())
                if (hiddenFor.getOrPut(refreshFor.uniqueId) { ConcurrentHashMap.newKeySet() }.add(toRefresh.uniqueId)) {
                    UnifyCore.instance.nms?.sendHideNametagPacket(refreshFor, toRefresh)
                    teamMap[refreshFor.uniqueId]?.remove(toRefresh.uniqueId)
                }
            }
            return
        }

        // No answer means a plain plate: a blank team replaces whatever prefix the player had before.
        val provided = providers.firstOf { it.fetchNametag(toRefresh, refreshFor) }
            ?: NametagProvider.createNametag("", "")

        if (isNametagRestrictionEnabled && !provided.prefix.equals(nametagRestrictBypass, ignoreCase = true)) {
            NametagLines.update(toRefresh, refreshFor, emptyList())
            return
        }

        val prefixLimit = UnifyCore.instance.nms?.getTeamPrefixLimit() ?: 16
        val prefix = provided.prefix.take(prefixLimit)
        val suffix = provided.suffix.take(prefixLimit)
        val sortWeight = TabHandler.sortWeight(toRefresh)

        synchronized(sendLock) {
            if (startedIn != generation) return
            hiddenFor[refreshFor.uniqueId]?.remove(toRefresh.uniqueId)
            val team = getOrCreate(prefix, suffix, sortWeight)
            val sent = teamMap.getOrPut(refreshFor.uniqueId) { ConcurrentHashMap() }
            // The periodic refresh asks about every pair; only the ones that changed need a packet.
            if (sent[toRefresh.uniqueId] !== team) {
                UnifyCore.instance.nms?.sendNametagPacket(refreshFor, toRefresh, team.name, team.prefix, team.suffix)
                sent[toRefresh.uniqueId] = team
            }
            NametagLines.update(toRefresh, refreshFor, provided.lines)
        }
    }

    // Callers hold sendLock.
    private fun getOrCreate(prefix: String, suffix: String, sortWeight: Int): NametagInfo {
        val clampedWeight = sortWeight.coerceIn(0, TabSortProvider.MAX_WEIGHT)
        registeredTeams.firstOrNull { it.prefix == prefix && it.suffix == suffix && it.sortWeight == clampedWeight }
            ?.let { return it }

        val newTeam = NametagInfo(nextTeamName(clampedWeight), prefix, suffix, clampedWeight)
        registeredTeams.add(newTeam)
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

    // Callers hold sendLock.
    private fun clearClientTeams() {
        val nms = UnifyCore.instance.nms ?: return
        for ((viewerId, teams) in teamMap) {
            val viewer = viewers[viewerId] ?: continue
            teams.values.map { it.name }.distinct().forEach { nms.sendRemoveTeamPacket(viewer, it) }
        }
        for ((viewerId, hidden) in hiddenFor) {
            val viewer = viewers[viewerId] ?: continue
            hidden.mapNotNull { viewers[it] }.forEach { nms.sendRemoveNametagTeamPacket(viewer, it) }
        }
    }

    internal fun disable() {
        thread?.interrupt()
        thread = null
        refreshTask?.cancel()
        refreshTask = null
        pending.clear()
        synchronized(sendLock) {
            generation++
            if (enabled) clearClientTeams()
            enabled = false
        }
        NametagLines.disable()
    }

    fun providerCount(): Int {
        return providers.size
    }
}
