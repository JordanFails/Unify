package me.jordanfails.unify.bossbar

import me.jordanfails.unify.module.UnifyModule
import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.config.UnifyConfig
import me.jordanfails.unify.npc.NPCRegistry
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import me.jordanfails.unify.plugin.ProviderRegistry
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Provider-driven boss-bar HUD, the boss-bar twin of
 * [me.jordanfails.unify.scoreboard.ScoreboardHandler].
 *
 * Each refresh asks the registered [BossBarProvider]s for a [BossBarInfo], diffs it against what the
 * player is currently seeing, and only touches the client when something actually changed. Rows are
 * keyed by [BossBarLine.id] so a title/progress change updates in place instead of flicker-
 * respawning the bar; nesting depth becomes the bar's indent.
 */
object BossBarHandler {

    private val playerInfo = ConcurrentHashMap<UUID, BossBarInfo>()
    private val playerBars = ConcurrentHashMap<UUID, LinkedHashMap<String, UnifyBossBar>>()
    private val providers = ProviderRegistry<BossBarProvider> { it.weight }

    private var enabled: Boolean = false
    private var refreshTask: BukkitTask? = null

    // Bumped on every restart; a display queued before it is dropped instead of reappearing after cleanup.
    @Volatile
    private var generation = 0


    var updateInterval: Int = 20
        private set

    internal fun initialLoad() {
        reloadAll()
    }

    fun reloadAll() {
        updateInterval = UnifyConfig.BossBar.updateInterval.get().coerceAtLeast(1)
        // bossbar.enabled only controls Unify's own bars from config; plugin providers work regardless.
        providers.setBuiltIns(
            if (UnifyConfig.BossBar.enabled.get()) listOf(BossBarProvider.DefaultBossBarProvider()) else emptyList()
        )
        restart()
    }

    fun registerProvider(newProvider: BossBarProvider) {
        if (!UnifyModule.BOSSBAR.allowsRegistration(newProvider)) return
        providers.add(newProvider)
        restart()
    }

    internal fun forgetPlugin(plugin: Plugin) {
        if (providers.removeOwnedBy(plugin)) restart()
    }

    private fun restart() {
        generation++
        refreshTask?.cancel()
        refreshTask = null
        enabled = UnifyModule.BOSSBAR.isEnabled && !providers.isEmpty()

        if (!enabled) {
            clearAll()
            return
        }

        refreshTask = Bukkit.getScheduler().runTaskTimer(UnifyCore.instance, Runnable {
            for (player in NPCRegistry.realOnlinePlayers()) {
                reloadPlayer(player)
            }
        }, 20L, updateInterval.toLong())

        NPCRegistry.realOnlinePlayers().forEach { reloadPlayer(it) }
    }

    fun initiatePlayer(player: Player) {
        reloadPlayer(player)
    }

    fun removePlayer(player: Player) {
        playerInfo.remove(player.uniqueId)
        dropBars(player)
    }

    /** Takes every HUD bar off one player, leaving their provider cache alone. */
    private fun dropBars(player: Player) {
        val bars = playerBars.remove(player.uniqueId) ?: return
        for (bar in bars.values) {
            bar.removePlayer(player)
            UnifyBossBar.drop(bar)
        }
    }

    /**
     * Fetches this player's HUD and pushes it if it differs from what they're already seeing.
     * Safe to call from the async refresh task; the client work is bounced back to the main thread.
     */
    fun reloadPlayer(player: Player) {
        if (!enabled) return
        if (NPCRegistry.isNpc(player)) return

        // Every provider's bars stack, highest weight on top; a repeated line id keeps its first row.
        val results = providers.allOf { it.fetchBossBar(player) }
        val provided = if (results.isEmpty()) null
            else BossBarInfo(results.flatMap { it.lines }.distinctBy { it.id })

        if (provided == null) {
            // No provider has anything to show. Whatever the player is still seeing describes a
            // state that is over — leave it up and it stays frozen there until they reconnect.
            playerInfo.remove(player.uniqueId)
            if (playerBars[player.uniqueId].isNullOrEmpty()) return
            Bukkit.getScheduler().runTask(UnifyCore.instance, Runnable { dropBars(player) })
            return
        }

        val info = provided
        if (playerInfo[player.uniqueId] == info) return
        playerInfo[player.uniqueId] = info

        val queuedIn = generation
        Bukkit.getScheduler().runTask(UnifyCore.instance, Runnable {
            if (!player.isOnline || queuedIn != generation) return@Runnable
            apply(player, info)
        })
    }

    /** Drops every HUD bar from every viewer — used on reload and shutdown. */
    fun clearAll() {
        for ((uuid, bars) in playerBars) {
            val player = Bukkit.getPlayer(uuid)
            for (bar in bars.values) {
                if (player != null) bar.removePlayer(player) else bar.removeAll()
                UnifyBossBar.drop(bar)
            }
        }
        playerBars.clear()
        playerInfo.clear()
    }

    fun isEnabled(): Boolean = enabled

    fun providerCount(): Int = providers.size

    fun providers(): List<BossBarProvider> = providers.sorted.toList()

    private fun apply(player: Player, info: BossBarInfo) {
        val flattened = info.flatten()
        val existing = playerBars.getOrPut(player.uniqueId) { LinkedHashMap() }

        // Bars stack in the order the client was told about them, so any change to the row order
        // means a full respawn; otherwise we can update every row in place.
        val reorder = existing.keys.toList() != flattened.map { it.second.id }
        if (reorder) {
            for (bar in existing.values) {
                bar.removePlayer(player)
                UnifyBossBar.drop(bar)
            }
            existing.clear()
        }

        for ((depth, line) in flattened) {
            val bar = existing[line.id]
            if (bar == null) {
                val created = UnifyBossBar(line.title, line.progress, line.color, line.style, line.flags, depth)
                existing[line.id] = created
                created.addPlayer(player)
            } else {
                bar.setTitle(line.title)
                bar.setProgress(line.progress)
                bar.setColor(line.color)
                bar.setStyle(line.style)
                bar.setFlags(line.flags)
                bar.setIndent(depth)
            }
        }
    }
}
