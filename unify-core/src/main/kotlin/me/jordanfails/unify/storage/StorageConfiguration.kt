package me.jordanfails.unify.storage

data class StorageConfiguration(
    val backend: BackendType = BackendType.JSON,
    val json: Json = Json(),
    val mongo: Mongo = Mongo()
) {

    enum class BackendType {
        JSON,
        MONGO
    }

    data class Json(
        val directory: String = "storage",
        val storeId: String = "player_storage",
        val keySuffix: String = ".json"
    )

    data class Mongo(
        val uri: String = "",
        val database: String = "unify",
        val collection: String = "player_storage"
    )

    companion object {
        @JvmStatic
        fun json(
            directory: String = "storage",
            storeId: String = "player_storage",
            keySuffix: String = ".json"
        ): StorageConfiguration {
            return StorageConfiguration(
                backend = BackendType.JSON,
                json = Json(
                    directory = directory,
                    storeId = storeId,
                    keySuffix = keySuffix
                )
            )
        }

        @JvmStatic
        fun mongo(
            uri: String,
            database: String = "unify",
            collection: String = "player_storage"
        ): StorageConfiguration {
            return StorageConfiguration(
                backend = BackendType.MONGO,
                mongo = Mongo(
                    uri = uri,
                    database = database,
                    collection = collection
                )
            )
        }
    }
}
