package me.jordanfails.unify.utils.command

import co.aikar.commands.BukkitCommandCompletionContext
import co.aikar.commands.BukkitCommandExecutionContext
import co.aikar.commands.InvalidCommandArgument

/**
 * Base class for command contexts in ACF (Aikar's Command Framework).
 * Provides a consistent generic wrapper for resolving custom objects
 * from command arguments and providing tab completions.
 */
abstract class CommonCommandContext<T>(
    val id: String,
    val clazz: Class<T>
) {

    /**
     * Resolves the argument into the expected type when the command is executed.
     *
     * @param arg the BukkitCommandExecutionContext populated with parsed arguments
     * @return the resolved value of type T
     * @throws InvalidCommandArgument when the argument cannot be resolved into T
     */
    @Throws(InvalidCommandArgument::class)
    abstract fun getContext(arg: BukkitCommandExecutionContext): T

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

    fun getType(): Class<T> = clazz
}