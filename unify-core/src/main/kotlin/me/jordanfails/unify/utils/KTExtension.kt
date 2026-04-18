package me.jordanfails.unify.utils

import me.jordanfails.unify.UnifyCore
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

fun Player.sendMessage(text: Component) {
    UnifyCore.instance.audience!!.player(this).sendMessage(text)
}

fun String.toTitleCase(): String {
    if (this.isBlank()) return this
    return this
        .trim()
        // treat underscores, hyphens and multiple spaces as separators
        .split(Regex("[_\\-\\s]+"))
        .filter { it.isNotEmpty() }
        .joinToString(" ") { word ->
            val lower = word.lowercase()
            lower.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
}