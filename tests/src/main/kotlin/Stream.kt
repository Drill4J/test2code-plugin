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

import com.epam.drill.admin.api.websocket.*
import com.epam.drill.admin.common.*
import com.epam.drill.admin.common.serialization.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.e2e.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.api.routes.*
import io.ktor.http.cio.websocket.*
import io.ktor.locations.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.serialization.*
import kotlinx.serialization.builtins.*
import kotlinx.serialization.json.*
import java.util.concurrent.*
import kotlin.collections.set
import kotlin.reflect.*
import kotlin.reflect.full.*

class CoverageSocketStreams : PluginStreams() {

    init {
        fillMapping(Routes::class)
    }


    private lateinit var iut: SendChannel<Frame>

    override suspend fun initSubscriptions(subscription: AgentSubscription) {
        val message = Subscription.serializer() stringify subscription
        pathToCallBackMapping.filterKeys { !it.contains("{") }.forEach {
            iut.send(Subscribe(it.key, message).toTextFrame())
        }
        activeScope()
        activeSessions()
        associatedTests()
        buildCoverage()
        coveragePackages()
        methods()
        risks()
        tests()
        scopes()
        summary()
    }

    override suspend fun subscribe(subscription: AgentSubscription, destination: String) {
        val message = Subscription.serializer() stringify subscription
        iut.send(uiMessage(Subscribe(destination, message)))
    }

    private val activeScope: Channel<ScopeSummary?> = Channel(Channel.UNLIMITED)
    suspend fun activeScope() = activeScope.receive()

    private val activeSessions: Channel<ActiveSessions?> = Channel(Channel.UNLIMITED)
    suspend fun activeSessions() = activeSessions.receive()

    private val associatedTests: Channel<List<AssociatedTests>?> = Channel(Channel.UNLIMITED)
    suspend fun associatedTests() = associatedTests.receive()

    private val methods: Channel<MethodsSummaryDto?> = Channel(Channel.UNLIMITED)
    suspend fun methods() = methods.receive()

    private val buildCoverage: Channel<BuildCoverage?> = Channel(Channel.UNLIMITED)
    suspend fun buildCoverage() = buildCoverage.receive()

    private val coveragePackages: Channel<List<JavaPackageCoverage>?> = Channel(Channel.UNLIMITED)
    suspend fun coveragePackages() = coveragePackages.receive()

    private val tests: Channel<List<TestCoverageDto>?> = Channel(Channel.UNLIMITED)
    suspend fun tests() = tests.receive()


    private val risks: Channel<List<RiskDto>?> = Channel(Channel.UNLIMITED)
    suspend fun risks() = risks.receive()

    private val scopes: Channel<List<ScopeSummary>?> = Channel(Channel.UNLIMITED)
    suspend fun scopes() = scopes.receive()

    private val summary: Channel<SummaryDto?> = Channel(Channel.UNLIMITED)
    suspend fun summary() = summary.receive()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun queued(incoming: ReceiveChannel<Frame>, out: SendChannel<Frame>, isDebugStream: Boolean) {
        iut = out
        app.launch {

            incoming.consumeEach { frame ->
                when (frame) {
                    is Frame.Text -> {
                        val json = frame.readText().parseJson() as JsonObject
                        if (isDebugStream)
                            println("PLUGIN: $json")
                        val messageType = WsMessageType.valueOf((json[WsSendMessage::type.name] as JsonPrimitive).content)
                        val url = (json[WsSendMessage::destination.name] as JsonPrimitive).content
                        val jsonMessage = json[WsSendMessage::message.name]
                        val content = jsonMessage?.run { toString() }

                        when (messageType) {
                            WsMessageType.MESSAGE -> {

                                when (val resolved = app.resolve(url)) {

                                    is Routes.ActiveScope -> {
                                        activeScope.send(ScopeSummary.serializer(), content)
                                    }
                                    is Routes.ActiveScope.ActiveSessionSummary -> {
                                        activeSessions.send(ActiveSessions.serializer(), content)
                                    }

                                    is Routes.Build.Scopes.FinishedScopes -> {
                                        scopes.send(ListSerializer(ScopeSummary.serializer()), content)
                                    }
                                    is Routes.Build.Scopes.Scope -> {
                                        val scope =
                                            scopeSubscriptions.getValue(resolved.scopeId).first.scope
                                        scope.send(ScopeSummary.serializer(), content)
                                    }

                                    is Routes.Build.Scopes.Scope.AssociatedTests -> {
                                        val associatedTests =
                                            scopeSubscriptions.getValue(resolved.scope.scopeId).first.associatedTests
                                        associatedTests.send(
                                            ListSerializer(AssociatedTests.serializer()), content
                                        )
                                    }

                                    is Routes.Build.Scopes.Scope.Methods -> {
                                        val methods =
                                            scopeSubscriptions.getValue(resolved.scope.scopeId).first.methods
                                        methods.send(
                                            MethodsSummaryDto.serializer(), content
                                        )
                                    }

                                    is Routes.Build.Scopes.Scope.Tests -> {
                                        val tests =
                                            scopeSubscriptions.getValue(resolved.scope.scopeId).first.tests
                                        tests.send(
                                            ListSerializer(TestCoverageDto.serializer()), content
                                        )
                                    }

                                    is Routes.Build.Scopes.Scope.Coverage.Packages -> {
                                        val coveragePackages = run {
                                            scopeSubscriptions.getValue(
                                                resolved.coverage.scope.scopeId
                                            ).first.coveragePackages
                                        }
                                        coveragePackages.send(
                                            ListSerializer(JavaPackageCoverage.serializer()), content
                                        )
                                    }

                                    is Routes.Build.Scopes.Scope.MethodsCoveredByTest.Summary -> {
                                        val methodsCoveredByTest = testSubscriptions
                                            .getValue(resolved.test.scope.scopeId)
                                            .first
                                            .methodsCoveredByTest
                                        methodsCoveredByTest.send(
                                            TestedMethodsSummary.serializer(), content
                                        )
                                    }

                                    is Routes.Build.Scopes.Scope.Coverage -> {
                                        val coverage =
                                            scopeSubscriptions.getValue(resolved.scope.scopeId).first.coverage
                                        coverage.send(ScopeCoverage.serializer(), content)
                                    }

                                    is Routes.Build.AssociatedTests -> {
                                        associatedTests.send(ListSerializer(AssociatedTests.serializer()), content)
                                    }

                                    is Routes.Build.Methods -> {
                                        methods.send(MethodsSummaryDto.serializer(), content)
                                    }

                                    is Routes.Build.Tests -> {
                                        tests.send(
                                            ListSerializer(TestCoverageDto.serializer()), content
                                        )
                                    }

                                    is Routes.Build.Coverage.Packages -> {
                                        coveragePackages.send(
                                            ListSerializer(JavaPackageCoverage.serializer()), content
                                        )
                                    }

                                    is Routes.Build.Coverage -> {
                                        buildCoverage.send(BuildCoverage.serializer(), content)
                                    }

                                    is Routes.Build.Risks -> {
                                        risks.send(ListSerializer(RiskDto.serializer()), content)
                                    }

                                    is Routes.Group.Summary -> {
                                        summary.send(SummaryDto.serializer(), content)
                                    }
                                    else -> println("!!!$url ignored")
                                }

                            }
                            else -> println("!!!!!$messageType not supported!")
                        }
                    }
                    else -> println("!!!!${frame.frameType} not supported!")
                }
            }
        }
    }

    private suspend fun <T> Channel<T?>.send(
        serializer: KSerializer<T>,
        message: String?
    ) {
        val sentMessage = message?.takeIf {
            it.any() && it != "[]" && it != "\"\""
        }?.let { serializer parse it }
        send(sentMessage)
    }

    private val scopeSubscriptions = ConcurrentHashMap<String, Pair<ScopeContext, suspend ScopeContext.() -> Unit>>()
    private val testSubscriptions = ConcurrentHashMap<String, Pair<TestChannels, suspend TestChannels.() -> Unit>>()

    suspend fun subscribeOnScope(
        scopeId: String,
        agentId: String = info.agentId,
        buildVersion: String = info.buildVersionHash,
        block: suspend ScopeContext.() -> Unit
    ) {
        val scope = scopeById(scopeId)
        val coverage = Routes.Build.Scopes.Scope.Coverage(scope)

        val scopeContext = ScopeContext()
        scopeSubscriptions[scopeId] = scopeContext to block

        arrayOf(
            scope,
            Routes.Build.Scopes.Scope.Methods(scope),
            coverage,
            Routes.Build.Scopes.Scope.Coverage.Packages(coverage),
            Routes.Build.Scopes.Scope.Tests(scope),
            Routes.Build.Scopes.Scope.AssociatedTests(scope),
            Routes.Build.Scopes.Scope.AssociatedTests(scope)
        ).forEach {
            iut.send(
                Subscribe(
                    destination = app.toLocation(it),
                    message = Subscription.serializer() stringify AgentSubscription(
                        agentId = agentId,
                        buildVersion = buildVersion
                    )
                ).toTextFrame()
            )
        }
        block(scopeContext)
    }

    suspend fun subscribeOnTest(
        scopeId: String,
        testId: String,
        agentId: String = info.agentId,
        buildVersion: String = info.buildVersionHash,
        block: suspend TestChannels.() -> Unit
    ) {
        val scope = scopeById(scopeId)
        Routes.Build.Scopes.Scope.MethodsCoveredByTest(testId, scope).let {
            Routes.Build.Scopes.Scope.MethodsCoveredByTest.Summary(it)
        }.also {
            iut.send(
                Subscribe(
                    destination = app.toLocation(it),
                    message = Subscription.serializer() stringify AgentSubscription(
                        agentId = agentId,
                        buildVersion = buildVersion
                    )
                ).toTextFrame()
            )
        }
        val testContext = TestChannels()
        testSubscriptions[scopeId] = testContext to block
        block(testContext)
    }
}

val pathToCallBackMapping = mutableMapOf<String, KClass<*>>()

@OptIn(KtorExperimentalLocationsAPI::class)
fun fillMapping(kclass: KClass<*>, str: String = "") {
    kclass.nestedClasses.forEach {
        val nestedClasses = it.nestedClasses
        if (nestedClasses.isEmpty()) {
            val findAnnotation = it.findAnnotation<Location>()
            val path = findAnnotation?.path!!
            pathToCallBackMapping[str + path] = it
        } else {
            val findAnnotation = it.findAnnotation<Location>()
            val path = findAnnotation?.path!!
            pathToCallBackMapping[str + path] = it
            fillMapping(it, str + path)
        }
    }
}
