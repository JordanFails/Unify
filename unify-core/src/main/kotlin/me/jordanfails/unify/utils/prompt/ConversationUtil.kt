package me.jordanfails.unify.utils.prompt

import me.jordanfails.unify.UnifyCore
import org.bukkit.conversations.ConversationFactory
import org.bukkit.conversations.Prompt
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

object ConversationUtil {

    @JvmStatic
    fun startConversation(player: Player, prompt: Prompt, plugin: JavaPlugin = UnifyCore.instance) {
        if (player.openInventory != null) {
            player.closeInventory()
        }

        val factory = ConversationFactory(plugin)
            .withModality(false)
            .withFirstPrompt(prompt)
            .withLocalEcho(false)
            .thatExcludesNonPlayersWithMessage("Go away evil console!")

        player.beginConversation(factory.buildConversation(player))
    }

}