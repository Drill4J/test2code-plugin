package com.epam.drill.plugins.coverage


import com.epam.drill.common.*
import com.epam.drill.plugin.api.*
import com.epam.drill.plugin.api.end.*
import com.epam.drill.plugin.api.message.*
import com.epam.kodux.*
import org.jacoco.core.analysis.*
import org.jacoco.core.data.*


internal val agentStates = AtomicCache<String, AgentState>()

@Suppress("unused", "MemberVisibilityCanBePrivate")
class CoverageAdminPart(
    adminData: AdminData,
    sender: Sender,
    storeClient: StoreClient,
    agentInfo: AgentInfo,
    id: String
) : AdminPluginPart<Action>(adminData, sender, storeClient, agentInfo, id) {

    override val serDe: SerDe<Action> = commonSerDe

    private val agentId = agentInfo.id

    private val buildVersion = agentInfo.buildVersion

    internal val agentState: AgentState = agentStates(agentId) { state ->
        when (state?.agentInfo) {
            agentInfo -> state
            else -> AgentState(storeClient, agentInfo, state)
        }
    }!!

    private val activeScope get() = agentState.activeScope

    override suspend fun doAction(action: Action): Any {
        return when (action) {
            is SwitchActiveScope -> changeActiveScope(action.payload)
            is RenameScope -> renameScope(action.payload)
            is ToggleScope -> toggleScope(action.payload.scopeId)
            is DropScope -> dropScope(action.payload.scopeId)
            is StartNewSession -> {
                val startAgentSession = StartSession(
                    payload = StartSessionPayload(
                        sessionId = genUuid(),
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
            agentState.scopeNotExisting(payload.scopeId) ->
                StatusMessage(
                    StatusCodes.NOT_FOUND,
                    "Failed to rename scope with id ${payload.scopeId}: scope not found"
                )
            agentState.scopeNameNotExisting(payload.scopeName, buildVersion) -> {
                agentState.renameScope(payload.scopeId, payload.scopeName)
                val scope: Scope = agentState.scopeManager.getScope(payload.scopeId) ?: activeScope
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

    internal suspend fun processData(coverMsg: CoverMessage): Any {
        when (coverMsg) {
            is InitInfo -> {
                agentState.init()
                println(coverMsg.message) //log init message
                println("${coverMsg.classesCount} classes to load")
            }
            is Initialized -> {
                agentState.initialized(adminData.buildManager[buildVersion])
                val classesData = agentState.classesData(buildVersion) as ClassesData
                cleanActiveScope(classesData.prevAgentInfo)
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
                val scope = agentState.activeScope
                when (val session = scope.finishSession(coverMsg)) {
                    null -> println("No active session for sessionId ${coverMsg.sessionId}")
                    else -> {
                        if (session.any()) {
                            val totalInstructions = (agentState.classesData() as ClassesData).totalInstructions
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
        val classesData = agentState.classesData(buildVersion) as ClassesData
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

        val coverageBlock = Coverage(
            coverage = totalCoveragePercent,
            diff = totalCoveragePercent - classesData.prevBuildCoverage,
            previousBuildInfo = classesData.prevAgentInfo.buildVersion
                to classesData.prevAgentInfo.buildAlias,
            diff = totalCoveragePercent - agentState.classesData().prevBuildCoverage,
            previousBuildInfo = agentState.classesData().prevAgentInfo?.buildVersion.orEmpty()
                    to agentState.classesData().prevAgentInfo?.buildAlias.orEmpty(),
            coverageByType = coverageByType,
            arrow = if (isBuildCvg) classesData.arrowType(totalCoveragePercent) else null
        )
        println(coverageBlock)

        val methodsChanges = buildInfo?.methodChanges ?: MethodChanges()
        val buildMethods = calculateBuildMethods(methodsChanges, bundleCoverage)

        val packageCoverage = packageCoverage(bundleCoverage, assocTestsMap)
        val testUsages = testUsages(classesBytes.bundlesByTests(finishedSessions), classesData.totalInstructions)

        agentState.testsAssociatedWithBuild.add(buildVersion, associatedTests)

        return CoverageInfoSet(
            associatedTests,
            coverageBlock,
            buildMethods,
            packageCoverage,
            testUsages
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
        sender.send(agentId, buildVersion, "/active-sessions", activeSessions)
    }

    internal suspend fun sendActiveScope() {
        val activeScopeSummary = agentState.activeScope.summary
        sender.send(agentId, buildVersion, "/active-scope", activeScopeSummary)
        sendScopeSummary(activeScopeSummary)
    }

    internal suspend fun cleanActiveScope(agentInfo: AgentInfo) {
        sender.send(agentId, agentInfo.buildVersion, "/active-scope", "")
    }

    internal suspend fun sendScopeSummary(scopeSummary: ScopeSummary, buildVersion: String = this.buildVersion) {
        sender.send(agentId, buildVersion, "/scope/${scopeSummary.id}", scopeSummary)
    }

    internal suspend fun sendScopes(buildVersion: String = this.buildVersion) {
        sender.send(agentId, buildVersion, "/scopes", agentState.scopeSummariesByBuild(buildVersion))
    }

    internal suspend fun toggleScope(scopeId: String): StatusMessage {
        agentState.toggleScope(scopeId)
        return agentState.scopeManager.getScope(scopeId)?.let { scope ->
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
        return agentState.scopeManager.delete(scopeId)?.let { scope ->
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
        if (agentState.scopeNameNotExisting(scopeChange.scopeName, buildVersion)) {
            val prevScope = agentState.changeActiveScope(scopeChange.scopeName.trim())
            if (scopeChange.savePrevScope) {
                if (prevScope.any()) {
                    val finishedScope = prevScope.finish(scopeChange.prevScopeEnabled)
                    sendScopeSummary(finishedScope.summary)
                    println("$finishedScope have been saved.")
                    agentState.scopeManager.saveScope(finishedScope)
                    if (finishedScope.enabled) {
                        calculateAndSendBuildCoverage()
                    }
                } else {
                    println("$prevScope is empty, it won't be added to the build.")
                    cleanTopics(prevScope.id)
                }
            }
            val activeScope = agentState.activeScope
            println("Current active scope $activeScope")
            calculateAndSendActiveScopeCoverage()
            sendScopeMessages()
            StatusMessage(StatusCodes.OK, "Switched to the new scope \'${scopeChange.scopeName}\'")
        } else StatusMessage(
            StatusCodes.CONFLICT,
            "Failed to switch to a new scope: name ${scopeChange.scopeName} is already in use"
        )

    internal suspend fun sendCalcResults(
        cis: CoverageInfoSet, path: String = "",
        buildVersion: String = this.buildVersion
    ) {
        // TODO extend destination with plugin id
        if (cis.associatedTests.isNotEmpty()) {
            println("Assoc tests - ids count: ${cis.associatedTests.count()}")
            sender.send(agentId, buildVersion, "$path/associated-tests", cis.associatedTests)
        }
        sender.send(agentId, buildVersion, "$path/coverage", cis.coverage)
        sender.send(agentId, buildVersion, "$path/coverage-by-packages", cis.packageCoverage)
        sender.send(agentId, buildVersion, "$path/methods", cis.buildMethods)
        sender.send(agentId, buildVersion, "$path/tests-usages", cis.testUsages)
    }


    internal suspend fun calculateAndSendBuildCoverage(buildVersion: String = this.buildVersion) {
        val sessions = agentState.scopeManager.scopesByBuildVersion(buildVersion)
            .filter { it.enabled }
            .flatMap { it.probes.values.flatten() }
            .asSequence()
        val coverageInfoSet = calculateCoverageData(sessions, buildVersion, true)
        agentState.lastBuildCoverage = coverageInfoSet.coverage.coverage
        sendCalcResults(coverageInfoSet, "/build", buildVersion)
        sendRisks(buildVersion, coverageInfoSet.buildMethods)
        sendTestsToRun(buildVersion, coverageInfoSet.buildMethods)
    }

    internal suspend fun sendTestsToRun(buildVersion: String, buildMethods: BuildMethods) {
        val tests = TestsToRun(
            agentState.testsAssociatedWithBuild.getTestsAssociatedWithMethods(
                buildVersion,
                agentState,
                buildMethods.allModified
            )
        )
        sender.send(agentId, buildVersion, "/build/tests-to-run", tests)
    }

    internal suspend fun sendRisks(buildVersion: String, buildMethods: BuildMethods) {
        val risks = risks(buildMethods)
        sender.send(agentId, buildVersion, "/build/risks", risks)
    }

    internal fun risks(buildMethods: BuildMethods): Risks {
        val newRisks = buildMethods.newMethods.methods.filter { it.coverageRate == CoverageRate.MISSED }
        val modifiedRisks = buildMethods.allModified.filter { it.coverageRate == CoverageRate.MISSED }
        return Risks(newRisks, modifiedRisks)
    }

    internal suspend fun calculateAndSendActiveScopeCoverage() {
        val activeScope = agentState.activeScope
        val coverageInfoSet = calculateCoverageData(activeScope)
        sendActiveSessions()
        sendCalcResults(coverageInfoSet, "/scope/${activeScope.id}")
    }

    internal suspend fun cleanTopics(id: String) {
        sender.send(agentId, buildVersion, "/scope/$id/associated-tests", "")
        sender.send(agentId, buildVersion, "/scope/$id/methods", "")
        sender.send(agentId, buildVersion, "/scope/$id/tests-usages", "")
        sender.send(agentId, buildVersion, "/scope/$id/coverage-by-packages", "")
        sender.send(agentId, buildVersion, "/scope/$id/coverage", "")
    }

    override suspend fun dropData() {
        val buildInfo = adminData.buildManager[buildVersion]!!
        agentInfo.buildVersions.map { it.id }.forEach {
            sender.send(agentId, it, "/scopes", "")
            sender.send(agentId, it, "/build/associated-tests", "")
            sender.send(agentId, it, "/build/coverage-new", "")
            sender.send(agentId, it, "/build/methods", "")
            sender.send(agentId, it, "/build/tests-usages", "")
            sender.send(agentId, it, "/build/coverage-by-packages", "")
            sender.send(agentId, it, "/build/coverage", "")
        }
        val classesBytes = buildInfo.classesBytes
        agentState.reset()
        processData(InitInfo(classesBytes.keys.count(), ""))
        agentState.initialized(buildInfo)
        processData(Initialized())
    }


}
