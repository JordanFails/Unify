package me.jordanfails.unify.acf.context

import co.aikar.commands.BukkitCommandExecutionContext
import co.aikar.commands.contexts.ContextResolver
import me.jordanfails.unify.npc.NPCManager
import me.jordanfails.unify.npc.UnifyNPC

class NPCContextResolver : ContextResolver<UnifyNPC, BukkitCommandExecutionContext> {
    override fun getContext(c: BukkitCommandExecutionContext?): UnifyNPC? {
        val firstArg = c?.popFirstArg() ?: return null
        return NPCManager.get(firstArg)
    }
}