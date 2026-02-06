package me.jordanfails.unify.config

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import java.io.File
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties

abstract class TypeSafeConfig(
    private val plugin: Plugin,
    private val fileName: String = "config.yml"
) {
    private val configFile: File = File(plugin.dataFolder, fileName)
    private var config: FileConfiguration

    init {
        if (!configFile.exists()) {
            plugin.saveResource(fileName, false)
        }
        config = YamlConfiguration.loadConfiguration(configFile)
        load()
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> get(path: String, default: T): T {
        return when (default) {
            is String -> (config.getString(path) ?: default) as T
            is Int -> (config.getInt(path, default)) as T
            is Double -> (config.getDouble(path, default)) as T
            is Boolean -> (config.getBoolean(path, default)) as T
            is Long -> (config.getLong(path, default)) as T
            is List<*> -> (config.getList(path) ?: default) as T
            else -> (config.get(path) ?: default) as T
        }
    }

    fun <T> set(path: String, value: T) {
        config.set(path, value)
    }

    fun save() {
        config.save(configFile)
    }

    fun reload() {
        config = YamlConfiguration.loadConfiguration(configFile)
        load()
    }

    private fun load() {
        val properties = this::class.memberProperties
        val commentsToWrite = mutableMapOf<String, String>()
        
        for (property in properties) {
            val configPath = property.findAnnotation<ConfigPath>()
            val configNote = property.findAnnotation<ConfigNote>()
            
            if (configPath != null) {
                val path = configPath.value
                
                if (configNote != null) {
                    commentsToWrite[path] = configNote.value
                }
                
                if (!config.contains(path)) {
                    @Suppress("UNCHECKED_CAST")
                    val prop = property as? KProperty1<TypeSafeConfig, *>
                    val defaultValue = prop?.get(this)
                    if (defaultValue != null) {
                        config.set(path, defaultValue)
                    }
                }
            }
        }
        
        save()
        
        if (commentsToWrite.isNotEmpty()) {
            writeCommentsToFile(commentsToWrite)
        }
    }

    private fun writeCommentsToFile(comments: Map<String, String>) {
        if (!configFile.exists()) return
        
        val lines = configFile.readLines().toMutableList()
        val newLines = mutableListOf<String>()
        val processedPaths = mutableSetOf<String>()
        
        for (line in lines) {
            val trimmed = line.trim()
            
            for ((path, comment) in comments) {
                val key = path.substringAfterLast('.')
                if (trimmed.startsWith("$key:") && !processedPaths.contains(path)) {
                    val indent = line.takeWhile { it.isWhitespace() }
                    if (newLines.lastOrNull()?.trim() != "# $comment") {
                        newLines.add("$indent# $comment")
                    }
                    processedPaths.add(path)
                    break
                }
            }
            
            newLines.add(line)
        }
        
        configFile.writeText(newLines.joinToString("\n"))
    }

    protected fun <T> value(default: T): ConfigValue<T> {
        val stackTrace = Thread.currentThread().stackTrace
        val callerMethod = stackTrace[2].methodName
        val propertyName = callerMethod.removePrefix("get").replaceFirstChar { it.lowercase() }
        return ConfigValue(this, propertyName, default)
    }

    protected fun <T> value(path: String, default: T): ConfigValue<T> {
        return ConfigValue(this, path, default)
    }
}
