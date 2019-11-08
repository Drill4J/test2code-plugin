import com.epam.drill.builds.*
import com.epam.drill.e2e.*
import com.epam.drill.plugins.coverage.*
import org.junit.jupiter.api.*


class E2eTest : E2EPluginTest<CoverageSocketStreams>() {
    @Test
    fun sad() {
        createSimpleAppWithPlugin<CoverageSocketStreams>(true, true) {
            connectAgent<Build1> { plugUi, _ ->
                val activeScope = plugUi.activeScope()
                plugUi.subscribeOnScope(activeScope!!.id) {
                    println(methods())
                    println(associatedTests())
                    println(coverage())
                    println(coverageByPackages())
                    println(testsUsages())
                }
            }.reconnect<Build2> { plug, _ ->
                plug.subscribeOnScope(plug.activeScope()!!.id) {
                    println(methods())
                    println(associatedTests())
                    println(coverage())
                    println(coverageByPackages())
                    println(testsUsages())
                }
            }
        }
    }
}