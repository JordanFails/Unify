package me.jordanfails.unify.utils

import me.jordanfails.unify.UnifyCore
import java.util.logging.Logger

object LogUtil {

    private val logger: Logger = UnifyCore.instance.logger

    @JvmStatic
    fun info(message: String, vararg arguments: Any?) {
        logger.info(CC.formatTranslate(message, *arguments))
    }

    @JvmStatic
    fun warning(message: String, vararg arguments: Any?) {
        logger.warning(CC.formatTranslate(message, *arguments))
    }

    @JvmStatic
    fun error(message: String, vararg arguments: Any?) {
        logger.severe(CC.formatTranslate(message, *arguments))
    }
}