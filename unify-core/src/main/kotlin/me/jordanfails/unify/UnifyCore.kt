package me.jordanfails.unify

import me.jordanfails.unify.utils.Tasks
import me.jordanfails.unify.menu.listener.ButtonListeners
import me.jordanfails.unify.menu.menus.listener.SelectItemListeners
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
import me.jordanfails.unify.commands.PingCommand
import me.jordanfails.unify.commands.UnifyAdminCommand
import me.jordanfails.unify.hologram.HologramManager
import me.jordanfails.unify.hologram.command.HologramCommand
import me.jordanfails.unify.npc.NPCManager
import me.jordanfails.unify.npc.command.NPCCommand
import me.jordanfails.unify.visibility.VisibilityHandler
import me.jordanfails.unify.visibility.VisibilityListeners
import me.jordanfails.unify.visibility.VanishVisibilityAdapter
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import org.bukkit.plugin.java.JavaPlugin

class UnifyCore : JavaPlugin() {

    companion object {
        lateinit var instance: UnifyCore
        lateinit var commandManager: PaperCommandManager
    }

    var nms: NMSHandler? = null
    var audience: BukkitAudiences? = null
    override fun onEnable() {
        instance = this
        audience = BukkitAudiences.create(this);
        saveDefaultConfig()
        nms = NMSHandlerFactory.getHandler()
        commandManager = PaperCommandManager(this)
        ACFCommandController.registerAll()
        setupVisibility()
        setupNametags()
        setupScoreboards()
        setupTab()
        setupMenu()
        setupHolograms()
        setupNPCs()
        setupCommands()
    }

    override fun onDisable() {
        NPCManager.disable()
        HologramManager.disable()
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

    fun setupMenu() {
        Tasks.runTimerAsync(this, 2L, {
            MenuAutoUpdater.run()
        })
        listOf(
            SelectItemListeners,
            ButtonListeners
        ).forEach { listener -> this.server.pluginManager.registerEvents(listener, this) }
    }

    private fun setupCommands() {
        listOf(
            HologramCommand(),
            NPCCommand(),
            PingCommand(),
            UnifyAdminCommand()
        ).forEach { commandManager.registerCommand(it) }
    }

    private fun setupHolograms() {
        HologramManager.enable(this)
    }

    private fun setupNPCs() {
        NPCManager.enable(this)
    }

    private fun setupScoreboards() {
        ScoreboardHandler.initialLoad()
        server.pluginManager.registerEvents(ScoreboardListener, this)
    }

    private fun setupTab() {
        TabHandler.initialLoad()
        server.pluginManager.registerEvents(TabListener, this)
    }
}
