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
    ): List<AssociatedTests>? = map[buildVersion]?.filter { test ->
        methods.any { method -> method.ownerClass == test.className && method.name == test.methodName }
    }

    fun add(buildVersion: String, associatedTestsList: List<AssociatedTests>) {
        val existingList = map[buildVersion] ?: emptyList()
        map[buildVersion] = existingList + associatedTestsList
    }

    private fun Collection<FinishedScope>.typedTests() = flatMap {
        it.probes.values.flatMap { value ->
            value.flatMap { finishedSession -> finishedSession.testNames }
        }
    }.toSet()

    fun deletedCoveredMethodsCount(
        buildVersion: String,
        deletedMethods: List<JavaMethod>
    ): Int = testsAssociatedWithMethods(
        deletedMethods,
        buildVersion
    )?.distinct()?.count() ?: 0

    suspend fun getTestsToRun(
        pluginInstanceState: PluginInstanceState,
        buildVersion: String,
        javaMethods: List<JavaMethod>
    ): Map<String, List<String>> {
        val allScopes = pluginInstanceState.scopeManager.enabledScopes()
        val buildScopes = allScopes.filter { it.buildVersion == buildVersion }
        val classesData = pluginInstanceState.classesData(buildVersion) as? ClassesData

        val testsAssociatedWithMethods = classesData?.run {
            testsAssociatedWithMethods(
                methods = javaMethods,
                buildVersion = prevBuildVersion
            )
        }
        return testsAssociatedWithMethods
            ?.flatMap { it.tests }
            ?.filter { allScopes.typedTests().contains(it) && !(buildScopes.typedTests().contains(it)) }
            ?.toSet()
            ?.groupBy({ it.type }, { it.name })
            .orEmpty()
    }
}
