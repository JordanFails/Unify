package me.jordanfails.unify.utils.command

import co.aikar.commands.BukkitCommandCompletionContext
import co.aikar.commands.BukkitCommandExecutionContext
import co.aikar.commands.BukkitCommandManager
import co.aikar.commands.InvalidCommandArgument
import co.aikar.commands.contexts.ContextResolver

abstract class CommonCommandContext<T>(
    val id: String,
    val clazz: Class<T>
) : ContextResolver<T, BukkitCommandExecutionContext> {

    /**
     * Resolves the argument into the expected type when the command is executed.
     *
     * @param arg the BukkitCommandExecutionContext populated with parsed arguments
     * @return the resolved value of type T
     * @throws InvalidCommandArgument when the argument cannot be resolved into T
     */
    @Throws(InvalidCommandArgument::class)
    abstract override fun getContext(arg: BukkitCommandExecutionContext?): T?

    /**
     * Provides tab completions for this context type.
     *
     * @param context the BukkitCommandCompletionContext
     * @return collection of suggestions
     */
    @Throws(InvalidCommandArgument::class)
    open fun getCompletions(context: BukkitCommandCompletionContext): Collection<String> {
        return emptyList()
    }

    /**
     * Registers this context's resolver and tab completions with the given manager.
     */
    fun register(manager: BukkitCommandManager) {
        manager.commandContexts.registerContext(clazz, this)
        manager.commandCompletions.registerCompletion(id) { ctx ->
            getCompletions(ctx)
        }
    }

    fun getType(): Class<T> = clazz
}