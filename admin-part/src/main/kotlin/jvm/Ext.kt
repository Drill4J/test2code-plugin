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
import com.epam.kodux.util.*
import kotlinx.collections.immutable.*
import org.apache.bcel.classfile.*
import org.objectweb.asm.*
import java.io.*
import java.util.concurrent.*

val lambdas = ConcurrentHashMap<String, PersistentSet<String>>()

internal fun ClassCounter.parseMethods(classBytes: ByteArray): List<Method> = run {
    ClassReader(classBytes).accept(
        LambdaClassVisitor(fullName, Opcodes.ASM7),
        ClassReader.EXPAND_FRAMES
    )
    val classParser = ClassParser(ByteArrayInputStream(classBytes), fullName)
    val parsedMethods = classParser.parse().run {
        methods.associateBy { signature(fullName, it.name, it.signature) }
    }
    methods.map { m ->
        val method = parsedMethods[m.sign.weakIntern()]
        Method(
            ownerClass = fullName.weakIntern(),
            name = methodName(m.name, fullName),
            desc = m.desc.weakIntern(),
            hash = method.checksum(),
            lambdasHash = lambdas[m.sign]?.mapNotNull { sign ->
                parsedMethods[sign]?.let { sign to it.checksum() }
            }?.toMap() ?: emptyMap()
        )
    }
}.also { lambdas.clear() }

private fun org.apache.bcel.classfile.Method?.checksum(): String = (this?.code?.run {
    Utility.codeToString(code, constantPool, 0, length, false)
} ?: "").crc64.weakIntern()
