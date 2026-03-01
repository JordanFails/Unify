package me.jordanfails.unify.utils.prompt

import org.bukkit.ChatColor

object PromptUtil {

    val FORMATTED_NAME_PROMPT = "${ChatColor.GREEN}Please input a name. ${ChatColor.GRAY}(Colors supported, limit of 48 characters)"

    val IDENTIFIER_PROMPT = "${ChatColor.GREEN}Please input a unique ID. ${ChatColor.GRAY}(Limit of 16 characters)"
    val IDENTIFIER_REGEX = "[a-zA-Z_\\-0-9]*".toRegex()

}