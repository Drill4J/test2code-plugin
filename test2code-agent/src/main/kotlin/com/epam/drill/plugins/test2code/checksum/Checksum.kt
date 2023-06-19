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
    val bcelMethodsMap = bcelMethods.associateBy { it.name }

    return astMethods.map { astMethod ->
        val astSignature = astMethod.name
        val bcelMethod = bcelMethodsMap[astSignature]

        val codeToStringService = CodeToStringService()
        val methodHash = bcelMethod.let { codeToStringService.checksum(it) }

        astMethod.copy(checksum = methodHash)
    }
}

private fun CodeToStringService.checksum(
    method: Method?,
): String = (method?.code?.run {
    codeToString(code, constantPool, 0, length, false)
} ?: "").crc64

val String.crc64: String get() = CRC64.classId(toByteArray()).toString(Character.MAX_RADIX)
