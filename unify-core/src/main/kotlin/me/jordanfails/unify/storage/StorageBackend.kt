package me.jordanfails.unify.storage

import java.util.UUID

interface StorageBackend {
    fun load(playerId: UUID): PlayerStorageRecord?
    fun save(playerId: UUID, record: PlayerStorageRecord)
    fun delete(playerId: UUID)
    fun close() {}
}
