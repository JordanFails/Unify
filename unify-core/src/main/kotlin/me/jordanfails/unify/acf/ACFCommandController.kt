package me.jordanfails.unify.acf

import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.acf.context.HologramContextResolver
import me.jordanfails.unify.acf.context.NPCContextResolver
import me.jordanfails.unify.hologram.HologramManager
import me.jordanfails.unify.hologram.UnifyHologram
import me.jordanfails.unify.npc.NPCManager

object ACFCommandController {
    val acf = UnifyCore.commandManager
    val commandHandler: CommandHandler = CommandHandler(acf)

    fun registerAll() {
        commandHandler.registerResolvers(
            HologramContextResolver(),
            NPCContextResolver()
        )
        acf.enableUnstableAPI("help")
    }
}
