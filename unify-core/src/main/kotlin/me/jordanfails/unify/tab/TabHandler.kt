package me.jordanfails.unify.tab

import me.jordanfails.unify.UnifyCore
import org.bukkit.Bukkit
import org.bukkit.entity.Player

object TabHandler {

    private var enabled: Boolean = false
    var updateInterval: Int = 40

    private var headerLines = listOf<String>()
    private var footerLines = listOf<String>()

    fun initialLoad() {
        val config = UnifyCore.instance.config
        enabled = config.getBoolean("tab.enabled", true)
        if (!enabled) {
            UnifyCore.instance.logger.info("Custom tab header/footer is disabled by config")
            return
        }

        updateInterval = config.getInt("tab.update-interval-ticks", updateInterval).coerceAtLeast(1)
        headerLines = config.getStringList("tab.header")
        footerLines = config.getStringList("tab.footer")

        Bukkit.getScheduler().runTaskTimerAsynchronously(UnifyCore.instance, Runnable {
            for (player in Bukkit.getOnlinePlayers()) {
                sendTab(player)
            }
        }, 20L, updateInterval.toLong())
    }

    fun sendTab(player: Player) {
        if (!enabled) return
        val nms = UnifyCore.instance.nms ?: return

        val header = replacePlaceholders(headerLines.joinToString("\n"), player)
        val footer = replacePlaceholders(footerLines.joinToString("\n"), player)

        Bukkit.getScheduler().runTask(UnifyCore.instance, Runnable {
            if (!player.isOnline) return@Runnable
            nms.sendTabHeaderFooter(player, header, footer)
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
