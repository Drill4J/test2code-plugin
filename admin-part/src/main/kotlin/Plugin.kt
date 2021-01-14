package com.epam.drill.plugins.test2code


import com.epam.drill.common.*
import com.epam.drill.plugin.api.*
import com.epam.drill.plugin.api.end.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.api.routes.*
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.drill.plugins.test2code.coverage.*
import com.epam.drill.plugins.test2code.group.*
import com.epam.drill.plugins.test2code.storage.*
import com.epam.drill.plugins.test2code.util.*
import com.epam.kodux.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.*

@Suppress("unused")
class Plugin(
    adminData: AdminData,
    sender: Sender,
    val storeClient: StoreClient,
    agentInfo: AgentInfo,
    id: String
) : AdminPluginPart<Action>(
    id = id,
    agentInfo = agentInfo,
    adminData = adminData,
    sender = sender
), Closeable {
    companion object {
        val json = Json { encodeDefaults = true }
    }

    internal val logger = logger(agentInfo.id)

    internal val runtimeConfig = RuntimeConfig(id)

    internal val state: AgentState get() = _state.value!!

    val buildVersion = agentInfo.buildVersion

    val activeScope: ActiveScope get() = state.activeScope

    private val agentId = agentInfo.id

    private val _state = atomic<AgentState?>(null)

    override suspend fun initialize() {
        changeState()
        state.loadFromDb {
            processInitialized()
        }
    }

    override fun close() {
        _state.getAndUpdate { null }?.close()
    }

    override suspend fun applyPackagesChanges() {
        state.scopeManager.deleteByVersion(buildVersion)
        storeClient.deleteById<ClassData>(buildVersion)
        changeState()
    }

    override fun parseAction(
        rawAction: String
    ): Action = json.decodeFromString(Action.serializer(), rawAction)

    override suspend fun doAction(
        action: Action
    ): ActionResult = when (action) {
        is ToggleBaseline -> toggleBaseline()
        is SwitchActiveScope -> changeActiveScope(action.payload)
        is RenameScope -> renameScope(action.payload)
        is ToggleScope -> toggleScope(action.payload.scopeId)
        is DropScope -> dropScope(action.payload.scopeId)
        is UpdateSettings -> updateSettings(action.payload)
        is StartNewSession -> action.payload.run {
            val newSessionId = sessionId.ifEmpty(::genUuid)
            activeScope.startSession(
                newSessionId,
                testType,
                isGlobal,
                runtimeConfig.realtime && isRealtime
            )?.run {
                StartAgentSession(
                    payload = StartSessionPayload(
                        sessionId = id,
                        testType = testType,
                        testName = testName,
                        isGlobal = isGlobal,
                        isRealtime = isRealtime
                    )
                ).toActionResult()
            } ?: if (isGlobal && activeScope.hasActiveGlobalSession()) {
                ActionResult(
                    code = StatusCodes.CONFLICT,
                    data = listOf(
                        "Error! Only one active global session is allowed.",
                        "Please finish the active one in order to start new."
                    ).joinToString(" ")
                )
            } else FieldErrorDto(
                field = "sessionId",
                message = "Session with such ID already exists. Please choose a different ID."
            ).toActionResult(StatusCodes.CONFLICT)
        }
        is AddSessionData -> action.payload.run {
            activeScope.activeSessionOrNull(sessionId)?.let { session ->
                AddAgentSessionData(
                    payload = AgentSessionDataPayload(sessionId = session.id, data = data)
                ).toActionResult()
            } ?: ActionResult(StatusCodes.NOT_FOUND, "Active session '$sessionId' not found.")
        }
        is AddCoverage -> action.payload.run {
            activeScope.addProbes(sessionId) {
                data.map { probes ->
                    ExecClassData(className = probes.name, testName = probes.test, probes = probes.probes)
                }
            }?.run {
                if (isRealtime) {
                    activeScope.probesChanged()
                }
                ActionResult(StatusCodes.OK, "")
            } ?: ActionResult(StatusCodes.NOT_FOUND, "Active session '$sessionId' not found.")
        }
        is CancelSession -> action.payload.run {
            activeScope.cancelSession(action.payload.sessionId)?.let { session ->
                CancelAgentSession(payload = AgentSessionPayload(session.id)).toActionResult()
            } ?: ActionResult(StatusCodes.NOT_FOUND, "Active session '$sessionId' not found.")
        }
        is CancelAllSessions -> {
            activeScope.cancelAllSessions()
            CancelAllAgentSessions.toActionResult()
        }
        is StopSession -> action.payload.run {
            activeScope.activeSessionOrNull(sessionId)?.let { session ->
                testRun?.let { session.setTestRun(it) }
                StopAgentSession(
                    payload = AgentSessionPayload(session.id)
                ).toActionResult()
            } ?: ActionResult(StatusCodes.NOT_FOUND, "Active session '$sessionId' not found.")
        }
        is StopAllSessions -> StopAllAgentSessions.toActionResult()
        else -> "Action '$action' is not supported!".let { message ->
            logger.error { message }
            ActionResult(StatusCodes.BAD_REQUEST, message)
        }
    }

    override suspend fun processData(
        instanceId: String,
        content: String
    ): Any = run {
        val message = json.decodeFromString(CoverMessage.serializer(), content)
        processData(instanceId, message)
            .let { "" } //TODO eliminate magic empty strings from API
    }

    private suspend fun processData(
        instanceId: String,
        message: CoverMessage
    ) = when (message) {
        is InitInfo -> {
            if (message.init) {
                state.init()
            }
            logger.info { "$instanceId: ${message.message}" } //log init message
            logger.info { "$instanceId: ${message.classesCount} classes to load" }
        }
        is InitDataPart -> {
            (state.data as? DataBuilder)?.also {
                logger.info { message }
                it += message.astEntities
            }
        }
        is Initialized -> state.initialized {
            processInitialized()
        }
        is ScopeInitialized -> scopeInitialized(message.prevId)
        is SessionStarted -> logger.info { "$instanceId: Agent session ${message.sessionId} started." }
        is SessionCancelled -> logger.info { "$instanceId: Agent session ${message.sessionId} cancelled." }
        is SessionsCancelled -> message.run {
            activeScope.let { ids.forEach { id: String -> it.cancelSession(id) } }
            logger.info { "$instanceId: Agent sessions cancelled: $ids." }
        }
        is CoverDataPart -> activeScope.addProbes(message.sessionId) { message.data }
        is SessionChanged -> activeScope.probesChanged()
        is SessionFinished -> {
            delay(500L) //TODO remove after multi-instance core is implemented
            state.finishSession(message.sessionId) ?: logger.info {
                "$instanceId: No active session with id ${message.sessionId}."
            }
        }
        is SessionsFinished -> {
            delay(500L) //TODO remove after multi-instance core is implemented
            message.ids.forEach { state.finishSession(it) }
        }
        else -> logger.info { "$instanceId: Message is not supported! $message" }
    }

    private suspend fun Plugin.processInitialized(): Boolean {
        initGateSettings()
        sendGateSettings()
        sendParentBuild()
        sendBaseline()
        sendParentTestsToRunStats()
        state.classDataOrNull()?.sendBuildStats()
        sendScopes(buildVersion)
        calculateAndSendCachedCoverage()
        return initActiveScope()
    }

    private suspend fun sendParentBuild() = send(
        buildVersion,
        destination = Routes.Data().let(Routes.Data::Parent),
        message = state.coverContext().parentBuild?.version?.let(::BuildVersionDto) ?: ""
    )

    internal suspend fun sendBaseline() = send(
        buildVersion,
        destination = Routes.Data().let(Routes.Data::Baseline),
        message = storeClient.findById<GlobalAgentData>(agentId)?.baseline?.version?.let(::BuildVersionDto) ?: ""
    )

    private suspend fun sendParentTestsToRunStats() = send(
        buildVersion,
        destination = Routes.Build().let(Routes.Build::TestsToRun).let(Routes.Build.TestsToRun::ParentTestsToRunStats),
        message = state.storeClient.loadTestsToRunSummary(
            buildVersion = buildVersion,
            parentVersion = state.coverContext().build.parentVersion
        ).map { it.toTestsToRunSummaryDto() }
    )

    private suspend fun ClassData.sendBuildStats() {
        send(buildVersion, Routes.Data().let(Routes.Data::Build), state.coverContext().toBuildStatsDto())
    }

    private suspend fun calculateAndSendCachedCoverage() = state.coverContext().build.let { build ->
        val scopes = state.scopeManager.byVersion(
            buildVersion, withData = true
        )
        state.updateProbes(scopes.enabled())
        val coverContext = state.coverContext()
        build.bundleCounters.calculateAndSendBuildCoverage(coverContext, build.stats.scopeCount)
        scopes.forEach { scope ->
            val coverageInfoSet = scope.data.bundleCounters.calculateCoverageData(coverContext, scope)
            coverageInfoSet.sendScopeCoverage(buildVersion, scope.id)
        }
    }

    internal suspend fun sendScopeMessages(buildVersion: String = this.buildVersion) {
        sendActiveScope()
        sendScopes(buildVersion)
    }

    internal suspend fun sendActiveSessions() {
        val sessions = activeScope.activeSessions.values.map {
            ActiveSessionDto(
                id = it.id,
                agentId = agentId,
                testType = it.testType,
                isGlobal = it.isGlobal,
                isRealtime = it.isRealtime
            )
        }
        val summary = ActiveSessions(
            count = sessions.count(),
            testTypes = sessions.groupBy { it.testType }.keys
        )
        send(buildVersion, Routes.ActiveSessionStats, summary)
        send(buildVersion, Routes.ActiveSessions, sessions)
        val serviceGroup = agentInfo.serviceGroup
        if (serviceGroup.any()) {
            val aggregatedSessions = sessionAggregator(serviceGroup, agentId, sessions) ?: sessions
            sendToGroup(
                destination = Routes.ServiceGroup.ActiveSessions(Routes.ServiceGroup()),
                message = aggregatedSessions
            )
        }
    }

    internal suspend fun sendActiveScope() {
        val summary = activeScope.summary
        send(buildVersion, Routes.ActiveScope, summary)
        sendScopeSummary(summary)
    }

    internal suspend fun sendScopeSummary(scopeSummary: ScopeSummary, buildVersion: String = this.buildVersion) {
        send(buildVersion, Routes.Scope(scopeSummary.id), scopeSummary)
    }

    internal suspend fun sendScopes(buildVersion: String = this.buildVersion) {
        val scopes = state.scopeManager.byVersion(buildVersion)
        sendScopes(buildVersion, scopes)
    }

    private suspend fun sendScopes(
        buildVersion: String,
        scopes: Sequence<FinishedScope>
    ) = sender.send(
        context = AgentSendContext(
            agentId,
            buildVersion
        ),
        destination = Routes.Scopes,
        message = scopes.summaries()
    )

    internal suspend fun calculateAndSendBuildCoverage() {
        val scopes = state.scopeManager.run {
            byVersion(buildVersion, withData = true).enabled()
        }
        scopes.calculateAndSendBuildCoverage(state.coverContext())
    }

    private suspend fun Sequence<FinishedScope>.calculateAndSendBuildCoverage(context: CoverContext) {
        state.updateProbes(this)
        val bundleCounters = flatten().calcBundleCounters(context)
        state.updateBundleCounters(bundleCounters)
        bundleCounters.calculateAndSendBuildCoverage(context, scopeCount = count())
    }

    private suspend fun BundleCounters.calculateAndSendBuildCoverage(
        context: CoverContext,
        scopeCount: Int
    ) {
        val coverageInfoSet = calculateCoverageData(context)
        val parentCoverageCount = context.parentBuild?.let { context.parentBuild.stats.coverage } ?: zeroCount
        val risks = context.methodChanges.risks(all)
        val buildCoverage = (coverageInfoSet.coverage as BuildCoverage).copy(
            finishedScopesCount = scopeCount,
            riskCount = Count(
                risks.values.sumBy { it.count() },
                context.methodChanges.run { new.count() + modified.count() }
            )
        )
        state.updateBuildStats(buildCoverage, context)
        val cachedBuild = state.updateBuildTests(
            byTest.keys.groupBy(TypedTest::type, TypedTest::name),
            coverageInfoSet.associatedTests
        )
        state.storeBuild()
        val summary = cachedBuild.toSummary(
            agentInfo.name,
            context.testsToRun,
            risks,
            coverageInfoSet.coverageByTests,
            parentCoverageCount
        )
        coverageInfoSet.sendBuildCoverage(buildVersion, buildCoverage, summary)
        val stats = summary.toStatsDto()
        val qualityGate = checkQualityGate(stats)
        send(buildVersion, Routes.Build().let(Routes.Build::Summary), summary.toDto())
        Routes.Data().let {
            send(buildVersion, Routes.Data.Stats(it), stats)
            send(buildVersion, Routes.Data.QualityGate(it), qualityGate)
            send(buildVersion, Routes.Data.Recommendations(it), summary.recommendations())
            send(buildVersion, Routes.Data.Tests(it), summary.tests.toDto())
            send(buildVersion, Routes.Data.TestsToRun(it), summary.testsToRun.toDto())
        }
        sendGroupSummary(summary)
    }

    private suspend fun CoverageInfoSet.sendBuildCoverage(
        buildVersion: String,
        buildCoverage: BuildCoverage,
        summary: AgentSummary
    ) = Routes.Build().let { buildRoute ->
        val coverageRoute = Routes.Build.Coverage(buildRoute)
        send(buildVersion, coverageRoute, buildCoverage)
        val methodSummaryDto = buildMethods.toSummaryDto().copy(risks = summary.riskCounts)
        send(buildVersion, Routes.Build.Methods(buildRoute), methodSummaryDto)
        val pkgsRoute = Routes.Build.Coverage.Packages(coverageRoute)
        val packages = packageCoverage.takeIf { runtimeConfig.sendPackages } ?: emptyList()
        send(buildVersion, pkgsRoute, packages.map { it.copy(classes = emptyList()) })
        packages.forEach {
            send(buildVersion, Routes.Build.Coverage.Packages.Package(it.name, pkgsRoute), it)
        }
        if (associatedTests.isNotEmpty()) {
            logger.info { "Assoc tests - ids count: ${associatedTests.count()}" }
            val beautifiedAssociatedTests = associatedTests.map { batch ->
                batch.copy(className = batch.className.replace("${batch.packageName}/", ""))
            }
            send(buildVersion, Routes.Build.AssociatedTests(buildRoute), beautifiedAssociatedTests)
        }
        send(buildVersion, Routes.Build.Tests(buildRoute), tests)
        //TODO remove after changes on frontend EPMDJ-5622
        send(buildVersion, Routes.Build.TestsUsages(buildRoute), tests.toUsageInfo())
        Routes.Build.Summary.Tests(Routes.Build.Summary(buildRoute)).let {
            send(buildVersion, Routes.Build.Summary.Tests.All(it), coverageByTests.all)
            send(buildVersion, Routes.Build.Summary.Tests.ByType(it), coverageByTests.byType)
        }
        //TODO remove after changes on the frontend
        send(buildVersion, Routes.Build.CoveredMethodsByTest(buildRoute), methodsCoveredByTest)

        methodsCoveredByTest.forEach {
            Routes.Build.MethodsCoveredByTest(it.id, buildRoute).let { test ->
                send(buildVersion, Routes.Build.MethodsCoveredByTest.Summary(test), it.toSummary())
                send(buildVersion, Routes.Build.MethodsCoveredByTest.All(test), it.allMethods)
                send(buildVersion, Routes.Build.MethodsCoveredByTest.Modified(test), it.modifiedMethods)
                send(buildVersion, Routes.Build.MethodsCoveredByTest.Unaffected(test), it.unaffectedMethods)
                send(buildVersion, Routes.Build.MethodsCoveredByTest.New(test), it.newMethods)
            }
        }
        send(buildVersion, Routes.Build.Risks(buildRoute), summary.risks.toListDto())
        val context = state.coverContext() //TODO remove context from this method
        send(buildVersion, Routes.Build.TestsToRun(buildRoute), context.testsToRunDto())
        val testsToRunSummary = context.toTestsToRunSummary()
        state.storeClient.store(testsToRunSummary)
        Routes.Build.Summary(buildRoute).let {
            send(buildVersion, Routes.Build.Summary.TestsToRun(it), testsToRunSummary.toTestsToRunSummaryDto())
        }
    }

    private suspend fun Plugin.sendGroupSummary(summary: AgentSummary) {
        val serviceGroup = agentInfo.serviceGroup
        if (serviceGroup.any()) {
            val aggregated = summaryAggregator(serviceGroup, agentId, summary)
            val summaries = summaryAggregator.getSummaries(serviceGroup)
            Routes.ServiceGroup().let { groupParent ->
                sendToGroup(
                    destination = Routes.ServiceGroup.Summary(groupParent),
                    message = ServiceGroupSummaryDto(
                        name = serviceGroup,
                        aggregated = aggregated.toDto(),
                        summaries = summaries.map { (id, summary) ->
                            summary.toDto(id)
                        }
                    )
                )
                Routes.ServiceGroup.Data(groupParent).let {
                    sendToGroup(
                        destination = Routes.ServiceGroup.Data.Tests(it),
                        message = aggregated.tests.toDto()
                    )

                    sendToGroup(
                        destination = Routes.ServiceGroup.Data.TestsToRun(it),
                        message = aggregated.testsToRun.toDto()
                    )
                    sendToGroup(
                        destination = Routes.ServiceGroup.Data.Recommendations(it),
                        message = aggregated.recommendations()
                    )
                }
            }
        }
    }

    internal suspend fun calculateAndSendScopeCoverage() = activeScope.let { scope ->
        val context = state.coverContext()
        val bundleCounters = scope.calcBundleCounters(context)
        val coverageInfoSet = bundleCounters.calculateCoverageData(context, scope)
        activeScope.updateSummary {
            it.copy(coverage = coverageInfoSet.coverage as ScopeCoverage)
        }
        coverageInfoSet.sendScopeCoverage(buildVersion, scope.id)
    }

    internal suspend fun CoverageInfoSet.sendScopeCoverage(
        buildVersion: String,
        scopeId: String
    ) = Routes.Scope(scopeId).let { scope ->
        val coverageRoute = Routes.Scope.Coverage(scope)
        send(buildVersion, coverageRoute, coverage)
        send(buildVersion, Routes.Scope.Methods(scope), buildMethods.toSummaryDto())
        val pkgsRoute = Routes.Scope.Coverage.Packages(coverageRoute)
        val packages = packageCoverage.takeIf { runtimeConfig.sendPackages } ?: emptyList()
        send(buildVersion, pkgsRoute, packages.map { it.copy(classes = emptyList()) })
        packages.forEach {
            send(buildVersion, Routes.Scope.Coverage.Packages.Package(it.name, pkgsRoute), it)
        }
        if (associatedTests.isNotEmpty()) {
            logger.info { "Assoc tests - ids count: ${associatedTests.count()}" }
            val beautifiedAssociatedTests = associatedTests.map { batch ->
                batch.copy(className = batch.className.replace("${batch.packageName}/", ""))
            }
            send(buildVersion, Routes.Scope.AssociatedTests(scope), beautifiedAssociatedTests)
        }
        send(buildVersion, Routes.Scope.Tests(scope), tests)
        //TODO remove after changes on frontend EPMDJ-5622
        send(buildVersion, Routes.Scope.TestsUsages(scope), tests.toUsageInfo())
        Routes.Scope.Summary.Tests(Routes.Scope.Summary(scope)).let {
            send(buildVersion, Routes.Scope.Summary.Tests.All(it), coverageByTests.all)
            send(buildVersion, Routes.Scope.Summary.Tests.ByType(it), coverageByTests.byType)
        }
        //TODO remove after changes on the frontend
        send(buildVersion, Routes.Scope.CoveredMethodsByTest(scope), methodsCoveredByTest)

        methodsCoveredByTest.forEach {
            Routes.Scope.MethodsCoveredByTest(it.id, scope).let { test ->
                send(buildVersion, Routes.Scope.MethodsCoveredByTest.Summary(test), it.toSummary())
                send(buildVersion, Routes.Scope.MethodsCoveredByTest.All(test), it.allMethods)
                send(buildVersion, Routes.Scope.MethodsCoveredByTest.Modified(test), it.modifiedMethods)
                send(buildVersion, Routes.Scope.MethodsCoveredByTest.New(test), it.newMethods)
                send(buildVersion, Routes.Scope.MethodsCoveredByTest.Unaffected(test), it.unaffectedMethods)
            }
        }
    }

    internal suspend fun send(buildVersion: String, destination: Any, message: Any) {
        sender.send(AgentSendContext(agentInfo.id, buildVersion), destination, message)
    }

    private fun changeState() {
        _state.getAndUpdate {
            AgentState(
                storeClient = storeClient,
                agentInfo = agentInfo,
                adminData = adminData
            )
        }?.close()
    }
}
