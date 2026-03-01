package me.jordanfails.unify.utils.command

interface IPluginCommandContext<T> {
    val id: String
    val clazz: Class<T>
    fun resolve(playerName: String): T
    fun completions(input: String): Collection<String>
}