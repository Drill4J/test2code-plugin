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
import com.epam.drill.plugins.test2code.ast.CheckProbeRanges
import com.epam.drill.plugins.test2code.common.api.AstMethod
import com.epam.drill.plugins.test2code.ast.SimpleClass
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class AstTest {

    @Test
    fun `check parsing method signature`() {
        val astEntity = parseAstClass(SimpleClass::class.getFullName(), SimpleClass::class.readBytes())
        assertEquals(SimpleClass::class.simpleName, astEntity.name)
        assertEquals("com/epam/drill/plugins/test2code/ast", astEntity.path)
        assertEquals(4, astEntity.methods.size)
        astEntity.methods[0].run {
            assertEquals("<init>", name)
            assertNotNull(checksum)
        }
        astEntity.methods[1].run {
            assertEquals("simpleMethod", name)
            assertNotNull(checksum)
        }
        astEntity.methods[2].run {
            assertEquals("methodWithReturn", name)
            assertEquals("java.lang.String", returnType)
            assertNotNull(checksum)
        }
        astEntity.methods[3].run {
            assertEquals("methodWithParams", name)
            assertEquals(listOf("java.lang.String", "int"), params)
            assertNotNull(checksum)
        }
    }

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

    @Test
    fun `method with different params`() {
        val methodName = "methodWithDifferentParams"
        assertChecksum(methodName)
    }

    @Test
    fun `method with incremented value`() {
        val methodName = "methodWithIteratedValue"
        assertChecksum(methodName)
    }

    @Test
    fun `change name of local var`() {
        val methodName = "changeLocalVarName"
        assertChecksum(methodName)
    }


    @Test
    fun `check probe ranges`() {
        val astEntity = parseAstClass(CheckProbeRanges::class.getFullName(), CheckProbeRanges::class.readBytes())
        val methods = astEntity.methods.groupBy { it.name }.mapValues { it.value[0] }

        /**
         *     void noOp() {
         *         AgentProbes var1 = ...
         *         var1.set(1);
         *     }
         */
        methods["noOp"].assertProbesCount(1)

        /**
         *     String oneOp() {
         *         AgentProbes var1 = ...
         *         var1.set(2);
         *         return "oneOp";
         *     }
         */
        methods["oneOp"].assertProbesCount(1)

        /**
         *     void twoOps(List<String> list) {
         *         AgentProbes var2 = ...
         *         list.add("1");
         *         var2.set(3);
         *         list.add("2");
         *         var2.set(4);
         *     }
         */
        methods["twoOps"].assertProbesCount(2)

        /**
         *     String ifOp(boolean b) {
         *         AgentProbes var2 = ...
         *         if (b) {
         *             var2.set(5);
         *             return "true";
         *         } else {
         *             var2.set(6);
         *             return "false";
         *         }
         *     }
         */
        methods["ifOp"].assertProbesCount(2)

        /**
         *     String ifExpr(boolean b) {
         *         AgentProbes var2 = ...
         *         String var10000;
         *         if (b) {
         *             var10000 = "true";
         *             var2.set(7);
         *         } else {
         *             var10000 = "false";
         *             var2.set(8);
         *         }
         *
         *         var2.set(9);
         *         return var10000;
         *     }
         */
        methods["ifExpr"].assertProbesCount(3)

        /**
         *     void whileOp(List<String> list) {
         *         AgentProbes var2 = ...
         *
         *         while(!list.isEmpty()) {
         *             var2.set(10);
         *             list.remove(0);
         *             var2.set(11);
         *         }
         *
         *         var2.set(12);
         *     }
         */
        methods["whileOp"].assertProbesCount(3)

        /**
         *     void methodWithLambda(List<String> list) {
         *         AgentProbes var2 = ...
         *         list.forEach((s) -> {
         *            AgentProbes var1 = ...
         *            System.out.println(s);
         *            var1.set(15);
         *         });
         *         var2.set(13);
         *     }
         */
        methods["methodWithLambda"].assertProbesCount(1)
        methods["lambda\$methodWithLambda\$0"].assertProbesCount(1)

        /**
         *     void methodRef(List<String> list) {
         *         AgentProbes var2 = ...
         *         PrintStream var10001 = System.out;
         *         list.forEach(var10001::println);
         *         var2.set(14);
         *     }
         */
        methods["methodRef"].assertProbesCount(1)
    }

    private fun assertChecksum(methodName: String) {
        assertNotEquals(
            methodsBuild1.filter { it.name == methodName }[0].checksum,
            methodsBuild2.filter { it.name == methodName }[0].checksum
        )
    }

}

internal fun KClass<*>.readBytes(): ByteArray = java.getResourceAsStream(
    "/${getFullName()}.class"
)!!.readBytes()

internal fun KClass<*>.getFullName() = java.name.replace('.', '/')

internal fun AstMethod?.assertProbesCount(count: Int) {
    assertNotNull(this)
    assertEquals(count, this.probes.size)
}

