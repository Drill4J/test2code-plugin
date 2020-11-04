@file:OptIn(
    KtorExperimentalLocationsAPI::class,
    ExperimentalCoroutinesApi::class
)

package com.epam.drill.plugins.test2code

import com.epam.drill.admin.api.websocket.*
import com.epam.drill.admin.common.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.common.*
import com.epam.drill.e2e.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.api.routes.*
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.locations.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
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


    lateinit var iut: SendChannel<Frame>

    override suspend fun subscribe(sinf: AgentSubscription, destination: String) {
        pathToCallBackMapping.filterKeys { !it.contains("{") }.forEach {
            iut.send(Subscribe(it.key, Subscription.serializer() stringify sinf).toTextFrame())
        }
        delay(1000)
        activeScope()
        activeSessions()
        associatedTests()
        buildCoverage()
        coveragePackages()
        methods()
        risks()
        testsUsages()
        scopes()
    }

    private val activeScope: Channel<ScopeSummary?> = Channel()
    suspend fun activeScope() = activeScope.receive()

    private val activeSessions: Channel<ActiveSessions?> = Channel()
    suspend fun activeSessions() = activeSessions.receive()

    private val associatedTests = Channel<List<AssociatedTests>?>()
    suspend fun associatedTests() = associatedTests.receive()

    private val methods = Channel<MethodsSummaryDto?>()
    suspend fun methods() = methods.receive()

    private val buildCoverage = Channel<BuildCoverage?>()
    suspend fun buildCoverage() = buildCoverage.receive()

    private val coveragePackages = Channel<List<JavaPackageCoverage>?>()
    suspend fun coveragePackages() = coveragePackages.receive()

    private val testsUsages = Channel<List<TestsUsagesInfoByType>?>()
    suspend fun testsUsages() = testsUsages.receive()


    private val risks: Channel<Risks?> = Channel()
    suspend fun risks() = risks.receive()

    private val scopes: Channel<List<ScopeSummary>?> = Channel()
    suspend fun scopes() = scopes.receive()

    private val summary: Channel<SummaryDto?> = Channel()
    suspend fun summary() = summary.receive()

    override fun queued(incoming: ReceiveChannel<Frame>, out: SendChannel<Frame>, isDebugStream: Boolean) {
        iut = out
        app.launch {

            incoming.consumeEach {
                when (it) {
                    is Frame.Text -> {
                        val parseJson = json.parseJson(it.readText()) as JsonObject
                        if (isDebugStream)
                            println("PLUGIN: $parseJson")
                        val messageType = WsMessageType.valueOf(parseJson[WsSendMessage::type.name]!!.content)
                        val url = parseJson[WsSendMessage::destination.name]!!.content
                        val content = parseJson[WsSendMessage::message.name]!!.toString()

                        when (messageType) {
                            WsMessageType.MESSAGE ->
                                app.launch {
                                    val resolve = app.resolve(url)
                                    when (resolve) {

                                        is Routes.ActiveScope -> {
                                            if (content.isEmpty() || content == "[]" || content == "\"\"") {
                                                activeScope.send(null)
                                            } else {
                                                val element = ScopeSummary.serializer() parse content
                                                activeScope.send(element)
                                            }
                                        }
                                        is Routes.ActiveSessionStats -> {
                                            if (content.isEmpty() || content == "[]" || content == "\"\"") {
                                                activeSessions.send(null)
                                            } else {
                                                activeSessions.send(ActiveSessions.serializer() parse content)
                                            }
                                        }

                                        is Routes.Scopes -> {
                                            if (content.isEmpty() || content == "[]" || content == "\"\"") {
                                                scopes.send(null)
                                            } else
                                                scopes.send(ScopeSummary.serializer().list parse content)
                                        }
                                        is Routes.Scope -> {
                                            delay(100)
                                            val scope =
                                                scopeSubscriptions.getValue(resolve.scopeId).first.scope
                                            if (content.isEmpty() || content == "[]" || content == "\"\"") {
                                                scope.send(null)
                                            } else {
                                                scope.send(
                                                    ScopeSummary.serializer() parse content
                                                )
                                            }
                                        }

                                        is Routes.Scope.AssociatedTests -> {
                                            delay(100)
                                            val associatedTests =
                                                scopeSubscriptions.getValue(resolve.scope.scopeId).first.associatedTests
                                            if (content.isEmpty() || content == "[]" || content == "\"\"") {
                                                associatedTests.send(null)
                                            } else {
                                                associatedTests.send(
                                                    AssociatedTests.serializer().list parse content
                                                )
                                            }
                                        }

                                        is Routes.Scope.Methods -> {
                                            delay(100)
                                            val methods =
                                                scopeSubscriptions.getValue(resolve.scope.scopeId).first.methods
                                            if (content.isEmpty() || content == "[]" || content == "\"\"") {
                                                methods.send(null)
                                            } else
                                                methods.send(
                                                    MethodsSummaryDto.serializer() parse content
                                                )
                                        }

                                        is Routes.Scope.TestsUsages -> {
                                            delay(100)
                                            val testsUsages =
                                                scopeSubscriptions.getValue(resolve.scope.scopeId).first.testsUsages
                                            if (content.isEmpty() || content == "[]" || content == "\"\"") {
                                                testsUsages.send(null)
                                            } else {
                                                testsUsages.send(
                                                    TestsUsagesInfoByType.serializer().list parse content
                                                )
                                            }
                                        }

                                        is Routes.Scope.Coverage.Packages -> {
                                            delay(100)
                                            val coveragePackages = run {
                                                scopeSubscriptions.getValue(
                                                    resolve.coverage.scope.scopeId
                                                ).first.coveragePackages
                                            }

                                            if (content.isEmpty() || content == "[]" || content == "\"\"") {
                                                coveragePackages.send(null)
                                            } else {
                                                coveragePackages.send(
                                                    JavaPackageCoverage.serializer().list parse content
                                                )
                                            }
                                        }

                                        is Routes.Scope.MethodsCoveredByTest.Summary -> {
                                            delay(100)
                                            val methodsCoveredByTest = testSubscriptions
                                                .getValue(resolve.test.scope.scopeId)
                                                .first
                                                .methodsCoveredByTest
                                            if (content.isEmpty() || content == "[]" || content == "\"\"") {
                                                methodsCoveredByTest.send(null)
                                            } else
                                                methodsCoveredByTest.send(
                                                    TestedMethodsSummary.serializer() parse content
                                                )
                                        }

                                        is Routes.Scope.MethodsCoveredByTestType.Summary -> {
                                            delay(100)
                                            val methodsCoveredByTestType = testTypeSubscriptions
                                                .getValue(resolve.type.scope.scopeId)
                                                .first
                                                .methodsCoveredByTestType
                                            if (content.isEmpty() || content == "[]" || content == "\"\"") {
                                                methodsCoveredByTestType.send(null)
                                            } else
                                                methodsCoveredByTestType.send(
                                                    TestedMethodsByTypeSummary.serializer() parse content
                                                )
                                        }

                                        is Routes.Scope.Coverage -> {
                                            delay(100)
                                            val coverage =
                                                scopeSubscriptions.getValue(resolve.scope.scopeId).first.coverage
                                            if (content.isEmpty() || content == "[]" || content == "\"\"") {
                                                coverage.send(null)
                                            } else
                                                coverage.send(ScopeCoverage.serializer() parse content)
                                        }

                                        is Routes.Build.AssociatedTests -> {
                                            if (content.isEmpty() || content == "[]" || content == "\"\"") {
                                                associatedTests.send(null)
                                            } else
                                                associatedTests.send(AssociatedTests.serializer().list parse content)
                                        }

                                        is Routes.Build.Methods -> {
                                            if (content.isEmpty() || content == "[]" || content == "\"\"") {
                                                methods.send(null)
                                            } else methods.send(MethodsSummaryDto.serializer() parse content)
                                        }

                                        is Routes.Build.TestsUsages -> {
                                            if (content.isEmpty() || content == "[]" || content == "\"\"") {
                                                testsUsages.send(null)
                                            } else {
                                                testsUsages.send(
                                                    TestsUsagesInfoByType.serializer().list parse content
                                                )
                                            }
                                        }

                                        is Routes.Build.Coverage.Packages -> {
                                            if (content.isEmpty() || content == "[]" || content == "\"\"") {
                                                coveragePackages.send(null)
                                            } else {
                                                coveragePackages.send(
                                                    JavaPackageCoverage.serializer().list parse content
                                                )
                                            }
                                        }

                                        is Routes.Build.Coverage -> {
                                            if (content.isEmpty() || content == "[]" || content == "\"\"") {
                                                buildCoverage.send(null)
                                            } else
                                                buildCoverage.send(BuildCoverage.serializer() parse content)
                                        }

                                        is Routes.Build.Risks -> {
                                            if (content.isEmpty() || content == "[]" || content == "\"\"") {
                                                risks.send(null)
                                            } else
                                                risks.send(Risks.serializer() parse content)
                                        }

                                        is Routes.ServiceGroup.Summary -> {
                                            if (content.isEmpty() || content == "[]" || content == "\"\"") {
                                                summary.send(null)
                                            } else summary.send(SummaryDto.serializer() parse content)
                                        }
                                        else -> println("!!!$url ignored")
                                    }
                                }
                            else -> TODO("not implemented yet")
                        }
                    }
                }
            }


        }
    }


    class ScopeContext {

        internal val scope: Channel<ScopeSummary?> = Channel()
        suspend fun scope() = scope.receive()

        internal val associatedTests = Channel<List<AssociatedTests>?>()
        suspend fun associatedTests() = associatedTests.receive()

        internal val methods = Channel<MethodsSummaryDto?>()
        suspend fun methods() = methods.receive()


        internal val coverage = Channel<Coverage?>()
        suspend fun coverage() = coverage.receive()

        internal val coveragePackages = Channel<List<JavaPackageCoverage>?>()
        suspend fun coveragePackages() = coveragePackages.receive()

        internal val testsUsages = Channel<List<TestsUsagesInfoByType>?>()
        suspend fun testsUsages() = testsUsages.receive()

    }

    class TestContext {
        internal val methodsCoveredByTest = Channel<TestedMethodsSummary?>()
        suspend fun methodsCoveredByTest() = methodsCoveredByTest.receive()
    }

    class TestTypeContext {
        internal val methodsCoveredByTestType = Channel<TestedMethodsByTypeSummary?>()
        suspend fun methodsCoveredByTestType() = methodsCoveredByTestType.receive()
    }


    private val scopeSubscriptions = ConcurrentHashMap<String, Pair<ScopeContext, suspend ScopeContext.() -> Unit>>()
    private val testSubscriptions = ConcurrentHashMap<String, Pair<TestContext, suspend TestContext.() -> Unit>>()
    private val testTypeSubscriptions =
        ConcurrentHashMap<String, Pair<TestTypeContext, suspend TestTypeContext.() -> Unit>>()

    suspend fun subscribeOnScope(
        scopeId: String,
        agentId: String = info.agentId,
        buildVersion: String = info.buildVersionHash,
        block: suspend ScopeContext.() -> Unit
    ) {
        val scope = Routes.Scope(scopeId)
        val coverage = Routes.Scope.Coverage(scope)
        arrayOf(
            scope,
            Routes.Scope.Methods(scope),
            coverage,
            Routes.Scope.Coverage.Packages(coverage),
            Routes.Scope.TestsUsages(scope),
            Routes.Scope.AssociatedTests(scope),
            Routes.Scope.AssociatedTests(scope)
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

        val scopeContext = ScopeContext()
        scopeSubscriptions[scopeId] = scopeContext to block
        block(scopeContext)
    }

    suspend fun subscribeOnTest(
        scopeId: String,
        testId: String,
        agentId: String = info.agentId,
        buildVersion: String = info.buildVersionHash,
        block: suspend TestContext.() -> Unit
    ) {
        val scope = Routes.Scope(scopeId)
        Routes.Scope.MethodsCoveredByTest(testId, scope).let {
            Routes.Scope.MethodsCoveredByTest.Summary(it)
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
        val testContext = TestContext()
        testSubscriptions[scopeId] = testContext to block
        block(testContext)
    }

    suspend fun subscribeOnTestType(
        scopeId: String,
        testType: String,
        agentId: String = info.agentId,
        buildVersion: String = info.buildVersionHash,
        block: suspend TestTypeContext.() -> Unit
    ) {
        val scope = Routes.Scope(scopeId)
        Routes.Scope.MethodsCoveredByTestType(testType, scope).let {
            Routes.Scope.MethodsCoveredByTestType.Summary(it)
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
        val testContext = TestTypeContext()
        testTypeSubscriptions[scopeId] = testContext to block
        block(testContext)
    }

}

val pathToCallBackMapping = mutableMapOf<String, KClass<*>>()
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


fun Application.resolve(destination: String): Any {
    val p = "\\{(.*)}".toRegex()
    val urlTokens = destination.split("/")

    val filter = pathToCallBackMapping.filter { it.key.count { c -> c == '/' } + 1 == urlTokens.size }.filter {
        var matche = true
        it.key.split("/").forEachIndexed { x, y ->
            if (!(y == urlTokens[x] || y.startsWith("{"))) {
                matche = false
            }
        }
        matche
    }
    val suitableRout = filter.entries.first()

    val parameters = suitableRout.run {
        val mutableMapOf = mutableMapOf<String, String>()
        key.split("/").forEachIndexed { x, y ->
            if (y != urlTokens[x] && (p.matches(y))) {
                mutableMapOf[p.find(y)!!.groupValues[1]] = urlTokens[x]
            }
        }
        val map = mutableMapOf.map { Pair(it.key, listOf(it.value)) }
        parametersOf(* map.toTypedArray())
    }
    return feature(Locations).resolve(suitableRout.value, parameters)

}
