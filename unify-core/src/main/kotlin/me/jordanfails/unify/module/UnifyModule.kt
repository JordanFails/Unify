package me.jordanfails.unify.module

import me.jordanfails.unify.UnifyCore
import org.bukkit.Bukkit
import me.jordanfails.unify.plugin.owningPlugin

enum class UnifyModule(val key: String) {
    VISIBILITY("visibility"),
    NAMETAGS("nametags"),
    SCOREBOARD("scoreboard"),
    TAB("tab"),
    BOSSBAR("bossbar"),
    MENUS("menus"),
    HOLOGRAMS("holograms"),
    NPCS("npcs"),
    REQUESTS("requests"),
    WORLD_PREVIEWS("world-previews");

    /** False until Unify has enabled, so calls made too early fail with a clear message instead of a crash. */
    val isEnabled: Boolean get() = UnifyCore.moduleEnabled(this)

    /** Providers registered while the module is off are ignored with a warning rather than crashing the caller's enable. */
    internal fun allowsRegistration(registered: Any): Boolean {
        val owner = owningPlugin(registered)
        val problem = when {
            !isEnabled -> "the '$key' module is disabled (modules.$key in config.yml)"
            // A plugin registering from its onDisable would outlive the cleanup that already ran for it.
            owner != null && !owner.isEnabled -> "${owner.name} is disabled"
            else -> return true
        }
        Bukkit.getLogger().warning("[Unify] ${owner?.name ?: "A plugin"} registered ${registered.javaClass.name}, but $problem. Ignoring it.")
        return false
    }

    /** Fails loudly at the call site instead of half-working (e.g. a menu whose clicks nothing handles). */
    fun require() {
        check(UnifyCore.isReady) { "Unify isn't enabled yet. Depend on unify-core in plugin.yml and use '$key' from onEnable or later." }
        check(isEnabled) {
            "Unify's '$key' module is disabled. Set modules.$key: true in plugins/unify-core/config.yml and restart."
        }
    }
}
