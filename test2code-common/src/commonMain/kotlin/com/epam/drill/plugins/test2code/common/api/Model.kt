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
package com.epam.drill.plugins.test2code.common.api

import com.epam.dsm.*
import kotlinx.serialization.*

const val DEFAULT_TEST_NAME = "unspecified"

/**
 * Ast metadata about the file or the class containing methods
 * @param path the path to class or file
 * @param name the name of class or file
 * @param methods the list of methods
 */
@Serializable
data class AstEntity(
    val path: String,
    val name: String,
    var methods: List<AstMethod>,
)

/**
 * Ast metadata about the method of class or file
 * @param name the name of the method
 * @param params the list of parameters of the method
 * @param returnType the method return type
 * @param count todo
 * @param probes the probe indices of the method
 * @param checksum the checksum of the method body
 */
@Serializable
data class AstMethod(
    val name: String,
    val params: List<String>,
    val returnType: String,
    val probes: List<Int> = emptyList(),
    val checksum: String = "",
) {
    val count: Int = probes.size
}

@Serializable
data class ActionScopeResult(
    val id: String,
    val name: String,
    val prevId: String,
)

/**
 * Information about a started test session
 * @param sessionId the started session ID
 * @param testType the type of the test (AUTO, MANUAL)
 * @param testName the name of first running test
 * @param isRealtime a sign that it is necessary to collect test coverage in real time
 * @param isGlobal a sign that the session is global
 * @features Test running
 */
@Serializable
data class StartSessionPayload(
    val sessionId: String,
    val testType: String,
    val testName: String?,
    val isRealtime: Boolean,
    val isGlobal: Boolean,
)

@Serializable
data class AgentSessionPayload(
    val sessionId: String,
)

@Serializable
data class AgentSessionDataPayload(val sessionId: String, val data: String)

@Serializable
data class AgentSessionTestsPayload(val sessionId: String, val tests: List<String>)

/**
 * Class probes received by a specific test
 */
@Serializable
data class ExecClassData(
    val id: Long? = null,
    val className: String,
    @Serializable(with = BitSetSerializer::class)
    @Column("probes", PostgresType.BIT_VARYING)
    val probes: Probes,
    /**
     * Test name only use for global session/manual testing and in cases where we couldn't set testId(hash) in headers
     */
    val testName: String = "",
    /**
     * It's full test name hashed by CRC32 algorithm
     */
    val testId: String = "", // TODO must not have default value
)


