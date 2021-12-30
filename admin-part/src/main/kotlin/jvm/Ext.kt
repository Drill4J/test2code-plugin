/**
 * Copyright 2020 EPAM Systems
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
package com.epam.drill.plugins.test2code.jvm

import com.epam.drill.plugins.test2code.Method
import com.epam.drill.plugins.test2code.coverage.*
import com.epam.drill.plugins.test2code.util.*
import com.epam.dsm.util.*
import org.apache.bcel.classfile.*
import java.io.*

internal fun ClassCounter.parseMethods(classBytes: ByteArray): List<Method> = run {
    val classParser = ClassParser(ByteArrayInputStream(classBytes), fullName)
    val parsedClass = classParser.parse()
    val parsedMethods = parsedClass.run {
        methods.associateBy { signature(fullName, it.name, it.signature) }
    }
    val lambdas = parsedMethods.filter { LAMBDA in it.key }
    val bootstrapMethods = parsedClass.bootstrapMethods()
    methods.map { m ->
        val method = parsedMethods[m.sign.weakIntern()]
        val parser = LambdaParser(bootstrapMethods, lambdas)
        val hash = method.takeIf { it !in lambdas.values }.let { parser.checksum(it) }
        Method(
            ownerClass = fullName.weakIntern(),
            name = methodName(m.name, fullName),
            desc = m.desc.weakIntern(),
            hash = hash,
            lambdasHash = parser.lambdaHash
        )
    }
}

private fun LambdaParser.checksum(
    method: org.apache.bcel.classfile.Method?,
): String = (method?.code?.run {
    codeToString(code, constantPool, 0, length, false)
} ?: "").crc64.weakIntern()

/**
 * For each lambda creates a bootstrap method, which make references back into the main constant pool.
 * Bootstrap method of lambda and method reference have a fixed set of arguments, the second of them is the index of the lambda method
 * in the constant pool.
 * For more info: https://blogs.oracle.com/javamagazine/post/behind-the-scenes-how-do-lambda-expressions-really-work-in-java
 */
internal fun ConstantPool.parse(
    bootstrapMethod: BootstrapMethod
): String? = bootstrapMethod.takeIf { it.numBootstrapArguments == LAMBDA_NUM_ARGS }?.run {
    val (_, lambdaMethodIndex, _) = bootstrapArguments
    lambdaMethodIndex
}?.let { getConstant(it) as? ConstantMethodHandle }
    ?.let { getConstant(it.referenceIndex) as ConstantCP }
    ?.let {
        val classIndex = getConstant(it.classIndex) as ConstantClass
        val nameAndTypeIndex = getConstant(it.nameAndTypeIndex) as ConstantNameAndType
        classIndex to nameAndTypeIndex
    }?.let { (classIndex, nameAndTypeIndex) ->
        val classFullName = (getConstant(classIndex.nameIndex) as ConstantUtf8).bytes
        val methodName = (getConstant(nameAndTypeIndex.nameIndex) as ConstantUtf8).bytes
        val signature = (getConstant(nameAndTypeIndex.signatureIndex) as ConstantUtf8).bytes
        signature(classFullName, methodName, signature)
    } ?: bootstrapMethod.toString(this)

internal fun JavaClass.bootstrapMethods() = attributes
    .filterIsInstance<BootstrapMethods>()
    .firstOrNull()?.bootstrapMethods ?: emptyArray()
