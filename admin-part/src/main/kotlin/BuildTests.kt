package com.epam.drill.plugins.test2code

import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.storage.*
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
        map[buildVersion] = (existingList + associatedTestsList).distinct() //FIXME replace this
    }

    operator fun get(buildVersion: String) = map[buildVersion]
}

suspend fun PluginInstanceState.testsToRun(
    buildVersion: String,
    javaMethods: List<JavaMethod>
): GroupedTests = when (val classesData = classesData(buildVersion)) {
    is ClassData -> {
        val prevBuildVersion = classesData.prevBuildVersion
        val testsAssociatedWithMethods = javaMethods.associatedTests(buildTests, prevBuildVersion)
        testsAssociatedWithMethods?.let { assocTestsSeq ->
            val curBuildTests: Set<TypedTest> = scopeManager.byVersion(
                buildVersion, withData = true
            ).enabled().typedTests()
            val prevBuildTests: Set<TypedTest> = scopeManager.byVersion(
                prevBuildVersion, withData = true
            ).enabled().typedTests()
            assocTestsSeq
                .flatMap { it.tests.asSequence() }
                .distinct()
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

fun MethodsInfo.testCount(
    buildTests: BuildTests,
    buildVersion: String
) : Int = methods.associatedTests(buildTests, buildVersion)?.distinct()?.count() ?: 0

private fun Sequence<FinishedScope>.typedTests(): Set<TypedTest> = flatMapTo(mutableSetOf()) {
    it.data.typedTests.asSequence()
}
