package me.jordanfails.unify.utils

class ListBuilder<K> {

    val list: MutableList<K> = mutableListOf()

    /**
     * Adds a single item to the list.
     */
    fun add(item: K): ListBuilder<K> {
        list.add(item)
        return this
    }

    /**
     * Adds all elements from another list.
     */
    fun add(list: List<K>): ListBuilder<K> {
        this.list.addAll(list)
        return this
    }

    /**
     * Builds and returns an immutable copy of the list.
     */
    fun build(): List<K> {
        return list.toList()
    }
}
