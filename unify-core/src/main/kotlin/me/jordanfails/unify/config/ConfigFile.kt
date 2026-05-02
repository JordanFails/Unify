package me.jordanfails.unify.config

import me.jordanfails.unify.utils.CC
import org.bukkit.entity.Player
import kotlin.reflect.KProperty

// Delegate-based access: val enabled by ConfigValue("path", true, config)
class ConfigValue<T : Any>(
    private val path: String,
    private val default: T,
    private val config: Config
) {
    @Suppress("UNCHECKED_CAST")
    fun get(): T = when (default) {
        is String  -> (config.getString(path).takeIf { it.isNotEmpty() } ?: default) as T
        is Int     -> (config.getOrDefault(path) { config.getInt(path) } ?: default) as T
        is Boolean -> (config.getOrDefault(path) { config.getBoolean(path) } ?: default) as T
        is Double  -> (config.getOrDefault(path) { config.getDouble(path) } ?: default) as T
        is List<*> -> (config.getStringList(path).takeIf { it.isNotEmpty() } ?: default) as T
        else       -> default
    }

    fun set(value: T) {
        config[path] = value
        config.save()
    }

    fun sendMessage(player: Player) {
        player.sendMessage(CC.translate(get().toString()))
    }

    // Property delegate support
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = get()
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) = set(value)
}

// Extension to reduce boilerplate in get()
private fun <T> Config.getOrDefault(path: String, fetch: () -> T): T? =
    if (contains(path)) fetch() else null