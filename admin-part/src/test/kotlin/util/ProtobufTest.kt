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
import kotlinx.serialization.*
import kotlinx.serialization.protobuf.*
import org.junit.jupiter.api.*
import kotlin.test.*
import kotlin.test.Test


@Serializable
private data class TestClass(
    val int: Int,
    @StringIntern
    val string: String
)

@Serializable
private data class ListWrapper(val list: List<TestClass>)

@Serializable
private data class ListListWrapper(val list: List<List<TestClass>>)

@Serializable
private data class MapWrapper(val map: Map<String, TestClass>)

@Serializable
private data class MapMapWrapper(val map: Map<String, Map<String, TestClass>>)

@Disabled
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
        val list = Array(10) { TestClass(1, string) }.toList()
        val wrapperTestClass = ProtoBuf.load(
            ListWrapper.serializer(),
            ProtoBuf.dump(ListWrapper.serializer(), ListWrapper(list))
        )
        assertEquals(list.size, wrapperTestClass.list.size)
        wrapperTestClass.list.forEach { assertSame(string, it.string) }
    }

    @Ignore
    @Test
    fun `decoded strings of list of list must be in string pool`() {
        val string = "I am string"
        val list = Array(5) { Array(10) { TestClass(1, string) }.toList() }.toList()
        val wrapperTestClass = ProtoBuf.load(
            ListListWrapper.serializer(),
            ProtoBuf.dump(
                ListListWrapper.serializer(),
                ListListWrapper(list)
            )
        )
        //default realisation doesn't work wright lists  are not equals, wtf ??
        assertEquals(list.size, wrapperTestClass.list.size)
        wrapperTestClass.list.forEach { listOfList -> listOfList.forEach { assertSame(string, it.string) } }
    }

    @Test
    fun `decoded strings of map must be in string pool`() {

        val string = "I am string"
        val strings = Array(10) { "$string $it" }
        val map = strings.asSequence().associateWith { TestClass(1, string) }.toMap()
        val wrapperTestClass = ProtoBuf.load(
            MapWrapper.serializer(),
            ProtoBuf.dump(
                MapWrapper.serializer(),
                MapWrapper(map)
            )
        )
        assertEquals(map.size, wrapperTestClass.map.size)
        wrapperTestClass.map.values.forEach { assertSame(string, it.string) }
    }

    @Test
    fun `decoded strings of map of map must be in string pool`() {

        val string = "I am string"
        val strings = Array(10) { "$string $it" }
        val map = strings.asSequence().associateWith {
            strings.asSequence().associateWith { TestClass(1, string) }.toMap()
        }
        val wrapperTestClass = ProtoBuf.load(
            MapMapWrapper.serializer(),
            ProtoBuf.dump(
                MapMapWrapper.serializer(),
                MapMapWrapper(map)
            )
        )
        assertEquals(map.size, wrapperTestClass.map.size)
        map.forEach { (k, v) ->
            assertEquals(v.size, wrapperTestClass.map[k]?.size)
        }
        assertEquals(map.size, wrapperTestClass.map.size)

        wrapperTestClass.map.values.forEach {
            it.values.forEach { test ->
                assertSame(test.string, string)
            }
        }
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
                                    "Package/ClassCounter" + ":" + "MethodCounter" + "desc",
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
