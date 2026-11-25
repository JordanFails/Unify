package me.jordanfails.unify.tab

import me.jordanfails.unify.UnifyCore
import org.bukkit.entity.Player

/**
 * Supplies display data for each player on every refresh; the highest-weight answer wins.
 *
 * Threading: Unify calls this on the main thread, so it may use the Bukkit API. If your plugin calls a
 * handler's reloadPlayer from another thread, it runs on that thread instead.
 * A provider that throws is skipped and logged once. Providers are removed when their plugin disables.
 */
abstract class TabProvider(val name: String, val weight: Int) {

    abstract fun fetchTab(player: Player): TabInfo?

    class DefaultTabProvider : TabProvider("Default Provider", 0) {
        override fun fetchTab(player: Player): TabInfo {
            val config = UnifyCore.instance.config
            val header = config.getStringList("tab.header").joinToString("\n")
            val footer = config.getStringList("tab.footer").joinToString("\n")
            return createTab(header, footer)
        }
    }

    companion object {
        @JvmStatic
        fun createTab(header: String, footer: String): TabInfo {
            return TabInfo(header, footer)
        }
    }
}
