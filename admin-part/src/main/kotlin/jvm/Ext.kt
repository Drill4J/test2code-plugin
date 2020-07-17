package com.epam.drill.plugins.test2code.jvm

import com.epam.drill.common.Method
import com.epam.drill.plugins.test2code.*
import com.epam.drill.plugins.test2code.coverage.*
import org.apache.bcel.classfile.*
import java.io.*

internal fun ClassCounter.parseMethods(classBytes: ByteArray): List<Method> = run {
    val classParser = ClassParser(ByteArrayInputStream(classBytes), fullName)
    val parsedMethods = classParser.parse().run {
        methods.associateBy { it.name to it.signature }
    }
    methods.map { m ->
        val method = parsedMethods[m.name to m.desc]
        Method(
            ownerClass = fullName,
            name = m.name,
            desc = m.desc,
            hash = method.checksum()
        )
    }
}

private fun org.apache.bcel.classfile.Method?.checksum(): String = (this?.code?.run {
    Utility.codeToString(code, constantPool, 0, length, false)
} ?: "").crc64
