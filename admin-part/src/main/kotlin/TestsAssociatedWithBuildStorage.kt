package com.epam.drill.plugins.test2code

import com.epam.kodux.*
import kotlinx.serialization.*
import java.util.concurrent.*
import kotlin.collections.set

interface TestsAssociatedWithBuild {
    suspend fun add(buildVersion: String, associatedTestsList: List<AssociatedTests>)

    suspend fun getTestsToRun(
        pluginInstanceState: PluginInstanceState,
        javaMethods: List<JavaMethod>
    ): Map<String, List<String>>

    suspend fun deletedCoveredMethodsCount(
        buildVersion: String,
        pluginInstanceState: PluginInstanceState,
        deletedMethods: List<JavaMethod>
    ): Int
}

interface TestsAssociatedWithBuildStorageManager {
    suspend fun getStorage(agentId: String, storageImplementation: TestsAssociatedWithBuild): TestsAssociatedWithBuild
}

class MutableMapTestsAssociatedWithBuild : TestsAssociatedWithBuild {

    private val map: MutableMap<String, MutableList<AssociatedTests>> = ConcurrentHashMap()

    private fun testsAssociatedWithMethods(
        methods: List<JavaMethod>,
        buildVersion: String
    ) = map[buildVersion]?.filter { test ->
        methods.any { method -> method.ownerClass == test.className && method.name == test.methodName }
    }

    override suspend fun add(buildVersion: String, associatedTestsList: List<AssociatedTests>) {
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
        pluginInstanceState: PluginInstanceState,
        deletedMethods: List<JavaMethod>
    ): Int {
        return testsAssociatedWithMethods(
            deletedMethods,
            pluginInstanceState.prevBuildVersion
        )
            ?.toSet()
            ?.count() ?: 0
    }

    override suspend fun getTestsToRun(
        pluginInstanceState: PluginInstanceState,
        javaMethods: List<JavaMethod>
    ): Map<String, List<String>> {
        val scopes = pluginInstanceState.scopeManager.enabledScopes()
        val scopesInBuild = scopes.filter { it.buildVersion == pluginInstanceState.agentInfo.buildVersion }

        return testsAssociatedWithMethods(javaMethods, pluginInstanceState.prevBuildVersion)
            ?.flatMap { it.tests }
            ?.filter { scopes.typedTests().contains(it) && !(scopesInBuild.typedTests().contains(it)) }
            ?.toSet()
            ?.groupBy({ it.type }, { it.name })
            .orEmpty()
    }
}

@Serializable
class KoduxTestsAssociatedWithBuild(
    @Id
    val id: String,
    @Transient
    private val storeClient: StoreClient? = null
) : TestsAssociatedWithBuild {

    val map: MutableMap<String, List<AssociatedTests>> = ConcurrentHashMap()

    private fun testsAssociatedWithMethods(
        methods: List<JavaMethod>,
        buildVersion: String
    ) = map[buildVersion]?.filter { test ->
        methods.any { method -> method.ownerClass == test.className && method.name == test.methodName }
    }

    override suspend fun add(buildVersion: String, associatedTestsList: List<AssociatedTests>) {
        val existingList = map[buildVersion] ?: emptyList()
        map[buildVersion] = existingList + associatedTestsList
        storeClient?.store(this)
    }

    private fun Collection<FinishedScope>.typedTests() = flatMap {
        it.probes.values.flatMap { value ->
            value.flatMap { finishedSession -> finishedSession.testNames }
        }
    }.toSet()

    override suspend fun deletedCoveredMethodsCount(
        buildVersion: String,
        pluginInstanceState: PluginInstanceState,
        deletedMethods: List<JavaMethod>
    ): Int {
        return testsAssociatedWithMethods(
            deletedMethods,
            pluginInstanceState.prevBuildVersion
        )
            ?.toSet()
            ?.count() ?: 0
    }

    override suspend fun getTestsToRun(
        pluginInstanceState: PluginInstanceState,
        javaMethods: List<JavaMethod>
    ): Map<String, List<String>> {
        val scopes = pluginInstanceState.scopeManager.enabledScopes()
        val scopesInBuild = scopes.filter { it.buildVersion == pluginInstanceState.agentInfo.buildVersion }

        return testsAssociatedWithMethods(javaMethods, pluginInstanceState.prevBuildVersion)
            ?.flatMap { it.tests }
            ?.filter { scopes.typedTests().contains(it) && !(scopesInBuild.typedTests().contains(it)) }
            ?.toSet()
            ?.groupBy({ it.type }, { it.name })
            .orEmpty()
    }
}

object MutableMapTestsAssociatedWithBuildStorageManager : TestsAssociatedWithBuildStorageManager {
    private val storage: MutableMap<String, TestsAssociatedWithBuild> = ConcurrentHashMap()

    override suspend fun getStorage(
        agentId: String,
        storageImplementation: TestsAssociatedWithBuild
    ): TestsAssociatedWithBuild = storage[agentId] ?: addStorage(agentId, storageImplementation)


    private fun addStorage(
        agentId: String,
        testsAssociatedWithBuild: TestsAssociatedWithBuild
    ): TestsAssociatedWithBuild = testsAssociatedWithBuild.apply { storage[agentId] = testsAssociatedWithBuild }
}

class KoduxTestsAssociatedWithBuildStorageManager(private val storeClient: StoreClient) :
    TestsAssociatedWithBuildStorageManager {
    override suspend fun getStorage(
        agentId: String,
        storageImplementation: TestsAssociatedWithBuild
    ): TestsAssociatedWithBuild = storeClient.findById<KoduxTestsAssociatedWithBuild>(agentId)
        ?: addStorage(storageImplementation)


    private suspend fun addStorage(
        testsAssociatedWithBuild: TestsAssociatedWithBuild
    ): TestsAssociatedWithBuild = storeClient.store(testsAssociatedWithBuild as KoduxTestsAssociatedWithBuild)
}
