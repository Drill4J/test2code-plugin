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

    fun delete(scopeId: String): FinishedScope? =
        get(scopeId)?.apply {
            storage.deleteBy<FinishedScope> { FinishedScope::id eq scopeId }
        }

    operator fun get(scopeId: String): FinishedScope? =
        storage.findBy<FinishedScope> { FinishedScope::id eq scopeId }.firstOrNull()

    fun classesData(buildVersion: String): ClassesData? =
        storage.findBy<ClassesData> { ClassesData::buildVersion eq buildVersion }.firstOrNull()

    //TODO: Investigate possibility to search by two and more fields
    fun <Q, R : Comparable<*>> scopesByField(property: KProperty1<Q, R>, value: R): List<FinishedScope> =
        storage.findBy { property eq value }

    suspend fun summariesByBuildVersion(buildVersion: String): List<ScopeSummary> =
        scopesByBuildVersion(buildVersion).map { it.summary }
}
