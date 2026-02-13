package me.jordanfails.unify.acf

import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.acf.context.HologramContextResolver
import me.jordanfails.unify.hologram.HologramManager
import me.jordanfails.unify.hologram.UnifyHologram

object ACFCommandController {
    val acf = UnifyCore.commandManager


    fun registerAll() {

        acf.commandContexts.registerContext(UnifyHologram::class.java, HologramContextResolver())

        acf.commandCompletions.registerCompletion("holograms") {
            return@registerCompletion HologramManager.getIds().toList()
        }

        acf.enableUnstableAPI("help")
    }
}