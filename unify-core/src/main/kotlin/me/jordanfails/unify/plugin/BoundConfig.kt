package me.jordanfails.unify.plugin

import me.jordanfails.unify.Beta
import org.bukkit.configuration.InvalidConfigurationException
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import java.io.File
import java.io.IOException
import java.lang.reflect.Field
import java.lang.reflect.Modifier

/** Keeps a [ConfigFile] object's `var` properties in sync with its YAML file. */
@Beta
internal class BoundConfig(private val plugin: Plugin, cls: Class<*>) {
    private val file = File(plugin.dataFolder, cls.getAnnotation(ConfigFile::class.java).name)

    // Kotlin stores an object's properties as static fields; `val`s are final and can't be loaded.
    private val fields: List<Field>

    // Captured once so a key deleted from the file gets its original default back, not the last loaded value.
    private val defaults: Map<Field, Any?>

    init {
        PluginScanner.instanceOf(cls) // runs the object's initializer so defaults are set
        val properties = cls.declaredFields.filter {
            Modifier.isStatic(it.modifiers) && !it.isSynthetic && it.name != "INSTANCE" && '$' !in it.name
        }
        properties.filter { Modifier.isFinal(it.modifiers) }.forEach {
            plugin.logger.warning("[Unify] ${cls.simpleName}.${it.name} is a val; make it a var so it loads from ${file.name}")
        }
        fields = properties.filterNot { Modifier.isFinal(it.modifiers) }.onEach { it.isAccessible = true }
        defaults = fields.associateWith { it.get(null) }
    }

    /** Never writes over a file it couldn't parse, so an operator's typo can't wipe their settings. */
    fun load() {
        val yaml = YamlConfiguration()
        if (file.exists()) {
            try {
                yaml.load(file)
            } catch (error: InvalidConfigurationException) {
                plugin.logger.severe("[Unify] ${file.name} has invalid YAML, keeping current values: ${error.message}")
                return
            } catch (error: IOException) {
                plugin.logger.severe("[Unify] Could not read ${file.name}, keeping current values: ${error.message}")
                return
            }
        }

        var addedDefaults = !file.exists()
        for (field in fields) {
            val key = field.name.toKebabCase()
            val default = defaults[field]
            if (!yaml.contains(key)) {
                yaml.set(key, default)
                field.set(null, default)
                addedDefaults = true
                continue
            }
            val value = convert(yaml.get(key), field.type)
            if (value == null) {
                plugin.logger.warning("[Unify] '$key' in ${file.name} should be a ${field.type.simpleName}; keeping ${field.get(null)}")
                continue
            }
            field.set(null, value)
        }

        if (addedDefaults) {
            file.parentFile.mkdirs()
            yaml.save(file)
        }
    }

    // Null means the YAML value can't become this field's type; Bukkit's typed getters would silently give 0/false.
    private fun convert(raw: Any?, type: Class<*>): Any? = when (type) {
        // YAML hands back Integer, Long or BigInteger depending on size; anything that doesn't fit is rejected.
        Int::class.javaPrimitiveType, Int::class.javaObjectType -> raw as? Int
        Long::class.javaPrimitiveType, Long::class.javaObjectType -> (raw as? Int)?.toLong() ?: raw as? Long
        Double::class.javaPrimitiveType, Double::class.javaObjectType -> (raw as? Number)?.toDouble()
        Boolean::class.javaPrimitiveType, Boolean::class.javaObjectType -> raw as? Boolean
        String::class.java -> raw?.takeIf { it is String || it is Number || it is Boolean }?.toString()
        else -> raw?.takeIf { type.isInstance(it) }
    }

    private fun String.toKebabCase() = replace(Regex("([a-z0-9])([A-Z])"), "$1-$2").lowercase()
}
