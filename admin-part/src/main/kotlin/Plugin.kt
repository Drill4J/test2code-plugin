package com.epam.drill.plugins.test2code


import com.epam.drill.common.*
import com.epam.drill.plugin.api.*
import com.epam.drill.plugin.api.end.*
import com.epam.drill.plugin.api.message.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.api.routes.*
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.drill.plugins.test2code.coverage.*
import com.epam.drill.plugins.test2code.storage.*
import com.epam.kodux.*
import mu.*

@Suppress("unused")
class Plugin(
    adminData: AdminData,
    sender: Sender,
    val storeClient: StoreClient,
    agentInfo: AgentInfo,
    id: String
) : AdminPluginPart<Action>(adminData, sender, storeClient, agentInfo, id) {

    override val serDe: SerDe<Action> = apiSerDe

    internal val runtimeConfig = RuntimeConfig(id)

    internal lateinit var state: AgentState

    val buildVersion = agentInfo.buildVersion

    val activeScope get() = state.activeScope

    private val agentId = agentInfo.id

    private val buildInfo: BuildInfo? get() = adminData.buildManager[buildVersion]

    private val logger = KotlinLogging.logger("Plugin $id")

    override suspend fun initialize() {
        state = agentState()
        state.readScopeCounter()?.let { processData(Initialized("")) }
    }

    override suspend fun applyPackagesChanges() {
        state.scopeManager.deleteByVersion(buildVersion)
        storeClient.deleteById<ClassData>(buildVersion)
        state = agentState()
    }

    override suspend fun doAction(action: Action): Any {
        return when (action) {
            is SwitchActiveScope -> changeActiveScope(action.payload)
            is RenameScope -> renameScope(action.payload)
            is ToggleScope -> toggleScope(action.payload.scopeId)
            is DropScope -> dropScope(action.payload.scopeId)
            is UpdateSettings -> updateSettings(action.payload)
            is StartNewSession -> StartSession(
                payload = StartSessionPayload(
                    sessionId = action.payload.sessionId.ifEmpty(::genUuid),
                    startPayload = action.payload
                )
            )
            else -> logger.info { "Action '$action' is not supported!" }
        }
    }

    override suspend fun processData(dm: DrillMessage): Any = dm.content!!.let { content ->
        val message = CoverMessage.serializer() parse content
        processData(message)
            .let { "" } //TODO eliminate magic empty strings from API
    }

    private suspend fun processData(message: CoverMessage) = when (message) {
        is InitInfo -> {
            if (message.init) {
                state.init()
            }
            logger.info { message.message } //log init message
            logger.info { "${message.classesCount} classes to load" }
        }
        is InitDataPart -> {
            (state.data as? DataBuilder)?.also {
                logger.info { message }
                it += message.astEntities
            }
        }
        is Initialized -> {
            val otherVersions = state.initialized()
            classDataOrNull()?.let {
                send(buildVersion, Routes.Data().let(Routes.Data::Build), it.toBuildStatsDto())
            }
            initGateSettings()
            initActiveScope()
            sendActiveSessions()
            sendActiveScope()
            calculateAndSendScopeCoverage(activeScope)
            sendScopes(buildVersion)
            calculateAndSendCachedCoverage(buildVersion)
            adminData.buildManager.builds.filter { it.version != buildVersion }.forEach {
                cleanActiveScope(it.version)
            }
            otherVersions.forEach { version ->
                sendScopes(version)
                calculateAndSendCachedCoverage(version)
            }
        }
        is ScopeInitialized -> scopeInitialized(message.prevId)
        is SessionStarted -> {
            activeScope.startSession(message.sessionId, message.testType)
            logger.info { "Session ${message.sessionId} started." }
            sendActiveSessions()
        }
        is SessionCancelled -> {
            activeScope.cancelSession(message)
            logger.info { "Session ${message.sessionId} cancelled." }
            sendActiveSessions()
        }
        is AllSessionsCancelled -> {
            activeScope.cancelAllSessions()
            logger.info { "All sessions cancelled, ids: ${message.ids}." }
            sendActiveSessions()
        }
        is CoverDataPart -> {
            activeScope.addProbes(message.sessionId, message.data)
        }
        is SessionChanged -> {
            activeScope.sessionChanged()
        }
        is SessionFinished -> {
            val sessionId = message.sessionId
            val context = state.coverContext()
            activeScope.finishSession(sessionId) {
                activeScope.updateSummary { it.calculateCoverage(this, context) }
            }?.also {
                sendActiveSessions()
                if (it.any()) {
                    sendActiveScope()
                    sendScopes(buildVersion)
                    calculateAndSendScopeCoverage(activeScope)
                    logger.info { "Session $sessionId finished." }
                } else logger.info { "Session with id $sessionId is empty, it won't be added to the active scope." }
            } ?: logger.info { "No active session with id $sessionId." }
        }
        else -> logger.info { "Message is not supported! $message" }
    }

    private suspend fun calculateAndSendCachedCoverage(
        buildVersion: String
    ) = state.builds[buildVersion]?.let { build ->
        val coverContext = state.coverContext(build.version)
        build.bundleCounters.calculateAndSendBuildCoverage(coverContext, buildVersion, build.coverage.scopeCount)
        val scopes = state.scopeManager.byVersion(
            buildVersion, withData = true
        )
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

    private suspend fun cleanActiveScope(buildVersion: String) {
        send(buildVersion, Routes.ActiveScope, "")
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
        scopes.calculateAndSendBuildCoverage(state.coverContext(), buildVersion)
    }

    private suspend fun Sequence<FinishedScope>.calculateAndSendBuildCoverage(
        context: CoverContext,
        buildVersion: String
    ) {
        state.updateProbes(buildVersion, this)
        val bundleCounters = flatten().calcBundleCounters(context)
        state.updateBundleCounters(buildVersion, bundleCounters)
        bundleCounters.calculateAndSendBuildCoverage(context, buildVersion, scopeCount = count())
    }

    private suspend fun BundleCounters.calculateAndSendBuildCoverage(
        context: CoverContext,
        buildVersion: String,
        scopeCount: Int
    ) {
        val coverageInfoSet = calculateCoverageData(context)
        val buildMethods = coverageInfoSet.buildMethods
        val testsToRun = state.testsToRun(
            buildVersion,
            buildMethods.allModifiedMethods.methods
        )
        val parentVersion = adminData.buildManager[buildVersion]?.parentVersion?.takeIf(String::any)
        val parentCoverageCount = parentVersion?.let {
            state.builds[parentVersion]?.coverage?.count ?: zeroCount
        }
        val risks = parentVersion?.let { buildMethods.risks() } ?: Risks(emptyList(), emptyList())
        val coverageCount = coverageInfoSet.coverage.count
        val buildCoverage = (coverageInfoSet.coverage as BuildCoverage).copy(
            finishedScopesCount = scopeCount,
            prevBuildVersion = parentVersion ?: "",
            arrow = parentCoverageCount?.arrowType(coverageCount),
            diff = parentCoverageCount?.let {
                (coverageCount - it).run { first percentOf second }
            } ?: coverageCount.percentage(),
            riskCount = buildMethods.run {
                Count(
                    covered = newMethods.coveredCount + allModifiedMethods.coveredCount,
                    total = newMethods.totalCount + allModifiedMethods.totalCount
                )
            },
            risks = buildMethods.toRiskSummaryDto()
        )
        coverageInfoSet.sendBuildCoverage(buildVersion, buildCoverage, risks, testsToRun)
        if (buildVersion == agentInfo.buildVersion) {
            val cachedCoverage = state.updateBuildCoverage(
                buildVersion,
                buildCoverage
            ).coverage
            state.updateBuildTests(buildVersion, coverageInfoSet.associatedTests)
            val cachedTests = state.updateTestsToRun(buildVersion, testsToRun).tests
            val summaryDto = cachedCoverage.toSummaryDto(cachedTests)
            val stats = summaryDto.toStatsDto()
            val qualityGate = checkQualityGate(stats)
            Routes.Data().let {
                send(buildVersion, Routes.Data.Stats(it), stats)
                send(buildVersion, Routes.Data.QualityGate(it), qualityGate)
                send(buildVersion, Routes.Data.Recommendations(it), summaryDto.recommendations)
                send(buildVersion, Routes.Data.TestsToRun(it), summaryDto.testsToRun)
            }
            send(summaryDto)
            state.storeBuild(buildVersion)
        }
    }

    private suspend fun CoverageInfoSet.sendBuildCoverage(
        buildVersion: String,
        buildCoverage: BuildCoverage,
        risks: Risks,
        testsToRun: GroupedTests
    ) = Routes.Build().let { buildRoute ->
        val coverageRoute = Routes.Build.Coverage(buildRoute)
        send(buildVersion, coverageRoute, buildCoverage)
        val methodSummaryDto = buildMethods.toSummaryDto().copy(risks = buildMethods.toRiskSummaryDto())
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
        send(buildVersion, Routes.Build.TestsUsages(buildRoute), testsUsagesInfoByType)
        Routes.Build.Summary.Tests(Routes.Build.Summary(buildRoute)).let {
            send(buildVersion, Routes.Build.Summary.Tests.All(it), coverageByTests.all)
            send(buildVersion, Routes.Build.Summary.Tests.ByType(it), coverageByTests.byType)
        }
        Routes.Build.Summary(buildRoute).let {
            send(buildVersion, Routes.Build.Summary.TestsToRun(it), testsToRun.toSummary())
        }
        send(buildVersion, Routes.Build.MethodsCoveredByTest(buildRoute), methodsCoveredByTest)
        send(buildVersion, Routes.Build.MethodsCoveredByTestType(buildRoute), methodsCoveredByTestType)
        send(buildVersion, Routes.Build.Risks(buildRoute), risks)
        send(buildVersion, Routes.Build.TestsToRun(buildRoute), TestsToRun(testsToRun))
    }

    private suspend fun Plugin.send(
        summaryDto: SummaryDto
    ) {
        val serviceGroup = agentInfo.serviceGroup
        if (serviceGroup.isNotEmpty()) {
            val agentSummary = AgentSummaryDto(
                id = agentId,
                buildVersion = buildVersion,
                name = agentInfo.name,
                summary = summaryDto
            )
            val aggregatedMessage = aggregator(serviceGroup, agentSummary) ?: summaryDto
            val summaries = aggregator.getSummaries(serviceGroup) ?: emptyList()
            Routes.ServiceGroup().let { groupParent ->
                sendToGroup(
                    destination = Routes.ServiceGroup.Summary(groupParent),
                    message = ServiceGroupSummaryDto(
                        name = serviceGroup,
                        aggregated = aggregatedMessage,
                        summaries = summaries
                    )
                )
                Routes.ServiceGroup.Data(groupParent).let {
                    sendToGroup(
                        destination = Routes.ServiceGroup.Data.TestsToRun(it),
                        message = aggregatedMessage.testsToRun
                    )
                    sendToGroup(
                        destination = Routes.ServiceGroup.Data.Recommendations(it),
                        message = aggregatedMessage.recommendations
                    )
                }
            }
        }
    }

    internal suspend fun calculateAndSendScopeCoverage(
        scope: Scope = activeScope,
        buildVersion: String = this.buildVersion
    ) {
        val context = state.coverContext(buildVersion)
        val bundleCounters = scope.calcBundleCounters(context)
        val coverageInfoSet = bundleCounters.calculateCoverageData(context, scope)
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
        send(buildVersion, Routes.Scope.TestsUsages(scope), testsUsagesInfoByType)
        Routes.Scope.Summary.Tests(Routes.Scope.Summary(scope)).let {
            send(buildVersion, Routes.Scope.Summary.Tests.All(it), coverageByTests.all)
            send(buildVersion, Routes.Scope.Summary.Tests.ByType(it), coverageByTests.byType)
        }
        send(buildVersion, Routes.Scope.MethodsCoveredByTest(scope), methodsCoveredByTest)
        send(buildVersion, Routes.Scope.MethodsCoveredByTestType(scope), methodsCoveredByTestType)
    }

    internal suspend fun cleanTopics(id: String) = Routes.Scope(id).let { scope ->
        send(buildVersion, Routes.Scope.AssociatedTests(scope), "")
        Routes.Scope.Methods(scope).let {
            send(buildVersion, it, "")
            send(buildVersion, Routes.Scope.Methods.All(it), "")
            send(buildVersion, Routes.Scope.Methods.New(it), "")
            send(buildVersion, Routes.Scope.Methods.Modified(it), "")
            send(buildVersion, Routes.Scope.Methods.Deleted(it), "")
        }
        val coverageRoute = Routes.Scope.Coverage(scope)
        send(buildVersion, coverageRoute, "")
        classDataOrNull()?.let { classData ->
            val pkgsRoute = Routes.Scope.Coverage.Packages(coverageRoute)
            classData.packageTree.packages.forEach {
                send(buildVersion, Routes.Scope.Coverage.Packages.Package(it.name, pkgsRoute), "")
            }
        }
        send(buildVersion, Routes.Scope.TestsUsages(scope), "")
    }

    override suspend fun dropData() {
    }

    internal suspend fun send(buildVersion: String, destination: Any, message: Any) {
        sender.send(AgentSendContext(agentInfo.id, buildVersion), destination, message)
    }

    private fun classDataOrNull() = state.data as? ClassData

    private fun agentState() = AgentState(
        storeClient = storeClient,
        agentInfo = agentInfo,
        adminData = adminData
    )
}
