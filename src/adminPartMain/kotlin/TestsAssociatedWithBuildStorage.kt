package com.epam.drill.plugins.coverage

import java.util.concurrent.ConcurrentHashMap

interface TestsAssociatedWithBuild {
    fun add(buildVersion: String, associatedTestsList: List<AssociatedTests>)
    fun getTestsAssociatedWithMethods(agentState: AgentState, javaMethods: List<JavaMethod>): List<String>
}

class MutableMapTestsAssociatedWithBuild : TestsAssociatedWithBuild {

    private val map: MutableMap<String, MutableSet<AssociatedTests>> = ConcurrentHashMap()

    override fun add(buildVersion: String, associatedTestsList: List<AssociatedTests>) {
        when {
            map[buildVersion].isNullOrEmpty() -> map[buildVersion] = associatedTestsList.toMutableSet()
            else -> map[buildVersion]?.addAll(associatedTestsList)
        }
    }

    override fun getTestsAssociatedWithMethods(agentState: AgentState, javaMethods: List<JavaMethod>) =
        map[previousBuildVersion(agentState)]?.filter { test ->
            javaMethods.any { it.ownerClass == test.className && it.name == test.methodName }
        }?.flatMap { it -> it.tests }.orEmpty()

    private fun previousBuildVersion(agentState: AgentState): String {
        return agentState.classesData().prevAgentInfo?.buildVersion.toString()
    }
}

object StorageManager {
    private val storage: MutableMap<String, TestsAssociatedWithBuild> = ConcurrentHashMap()

    fun getStorage(
        id: String,
        storageImplementation: TestsAssociatedWithBuild
    ): TestsAssociatedWithBuild = storage[id] ?: addStorage(id, storageImplementation)


    private fun addStorage(
        id: String,
        testsAssociatedWithBuild: TestsAssociatedWithBuild
    ): TestsAssociatedWithBuild = testsAssociatedWithBuild.also { storage[id] = testsAssociatedWithBuild }
}
