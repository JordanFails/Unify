package me.jordanfails.unify.tab

import me.jordanfails.unify.module.UnifyModule
import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.nametag.NametagHandler
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import me.jordanfails.unify.plugin.ProviderRegistry
import org.bukkit.scheduler.BukkitTask
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object TabHandler {

    private val playerTabs = ConcurrentHashMap<UUID, TabInfo>()
    private val providers = ProviderRegistry<TabProvider> { it.weight }
    private val sortProviders = ProviderRegistry<TabSortProvider> { it.weight }

    private var enabled: Boolean = false
    private var refreshTask: BukkitTask? = null

    // Bumped on every restart; a display queued before it is dropped instead of reappearing after cleanup.
    @Volatile
    private var generation = 0

    var updateInterval: Int = 40

    internal fun initialLoad() {
        reloadAll()
    }

    fun reloadAll() {
        val config = UnifyCore.instance.config
        updateInterval = config.getInt("tab.update-interval-ticks", updateInterval).coerceAtLeast(1)
        // tab.enabled only controls Unify's own header/footer from config; plugin providers work regardless.
        providers.setBuiltIns(
            if (config.getBoolean("tab.enabled", false)) listOf(TabProvider.DefaultTabProvider()) else emptyList()
        )
        restart()
    }

    internal fun disable() {
        generation++
        refreshTask?.cancel()
        refreshTask = null
        enabled = false
        val nms = UnifyCore.instance.nms
        playerTabs.keys.mapNotNull(Bukkit::getPlayer).forEach { nms?.sendTabHeaderFooter(it, "", "") }
        playerTabs.clear()
    }

    fun registerProvider(newProvider: TabProvider) {
        if (!UnifyModule.TAB.allowsRegistration(newProvider)) return
        providers.add(newProvider)
        restart()
    }

    internal fun forgetPlugin(plugin: Plugin) {
        if (providers.removeOwnedBy(plugin)) restart()
        if (sortProviders.removeOwnedBy(plugin) && NametagHandler.isEnabled()) {
            Bukkit.getOnlinePlayers().forEach { NametagHandler.reloadPlayer(it) }
        }
    }

    private fun restart() {
        generation++
        refreshTask?.cancel()
        refreshTask = null
        enabled = UnifyModule.TAB.isEnabled && !providers.isEmpty()

        if (!enabled) {
            val nms = UnifyCore.instance.nms
            playerTabs.keys.mapNotNull(Bukkit::getPlayer).forEach { nms?.sendTabHeaderFooter(it, "", "") }
            playerTabs.clear()
            return
        }

        refreshTask = Bukkit.getScheduler().runTaskTimer(UnifyCore.instance, Runnable {
            for (player in Bukkit.getOnlinePlayers()) {
                reloadPlayer(player)
            }
        }, 20L, updateInterval.toLong())

        Bukkit.getOnlinePlayers().forEach { reloadPlayer(it) }
    }

    /**
     * Register a [TabSortProvider] from another plugin. Sort providers survive `/unify reload`
     * (they are owned by the plugin that registered them, not by config).
     *
     * Already-online players are refreshed so the new order applies immediately.
     */
    fun registerSortProvider(newProvider: TabSortProvider) {
        if (!UnifyModule.TAB.allowsRegistration(newProvider)) return
        sortProviders.add(newProvider)

        if (!NametagHandler.isEnabled()) {
            UnifyCore.instance.logger.warning(
                "Tab sort provider '${newProvider.name}' registered, but nametags are disabled. " +
                    "Tab sorting uses nametag teams, so it only applies once a nametag provider is registered or nametags.enabled is true."
            )
            return
        }

        Bukkit.getOnlinePlayers().forEach { NametagHandler.reloadPlayer(it) }
    }

    /**
     * Highest-priority non-null sort weight for [player], or `0` when no provider has an opinion.
     * Higher values appear higher in the tab list.
     */
    fun sortWeight(player: Player): Int {
        if (!UnifyCore.instance.modules.isEnabled(UnifyModule.TAB)) return 0
        return sortProviders.firstOf { it.fetchSortWeight(player) } ?: 0
    }

    fun initiatePlayer(player: Player) {
        reloadPlayer(player)
    }

    fun removePlayer(player: Player) {
        playerTabs.remove(player.uniqueId)
    }

    fun sendTab(player: Player) {
        reloadPlayer(player)
    }

    fun reloadPlayer(player: Player) {
        if (!enabled) return

        val provided = providers.firstOf { it.fetchTab(player) }
        if (provided == null) {
            // No provider applies to this player anymore; don't leave a stale header/footer up.
            if (playerTabs.remove(player.uniqueId) != null) {
                Bukkit.getScheduler().runTask(UnifyCore.instance, Runnable {
                    if (player.isOnline) UnifyCore.instance.nms?.sendTabHeaderFooter(player, "", "")
                })
            }
            return
        }

        val rendered = TabInfo(
            replacePlaceholders(provided.header, player),
            replacePlaceholders(provided.footer, player)
        )

        val currentTab = playerTabs[player.uniqueId]
        if (currentTab != null && currentTab == rendered) {
            return
        }

        val nms = UnifyCore.instance.nms ?: return
        playerTabs[player.uniqueId] = rendered

        val queuedIn = generation
        Bukkit.getScheduler().runTask(UnifyCore.instance, Runnable {
            if (!player.isOnline || queuedIn != generation) return@Runnable
            nms.sendTabHeaderFooter(player, rendered.header, rendered.footer)
        })
    }

    private fun replacePlaceholders(text: String, player: Player): String {
        return text
            .replace("{player}", player.name)
            .replace("{online}", Bukkit.getOnlinePlayers().size.toString())
            .replace("{max_players}", Bukkit.getMaxPlayers().toString())
            .replace("{ping}", try { UnifyCore.instance.nms?.getPing(player)?.toString() ?: "?" } catch (_: Exception) { "?" })
    }

    fun isEnabled(): Boolean {
        return enabled
    }

    fun providerCount(): Int {
        return providers.size
    }

    fun sortProviderCount(): Int {
        return sortProviders.size
    }

    fun sortProviders(): List<TabSortProvider> {
        return sortProviders.sorted.toList()
    }
}
