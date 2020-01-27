package com.epam.drill.plugins.test2code

import com.epam.kodux.*
import kotlinx.serialization.*
import java.util.concurrent.*
import kotlin.collections.set

//TODO remove this incoherent mess

@Serializable
class BuildTests(
    @Id val id: String,
    val map: MutableMap<String, List<AssociatedTests>> = ConcurrentHashMap()
) {
    private fun testsAssociatedWithMethods(
        methods: List<JavaMethod>,
        buildVersion: String
    ): Sequence<AssociatedTests>? = map[buildVersion]?.filter { test ->
        methods.any { method -> method.ownerClass == test.className && method.name == test.methodName }
    }?.asSequence()

    fun add(buildVersion: String, associatedTestsList: List<AssociatedTests>) {
        val existingList = map[buildVersion] ?: emptyList()
        map[buildVersion] = existingList + associatedTestsList
    }

    fun deletedCoveredMethodsCount(
        buildVersion: String,
        deletedMethods: List<JavaMethod>
    ): Int = testsAssociatedWithMethods(
        deletedMethods,
        buildVersion
    )?.distinct()?.count() ?: 0

    suspend fun getTestsToRun(
        state: PluginInstanceState,
        buildVersion: String,
        javaMethods: List<JavaMethod>
    ): Map<String, List<String>> = when (val classesData = state.classesData(buildVersion)) {
        is ClassesData -> {
            val scopeManager = state.scopeManager
            val prevBuildVersion = classesData.prevBuildVersion
            val testsAssociatedWithMethods = testsAssociatedWithMethods(
                methods = javaMethods,
                buildVersion = prevBuildVersion
            )
            testsAssociatedWithMethods?.let { assocTestsSeq ->
                val buildTests: Set<TypedTest> = scopeManager.scopes(buildVersion).typedTests()
                val prevBuildTests: Set<TypedTest> = scopeManager.scopes(prevBuildVersion).typedTests()
                assocTestsSeq
                    .flatMap { it.tests.asSequence() }
                    .filter { it in prevBuildTests && it !in buildTests }
                    .groupBy({ it.type }, { it.name })

            }.orEmpty()
        }
        else -> emptyMap()
    }
}


private fun Sequence<FinishedScope>.typedTests(): Set<TypedTest> = flatMap { scope ->
    scope.probes.asSequence().flatMap { (_, sessions) ->
        sessions.asSequence().flatMap { finishedSession -> finishedSession.testNames.asSequence() }
    }
}.toSet()
