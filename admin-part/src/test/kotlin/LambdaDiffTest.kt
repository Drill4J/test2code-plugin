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

import kotlin.test.*

class LambdaDiffTest {
    private val methodExample = Method(
        "foo/bar/Bar",
        "method",
        "()V",
        hash = "123",
        lambdasHash = mapOf("lambda\$method\$1" to "456"),
    )

    @Test
    fun `diff - lambda has not changed, method has changed`() {
        val firstBuildMethods = listOf(methodExample)
        val secondBuildMethods = listOf(methodExample.copy(hash = "896"))
        val diff = firstBuildMethods.diff(secondBuildMethods)
        assertTrue { diff.modified.isNotEmpty() }
        assertEquals(methodExample, diff.modified.first())
    }

    @Test
    fun `diff - method without lambda has changed`() {
        val expectedMethod = methodExample.copy(lambdasHash = emptyMap())
        val firstBuildMethods = listOf(expectedMethod)
        val secondBuildMethods = listOf(expectedMethod.copy(hash = "896"))
        val diff = firstBuildMethods.diff(secondBuildMethods)
        assertTrue { diff.modified.isNotEmpty() }
        assertEquals(expectedMethod, diff.modified.first())
    }

    @Test
    fun `diff - method without lambda doesn't changed`() {
        val expectedMethod = methodExample.copy(lambdasHash = emptyMap())
        val firstBuildMethods = listOf(expectedMethod)
        val secondBuildMethods = listOf(expectedMethod)
        val diff = firstBuildMethods.diff(secondBuildMethods)
        assertTrue { diff.modified.isEmpty() }
        assertTrue { diff.unaffected.isNotEmpty() }
        assertEquals(expectedMethod, diff.unaffected.first())
    }

    @Test
    fun `diff - lambda name has changed`() {
        val firstBuildMethods = listOf(methodExample)
        val secondBuildMethods = listOf(
            methodExample.copy(
                lambdasHash = methodExample.lambdasHash.mapKeys { it.key + "f" }
            )
        )
        val diff = firstBuildMethods.diff(secondBuildMethods)
        assertTrue { diff.modified.isEmpty() }
        assertTrue { diff.unaffected.isNotEmpty() }
        assertEquals(methodExample, diff.unaffected.first())
    }

    @Test
    fun `diff - lambda has changed, method doesn't change`() {
        val changedMethod = methodExample.copy(
            lambdasHash = methodExample.lambdasHash.mapValues { it.value + "f" }
        )
        val firstBuildMethods = listOf(methodExample)
        val secondBuildMethods = listOf(methodExample.copy(
            lambdasHash = methodExample.lambdasHash.mapValues { it.value + "f" }
        ))
        val diff = secondBuildMethods.diff(firstBuildMethods)
        assertTrue { diff.modified.isNotEmpty() }
        assertEquals(changedMethod, diff.modified.first())
    }

    @Test
    fun `diff - lambda and method has changed`() {
        val firstBuildMethods = listOf(methodExample)
        val secondBuildMethods = listOf(
            methodExample.copy(
                hash = "qwerty",
                lambdasHash = methodExample.lambdasHash.mapValues { it.value + "f" })
        )
        val diff = firstBuildMethods.diff(secondBuildMethods)
        assertTrue { diff.modified.isNotEmpty() }
        assertEquals(methodExample, diff.modified.first())
    }

    @Test
    fun `diff - lambda and lambda name has changed`() {
        val firstBuildMethods = listOf(methodExample)
        val secondBuildMethods = listOf(
            methodExample.copy(
                hash = "qwerty",
                lambdasHash = methodExample.lambdasHash.map { (it.key + "some") to (it.value + "some") }.toMap()
            )
        )
        val diff = firstBuildMethods.diff(secondBuildMethods)
        assertTrue { diff.modified.isNotEmpty() }
        assertEquals(methodExample, diff.modified.first())
    }

    @Test
    fun `diff - lambda method shouldn't be in diff`() {
        val lambdaMethod = Method(
            ownerClass = "foo/bar/Bar",
            name = "lambda\$method\$0",
            desc = "(java/lang/String)V",
            hash = "125",
        )
        val firstBuildMethods = listOf(methodExample, lambdaMethod)
        val secondBuildMethod = listOf(
            methodExample.copy(hash = "894"),
            lambdaMethod.copy(name = "lambda\$method\$1"),
            lambdaMethod.copy(name = "lambda\$otherMethod\$0")
        )
        val diff = firstBuildMethods.diff(secondBuildMethod)
        assertEquals(1, diff.modified.size)
        assertEquals(0, diff.new.size)
    }
}
