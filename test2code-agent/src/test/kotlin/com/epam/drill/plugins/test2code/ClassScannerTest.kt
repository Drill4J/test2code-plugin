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

import com.epam.drill.plugins.test2code.classloading.scanClasses
import kotlin.test.Test
import kotlin.test.assertEquals

class ClassScannerTest {
    @Test
    fun `check class scan in one consumer call`() {
        var classCount = 0
        scanClasses(listOf("com/fixture/epam/drill/classloading/sub")) { classes ->
            classCount += classes.size
        }
        assertEquals(2, classCount)
    }

    @Test
    fun `check class scan in several consumer call`() {
        var classCount = 0
        var transferCount = 0
        scanClasses(listOf("com/fixture/epam/drill/classloading"), 5) { classes ->
            classCount += classes.size
            transferCount++
        }
        assertEquals(6, classCount)
        assertEquals(2, transferCount)
    }
}