package me.jordanfails.unify.storage

data class PlayerStorageRecord(
    var pages: Int = 1,
    val items: MutableMap<String, String> = linkedMapOf()
) {
    fun copyRecord(): PlayerStorageRecord {
        return PlayerStorageRecord(
            pages = pages,
            items = LinkedHashMap(items)
        )
    }
}
