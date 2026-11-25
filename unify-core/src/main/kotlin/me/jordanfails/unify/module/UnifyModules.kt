package me.jordanfails.unify.module

import org.bukkit.configuration.ConfigurationSection

/** Module choices are fixed at startup; changing them requires a server restart. */
class UnifyModules(config: ConfigurationSection) {
    private val enabled = UnifyModule.entries.filterTo(linkedSetOf()) {
        config.getBoolean("modules.${it.key}", true)
    }

    fun isEnabled(module: UnifyModule): Boolean = module in enabled

    internal fun ifEnabled(module: UnifyModule, action: () -> Unit) {
        if (isEnabled(module)) action()
    }
}
