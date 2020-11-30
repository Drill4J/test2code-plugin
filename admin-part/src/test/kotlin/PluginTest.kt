import com.epam.drill.common.*
import com.epam.drill.plugin.api.*
import com.epam.drill.plugin.api.end.*
import com.epam.drill.plugins.test2code.*
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
        agentType = AgentType.JAVA,
        status = AgentStatus.ONLINE,
        agentVersion = "0.1.0"
    )

    private val sender = EmptySender

    private val adminData = object : AdminData {
        override val buildManager = EmptyBuildManager
        override val classBytes = emptyMap<String, ByteArray>()
    }

    @AfterTest
    fun cleanStore() {
        storeClient.store.close()
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

}

private object EmptySender : Sender {
    override suspend fun send(context: SendContext, destination: Any, message: Any) {}

}
