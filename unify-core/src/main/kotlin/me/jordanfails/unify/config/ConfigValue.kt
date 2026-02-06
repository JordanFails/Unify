package me.jordanfails.unify.config

import kotlin.reflect.KProperty

class ConfigValue<T>(
    private val config: TypeSafeConfig,
    private val path: String,
    private val defaultValue: T
) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return config.get(path, defaultValue)
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        config.set(path, value)
    }
}
