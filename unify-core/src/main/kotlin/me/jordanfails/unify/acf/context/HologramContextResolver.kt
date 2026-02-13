package me.jordanfails.unify.acf.context

import co.aikar.commands.BukkitCommandExecutionContext
import co.aikar.commands.contexts.ContextResolver
import me.jordanfails.unify.hologram.HologramManager
import me.jordanfails.unify.hologram.UnifyHologram

class HologramContextResolver : ContextResolver<UnifyHologram, BukkitCommandExecutionContext> {
    override fun getContext(c: BukkitCommandExecutionContext?): UnifyHologram? {
        val firstArg = c?.popFirstArg() ?: return null

        return HologramManager.get(firstArg)
    }

}