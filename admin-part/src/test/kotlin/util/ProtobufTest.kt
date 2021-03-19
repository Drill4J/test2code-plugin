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
package com.epam.drill.plugins.test2code.util

import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.coverage.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.*
import kotlin.test.*


@Serializable
private data class TestClass(
    val int: Int,
    @StringIntern
    val string: String
)

@Serializable
private data class ListWrapper(val list: List<TestClass>)

class ProtobufTest {
    @Test
    fun `decoded string of POJO must be in string pool`() {
        val string = "I am string"
        val testClass = ProtoBuf.load(
            TestClass.serializer(),
            ProtoBuf.dump(TestClass.serializer(), TestClass(1, string))
        )
        assertSame(string, testClass.string)
    }

    @Test
    fun `decoded strings of list must be in string pool`() {
        val string = "I am string"
        val wrapperTestClass = ProtoBuf.load(
            ListWrapper.serializer(),
            ProtoBuf.dump(ListWrapper.serializer(), ListWrapper(Array(10) { TestClass(1, string) }.toList()))
        )
        wrapperTestClass.list.forEach { assertSame(string, it.string) }
    }

    @Test
    fun `decoded strings of hierarchical structure must be in string pool`() {
        val bundleCounter = BundleCounter(
            name = "BundleCounter",
            packages = listOf(
                PackageCounter(
                    "Package",
                    zeroCount,
                    zeroCount,
                    zeroCount,
                    listOf(
                        ClassCounter(
                            "Path",
                            "ClassCounter",
                            zeroCount,
                            listOf(
                                MethodCounter(
                                    "MethodCounter",
                                    "desc",
                                    "decl",
                                    zeroCount
                                )
                            )
                        )
                    )
                )
            )
        )
        val decodedBundleCounter = ProtoBuf.load(
            BundleCounter.serializer(),
            ProtoBuf.dump(BundleCounter.serializer(), bundleCounter)
        )
        assertNotSame(bundleCounter, decodedBundleCounter)
        assertSame(bundleCounter.name, decodedBundleCounter.name)

        val packageCounter = bundleCounter.packages.first()
        val decodePackageCounter = decodedBundleCounter.packages.first()
        assertSame(packageCounter.name, decodePackageCounter.name)

        val classCounter = packageCounter.classes.first()
        val decodeClassCounter = decodePackageCounter.classes.first()
        assertSame(classCounter.name, decodeClassCounter.name)
        assertSame(classCounter.path, decodeClassCounter.path)

        val methodCounter = classCounter.methods.first()
        val decodeMethodCounter = decodeClassCounter.methods.first()
        assertSame(methodCounter.name, decodeMethodCounter.name)
        assertSame(methodCounter.desc, decodeMethodCounter.desc)
        assertSame(methodCounter.decl, decodeMethodCounter.decl)
    }
}
