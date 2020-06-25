package com.epam.drill.plugins.test2code

import com.epam.drill.plugin.api.*
import com.epam.drill.plugin.api.processing.*
import com.epam.drill.plugins.test2code.common.api.*
import kotlinx.atomicfu.*
import org.jacoco.core.internal.data.*

private val logger = logger("AgentPart")

@Suppress("unused")
class CoverageAgentPart @JvmOverloads constructor(
    private val payload: PluginPayload,
    private val instrContext: SessionProbeArrayProvider = DrillProbeArrayProvider
) : AgentPart<CoverConfig, Action>(payload), InstrumentationPlugin {

    override val id: String = payload.pluginId

    override val confSerializer = CoverConfig.serializer()

    override val serDe: SerDe<Action> = commonSerDe

    private val instrumenter: DrillInstrumenter = instrumenter(instrContext)

    private val _retransformed = atomic(false)

    override fun on() {
        val initializingMessage = "Initializing plugin $id...\nConfig: ${config.message}"
        val classBytes: Map<String, ByteArray> = payload.agentData.classMap
        val initInfo = InitInfo(classBytes.count(), initializingMessage)
        sendMessage(initInfo)
        if (_retransformed.compareAndSet(expect = false, update = true)) {
            retransform()
        }
        sendMessage(Initialized(msg = "Initialized"))
        logger.info { "Plugin $id initialized! Loaded ${classBytes.count()} classes" }
    }

    override fun off() {
        logger.info { "Enabled $enabled" }
        val cancelledCount = instrContext.cancelAll()
        logger.info { "Plugin $id is off" }
        if (_retransformed.compareAndSet(expect = true, update = false)) {
            retransform()
        }
        sendMessage(AllSessionsCancelled(cancelledCount, currentTimeMillis()))
    }

    override fun instrument(
        className: String,
        initialBytes: ByteArray
    ): ByteArray? = takeIf { enabled }?.run {
        val idFromClassName = CRC64.classId(className.encodeToByteArray())
        instrumenter(className, idFromClassName, initialBytes)
    }

    override fun destroyPlugin(unloadReason: UnloadReason) {}

    override fun retransform() {
        Native.RetransformClassesByPackagePrefixes(byteArrayOf())
    }

    override fun initPlugin() {
        logger.info { "Plugin $id: initializing..." }
        retransform()
        _retransformed.value = true
    }

    override suspend fun doAction(action: Action) {
        when (action) {
            is InitActiveScope -> action.payload.apply {
                logger.info { "Initializing scope $id, $name, prevId=$prevId" }
                instrContext.cancelAll()
                sendMessage(
                    ScopeInitialized(
                        id = id,
                        name = name,
                        prevId = prevId,
                        ts = currentTimeMillis()
                    )
                )
            }
            is StartSession -> {
                val sessionId = action.payload.sessionId
                val testType = action.payload.startPayload.testType
                logger.info { "Start recording for session $sessionId" }
                instrContext.start(sessionId, testType, probeSender(sessionId))
                sendMessage(SessionStarted(sessionId, testType, currentTimeMillis()))
            }
            is StopSession -> {
                val sessionId = action.payload.sessionId
                logger.info { "End of recording for session $sessionId" }
                val runtimeData = instrContext.stop(sessionId) ?: emptySequence()
                if (runtimeData.any()) {
                    probeSender(sessionId)(runtimeData)
                } else logger.info { "No data for session $sessionId" }
                sendMessage(SessionFinished(sessionId, currentTimeMillis()))
            }
            is CancelSession -> {
                val sessionId = action.payload.sessionId
                logger.info { "Cancellation of recording for session $sessionId" }
                instrContext.cancel(sessionId)
                sendMessage(SessionCancelled(sessionId, currentTimeMillis()))
            }
            else -> Unit
        }
    }
}

//extracted for agent emulator compatibility
fun AgentPart<*, *>.probeSender(sessionId: String): (Sequence<ExecDatum>) -> Unit = { execData ->
    execData.map(ExecDatum::toExecClassData)
        .chunked(128)
        .map { chunk -> CoverDataPart(sessionId, chunk) }
        .sumBy { message ->
            sendMessage(message)
            message.data.count()
        }.takeIf { it > 0 }?.let {
            sendMessage(SessionChanged(sessionId, it))
        }
}

fun AgentPart<*, *>.sendMessage(message: CoverMessage) {
    val messageStr = CoverMessage.serializer() stringify message
    send(messageStr)
}
