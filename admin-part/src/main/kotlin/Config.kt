package com.epam.drill.plugins.test2code

internal class RuntimeConfig(private val pluginId: String) {
    val realtime: Boolean = sysProp("features.realtime")?.toBoolean() ?: true

    val sendPackages: Boolean = sysProp("send.packages")?.toBoolean() ?: true

    private fun sysProp(key: String): String? = System.getProperty("drill.plugins.$pluginId.$key")
}
