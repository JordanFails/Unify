package me.jordanfails.unify.storage

import me.jordanfails.honey.DataHandler
import me.jordanfails.honey.DataStoreType
import me.jordanfails.honey.connection.flatfile.FlatfileConnectionPool
import me.jordanfails.honey.connection.mongo.URIMongoConnectionPool
import me.jordanfails.honey.store.StoreType
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.UUID

class HoneyStorageBackend(
    private val plugin: JavaPlugin,
    configuration: StorageConfiguration
) : StorageBackend {

    private val store: StoreType<String, PlayerStorageRecord>
    private val keyMapper: (UUID) -> String

    init {
        when (configuration.backend) {
            StorageConfiguration.BackendType.JSON -> {
                val normalizedDirectory = normalizeJsonDirectory(configuration.json.directory)
                val storageDirectory = File(plugin.dataFolder, normalizedDirectory)

                val pool = FlatfileConnectionPool().apply {
                    directory = storageDirectory.absolutePath
                }

                DataHandler.withConnectionPool(pool)
                store = DataHandler.createStoreType<String, PlayerStorageRecord>(DataStoreType.FLATFILE)
                store.id = configuration.json.storeId
                keyMapper = { uuid -> uuid.toString() + configuration.json.keySuffix }
            }

            StorageConfiguration.BackendType.MONGO -> {
                val uri = configuration.mongo.uri
                require(uri.isNotBlank()) { "storage.mongo.uri is blank" }

                val pool = URIMongoConnectionPool().apply {
                    this.uri = uri
                    this.databaseName = configuration.mongo.database
                }

                DataHandler.withConnectionPool(pool)
                store = DataHandler.createStoreType<String, PlayerStorageRecord>(DataStoreType.MONGO)
                store.id = configuration.mongo.collection
                keyMapper = { uuid -> uuid.toString() }
            }
        }
    }

    override fun load(playerId: UUID): PlayerStorageRecord? {
        return store.retrieve(toStorageKey(playerId))?.copyRecord()
    }

    override fun save(playerId: UUID, record: PlayerStorageRecord) {
        store.store(toStorageKey(playerId), record.copyRecord())
    }

    override fun delete(playerId: UUID) {
        store.delete(toStorageKey(playerId))
    }

    override fun close() {
        // Honey 1.0.0 does not expose connection close hooks on ConnectionPool yet.
    }

    private fun toStorageKey(playerId: UUID): String {
        return keyMapper(playerId)
    }

    private fun normalizeJsonDirectory(configured: String): String {
        val trimmed = configured.trim().ifBlank { "storage.json" }
        val normalized = if (trimmed.endsWith(".json", ignoreCase = true)) {
            trimmed.removeSuffix(".json")
        } else {
            trimmed
        }

        return normalized.ifBlank { "storage" }
    }
}
