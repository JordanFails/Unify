package me.jordanfails.unify.utils

import me.jordanfails.unify.UnifyCore
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

fun Player.sendMessage(text: Component) {
    UnifyCore.instance.audience!!.player(this).sendMessage(text)
}