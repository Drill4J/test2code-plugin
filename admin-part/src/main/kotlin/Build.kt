package com.epam.drill.plugins.test2code

import com.epam.drill.common.*
import com.epam.drill.plugin.api.*
import com.epam.kodux.*
import kotlinx.serialization.Serializable

@Serializable
data class LastBuildCoverage(
    @Id val id: String,
    val coverage: Double,
    val arrow: String?,
    val risks: Int,
    val testsToRun: TestsToRunDto
)

//TODO move to admin api

fun BuildManager.childrenOf(version: String): List<BuildInfo> {
    return builds.childrenOf(version)
}

fun BuildManager.otherVersions(version: String): List<BuildInfo> {
    return childrenOf("").filter { it.version != version }
}

val Iterable<BuildInfo>.roots: List<BuildInfo>
    get() = filter { it.parentVersion.isBlank() }

fun Iterable<BuildInfo>.childrenOf(version: String): List<BuildInfo> {
    val other = filter { it.version != version }
    val (children, rest) = other.partition { it.parentVersion == version }
    return children + children.flatMap { rest.childrenOf(it.version) }
}
