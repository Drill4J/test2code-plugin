package com.epam.drill.plugins.test2code

import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.storage.*
import com.epam.kodux.*
import kotlinx.collections.immutable.*
import kotlinx.serialization.*

@Serializable
data class BuildTests(
    val assocTests: Set<AssociatedTests> = persistentSetOf(),
    val testsToRun: GroupedTests = emptyMap()
)

@Serializable
data class StoredBuildTests(
    @Id val version: String,
    val data: ByteArray
) {
    override fun equals(other: Any?) = (other as? StoredBuildTests)?.version == version

    override fun hashCode() = version.hashCode()
}

suspend fun AgentState.testsToRun(
    buildVersion: String,
    coverMethods: List<CoverMethod>
): GroupedTests = buildManager[buildVersion]?.parentVersion?.takeIf { it.any() }?.let { parentVersion ->
    val assocTests = builds[parentVersion]?.let {
        coverMethods.associatedTests(it.tests.assocTests)
    }
    assocTests?.let { assocTestsSeq ->
        val curBuildTests: Set<TypedTest> = scopeManager.byVersion(
            buildVersion, withData = true
        ).enabled().typedTests()
        val prevBuildTests: Set<TypedTest> = scopeManager.byVersion(
            parentVersion, withData = true
        ).enabled().typedTests()
        assocTestsSeq
            .flatMap { it.tests.asSequence() }
            .distinct()
            .filter { it in prevBuildTests && it !in curBuildTests }
            .groupBy({ it.type }, { it.name })

    }
} ?: emptyMap()

fun MethodsInfo.testCount(
    tests: Set<AssociatedTests>
): Int = methods.associatedTests(tests).count()

fun Iterable<CoverMethod>.associatedTests(
    tests: Set<AssociatedTests>
): Sequence<AssociatedTests> = tests.filter { test ->
    any { method -> method.ownerClass == test.className && method.name == test.methodName }
}.asSequence()

private fun Sequence<FinishedScope>.typedTests(): Set<TypedTest> = flatMapTo(mutableSetOf()) {
    it.data.typedTests.asSequence()
}
