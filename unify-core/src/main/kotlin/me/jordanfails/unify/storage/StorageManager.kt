package me.jordanfails.unify.storage

import me.jordanfails.unify.utils.ItemSerialization
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object StorageManager {

    private lateinit var plugin: JavaPlugin
    private lateinit var configuration: StorageConfiguration
    private val records = ConcurrentHashMap<UUID, PlayerStorageRecord>()
    private var backend: StorageBackend? = null
    private var backendName: String = "json"

    fun enable(plugin: JavaPlugin, configuration: StorageConfiguration) {
        this.plugin = plugin
        this.configuration = configuration
        initializeBackend()
    }

    fun disable() {
        saveAll()
        backend?.close()
        backend = null
        records.clear()
    }

    fun reload() {
        requireInitialized()
        saveAll()
        backend?.close()
        initializeBackend()
    }

    fun isEnabled(): Boolean = backend != null

    fun getBackendName(): String = backendName

    fun getTotalPages(playerId: UUID, slotsPerPage: Int): Int {
        val record = getRecord(playerId)
        val highestSlot = record.items.keys
            .asSequence()
            .mapNotNull { it.toIntOrNull() }
            .maxOrNull()

        val usedPages = if (highestSlot == null) 1 else (highestSlot / slotsPerPage) + 1
        return maxOf(1, record.pages, usedPages)
    }

    fun ensurePage(playerId: UUID, page: Int) {
        val record = getRecord(playerId)
        if (page <= record.pages) {
            return
        }

        record.pages = page.coerceAtLeast(1)
        persist(playerId, record)
    }

    fun getPageItems(playerId: UUID, page: Int, slotsPerPage: Int): Map<Int, ItemStack> {
        val startIndex = (page - 1).coerceAtLeast(0) * slotsPerPage
        val items = linkedMapOf<Int, ItemStack>()

        for (relativeSlot in 0 until slotsPerPage) {
            val absoluteSlot = startIndex + relativeSlot
            val item = getItem(playerId, absoluteSlot) ?: continue
            items[relativeSlot] = item
        }

        return items
    }

    fun getItem(playerId: UUID, absoluteSlot: Int): ItemStack? {
        val serialized = getRecord(playerId).items[absoluteSlot.toString()] ?: return null
        return ItemSerialization.deserialize(serialized)?.clone()
    }

    fun setItem(playerId: UUID, absoluteSlot: Int, item: ItemStack?) {
        val record = getRecord(playerId)
        val slotKey = absoluteSlot.toString()

        if (item == null || item.type == Material.AIR) {
            record.items.remove(slotKey)
        } else {
            val serialized = ItemSerialization.serialize(item.clone()) ?: return
            record.items[slotKey] = serialized
        }

        if (record.items.isEmpty() && record.pages <= 1) {
            records.remove(playerId)
            backend?.delete(playerId)
            return
        }

        persist(playerId, record)
    }

    private fun initializeBackend() {
        val selectedBackend = configuration.backend

        val initializedBackend = try {
            when (selectedBackend) {
                StorageConfiguration.BackendType.MONGO -> {
                    backendName = "mongo"
                    HoneyStorageBackend(plugin, configuration)
                }
                else -> {
                    backendName = "json"
                    HoneyStorageBackend(plugin, configuration)
                }
            }
        } catch (ex: Exception) {
            plugin.logger.warning("Failed to initialize '$selectedBackend' storage backend: ${ex.message}")
            plugin.logger.warning("Falling back to JSON storage.")
            backendName = "json"
            HoneyStorageBackend(plugin, configuration.copy(backend = StorageConfiguration.BackendType.JSON))
        }

        backend = initializedBackend
        records.clear()
        plugin.logger.info("Storage backend: $backendName")
    }

    private fun getRecord(playerId: UUID): PlayerStorageRecord {
        requireInitialized()
        return records.computeIfAbsent(playerId) {
            backend?.load(playerId)?.copyRecord() ?: PlayerStorageRecord()
        }
    }

    private fun persist(playerId: UUID, record: PlayerStorageRecord) {
        val snapshot = record.copyRecord()
        records[playerId] = snapshot
        backend?.save(playerId, snapshot)
    }

    private fun saveAll() {
        val activeBackend = backend ?: return
        records.forEach { (playerId, record) ->
            activeBackend.save(playerId, record.copyRecord())
        }
    }

    private fun requireInitialized() {
        check(::plugin.isInitialized && ::configuration.isInitialized && backend != null) {
            "StorageManager has not been initialized. Call StorageManager.enable(plugin, configuration) from the owning plugin first."
        }
    }
}
