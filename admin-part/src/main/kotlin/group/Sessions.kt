package com.epam.drill.plugins.test2code.group

import com.epam.drill.plugins.test2code.api.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*

val sessionAggregator = SessionAggregator()

class SessionAggregator : (String, String, List<ActiveSessionDto>) -> List<ActiveSessionDto>? {
    private val _groups = atomic(
        persistentHashMapOf<String, Map<String, List<ActiveSessionDto>>>()
    )

    override operator fun invoke(
        serviceGroup: String,
        agentId: String,
        activeSessions: List<ActiveSessionDto>
    ): List<ActiveSessionDto>? {
        val sessions = _groups.updateAndGet {
            val sessionGroups = it[serviceGroup] ?: persistentMapOf()
            it.put(serviceGroup, sessionGroups + (agentId to activeSessions))
        }
        return sessions[serviceGroup]?.values?.flatten()
    }
}
