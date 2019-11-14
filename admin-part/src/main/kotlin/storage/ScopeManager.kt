package com.epam.drill.plugins.coverage.storage

import com.epam.drill.plugins.coverage.*
import com.epam.kodux.*
import kotlin.reflect.*

class ScopeManager(private val storage: StoreClient) {

    suspend fun allScopes(): Collection<FinishedScope> = storage.getAll()

    suspend fun scopesByBuildVersion(buildVersion: String): List<FinishedScope> =
        allScopes().filter { it.buildVersion == buildVersion }

    suspend fun scopeCountByBuildVersion(buildVersion: String, buildIsActive: Boolean): Int {
        val storedScopesCount = scopesByBuildVersion(buildVersion).count()
        return if (buildIsActive) storedScopesCount + 1 else storedScopesCount
    }

    suspend fun enabledScopes() = allScopes().filter { it.enabled }

    suspend fun enabledScopesSessionsByBuildVersion(buildVersion: String): Sequence<FinishedSession> =
        scopesByBuildVersion(buildVersion).filter { it.enabled }
            .flatMap { it.probes.values.flatten() }
            .asSequence()

    suspend fun clean() {
        allScopes().forEach { delete(it.id) }
    }

    suspend fun saveScope(scope: FinishedScope) {
        storage.store(scope)
    }

    suspend fun saveClassesData(classesData: ClassesData) {
        storage.store(classesData)
    }

    suspend fun getVersionMap(): Map<String, String> {
        val classesDatas = storage.getAll<ClassesData>()
        return classesDatas.mapNotNull { classesData ->
            if (!(classesData.prevBuildVersion.isBlank())) {
                classesData.prevBuildVersion to classesData.buildVersion
            } else null
        }.toMap()
    }

    suspend fun delete(scopeId: String): FinishedScope? =
        getScope(scopeId)?.apply {
            storage.deleteById<FinishedScope>(scopeId)
        }

    suspend fun getScope(scopeId: String): FinishedScope? = storage.findById(scopeId)

    suspend fun getLastCoverage(buildVersion: String): Double? =
        storage.findById<ClassesData>(buildVersion)?.prevBuildCoverage

    suspend fun classesData(buildVersion: String): ClassesData? =
        storage.findBy<ClassesData> { ClassesData::buildVersion eq buildVersion }.firstOrNull()

    //TODO: Investigate possibility to search by two and more fields
    suspend fun <R : Comparable<*>> scopesByField(
        property: KProperty1<FinishedScope, R>,
        value: R
    ): List<FinishedScope> =
        storage.findBy { property eq value }

    suspend fun summariesByBuildVersion(buildVersion: String): List<ScopeSummary> =
        scopesByBuildVersion(buildVersion).map { it.summary }
}
