package com.epam.drill.plugins.test2code

import com.epam.drill.common.*
import com.epam.drill.plugin.api.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.coverage.*
import com.epam.drill.plugins.test2code.jvm.*
import com.epam.drill.plugins.test2code.storage.*
import com.epam.drill.plugins.test2code.util.*
import com.epam.kodux.*
import kotlinx.atomicfu.*
import org.jacoco.core.internal.data.*

/**
 * Agent state.
 * The state itself holds only an atomic reference to the data.
 * The data is represented by the sealed class hierarchy AgentData.
 * In case of inconsistencies of the data a ClassCastException is thrown.
 */

internal const val DEFAULT_SCOPE_NAME = "New Scope"

internal class AgentState(
    val storeClient: StoreClient,
    val agentInfo: AgentInfo,
    val adminData: AdminData
) {

    val buildManager: BuildManager = adminData.buildManager

    val data get() = _data.value

    val scopeManager = ScopeManager(storeClient)

    val activeScope get() = _activeScope.value

    val qualityGateSettings = AtomicCache<String, ConditionSetting>()

    internal val builds: AtomicCache<String, CachedBuild> = AtomicCache()

    private val buildInfo: BuildInfo? get() = buildManager[agentInfo.buildVersion]

    private val _data = atomic<AgentData>(NoData)

    private val _activeScope = atomic(ActiveScope(buildVersion = agentInfo.buildVersion))

    fun init() = _data.update { DataBuilder() }

    suspend fun initialized() {
        val parentVersion = buildInfo?.parentVersion
        val parentClassData = parentVersion?.run {
            takeIf(String::any)?.let { classData(it) }
        }
        val classData = _data.updateAndGet { data ->
            when (data) {
                is DataBuilder -> data.flatMap { e -> e.methods.map { e to it } }.run {
                    val methods = map { (e, m) ->
                        Method(
                            ownerClass = "${e.path}/${e.name}",
                            name = m.name,
                            desc = m.toDesc(),
                            hash = m.checksum
                        )
                    }.sorted()
                    val packages = data.toPackages()
                    PackageTree(
                        totalCount = sumBy { it.second.count },
                        totalMethodCount = count(),
                        totalClassCount = packages.sumBy { it.totalClassesCount },
                        packages = packages
                    ).toClassData(
                        methods = methods,
                        otherMethods = parentClassData?.methods
                    )
                }
                is ClassData -> data
                is NoData -> {
                    val classBytes = adminData.classBytes
                    val probeIds: Map<String, Long> = classBytes.mapValues { CRC64.classId(it.value) }
                    val bundleCoverage = classBytes.keys.bundle(classBytes, probeIds)
                    val sortedPackages = bundleCoverage.packages.asSequence().run {
                        mapNotNull { pc ->
                            val classes = pc.classes.filter { it.methods.any() }
                            if (classes.any()) {
                                pc.copy(classes = classes.sortedBy(ClassCounter::name))
                            } else null
                        }.sortedBy(PackageCounter::name)
                    }.toList()
                    val classCounters = sortedPackages.asSequence().flatMap {
                        it.classes.asSequence()
                    }
                    val groupedMethods = classCounters.associate { classCounter ->
                        val name = classCounter.fullName
                        val bytes = classBytes.getValue(name)
                        name to classCounter.parseMethods(bytes).sorted()
                    }
                    val methods = groupedMethods.flatMap { it.value }
                    val packages = sortedPackages.toPackages(groupedMethods)
                    PackageTree(
                        totalCount = packages.sumBy { it.totalCount },
                        totalMethodCount = groupedMethods.values.sumBy { it.count() },
                        totalClassCount = packages.sumBy { it.totalClassesCount },
                        packages = packages
                    ).toClassData(
                        methods = methods,
                        otherMethods = parentClassData?.methods,
                        probeIds = probeIds
                    )
                }
            }
        } as ClassData
        initActiveScope()
        val buildVersion = agentInfo.buildVersion
        builds[buildVersion] = storeClient.loadBuild(buildVersion) ?: CachedBuild(buildVersion)
        parentVersion?.let {
            storeClient.loadBuild(it)
        }?.also { builds[it.version] = it }
        classData.store(storeClient)
    }

    internal fun updateProbes(
        buildVersion: String,
        buildScopes: Sequence<FinishedScope>
    ) {
        builds(buildVersion) {
            it?.copy(probes = buildScopes.flatten().flatten().merge())
        }
    }

    internal fun updateBundleCounters(
        buildVersion: String,
        bundleCounters: BundleCounters
    ) = builds(buildVersion) {
        it?.copy(bundleCounters = bundleCounters)
    }

    internal fun updateBuildTests(
        buildVersion: String,
        tests: List<AssociatedTests>
    ): CachedBuild = builds(buildVersion) {
        it?.copy(
            tests = it.tests.copy(assocTests = tests.toSet())
        )
    }!!

    internal fun updateTestsToRun(
        buildVersion: String,
        testsToRun: GroupedTests
    ): CachedBuild = builds(buildVersion) {
        it?.copy(tests = it.tests.copy(testsToRun = testsToRun))
    }!!

    internal fun updateBuildCoverage(
        buildVersion: String,
        buildCoverage: BuildCoverage
    ): CachedBuild = builds(buildVersion) {
        it?.copy(coverage = buildCoverage.toCachedBuildCoverage(buildVersion))
    }!!

    suspend fun storeBuild(buildVersion: String) {
        builds[buildVersion]?.store(storeClient)
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

    internal fun classDataOrNull(): ClassData? = _data.value as? ClassData

    internal suspend fun classData(
        buildVersion: String = agentInfo.buildVersion
    ): ClassData? = when (buildVersion) {
        agentInfo.buildVersion -> _data.value as? ClassData
        else -> storeClient.loadClassData(buildVersion)
    }

    private suspend fun initActiveScope() {
        readActiveScopeInfo()?.run {
            val sessions = storeClient.loadSessions(id)
            _activeScope.update {
                ActiveScope(
                    id = id,
                    nth = nth,
                    buildVersion = agentInfo.buildVersion,
                    name = name,
                    sessions = sessions
                ).apply {
                    updateSummary {
                        it.copy(started = startedAt)
                    }
                }
            }
        } ?: storeActiveScopeInfo()
    }

    fun changeActiveScope(name: String): ActiveScope = _activeScope.getAndUpdate {
        ActiveScope(nth = it.nth.inc(), name = scopeName(name), buildVersion = agentInfo.buildVersion)
    }.apply { close() }

    suspend fun readActiveScopeInfo(): ActiveScopeInfo? = scopeManager.counter(agentInfo.buildVersion)

    suspend fun storeActiveScopeInfo() = scopeManager.storeCounter(
        activeScope.run {
            ActiveScopeInfo(
                buildVersion = buildVersion,
                id = id,
                nth = nth,
                name = name,
                startedAt = summary.started
            )
        }
    )

    private fun scopeName(name: String) = when (val trimmed = name.trim()) {
        "" -> "$DEFAULT_SCOPE_NAME ${activeScope.nth + 1}"
        else -> trimmed
    }

    private fun PackageTree.toClassData(
        methods: List<Method>,
        otherMethods: List<Method>?,
        probeIds: Map<String, Long> = emptyMap()
    ) = ClassData(
        buildVersion = agentInfo.buildVersion,
        packageTree = this,
        methods = methods,
        methodChanges = otherMethods?.let(methods::diff) ?: DiffMethods(),
        probeIds = probeIds
    )
}

internal suspend fun AgentState.coverContext(
    buildVersion: String = agentInfo.buildVersion
): CoverContext = classData(buildVersion)!!.let { classData ->
    CoverContext(
        agentType = agentInfo.agentType,
        packageTree = classData.packageTree,
        methods = classData.methods,
        methodChanges = classData.methodChanges,
        probeIds = classData.probeIds,
        classBytes = adminData.classBytes,
        build = builds[buildVersion]
    )
}
