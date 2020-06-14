package com.epam.drill.plugins.test2code

import com.epam.drill.plugin.api.*
import com.epam.drill.plugin.api.processing.*
import com.epam.drill.plugins.test2code.common.api.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
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

    private val _loadedClasses: AtomicRef<LoadedClasses> = atomic(emptyClasses)

    override fun on() {
        val initializingMessage = "Initializing plugin $id...\nConfig: ${config.message}"
        val classBytes: Map<String, ByteArray> = payload.agentData.classMap
        val initInfo = InitInfo(classBytes.count(), initializingMessage)
        sendMessage(initInfo)
        updateLoadedClasses()
        retransform()
        sendMessage(Initialized(msg = "Initialized"))
        logger.info { "Plugin $id initialized! Loaded ${classBytes.count()} classes" }
    }

    override fun off() {
        val cancelledCount = instrContext.cancelAll()
        _loadedClasses.value = emptyClasses
        logger.info { "Plugin $id is off" }
        retransform()
        sendMessage(AllSessionsCancelled(cancelledCount, currentTimeMillis()))
    }

    override fun instrument(
        className: String,
        initialBytes: ByteArray
    ): ByteArray? = takeIf { enabled }?.run {
        _loadedClasses.value[className]?.let { classId ->
            instrumenter(className, classId, initialBytes)
        }
    }

    override fun destroyPlugin(unloadReason: UnloadReason) {

    }

    override fun retransform() {
        Native.RetransformClassesByPackagePrefixes(byteArrayOf())
    }

    private fun updateLoadedClasses() {
        val t = System.currentTimeMillis()
        logger.info { "Plugin $id: updating loaded classes..." }
        val loadedClasses = payload.agentData.classMap.run {
            LoadedClasses(
                names = keys.mapTo(persistentHashSetOf<String>().builder()) { it.replace('/', '.') },
                checkSums = mapValuesTo(persistentHashMapOf<String, Long>().builder()) { (_, bytes) ->
                    CRC64.classId(bytes)
                }.build()
            )
        }
        _loadedClasses.value = loadedClasses
        logger.info { "Plugin $id: updated ${loadedClasses.count()} classes in ${System.currentTimeMillis() - t}ms." }
    }

    override fun initPlugin() {
        logger.info { "Plugin $id: initializing..." }
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

private class LoadedClasses(
    private val names: Set<String> = emptySet(),
    private val checkSums: Map<String, Long> = emptyMap()
) {
    operator fun get(path: String): Long? = checkSums[path]

    operator fun contains(name: String): Boolean = names.contains(name)

    fun count() = names.count()
}

private val emptyClasses = LoadedClasses()
