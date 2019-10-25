package com.epam.drill.plugins.coverage

import com.epam.drill.plugin.api.*
import com.epam.drill.plugin.api.processing.*
import com.epam.drill.session.*
import kotlinx.atomicfu.*
import org.jacoco.core.internal.data.*

const val DEFAULT_SESSION = "DEFAULT_SESSION"

@Suppress("unused")
class CoverageAgentPart @JvmOverloads constructor(
    private val payload: PluginPayload,
    private val instrContext: SessionProbeArrayProvider = DrillProbeArrayProvider
) : AgentPart<CoverConfig, Action>(payload), InstrumentationPlugin {

    override val id: String = payload.pluginId

    override val confSerializer = CoverConfig.serializer()

    override val serDe = commonSerDe

    val instrumenter = instrumenter(instrContext)

    private val _loadedClasses = atomic(emptyMap<String, Long?>())

    private var loadedClasses
        get() = _loadedClasses.value
        set(value) {
            _loadedClasses.value = value
        }

    override fun on() {
        val initializingMessage = "Initializing plugin $id...\nConfig: ${config.message}"
        val loadedClassesMap = payload.agentData.classMap
        val initInfo = InitInfo(loadedClassesMap.keys.count(), initializingMessage)
        sendMessage(initInfo)
        loadedClasses = loadedClassesMap.map { (className, bytes) ->
            val classId = CRC64.classId(bytes)
            className to classId
        }.toMap()
        sendMessage(Initialized(msg = "Initialized"))
        println("Plugin $id initialized! Loaded ${loadedClassesMap.count()} classes")
        retransform()
    }

    override fun off() {
        retransform()
    }

    override fun instrument(className: String, initialBytes: ByteArray): ByteArray? {
        if (!enabled) return null
        return loadedClasses[className]?.let { classId ->
            instrumenter(className, classId, initialBytes)
        }
    }

    override fun destroyPlugin(unloadReason: UnloadReason) {

    }

    override fun retransform() {
        val classes = payload.agentData.classMap.keys.map { it.replace("/", ".") }
        val filter = DrillRequest.GetAllLoadedClasses().filter { it.name in classes }
        if (filter.isNotEmpty())
            DrillRequest.RetransformClasses(filter.toTypedArray())
        println("${filter.size} classes were re-transformed")
    }

    override fun initPlugin() {
        println("Plugin $id initialized")

    }


    override suspend fun doAction(action: Action) {
        when (action) {
            is StartSession -> {
                val sessionId = action.payload.sessionId
                val testType = action.payload.startPayload.testType
                val testName = action.payload.startPayload.testName
                val godMode = action.payload.startPayload.godMode
                println("Start recording for session $sessionId")
                instrContext.start(sessionId, testType, testName)
                if (godMode) instrContext.start(DEFAULT_SESSION, testType, testName)
                sendMessage(SessionStarted(sessionId, testType, currentTimeMillis()))
            }
            is StopSession -> {
                val sessionId = action.payload.sessionId
                stopSession(sessionId)
                stopSession(DEFAULT_SESSION)
                sendMessage(SessionFinished(sessionId, currentTimeMillis()))
            }
            is CancelSession -> {
                val sessionId = action.payload.sessionId
                println("Cancellation of recording for session $sessionId")
                instrContext.cancel(sessionId)
                instrContext.cancel(DEFAULT_SESSION)
                sendMessage(SessionCancelled(sessionId, currentTimeMillis()))
            }
            else -> Unit
        }

    }

    private fun stopSession(sessionId: String) {
        println("End of recording for session $sessionId")
        val runtimeData = instrContext.stop(sessionId) ?: emptySequence()
        if (runtimeData.any()) {
            runtimeData.map { datum ->
                ExecClassData(
                    id = datum.id,
                    className = datum.name,
                    probes = datum.probes.toList(),
                    testName = datum.testName
                )
            }.chunked(10)
                .forEach { dataChunk ->
                    //send data in chunks of 10
                    sendMessage(CoverDataPart(sessionId, dataChunk))
                }
        } else println("No data for session $sessionId")
    }

    private fun sendMessage(message: CoverMessage) {
        val messageStr = CoverMessage.serializer() stringify message
        Sender.sendMessage(id, messageStr)
    }
}