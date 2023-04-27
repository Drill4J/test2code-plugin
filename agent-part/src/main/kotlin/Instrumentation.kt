/**
 * Copyright 2020 - 2022 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.drill.plugins.test2code

import com.epam.drill.jacoco.*
import com.epam.drill.jacoco.BooleanArrayProbeInserter.*
import com.epam.drill.logger.api.*
import kotlinx.atomicfu.*
import org.jacoco.core.internal.flow.*
import org.jacoco.core.internal.instr.*
import org.objectweb.asm.*

/**
 *  Type of the instrumenter
 */
typealias DrillInstrumenter = (String, Long, ByteArray) -> ByteArray?

/**
 * Main method that covers all instrumentation process. It will be called by agent
 *
 * @param probeArrayProvider
 * @param logger
 * @return DrillInstrumenter - wrapper of ByteArray
 */
fun instrumenter(probeArrayProvider: ProbeArrayProvider, logger: Logger): DrillInstrumenter {
    return CustomInstrumenter(probeArrayProvider, logger)
}

/**
 *  Count number of classes
 */
private val classCounter = atomic(0)


private class CustomInstrumenter(
    private val probeArrayProvider: ProbeArrayProvider,
    private val logger: Logger
) : DrillInstrumenter {

    /**
     * Start to instrument class
     *
     * @param className name of the class
     * @param classId Long value in CRC64 format
     * @param classBody content of the class
     * @return instrumented ByteArray
     * @features Class instrumentation, Probe inserter
     */
    override fun invoke(className: String, classId: Long, classBody: ByteArray): ByteArray? = try {
        instrument(className, classId, classBody)
    } catch (e: Exception) {
        logger.error { "Error while instrumenting $className classId=$classId: ${e.message}" }
        null
    }

    fun instrument(className: String, classId: Long, classBody: ByteArray): ByteArray? {
        val version = InstrSupport.getMajorVersion(classBody)

        //count probes before transformation
        val counter = ProbeCounter()
        val reader = InstrSupport.classReaderFor(classBody)
        reader.accept(DrillClassProbesAdapter(counter, false), 0)

        val genId = classCounter.incrementAndGet()
        val probeCount = counter.count
        val strategy = DrillProbeStrategy(
            probeArrayProvider,
            className,
            classId,
            genId,
            probeCount
        )
        val writer = object : ClassWriter(reader, 0) {
            override fun getCommonSuperClass(type1: String, type2: String): String = throw IllegalStateException()
        }
        val visitor = DrillClassProbesAdapter(
            DrillClassInstrumenter(strategy, className, writer),
            InstrSupport.needsFrames(version)
        )
        reader.accept(visitor, ClassReader.EXPAND_FRAMES)

        (probeArrayProvider as? SimpleSessionProbeArrayProvider)?.run {
            probeMetaContainer.addDescriptor(
                genId,
                ProbeDescriptor(
                    id = classId,
                    name = className,
                    probeCount = probeCount
                ),
                global?.second,
                runtimes.values
            )
        }

        return writer.toByteArray()
    }
}

/**
 * Probe counter
 *
 * @constructor Create empty Probe counter
 * @features Probe inserter
 */
private class ProbeCounter : ClassProbesVisitor() {
    var count = 0
        private set

    override fun visitMethod(
        access: Int, name: String?, desc: String?, signature: String?, exceptions: Array<out String>?
    ): MethodProbesVisitor? {
        return null
    }

    override fun visitTotalProbeCount(count: Int) {
        this.count = count
    }
}

/**
 *  Class provides strategy for IProbeArrayStrategy interface.
 *
 * @property probeArrayProvider Provides boolean array for the probe.
 * @property className Name of the class
 * @property classId ID of the class
 * @property number Number of class
 * @property probeCount Number of probes
 * @constructor Create DrillProbeStrategy
 * @features Class Instrumentation
 */
private class DrillProbeStrategy(
    private val probeArrayProvider: ProbeArrayProvider,
    private val className: String,
    private val classId: Long,
    private val number: Int,
    private val probeCount: Int
) : IProbeArrayStrategy {
    override fun storeInstance(mv: MethodVisitor?, clinit: Boolean, variable: Int): Int = mv!!.run {
        val drillClassName = probeArrayProvider.javaClass.name.replace('.', '/')
        visitFieldInsn(Opcodes.GETSTATIC, drillClassName, "INSTANCE", "L$drillClassName;")
        // Stack[0]: Lcom/epam/drill/jacoco/Stuff;
        //TODO investigate code

        visitLdcInsn(classId)
        visitLdcInsn(number)
        visitLdcInsn(className)
        visitLdcInsn(probeCount)
        visitMethodInsn(
            Opcodes.INVOKEVIRTUAL, drillClassName, "invoke", "(JILjava/lang/String;I)L$PROBE_IMPL;",
            false
        )
        visitVarInsn(Opcodes.ASTORE, variable)

        6 //stack size
    }

    override fun addMembers(cv: ClassVisitor?, probeCount: Int) {
//        createDataField(cv)
    }
}


class DrillClassInstrumenter(
    private val probeArrayStrategy: IProbeArrayStrategy,
    private val clazzName: String,
    cv: ClassVisitor
) : ClassInstrumenter(probeArrayStrategy, cv) {

    override fun visitMethod(
        access: Int,
        name: String?,
        desc: String?,
        signature: String?,
        exceptions: Array<out String>?
    ): MethodProbesVisitor {
        InstrSupport.assertNotInstrumented(name, clazzName)
        val mv = cv.visitMethod(
            access, name, desc, signature,
            exceptions
        )
        val frameEliminator: MethodVisitor = DrillDuplicateFrameEliminator(mv)
        val probeVariableInserter = BooleanArrayProbeInserter(
            access,
            name,
            desc,
            frameEliminator,
            this.probeArrayStrategy
        )
        return DrillMethodInstrumenter(
            probeVariableInserter,
            probeVariableInserter
        )
    }
}
