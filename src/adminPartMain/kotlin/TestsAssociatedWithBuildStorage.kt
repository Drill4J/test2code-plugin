package com.epam.drill.plugins.coverage

import java.util.concurrent.*

interface TestsAssociatedWithBuild {
    fun add(buildVersion: String, associatedTestsList: List<AssociatedTests>)
    suspend fun getTestsAssociatedWithMethods(
        buildVersion: String,
        agentState: AgentState,
        javaMethods: List<JavaMethod>
    ): Map<String, List<String>>
}

interface TestsAssociatedWithBuildStorageManager {
    fun getStorage(id: String, storageImplementation: TestsAssociatedWithBuild): TestsAssociatedWithBuild
}

class MutableMapTestsAssociatedWithBuild : TestsAssociatedWithBuild {

    private val map: MutableMap<String, MutableSet<AssociatedTests>> = ConcurrentHashMap()

    override fun add(buildVersion: String, associatedTestsList: List<AssociatedTests>) {
        when {
            map[buildVersion].isNullOrEmpty() -> map[buildVersion] = associatedTestsList.toMutableSet()
            else -> map[buildVersion]?.addAll(associatedTestsList)
        }
    }

    private suspend fun enabledFinishedScopes(
        agentState: AgentState
    ): Collection<FinishedScope> {
        return agentState.scopeManager.allScopes().filter { it.enabled }
    }

    private fun typedTestsFromScopes(scopes: Collection<FinishedScope>): Set<TypedTest> {
        return scopes.flatMap {
            it.probes.values.flatMap { value ->
                value.flatMap { finishedSession -> finishedSession.testNames }
            }
        }.toSet()
    }

    private fun typedTestsFromScopesAssociatedWithCurrentBuildVersion(
        scopes: Collection<FinishedScope>,
        buildVersion: String
    ): Set<TypedTest> {
        return typedTestsFromScopes(scopes.filter { it.buildVersion == buildVersion })
    }

    override suspend fun getTestsAssociatedWithMethods(
        buildVersion: String,
        agentState: AgentState,
        javaMethods: List<JavaMethod>
    ): Map<String, List<String>> {
        val scopes = enabledFinishedScopes(agentState)

        return map[previousBuildVersion(buildVersion, agentState)].orEmpty()
            .filter { test ->
                javaMethods.any { method -> method.ownerClass == test.className && method.name == test.methodName }
            }
            .flatMap { it.tests }
            .filter {
                typedTestsFromScopes(scopes).contains(it)
                        && !typedTestsFromScopesAssociatedWithCurrentBuildVersion(scopes, buildVersion).contains(it)
            }.toSet()
            .groupBy({ it.type }, { it.name })
    }

    private suspend fun previousBuildVersion(buildVersion: String, agentState: AgentState): String =
        (agentState.classesData(buildVersion) as ClassesData).prevBuildVersion
}

object MutableMapStorageManager : TestsAssociatedWithBuildStorageManager {
    private val storage: MutableMap<String, TestsAssociatedWithBuild> = ConcurrentHashMap()

    override fun getStorage(
        id: String,
        storageImplementation: TestsAssociatedWithBuild
    ): TestsAssociatedWithBuild = storage[id] ?: addStorage(id, storageImplementation)


    private fun addStorage(
        id: String,
        testsAssociatedWithBuild: TestsAssociatedWithBuild
    ): TestsAssociatedWithBuild = testsAssociatedWithBuild.apply { storage[id] = testsAssociatedWithBuild }
}

val testsAssociatedWithBuildStorageManager: TestsAssociatedWithBuildStorageManager = MutableMapStorageManager
