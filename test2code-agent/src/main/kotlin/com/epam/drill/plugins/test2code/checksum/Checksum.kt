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
package com.epam.drill.plugins.test2code.checksum

import com.epam.drill.plugins.test2code.common.api.AstEntity
import com.epam.drill.plugins.test2code.common.api.AstMethod
import org.apache.bcel.classfile.ClassParser
import org.apache.bcel.classfile.Method
import org.jacoco.core.internal.data.CRC64
import java.io.ByteArrayInputStream

internal fun checksumCalculation(
    classBytes: ByteArray,
    className: String,
    astClass: AstEntity
): List<AstMethod> {
    val astMethods = astClass.methods

    //Bcel parses process
    val classParser = ClassParser(ByteArrayInputStream(classBytes), className)
    val bcelClass = classParser.parse()
    val bcelMethods = bcelClass.methods
    //Map of ClassName to Bcel method instance
    val bcelMethodsMap = bcelMethods.associateBy {
        it.classSignature()
    }

    return astMethods.map { astMethod ->
        val astSignature = astMethod.classSignature()
        val bcelMethod = bcelMethodsMap[astSignature]

        val codeToStringService = CodeToStringService()
        val methodHash = bcelMethod.let { codeToStringService.checksum(it) }

        astMethod.copy(checksum = methodHash)
    }
}

private fun AstMethod.classSignature() =
    "${name}/${params.joinToString()}/${returnType}"

private fun Method.classSignature() =
    "${name}/${argumentTypes.asSequence().map { type -> type.toString() }.joinToString()}/${returnType}"

private fun CodeToStringService.checksum(
    method: Method?,
): String = (method?.code?.run {
    codeToString(code, constantPool, 0, length, false)
} ?: "").crc64

val String.crc64: String get() = CRC64.classId(toByteArray()).toString(Character.MAX_RADIX)
