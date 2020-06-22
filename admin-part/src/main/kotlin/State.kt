package com.epam.drill.plugins.test2code

import com.epam.drill.common.*
import com.epam.drill.plugin.api.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.storage.*
import com.epam.kodux.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import kotlinx.serialization.protobuf.*
import org.jacoco.core.internal.data.*

/**
 * Agent state.
 * The state itself holds only an atomic reference to the data.
 * The data is represented by the sealed class hierarchy AgentData.
 * In case of inconsistencies of the data a ClassCastException is thrown.
 */

internal const val DEFAULT_SCOPE_NAME = "New Scope"

class PluginInstanceState(
    val storeClient: StoreClient,
    val agentInfo: AgentInfo,
    val buildManager: BuildManager
) {

    val data get() = _data.value

    val buildInfo: BuildInfo? get() = buildManager[agentInfo.buildVersion]

    val scopeManager = ScopeManager(storeClient)

    val activeScope get() = _activeScope.value

    val coverages = AtomicCache<AgentBuildId, StoredBuildCoverage>()

    val buildTests = AtomicCache<AgentBuildId, BuildTests>()

    private val agentBuildId = AgentBuildId(agentId = agentInfo.id, buildVersion = agentInfo.buildVersion)

    private val _data = atomic<AgentData>(NoData)

    private val _activeScope = atomic(ActiveScope(buildVersion = agentInfo.buildVersion))

    fun init() = _data.update { DataBuilder() }

    suspend fun initialized() {
        val classesData = _data.updateAndGet { data ->
            when (data) {
                is DataBuilder -> data.flatMap { e -> e.methods.map { e to it } }.run {
                    val methods = map { (e, m) ->
                        Method(
                            ownerClass = "${e.path}/${e.name}",
                            name = m.name,
                            desc = "(${m.params.joinToString(",")}):${m.returnType}",
                            hash = "".crc64
                        )
                    }
                    PackageTree(
                        totalCount = sumBy { it.second.count },
                        totalMethodCount = count(),
                        packages = data.toPackages()
                    ).toClassesData(MethodChanges(mapOf(DiffType.UNAFFECTED to methods)))
                }
                is ClassData -> data
                is NoData -> {
                    val classesBytes = buildInfo?.classesBytes ?: emptyMap()
                    val javaMethods = buildInfo?.javaMethods ?: emptyMap()
                    val probeIds: Map<String, Long> = classesBytes.mapValues { CRC64.classId(it.value) }
                    val bundleCoverage = classesBytes.bundle(probeIds)
                    val packages = bundleCoverage.toPackages(javaMethods)
                    PackageTree(
                        totalCount = packages.sumBy { it.totalCount },
                        totalMethodCount = javaMethods.values.sumBy { it.count() },
                        packages = packages
                    ).toClassesData(
                        methodChanges = buildInfo?.methodChanges ?: MethodChanges(),
                        probeIds = probeIds
                    )
                }
            }
        } as ClassData
        readScopeCounter()?.run {
            _activeScope.update { ActiveScope(nth = count.inc(), buildVersion = agentInfo.buildVersion) }
        }
        storeScopeCounter()
        storeClient.executeInAsyncTransaction {
            store(classesData.copy(packageTree = PackageTree()))
            store(
                PackageTreeBytes(
                    buildVersion = classesData.buildVersion,
                    bytes = ProtoBuf.dump(PackageTree.serializer(), classesData.packageTree)
                )
            )
            if (classesData.probeIds.any()) {
                store(
                    ProbeIdBytes(
                        buildVersion = classesData.buildVersion,
                        bytes = ProtoBuf.dump(ProbeIdData.serializer(), ProbeIdData(classesData.probeIds))
                    )
                )
            }
            getAll<StoredBuildCoverage>().forEach { stored ->
                coverages(stored.id) { stored }
            }
            getAll<StoredBuildTests>().forEach { stored ->
                buildTests(stored.id) {
                    ProtoBuf.load(BuildTests.serializer(), stored.data)
                }
            }
        }
    }

    fun buildId(buildVersion: String) = when (buildVersion) {
        agentInfo.buildVersion -> agentBuildId
        else -> agentBuildId.copy(buildVersion = buildVersion)
    }

    fun updateBuildTests(
        buildVersion: String,
        tests: List<AssociatedTests>
    ) = buildId(buildVersion).let { buildId ->
        buildTests[buildId] = buildTests[buildId]?.run {
            copy(assocTests = assocTests.toPersistentSet().addAll(tests))
        } ?: BuildTests(assocTests = tests.toPersistentSet())
    }

    fun updateTestsToRun(
        buildVersion: String,
        testsToRun: GroupedTests
    ) = buildId(buildVersion).let { buildId ->
        buildTests[buildId] = buildTests[buildId]?.run {
            copy(testsToRun = testsToRun)
        } ?: BuildTests(testsToRun = testsToRun)
    }

    fun updateBuildCoverage(
        buildVersion: String,
        buildCoverage: BuildCoverage,
        risks: Risks
    ) {
        val id = buildId(buildVersion)
        val coverage = StoredBuildCoverage(
            id = id,
            count = buildCoverage.count,
            arrow = buildCoverage.arrow?.name,
            risks = risks.run { newMethods.count() + modifiedMethods.count() }
        )
        coverages[id] = coverage
    }

    suspend fun storeBuildCoverage(
        buildVersion: String
    ) {
        val id = buildId(buildVersion)
        val coverage = coverages[id]
        val tests = buildTests[id]
        storeClient.executeInAsyncTransaction {
            coverage?.let { store(it) }
            tests?.let { tests ->
                val data = ProtoBuf.dump(BuildTests.serializer(), tests)
                store(StoredBuildTests(id, data))
            }
        }
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

    private fun PackageTree.toClassesData(
        methodChanges: MethodChanges,
        probeIds: Map<String, Long> = emptyMap()
    ) = ClassData(
        buildVersion = agentInfo.buildVersion,
        packageTree = this,
        methodChanges = methodChanges,
        probeIds = probeIds
    )
}

internal suspend fun StoreClient.classData(buildVersion: String): ClassData? = executeInAsyncTransaction {
    findById<ClassData>(buildVersion)?.run {
        val foundPackageTree = findById<PackageTreeBytes>(buildVersion)?.run {
            ProtoBuf.load(PackageTree.serializer(), bytes)
        }
        val foundProbeIds = findById<ProbeIdBytes>(buildVersion)?.run {
            ProtoBuf.load(ProbeIdData.serializer(), bytes).map
        }
        val treeMutator: (ClassData) -> ClassData = { foundPackageTree?.run { it.copy(packageTree = this) } ?: it }
        val probeIdsMutator: (ClassData) -> ClassData = { foundProbeIds?.run { it.copy(probeIds = this) } ?: it }
        let(treeMutator).let(probeIdsMutator)
    }
}
