package com.epam.drill.plugins.test2code


import com.epam.drill.common.*
import com.epam.drill.plugin.api.*
import com.epam.drill.plugin.api.end.*
import com.epam.drill.plugin.api.message.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.api.routes.*
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.drill.plugins.test2code.storage.*
import com.epam.kodux.*
import mu.*

@Suppress("unused", "MemberVisibilityCanBePrivate")
class Test2CodeAdminPart(
    adminData: AdminData,
    sender: Sender,
    val storeClient: StoreClient,
    agentInfo: AgentInfo,
    id: String
) : AdminPluginPart<Action>(adminData, sender, storeClient, agentInfo, id) {

    override val serDe: SerDe<Action> = apiSerDe

    lateinit var pluginInstanceState: PluginInstanceState

    val buildVersion = agentInfo.buildVersion

    val buildInfo: BuildInfo? get() = adminData.buildManager[buildVersion]

    val activeScope get() = pluginInstanceState.activeScope

    val agentId = agentInfo.id

    private val logger = KotlinLogging.logger("Plugin $id")

    override suspend fun initialize() {
        pluginInstanceState = pluginInstanceState()
        pluginInstanceState.readScopeCounter()?.let { processData(Initialized("")) }
    }

    override suspend fun applyPackagesChanges() {
        pluginInstanceState.scopeManager.deleteByVersion(buildVersion)
        storeClient.deleteById<ClassData>(buildVersion)
        pluginInstanceState = pluginInstanceState()
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

    //TODO remove this after the data API has been redesigned
    override suspend fun getPluginData(params: Map<String, String>) = Unit

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
            logger.info { message.message } //log init message
            logger.info { "${message.classesCount} classes to load" }
        }
        is InitDataPart -> {
            (pluginInstanceState.data as? DataBuilder)?.also {
                logger.info { message }
                it += message.astEntities
            }
        }
        is Initialized -> {
            pluginInstanceState.initialized()
            classDataOrNull()?.let {
                send(buildVersion, Routes.Data().let(Routes.Data::Build) , it.toBuildStatsDto())
            }
            initGateSettings()
            initActiveScope()
            sendActiveSessions()
            sendActiveScope()
            calculateAndSendScopeCoverage(activeScope)
            sendScopes(buildVersion)
            calculateAndSendAllCoverage(buildVersion)
            adminData.buildManager.otherVersions(buildVersion).map { it.version }.run {
                forEach { version ->
                    cleanActiveScope(version)
                    sendScopes(version)
                }
                forEach { calculateAndSendAllCoverage(it) }
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
            val context = pluginInstanceState.coverContext()
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

    private suspend fun calculateAndSendAllCoverage(buildVersion: String) {
        val finishedScopes = pluginInstanceState.scopeManager.byVersion(
            buildVersion, withData = true
        )
        finishedScopes.enabled().calculateAndSendBuildCoverage(buildVersion)
        finishedScopes.forEach { scope -> calculateAndSendScopeCoverage(scope, buildVersion) }
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
        send(buildVersion, Routes.Scope(scopeSummary.id), scopeSummary)
    }

    internal suspend fun sendScopes(buildVersion: String = this.buildVersion) {
        val scopes = pluginInstanceState.scopeManager.byVersion(buildVersion)
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
        val scopes = pluginInstanceState.scopeManager.run {
            byVersion(buildVersion, true).enabled()
        }
        scopes.calculateAndSendBuildCoverage(buildVersion)
    }

    private suspend fun Sequence<FinishedScope>.calculateAndSendBuildCoverage(buildVersion: String) {
        val parentVersion = adminData.buildManager[buildVersion]?.parentVersion?.takeIf(String::any)
        val prevCoverage = parentVersion?.let {
            pluginInstanceState.coverages[pluginInstanceState.buildId(parentVersion)]?.count ?: zeroCount
        }
        val context = pluginInstanceState.coverContext(buildVersion)
        val sessions = flatten()
        val coverageInfoSet = sessions.calculateCoverageData(
            context, count(), prevCoverage
        )
        pluginInstanceState.updateBuildTests(buildVersion, coverageInfoSet.associatedTests)
        val buildMethods = coverageInfoSet.buildMethods
        val testsToRun = pluginInstanceState.testsToRun(
            buildVersion,
            buildMethods.allModifiedMethods.methods
        )
        val cachedTests = pluginInstanceState.updateTestsToRun(buildVersion, testsToRun)
        val risks = parentVersion?.let { buildMethods.risks() } ?: Risks(emptyList(), emptyList())
        val buildCoverage = (coverageInfoSet.coverage as BuildCoverage).copy(
            riskCount = buildMethods.run {
                Count(
                    covered = newMethods.coveredCount + allModifiedMethods.coveredCount,
                    total = newMethods.totalCount + allModifiedMethods.totalCount
                )
            }
        )
        val cachedCoverage = pluginInstanceState.updateBuildCoverage(
            buildVersion,
            buildCoverage
        )
        coverageInfoSet.sendBuildCoverage(buildVersion, buildCoverage, risks, testsToRun)
        if (buildVersion == agentInfo.buildVersion) {
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
            pluginInstanceState.storeBuildCoverage(buildVersion)
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
        send(buildVersion, Routes.Build.Methods(buildRoute), buildMethods.toSummaryDto())
        Routes.Build.Methods(buildRoute).let {
            send(buildVersion, Routes.Build.Methods.All(it), buildMethods.totalMethods)
            send(buildVersion, Routes.Build.Methods.New(it), buildMethods.newMethods)
            send(buildVersion, Routes.Build.Methods.Modified(it), buildMethods.allModifiedMethods)
            send(buildVersion, Routes.Build.Methods.Deleted(it), buildMethods.deletedMethods)
            send(buildVersion, Routes.Build.Methods.Unaffected(it), buildMethods.unaffectedMethods)
        }
        val pkgsRoute = Routes.Build.Coverage.Packages(coverageRoute)
        send(buildVersion, pkgsRoute, packageCoverage.map { it.copy(classes = emptyList()) })
        packageCoverage.forEach {
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
        send(buildVersion, Routes.Build.MethodsCoveredByTest(buildRoute), methodsCoveredByTest)
        send(buildVersion, Routes.Build.MethodsCoveredByTestType(buildRoute), methodsCoveredByTestType)
        send(buildVersion, Routes.Build.Risks(buildRoute), risks)
        send(buildVersion, Routes.Build.TestsToRun(buildRoute), TestsToRun(testsToRun))
    }

    private suspend fun Test2CodeAdminPart.send(
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


    internal fun BuildMethods.risks(): Risks {
        val newRisks = newMethods.methods.filter { it.coverageRate == CoverageRate.MISSED }
        val modifiedRisks = allModifiedMethods.methods.filter { it.coverageRate == CoverageRate.MISSED }
        return Risks(newRisks, modifiedRisks)
    }

    internal suspend fun calculateAndSendScopeCoverage(
        scope: Scope = activeScope,
        buildVersion: String = this.buildVersion
    ) {
        val context = pluginInstanceState.coverContext(buildVersion)
        val coverageInfoSet = scope.calculateCoverageData(context)
        coverageInfoSet.sendScopeCoverage(buildVersion, scope.id)
    }

    internal suspend fun CoverageInfoSet.sendScopeCoverage(
        buildVersion: String,
        scopeId: String
    ) = Routes.Scope(scopeId).let { scope ->
        val coverageRoute = Routes.Scope.Coverage(scope)
        send(buildVersion, coverageRoute, coverage)
        send(buildVersion, Routes.Scope.Methods(scope), buildMethods.toSummaryDto())
        Routes.Scope.Methods(scope).let { methods ->
            send(buildVersion, Routes.Scope.Methods.All(methods), buildMethods.totalMethods)
            send(buildVersion, Routes.Scope.Methods.New(methods), buildMethods.newMethods)
            send(buildVersion, Routes.Scope.Methods.Modified(methods), buildMethods.allModifiedMethods)
            send(buildVersion, Routes.Scope.Methods.Deleted(methods), buildMethods.deletedMethods)
            send(buildVersion, Routes.Scope.Methods.Unaffected(methods), buildMethods.unaffectedMethods)
        }
        val pkgsRoute = Routes.Scope.Coverage.Packages(coverageRoute)
        send(buildVersion, pkgsRoute, packageCoverage.map { it.copy(classes = emptyList()) })
        packageCoverage.forEach {
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
        adminData.buildManager.builds.map(BuildInfo::version).forEach { buildVersion ->
            send(buildVersion, Routes.Scopes, "")
            Routes.Build().let { buildRoute ->
                send(buildVersion, Routes.Build.AssociatedTests(buildRoute), "")
                Routes.Build.Methods(buildRoute).let { methodsRoute ->
                    send(buildVersion, methodsRoute, "")
                    send(buildVersion, Routes.Build.Methods.All(methodsRoute), "")
                    send(buildVersion, Routes.Build.Methods.New(methodsRoute), "")
                    send(buildVersion, Routes.Build.Methods.Modified(methodsRoute), "")
                    send(buildVersion, Routes.Build.Methods.Deleted(methodsRoute), "")
                }
                val coverageRoute = Routes.Build.Coverage(buildRoute)
                send(buildVersion, coverageRoute, "")
                classDataOrNull()?.let { classData ->
                    val pkgsRoute = Routes.Build.Coverage.Packages(coverageRoute)
                    classData.packageTree.packages.forEach {
                        send(buildVersion, Routes.Build.Coverage.Packages.Package(it.name, pkgsRoute), "")
                    }
                }
                send(buildVersion, Routes.Build.TestsUsages(buildRoute), "")
            }
        }
        val classesBytes = buildInfo?.classesBytes ?: emptyMap()
        pluginInstanceState = pluginInstanceState()
        processData(InitInfo(classesBytes.keys.count(), ""))
        pluginInstanceState.initialized()
        processData(Initialized())
    }

    internal suspend fun send(buildVersion: String, destination: Any, message: Any) {
        sender.send(AgentSendContext(agentInfo.id, buildVersion), destination, message)
    }

    private fun classDataOrNull() = pluginInstanceState.data as? ClassData

    private fun pluginInstanceState() = PluginInstanceState(
        storeClient = storeClient,
        agentInfo = agentInfo,
        buildManager = adminData.buildManager
    )
}
