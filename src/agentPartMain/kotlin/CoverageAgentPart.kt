package com.epam.drill.plugins.coverage

import com.epam.drill.ClassPath
import com.epam.drill.common.parse
import com.epam.drill.common.stringify
import com.epam.drill.plugin.api.processing.AgentPart
import com.epam.drill.plugin.api.processing.InstrumentationPlugin
import com.epam.drill.plugin.api.processing.Sender
import com.epam.drill.plugin.api.processing.UnloadReason
import com.epam.drill.session.DrillRequest
import com.epam.drill.url
import kotlinx.serialization.list
import org.jacoco.core.internal.data.CRC64
import java.util.concurrent.atomic.AtomicReference

val instrContext = object : InstrContext {
    override fun invoke(): String? = DrillRequest.currentSession()

    override fun get(key: String): String? = DrillRequest[key.toLowerCase()]
}


object DrillProbeArrayProvider : SimpleSessionProbeArrayProvider(instrContext)

@Suppress("unused")
class CoveragePlugin @JvmOverloads constructor(
    override val id: String,
    private val instrContext: SessionProbeArrayProvider = DrillProbeArrayProvider
) : AgentPart<CoverConfig, Action>(), InstrumentationPlugin {

    val instrumenter = instrumenter(instrContext)

    private val loadedClassesRef = AtomicReference<Map<String, Long?>>(emptyMap())

    override fun doRawAction(action: String) {
        doAction(actionSerializer parse action)
    }

    override fun on() {
        val initializingMessage = "Initializing plugin $id...\nConfig: ${config.message}"
        val classPath1 = ClassPath()
        val scanItPlease = classPath1.scanItPlease(ClassLoader.getSystemClassLoader())
        val filter = scanItPlease.filter { (k, _) ->
            config.pathPrefixes.any {
                k.removePrefix("BOOT-INF/classes/") //fix from Spring Boot Executable jar
                    .removeSuffix(".class").startsWith(it)
            }
        }

        val initInfo = InitInfo(filter.count(), initializingMessage)
        sendMessage(CoverageEventType.INIT, InitInfo.serializer() stringify initInfo)
        val loadedClasses = filter.map { (resourceName, classInfo) ->
            val className = resourceName
                .removePrefix("BOOT-INF/classes/") //fix from Spring Boot Executable jar
                .removeSuffix(".class")
            val bytes = classInfo.url(resourceName).readBytes()

            sendClass(ClassBytes(className, bytes.toList()))
            val classId = CRC64.classId(bytes)
            className to classId

        }.toMap()
        loadedClassesRef.set(loadedClasses)
        val initializedStr = "Plugin $id initialized!"
        sendMessage(CoverageEventType.INITIALIZED, initializedStr)
        println(initializedStr)
        println("Loaded ${loadedClasses.count()} classes")
        retransform()

    }

    override fun off() {
        retransform()
    }

    override fun destroyPlugin(unloadReason: UnloadReason) {

    }

    override fun retransform() {
        val filter = DrillRequest.GetAllLoadedClasses().filter { it.`package` != null }.filter { cla ->
            config.pathPrefixes.any {
                cla.`package`.name //fix from Spring Boot Executable jar
                    .replace(".", "/").startsWith(it)
            }
        }
        filter.forEach {
            DrillRequest.RetransformClasses(arrayOf(it))
        }


        println("${filter.size} classes were retransformed")
    }

    override fun initPlugin() {

    }


    override fun doAction(action: Action) {
        val sessionId = action.payload.sessionId
        when (action.type) {
            ActionType.START -> {
                println("Start recording for session $sessionId")
                instrContext.start(sessionId)
                sendMessage(CoverageEventType.SESSION_STARTED, sessionId)
            }
            ActionType.STOP -> {
                println("End of recording for session $sessionId")
                val runtimeData = instrContext.stop(sessionId)
                runtimeData?.apply {
                    val dataToSend = map { datum ->
                        ExDataTemp(
                            id = datum.id,
                            className = datum.name,
                            probes = datum.probes.toList(),
                            testName = datum.testName
                        )
                    }
                    //send data in chunk of 10
                    dataToSend.chunked(10) { dataChunk ->
                        sendExecutionData(dataChunk)
                    }
                    sendMessage(CoverageEventType.SESSION_FINISHED, sessionId)
                }
            }
            ActionType.CANCEL -> {
                println("Cancellation of recording for session $sessionId")
                instrContext.cancel(sessionId)
                sendMessage(CoverageEventType.SESSION_CANCELLED, sessionId)
            }
        }

    }

    override fun instrument(className: String, initialBytes: ByteArray): ByteArray? {
        if (!enabled) return null
        return loadedClassesRef.get()[className]?.let { classId ->
            val instrumenter1 = instrumenter(className, classId, initialBytes)
            println("$className instrumented")
            instrumenter1
        }
    }

    private fun sendClass(classBytes: ClassBytes) {
        val classJson = ClassBytes.serializer() stringify classBytes
        sendMessage(CoverageEventType.CLASS_BYTES, classJson)
    }

    private fun sendExecutionData(exData: List<ExDataTemp>) {
        val exDataJson = ExDataTemp.serializer().list stringify exData
        sendMessage(CoverageEventType.COVERAGE_DATA_PART, exDataJson)
    }

    private fun sendMessage(type: CoverageEventType, str: String) {
        val message = CoverageMessage.serializer() stringify CoverageMessage(type, str)
        Sender.sendMessage("coverage", message)
    }


    override var confSerializer: kotlinx.serialization.KSerializer<CoverConfig> = CoverConfig.serializer()
    override var actionSerializer: kotlinx.serialization.KSerializer<Action> = Action.serializer()
}
