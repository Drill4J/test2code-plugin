import com.epam.drill.builds.*
import com.epam.drill.e2e.*
import com.epam.drill.e2e.plugin.*
import com.epam.drill.plugins.test2code.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import io.kotlintest.*
import io.kotlintest.matchers.doubles.*
import io.kotlintest.matchers.numerics.*
import io.ktor.http.*
import javassist.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.jupiter.api.*
import java.io.*
import java.util.*
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*
import kotlin.test.Test

class LoadTest : E2EPluginTest() {

    companion object {
        //with such setting max heap size is ~1.3g time for test ~15 min
        private const val CLASS_COUNT = 25_000 //50_000
        private const val METHODS_COUNT = 45_000 //100_000
        private const val PACKAGES_COUNT = 80 //150
        private const val PACKAGE_HIERARCHY_LEVEL = 10
    }

    @BeforeEach
    fun classGenerator() {
        val random = Random().apply { setSeed(0) }
        val destination = File("build/classes/java/bigBuild/com/epam/test")
        if (destination.listFiles()?.isEmpty() != false) {
            destination.mkdirs()
            val pool = ClassPool(true)
            pool.appendClassPath(LoaderClassPath(ClassLoader.getSystemClassLoader()))
            val packages = generateAllowedPackages(random)
            Array(CLASS_COUNT) {
                packages.random() to "RandomClass$it"
            }

                .forEachIndexed { idx, (packages, className) ->
                    val cc = pool.makeClass("com.epam.test.${packages.joinToString(".")}.$className")
                    cc.interfaces = arrayOf(pool.get("com.epam.drill.builds.CustomBuild\$Test"))
                    repeat(METHODS_COUNT / CLASS_COUNT) {
                        cc.addMethod(
                            CtMethod.make(
                                """
                        public void method$idx$it(boolean ifBranch, boolean elseBranch) {
                            if (ifBranch) {
                                
                                    long w = $idx${it}l;
                                    boolean s = System.nanoTime() > w;
                            }
                            if (elseBranch) {
                                    long w = $idx${it}l;
                                    boolean s = System.nanoTime() > w;
                                
                            }
                        }
                        """.trimIndent(), cc
                            )
                        )
                    }
                    File(destination, "${packages.joinToString(separator = "/", postfix = "/")}$className.class")
                        .apply { parentFile.mkdirs() }
                        .writeBytes(cc.toBytecode())
                }
        }
    }

    private fun generateAllowedPackages(random: Random) = Array(PACKAGES_COUNT) { num ->
        val size = random.nextInt(PACKAGE_HIERARCHY_LEVEL)
        Array(if (size == 0) 1 else size) {
            "package$num$it"
        }
    }

    @Test
    fun `load test`() {
        createSimpleAppWithPlugin<CoverageSocketStreams>(timeout = 500000000) {
            connectAgent<CustomBuild> { plugUi, build ->
                plugUi.buildCoverage()
                plugUi.coveragePackages()
                plugUi.activeSessions()!!.run {
                    count shouldBe 0
                    testTypes shouldBe emptySet()
                }
                plugUi.activeScope()
                val startNewSession = StartNewSession(StartPayload("LOAD")).stringify()
                println("START SESSION")
                pluginAction(startNewSession) { status, content ->
                    status shouldBe HttpStatusCode.OK
                    val startSession = content!!.parseJsonData<StartAgentSession>()

                    plugUi.activeSessions()!!.run { count shouldBe 1 }

                    repeat(1) { index ->
                        runWithSession(startSession.payload.sessionId, "test$index") {//todo change testName
                            val tests = build.tests

                            tests.forEach {
                                val newInstance = it.newInstance()

                                newInstance::class.functions.filter { it.parameters.size == 3 }
                                    .filter { it.parameters[1].type.javaType == Boolean::class.java }.forEach {
                                        kotlin.runCatching {
                                            it.call(newInstance, true, true)
                                        }
                                    }
                            }

                        }
                    }
                    pluginAction(StopAgentSession(AgentSessionPayload(startSession.payload.sessionId)).stringify()).join()
                }.join()
                println("STOP SESSION")
                plugUi.activeSessions()!!.count shouldBe 0
                println("SMT HAPPEN")
                plugUi.activeScope()!!.apply {
                    coverage.percentage shouldBeGreaterThan 0.0
                }
                println("SMT HAPPEN X2")
                val switchScope = SwitchActiveScope(
                    ActiveScopeChangePayload(
                        scopeName = "new2",
                        savePrevScope = true,
                        prevScopeEnabled = true
                    )
                ).stringify()
                println("SWITCHING SCOPE")
                pluginAction(switchScope) { status, _ ->
                    status shouldBe HttpStatusCode.OK
                    plugUi.buildCoverage()!!.apply {
                        count.covered shouldBeGreaterThan 0
                    }
                    plugUi.coveragePackages()!!.apply {
                        //  first().coverage shouldBeGreaterThan 0.0
                    }
                }.join()
                println("EVERYTHING DONE")

                //Waiting for culc after change scope
                while (true)
                    delay(100)

            }
        }
    }
}
