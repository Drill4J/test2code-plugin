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
package com.epam.drill.plugins.test2code.test.java

import com.epam.drill.plugins.test2code.*
import com.epam.drill.plugins.test2code.common.api.*
import org.jacoco.core.internal.data.*

class EmptyBody {
    fun run() {
    }
}

const val fullNameClass = "com/epam/drill/plugins/test2code/test/java/EmptyBody"

val emptyBodyBytes = EmptyBody::class.java.readBytes()
val classBytesEmptyBody = mapOf(fullNameClass to emptyBodyBytes)

val manualFullProbes = listOf(
    ExecClassData(
        id = CRC64.classId(emptyBodyBytes),
        className = fullNameClass,
        testName = "test",
        probes = probesOf(true, true, true, true)
    )
)

val autoProbes = listOf(
    ExecClassData(
        id = CRC64.classId(emptyBodyBytes),
        className = fullNameClass,
        testName = "test1",
        probes = probesOf(true, false, false, false)
    )
)
val autoProbes2 = listOf(
    ExecClassData(
        id = CRC64.classId(emptyBodyBytes),
        className = fullNameClass,
        testName = "test2",
        probes = probesOf(true, true, true, false)
    )
)
