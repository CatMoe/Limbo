package net.miaomoe.limbo.util

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.logging.log4j.jul.LevelTranslator
import org.apache.logging.log4j.message.MessageFormatMessage
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Handler
import java.util.logging.LogRecord

// Basically from https://github.com/PaperMC/Waterfall/blob/master/BungeeCord-Patches/0034-Use-Log4j2-for-logging-and-TerminalConsoleAppender-f.patch#L98-L158
class Log4jJulHandler : Handler() {

    private val cache: MutableMap<String, Logger> = ConcurrentHashMap()

    override fun publish(record: LogRecord) {
        if (!isLoggable(record)) return
        val logger = cache.computeIfAbsent(record.loggerName ?: "") { LogManager.getLogger(it) }
        var message = record.message
        val resourceBundle = record.resourceBundle
        if (resourceBundle != null) try { message = resourceBundle.getString(message) } catch (_: MissingResourceException) {}
        val level = LevelTranslator.toLevel(record.level)
        val parameters = record.parameters
        if (!parameters.isNullOrEmpty())
            logger.log(level, MessageFormatMessage(message, parameters, record.thrown))
        else
            logger.log(level, message, record.thrown)
    }

    override fun flush() {
        // Not Impl.
    }

    override fun close() {
        // Not Impl.
    }
}