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

import com.epam.drill.plugin.api.end.ActionResult
import com.epam.drill.plugins.test2code.api.routes.Routes
import com.epam.drill.plugins.test2code.coverage.BundleCounter
import com.epam.drill.plugins.test2code.util.trackTime
import com.epam.dsm.util.logPoolStats

internal fun Plugin.initBundleHandler(): Boolean = scope.initBundleHandler { tests ->
    val context = state.coverContext()
    val preparedBundle = tests.keys.associateWithTo(mutableMapOf()) {
        BundleCounter.empty
    }
    val calculated = tests.mapValuesTo(preparedBundle) {
        it.value.bundle(context, state.classBytes(buildVersion))
    }
    addBundleCache(calculated)
}

internal suspend fun Plugin.dropScope(scopeId: String): ActionResult {
    return state.scopeManager.deleteById(scopeId)?.let { scope ->
        cleanTopics(scope.id)
        handleChange(scope)
        ActionResult(
            StatusCodes.OK,
            "Scope with id $scopeId was removed"
        )
    } ?: ActionResult(
        StatusCodes.NOT_FOUND,
        "Failed to drop scope with id $scopeId: scope not found"
    )
}

private suspend fun Plugin.cleanTopics(scopeId: String) = getRouteScope(scopeId).let { scope ->
    val coverageRoute = Routes.Build.Scope.Coverage(scope)
    send(buildVersion, coverageRoute, "")
    state.classDataOrNull()?.let { classData ->
        val pkgsRoute = Routes.Build.Scope.Coverage.Packages(coverageRoute)
        classData.packageTree.packages.forEach { p ->
            send(buildVersion, Routes.Build.Scope.Coverage.Packages.Package(p.name, pkgsRoute), "")
            send(buildVersion, Routes.Build.Scope.AssociatedTests(p.id, scope), "")
            p.classes.forEach { c ->
                send(buildVersion, Routes.Build.Scope.AssociatedTests(c.id, scope), "")
                c.methods.forEach { m ->
                    send(buildVersion, Routes.Build.Scope.AssociatedTests(m.id, scope), "")
                }
            }
        }
    }
    send(buildVersion, Routes.Build.Scope.Tests(scope), "")
}

private suspend fun Plugin.handleChange(scope: Scope) {
    val scopeBuildVersion = scope.agentKey.buildVersion
    if (scopeBuildVersion == buildVersion) {
        calculateAndSendBuildCoverage()
        scope.probesChanged()
    }
    sendScopes(scopeBuildVersion)
}

internal fun Plugin.initScope(): Boolean = scope.initRealtimeHandler { sessionChanged, sessions ->
    if (sessionChanged) {
        sendActiveSessions()
    }
    sessions?.let {
        val context = state.coverContext()
        val bundleCounters = trackTime("bundleCounters") {
            sessions.calcBundleCounters(context, state.classBytes(buildVersion), bundleByTests)
        }.also { logPoolStats() }
        val coverageInfoSet = trackTime("coverageInfoSet") {
            bundleCounters.calculateCoverageData(context, this)
        }.also { logPoolStats() }
        coverageInfoSet.sendScopeCoverage(id, buildVersion)
        if (sessionChanged) {
            bundleCounters.assocTestsJob(this)
            bundleCounters.coveredMethodsJob()
        }
    }
}