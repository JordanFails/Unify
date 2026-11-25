package me.jordanfails.unify.acf

import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.acf.context.HologramContextResolver
import me.jordanfails.unify.acf.context.NPCContextResolver
import me.jordanfails.unify.hologram.HologramManager
import me.jordanfails.unify.hologram.UnifyHologram
import me.jordanfails.unify.nms.NMSHandlerFactory
import me.jordanfails.unify.npc.NPCRegistry
import me.jordanfails.unify.plugin.UnifyPlugins
import co.aikar.commands.PaperCommandManager
import org.bukkit.entity.EntityType

object ACFCommandController {
    val acf = UnifyCore.commandManager
    val commandHandler: CommandHandler = CommandHandler(acf)

    fun registerAll() {
        setup(acf)
        acf.commandCompletions.registerCompletion("unifyPlugins") { UnifyPlugins.all.map { it.plugin.name } }
    }

    /** Adds Unify's argument types and completions to a command manager, including other plugins' own managers. */
    fun setup(manager: PaperCommandManager) {
        CommandHandler(manager).registerResolvers(
            HologramContextResolver(),
            NPCContextResolver()
        )
        registerCompletions(manager)
        manager.enableUnstableAPI("help")
    }

    /**
     * Entity types that can back an NPC on this server version, for `/npc create` and `/npc type`.
     *
     * Filtered through the NMS handler rather than listing [EntityType.values] wholesale, so the
     * completions never offer a type that would be rejected — the set differs a lot between 1.8
     * and current.
     */
    private fun registerCompletions(manager: PaperCommandManager) {
        manager.commandCompletions.registerCompletion("entityTypes") {
            val handler = NMSHandlerFactory.getHandler()
            EntityType.values()
                .filter { it.isSpawnable && (handler == null || handler.supportsNpcEntityType(it)) }
                .map { it.name.lowercase() }
                .sorted()
        }
    }
}
