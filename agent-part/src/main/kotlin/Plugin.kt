package com.epam.drill.plugins.test2code

import com.epam.drill.plugin.api.*
import com.epam.drill.plugin.api.processing.*
import com.epam.drill.plugins.test2code.common.api.*
import kotlinx.atomicfu.*
import org.jacoco.core.internal.data.*

@Suppress("unused")
class Plugin(
    override val id: String,
    agentContext: AgentContext
) : AgentPart<CoverConfig, Action>(id, agentContext), Instrumenter {
    private val logger = agentContext.logging.logger("Plugin $id")

    override val confSerializer = CoverConfig.serializer()

    override val serDe: SerDe<Action> = commonSerDe

    private val instrContext: SessionProbeArrayProvider = DrillProbeArrayProvider

    private val instrumenter: DrillInstrumenter = instrumenter(instrContext, logger)

    private val _retransformed = atomic(false)

    override fun on() {
        val initInfo = InitInfo(
            message = "Initializing plugin $id...\nConfig: ${config.message}"
        )
        sendMessage(initInfo)
        if (_retransformed.compareAndSet(expect = false, update = true)) {
            retransform()
        }
        sendMessage(Initialized(msg = "Initialized"))
        logger.info { "Plugin $id initialized!" }
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

    /**
     * Retransforming does not require an agent part instance.
     * This method is used in integration tests.
     */
    fun retransform() {
        try {
            Native.RetransformClassesByPackagePrefixes(byteArrayOf())
        } catch (ex: Throwable) {
            logger.error(ex) { "Error retransforming classes." }
        }
    }

    override fun instrument(
        className: String,
        initialBytes: ByteArray
    ): ByteArray? = takeIf { enabled }?.run {
        val idFromClassName = CRC64.classId(className.encodeToByteArray())
        instrumenter(className, idFromClassName, initialBytes)
    }

    override fun destroyPlugin(unloadReason: UnloadReason) {}

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
            is StartSession -> action.payload.run {
                val isRealtime = startPayload.isRealtime
                logger.info { "Start recording for session $sessionId" }
                val realtimeHandler = if (startPayload.isRealtime) probeSender(sessionId) else null
                instrContext.start(sessionId, realtimeHandler)
                sendMessage(SessionStarted(sessionId, startPayload.testType, isRealtime, currentTimeMillis()))
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

fun AgentPart<*, *>.probeSender(sessionId: String): RealtimeHandler = { execData ->
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
