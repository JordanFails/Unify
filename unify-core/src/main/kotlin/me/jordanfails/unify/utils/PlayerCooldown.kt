package me.jordanfails.unify.utils

import java.util.function.LongSupplier

class PlayerCooldown<T>(
    private val cooldown: Long,
    private val cooldownProvider: LongSupplier? = null
) {
    private val timer = mutableMapOf<T, Long>()

    /**
     * Returns the current cooldown duration.
     * If a provider is supplied, its value is used; otherwise the fixed cooldown is used.
     */
    private fun currentCooldown(): Long {
        return cooldownProvider?.asLong ?: cooldown
    }

    /**
     * Starts or resets the cooldown for the given key.
     */
    fun set(key: T) {
        timer[key] = System.currentTimeMillis()
    }

    /**
     * Removes the cooldown for the given key.
     */
    fun remove(key: T) {
        timer.remove(key)
    }

    /**
     * Clears all cooldowns.
     */
    fun clear() {
        timer.clear()
    }

    /**
     * Returns true if the cooldown is still active.
     */
    fun active(key: T): Boolean {
        val start = timer[key] ?: return false
        return System.currentTimeMillis() - start < currentCooldown()
    }

    /**
     * Returns the remaining cooldown in milliseconds.
     * Returns 0 if there is no active cooldown.
     */
    fun get(key: T): Long {
        val start = timer[key] ?: return 0L
        return (currentCooldown() - (System.currentTimeMillis() - start))
            .coerceAtLeast(0L)
    }

    /**
     * Returns the elapsed time since the cooldown started.
     * Returns 0 if the cooldown does not exist.
     */
    fun elapsed(key: T): Long {
        val start = timer[key] ?: return 0L
        return System.currentTimeMillis() - start
    }

    /**
     * Returns true if a cooldown entry exists, regardless of whether it has expired.
     */
    fun contains(key: T): Boolean {
        return timer.containsKey(key)
    }
}