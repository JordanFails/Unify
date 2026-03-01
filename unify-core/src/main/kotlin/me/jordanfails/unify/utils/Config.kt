package me.jordanfails.unify.utils

import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.plugin.java.JavaPlugin
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.nio.file.FileSystems
import java.nio.file.StandardWatchEventKinds.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.reflect.KClass

class Config(
    private val plugin: JavaPlugin,
    private val fileName: String
) {
    private val yaml = Yaml(SafeConstructor(LoaderOptions()))
    private val file = File(plugin.dataFolder, fileName)

    @PublishedApi
    internal var data: MutableMap<String, Any> = mutableMapOf()

    @PublishedApi
    internal val cache = ConcurrentHashMap<String, Any>()

    @PublishedApi
    internal val lock = ReentrantReadWriteLock()

    private val watcherExecutor = Executors.newSingleThreadExecutor()
    @Volatile private var watching = false

    init {
        ensureFileExists()
        reload()
        startWatcher()
    }

    // region Accessors

    inline operator fun <reified T : Any> get(key: String): T? = get(key, null)

    inline operator fun <reified T : Any> get(key: String, default: T?): T? {
        if (key.isEmpty()) return null

        cache[key]?.let {
            @Suppress("UNCHECKED_CAST")
            return it as T
        }

        return lock.read {
            val value = resolveKey(key) ?: return@read default
            val result = convertValue(value, T::class)
            cache[key] = result
            result
        }
    }

    inline operator fun <reified T : Any> invoke(key: String): T? = get(key, null)

    inline operator fun <reified T : Any> invoke(key: String, default: T?): T? = get(key, default)

    operator fun set(key: String, value: Any) {
        lock.write {
            val parts = key.split(".")
            var current = data
            for (i in 0 until parts.size - 1) {
                val part = parts[i]
                val next = (current[part] as? MutableMap<String, Any>) ?: mutableMapOf()
                current[part] = next
                current = next
            }
            current[parts.last()] = value
            cache.remove(key)
            save()
        }
    }

    fun contains(key: String): Boolean = lock.read { resolveKey(key) != null }

    fun remove(key: String) = lock.write {
        val parts = key.split(".")
        var current = data
        for (i in 0 until parts.size - 1) {
            val next = current[parts[i]] as? MutableMap<String, Any> ?: return@write
            current = next
        }
        current.remove(parts.last())
        cache.remove(key)
        save()
    }

    // endregion

    // region File Operations

    fun reload() {
        lock.write {
            if (!file.exists()) ensureFileExists()

            FileReader(file).use { reader ->
                val loaded = yaml.load<Any>(reader)
                @Suppress("UNCHECKED_CAST")
                data = (loaded as? MutableMap<String, Any>) ?: mutableMapOf()
            }

            cache.clear()
            plugin.logger.info("[Config] Reloaded ${file.name}")
        }
    }

    fun save() {
        lock.read {
            val tempFile = File(file.parentFile, "${file.name}.tmp")
            FileWriter(tempFile).use { writer -> yaml.dump(data, writer) }
            if (file.exists()) file.delete()
            tempFile.renameTo(file)
        }
    }

    private fun ensureFileExists() {
        if (!plugin.dataFolder.exists()) plugin.dataFolder.mkdirs()
        if (!file.exists()) plugin.saveResource(fileName, false)
    }

    // endregion

    // region Watcher

    private fun startWatcher() {
        if (watching) return
        watching = true

        watcherExecutor.submit {
            try {
                val watchService = FileSystems.getDefault().newWatchService()
                val dir = file.parentFile.toPath()
                dir.register(watchService, ENTRY_MODIFY, ENTRY_CREATE, ENTRY_DELETE)

                plugin.logger.info("[Config] Watching for changes in ${file.name}")

                var lastReload = 0L

                while (watching) {
                    val key = watchService.poll(1, TimeUnit.SECONDS) ?: continue
                    for (event in key.pollEvents()) {
                        val changed = event.context()?.toString() ?: continue
                        if (changed.equals(file.name, ignoreCase = true)) {
                            val now = System.currentTimeMillis()
                            if (now - lastReload > 1000) {
                                lastReload = now
                                plugin.server.scheduler.runTaskLater(plugin, Runnable {
                                    reload()
                                }, 20L)
                            }
                        }
                    }
                    key.reset()
                }
            } catch (ex: Exception) {
                plugin.logger.warning("[Config] Watcher stopped: ${ex.message}")
            }
        }
    }

    fun stopWatcher() {
        watching = false
        watcherExecutor.shutdownNow()
    }

    // endregion

    // region Helpers

    @PublishedApi
    internal fun resolveKey(key: String): Any? {
        val parts = key.split(".")
        var current: Any? = data
        for (part in parts) {
            current = (current as? Map<*, *>)?.get(part)
        }
        return current
    }

    @Suppress("UNCHECKED_CAST")
    @PublishedApi
    internal fun <T : Any> convertValue(value: Any, type: KClass<T>): T = when (type) {
        String::class -> value.toString() as T
        Int::class -> (value as Number).toInt() as T
        Long::class -> (value as Number).toLong() as T
        Double::class -> (value as Number).toDouble() as T
        Boolean::class -> (value as Boolean) as T
        List::class -> (value as List<*>) as T
        Map::class -> (value as Map<*, *>) as T
        Material::class -> Material.matchMaterial(value.toString()) as T
        Color::class -> Color.fromRGB((value as Number).toInt()) as T
        else -> {
            if (value is Map<*, *> && type.isData) {
                mapToDataClass(value as Map<String, Any>, type)
            } else {
                value as? T ?: throw IllegalArgumentException(
                    "Type mismatch: expected ${type.simpleName}, got ${value::class.simpleName}"
                )
            }
        }
    }

    @PublishedApi
    internal fun <T : Any> mapToDataClass(map: Map<String, Any>, type: KClass<T>): T {
        // Create instance using no-arg constructor via Java reflection
        val instance = type.java.getDeclaredConstructor().newInstance()

        // Set fields via Java reflection
        for ((fieldName, value) in map) {
            try {
                val field = type.java.getDeclaredField(fieldName)
                field.isAccessible = true

                // Determine field type
                val fieldType = field.type.kotlin
                val convertedValue = convertFieldValue(value, fieldType)
                field.set(instance, convertedValue)
            } catch (e: NoSuchFieldException) {
                // Skip fields that don't exist in the class
            } catch (e: Exception) {
                plugin.logger.warning("[Config] Failed to set field '$fieldName': ${e.message}")
            }
        }

        return instance
    }

    @PublishedApi
    internal fun convertFieldValue(value: Any, paramType: KClass<*>?): Any? = when (paramType) {
        String::class -> value.toString()
        Int::class -> (value as Number).toInt()
        Long::class -> (value as Number).toLong()
        Double::class -> (value as Number).toDouble()
        Boolean::class -> value as Boolean
        Material::class -> Material.matchMaterial(value.toString())
        Color::class -> Color.fromRGB((value as Number).toInt())
        else -> when {
            paramType?.isData == true && value is Map<*, *> ->
                mapToDataClass(value as Map<String, Any>, paramType)
            else -> value
        }
    }

    // endregion
}