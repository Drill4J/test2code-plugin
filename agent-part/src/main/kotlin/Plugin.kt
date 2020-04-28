package com.epam.drill.plugins.test2code

import com.epam.drill.plugin.api.*
import com.epam.drill.plugin.api.processing.*
import com.epam.drill.plugins.test2code.common.*
import com.epam.drill.plugins.test2code.common.api.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import org.jacoco.core.internal.data.*

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
        println("Plugin $id initialized! Loaded ${classBytes.count()} classes")
    }

    override fun off() {
        val cancelledCount = instrContext.cancelAll()
        _loadedClasses.value = emptyClasses
        println("Plugin $id is off")
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
        val t = System.currentTimeMillis()
        println("Plugin $id: retransforming classes...")
        val loadedClasses = Native.GetAllLoadedClasses()
        println("Plugin $id: ${loadedClasses.count()} classes total.")
        val classes = _loadedClasses.value
        val toTransform = loadedClasses.filter {
            !it.isAnnotation && !it.isSynthetic && it.name in classes
        }
        println("Plugin $id: ${toTransform.count()} classes to retransform.")
        if (toTransform.isNotEmpty()) {
            Native.RetransformClasses(toTransform.toTypedArray())
        }
        println("Plugin $id: ${toTransform.size} classes retransformed in ${System.currentTimeMillis() - t}ms.")
    }

    private fun updateLoadedClasses() {
        val t = System.currentTimeMillis()
        println("Plugin $id: updating loaded classes...")
        val loadedClasses = payload.agentData.classMap.run {
            LoadedClasses(
                names = keys.mapTo(persistentHashSetOf<String>().builder()) { it.replace('/', '.') },
                checkSums = mapValuesTo(persistentHashMapOf<String, Long>().builder()) { (_, bytes) ->
                    CRC64.classId(bytes)
                }.build()
            )
        }
        _loadedClasses.value = loadedClasses
        println("Plugin $id: updated ${loadedClasses.count()} classes in ${System.currentTimeMillis() - t}ms.")
    }

    override fun initPlugin() {
        println("Plugin $id: initializing...")
    }

    override suspend fun doAction(action: Action) {
        when (action) {
            is InitActiveScope -> action.payload.apply {
                println("Initializing scope $id, $name, prevId=$prevId")
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
                println("Start recording for session $sessionId")
                instrContext.start(sessionId, testType, probeSender(sessionId))
                sendMessage(SessionStarted(sessionId, testType, currentTimeMillis()))
            }
            is StopSession -> {
                val sessionId = action.payload.sessionId
                println("End of recording for session $sessionId")
                val runtimeData = instrContext.stop(sessionId) ?: emptySequence()
                if (runtimeData.any()) {
                    probeSender(sessionId)(runtimeData)
                } else println("No data for session $sessionId")
                sendMessage(SessionFinished(sessionId, currentTimeMillis()))
            }
            is CancelSession -> {
                val sessionId = action.payload.sessionId
                println("Cancellation of recording for session $sessionId")
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
        .chunked(10) // send data in chunks of 10
        .map { chunk -> CoverDataPart(sessionId, chunk) }
        .forEach { message -> sendMessage(message) }
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
