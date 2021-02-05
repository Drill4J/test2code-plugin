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
package com.epam.drill.plugins.test2code.storage

import com.epam.drill.plugins.test2code.*
import com.epam.drill.plugins.test2code.util.*
import com.epam.kodux.*
import kotlinx.serialization.*
import kotlinx.serialization.protobuf.*

fun Sequence<FinishedScope>.enabled() = filter { it.enabled }

class ScopeManager(private val storage: StoreClient) {

    suspend fun byVersion(
        buildVersion: String,
        withData: Boolean = false
    ): Sequence<FinishedScope> = storage.executeInAsyncTransaction {
        findBy<FinishedScope> {
            FinishedScope::buildVersion eq buildVersion
        }.run {
            takeIf { withData }?.run {
                findBy<ScopeDataBytes> { ScopeDataBytes::buildVersion eq buildVersion }.takeIf { it.any() }
            }?.associateBy { it.id }?.let { dataMap ->
                map { it.withProbes(dataMap[it.id]) }
            } ?: this
        }
    }.asSequence()

    suspend fun store(scope: FinishedScope) {
        storage.executeInAsyncTransaction {
            store(scope.copy(data = ScopeData.empty))
            scope.takeIf { it.any() }?.let {
                val dataBytes: ScopeDataBytes = it.toDataBytes()
                store(dataBytes)
            }
        }
    }

    suspend fun deleteById(scopeId: String): FinishedScope? = storage.executeInAsyncTransaction {
        findById<FinishedScope>(scopeId)?.also {
            deleteById<FinishedScope>(scopeId)
            deleteById<ScopeDataBytes>(scopeId)
        }
    }

    suspend fun deleteByVersion(buildVersion: String) {
        storage.executeInAsyncTransaction {
            deleteBy<FinishedScope> { FinishedScope::buildVersion eq buildVersion }
            deleteBy<ScopeDataBytes> { ScopeDataBytes::buildVersion eq buildVersion }
        }
    }

    suspend fun byId(
        scopeId: String,
        withProbes: Boolean = false
    ): FinishedScope? = storage.run {
        takeIf { withProbes }?.executeInAsyncTransaction {
            findById<FinishedScope>(scopeId)?.run {
                withProbes(findById(scopeId))
            }
        } ?: findById(scopeId)
    }

    internal suspend fun counter(buildVersion: String): ActiveScopeInfo? = storage.findById(buildVersion)

    internal suspend fun storeCounter(activeScopeInfo: ActiveScopeInfo) = storage.store(activeScopeInfo)
}

@Serializable
internal class ScopeDataBytes(
    @Id val id: String,
    val buildVersion: String,
    val bytes: ByteArray
)

private fun FinishedScope.toDataBytes() = ScopeDataBytes(
    id = id,
    buildVersion = buildVersion,
    bytes = ProtoBuf.dump(ScopeData.serializer(), data).let(Zstd::compress)
)

private fun FinishedScope.withProbes(
    data: ScopeDataBytes?
): FinishedScope = data?.let {
    copy(data = ProtoBuf.load(ScopeData.serializer(), Zstd.decompress(it.bytes)))
} ?: this
