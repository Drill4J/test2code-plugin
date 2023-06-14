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
import com.epam.drill.plugins.test2code.common.api.AstEntity
import com.epam.drill.plugins.test2code.common.api.AstMethod
import org.jacoco.core.internal.data.CRC64
import org.jacoco.core.internal.flow.ClassProbesVisitor
import org.jacoco.core.internal.flow.IFrame
import org.jacoco.core.internal.flow.MethodProbesVisitor
import org.jacoco.core.internal.instr.InstrSupport
import org.objectweb.asm.*
import org.objectweb.asm.tree.*
import java.nio.ByteBuffer


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
            checksum = calculateMethodHash(methodNode),
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
    return counter.astClass
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

private fun calculateMethodHash(methodNode: MethodNode): String {
    // filter "-1" virtual opcodes: LabelNode, LineNumberNode.
    val instructions = methodNode.instructions.filter { (it?.opcode != -1) }

    val builder: StringBuilder = StringBuilder()
    // Skip "this" object
    methodNode.localVariables.filter { it.name != "this" }.forEach {
        builder.append("${it.desc}/")
        builder.append("${it.index} = ${it.name}/")
    }
    for (insnNode in instructions) {
        builder.append("${insnNode.opcode}$")
        when (insnNode) {
            //context of int var
            is IntInsnNode -> {
                builder.append("${insnNode.operand}/")
            }
            //context of type instruction
            is TypeInsnNode -> {
                builder.append("${insnNode.desc}/")
            }
            //context of reference call prep.
            is FieldInsnNode -> {
                builder.append("${insnNode.owner}/")
                builder.append("${insnNode.desc}/")
                builder.append("${insnNode.name}/")
            }
            //context of instance method
            is MethodInsnNode -> {
                builder.append("${insnNode.owner}/")
                builder.append("${insnNode.desc}/")
                builder.append("${insnNode.name}/")
            }
            //context of primitive var
            is LdcInsnNode -> {
                builder.append("${insnNode.cst}/")
            }
            //context of a sequence switch case
            is TableSwitchInsnNode -> {
                builder.append("${insnNode.min}/")
                builder.append("${insnNode.max}/")
                builder.append("${insnNode.labels.size}/")
            }
            //context of a distributed value of cases
            is LookupSwitchInsnNode -> {
                builder.append("${insnNode.labels.size}/")
                insnNode.keys.forEach { builder.append("${it}/") }
            }
            // context of matrix view [][]...n[]
            is MultiANewArrayInsnNode -> {
                builder.append("${insnNode.desc}/")
                builder.append("${insnNode.dims}/")
            }
            // context of incremented value
            is IincInsnNode -> {
                builder.append("${insnNode.incr}/")
                val localVariable = methodNode.localVariables[insnNode.`var`]
                builder.append("${localVariable.desc}/")
                builder.append("${localVariable.index} =${localVariable.name}/")
            }
        }
    }
    val builderString = builder.toString()
    // multiply on 4 is necessary, cause position increments by four
    val buffer = ByteBuffer.allocate(builderString.length * 4)
    builderString.toCharArray().map { it.code }.toIntArray().forEach { buffer.putInt(it) }

    val bytecode = buffer.array()
    return CRC64.classId(bytecode).toString(Character.MAX_RADIX)
}
