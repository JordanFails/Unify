package me.jordanfails.unify.utils

import me.jordanfails.unify.UnifyCore
import java.util.logging.Logger

object LogUtil {
    val LOGGER: Logger = UnifyCore.instance.logger
    @JvmStatic
    fun info(message: String, vararg arguments: Any?) {
        LOGGER.info(CC.formatTranslate(message, *arguments))
    }
    @JvmStatic
    fun warning(message: String, vararg arguments: Any?) {
        LOGGER.warning(CC.formatTranslate(message, *arguments))
    }
    @JvmStatic
    fun error(message: String, vararg arguments: Any?) {
        LOGGER.severe(CC.formatTranslate(message, *arguments))
    }
}