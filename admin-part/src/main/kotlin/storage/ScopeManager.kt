package com.epam.drill.plugins.test2code.storage

import com.epam.drill.plugins.test2code.*
import com.epam.kodux.*

class ScopeManager(private val storage: StoreClient) {

    suspend fun byVersion(
        buildVersion: String
    ): Sequence<FinishedScope> = storage.findBy<FinishedScope> {
        FinishedScope::buildVersion eq buildVersion
    }.asSequence()

    suspend fun byVersionEnabled(
        buildVersion: String
    ): Sequence<FinishedScope> = byVersion(buildVersion).filter { it.enabled }

    suspend fun store(scope: FinishedScope) {
        storage.store(scope)
    }

    suspend fun deleteById(scopeId: String): FinishedScope? = byId(scopeId)?.apply {
        storage.deleteById<FinishedScope>(scopeId)
    }

    suspend fun byId(scopeId: String): FinishedScope? = storage.findById(scopeId)

    suspend fun counter(agentBuildId: AgentBuildId): ScopeCounter? = storage.findById(agentBuildId)

    suspend fun storeCounter(scopeCounter: ScopeCounter) = storage.store(scopeCounter)
}
