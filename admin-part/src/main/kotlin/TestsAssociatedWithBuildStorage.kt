package com.epam.drill.plugins.coverage

import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger { }

interface TestsAssociatedWithBuild {
    fun add(buildVersion: String, associatedTestsList: List<AssociatedTests>)

    suspend fun getTestsToRun(
        agentState: AgentState,
        javaMethods: List<JavaMethod>
    ): Map<String, List<String>>

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
        logger.debug { "Adding associated tests to storage associates witt build version $buildVersion" }
        when {
            map[buildVersion].isNullOrEmpty() -> {
                map[buildVersion] = associatedTestsList.toMutableList()
                logger.debug {
                    "Add new list of tests associates with build version $buildVersion. " +
                            "Added ${associatedTestsList.count()} tests"
                }
            }
            else -> {
                map[buildVersion]?.addAll(associatedTestsList)
                logger.debug { "Added ${associatedTestsList.count()} tests" }
            }
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
    ): Map<String, List<String>> {

        logger.debug { "Getting tests to run" }
        val scopes = agentState.scopeManager.enabledScopes()
        val scopesInBuild = scopes.filter { it.buildVersion == agentState.agentInfo.buildVersion }

        val result = testsAssociatedWithMethods(javaMethods, agentState.prevBuildVersion)
            ?.flatMap { it.tests }
            ?.filter { scopes.typedTests().contains(it) && !(scopesInBuild.typedTests().contains(it)) }
            ?.toSet()
            ?.groupBy({ it.type }, { it.name })
            .orEmpty()

        logger.debug { "Tests to run count ${result.count()}" }
        return result
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
