package me.jordanfails.unify.acf.context

import co.aikar.commands.BukkitCommandCompletionContext
import co.aikar.commands.BukkitCommandExecutionContext
import co.aikar.commands.contexts.ContextResolver
import me.jordanfails.unify.npc.NPCManager
import me.jordanfails.unify.npc.UnifyNPC
import me.jordanfails.unify.utils.command.CommonCommandContext
import java.util.stream.Collectors

class NPCContextResolver : CommonCommandContext<UnifyNPC>(
    "npcs",
    UnifyNPC::class.java
){
    override fun getContext(c: BukkitCommandExecutionContext?): UnifyNPC? {
        val firstArg = c?.popFirstArg() ?: return null
        return NPCManager.get(firstArg)
    }

    override fun getCompletions(context: BukkitCommandCompletionContext): Collection<String> {
        return NPCManager.getAll().values.stream().map { it.id }.collect(Collectors.toList())
    }
}