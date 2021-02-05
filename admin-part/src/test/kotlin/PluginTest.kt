/**
 * Copyright 2020 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.drill.plugins.test2code

import com.epam.drill.common.*
import com.epam.drill.plugin.api.*
import com.epam.drill.plugin.api.end.*
import com.epam.drill.plugins.test2code.storage.*
import com.epam.kodux.*
import jetbrains.exodus.entitystore.*
import kotlinx.coroutines.*
import java.io.*
import java.util.*
import kotlin.test.*


class PluginTest {

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

    private val sender = EmptySender

    private val adminData = object : AdminData {
        override val classBytes = emptyMap<String, ByteArray>()
    }

    @AfterTest
    fun cleanStore() {
        storeClient.close()
        storageDir.deleteRecursively()
    }

    private suspend fun initPlugin(
        buildVersion: String
    ): Plugin = Plugin(
        adminData,
        sender,
        storeClient,
        agentInfo.copy(buildVersion = buildVersion),
        "test2code"
    ).apply {
        ClassData(buildVersion).store(storeClient)
        initialize()
        return this
    }

    @Test
    fun `cannot toggleBaseline initial build`() = runBlocking {
        val plugin = initPlugin("0.1.0")

        assertEquals(StatusCodes.BAD_REQUEST, plugin.toggleBaseline().code)
    }

    @Test
    fun `toggleBaseline second build`() = runBlocking {
        val version = "0.1.0"
        initPlugin(version)

        val plugin2 = initPlugin("0.2.0")

        assertEquals(version, plugin2.state.coverContext().parentBuild?.version)
        assertEquals(StatusCodes.OK, plugin2.toggleBaseline().code)
        assertEquals(StatusCodes.OK, plugin2.toggleBaseline().code)
    }

    @Test
    fun `cannot toggle initial build after redeploy it`() = runBlocking {
        val plugin = initPlugin("0.1.0")

        initPlugin("0.2.0")

        plugin.initialize()
        assertEquals(StatusCodes.BAD_REQUEST, plugin.toggleBaseline().code)
    }

    @Test
    fun `when redeploy current build it should compare with parent baseline`() = runBlocking {
        val version1 = "0.1.0"
        initPlugin(version1)

        val plugin2 = initPlugin("0.2.0")
        assertEquals(StatusCodes.OK, plugin2.toggleBaseline().code)

        plugin2.initialize()
        assertEquals(version1, plugin2.state.coverContext().parentBuild?.version)
    }

    @Test
    fun `when redeploy stored build - compare it with new baseline and there is able to toggle`() = runBlocking {
        val plugin = initPlugin("0.1.0")

        val version2 = "0.2.0"
        val plugin2 = initPlugin(version2)
        assertEquals(StatusCodes.OK, plugin2.toggleBaseline().code)

        plugin.initialize()
        assertEquals(version2, plugin.state.coverContext().parentBuild?.version)
        assertEquals(StatusCodes.OK, plugin.toggleBaseline().code)
    }

    @Test
    fun `when redeploy stored build with new baseline it will be recalculated with it`() = runBlocking {
        val version1 = "0.1.0"
        initPlugin(version1)

        val version2 = "0.2.0"
        val plugin2 = initPlugin(version2)
        assertEquals(StatusCodes.OK, plugin2.toggleBaseline().code)

        val plugin3 = initPlugin("0.3.0")
        assertEquals(version2, plugin3.state.coverContext().parentBuild?.version)

        plugin2.initialize()
        assertEquals(StatusCodes.OK, plugin2.toggleBaseline().code)

        plugin3.initialize()
        assertEquals(version1, plugin3.state.coverContext().parentBuild?.version)
    }

    @Test
    fun `for the first and second build testsToRunSummaries will be empty`() = runBlocking {
        val version1 = "0.1.0"
        val plugin = initPlugin(version1)
        assertTrue(
            plugin.storeClient.loadTestsToRunSummary(
                buildVersion = version1
            ).isEmpty()
        )

        val version2 = "0.2.0"
        val plugin2 = initPlugin(version2)
        assertTrue(
            plugin2.storeClient.loadTestsToRunSummary(
                buildVersion = version2,
                parentVersion = version1
            ).isEmpty()
        )
    }

    @Test
    fun `for the third build testsToRunSummaries will have one element`() = runBlocking {
        val version1 = "0.1.0"
        initPlugin(version1)
        val version2 = "0.2.0"
        initPlugin(version2)

        val currentVersion = "0.3.0"
        val plugin3 = initPlugin(currentVersion)
        val testsToRunSummary = plugin3.storeClient.loadTestsToRunSummary(
            buildVersion = currentVersion,
            parentVersion = version1
        )
        assertEquals(1, testsToRunSummary.size)
        assertEquals(version2, testsToRunSummary.first().buildVersion)
        assertEquals(version1, testsToRunSummary.first().parentVersion)
    }

    @Test
    fun `testsToRunSummaries should be sorted by timestamp`() = runBlocking {
        val version1 = "0.1.0"
        initPlugin(version1)
        initPlugin("0.2.0")
        initPlugin("0.3.0")
        val currentVersion = "0.4.0"
        val plugin4 = initPlugin(currentVersion)

        val testsToRunSummaries = plugin4.storeClient.loadTestsToRunSummary(
            buildVersion = currentVersion,
            parentVersion = version1
        )
        assertEquals(2, testsToRunSummaries.size)
        assertTrue(testsToRunSummaries.first().lastModifiedAt < testsToRunSummaries.last().lastModifiedAt)
    }

}

private object EmptySender : Sender {
    override suspend fun send(context: SendContext, destination: Any, message: Any) {}

}
