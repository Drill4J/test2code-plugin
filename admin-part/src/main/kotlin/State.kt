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
import mu.*
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
    private val logger = KotlinLogging.logger("AgentState ${agentInfo.id}")

    val buildManager: BuildManager = adminData.buildManager

    val data get() = _data.value

    val scopeManager = ScopeManager(storeClient)

    val activeScope get() = _activeScope.value

    val qualityGateSettings = AtomicCache<String, ConditionSetting>()

    private val buildInfo: BuildInfo? get() = buildManager[agentInfo.buildVersion]

    private val _data = atomic<AgentData>(NoData)

    private val _coverContext = atomic<CoverContext?>(null)

    private val _activeScope = atomic(ActiveScope(buildVersion = agentInfo.buildVersion))

    suspend fun loadFromDb(block: suspend () -> Unit = {}) {
        storeClient.loadClassData(agentInfo.buildVersion)?.let { classData ->
            _data.value = classData
            initialized(classData)
            block()
        }
    }

    fun init() = _data.update {
        _coverContext.value = null
        DataBuilder()
    }

    fun close() {
        activeScope.close()
    }

    suspend fun initialized(block: suspend () -> Unit = {}) {
        _data.getAndUpdate {
            when (it) {
                is ClassData -> it
                else -> ClassData(agentInfo.buildVersion)
            }
        }.takeIf { it !is ClassData }?.also { data ->
            val classData = when (data) {
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
                    ).toClassData(methods = methods)
                }
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
                    ).toClassData(methods = methods, probeIds = probeIds)
                }
                else -> data
            } as ClassData
            classData.store(storeClient)
            initialized(classData)
            block()
        }
    }

    private suspend fun initialized(classData: ClassData) {
        val buildVersion = agentInfo.buildVersion
        val build: CachedBuild = storeClient.loadBuild(buildVersion) ?: CachedBuild(buildVersion)
        val coverContext = CoverContext(
            agentType = agentInfo.agentType,
            packageTree = classData.packageTree,
            methods = classData.methods,
            probeIds = classData.probeIds,
            classBytes = adminData.classBytes,
            build = build
        )
        _coverContext.value = coverContext
        buildInfo?.parentVersion?.takeIf(String::any)?.let { parentVersion ->
            storeClient.loadClassData(parentVersion)?.let { parentClassData ->
                val methodChanges = classData.methods.diff(parentClassData.methods)
                val parentBuild = storeClient.loadBuild(parentVersion)
                val testsToRun = parentBuild?.run {
                    bundleCounters.testsWith(methodChanges.modified)
                }.orEmpty()
                val deletedWithCoverage: Map<Method, Count> = parentBuild?.run {
                    bundleCounters.all.coveredMethods(methodChanges.deleted)
                }.orEmpty()
                _coverContext.value = coverContext.copy(
                    methodChanges = methodChanges.copy(deletedWithCoverage = deletedWithCoverage),
                    parentBuild = parentBuild,
                    testsToRun = testsToRun
                )
            }
        }
        initActiveScope()
    }

    internal suspend fun finishSession(
        sessionId: String
    ): FinishedSession? = activeScope.finishSession(sessionId)?.also {
        if (it.any()) {
            storeClient.storeSession(activeScope.id, it)
            logger.debug { "Session $sessionId finished." }
        } else logger.debug { "Session with id $sessionId is empty, it won't be added to the active scope." }
    }

    internal fun updateProbes(
        buildScopes: Sequence<FinishedScope>
    ) {
        _coverContext.update {
            it?.copy(build = it.build.copy(probes = buildScopes.flatten().flatten().merge()))
        }
    }

    internal fun updateBundleCounters(
        bundleCounters: BundleCounters
    ): CachedBuild = updateBuild {
        copy(bundleCounters = bundleCounters)
    }

    internal fun updateBuildTests(
        tests: GroupedTests,
        assocTests: List<AssociatedTests>
    ): CachedBuild = updateBuild {
        copy(
            tests = this.tests.copy(
                tests = tests,
                assocTests = assocTests.toSet()
            )
        )
    }

    internal fun updateBuildCoverage(
        buildVersion: String,
        buildCoverage: BuildCoverage
    ): CachedBuild = updateBuild {
        copy(coverage = buildCoverage.toCachedBuildCoverage(buildVersion))
    }

    private fun updateBuild(
        updater: CachedBuild.() -> CachedBuild
    ): CachedBuild = _coverContext.updateAndGet {
        it?.copy(build = it.build.updater())
    }!!.build

    suspend fun storeBuild() {
        _coverContext.value?.build?.store(storeClient)
    }

    suspend fun renameScope(id: String, newName: String) {
        when (id) {
            activeScope.id -> activeScope.rename(newName.trim()).also { storeActiveScopeInfo() }
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

    internal fun coverContext(): CoverContext = _coverContext.value!!

    internal fun classDataOrNull(): ClassData? = _data.value as? ClassData

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

    private suspend fun readActiveScopeInfo(): ActiveScopeInfo? = scopeManager.counter(agentInfo.buildVersion)

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
        probeIds: Map<String, Long> = emptyMap()
    ) = ClassData(
        buildVersion = agentInfo.buildVersion,
        packageTree = this,
        methods = methods,
        probeIds = probeIds
    )
}
