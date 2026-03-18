package me.jordanfails.unify.acf.context

import co.aikar.commands.BukkitCommandCompletionContext
import co.aikar.commands.BukkitCommandExecutionContext
import co.aikar.commands.InvalidCommandArgument
import me.jordanfails.unify.hologram.HologramManager
import me.jordanfails.unify.hologram.UnifyHologram
import me.jordanfails.unify.utils.command.CommonCommandContext

class HologramContextResolver : CommonCommandContext<UnifyHologram>(
    "holograms",
    UnifyHologram::class.java
){

    override fun getContext(arg: BukkitCommandExecutionContext): UnifyHologram {
        val firstArg = arg.popFirstArg()

        return HologramManager.get(firstArg!!) ?: throw InvalidCommandArgument("No such hologram with the name \"${firstArg}\"!")
    }

    override fun getCompletions(context: BukkitCommandCompletionContext): Collection<String> {
        return HologramManager.getIds().toList()
    }

}