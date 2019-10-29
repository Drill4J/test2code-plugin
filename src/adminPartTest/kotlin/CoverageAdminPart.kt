package com.epam.drill.plugins.coverage

import com.epam.drill.common.*
import com.epam.drill.plugin.api.*
import com.epam.drill.plugin.api.end.*
import com.epam.drill.plugin.api.message.*
import com.epam.drill.plugins.coverage.test.bar.*
import com.epam.drill.plugins.coverage.test.foo.*
import com.epam.kodux.*
import io.ktor.locations.*
import kotlinx.coroutines.*
import java.io.*
import java.util.concurrent.*
import kotlin.collections.set
import kotlin.reflect.full.*
import kotlin.test.*

const val AGENT_BUILD_VERSION = "1.0.1"

class CoverageAdminPartTest {
    private val agentInfo = AgentInfo(
        id = "id",
        name = "test",
        status = AgentStatus.ONLINE,
        groupName = "test",
        description = "test",
        ipAddress = "127.0.0.1",
        buildVersion = AGENT_BUILD_VERSION,
        buildAlias = "test alias",
        buildVersions = mutableSetOf()
    )
    private val ws = SenderStub()

    private val adminData = AdminDataStub()

    private var store: StoreClient = StoreManger(File("test-storage")).agentStore("test")

    private var coverageController = CoverageAdminPart(adminData, ws, store, agentInfo, "test")

    lateinit var agentState: AgentState

    @BeforeTest
    fun init() {
        if (!store.environment.isOpen) {
            store = StoreManger(File("test-storage")).agentStore("test")
            coverageController = CoverageAdminPart(adminData, ws, store, agentInfo, "test")
        }
        agentState = coverageController.agentState
        store.clear()
    }

    @AfterTest
    fun finalize() {
        agentStates.map.keySet().forEach { agentStates.remove(it) }
        if (store.environment.isOpen) {
            store.close()
        }
    }

    @Test
    fun `should have some state before init`() {
        assertTrue { agentStates.isNotEmpty() }
    }

    @Test
    @Ignore
    fun `should send messages to WebSocket on empty data`() {
        sendInit()
        val sessionId = "xxx"
        sendMessage(SessionStarted(sessionId, "", currentTimeMillis()))
        val finished = SessionFinished(sessionId, currentTimeMillis())
        sendMessage(finished)
        val scopeDestinationPrefix = "/scope/${agentState.activeScope.id}"
        assertTrue { ws.sent["$scopeDestinationPrefix/methods"] != null }
        assertTrue { ws.sent["$scopeDestinationPrefix/coverage"] != null }
        assertTrue { ws.sent["$scopeDestinationPrefix/coverage-by-packages"] != null }
    }

    @Test
    fun `should preserve coverage for packages`() {
        sendInit()
        // Count of Classes in package for test
        val countClassesInPackage = 1
        // Count of packages for test
        val countPackages = 3
        // Total count of Classes for test
        val countAllClasses = 3
        // Total count of Methods for test
        val countAllMethods = 6

        val sessionId = "xxx"

        val started = SessionStarted(sessionId, "", currentTimeMillis())

        sendMessage(started)

        val finished = SessionFinished(sessionId, currentTimeMillis())

        sendMessage(finished)

        assertNotNull(JavaPackageCoverage)

        val methods = ws.javaPackagesCoverage.flatMap { it.classes }.flatMap { it.methods }

        assertEquals(countClassesInPackage, ws.javaPackagesCoverage.first().classes.size)
        assertEquals("Dummy", ws.javaPackagesCoverage.first().classes.first().name)
        assertEquals(countPackages, ws.javaPackagesCoverage.size)
        assertEquals(countAllClasses, ws.javaPackagesCoverage.count { it.classes.isNotEmpty() })
        assertEquals(countAllMethods, methods.size)
    }

    @Test
    fun `empty activeScope should not be saved during switch to new scope`() {
        sendInit()
        runBlocking { coverageController.doAction(SwitchActiveScope(ActiveScopeChangePayload("testScope"))) }
        assertEquals("testScope", agentState.activeScope.name)
        runBlocking {
            coverageController.doAction(SwitchActiveScope(ActiveScopeChangePayload("testScope2", true)))
            assertNull(agentState.scopeManager.getScope("testScope"))
        }
    }

    @Test
    fun `not empty activeScope should switch to a specified one with previous scope deletion`() {
        sendInit()
        runBlocking { coverageController.doAction(SwitchActiveScope(ActiveScopeChangePayload("testScope"))) }
        assertEquals("testScope", agentState.activeScope.name)
        runBlocking {
            appendSessionStub(agentState, classesBytes(), (agentState.classesData() as ClassesData).totalInstructions)
            coverageController.doAction(SwitchActiveScope(ActiveScopeChangePayload("testScope2")))
            assertNull(agentState.scopeManager.getScope("testScope"))
        }
    }

    @Test
    fun `not empty activeScope should switch to a specified one with saving previous scope`() {
        sendInit()
        runBlocking { coverageController.changeActiveScope(ActiveScopeChangePayload("testScope66")) }
        assertEquals("testScope66", agentState.activeScope.name)
        runBlocking {
            appendSessionStub(
                agentState,
                classesBytes(),
                (agentState.classesData() as ClassesData).totalInstructions
            )
        }
        val allScopes = runBlocking {
            coverageController.doAction(SwitchActiveScope(ActiveScopeChangePayload("testScope6", true)))
            agentState.scopeManager.allScopes()
        }
        assertTrue { allScopes.any { it.name == "testScope66" } }
    }

    @Test
    fun `DropScope action deletes the specified scope data from storage`() {
        sendInit()
        runBlocking {
            coverageController.doAction(SwitchActiveScope(ActiveScopeChangePayload("testDropScope")))
            appendSessionStub(agentState, classesBytes(), (agentState.classesData() as ClassesData).totalInstructions)
        }
        val allScopesBeforeSwitch = runBlocking {
            coverageController.doAction(SwitchActiveScope(ActiveScopeChangePayload("testDropScope2", true)))
            agentState.scopeManager.allScopes()
        }
        val id = allScopesBeforeSwitch.find { it.name == "testDropScope" }?.id
        assertNotNull(id)
        val allScopesAfterSwitch = runBlocking {
            coverageController.doAction(DropScope(ScopePayload(id)))
            agentState.scopeManager.allScopes()
        }
        val deleted = allScopesAfterSwitch.find { it.id == id }
        assertNull(deleted)
    }

    @Test
    fun `active scope renaming process goes correctly`() {
        sendInit()
        runBlocking { coverageController.doAction(SwitchActiveScope(ActiveScopeChangePayload("renameActiveScope1"))) }
        assertEquals(agentState.activeScope.summary.name, "renameActiveScope1")
        val activeId = agentState.activeScope.id
        runBlocking { coverageController.doAction(RenameScope(RenameScopePayload(activeId, "renameActiveScope2"))) }
        assertEquals(agentState.activeScope.summary.name, "renameActiveScope2")
    }

    @Test
    fun `finished scope renaming process goes correctly`() {
        sendInit()
        runBlocking {
            coverageController.doAction(SwitchActiveScope(ActiveScopeChangePayload("renameFinishedScope1")))
            appendSessionStub(agentState, classesBytes(), (agentState.classesData() as ClassesData).totalInstructions)
        }
        val allScopes = runBlocking {
            coverageController.doAction(
                SwitchActiveScope(
                    ActiveScopeChangePayload(
                        "renameFinishedScope2",
                        true
                    )
                )
            )
            agentState.scopeManager.allScopes()
        }
        val finishedId = allScopes.find { it.name == "renameFinishedScope1" }?.id!!
        runBlocking { coverageController.doAction(RenameScope(RenameScopePayload(finishedId, "renamedScope1"))) }
        val renamed = runBlocking { agentState.scopeManager.getScope(finishedId)!! }
        assertEquals(renamed.name, "renamedScope1")
    }

    @Test
    fun `neither active nor finished scope can be renamed to an existing scope name`() {
        sendInit()
        runBlocking {
            coverageController.doAction(SwitchActiveScope(ActiveScopeChangePayload("occupiedName1")))
            appendSessionStub(agentState, classesBytes(), (agentState.classesData() as ClassesData).totalInstructions)
            coverageController.doAction(SwitchActiveScope(ActiveScopeChangePayload("occupiedName2", true)))
            appendSessionStub(agentState, classesBytes(), (agentState.classesData() as ClassesData).totalInstructions)
        }
        val allScopes = runBlocking {
            coverageController.doAction(SwitchActiveScope(ActiveScopeChangePayload("freeName", true)))
            agentState.scopeManager.allScopes()
        }
        val finishedId = allScopes.find { it.name == "occupiedName1" }?.id!!
        runBlocking {
            coverageController.doAction(RenameScope(RenameScopePayload(finishedId, "occupiedName2")))
            assertEquals(agentState.scopeManager.getScope(finishedId)!!.name, "occupiedName1")
        }
        val activeId = agentState.activeScope.id
        runBlocking { coverageController.doAction(RenameScope(RenameScopePayload(activeId, "occupiedName2"))) }
        assertEquals(agentState.activeScope.summary.name, "freeName")
    }

    @Test
    fun `not possible to switch scope to a new one with already existing name`() {
        sendInit()
        runBlocking {
            coverageController.doAction(SwitchActiveScope(ActiveScopeChangePayload("occupiedName")))
            appendSessionStub(agentState, classesBytes(), (agentState.classesData() as ClassesData).totalInstructions)
        }
        val activeId1 = agentState.activeScope.id
        runBlocking { coverageController.doAction(SwitchActiveScope(ActiveScopeChangePayload("occupiedName", true))) }
        val activeId2 = agentState.activeScope.id
        assertEquals(activeId1, activeId2)
    }

    @Test
    fun `should switch agent data ref to ClassDataBuilder on init`() = runBlocking {
        val initInfo = InitInfo(3, "hello")
        sendMessage(initInfo)
        assertEquals(1, agentStates.count())
        val agentData = agentStates[agentInfo.id]?.data
        assertTrue { agentData is ClassDataBuilder }
    }

    private fun appendSessionStub(agentState: AgentState, classesBytes: ClassesBytes, totalInstructions: Int) {
        agentState.activeScope.update(
            FinishedSession(
                "testSession",
                "MANUAL",
                mapOf()
            ),
            classesBytes,
            totalInstructions
        )
    }

    private fun sendInit() {
        val initInfo = InitInfo(3, "Start initialization")
        sendMessage(initInfo)
        sendMessage(Initialized())
    }

    private fun sendMessage(message: CoverMessage) {
        val messageStr = commonSerDe.stringify(CoverMessage.serializer(), message)
        runBlocking {
            coverageController.processData(DrillMessage("", messageStr))
        }
    }

    private fun classesBytes() = adminData.buildManager[agentInfo.buildVersion]?.classesBytes ?: emptyMap()

}

class SenderStub : Sender {

    val sent = ConcurrentHashMap<String, Any>()

    lateinit var javaPackagesCoverage: List<JavaPackageCoverage>

    override suspend fun send(agentId: String, buildVersion: String, destination: Any, message: Any) {

        val destination = destination as? String?:destination::class.findAnnotation<Location>()!!.path!!
        if (message.toString().isNotEmpty()) {
            sent[destination] = message
            if (destination.endsWith("/coverage-by-packages")) {
                @Suppress("UNCHECKED_CAST")
                javaPackagesCoverage = message as List<JavaPackageCoverage>
            }
        }
    }
}

class AdminDataStub : AdminData {
    private val manager: BuildManagerStub = BuildManagerStub()
    override val buildManager = manager
}

class BuildManagerStub : BuildManager {
    override val buildInfos: Map<String, BuildInfo> =
        mapOf(
            AGENT_BUILD_VERSION to BuildInfo(
                buildVersion = AGENT_BUILD_VERSION,
                classesBytes = mapOf(
                    parseClass(Dummy::class.java),
                    parseClass(BarDummy::class.java),
                    parseClass(FooDummy::class.java)
                )
            )
        )

    private fun parseClass(clazz: Class<*>) = clazz.path to clazz.readBytes()

    override val summaries: List<BuildSummary> = buildInfos.values.map { it.buildSummary }

    override operator fun get(buildVersion: String): BuildInfo? = buildInfos[buildVersion]

}
