package com.epam.drill.plugins.coverage

import com.epam.drill.common.*
import com.epam.drill.e2e.*
import com.epam.drill.endpoints.*
import com.epam.drill.endpoints.plugin.*
import com.epam.drill.plugin.api.message.*
import com.epam.drill.plugins.coverage.routes.*
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.locations.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.util.concurrent.*
import kotlin.reflect.*
import kotlin.reflect.full.*


class CoverageSocketStreams() : PluginStreams() {

    init {
        fillMapping(Routes::class)
    }


    lateinit var iut: SendChannel<Frame>

    suspend fun subscribe(sinf: SubscribeInfo) {
        pathToCallBackMapping.filterKeys { !it.contains("{") }.forEach {
            iut.send(UiMessage(WsMessageType.SUBSCRIBE, it.key, SubscribeInfo.serializer() stringify sinf))
        }
        delay(1000)
        activeScope()
        activeSessions()
        associatedTests()
        coverage()
        coverageByPackages()
        coverageNew()
        methods()
        risks()
        testsToRun()
        testsUsages()
        scopes()
    }

    private val activeScope: Channel<ScopeSummary?> = Channel()
    suspend fun activeScope() = activeScope.receive()

    private val activeSessions: Channel<ActiveSessions?> = Channel()
    suspend fun activeSessions() = activeSessions.receive()

    private val associatedTests = Channel<List<AssociatedTests>?>()
    suspend fun associatedTests() = associatedTests.receive()

    private val methods = Channel<BuildMethods?>()
    suspend fun methods() = methods.receive()


    private val coverage = Channel<Coverage?>()
    suspend fun coverage() = coverage.receive()


    private val coverageByPackages = Channel<List<JavaPackageCoverage>?>()
    suspend fun coverageByPackages() = coverageByPackages.receive()


    private val testsUsages = Channel<List<TestUsagesInfo>?>()
    suspend fun testsUsages() = testsUsages.receive()


    private val coverageNew: Channel<Coverage?> = Channel()
    suspend fun coverageNew() = coverageNew.receive()


    private val risks: Channel<Risks?> = Channel()
    suspend fun risks() = risks.receive()


    private val testsToRun: Channel<TestsToRun?> = Channel()
    suspend fun testsToRun() = testsToRun.receive()


    private val scopes: Channel<List<ScopeSummary>?> = Channel()
    suspend fun scopes() = scopes.receive()


    override fun queued(incoming: ReceiveChannel<Frame>, out: SendChannel<Frame>) {
        iut = out
        app.launch {
            incoming.consumeEach {
                when (it) {
                    is Frame.Text -> {
                        val parseJson = json.parseJson(it.readText()) as JsonObject
                        if (true)
                            println("PLUGIN: $parseJson")
                        val messageType = WsMessageType.valueOf(parseJson[WsReceiveMessage::type.name]!!.content)
                        val url = parseJson[WsReceiveMessage::destination.name]!!.content
                        val content = parseJson[WsReceiveMessage::message.name]!!.toString()

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
                                        is Routes.ActiveSessions -> {
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
                                        is Routes.Scope.Scope -> {
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
                                                scopeSubscriptions.getValue(resolve.scopeId).first.associatedTests
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
                                            val methods = scopeSubscriptions.getValue(resolve.scopeId).first.methods
                                            if (content.isEmpty() || content == "[]" || content == "\"\"") {
                                                methods.send(null)
                                            } else
                                                methods.send(
                                                    BuildMethods.serializer() parse content
                                                )

                                        }

                                        is Routes.Scope.TestsUsages -> {
                                            delay(100)
                                            val testsUsages =
                                                scopeSubscriptions.getValue(resolve.scopeId).first.testsUsages
                                            if (content.isEmpty() || content == "[]" || content == "\"\"") {
                                                testsUsages.send(null)
                                            } else {
                                                testsUsages.send(
                                                    TestUsagesInfo.serializer().list parse content
                                                )
                                            }
                                        }

                                        is Routes.Scope.CoverageByPackages -> {
                                            delay(100)
                                            val coverageByPackages =
                                                scopeSubscriptions.getValue(resolve.scopeId).first.coverageByPackages

                                            if (content.isEmpty() || content == "[]" || content == "\"\"") {
                                                coverageByPackages.send(null)
                                            } else {
                                                coverageByPackages.send(
                                                    JavaPackageCoverage.serializer().list parse content
                                                )
                                            }
                                        }

                                        is Routes.Scope.Coverage -> {
                                            delay(100)
                                            val coverage = scopeSubscriptions.getValue(resolve.scopeId).first.coverage
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
                                            } else
                                                methods.send(
                                                    BuildMethods.serializer() parse content
                                                )

                                        }

                                        is Routes.Build.TestsUsages -> {
                                            if (content.isEmpty() || content == "[]" || content == "\"\"") {
                                                testsUsages.send(null)
                                            } else {
                                                testsUsages.send(
                                                    TestUsagesInfo.serializer().list parse content
                                                )
                                            }
                                        }

                                        is Routes.Build.CoverageByPackages -> {
                                            if (content.isEmpty() || content == "[]" || content == "\"\"") {
                                                coverageByPackages.send(null)
                                            } else {
                                                coverageByPackages.send(
                                                    JavaPackageCoverage.serializer().list parse content
                                                )
                                            }
                                        }

                                        is Routes.Build.Coverage -> {
                                            if (content.isEmpty() || content == "[]" || content == "\"\"") {
                                                coverage.send(null)
                                            } else
                                                coverage.send(BuildCoverage.serializer() parse content)
                                        }

                                        is Routes.Build.CoverageNew -> {
                                            if (content.isEmpty() || content == "[]" || content == "\"\"") {
                                                coverageNew.send(null)
                                            } else
                                                coverageNew.send(BuildCoverage.serializer() parse content)
                                        }

                                        is Routes.Build.TestsToRun -> {
                                            if (content.isEmpty() || content == "[]" || content == "\"\"") {
                                                testsToRun.send(null)
                                            } else
                                                testsToRun.send(TestsToRun.serializer() parse content)
                                        }

                                        is Routes.Build.Risks -> {
                                            if (content.isEmpty() || content == "[]" || content == "\"\"") {
                                                risks.send(null)
                                            } else
                                                risks.send(Risks.serializer() parse content)
                                        }
                                        else -> TODO("$url not implemented yet")

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

        internal val methods = Channel<BuildMethods?>()
        suspend fun methods() = methods.receive()


        internal val coverage = Channel<Coverage?>()
        suspend fun coverage() = coverage.receive()


        internal val coverageByPackages = Channel<List<JavaPackageCoverage>?>()
        suspend fun coverageByPackages() = coverageByPackages.receive()


        internal val testsUsages = Channel<List<TestUsagesInfo>?>()
        suspend fun testsUsages() = testsUsages.receive()

    }


    private val scopeSubscriptions = ConcurrentHashMap<String, Pair<ScopeContext, suspend ScopeContext.() -> Unit>>()

    suspend fun subscribeOnScope(
        scopeId: String,
        agentId: String = info.agentId,
        buildVersio: String = info.buildVersionHash,
        block: suspend ScopeContext.() -> Unit
    ) {
        arrayOf(
            Routes.Scope.Scope(scopeId),
            Routes.Scope.Methods(scopeId),
            Routes.Scope.Coverage(scopeId),
            Routes.Scope.CoverageByPackages(scopeId),
            Routes.Scope.TestsUsages(scopeId),
            Routes.Scope.AssociatedTests(scopeId)
        ).forEach {
            iut.send(
                UiMessage(
                    WsMessageType.SUBSCRIBE,
                    app.toLocation(it),
                    SubscribeInfo.serializer() stringify SubscribeInfo(
                        agentId,
                        buildVersio
                    )
                )
            )
        }

        val scopeContext = ScopeContext()
        scopeSubscriptions[scopeId] = scopeContext to block
        block(scopeContext)
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
            if (y == urlTokens[x] || y.startsWith("{")) {
            } else {
                matche = false
            }
        }
        matche
    }
    val suitableRout = filter.entries.first()

    val parameters = suitableRout.run {
        val mutableMapOf = mutableMapOf<String, String>()
        key.split("/").forEachIndexed { x, y ->
            if (y == urlTokens[x]) {
            } else if (p.matches(y)) {
                mutableMapOf[p.find(y)!!.groupValues[1]] = urlTokens[x]
            }
        }
        val map = mutableMapOf.map { Pair(it.key, listOf(it.value)) }
        parametersOf(* map.toTypedArray())
    }
    return feature(Locations).resolve(suitableRout.value, parameters)

}

suspend fun Agent.sendEvent(cov: CoverMessage) {
    sendPluginData(
        MessageWrapper(
            "test-to-code-mapping",
            DrillMessage(
                "ad",
                commonSerDe.stringify(CoverMessage.serializer(), cov)
            )
        )
    )
}

fun Action.stringify()= commonSerDe.stringify(commonSerDe.actionSerializer, this)

