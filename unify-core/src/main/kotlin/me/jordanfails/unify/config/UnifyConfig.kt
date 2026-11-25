package me.jordanfails.unify.config

import me.jordanfails.unify.UnifyCore

object UnifyConfig {
    private val config = Config(UnifyCore.instance, "config")

    object Scoreboard {
        val updateInterval = ConfigValue("scoreboard.update-interval-ticks", 20, config)
    }

    object Tab {
        val enabled        = ConfigValue("tab.enabled", false, config)
        val updateInterval = ConfigValue("tab.update-interval-ticks", 40, config)
        val header         = ConfigValue("tab.header", emptyList<String>(), config)
        val footer         = ConfigValue("tab.footer", emptyList<String>(), config)
    }

    object BossBar {
        val enabled        = ConfigValue("bossbar.enabled", false, config)
        val updateInterval = ConfigValue("bossbar.update-interval-ticks", 20, config)
        val tickInterval   = ConfigValue("bossbar.tick-interval-ticks", 4, config)
        val indent         = ConfigValue("bossbar.indent", "  ", config)
        val lines          = ConfigValue("bossbar.lines", emptyList<String>(), config)
    }

    fun reload() = config.reload()
}