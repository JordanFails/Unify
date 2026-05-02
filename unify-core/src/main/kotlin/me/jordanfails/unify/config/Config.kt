package me.jordanfails.unify.config

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.InvalidConfigurationException
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.FileConfigurationOptions
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.IOException

class Config(
    plugin: JavaPlugin,
    name: String,
    directory: File? = null
) {
    private val file: File
    private var config: FileConfiguration = YamlConfiguration()

    init {
        val folder = directory ?: plugin.dataFolder
        if (!folder.exists()) {
            folder.mkdirs()
        }

        file = File(folder, "$name.yml")

        if (!file.exists()) {
            try {
                if (plugin.getResource("$name.yml") != null) {
                    plugin.saveResource("$name.yml", false)
                } else {
                    file.createNewFile()
                }
            } catch (ex: Exception) {
                plugin.logger.warning("Could not create config file $name.yml: ${ex.message}")
            }
        }

        try {
            config.load(file)
        } catch (ex: IOException) {
            ex.printStackTrace()
        } catch (ex: InvalidConfigurationException) {
            ex.printStackTrace()
        }
    }

    fun reload() {
        config = YamlConfiguration()
        try {
            config.load(file)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun save() {
        try {
            config.save(file)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /** Add a default safely if missing */
    fun addDefault(path: String, value: Any) {
        if (!config.contains(path)) {
            config.set(path, value)
            save()
        }
    }

    operator fun set(path: String, value: Any?) {
        config.set(path, value)
    }

    fun contains(path: String): Boolean = config.contains(path)

    fun getString(path: String): String = config.getString(path) ?: ""
    fun getString(path: String, def: String): String? = config.getString(path, def)
    fun getInt(path: String): Int = config.getInt(path)
    fun getInt(path: String, def: Int): Int = config.getInt(path, def)
    fun getLong(path: String): Long = config.getLong(path)
    fun getDouble(path: String): Double = config.getDouble(path)
    fun getBoolean(path: String): Boolean = config.getBoolean(path)
    fun getList(path: String): MutableList<*>? = config.getList(path)

    fun getStringList(path: String): List<String> = config.getStringList(path)
    fun getIntegerList(path: String): List<Int> = config.getIntegerList(path)

    fun getConfigurationSection(path: String): ConfigurationSection? = config.getConfigurationSection(path)

    fun options(): FileConfigurationOptions? = config.options()
}