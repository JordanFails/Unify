package me.jordanfails.unify.utils

import me.jordanfails.unify.UnifyCore
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect

@Suppress("DEPRECATION")
object EffectUtil {

    @JvmStatic
    fun apply(player: Player, effect: PotionEffect) {
        if (canOverride(player, effect)) {
            if (player.hasPotionEffect(effect.type)) {
                val temp = player.activePotionEffects.find { it.type.id == effect.type.id }

                if (temp != null) {
                    val before = PotionEffect(
                        temp.type,
                        temp.duration,
                        temp.amplifier,
                        temp.isAmbient,
                        temp.hasParticles(),
                        temp.hasIcon()
                    )

                    Tasks.runLater(UnifyCore.instance, (effect.duration - 5).toLong(), {
                        if (!player.isOnline) return@runLater

                        if (player.hasPotionEffect(before.type)) {
                            val effect = player.activePotionEffects.find { it.type.id == effect.type.id }

                            if (effect == null) return@runLater

                            if (effect.amplifier == before.amplifier) {
                                if (before.duration > effect.duration) {
                                    player.addPotionEffect(before, true)
                                }
                            } else {
                                player.addPotionEffect(before, true)
                            }
                        } else {
                            player.addPotionEffect(before, true)
                        }

                        return@runLater

                    })
                }
            }

            player.addPotionEffect(effect, true)
        }

    }

    fun applyInfinite(player: Player, effect: PotionEffect) {
        val infiniteEffect = PotionEffect(
            effect.type,
            Int.MAX_VALUE,
            effect.amplifier,
            effect.isAmbient,
            effect.hasParticles(),
            effect.hasIcon()
        )
        player.addPotionEffect(infiniteEffect, true)
    }

    fun clear(player: Player) {
        player.activePotionEffects.clear()
    }

    private fun canOverride(player: Player, effect: PotionEffect): Boolean {
        if (player.hasPotionEffect(effect.type)) {
            val before = player.activePotionEffects.stream()
                .filter { potionEffect: PotionEffect? -> potionEffect!!.type.id == effect.type.id }
                .findFirst().orElse(null)
            if (before == null) return true

            return before.amplifier < effect.amplifier || (before.amplifier == effect.amplifier && before.duration < effect.duration)
        }

        return true
    }


}