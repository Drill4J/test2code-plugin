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
