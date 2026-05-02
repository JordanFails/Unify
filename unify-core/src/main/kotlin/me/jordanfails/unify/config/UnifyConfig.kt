package me.jordanfails.unify.config

import me.jordanfails.unify.UnifyCore

object UnifyConfig {
    private val config = Config(UnifyCore.instance, "config")

    object Scoreboard {
        val enabled= ConfigValue("scoreboard.enabled", true, config)
        val title         = ConfigValue("scoreboard.title", "&d&lHUB", config)
        val updateInterval = ConfigValue("scoreboard.update-interval-ticks", 20, config)
        val lines         = ConfigValue("scoreboard.lines", emptyList<String>(), config)
    }

    object Tab {
        val enabled        = ConfigValue("tab.enabled", true, config)
        val updateInterval = ConfigValue("tab.update-interval-ticks", 40, config)
        val header         = ConfigValue("tab.header", emptyList<String>(), config)
        val footer         = ConfigValue("tab.footer", emptyList<String>(), config)
    }

    fun reload() = config.reload()
}