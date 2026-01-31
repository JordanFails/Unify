package me.jordanfails.unify.exception

class InvalidOutputException(
    message: String? = null,
    cause: Throwable? = null
) : Exception(message, cause)