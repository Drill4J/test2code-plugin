package com.epam.drill.plugins.test2code


import com.epam.drill.common.*
import com.epam.drill.plugin.api.*
import com.epam.drill.plugin.api.end.*
import com.epam.drill.plugin.api.message.*
import com.epam.drill.plugins.test2code.routes.*
import com.epam.kodux.*
import kotlinx.atomicfu.*

@Suppress("unused", "MemberVisibilityCanBePrivate")
class Test2CodeAdminPart(
    adminData: AdminData,
    sender: Sender,
    val storeClient: StoreClient,
    agentInfo: AgentInfo,
    id: String
) : AdminPluginPart<Action>(adminData, sender, storeClient, agentInfo, id) {

    override val serDe: SerDe<Action> = adminSerDe

    lateinit var pluginInstanceState: PluginInstanceState

    internal var lastTestsToRun: GroupedTests
        get() = _lastTestsToRun.value
        set(value) {
            _lastTestsToRun.value = value
        }

    val buildVersion = agentInfo.buildVersion

    val buildInfo: BuildInfo? get() = adminData.buildManager[buildVersion]

    val activeScope get() = pluginInstanceState.activeScope

    val agentId = agentInfo.id

    private val _lastTestsToRun = atomic<GroupedTests>(emptyMap())

    override suspend fun initialize() {
        pluginInstanceState = pluginInstanceState()
        pluginInstanceState.buildInfo?.let { processData(Initialized("")) }
    }

    override suspend fun applyPackagesChanges() {
        storeClient.deleteBy<FinishedScope> { FinishedScope::buildVersion.eq(buildVersion) }
        storeClient.deleteById<ClassesData>(buildVersion)
        pluginInstanceState = pluginInstanceState()
        processData(Initialized(""))
    }

    override suspend fun updateDataOnBuildConfigChange(buildVersion: String) {
        //TODO figure out why this is needed
        calculateAndSendChildrenCoverage(buildVersion)
    }

    override suspend fun doAction(action: Action): Any {
        return when (action) {
            is SwitchActiveScope -> changeActiveScope(action.payload)
            is RenameScope -> renameScope(action.payload)
            is ToggleScope -> toggleScope(action.payload.scopeId)
            is DropScope -> dropScope(action.payload.scopeId)
            is StartNewSession -> StartSession(
                payload = StartSessionPayload(
                    sessionId = action.payload.sessionId.ifEmpty(::genUuid),
                    startPayload = action.payload
                )
            )
            else -> println("Actions is not supported! $action")
        }
    }

    //TODO remove this after the data API has been redesigned
    override suspend fun getPluginData(params: Map<String, String>) = handleGettingData(params)

    override suspend fun processData(dm: DrillMessage): Any = dm.content!!.let { content ->
        val message = CoverMessage.serializer() parse content
        processData(message)
            .let { "" } //TODO eliminate magic empty strings from API
    }

    internal suspend fun processData(message: CoverMessage) = when (message) {
        is InitInfo -> {
            if (message.init) {
                pluginInstanceState.init()
            }
            println(message.message) //log init message
            println("${message.classesCount} classes to load")
        }
        is InitDataPart -> {
            (pluginInstanceState.data as? DataBuilder)?.also {
                println(message)
                it += message.astEntities
            }
        }
        is Initialized -> {
            pluginInstanceState.initialized()
            initActiveScope()
            val buildManager = adminData.buildManager
            val otherVersions = buildManager.otherVersions(buildVersion)
            otherVersions.map(BuildInfo::version).forEach { version ->
                cleanActiveScope(version)
                calculateAndSendAllCoverage(version)
            }
            sendActiveSessions()
            calculateAndSendAllCoverage(buildVersion)
            calculateAndSendScopeCoverage(activeScope)
            sendActiveScope()
        }
        is SessionStarted -> {
            activeScope.startSession(message.sessionId, message.testType)
            println("Session ${message.sessionId} started.")
            sendActiveSessions()
        }
        is SessionCancelled -> {
            activeScope.cancelSession(message)
            println("Session ${message.sessionId} cancelled.")
            sendActiveSessions()
        }
        is CoverDataPart -> {
            activeScope.addProbes(message.sessionId, message.data)
        }
        is SessionFinished -> {
            val sessionId = message.sessionId
            activeScope.finishSession(sessionId) {
                activeScope.updateSummary { it.calculateCoverage(this, pluginInstanceState) }
            }?.also {
                sendActiveSessions()
                sendActiveScope()
                if (it.probes.any()) {
                    sendScopes(buildVersion)
                    calculateAndSendScopeCoverage(activeScope)
                    println("Session $sessionId finished.")
                } else println("Session with id $sessionId is empty, it won't be added to the active scope.")
            } ?: println("No active session with id $sessionId.")
        }
        else -> println("Message is not supported! $message")
    }

    private suspend fun calculateAndSendAllCoverage(buildVersion: String) {
        val finishedScopes = pluginInstanceState.scopeManager.scopes(buildVersion, enabled = null)
        finishedScopes.forEach { scope -> calculateAndSendScopeCoverage(scope, buildVersion) }
        sendScopes(buildVersion, finishedScopes)
        calculateAndSendBuildCoverage(buildVersion)
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
        send(buildVersion, Routes.ActiveSessions, activeSessions)
    }

    internal suspend fun sendActiveScope() {
        val summary = activeScope.summary
        send(buildVersion, Routes.ActiveScope, summary)
        sendScopeSummary(summary)
    }

    internal suspend fun cleanActiveScope(buildVersion: String) {
        send(buildVersion, Routes.ActiveScope, "")
    }

    internal suspend fun sendScopeSummary(scopeSummary: ScopeSummary, buildVersion: String = this.buildVersion) {
        send(buildVersion, Routes.Scope.Scope(scopeSummary.id), scopeSummary)
    }

    internal suspend fun sendScopes(buildVersion: String = this.buildVersion) {
        val scopes = pluginInstanceState.scopeManager.scopes(buildVersion, enabled = null)
        sendScopes(buildVersion, scopes)
    }

    private suspend fun sendScopes(
        buildVersion: String,
        scopes: Sequence<FinishedScope>
    ) = sender.send(
        agentId,
        buildVersion,
        Routes.Scopes,
        scopes.summaries()
    )


    internal suspend fun calculateAndSendBuildAndChildrenCoverage(buildVersion: String = this.buildVersion) {
        calculateAndSendBuildCoverage(buildVersion)
        calculateAndSendChildrenCoverage(buildVersion)
    }

    private suspend fun calculateAndSendChildrenCoverage(buildVersion: String) {
        adminData.buildManager.childrenOf(buildVersion).map(BuildInfo::version).forEach { version ->
            calculateAndSendBuildCoverage(version)
        }
    }

    internal suspend fun calculateAndSendBuildCoverage(buildVersion: String = this.buildVersion) {
        val sessions = pluginInstanceState.scopeManager.scopes(buildVersion).flatten()
        val coverageInfoSet = sessions.calculateCoverageData(pluginInstanceState, buildVersion)
        //TODO rewrite these add-hoc current build checks
        if (buildVersion == this.buildVersion) {
            pluginInstanceState.addBuildTests(buildVersion, coverageInfoSet.associatedTests) //FIXME
        }
        val buildMethods = coverageInfoSet.buildMethods
        val testsToRun = pluginInstanceState.testsToRun(
            buildVersion,
            buildMethods.allModifiedMethods.methods
        )
        if (buildVersion == this.buildVersion) {
            lastTestsToRun = testsToRun
        }

        val risks = buildMethods.risks()
        pluginInstanceState.storeBuildCoverage(coverageInfoSet.coverage as BuildCoverage, risks, testsToRun)
        coverageInfoSet.sendBuildCoverage(buildVersion, risks, testsToRun)
    }

    private suspend fun CoverageInfoSet.sendBuildCoverage(
        buildVersion: String, risks: Risks, testsToRun: GroupedTests
    ) {
        if (associatedTests.isNotEmpty()) {
            println("Assoc tests - ids count: ${associatedTests.count()}")
            val beautifiedAssociatedTests = associatedTests.map { batch ->
                batch.copy(className = batch.className?.replace("${batch.packageName}/", ""))
            }
            send(buildVersion, Routes.Build.AssociatedTests, beautifiedAssociatedTests)
        }
        send(buildVersion, Routes.Build.Coverage, coverage)
        send(buildVersion, Routes.Build.CoverageByPackages, packageCoverage)
        send(buildVersion, Routes.Build.Methods, buildMethods)
        send(buildVersion, Routes.Build.TestsUsages, testsUsagesInfoByType)
        send(buildVersion, Routes.Build.MethodsCoveredByTest, methodsCoveredByTest)
        send(buildVersion, Routes.Build.MethodsCoveredByTestType, methodsCoveredByTestType)
        send(buildVersion, Routes.Build.Risks, risks)
        send(buildVersion, Routes.Build.TestsToRun, TestsToRun(testsToRun))
    }

    internal fun BuildMethods.risks(): Risks {
        val newRisks = newMethods.methods.filter { it.coverageRate == CoverageRate.MISSED }
        val modifiedRisks = allModifiedMethods.methods.filter { it.coverageRate == CoverageRate.MISSED }
        return Risks(newRisks, modifiedRisks)
    }

    internal suspend fun calculateAndSendScopeCoverage(
        scope: Scope = activeScope,
        buildVersion: String = this.buildVersion
    ) {
        val coverageInfoSet = scope.calculateCoverageData(pluginInstanceState, buildVersion)
        coverageInfoSet.sendScopeCoverage(buildVersion, scope.id)
    }

    internal suspend fun CoverageInfoSet.sendScopeCoverage(buildVersion: String, scopeId: String) {
        if (associatedTests.isNotEmpty()) {
            println("Assoc tests - ids count: ${associatedTests.count()}")
            val beautifiedAssociatedTests = associatedTests.map { batch ->
                batch.copy(className = batch.className?.replace("${batch.packageName}/", ""))
            }
            send(buildVersion, Routes.Scope.AssociatedTests(scopeId), beautifiedAssociatedTests)
        }
        send(buildVersion, Routes.Scope.Coverage(scopeId), coverage)
        send(buildVersion, Routes.Scope.CoverageByPackages(scopeId), packageCoverage)
        send(buildVersion, Routes.Scope.Methods(scopeId), buildMethods)
        send(buildVersion, Routes.Scope.TestsUsages(scopeId), testsUsagesInfoByType)
        send(buildVersion, Routes.Scope.MethodsCoveredByTest(scopeId), methodsCoveredByTest)
        send(buildVersion, Routes.Scope.MethodsCoveredByTestType(scopeId), methodsCoveredByTestType)
    }

    internal suspend fun cleanTopics(id: String) {
        send(buildVersion, Routes.Scope.AssociatedTests(id), "")
        send(buildVersion, Routes.Scope.Methods(id), "")
        send(buildVersion, Routes.Scope.TestsUsages(id), "")
        send(buildVersion, Routes.Scope.CoverageByPackages(id), "")
        send(buildVersion, Routes.Scope.Coverage(id), "")
    }

    override suspend fun dropData() {
        adminData.buildManager.builds.map(BuildInfo::version).forEach {
            send(it, Routes.Scopes, "")
            send(it, Routes.Build.AssociatedTests, "")
            send(it, Routes.Build.Methods, "")
            send(it, Routes.Build.TestsUsages, "")
            send(it, Routes.Build.CoverageByPackages, "")
            send(it, Routes.Build.Coverage, "")
        }
        val classesBytes = buildInfo?.classesBytes ?: emptyMap()
        pluginInstanceState = pluginInstanceState()
        processData(InitInfo(classesBytes.keys.count(), ""))
        pluginInstanceState.initialized()
        processData(Initialized())
    }

    private suspend fun send(buildVersion: String, destination: Any, message: Any) {
        sender.send(agentInfo.id, buildVersion, destination, message)
    }

    private suspend fun pluginInstanceState(): PluginInstanceState {
        val prevBuildVersion = buildInfo?.parentVersion ?: ""
        val lastPrevBuildCoverage = storeClient.readLastBuildCoverage(agentId, prevBuildVersion)?.coverage
        return PluginInstanceState(
            agentInfo = agentInfo,
            lastPrevBuildCoverage = lastPrevBuildCoverage ?: 0.0,
            prevBuildVersion = prevBuildVersion,
            storeClient = storeClient,
            buildManager = adminData.buildManager
        )
    }
}
