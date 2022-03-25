/**
 * Copyright 2020 - 2022 EPAM Systems
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
package com.epam.drill.plugins.test2code.e2e

import com.epam.drill.admin.common.serialization.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.builds.*
import com.epam.drill.e2e.*
import com.epam.drill.e2e.plugin.*
import com.epam.drill.plugins.test2code.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.api.TestResult.*
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.drill.plugins.test2code.global_filter.*
import e2e.*
import io.kotlintest.*
import io.kotlintest.matchers.doubles.*
import io.kotlintest.matchers.numerics.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlin.test.*


class FiltersTest : E2EPluginTest() {

    @Test
    fun `should create, update, delete filter and handle edge cases`() {
        var filterId = ""
        val updateName = "updated filter"
        createSimpleAppWithPlugin<CoverageSocketStreams>(timeout = 300000) {
            connectAgent<Build1> { plugUi, build ->
                plugUi.buildCoverage()
                plugUi.coveragePackages()
                plugUi.activeSessions()!!.run {
                    count shouldBe 0
                    testTypes shouldBe emptySet()
                }

                val startNewSession = StartNewSession(StartPayload(MANUAL_TEST_TYPE)).stringify()
                pluginAction(startNewSession) { status, content ->
                    status shouldBe HttpStatusCode.OK
                    val startSession = content!!.parseJsonData<StartAgentSession>()

                    plugUi.activeSessions()!!.run { count shouldBe 1 }

                    runWithSession(startSession.payload.sessionId) {
                        val gt = build.entryPoint()
                        gt.test1()
                        gt.test2()
                        gt.test3()
                    }

                    pluginAction(StopAgentSession(AgentSessionPayload(startSession.payload.sessionId)).stringify()).join()
                }.join()

                plugUi.activeSessions()!!.count shouldBe 0
                val switchScope = SwitchActiveScope(
                    ActiveScopeChangePayload(
                        scopeName = "new2",
                        savePrevScope = true,
                        prevScopeEnabled = true
                    )
                ).stringify()
                pluginAction(switchScope) { status, _ ->
                    status shouldBe HttpStatusCode.OK
                    plugUi.buildCoverage()!!.apply {
                        count.covered shouldBeGreaterThan 0
                    }
                    plugUi.coveragePackages()
                    plugUi.coveragePackages()!!.apply {
                        first().coverage shouldBeGreaterThan 0.0
                    }
                }.join()
                println("filters ${plugUi.filters()}")
                //create filter
                val filterName = "new filter"
                val createFilter = CreateFilter(
                    FilterPayload(
                        name = filterName,
                        attributes = listOf(TestOverviewFilter(TestOverview::result.name,
                            false,
                            values = listOf(FilterValue(PASSED.name))))
                    )
                ).stringify()
                pluginAction(createFilter) { status, content ->
                    println("status createFilter: $status $content")
                    status shouldBe HttpStatusCode.OK
                    val response = StatusMessageResponse.serializer().parse(content!!)
                    filterId = response.message
                    assertEquals(listOf(FilterDto(filterName, filterId)), plugUi.filters())
                }.join()
                pluginAction(createFilter) { status, _ ->
                    println("status createFilter 2: $status")
                    status shouldBe HttpStatusCode.Conflict
                }.join()
                //update filter
                val updateFilterNewName = UpdateFilter(
                    FilterPayload(
                        name = updateName,
                        id = filterId,
                        attributes = listOf(TestOverviewFilter(TestOverview::result.name,
                            false,
                            values = listOf(FilterValue(PASSED.name))))
                    )
                )
                pluginAction(updateFilterNewName.stringify()) { status, content ->
                    println("status update : $status $content")
                    status shouldBe HttpStatusCode.OK
                    assertEquals(listOf(FilterDto(updateName, filterId)), plugUi.filters())
                }.join()
                val notExistUpdate =
                    updateFilterNewName.copy(payload = updateFilterNewName.payload.copy(id = "not exist"))
                pluginAction(notExistUpdate.stringify()) { status, content ->
                    println("status update : $status $content")
                    status shouldBe HttpStatusCode.BadRequest
                }.join()
                //delete filter
                val deleteFilter = DeleteFilter(DeleteFilterPayload(id = filterId)).stringify()
                pluginAction(deleteFilter) { status, content ->
                    println("status delete : $status $content")
                    status shouldBe HttpStatusCode.OK
                    assertEquals(null, plugUi.filters())
                }.join()
                delay(100)
            }
//                .reconnect<Build2> { plugUi, _ ->
            //todo reconnect and Apply. Status is not online
//                plugUi.buildCoverage()!!.apply {
//                    percentage shouldBe 0.0
//                    byTestType shouldBe emptyList()
//                }
//                delay(200)
//                val applyFilter = ApplyFilter(
//                    ApplyPayload(filterId)
//                ).stringify()
//                pluginAction(applyFilter) { status, content ->
//                    println("status apply: $status $content")
//                    status shouldBe HttpStatusCode.OK
//                    assertEquals(listOf(FilterDto(updateName, filterId)), plugUi.filters())
//                }.join()
//            }
        }
    }

}
