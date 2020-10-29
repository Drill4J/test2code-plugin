package com.epam.drill.plugins.test2code
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.storage.*
import kotlinx.serialization.*

@Serializable
data class BuildTests(
    val tests: GroupedTests = emptyMap(),
    val assocTests: Set<AssociatedTests> = emptySet(),
    val testsToRun: GroupedTests = emptyMap()
)

internal suspend fun AgentState.testsToRun(
    coverMethods: List<CoverMethod>
): GroupedTests = buildManager[agentInfo.buildVersion]?.parentVersion?.takeIf { it.any() }?.let { parentVersion ->
    val assocTests = coverContext().parentBuild?.let {
        coverMethods.associatedTests(it.tests.assocTests)
    }
    val buildVersion = agentInfo.buildVersion
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
