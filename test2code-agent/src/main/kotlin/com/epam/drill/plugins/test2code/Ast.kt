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

import com.epam.drill.jacoco.DrillClassProbesAdapter
import com.epam.drill.plugins.test2code.checksum.checksumCalculation
import com.epam.drill.plugins.test2code.common.api.AstEntity
import com.epam.drill.plugins.test2code.common.api.AstMethod
import org.jacoco.core.internal.flow.ClassProbesVisitor
import org.jacoco.core.internal.flow.IFrame
import org.jacoco.core.internal.flow.MethodProbesVisitor
import org.jacoco.core.internal.instr.InstrSupport
import org.objectweb.asm.*
import org.objectweb.asm.tree.*

class ClassProbeCounter(val name: String) : ClassProbesVisitor() {
    var count = 0
        private set
    val astClass = newAstClass(name, ArrayList())

    override fun visitMethod(
        access: Int, name: String?, desc: String?, signature: String?, exceptions: Array<out String>?
    ): MethodProbesVisitor {
        return MethodProbeCounter(astClass.methods as MutableList)
    }

    override fun visitTotalProbeCount(count: Int) {
        this.count = count
    }
}


class MethodProbeCounter(
    private val methods: MutableList<AstMethod>
) : MethodProbesVisitor() {

    private val probes = ArrayList<Int>()
    private lateinit var methodNode: MethodNode


    override fun visitEnd() {
        super.visitEnd()
        val method = AstMethod(
            name = methodNode.name,
            params = getParams(methodNode),
            returnType = getReturnType(methodNode),
            checksum = "",
            probes = probes
        )
        methods.add(method)
    }

    override fun accept(methodNode: MethodNode?, methodVisitor: MethodVisitor?) {
        this.methodNode = methodNode!!
        super.accept(methodNode, methodVisitor)
    }

    override fun visitProbe(probeId: Int) {
        super.visitProbe(probeId)
        probes += probeId
    }

    override fun visitInsnWithProbe(opcode: Int, probeId: Int) {
        super.visitInsnWithProbe(opcode, probeId)
        probes += probeId
    }

    override fun visitJumpInsnWithProbe(opcode: Int, label: Label?, probeId: Int, frame: IFrame?) {
        super.visitJumpInsnWithProbe(opcode, label, probeId, frame)
        probes += probeId
    }
}

fun parseAstClass(className: String, classBytes: ByteArray): AstEntity {
    val classReader = InstrSupport.classReaderFor(classBytes)
    val counter = ClassProbeCounter(className)
    classReader.accept(DrillClassProbesAdapter(counter, false), 0)

    val astClass = counter.astClass
    val astMethodsWithChecksum = checksumCalculation(classBytes, className, astClass)
    astClass.methods = astMethodsWithChecksum
    return astClass
}

fun newAstClass(
    className: String,
    methods: MutableList<AstMethod> = ArrayList()
) = AstEntity(
    path = getPackageName(className),
    name = getShortClassName(className),
    methods
)

private fun getShortClassName(className: String): String {
    val lastSlashIndex: Int = className.lastIndexOf('/')
    return if (lastSlashIndex != -1) {
        className.substring(lastSlashIndex + 1)
    } else {
        className
    }
}

private fun getPackageName(className: String): String {
    val lastSlashIndex: Int = className.lastIndexOf('/')
    return if (lastSlashIndex != -1) {
        className.substring(0, lastSlashIndex)
    } else {
        ""
    }
}

private fun getReturnType(methodNode: MethodNode): String {
    val returnTypeDesc: String = Type.getReturnType(methodNode.desc).descriptor
    return Type.getType(returnTypeDesc).className
}

private fun getParams(methodNode: MethodNode): List<String> {
    val params = ArrayList<String>()
    val parameterTypes: Array<Type> = Type.getArgumentTypes(methodNode.desc)
    for (parameterType in parameterTypes) {
        params.add(parameterType.className)
    }
    return params
}