package com.epam.drill.plugins.test2code

import com.epam.kodux.*
import kotlinx.serialization.*
import java.util.concurrent.*
import kotlin.collections.set

@Serializable
class BuildTests(
    @Id val id: String,
    private val map: MutableMap<String, List<AssociatedTests>> = ConcurrentHashMap() //TODO persistent hash map
) {
    fun add(buildVersion: String, associatedTestsList: List<AssociatedTests>) {
        val existingList = map[buildVersion] ?: emptyList()
        map[buildVersion] = existingList + associatedTestsList
    }

    operator fun get(buildVersion: String) = map[buildVersion]
}

suspend fun PluginInstanceState.testsToRun(
    buildVersion: String,
    javaMethods: List<JavaMethod>
): GroupedTests = when (val classesData = classesData(buildVersion)) {
    is ClassesData -> {
        val prevBuildVersion = classesData.prevBuildVersion
        val testsAssociatedWithMethods = javaMethods.associatedTests(buildTests, prevBuildVersion)
        testsAssociatedWithMethods?.let { assocTestsSeq ->
            val curBuildTests: Set<TypedTest> = scopeManager.scopes(buildVersion).typedTests()
            val prevBuildTests: Set<TypedTest> = scopeManager.scopes(prevBuildVersion).typedTests()
            assocTestsSeq
                .flatMap { it.tests.asSequence() }
                .filter { it in prevBuildTests && it !in curBuildTests }
                .groupBy({ it.type }, { it.name })

        }.orEmpty()
    }
    else -> emptyMap()
}

fun GroupedTests.testsToRunDto() = TestsToRunDto(
    groupedTests = this,
    count = totalCount()
)

fun Iterable<JavaMethod>.associatedTests(
    buildTests: BuildTests,
    buildVersion: String
): Sequence<AssociatedTests>? = buildTests[buildVersion]?.filter { test ->
    any { method -> method.ownerClass == test.className && method.name == test.methodName }
}?.asSequence()

fun Iterable<JavaMethod>.testCount(
    buildTests: BuildTests,
    buildVersion: String
) : Int = associatedTests(buildTests, buildVersion)?.distinct()?.count() ?: 0

private fun Sequence<FinishedScope>.typedTests(): Set<TypedTest> = flatMap { scope ->
    scope.probes.asSequence().flatMap { (_, sessions) ->
        sessions.asSequence().flatMap { finishedSession -> finishedSession.testNames.asSequence() }
    }
}.toSet()
