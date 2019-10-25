package com.epam.drill.plugins.coverage

import com.epam.drill.plugins.coverage.data.*
import java.util.concurrent.ConcurrentHashMap

interface TestsAssociatedWithBuild {
    fun add(buildVersion: String, associatedTestsList: List<AssociatedTests>)

    suspend fun getTestsToRun(
        agentState: AgentState,
        javaMethods: List<JavaMethod>
    ): TestsToRun

    suspend fun deletedCoveredMethodsCount(
        buildVersion: String,
        agentState: AgentState,
        deletedMethods: List<JavaMethod>
    ): Int
}

interface TestsAssociatedWithBuildStorageManager {
    fun getStorage(id: String, storageImplementation: TestsAssociatedWithBuild): TestsAssociatedWithBuild
}

class MutableMapTestsAssociatedWithBuild : TestsAssociatedWithBuild {

    private val map: MutableMap<String, MutableList<AssociatedTests>> = ConcurrentHashMap()

    private fun testsAssociatedWithMethods(
        methods: List<JavaMethod>,
        buildVersion: String
    ) = map[buildVersion]?.filter { test ->
        methods.any { method -> method.ownerClass == test.className && method.name == test.methodName }
    }

    override fun add(buildVersion: String, associatedTestsList: List<AssociatedTests>) {
        when {
            map[buildVersion].isNullOrEmpty() -> map[buildVersion] = associatedTestsList.toMutableList()
            else -> map[buildVersion]?.addAll(associatedTestsList)
        }
    }

    private fun Collection<FinishedScope>.typedTests() = flatMap {
        it.probes.values.flatMap { value ->
            value.flatMap { finishedSession -> finishedSession.testNames }
        }
    }.toSet()

    override suspend fun deletedCoveredMethodsCount(
        buildVersion: String,
        agentState: AgentState,
        deletedMethods: List<JavaMethod>
    ): Int {
        return testsAssociatedWithMethods(
            deletedMethods,
            agentState.prevBuildVersion
        )
            ?.toSet()
            ?.count() ?: 0
    }

    override suspend fun getTestsToRun(
        agentState: AgentState,
        javaMethods: List<JavaMethod>
    ): TestsToRun {
        val scopes = agentState.scopeManager.enabledScopes()
        val scopesInBuild = scopes.filter { it.buildVersion == agentState.agentInfo.buildVersion }

        return TestsToRun(testsAssociatedWithMethods(javaMethods, agentState.prevBuildVersion)
            ?.flatMap { it.tests }
            ?.filter { scopes.typedTests().contains(it) && !(scopesInBuild.typedTests().contains(it)) }
            ?.toSet()
            ?.groupBy({ it.type }, { it.name })
            .orEmpty()
        )
    }
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
