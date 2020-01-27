package com.epam.drill.plugins.test2code.storage

import com.epam.drill.plugins.test2code.*
import com.epam.kodux.*

class ScopeManager(private val storage: StoreClient) {

    suspend fun allScopes(): Collection<FinishedScope> = storage.getAll()

    suspend fun scopesByBuildVersion(buildVersion: String): List<FinishedScope> =
        allScopes().filter { it.buildVersion == buildVersion }

    suspend fun scopeCountByBuildVersion(buildVersion: String): Int = scopesByBuildVersion(buildVersion).count()

    suspend fun enabledScopes() = allScopes().filter { it.enabled }

    suspend fun enabledScopesSessionsByBuildVersion(buildVersion: String): Sequence<FinishedSession> =
        scopesByBuildVersion(buildVersion).filter { it.enabled }
            .flatMap { it.probes.values.flatten() }
            .asSequence()

    suspend fun saveScope(scope: FinishedScope) {
        storage.store(scope)
    }

    suspend fun saveClassesData(classesData: ClassesData) {
        storage.store(classesData)
    }

    suspend fun getVersionMap(): Map<String, String> {
        val classesDatas = storage.getAll<ClassesData>()
        return classesDatas.mapNotNull { classesData ->
            if (classesData.prevBuildVersion.isNotBlank()) {
                classesData.prevBuildVersion to classesData.buildVersion
            } else null
        }.toMap()
    }

    suspend fun delete(scopeId: String): FinishedScope? =
        getScope(scopeId)?.apply {
            storage.deleteById<FinishedScope>(scopeId)
        }

    suspend fun getScope(scopeId: String): FinishedScope? = storage.findById(scopeId)

    suspend fun classesData(buildVersion: String): ClassesData? =
        storage.findBy<ClassesData> { ClassesData::buildVersion eq buildVersion }.firstOrNull()

    suspend fun summariesByBuildVersion(buildVersion: String): List<ScopeSummary> =
        scopesByBuildVersion(buildVersion).map { it.summary }
}
