package me.jordanfails.unify

import me.jordanfails.unify.nms.NMSHandler
import me.jordanfails.unify.nms.NMSHandlerFactory
import org.bukkit.plugin.java.JavaPlugin

class UnifyCore : JavaPlugin() {

    companion object {
        lateinit var instance: UnifyCore
    }

    var nms: NMSHandler? = null

    override fun onEnable() {
        instance = this
        nms = NMSHandlerFactory.getHandler()
        this.server.logger.info("This server is running ${nms?.getServerVersion()}")
    }

    override fun onDisable() {
        // Plugin shutdown logic
    }
}
