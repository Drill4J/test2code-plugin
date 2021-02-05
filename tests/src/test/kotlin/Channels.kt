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
package com.epam.drill.plugins.test2code

import com.epam.drill.plugins.test2code.api.*
import kotlinx.coroutines.channels.*

class TestChannels {
    internal val methodsCoveredByTest: Channel<TestedMethodsSummary?> =
        Channel(Channel.UNLIMITED)
    suspend fun methodsCoveredByTest() = methodsCoveredByTest.receive()
}


class ScopeContext {

    internal val scope: Channel<ScopeSummary?> =
        Channel(Channel.UNLIMITED)
    suspend fun scope() = scope.receive()

    internal val associatedTests: Channel<List<AssociatedTests>?> =
        Channel(Channel.UNLIMITED)
    suspend fun associatedTests() = associatedTests.receive()

    internal val methods: Channel<MethodsSummaryDto?> =
        Channel(Channel.UNLIMITED)
    suspend fun methods() = methods.receive()


    internal val coverage: Channel<ScopeCoverage?> =
        Channel(Channel.UNLIMITED)
    suspend fun coverage() = coverage.receive()

    internal val coveragePackages: Channel<List<JavaPackageCoverage>?> =
        Channel(Channel.UNLIMITED)
    suspend fun coveragePackages() = coveragePackages.receive()

    internal val tests: Channel<List<TestCoverageDto>?> =
        Channel(Channel.UNLIMITED)
    suspend fun tests() = tests.receive()

}
