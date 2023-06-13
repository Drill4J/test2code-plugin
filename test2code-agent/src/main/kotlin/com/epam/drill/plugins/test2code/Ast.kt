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

import com.epam.drill.plugins.test2code.common.api.AstEntity
import com.epam.drill.plugins.test2code.common.api.AstMethod
import org.jacoco.core.internal.data.CRC64
import org.objectweb.asm.*
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import java.nio.ByteBuffer

fun parseAstClass(className: String, classBytes: ByteArray): AstEntity {
    val methods: MutableList<AstMethod> = ArrayList()
    val astClass = AstEntity(path = getPackageName(className), name = getShortClassName(className), methods)
    val classReader = ClassReader(classBytes)

    val classVisitor: ClassVisitor = object : ClassVisitor(Opcodes.ASM9) {
        override fun visitMethod(
            access: Int,
            name: String,
            desc: String,
            signature: String?,
            exceptions: Array<String>?
        ): MethodVisitor {
            super.visitMethod(access, name, desc, signature, exceptions)
            val methodNode = MethodNode(Opcodes.ASM9, access, name, desc, signature, exceptions)
            return object : MethodVisitor(Opcodes.ASM9, methodNode) {
                override fun visitEnd() {
                    super.visitEnd()
                    val method = AstMethod(
                        name = methodNode.name,
                        params = getParams(methodNode),
                        returnType = getReturnType(methodNode),
                        checksum = calculateMethodHash(methodNode),
                        probes = listOf(0,1,2)//TODO change the stub
                    )
                    methods.add(method)
                }
            }
        }
    }
    classReader.accept(classVisitor, ClassReader.SKIP_DEBUG or ClassReader.EXPAND_FRAMES)
    return astClass
}

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
    val instructions: MutableList<AbstractInsnNode?> = ArrayList(methodNode.instructions.size())
    for (insnNode in methodNode.instructions.toArray()) {
        instructions.add(insnNode)
    }
    // 8 is needed to increase buffer capacity
    val buffer = ByteBuffer.allocate((instructions.size * 8))
    for (insnNode in instructions) {
        buffer.putInt(insnNode!!.opcode)
        when (insnNode) {
            //To cover the context of lambda
            is LdcInsnNode -> {
                buffer.putInt(insnNode.cst.hashCode())
            }
            //To cover the context of int var
            is IntInsnNode -> {
                buffer.putInt(insnNode.operand.hashCode())
            }
            //To cover the context of instance method
            is MethodInsnNode -> {
                buffer.putInt(insnNode.name.hashCode())
            }
            //To cover the context of reference call prep.
            is FieldInsnNode -> {
                buffer.putInt(insnNode.name.hashCode())
            }
        }

    }
    val bytecode = buffer.array()
    return CRC64.classId(bytecode).toString(Character.MAX_RADIX)
}
