package com.epam.drill.plugins.test2code

import com.epam.drill.common.*
import com.epam.drill.plugin.api.*

//TODO move to admin api

fun BuildManager.childrenOf(version: String): List<BuildInfo> {
    val versions = buildInfos.values
    return versions.childrenOf(version)
}

fun BuildManager.otherVersions(version: String): List<BuildInfo> {
    return childrenOf("").filter { it.buildVersion != version }
}

val Iterable<BuildInfo>.roots: List<BuildInfo>
    get() = filter { it.prevBuild.isBlank() }

fun Iterable<BuildInfo>.childrenOf(version: String): List<BuildInfo> {
    val other = filter { it.buildVersion != version }
    val (children, rest) = other.partition { it.prevBuild == version }
    return children + children.flatMap { rest.childrenOf(it.buildVersion) }
}
