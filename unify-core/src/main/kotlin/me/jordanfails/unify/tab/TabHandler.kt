package me.jordanfails.unify.tab

import com.google.common.primitives.Ints
import me.jordanfails.unify.UnifyCore
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object TabHandler {

    private val playerTabs = ConcurrentHashMap<UUID, TabInfo>()
    private var providers = ArrayList<TabProvider>()

    private var enabled: Boolean = false
    var updateInterval: Int = 40

    fun initialLoad() {
        val config = UnifyCore.instance.config
        enabled = config.getBoolean("tab.enabled", true)
        if (!enabled) {
            UnifyCore.instance.logger.info("Custom tab header/footer is disabled by config")
            return
        }

        updateInterval = config.getInt("tab.update-interval-ticks", updateInterval).coerceAtLeast(1)

        registerProvider(TabProvider.DefaultTabProvider())

        Bukkit.getScheduler().runTaskTimerAsynchronously(UnifyCore.instance, Runnable {
            for (player in Bukkit.getOnlinePlayers()) {
                reloadPlayer(player)
            }
        }, 20L, updateInterval.toLong())
    }

    fun registerProvider(newProvider: TabProvider) {
        providers.add(newProvider)
        providers.sortWith { a, b -> Ints.compare(b.weight, a.weight) }
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

        var provided: TabInfo? = null
        var providerIndex = 0

        while (provided == null && providerIndex < providers.size) {
            provided = providers[providerIndex++].fetchTab(player)
        }

        if (provided == null) {
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

        Bukkit.getScheduler().runTask(UnifyCore.instance, Runnable {
            if (!player.isOnline) return@Runnable
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
}
