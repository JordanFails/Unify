package me.jordanfails.unify.npc

import org.bukkit.entity.Player

fun interface NPCAction {
    fun execute(clicker: Player, npc: UnifyNPC)
}
