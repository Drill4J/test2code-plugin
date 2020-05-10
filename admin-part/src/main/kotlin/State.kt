package com.epam.drill.plugins.test2code

import com.epam.drill.common.*
import com.epam.drill.plugin.api.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.drill.plugins.test2code.storage.*
import com.epam.kodux.*
import kotlinx.atomicfu.*
import kotlinx.serialization.protobuf.*

/**
 * Agent state.
 * The state itself holds only an atomic reference to the data.
 * The data is represented by the sealed class hierarchy AgentData.
 * In case of inconsistencies of the data a ClassCastException is thrown.
 */

internal const val DEFAULT_SCOPE_NAME = "New Scope"

class PluginInstanceState(
    val storeClient: StoreClient,
    val prevBuildVersion: String,
    val lastPrevBuildCoverage: Double,
    val agentInfo: AgentInfo,
    val buildManager: BuildManager
) {

    val data get() = _data.value

    val buildInfo: BuildInfo? get() = buildManager[agentInfo.buildVersion]

    val scopeManager = ScopeManager(storeClient)

    val activeScope get() = _activeScope.value

    val buildTests get() = _buildTests.value

    private val _data = atomic<AgentData>(NoData)

    private val _buildTests = atomic(BuildTests(agentInfo.id))

    private val _activeScope = atomic(ActiveScope(buildVersion = agentInfo.buildVersion))

    private val agentBuildId = AgentBuildId(agentId = agentInfo.id, buildVersion = agentInfo.buildVersion)

    fun init() = _data.update { DataBuilder() }

    suspend fun initialized() {
        val classesData = _data.updateAndGet { data ->
            when (data) {
                is DataBuilder -> PackageTree(
                    totalCount = data.flatMap(AstEntity::methods).sumBy(AstMethod::count),
                    packages = data.toPackages()
                ).toClassesData()
                is ClassData -> data
                is NoData -> {
                    val classesBytes = buildInfo?.classesBytes ?: emptyMap()
                    val javaMethods = buildInfo?.javaMethods ?: emptyMap()
                    val bundleCoverage = classesBytes.bundle()
                    val packages = bundleCoverage.toPackages(javaMethods)
                    PackageTree(
                        totalCount = packages.sumBy { it.totalCount },
                        packages = packages
                    ).toClassesData()
                }
            }
        } as ClassData
        readScopeCounter()?.run {
            _activeScope.update { ActiveScope(nth = count.inc(), buildVersion = agentInfo.buildVersion) }
        }
        storeScopeCounter()
        storeClient.executeInAsyncTransaction {
            store(classesData.copy(packageTree = PackageTree(0, emptyList())))
            store(
                PackageTreeBytes(
                    buildVersion = classesData.buildVersion,
                    bytes = ProtoBuf.dump(PackageTree.serializer(), classesData.packageTree)
                )
            )
        }
        val tests: BuildTests = storeClient.run { findById(agentInfo.id) ?: store(buildTests) }
        _buildTests.value = tests
    }

    suspend fun addBuildTests(buildVersion: String, tests: List<AssociatedTests>) {
        buildTests.add(buildVersion, tests)
        storeClient.store(buildTests)
    }

    suspend fun storeBuildCoverage(buildCoverage: BuildCoverage, risks: Risks, testsToRun: GroupedTests) {
        storeClient.store(
            LastBuildCoverage(
                id = lastCoverageId(agentInfo.id, agentInfo.buildVersion),
                coverage = buildCoverage.ratio,
                arrow = buildCoverage.arrow?.name,
                risks = risks.run { newMethods.count() + modifiedMethods.count() },
                testsToRun = TestsToRunDto(
                    groupedTests = testsToRun,
                    count = testsToRun.totalCount()
                )
            )
        )
    }

    suspend fun renameScope(id: String, newName: String) {
        when (id) {
            activeScope.id -> activeScope.rename(newName.trim())
            else -> scopeManager.byId(id)?.apply {
                scopeManager.store(copy(name = newName, summary = this.summary.copy(name = newName.trim())))
            }
        }
    }

    suspend fun toggleScope(id: String) {
        scopeManager.byId(id)?.apply {
            scopeManager.store(copy(enabled = !enabled, summary = this.summary.copy(enabled = !enabled)))
        }
    }

    suspend fun scopeByName(name: String): Scope? = when (name) {
        activeScope.name -> activeScope
        else -> scopeManager.byVersion(agentInfo.buildVersion).firstOrNull { it.name == name }
    }

    suspend fun scopeById(id: String): Scope? = when (id) {
        activeScope.id -> activeScope
        else -> scopeManager.byId(id)
    }

    suspend fun classesData(buildVersion: String = agentInfo.buildVersion): AgentData = when (buildVersion) {
        agentInfo.buildVersion -> data as? ClassData
        else -> storeClient.classData(buildVersion)
    } ?: NoData

    fun changeActiveScope(name: String): ActiveScope = _activeScope.getAndUpdate {
        ActiveScope(it.nth.inc(), scopeName(name), agentInfo.buildVersion)
    }.apply { close() }

    suspend fun readScopeCounter(): ScopeCounter? = scopeManager.counter(agentBuildId)

    suspend fun storeScopeCounter() = scopeManager.storeCounter(
        ScopeCounter(agentBuildId, activeScope.nth)
    )

    private fun scopeName(name: String) = when (val trimmed = name.trim()) {
        "" -> "$DEFAULT_SCOPE_NAME ${activeScope.nth + 1}"
        else -> trimmed
    }

    private fun PackageTree.toClassesData() = ClassData(
        buildVersion = agentInfo.buildVersion,
        prevBuildVersion = prevBuildVersion,
        prevBuildCoverage = lastPrevBuildCoverage,
        packageTree = this
    )
}

private suspend fun StoreClient.classData(buildVersion: String): ClassData? = executeInAsyncTransaction {
    findById<ClassData>(buildVersion)?.run {
        findById<PackageTreeBytes>(buildVersion)?.run {
            ProtoBuf.load(PackageTree.serializer(), bytes)
        }?.let { copy(packageTree = it) } ?: this
    }
}

suspend fun StoreClient.readLastBuildCoverage(agentId: String, buildVersion: String): LastBuildCoverage? {
    return findById(lastCoverageId(agentId, buildVersion))
}

private fun lastCoverageId(agentId: String, buildVersion: String) = "$agentId:$buildVersion"
