package me.jordanfails.unify

import me.jordanfails.unify.module.UnifyModule
import me.jordanfails.unify.module.UnifyModules
import me.jordanfails.unify.plugin.UnifyPlugins
import me.jordanfails.unify.request.PlayerRequests
import me.jordanfails.unify.request.RequestCommand
import me.jordanfails.unify.preview.WorldPreviews
import me.jordanfails.unify.hologram.HologramViews
import me.jordanfails.unify.utils.Tasks
import me.jordanfails.unify.menu.anvil.AnvilListener
import me.jordanfails.unify.menu.listener.ButtonListeners
import me.jordanfails.unify.menu.menus.listener.SelectItemListeners
import me.jordanfails.unify.screen.listener.ScreenListeners
import me.jordanfails.unify.nametag.NametagHandler
import me.jordanfails.unify.nametag.NametagListener
import me.jordanfails.unify.nms.NMSHandler
import me.jordanfails.unify.nms.NMSHandlerFactory
import me.jordanfails.unify.menu.tasks.MenuAutoUpdater
import me.jordanfails.unify.scoreboard.ScoreboardHandler
import me.jordanfails.unify.scoreboard.ScoreboardListener
import me.jordanfails.unify.tab.TabHandler
import me.jordanfails.unify.tab.TabListener
import co.aikar.commands.PaperCommandManager
import me.jordanfails.unify.acf.ACFCommandController
import me.jordanfails.unify.bossbar.BossBarHandler
import me.jordanfails.unify.bossbar.BossBarListener
import me.jordanfails.unify.bossbar.BossBarManager
import me.jordanfails.unify.commands.LoreCommand
import me.jordanfails.unify.commands.PingCommand
import me.jordanfails.unify.commands.UnifyAdminCommand
import me.jordanfails.unify.hologram.HologramManager
import me.jordanfails.unify.hologram.command.HologramCommand
import me.jordanfails.unify.npc.NPCRegistry
import me.jordanfails.unify.npc.command.NPCCommand
import me.jordanfails.unify.visibility.VisibilityHandler
import me.jordanfails.unify.visibility.VisibilityListeners
import me.jordanfails.unify.visibility.VanishVisibilityAdapter
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import org.bukkit.plugin.java.JavaPlugin
import java.util.logging.Level

@UnifyInternal
class UnifyCore : JavaPlugin() {

    companion object {
        lateinit var instance: UnifyCore
            private set
        lateinit var commandManager: PaperCommandManager
            private set

        internal val isReady: Boolean get() = ::instance.isInitialized && instance.isEnabled && instance::modules.isInitialized

        internal fun moduleEnabled(module: UnifyModule): Boolean = isReady && instance.modules.isEnabled(module)
    }

    lateinit var modules: UnifyModules
        private set

    var nms: NMSHandler? = null
        private set
    var audience: BukkitAudiences? = null
        private set
    override fun onEnable() {
        instance = this
        audience = BukkitAudiences.create(this)
        saveDefaultConfig()
        modules = UnifyModules(config)
        nms = NMSHandlerFactory.getHandler()
        if (nms == null) {
            logger.warning(
                "This Unify jar has no NMS support for ${server.bukkitVersion}; packet features (nametags, tab, NPCs) won't work. " +
                    "Use Unify-${description.version}-legacy.jar on 1.8-1.16 and Unify-${description.version}-modern.jar on Paper 1.20.6+."
            )
        }
        commandManager = PaperCommandManager(this)
        ACFCommandController.registerAll()
        modules.ifEnabled(UnifyModule.VISIBILITY) { setupVisibility() }
        modules.ifEnabled(UnifyModule.NAMETAGS) { setupNametags() }
        modules.ifEnabled(UnifyModule.SCOREBOARD) { setupScoreboards() }
        modules.ifEnabled(UnifyModule.TAB) { setupTab() }
        modules.ifEnabled(UnifyModule.BOSSBAR) { setupBossBars() }
        modules.ifEnabled(UnifyModule.MENUS) { setupMenu() }
        modules.ifEnabled(UnifyModule.HOLOGRAMS) {
            setupHolograms()
            HologramViews.enable(this)
        }
        modules.ifEnabled(UnifyModule.NPCS) { setupNPCs() }
        modules.ifEnabled(UnifyModule.REQUESTS) { PlayerRequests.enable(this) }
        modules.ifEnabled(UnifyModule.WORLD_PREVIEWS) { WorldPreviews.enable(this) }
        setupCommands()
        UnifyPlugins.enable(this)
    }

    override fun onDisable() {
        if (!::modules.isInitialized) return
        modules.ifEnabled(UnifyModule.NAMETAGS) { NametagHandler.disable() }
        modules.ifEnabled(UnifyModule.SCOREBOARD) { ScoreboardHandler.disable() }
        modules.ifEnabled(UnifyModule.TAB) { TabHandler.disable() }
        modules.ifEnabled(UnifyModule.REQUESTS) { PlayerRequests.disable() }
        modules.ifEnabled(UnifyModule.WORLD_PREVIEWS) { WorldPreviews.disable() }
        modules.ifEnabled(UnifyModule.HOLOGRAMS) { HologramViews.disable() }
        me.jordanfails.unify.menu.Menu.currentlyOpenedMenus.keys.toList().forEach { uuid ->
            try {
                server.getPlayer(uuid)?.closeInventory()
            } catch (error: Exception) {
                logger.log(Level.SEVERE, "Could not close menu for $uuid during shutdown", error)
            }
        }
        me.jordanfails.unify.menu.history.MenuHistory.clearAll()
        modules.ifEnabled(UnifyModule.BOSSBAR) {
            BossBarHandler.clearAll()
            BossBarManager.disable()
        }
        modules.ifEnabled(UnifyModule.NPCS) { NPCRegistry.disable() }
        modules.ifEnabled(UnifyModule.HOLOGRAMS) { HologramManager.disable() }
        audience?.close()
        audience = null
    }

    private fun setupVisibility() {
        VisibilityHandler.registerAdapter(VanishVisibilityAdapter)
        listOf(
            VisibilityListeners,
        ).forEach { listener -> this.server.pluginManager.registerEvents(listener, this) }
    }

    private fun setupNametags() {
        NametagHandler.initialLoad()
        server.pluginManager.registerEvents(NametagListener, this)
    }

    private fun setupMenu() {
        Tasks.runTimer(this, 2L) {
            MenuAutoUpdater.run()
        }
        listOf(
            SelectItemListeners,
            ButtonListeners,
            AnvilListener,
            ScreenListeners,
        ).forEach { listener -> this.server.pluginManager.registerEvents(listener, this) }
    }

    private fun setupCommands() {
        listOf(
            PingCommand(),
            UnifyAdminCommand(),
        ).forEach { commandManager.registerCommand(it) }
        modules.ifEnabled(UnifyModule.HOLOGRAMS) { commandManager.registerCommand(HologramCommand()) }
        modules.ifEnabled(UnifyModule.NPCS) { commandManager.registerCommand(NPCCommand()) }
        modules.ifEnabled(UnifyModule.REQUESTS) { commandManager.registerCommand(RequestCommand()) }
        modules.ifEnabled(UnifyModule.MENUS) { commandManager.registerCommand(LoreCommand()) }
    }

    private fun setupHolograms() {
        HologramManager.enable(this)
    }

    private fun setupNPCs() {
        NPCRegistry.enable(this)
    }

    private fun setupScoreboards() {
        ScoreboardHandler.initialLoad()
        server.pluginManager.registerEvents(ScoreboardListener, this)
    }

    private fun setupBossBars() {
        BossBarHandler.initialLoad()
        BossBarManager.enable(this)
        server.pluginManager.registerEvents(BossBarListener, this)
    }

    private fun setupTab() {
        TabHandler.initialLoad()
        server.pluginManager.registerEvents(TabListener, this)
    }
}
