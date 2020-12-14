package com.epam.drill.plugins.test2code

import com.epam.drill.common.*
import com.epam.drill.plugin.api.*
import com.epam.kodux.*
import jetbrains.exodus.entitystore.*
import kotlinx.coroutines.*
import java.io.*
import java.util.*
import kotlin.test.*

class AgentStateTest {

    private val storageDir = File("build/tmp/test/storages/${this::class.simpleName}-${UUID.randomUUID()}")

    private val storeClient = StoreClient(PersistentEntityStores.newInstance(storageDir))

    private val agentInfo = AgentInfo(
        id = "ag",
        name = "ag",
        description = "",
        buildVersion = "0.1.0",
        agentType = "JAVA",
        agentVersion = "0.1.0"
    )

    private val adminData = object : AdminData {
        override val classBytes = emptyMap<String, ByteArray>()
    }

    @AfterTest
    fun cleanStore() {
        storeClient.store.close()
        storageDir.deleteRecursively()
    }

    @Test
    fun `set initial build as baseline`() = runBlocking {
        val state = AgentState(storeClient, agentInfo, adminData)
        state.initialized()
        storeClient.findById<GlobalAgentData>(agentInfo.id)!!.baseline.let {
            assertEquals(agentInfo.buildVersion, it.version)
            assertEquals("", it.parentVersion)
        }
    }

    @Test
    fun `toggleBaseline - no toggling on initial version`() = runBlocking {
        val state = AgentState(storeClient, agentInfo, adminData)
        state.initialized()
        assertNull(state.toggleBaseline())
    }

    @Test
    fun `toggleBaseline - 2 builds`() = runBlocking {
        AgentState(storeClient, agentInfo, adminData).apply {
            initialized()
            storeBuild()
        }
        val version1 = agentInfo.buildVersion
        storeClient.findById<GlobalAgentData>(agentInfo.id)!!.baseline.let {
            assertEquals(version1, it.version)
            assertEquals("", it.parentVersion)
        }
        val version2 = "0.2.0"
        val state2 = AgentState(storeClient, agentInfo.copy(buildVersion = version2), adminData)
        state2.initialized()
        assertEquals(version2, state2.toggleBaseline())
        storeClient.findById<GlobalAgentData>(agentInfo.id)!!.baseline.let {
            assertEquals(version2, it.version)
            assertEquals(version1, it.parentVersion)
        }
        assertEquals(version1, state2.toggleBaseline())
        storeClient.findById<GlobalAgentData>(agentInfo.id)!!.baseline.let {
            assertEquals(version1, it.version)
            assertEquals("", it.parentVersion)
        }
        assertEquals(version2, state2.toggleBaseline())
        Unit
    }

    @Test
    fun `initialized - preserve parent version after restart`() = runBlocking {
        AgentState(storeClient, agentInfo, adminData).apply {
            initialized()
            storeBuild()
        }
        val version1 = agentInfo.buildVersion
        val version2 = "0.2.0"
        AgentState(storeClient, agentInfo.copy(buildVersion = version2), adminData).apply {
            initialized()
            storeBuild()
            assertEquals(version2, toggleBaseline())
            assertEquals(version1, coverContext().parentBuild?.version)
        }
        AgentState(storeClient, agentInfo.copy(buildVersion = version2), adminData).apply {
            initialized()
            assertEquals(version1, coverContext().parentBuild?.version)
        }
        Unit
    }
}
