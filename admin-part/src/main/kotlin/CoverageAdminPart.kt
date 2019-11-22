package com.epam.drill.plugins.coverage


import com.epam.drill.common.*
import com.epam.drill.plugin.api.*
import com.epam.drill.plugin.api.end.*
import com.epam.drill.plugin.api.message.*
import com.epam.drill.plugins.coverage.routes.*
import com.epam.kodux.*
import kotlinx.atomicfu.*
import kotlinx.serialization.*
import org.jacoco.core.analysis.*
import org.jacoco.core.data.*

@Suppress("unused", "MemberVisibilityCanBePrivate")
class CoverageAdminPart(
    adminData: AdminData,
    sender: Sender,
    val storeClient: StoreClient,
    agentInfo: AgentInfo,
    id: String
) : AdminPluginPart<Action>(adminData, sender, storeClient, agentInfo, id) {

    override val serDe: SerDe<Action> = commonSerDe

    private val _lastTestsToRun = atomic(TestsToRun(emptyMap()))

    internal var lastTestsToRun: TestsToRun
        get() = _lastTestsToRun.value
        set(value) {
            _lastTestsToRun.value = value
        }

    private val agentId = agentInfo.id

    private val buildVersion = agentInfo.buildVersion

    lateinit var pluginInstanceState: PluginInstanceState

    override suspend fun initialize() {
        pluginInstanceState = pluginInstanceState()
    }

    override suspend fun applyPackagesChanges() {
        wipeStoredData()
    }

    private val activeScope get() = pluginInstanceState.activeScope

    override suspend fun updateDataOnBuildConfigChange(buildVersion: String) {
        val next = pluginInstanceState.nextVersion(buildVersion)
        if (!next.isBlank())
            calculateAndSendBuildCoverage(next)
    }

    override suspend fun doAction(action: Action): Any {
        return when (action) {
            is SwitchActiveScope -> changeActiveScope(action.payload)
            is RenameScope -> renameScope(action.payload)
            is ToggleScope -> toggleScope(action.payload.scopeId)
            is DropScope -> dropScope(action.payload.scopeId)
            is StartNewSession -> {
                val startAgentSession = StartSession(
                    payload = StartSessionPayload(
                        sessionId = if (action.payload.sessionId.isEmpty()) genUuid() else action.payload.sessionId,
                        startPayload = action.payload
                    )
                )
                serDe.actionSerializer stringify startAgentSession
            }
            else -> Unit
        }
    }

    internal suspend fun renameScope(payload: RenameScopePayload) =
        when {
            pluginInstanceState.scopeNotExisting(payload.scopeId) ->
                StatusMessage(
                    StatusCodes.NOT_FOUND,
                    "Failed to rename scope with id ${payload.scopeId}: scope not found"
                )
            pluginInstanceState.scopeNameNotExisting(payload.scopeName, buildVersion) -> {
                pluginInstanceState.renameScope(payload.scopeId, payload.scopeName)
                val scope: Scope = pluginInstanceState.scopeManager.getScope(payload.scopeId) ?: activeScope
                sendScopeMessages(scope.buildVersion)
                sendScopeSummary(scope.summary, scope.buildVersion)
                StatusMessage(StatusCodes.OK, "Renamed scope with id ${payload.scopeId} -> ${payload.scopeName}")
            }
            else -> StatusMessage(
                StatusCodes.CONFLICT,
                "Scope with such name already exists. Please choose a different name."
            )
        }


    override suspend fun processData(dm: DrillMessage): Any {
        val content = dm.content
        val message = CoverMessage.serializer() parse content!!
        return processData(message)
    }

    override fun getPluginData(params: Map<String, String>): String {
        return when (params["type"]) {
            "tests-to-run" -> TestsToRun.serializer() stringify lastTestsToRun
            "recommendations" -> newBuildActionsList()
            else -> ""
        }
    }

    private fun newBuildActionsList(): String {
        val list = mutableListOf<String>()

        if (lastTestsToRun.testsToRun.isNotEmpty()) {
            list.add("Run recommended tests to cover modified methods")
        }

        if (newMethodsCount() > 0) {
            list.add("Update your tests to cover new methods")
        }
        return String.serializer().list stringify list
    }

    private fun newMethodsCount(): Int {
        return adminData.buildManager[buildVersion]?.buildSummary?.newMethods ?: 0
    }

    internal suspend fun processData(coverMsg: CoverMessage): Any {
        when (coverMsg) {
            is InitInfo -> {
                pluginInstanceState.init()
                println(coverMsg.message) //log init message
                println("${coverMsg.classesCount} classes to load")
            }
            is Initialized -> {
                pluginInstanceState.initialized(adminData.buildManager.buildInfos)
                val classesData = pluginInstanceState.classesData(buildVersion) as ClassesData
                cleanActiveScope(classesData.prevBuildVersion)
                calculateAndSendActiveScopeCoverage()
                calculateAndSendBuildCoverage()
                sendScopeMessages()
            }
            is SessionStarted -> {
                activeScope.startSession(coverMsg)
                println("Session ${coverMsg.sessionId} started.")
                sendActiveSessions()
            }
            is SessionCancelled -> {
                activeScope.cancelSession(coverMsg)
                println("Session ${coverMsg.sessionId} cancelled.")
                sendActiveSessions()
            }
            is CoverDataPart -> {
                activeScope.addProbes(coverMsg)
            }
            is SessionFinished -> {
                val scope = pluginInstanceState.activeScope
                when (val session = scope.finishSession(coverMsg)) {
                    null -> println("No active session for sessionId ${coverMsg.sessionId}")
                    else -> {
                        if (session.any()) {
                            val totalInstructions = (pluginInstanceState.classesData() as ClassesData).totalInstructions
                            val classesBytes = adminData.buildManager[buildVersion]?.classesBytes
                            scope.update(session, classesBytes, totalInstructions)
                            sendScopeMessages()
                        } else println("Session ${session.id} is empty, it won't be added to the active scope")
                        calculateAndSendActiveScopeCoverage()
                        println("Session ${session.id} finished.")
                    }
                }
            }
        }
        return ""
    }

    internal suspend fun calculateCoverageData(
        finishedSessions: Sequence<FinishedSession>,
        buildVersion: String = this.buildVersion,
        isBuildCvg: Boolean = false
    ): CoverageInfoSet {
        val buildInfo = adminData.buildManager[buildVersion]
        val classesBytes: ClassesBytes = buildInfo?.classesBytes ?: emptyMap()
        val probes = finishedSessions.flatten()
        val classesData = pluginInstanceState.classesData(buildVersion) as ClassesData
        // Analyze all existing classes
        val coverageBuilder = CoverageBuilder()
        val dataStore = ExecutionDataStore().with(probes)
        val initialClassBytes = buildInfo?.classesBytes ?: emptyMap()
        val analyzer = Analyzer(dataStore, coverageBuilder)

        val assocTestsMap = classesBytes.associatedTests(finishedSessions)
        val associatedTests = assocTestsMap.getAssociatedTests()

        initialClassBytes.forEach { (name, bytes) ->
            analyzer.analyzeClass(bytes, name)
        }
        val bundleCoverage = coverageBuilder.getBundle("")
        val totalCoveragePercent = bundleCoverage.coverage(classesData.totalInstructions)

        val coverageByType = if (isBuildCvg) {
            classesBytes.coveragesByTestType(finishedSessions, classesData.totalInstructions)
        } else activeScope.summary.coveragesByType
        println(coverageByType)

        val coverageBlock: Coverage = if (isBuildCvg) {
            val prevBuildVersion = classesData.prevBuildVersion
            val prevBuildAlias = adminData.buildManager[prevBuildVersion]?.buildAlias ?: ""
            BuildCoverage(
                coverage = totalCoveragePercent,
                diff = totalCoveragePercent - classesData.prevBuildCoverage,
                previousBuildInfo = prevBuildVersion to prevBuildAlias,
                coverageByType = coverageByType,
                arrow = if (prevBuildVersion.isNotBlank()) classesData.arrowType(totalCoveragePercent) else null,
                finishedScopesCount = pluginInstanceState.scopeManager.scopeCountByBuildVersion(buildVersion)
            )
        } else ScopeCoverage(
            totalCoveragePercent,
            coverageByType
        )
        println(coverageBlock)

        val methodsChanges = buildInfo?.methodChanges ?: MethodChanges()

        val buildMethods = calculateBuildMethods(
            methodsChanges,
            bundleCoverage
        ).deletedCoveredMethodsCountEnrichment(pluginInstanceState)
        val packageCoverage = packageCoverage(bundleCoverage, assocTestsMap)

        val testsUsagesInfoByType = coverageByType.map {
            TestsUsagesInfoByType(
                it.value.testType,
                it.value.coverage,
                it.value.coveredMethodsCount,
                testUsages(
                    classesBytes.bundlesByTests(finishedSessions),
                    classesData.totalInstructions,
                    it.value.testType
                )
            )
        }.sortedBy { it.testType }

        return CoverageInfoSet(
            associatedTests,
            coverageBlock,
            buildMethods,
            packageCoverage,
            testsUsagesInfoByType
        )
    }

    internal suspend fun sendScopeMessages(buildVersion: String = this.buildVersion) {
        sendActiveScope()
        sendScopes(buildVersion)
    }

    internal suspend fun sendActiveSessions() {
        val activeSessions = activeScope.activeSessions.run {
            ActiveSessions(
                count = count(),
                testTypes = values.groupBy { it.testType }.keys
            )
        }
        sender.send(agentId, buildVersion, Routes.ActiveSessions, activeSessions)
    }

    internal suspend fun sendActiveScope() {
        val activeScopeSummary = pluginInstanceState.activeScope.summary
        sender.send(agentId, buildVersion, Routes.ActiveScope, activeScopeSummary)
        sendScopeSummary(activeScopeSummary)
    }

    internal suspend fun cleanActiveScope(buildVersion: String) {
        sender.send(agentId, buildVersion, Routes.ActiveScope, "")
    }

    internal suspend fun sendScopeSummary(scopeSummary: ScopeSummary, buildVersion: String = this.buildVersion) {
        sender.send(agentId, buildVersion, Routes.Scope.Scope(scopeSummary.id), scopeSummary)
    }

    internal suspend fun sendScopes(buildVersion: String = this.buildVersion) {
        sender.send(
            agentId,
            buildVersion,
            Routes.Scopes,
            pluginInstanceState.scopeManager.summariesByBuildVersion(buildVersion)
        )
    }

    internal suspend fun toggleScope(scopeId: String): StatusMessage {
        pluginInstanceState.toggleScope(scopeId)
        return pluginInstanceState.scopeManager.getScope(scopeId)?.let { scope ->
            sendScopes(scope.buildVersion)
            sendScopeSummary(scope.summary, scope.buildVersion)
            calculateAndSendBuildCoverage(scope.buildVersion)
            StatusMessage(
                StatusCodes.OK,
                "Scope with id $scopeId toggled to 'enabled' value '${scope.enabled}'"
            )
        } ?: StatusMessage(
            StatusCodes.CONFLICT,
            "Failed to toggle scope with id $scopeId: scope not found"
        )
    }

    internal suspend fun dropScope(scopeId: String): StatusMessage {
        return pluginInstanceState.scopeManager.delete(scopeId)?.let { scope ->
            cleanTopics(scope.id)
            sendScopes(scope.buildVersion)
            sendScopeSummary(scope.summary, scope.buildVersion)
            calculateAndSendBuildCoverage(scope.buildVersion)
            StatusMessage(
                StatusCodes.OK,
                "Scope with id $scopeId was removed"
            )
        } ?: StatusMessage(
            StatusCodes.CONFLICT,
            "Failed to drop scope with id $scopeId: scope not found"
        )
    }

    internal suspend fun changeActiveScope(scopeChange: ActiveScopeChangePayload) =
        if (pluginInstanceState.scopeNameNotExisting(scopeChange.scopeName, buildVersion)) {
            val prevScope = pluginInstanceState.changeActiveScope(scopeChange.scopeName.trim())
            if (scopeChange.savePrevScope) {
                if (prevScope.any()) {
                    val finishedScope = prevScope.finish(scopeChange.prevScopeEnabled)
                    sendScopeSummary(finishedScope.summary)
                    println("$finishedScope have been saved.")
                    pluginInstanceState.scopeManager.saveScope(finishedScope)
                    if (finishedScope.enabled) {
                        calculateAndSendBuildCoverage()
                    }
                } else {
                    println("$prevScope is empty, it won't be added to the build.")
                    cleanTopics(prevScope.id)
                }
            }
            val activeScope = pluginInstanceState.activeScope
            println("Current active scope $activeScope")
            calculateAndSendActiveScopeCoverage()
            sendScopeMessages()
            StatusMessage(StatusCodes.OK, "Switched to the new scope \'${scopeChange.scopeName}\'")
        } else StatusMessage(
            StatusCodes.CONFLICT,
            "Failed to switch to a new scope: name ${scopeChange.scopeName} is already in use"
        )

    internal suspend fun calculateAndSendBuildCoverage(buildVersion: String = this.buildVersion) {
        val sessions = pluginInstanceState.scopeManager.enabledScopesSessionsByBuildVersion(buildVersion)
        val coverageInfoSet = calculateCoverageData(sessions, buildVersion, true)
        pluginInstanceState.setLastBuildCoverage(coverageInfoSet.coverage.coverage)
        if (coverageInfoSet.associatedTests.isNotEmpty()) {
            println("Assoc tests - ids count: ${coverageInfoSet.associatedTests.count()}")
            val beautifiedAssociatedTests = coverageInfoSet.associatedTests.map { batch ->
                batch.copy(className = batch.className?.replace("${batch.packageName}/", ""))
            }
            sender.send(agentId, buildVersion, Routes.Build.AssociatedTests, beautifiedAssociatedTests)
        }
        sender.send(agentId, buildVersion, Routes.Build.Coverage, coverageInfoSet.coverage)
        sender.send(agentId, buildVersion, Routes.Build.CoverageByPackages, coverageInfoSet.packageCoverage)
        sender.send(agentId, buildVersion, Routes.Build.Methods, coverageInfoSet.buildMethods)
        sender.send(agentId, buildVersion, Routes.Build.TestsUsages, coverageInfoSet.testsUsagesInfoByType)

        sendRisks(buildVersion, coverageInfoSet.buildMethods)
        pluginInstanceState.testsAssociatedWithBuild.add(buildVersion, coverageInfoSet.associatedTests)
        lastTestsToRun = testsToRun(coverageInfoSet.buildMethods)
        sendTestsToRun(lastTestsToRun)
    }

    internal suspend fun sendTestsToRun(testsToRun: TestsToRun) {
        sender.send(
            agentId,
            buildVersion,
            Routes.Build.TestsToRun,
            testsToRun
        )
    }

    private suspend fun testsToRun(
        buildMethods: BuildMethods
    ): TestsToRun {
        return TestsToRun(
            pluginInstanceState.testsAssociatedWithBuild.getTestsToRun(
                pluginInstanceState,
                buildMethods.allModifiedMethods.methods
            )
        )
    }

    internal suspend fun sendRisks(buildVersion: String, buildMethods: BuildMethods) {
        val risks = risks(buildMethods)
        sender.send(agentId, buildVersion, Routes.Build.Risks, risks)
    }

    internal fun risks(buildMethods: BuildMethods): Risks {
        val newRisks = buildMethods.newMethods.methods.filter { it.coverageRate == CoverageRate.MISSED }
        val modifiedRisks = buildMethods.allModifiedMethods.methods.filter { it.coverageRate == CoverageRate.MISSED }
        return Risks(newRisks, modifiedRisks)
    }

    internal suspend fun calculateAndSendActiveScopeCoverage() {
        val activeScope = pluginInstanceState.activeScope
        val coverageInfoSet = calculateCoverageData(activeScope)
        sendActiveSessions()

        if (coverageInfoSet.associatedTests.isNotEmpty()) {
            println("Assoc tests - ids count: ${coverageInfoSet.associatedTests.count()}")
            val beautifiedAssociatedTests = coverageInfoSet.associatedTests.map { batch ->
                batch.copy(className = batch.className?.replace("${batch.packageName}/", ""))
            }
            sender.send(agentId, buildVersion, Routes.Scope.AssociatedTests(activeScope.id), beautifiedAssociatedTests)
        }
        sender.send(agentId, buildVersion, Routes.Scope.Coverage(activeScope.id), coverageInfoSet.coverage)
        sender.send(
            agentId,
            buildVersion,
            Routes.Scope.CoverageByPackages(activeScope.id),
            coverageInfoSet.packageCoverage
        )
        sender.send(agentId, buildVersion, Routes.Scope.Methods(activeScope.id), coverageInfoSet.buildMethods)
        sender.send(
            agentId,
            buildVersion,
            Routes.Scope.TestsUsages(activeScope.id),
            coverageInfoSet.testsUsagesInfoByType
        )


    }

    internal suspend fun cleanTopics(id: String) {

        sender.send(agentId, buildVersion, Routes.Scope.AssociatedTests(id), "")
        sender.send(agentId, buildVersion, Routes.Scope.Methods(id), "")
        sender.send(agentId, buildVersion, Routes.Scope.TestsUsages(id), "")
        sender.send(agentId, buildVersion, Routes.Scope.CoverageByPackages(id), "")
        sender.send(agentId, buildVersion, Routes.Scope.Coverage(id), "")
    }

    override suspend fun dropData() {
        adminData.buildManager.buildInfos.keys.forEach {
            sender.send(agentId, it, Routes.Scopes, "")
            sender.send(agentId, it, Routes.Build.AssociatedTests, "")
            sender.send(agentId, it, Routes.Build.Methods, "")
            sender.send(agentId, it, Routes.Build.TestsUsages, "")
            sender.send(agentId, it, Routes.Build.CoverageByPackages, "")
            sender.send(agentId, it, Routes.Build.Coverage, "")
        }
        val classesBytes = adminData.buildManager[buildVersion]!!.classesBytes
        pluginInstanceState = pluginInstanceState()
        processData(InitInfo(classesBytes.keys.count(), ""))
        pluginInstanceState.initialized(adminData.buildManager.buildInfos)
        processData(Initialized())
    }

    private suspend fun pluginInstanceState(): PluginInstanceState {
        val prevBuildVersion = adminData.buildManager[buildVersion]?.prevBuild ?: ""
        val lastPrevBuildCoverage = storeClient
            .findById<LastBuildCoverage>(lastCoverageId(agentId, prevBuildVersion))
            ?.coverage ?: 0.0
        val testsAssociatedWithBuild = KoduxTestsAssociatedWithBuildStorageManager(storeClient).getStorage(
            agentInfo.id,
            KoduxTestsAssociatedWithBuild(agentInfo.id, storeClient)
        )
        return PluginInstanceState(
            agentInfo = agentInfo,
            lastPrevBuildCoverage = lastPrevBuildCoverage,
            prevBuildVersion = prevBuildVersion,
            storeClient = storeClient,
            testsAssociatedWithBuild = testsAssociatedWithBuild
        )
    }

    private suspend fun wipeStoredData() {
        storeClient.deleteAll<FinishedScope>()
        storeClient.deleteAll<KoduxTestsAssociatedWithBuild>()
        storeClient.deleteAll<LastBuildCoverage>()
        storeClient.deleteAll<ClassesData>()
        initialize()
    }

}
