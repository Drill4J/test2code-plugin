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

import com.epam.drill.plugins.test2code.ast.Build1
import com.epam.drill.plugins.test2code.ast.Build2
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ChecksumCalculationTest {

    private val methodsBuild1 =
        parseAstClass(
            Build1::class.getFullName(),
            Build1::class.readBytes()
        ).methods
    private val methodsBuild2 =
        parseAstClass(
            Build2::class.getFullName(),
            Build2::class.readBytes()
        ).methods

    @Test
    fun `lambda with different context should have other checksum`() {
        val methodName = "lambda\$differentContextChangeAtLambda\$1"
        // Lambdas should have different value, because they contain different context
        assertChecksum(methodName)
    }

    @Test
    fun `constant lambda hash calculation`() {
        val methodName = "lambda\$constantLambdaHashCalculation\$6"
        assertEquals(
            methodsBuild1.filter { it.name == methodName }[0].checksum,
            methodsBuild2.filter { it.name == methodName }[0].checksum
        )
    }

    @Test
    fun `method with lambda should have the same checksum`() {
        val methodName = "theSameMethodBody"
        //Methods, which contain lambda with different context, should not have difference in checksum
        assertEquals(
            methodsBuild1.filter { it.name == methodName }[0].checksum,
            methodsBuild2.filter { it.name == methodName }[0].checksum
        )
    }

    @Test
    fun `test on different context at inner lambda`() {
        val methodName = "lambda\$null\$2"
        assertChecksum(methodName)
    }

    @Test
    fun `should have different checksum for reference call`() {
        val methodName = "referenceMethodCall"
        assertChecksum(methodName)
    }

    @Test
    fun `should have different checksum for array`() {
        val methodName = "multiANewArrayInsnNode"
        assertChecksum(methodName)
    }

    @Test
    fun `should have different checksum for switch table`() {
        val methodName = "tableSwitchMethodTest"
        assertChecksum(methodName)
    }

    @Test
    fun `should have different checksum for look up switch`() {
        val methodName = "lookupSwitchMethodTest"
        assertChecksum(methodName)
    }

    @Test
    fun `method with different instance type`() {
        val methodName = "differentInstanceType"
        assertChecksum(methodName)
    }

    @Test
    fun `method call other method`() {
        val methodName = "callOtherMethod"
        assertChecksum(methodName)
    }
//    Do we need to cover that case?
//    @Test
//    fun `method with different params`() {
//        val methodName = "methodWithDifferentParams"
//        assertChecksum(methodName)
//    }

    @Test
    fun `method with incremented value`() {
        val methodName = "methodWithIteratedValue"
        assertChecksum(methodName)
    }

//   Do we need to cover that case?
//    @Test
//    fun `change name of local var`() {
//        val methodName = "changeLocalVarName"
//        assertChecksum(methodName)
//    }

    private fun assertChecksum(methodName: String) {
        assertNotEquals(
            methodsBuild1.filter { it.name == methodName }[0].checksum,
            methodsBuild2.filter { it.name == methodName }[0].checksum
        )
    }
}