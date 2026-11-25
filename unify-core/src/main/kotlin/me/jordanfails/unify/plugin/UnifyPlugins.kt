package me.jordanfails.unify.plugin

import co.aikar.commands.BaseCommand
import me.jordanfails.unify.Beta
import co.aikar.commands.PaperCommandManager
import me.jordanfails.unify.acf.ACFCommandController
import me.jordanfails.unify.bossbar.BossBarHandler
import me.jordanfails.unify.bossbar.BossBarManager
import me.jordanfails.unify.menu.Menu
import me.jordanfails.unify.nametag.NametagHandler
import me.jordanfails.unify.npc.NPCRegistry
import me.jordanfails.unify.scoreboard.ScoreboardHandler
import me.jordanfails.unify.tab.TabHandler
import me.jordanfails.unify.utils.command.CommonCommandContext
import me.jordanfails.unify.visibility.VisibilityHandler
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.event.server.PluginEnableEvent
import org.bukkit.plugin.Plugin
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.logging.Level

/** Tracks plugins marked with [UnifyPlugin] and wires up their annotated classes under their own name. */
@Beta
object UnifyPlugins : Listener {

    class Hooked internal constructor(val plugin: Plugin, internal val classes: ScannedClasses) {
        internal val configs = classes.configs.mapNotNull { cls ->
            plugin.guarded("bind config ${cls.name}") { BoundConfig(plugin, cls) }
        }
        internal var commandManager: PaperCommandManager? = null
        var commands = 0; private set
        var listeners = 0; private set
        var tasks = 0; private set
        val configCount get() = configs.size

        // Commands, listeners and repeating functions on the same class share one instance.
        private val instances = mutableMapOf<Class<*>, Any?>()

        internal fun start() {
            // The plugin gets its own manager so its commands are owned by it, not by Unify. If setting it
            // up fails, the plugin's listeners and tasks below still register.
            val manager = if (classes.commands.isEmpty()) null
                else plugin.guarded("set up commands") { PaperCommandManager(plugin).also(ACFCommandController::setup) }
            if (manager != null) {
                commandManager = manager
                // ACF checks parameter types at registration, so resolvers have to go in before commands.
                classes.contexts.forEach { cls -> register(cls) { (it as CommonCommandContext<*>).register(manager) } }
                classes.commands.forEach { cls ->
                    register(cls) { manager.registerCommand(it as BaseCommand); commands++ }
                }
            }
            classes.listeners.forEach { cls ->
                register(cls) { Bukkit.getPluginManager().registerEvents(it as Listener, plugin); listeners++ }
            }
            classes.repeating.forEach { method ->
                plugin.guarded("schedule ${method.declaringClass.name}.${method.name}") { if (schedule(method)) tasks++ }
            }
            plugin.logger.info("[Unify] Hooked: $commands commands, $listeners listeners, $tasks tasks, ${configs.size} configs")
        }

        // Listeners and tasks are cleaned up by Bukkit when the plugin disables; commands aren't.
        internal fun stop() {
            commandManager?.unregisterCommands()
        }

        private fun register(cls: Class<*>, action: (Any) -> Unit) {
            plugin.guarded("register ${cls.name}") {
                val instance = instanceFor(cls)
                if (instance == null) {
                    plugin.logger.warning("[Unify] Skipped ${cls.name}: needs to be an object or have a no-arg constructor")
                } else {
                    action(instance)
                }
            }
        }

        private fun schedule(method: Method): Boolean {
            val where = "${method.declaringClass.name}.${method.name}"
            val repeating = method.getAnnotation(Repeating::class.java)
            if (repeating.ticks < 1) {
                plugin.logger.warning("[Unify] Skipped $where: @Repeating(ticks = ${repeating.ticks}) must be at least 1")
                return false
            }
            val target = if (Modifier.isStatic(method.modifiers)) null else instanceFor(method.declaringClass)
            if (target == null && !Modifier.isStatic(method.modifiers)) {
                plugin.logger.warning("[Unify] Skipped $where: its class needs to be an object or have a no-arg constructor")
                return false
            }
            method.isAccessible = true
            val task = Runnable {
                try {
                    method.invoke(target)
                } catch (error: InvocationTargetException) {
                    plugin.logger.log(Level.SEVERE, "[Unify] Repeating task $where failed", error.cause)
                }
            }
            val scheduler = Bukkit.getScheduler()
            if (repeating.async) scheduler.runTaskTimerAsynchronously(plugin, task, repeating.ticks, repeating.ticks)
            else scheduler.runTaskTimer(plugin, task, repeating.ticks, repeating.ticks)
            return true
        }

        private fun instanceFor(cls: Class<*>): Any? = instances.getOrPut(cls) { PluginScanner.instanceOf(cls) }
    }

    private val hooked = linkedMapOf<String, Hooked>()
    private var core: Plugin? = null

    val all: Collection<Hooked> get() = hooked.values.toList()

    fun get(name: String): Hooked? = hooked.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    internal fun enable(core: Plugin) {
        this.core = core
        val plugins = Bukkit.getPluginManager().plugins
        DependentChecks.run(core, plugins)
        // Dependents are loaded but not enabled yet, so their configs are ready before their onEnable runs.
        plugins.filter(::usesUnify).forEach(::prepare)
        Bukkit.getPluginManager().registerEvents(this, core)
    }

    /**
     * Re-reads every [ConfigFile] of [plugin]; call it from your own reload command. Plugins loaded
     * after server startup (e.g. by a plugin manager) can call it at the top of onEnable so their
     * configs are bound before use.
     */
    fun reloadConfigs(plugin: Plugin) {
        val entry = hooked[plugin.name]?.takeIf { it.plugin === plugin }
        if (entry == null) prepare(plugin)
        else entry.configs.forEach { config -> plugin.guarded("reload config") { config.load() } }
    }

    // Fired after the plugin's onEnable, so commands and listeners can rely on its state.
    @EventHandler
    fun onPluginEnable(event: PluginEnableEvent) {
        val plugin = event.plugin
        if (!usesUnify(plugin)) return
        val entry = hooked[plugin.name]?.takeIf { it.plugin === plugin } ?: prepare(plugin) ?: return
        entry.start()
    }

    // Runs for every plugin, not just @UnifyPlugin ones: anything that registered into Unify gets cleaned up.
    @EventHandler
    fun onPluginDisable(event: PluginDisableEvent) {
        val plugin = event.plugin
        hooked.remove(plugin.name)?.let { plugin.guarded("unregister commands") { it.stop() } }
        if (plugin === core) return
        val cleanups: List<(Plugin) -> Unit> = listOf(
            Menu::forgetPlugin,
            NametagHandler::forgetPlugin,
            TabHandler::forgetPlugin,
            ScoreboardHandler::forgetPlugin,
            BossBarHandler::forgetPlugin,
            BossBarManager::forgetPlugin,
            VisibilityHandler::forgetPlugin,
            NPCRegistry::forgetPlugin,
        )
        for (cleanup in cleanups) plugin.guarded("clean up after ${plugin.name}") { cleanup(plugin) }
    }

    private fun prepare(plugin: Plugin): Hooked? = plugin.guarded("scan ${plugin.name}") {
        val declaration = plugin.javaClass.getAnnotation(UnifyPlugin::class.java)
        core?.let { DependentChecks.checkRequirements(it, plugin, declaration) }
        Hooked(plugin, PluginScanner.scan(plugin, declaration.autoCommands)).also { entry ->
            entry.configs.forEach { config -> plugin.guarded("load config") { config.load() } }
            hooked[plugin.name] = entry
        }
    }

    private fun usesUnify(plugin: Plugin) = plugin.javaClass.isAnnotationPresent(UnifyPlugin::class.java)
}

/**
 * Runs one step of hooking a dependent. A failure (including a class that can't link or whose
 * initializer throws) is logged against that plugin instead of taking Unify or other plugins down.
 */
internal fun <T> Plugin.guarded(what: String, action: () -> T): T? = try {
    action()
} catch (error: Exception) {
    logger.log(Level.SEVERE, "[Unify] Could not $what", error)
    null
} catch (error: LinkageError) {
    logger.log(Level.SEVERE, "[Unify] Could not $what", error)
    null
}
