package me.jordanfails.unify.acf

import co.aikar.commands.BaseCommand
import co.aikar.commands.BukkitCommandManager
import co.aikar.commands.PaperCommandManager
import me.jordanfails.unify.utils.command.CommonCommandContext

class CommandHandler(
    val commandManager: PaperCommandManager
) {

    fun registerResolvers(vararg contexts: CommonCommandContext<*>) {
        contexts.forEach { it.register(commandManager) }
    }

    fun registerCommands(vararg commands: BaseCommand) {
        commands.forEach { commandManager.registerCommand(it) }
    }
}