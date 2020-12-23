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
