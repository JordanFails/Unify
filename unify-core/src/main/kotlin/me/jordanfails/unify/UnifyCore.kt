package me.jordanfails.unify

import org.bukkit.plugin.java.JavaPlugin

class UnifyCore : JavaPlugin() {

    companion object {
        lateinit var instance: UnifyCore
    }

    override fun onEnable() {
        instance = this
    }

    override fun onDisable() {
        // Plugin shutdown logic
    }
}
