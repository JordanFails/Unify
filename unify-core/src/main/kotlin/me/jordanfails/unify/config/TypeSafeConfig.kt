package me.jordanfails.unify.config

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import java.io.File
import java.io.IOException
import java.util.LinkedHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.reflect.full.findAnnotation

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigPath(val value: String)

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigNote(val value: String)

abstract class TypeSafeConfig(
    private val plugin: Plugin,
    private val fileName: String = "config.yml"
) {
    private val configFile: File = File(plugin.dataFolder, fileName)
    private var config: FileConfiguration
    private val lock = ReentrantReadWriteLock()
    private val notesByPath = LinkedHashMap<String, String>()

    init {
        try {
            if (!configFile.exists()) {
                configFile.parentFile?.mkdirs()
                try {
                    plugin.saveResource(fileName, false)
                } catch (e: IllegalArgumentException) {
                    configFile.createNewFile()
                    plugin.logger.warning(
                        "No default $fileName found in resources, created empty file"
                    )
                }
            }
            config = YamlConfiguration.loadConfiguration(configFile)
        } catch (e: Exception) {
            plugin.logger.severe("Failed to initialize config file $fileName: ${e.message}")
            throw ConfigException("Failed to initialize config", e)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> get(path: String, default: T): T = lock.read {
        val raw = config.get(path) ?: return@read default
        try {
            return@read convertValue(path, raw, default)
        } catch (e: Exception) {
            plugin.logger.warning("Error reading config path '$path', using default: ${e.message}")
            return@read default
        }
    }

    fun <T> set(path: String, value: T) = lock.write {
        config.set(path, value)
    }

    fun save() = lock.write {
        try {
            config.save(configFile)
        } catch (e: IOException) {
            plugin.logger.severe("Failed to save config file $fileName: ${e.message}")
            throw ConfigException("Failed to save config", e)
        }

        writeNotesHeader()
    }

    fun reload() = lock.write {
        try {
            config = YamlConfiguration.loadConfiguration(configFile)
        } catch (e: Exception) {
            plugin.logger.severe("Failed to reload config file $fileName: ${e.message}")
            throw ConfigException("Failed to reload config", e)
        }
    }

    internal fun <T> bindProperty(property: KProperty<*>, explicitPath: String?, default: T): String {
        val annotationPath = property.findAnnotation<ConfigPath>()?.value
        val resolvedPath = explicitPath ?: annotationPath
            ?: throw ConfigException(
                "Missing config path for property '${property.name}'. Add @ConfigPath or use value(\"path\", default)."
            )
        val note = property.findAnnotation<ConfigNote>()?.value

        lock.write {
            if (!config.contains(resolvedPath)) {
                config.set(resolvedPath, default)
                try {
                    config.save(configFile)
                } catch (e: IOException) {
                    throw ConfigException("Failed to save config while binding '$resolvedPath'", e)
                }
            }

            if (note != null) {
                notesByPath[resolvedPath] = note
                writeNotesHeader()
            }
        }

        return resolvedPath
    }

    private fun writeNotesHeader() {
        if (notesByPath.isEmpty()) return
        if (!configFile.exists()) return

        val startMarker = "# --- Unify Config Notes (auto-generated) ---"
        val endMarker = "# --- End Unify Config Notes ---"

        try {
            val originalContent = configFile.readText()
            val originalLines = configFile.readLines()
            val bodyLines = mutableListOf<String>()

            var inManagedBlock = false
            for (line in originalLines) {
                val trimmed = line.trim()
                if (trimmed == startMarker) {
                    inManagedBlock = true
                    continue
                }
                if (trimmed == endMarker) {
                    inManagedBlock = false
                    continue
                }
                if (!inManagedBlock) bodyLines.add(line)
            }

            val noteLines = mutableListOf<String>()
            noteLines.add(startMarker)
            for ((path, note) in notesByPath) {
                noteLines.add("# $path: $note")
            }
            noteLines.add(endMarker)
            noteLines.add("")

            val newContent = (noteLines + bodyLines).joinToString("\n")
            if (newContent != originalContent) {
                configFile.writeText(newContent)
            }
        } catch (e: IOException) {
            throw ConfigException("Failed to write notes to config file", e)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> convertValue(path: String, raw: Any, default: T): T {
        return when (default) {
            is String -> raw.toString() as T
            is Int -> when (raw) {
                is Number -> raw.toInt()
                is String -> raw.toIntOrNull() ?: default
                else -> default
            } as T
            is Long -> when (raw) {
                is Number -> raw.toLong()
                is String -> raw.toLongOrNull() ?: default
                else -> default
            } as T
            is Double -> when (raw) {
                is Number -> raw.toDouble()
                is String -> raw.toDoubleOrNull() ?: default
                else -> default
            } as T
            is Float -> when (raw) {
                is Number -> raw.toFloat()
                is String -> raw.toFloatOrNull() ?: default
                else -> default
            } as T
            is Boolean -> when (raw) {
                is Boolean -> raw
                is String -> when {
                    raw.equals("true", ignoreCase = true) -> true
                    raw.equals("false", ignoreCase = true) -> false
                    else -> default
                }
                else -> default
            } as T
            is List<*> -> {
                val list = raw as? List<*> ?: return default
                if (default.firstOrNull() is String) {
                    return list.map { it?.toString().orEmpty() } as T
                }
                list as T
            }
            else -> {
                if (default == null) {
                    raw as T
                } else if (!default::class.java.isInstance(raw)) {
                    plugin.logger.warning("Type mismatch at '$path', using default '$default'")
                    default
                } else {
                    raw as T
                }
            }
        }
    }

    protected fun <T> value(default: T): ConfigValue<T> {
        return ConfigValue(this, null, default)
    }

    protected fun <T> value(path: String, default: T): ConfigValue<T> {
        return ConfigValue(this, path, default)
    }
}

class ConfigValue<T>(
    private val config: TypeSafeConfig,
    private val explicitPath: String?,
    private val default: T
) : ReadWriteProperty<Any?, T> {
    private var resolvedPath: String? = null

    operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): ConfigValue<T> {
        resolvedPath = config.bindProperty(property, explicitPath, default)
        return this
    }

    private fun path(property: KProperty<*>): String {
        return resolvedPath ?: config.bindProperty(property, explicitPath, default).also {
            resolvedPath = it
        }
    }

    override operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return config.get(path(property), default)
    }

    override operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        config.set(path(property), value)
    }
}

class ConfigException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)
