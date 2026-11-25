package me.jordanfails.unify.npc

import org.bukkit.Bukkit
import org.bukkit.entity.Entity
import java.util.UUID

// Bukkit.getEntity(UUID) only exists from 1.11; older servers need a scan of loaded entities.
private val hasGetEntity = runCatching { Bukkit::class.java.getMethod("getEntity", UUID::class.java) }.isSuccess

internal fun findEntity(uuid: UUID): Entity? =
    if (hasGetEntity) Bukkit.getEntity(uuid)
    else Bukkit.getWorlds().asSequence().flatMap { it.entities.asSequence() }.firstOrNull { it.uniqueId == uuid }
