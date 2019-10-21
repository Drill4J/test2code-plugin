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

    private fun testsAssociatedWithBuild(buildVersion: String): List<TypedTest> {
        return map[buildVersion]?.flatMap { it -> it.tests }.orEmpty()
    }

    override suspend fun getTestsAssociatedWithMethods(
        buildVersion: String,
        agentState: AgentState,
        javaMethods: List<JavaMethod>
    ) = map[previousBuildVersion(buildVersion, agentState)]
        ?.filter { test ->
            javaMethods.any { it.ownerClass == test.className && it.name == test.methodName }
        }
        ?.flatMap { it -> it.tests }.orEmpty()
        .filter { !testsAssociatedWithBuild(buildVersion).contains(it) }
        .toSet()
        .groupBy({ it.type }, { it.name })

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