package me.jordanfails.unify.utils

import kotlin.random.Random

object RandomUtil {

    @JvmStatic
    fun of(chance: Double): Boolean {
        val clampedChance = chance.coerceIn(0.0, 100.0)
        return Random.nextDouble(0.0, 100.0) < clampedChance
    }

    private val RANDOM = Random.Default

    @JvmStatic
    @Throws(IllegalArgumentException::class)
    fun getRandInt(min: Int, max: Int): Int {
        require(min <= max) { "min must be less than or equal to max" }
        return if (min == max) min else RANDOM.nextInt(min, max + 1)
    }

    @JvmStatic
    @Throws(IllegalArgumentException::class)
    fun getRandDouble(min: Double, max: Double): Double {
        require(min <= max) { "min must be less than or equal to max" }
        return if (min == max) min else RANDOM.nextDouble(min, max)
    }

    @JvmStatic
    fun getChance(chance: Double): Boolean {
        return chance >= 100.0 || chance >= getRandDouble(0.0, 100.0)
    }

}