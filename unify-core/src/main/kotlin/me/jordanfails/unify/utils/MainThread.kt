package me.jordanfails.unify.utils

import org.bukkit.Bukkit

internal fun checkMainThread() {
    check(Bukkit.isPrimaryThread()) { "This operation must run on the server thread" }
}
