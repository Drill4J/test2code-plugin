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

import com.epam.drill.plugins.test2code.ast.CheckProbeRanges
import com.epam.drill.plugins.test2code.common.api.AstMethod
import com.epam.drill.plugins.test2code.ast.SimpleClass
import com.epam.drill.plugins.test2code.lambda.*
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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

    @Test
    fun `lambda with different context should have other checksum`() {
        val astEntity =
            parseAstClass(
                ClassWithDifferentReferences::class.getFullName(),
                ClassWithDifferentReferences::class.readBytes()
            )

        val methods = astEntity.methods
        //Methods, which contain only lambda, should be equal
        assertEquals(methods[1].checksum, methods[3].checksum)
        assertEquals(methods[2].checksum, methods[4].checksum)

        // Lambdas should have different value, because they contain different context
        assertNotEquals(methods[5].checksum, methods[6].checksum)
    }

    @Test
    fun `test on existing inner lambda`() {
        val astEntity =
            parseAstClass(
                ClassWithInnerLambda::class.getFullName(),
                ClassWithInnerLambda::class.readBytes()
            )

        assertTrue(astEntity.methods[3].name.contains("lambda\$null"))
    }

    @Test
    fun `test on existing inner lambdas`() {
        val astEntity =
            parseAstClass(
                ClassWithInnerLambdas::class.getFullName(),
                ClassWithInnerLambdas::class.readBytes()
            )

        assertTrue(astEntity.methods[4].name.contains("lambda\$null"))
        assertTrue(astEntity.methods[6].name.contains("lambda\$null"))
    }

    @Test
    fun `test class with lambda`() {
        val astEntity =
            parseAstClass(
                ClassWithLambda::class.getFullName(),
                ClassWithLambda::class.readBytes()
            )

        assertTrue(astEntity.methods[3].name.contains("lambda\$secondMethod\$1"))
        assertTrue(astEntity.methods[4].name.contains("lambda\$firstMethod\$0"))
    }

    @Test
    fun `test class with lambda return`() {
        val astEntity =
            parseAstClass(
                ClassWithLambdaReturning::class.getFullName(),
                ClassWithLambdaReturning::class.readBytes()
            )

        assertEquals(astEntity.methods[1].returnType, "java.util.function.Function")
    }

    @Test
    fun `test class with reference call`() {
        val astEntity =
            parseAstClass(
                ClassWithReferences::class.getFullName(),
                ClassWithReferences::class.readBytes()
            )
        //Reference call has the same view as lambda
        assertNotNull(astEntity.methods[2])
        assertEquals(astEntity.methods[2].checksum, "-n27yc05empbd")
    }

    @Test
    fun `test method with different instance type`() {
        val astEntity =
            parseAstClass(
                ClassWithInstanceType::class.getFullName(),
                ClassWithInstanceType::class.readBytes()
            )
        //Should be different checksum of method
        assertNotEquals(astEntity.methods[1].checksum,astEntity.methods[2].checksum)
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