package com.epam.drill.plugins.test2code

import mu.*
import org.slf4j.*
import org.slf4j.helpers.*

internal fun logger(name: String): KLogger = KotlinLogging.logger(
    SimpleLogger("DRILL plugin test2code - $name")
)

private class SimpleLogger(
    private val loggerName: String
) : Logger by NOPLogger.NOP_LOGGER.toKLogger() {

    override fun getName() = loggerName

    override fun isInfoEnabled() = true
    override fun isWarnEnabled() = true
    override fun isErrorEnabled() = true

    override fun info(msg: String?) = log("INFO", msg)
    override fun info(msg: String?, t: Throwable?) = log("INFO", msg, t)
    override fun warn(msg: String?) = log("WARN", msg)
    override fun warn(msg: String?, t: Throwable?) = log("WARN", msg, t)
    override fun error(msg: String?) = log("ERROR", msg)
    override fun error(msg: String?, t: Throwable?) = log("ERROR", msg, t)

    private fun log(level: String, msg: String?, t: Throwable? = null) {
        System.currentTimeMillis()
        println("${System.currentTimeMillis()} [$level][$name] $msg")
        t?.printStackTrace()
    }
}
