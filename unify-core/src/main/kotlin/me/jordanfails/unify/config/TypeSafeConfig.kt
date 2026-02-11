package me.jordanfails.unify.config

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import java.io.File
import java.io.IOException
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties

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
            load()
        } catch (e: IOException) {
            plugin.logger.severe("Failed to initialize config file $fileName: ${e.message}")
            throw ConfigException("Failed to initialize config", e)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> get(path: String, default: T): T = lock.read {
        return try {
            when (default) {
                is String -> (config.getString(path) ?: default) as T
                is Int -> (config.getInt(path, default)) as T
                is Double -> (config.getDouble(path, default)) as T
                is Boolean -> (config.getBoolean(path, default)) as T
                is Long -> (config.getLong(path, default)) as T
                is Float -> (config.getDouble(path, default.toDouble()).toFloat()) as T
                is List<*> -> (config.getList(path) ?: default) as T
                else -> (config.get(path) ?: default) as T
            }
        } catch (e: Exception) {
            plugin.logger.warning(
                "Error reading config path '$path', using default: ${e.message}"
            )
            default
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
    }

    fun reload() = lock.write {
        try {
            config = YamlConfiguration.loadConfiguration(configFile)
            load()
        } catch (e: Exception) {
            plugin.logger.severe("Failed to reload config file $fileName: ${e.message}")
            throw ConfigException("Failed to reload config", e)
        }
    }

    private fun load() {
        val properties = this::class.memberProperties
        val commentsToWrite = mutableMapOf<String, String>()
        var hasChanges = false

        for (property in properties) {
            val configPath = property.findAnnotation<ConfigPath>() ?: continue
            val path = configPath.value

            property.findAnnotation<ConfigNote>()?.let {
                commentsToWrite[path] = it.value
            }

            if (!config.contains(path)) {
                @Suppress("UNCHECKED_CAST")
                val prop = property as? KProperty1<TypeSafeConfig, *>
                try {
                    val defaultValue = prop?.get(this)
                    if (defaultValue != null) {
                        config.set(path, defaultValue)
                        hasChanges = true
                    }
                } catch (e: Exception) {
                    plugin.logger.warning(
                        "Failed to get default value for property '${property.name}': ${e.message}"
                    )
                }
            }
        }

        if (hasChanges) {
            save()
        }

        if (commentsToWrite.isNotEmpty()) {
            try {
                writeCommentsToFile(commentsToWrite)
            } catch (e: Exception) {
                plugin.logger.warning(
                    "Failed to write comments to config: ${e.message}"
                )
            }
        }
    }

    private fun writeCommentsToFile(comments: Map<String, String>) {
        if (!configFile.exists()) return

        try {
            val originalContent = configFile.readText()
            val lines = originalContent.lines().toMutableList()
            val newLines = mutableListOf<String>()
            val processedPaths = mutableSetOf<String>()

            var i = 0
            while (i < lines.size) {
                val line = lines[i]
                val trimmed = line.trimStart()

                // Skip existing comments
                if (trimmed.startsWith("#")) {
                    newLines.add(line)
                    i++
                    continue
                }

                // Check if this line matches a config path
                var matchedPath: String? = null
                for ((path, comment) in comments) {
                    if (isMatchingConfigLine(line, path) && !processedPaths.contains(path)) {
                        matchedPath = path
                        break
                    }
                }

                if (matchedPath != null) {
                    val comment = comments[matchedPath]!!
                    val indent = line.takeWhile { it.isWhitespace() }

                    // Check if previous line is already this comment
                    val prevLine = newLines.lastOrNull()?.trimStart()
                    if (prevLine != "# $comment") {
                        newLines.add("$indent# $comment")
                    }

                    processedPaths.add(matchedPath)
                }

                newLines.add(line)
                i++
            }

            // Only write if content changed
            val newContent = newLines.joinToString("\n")
            if (newContent != originalContent) {
                configFile.writeText(newContent)
            }
        } catch (e: IOException) {
            throw ConfigException("Failed to write comments to config file", e)
        }
    }

    private fun isMatchingConfigLine(line: String, configPath: String): Boolean {
        val trimmed = line.trimStart()
        if (!trimmed.contains(':')) return false

        val key = trimmed.substringBefore(':').trim()
        val pathSegments = configPath.split('.')
        val lastSegment = pathSegments.last()

        // Match exact key or handle nested paths
        return key == lastSegment || key == configPath
    }

    protected fun <T> value(default: T): ConfigValue<T> {
        // Note: This method relies on being called from a property getter
        // The property must have @ConfigPath annotation
        throw UnsupportedOperationException(
            "Use value(path, default) instead or ensure property has @ConfigPath annotation"
        )
    }

    protected fun <T> value(path: String, default: T): ConfigValue<T> {
        return ConfigValue(this, path, default)
    }
}

class ConfigValue<T>(
    private val config: TypeSafeConfig,
    private val path: String,
    private val default: T
) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return config.get(path, default)
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        config.set(path, value)
    }
}

class ConfigException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)