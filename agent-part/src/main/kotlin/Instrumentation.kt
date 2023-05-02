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

import com.epam.drill.jacoco.BooleanArrayProbeInserter
import com.epam.drill.jacoco.BooleanArrayProbeInserter.PROBE_IMPL
import com.epam.drill.jacoco.DrillClassProbesAdapter
import com.epam.drill.jacoco.DrillDuplicateFrameEliminator
import com.epam.drill.jacoco.DrillMethodInstrumenter
import com.epam.drill.logger.api.Logger
import kotlinx.atomicfu.atomic
import org.jacoco.core.internal.flow.ClassProbesVisitor
import org.jacoco.core.internal.flow.MethodProbesVisitor
import org.jacoco.core.internal.instr.ClassInstrumenter
import org.jacoco.core.internal.instr.IProbeArrayStrategy
import org.jacoco.core.internal.instr.InstrSupport
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

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
 * @features Probe inserter, Class Instrumentation
 */
private class DrillProbeStrategy(
    private val probeArrayProvider: ProbeArrayProvider,
    private val className: String,
    private val classId: Long,
    private val number: Int,
    private val probeCount: Int
) : IProbeArrayStrategy {

    /**
     * Provide AgentProbe field
     *
     * @param mv instance of MethodVisitor
     * @param clinit flag of static initializer block
     * @param variable variable index to store probe array to
     * @return Maximum stack size required by the generated code
     * @features Probe inserter, Class Instrumentation
     */
    override fun storeInstance(mv: MethodVisitor?, clinit: Boolean, variable: Int): Int = mv!!.run {
        val drillClassName = probeArrayProvider.javaClass.name.replace('.', '/')
        visitFieldInsn(Opcodes.GETSTATIC, drillClassName, "INSTANCE", "L$drillClassName;")
        // Stack[0]: Lcom/epam/drill/jacoco/Stuff;

        //Insert Ldc instructions
        visitLdcInsn(classId)
        visitLdcInsn(number)
        visitLdcInsn(className)
        visitLdcInsn(probeCount)

        //Call invoke method
        visitMethodInsn(
            Opcodes.INVOKEVIRTUAL, drillClassName, "invoke", "(JILjava/lang/String;I)L$PROBE_IMPL;",
            false
        )
        // Store reference into local variable
        visitVarInsn(Opcodes.ASTORE, variable)

        6 //stack size
    }

    /**
     * Adds additional class members required by this strategy.
     * This method is called after all original members of the class has been processed.
     *
     * @param cv ClassVisitor instance
     * @param probeCount Total number of probes required for this class
     */
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
