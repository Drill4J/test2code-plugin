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
import com.epam.drill.plugins.test2code.checksum.calculateMethodsChecksums
import com.epam.drill.plugins.test2code.common.api.AstEntity
import com.epam.drill.plugins.test2code.common.api.AstMethod
import com.sun.xml.internal.fastinfoset.util.StringArray
import org.jacoco.core.internal.flow.ClassProbesVisitor
import org.jacoco.core.internal.flow.IFrame
import org.jacoco.core.internal.flow.MethodProbesVisitor
import org.jacoco.core.internal.instr.InstrSupport
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.MethodNode


open class ProbeCounter : ClassProbesVisitor() {
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

class ClassProbeCounter(val name: String) : ProbeCounter() {
    val astClass = AstEntity(
        path = getPackageName(name),
        name = getShortClassName(name),
        methods = ArrayList(),
        annotations = mutableMapOf()
    )

    override fun visitMethod(
        access: Int, name: String?, desc: String?, signature: String?, exceptions: Array<out String>?
    ): MethodProbesVisitor {
        return MethodProbeCounter(astClass.methods as MutableList)
    }

    override fun visitAnnotation(desc: String?, visible: Boolean): AnnotationVisitor? {
        val annotationName = Type.getType(desc).className
        val annotationValues = mutableListOf<String>()

        val annotationVisitor = object : AnnotationVisitor(api) {
            override fun visit(name: String?, value: Any?) {
                annotationValues.add(value.toString())
            }
        }
        (astClass.annotations)[annotationName] = annotationValues

        return annotationVisitor
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
            probes = probes,
            annotations = getAnnotations(methodNode)
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
    val astMethodsWithChecksum = calculateMethodsChecksums(classBytes, className)

    astClass.methods = astClass.methods.map {
        it.copy(
            checksum = astMethodsWithChecksum[it.classSignature()] ?: ""
        )
    }
    return astClass
}


private fun AstMethod.classSignature() =
    "${name}/${params.joinToString()}/${returnType}"

private fun getShortClassName(className: String): String {
    val lastSlashIndex: Int = className.lastIndexOf('/')
    val hasPackage = lastSlashIndex != -1
    return if (hasPackage) {
        className.substring(lastSlashIndex + 1)
    } else {
        className
    }
}

private fun getPackageName(className: String): String {
    val lastSlashIndex: Int = className.lastIndexOf('/')
    val hasPackage = lastSlashIndex != -1
    return if (hasPackage) {
        className.substring(0, lastSlashIndex)
    } else {
        ""
    }
}

private fun getReturnType(methodNode: MethodNode): String {
    val returnTypeDesc: String = Type.getReturnType(methodNode.desc).descriptor
    return Type.getType(returnTypeDesc).className
}

private fun getParams(methodNode: MethodNode): List<String> = Type
    .getArgumentTypes(methodNode.desc)
    .map { it.className }

private fun getAnnotations(methodNode: MethodNode): MutableMap<String, MutableList<String>> {
    val annotationToValue = mutableMapOf<String, MutableList<String>>()
    mutableListOf<AnnotationNode>().also {
        it.addAll(methodNode.visibleAnnotations ?: mutableListOf())
        it.addAll(methodNode.invisibleAnnotations ?: mutableListOf())
    }.forEach {
        val valuesOfAnnotation = mutableListOf<String>()
        parseAnnotationValuesToString(it, valuesOfAnnotation)
        annotationToValue[it.desc] = valuesOfAnnotation
    }
    return annotationToValue
}

private fun parseAnnotationValuesToString(
    annotationNode: AnnotationNode,
    valuesOfAnnotation: MutableList<String>
) {
    annotationNode.values?.forEach { el ->
        run {
            when (el) {
                is StringArray -> el._array.forEach { x -> valuesOfAnnotation.add(x.toString()) }
                is List<*> -> el.map { x -> valuesOfAnnotation.add(x.toString()) }
                is AnnotationNode -> parseAnnotationValuesToString(el, valuesOfAnnotation)
                else -> valuesOfAnnotation.add(el.toString())
            }
        }
    }
}