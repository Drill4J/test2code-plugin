package com.epam.drill.plugins.coverage.storage

import com.epam.drill.plugins.coverage.*
import com.epam.kodux.*
import kotlin.reflect.*

class ScopeManager(private val storage: StoreClient) {

    suspend fun allScopes(): Collection<FinishedScope> = storage.getAll()

    suspend fun scopesByBuildVersion(buildVersion: String): List<FinishedScope> =
        allScopes().filter { it.buildVersion == buildVersion }

    suspend fun clean() {
        allScopes().forEach { delete(it.id) }
    }

    suspend fun saveScope(scope: FinishedScope) {
        storage.store(scope)
    }

    suspend fun saveClassesData(classesData: ClassesData) {
        storage.store(classesData)
    }

    suspend fun delete(scopeId: String): FinishedScope? =
        getScope(scopeId)?.apply {
            storage.deleteById<FinishedScope>(scopeId)
        }

    suspend fun getScope(scopeId: String): FinishedScope? = storage.findById(scopeId)

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
