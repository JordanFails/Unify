package me.jordanfails.unify

import me.jordanfails.unify.utils.Tasks
import me.jordanfails.unify.menu.listener.ButtonListeners
import me.jordanfails.unify.menu.menus.listener.SelectItemListeners
import me.jordanfails.unify.nametag.NametagHandler
import me.jordanfails.unify.nametag.NametagListener
import me.jordanfails.unify.nms.NMSHandler
import me.jordanfails.unify.nms.NMSHandlerFactory
import me.jordanfails.unify.menu.tasks.MenuAutoUpdater
import me.jordanfails.unify.visibility.VisibilityListeners
import org.bukkit.plugin.java.JavaPlugin

class UnifyCore : JavaPlugin() {

    companion object {
        lateinit var instance: UnifyCore
    }

    var nms: NMSHandler? = null

    override fun onEnable() {
        instance = this
        saveDefaultConfig()
        nms = NMSHandlerFactory.getHandler()
        setupVisibility()
        setupNametags()
        setupMenu()
    }

    override fun onDisable() {
    }

    private fun setupVisibility() {
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
}
