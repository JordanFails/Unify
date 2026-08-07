package me.jordanfails.unify.acf

import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.acf.context.HologramContextResolver
import me.jordanfails.unify.acf.context.NPCContextResolver
import me.jordanfails.unify.hologram.HologramManager
import me.jordanfails.unify.hologram.UnifyHologram
import me.jordanfails.unify.nms.NMSHandlerFactory
import me.jordanfails.unify.npc.NPCRegistry
import org.bukkit.entity.EntityType

object ACFCommandController {
    val acf = UnifyCore.commandManager
    val commandHandler: CommandHandler = CommandHandler(acf)

    fun registerAll() {
        commandHandler.registerResolvers(
            HologramContextResolver(),
            NPCContextResolver()
        )
        registerCompletions()
        acf.enableUnstableAPI("help")
    }

    /**
     * Entity types that can back an NPC on this server version, for `/npc create` and `/npc type`.
     *
     * Filtered through the NMS handler rather than listing [EntityType.values] wholesale, so the
     * completions never offer a type that would be rejected — the set differs a lot between 1.8
     * and current.
     */
    private fun registerCompletions() {
        acf.commandCompletions.registerCompletion("entityTypes") {
            val handler = NMSHandlerFactory.getHandler()
            EntityType.values()
                .filter { it.isSpawnable && (handler == null || handler.supportsNpcEntityType(it)) }
                .map { it.name.lowercase() }
                .sorted()
        }
    }
}
