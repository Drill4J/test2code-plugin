package com.epam.drill.plugins.test2code

import com.epam.drill.common.*
import com.epam.drill.plugin.api.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.drill.plugins.test2code.storage.*
import com.epam.kodux.*
import kotlinx.atomicfu.*

/**
 * Agent state.
 * The state itself holds only an atomic reference to the data.
 * The data is represented by the sealed class hierarchy AgentData.
 * In case of inconsistencies of the data a ClassCastException is thrown.
 */
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

    private val _scopeCounter = atomic(0)

    private val _activeScope = atomic(ActiveScope(scopeName(), agentInfo.buildVersion))

    fun init() = _data.update { DataBuilder() }

    suspend fun initialized() {
        val classesData = _data.updateAndGet { data ->
            when (data) {
                is DataBuilder -> PackageTree(
                    totalCount = data.flatMap(AstEntity::methods).sumBy(AstMethod::count),
                    packages = data.toPackages()
                ).toClassesData()
                is ClassesData -> data
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
        } as ClassesData
        storeClient.store(classesData)
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
        val trimmedNewName = newName.trim()
        if (id == activeScope.id) activeScope.rename(trimmedNewName)
        else scopeManager.byId(id)?.apply {
            scopeManager.store(this.copy(name = newName, summary = this.summary.copy(name = trimmedNewName)))
        }
    }

    suspend fun toggleScope(id: String) {
        scopeManager.byId(id)?.apply {
            scopeManager.store(this.copy(enabled = !enabled, summary = this.summary.copy(enabled = !enabled)))
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
        agentInfo.buildVersion -> data as? ClassesData
        else -> storeClient.classesData(buildVersion)
    } ?: NoData


    fun changeActiveScope(name: String): ActiveScope = run {
        _activeScope.getAndUpdate { prevScope ->
            prevScope.close()
            ActiveScope(scopeName(name), agentInfo.buildVersion)
        }
    }

    private fun scopeName(name: String = "") = when (val trimmed = name.trim()) {
        "" -> "New Scope ${_scopeCounter.incrementAndGet()}"
        else -> trimmed
    }

    private fun PackageTree.toClassesData() = ClassesData(
        buildVersion = agentInfo.buildVersion,
        prevBuildVersion = prevBuildVersion,
        prevBuildCoverage = lastPrevBuildCoverage,
        packageTree = this
    )
}


private suspend fun StoreClient.classesData(buildVersion: String) = findBy<ClassesData> {
    ClassesData::buildVersion eq buildVersion
}.firstOrNull()

suspend fun StoreClient.readLastBuildCoverage(agentId: String, buildVersion: String): LastBuildCoverage? {
    return findById(lastCoverageId(agentId, buildVersion))
}

private fun lastCoverageId(agentId: String, buildVersion: String) = "$agentId:$buildVersion"
