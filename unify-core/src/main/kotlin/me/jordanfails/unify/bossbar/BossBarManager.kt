package me.jordanfails.unify.bossbar

import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.config.UnifyConfig
import me.jordanfails.unify.npc.NPCRegistry
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry + tick loop for standalone [UnifyBossBar]s (the ones you create yourself, as opposed to
 * the provider-driven HUD owned by [BossBarHandler]).
 *
 * The tick loop drains countdowns, refreshes titles, and reconciles [BossBarScope] membership so a
 * `GLOBAL` or `WORLD` bar picks up players who joined or changed worlds since the last tick.
 */
object BossBarManager {

    private val named = ConcurrentHashMap<String, UnifyBossBar>()

    private var tickTask: BukkitTask? = null

    /** Ticks between countdown/scope refreshes. */
    var tickInterval: Int = 4
        private set

    fun enable(plugin: JavaPlugin) {
        tickInterval = UnifyConfig.BossBar.tickInterval.get().coerceAtLeast(1)
        tickTask?.cancel()
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable { tick() }, 20L, tickInterval.toLong())
    }

    fun disable() {
        tickTask?.cancel()
        tickTask = null
        for (bar in named.values.toList()) {
            bar.removeAll()
            UnifyBossBar.drop(bar)
        }
        named.clear()
        for (bar in UnifyBossBar.allLive()) {
            bar.removeAll()
            UnifyBossBar.drop(bar)
        }
    }

    /** Creates (or replaces) a bar registered under [name]. */
    @JvmStatic
    @JvmOverloads
    fun create(
        name: String,
        title: String,
        progress: Double = 1.0,
        color: BossBarColor = BossBarColor.PURPLE,
        style: BossBarStyle = BossBarStyle.SOLID,
    ): UnifyBossBar {
        remove(name)
        val bar = UnifyBossBar(title, progress, color, style)
        named[name.lowercase()] = bar
        return bar
    }

    /** Registers an existing bar under [name], replacing any previous holder of that name. */
    @JvmStatic
    fun register(name: String, bar: UnifyBossBar): UnifyBossBar {
        remove(name)
        named[name.lowercase()] = bar
        return bar
    }

    @JvmStatic
    fun get(name: String): UnifyBossBar? = named[name.lowercase()]

    @JvmStatic
    fun getOrCreate(name: String, title: String): UnifyBossBar = get(name) ?: create(name, title)

    /** Removes the bar registered under [name] from every viewer. Returns true if one existed. */
    @JvmStatic
    fun remove(name: String): Boolean {
        val bar = named.remove(name.lowercase()) ?: return false
        bar.removeAll()
        UnifyBossBar.drop(bar)
        return true
    }

    /** Removes [bar] from every viewer and un-registers it under whatever name it holds. */
    @JvmStatic
    fun delete(bar: UnifyBossBar) {
        named.entries.removeIf { it.value === bar }
        bar.removeAll()
        UnifyBossBar.drop(bar)
    }

    @JvmStatic
    fun names(): Set<String> = named.keys.toSet()

    @JvmStatic
    fun all(): List<UnifyBossBar> = named.values.toList()

    /** Hides every named bar from [player]; the HUD is handled separately by [BossBarHandler]. */
    fun handleQuit(player: Player) {
        for (bar in named.values) {
            bar.removePlayer(player)
        }
    }

    /** Re-evaluates scope membership for [player] — used on join and world change. */
    fun handleScopeChange(player: Player) {
        if (NPCRegistry.isNpc(player)) return
        for (bar in named.values) {
            reconcile(bar, player)
        }
    }

    private fun tick() {
        val players = NPCRegistry.realOnlinePlayers()
        for (bar in named.values.toList()) {
            try {
                if (bar.tick()) continue
                for (player in players) {
                    reconcile(bar, player)
                }
            } catch (ex: Exception) {
                UnifyCore.instance.logger.warning("Failed to tick boss bar: ${ex.message}")
            }
        }
    }

    /** Adds/removes [player] so the bar's viewer set matches its [BossBarScope]. */
    private fun reconcile(bar: UnifyBossBar, player: Player) {
        if (bar.scope == BossBarScope.VIEWERS) return
        val shouldSee = bar.visible && when (bar.scope) {
            BossBarScope.GLOBAL -> true
            BossBarScope.WORLD -> player.world == bar.world
            BossBarScope.VIEWERS -> false
        }
        if (shouldSee && !bar.hasPlayer(player)) {
            bar.addPlayer(player)
        } else if (!shouldSee && bar.hasPlayer(player)) {
            bar.removePlayer(player)
        }
    }

    /** Convenience: a global bar that clears itself after [millis]. */
    @JvmStatic
    fun announce(name: String, title: String, millis: Long, color: BossBarColor = BossBarColor.PURPLE): UnifyBossBar {
        return create(name, title, 1.0, color).apply {
            setDuration(millis)
            showToAll()
        }
    }

    /** Convenience: a world-scoped bar that clears itself after [millis]. */
    @JvmStatic
    fun announce(name: String, title: String, millis: Long, world: World): UnifyBossBar {
        return create(name, title).apply {
            setDuration(millis)
            showToWorld(world)
        }
    }
}
