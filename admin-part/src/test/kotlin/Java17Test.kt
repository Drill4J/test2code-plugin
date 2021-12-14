package com.epam.drill.plugins.test2code

import com.epam.drill.plugins.test2code.jvm.*
import org.apache.bcel.classfile.*
import org.junit.jupiter.api.Test
import java.io.*
import kotlin.test.*


class Java17Test {

    @Test
    fun `parse bootstrap methods`() {
        val classname = "Java17TestClass.class"
        val bytes = this::class.java.classLoader.getResourceAsStream(classname)?.readBytes() ?: byteArrayOf()
        val classParser = ClassParser(ByteArrayInputStream(bytes), classname)
        val parsedClass = classParser.parse()
        val bootstrapMethods = parsedClass.parsedBootstrapMethods()
        assertEquals(4, bootstrapMethods.count())
        assertEquals(3, bootstrapMethods.count { LAMBDA in it })
        assertEquals(1, bootstrapMethods.count { "makeConcatWithConstants" in it })
    }
}
