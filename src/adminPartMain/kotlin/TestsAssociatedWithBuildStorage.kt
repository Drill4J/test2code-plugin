package com.epam.drill.plugins.coverage

import java.util.concurrent.ConcurrentHashMap

object TestsAssociatedWithBuildStorageImpl : TestsAssociatedWithBuildStorage {

    private val map: MutableMap<String, MutableSet<AssociatedTests>> = ConcurrentHashMap()

    override fun getStorage() = this

    override fun add(buildVersion: String, associatedTestsList: List<AssociatedTests>) {
        when {
            map[buildVersion].isNullOrEmpty() -> map[buildVersion] = associatedTestsList.toMutableSet()
            else -> map[buildVersion]?.addAll(associatedTestsList)
        }
    }

    override fun getTestsAssociatedWithMethods(agentState: AgentState, javaMethods: List<JavaMethod>) =
        map[previousBuildVersion(agentState)]?.filter { test ->
            javaMethods.any { it.ownerClass == test.className && it.name == test.methodName }
        }?.flatMap { it -> it.tests }

    override fun previousBuildVersion(agentState: AgentState): String {
        return agentState.classesData().prevAgentInfo?.buildVersion.toString()
    }
}

interface TestsAssociatedWithBuildStorage {
    fun add(buildVersion: String, associatedTestsList: List<AssociatedTests>)
    fun getTestsAssociatedWithMethods(agentState: AgentState, javaMethods: List<JavaMethod>): List<String>?
    fun getStorage(): TestsAssociatedWithBuildStorage
    fun previousBuildVersion(agentState: AgentState): String
}

val instanceOfStorageTestsAssociatedWithBuild = TestsAssociatedWithBuildStorageImpl.getStorage()