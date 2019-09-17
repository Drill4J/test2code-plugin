package com.epam.drill.plugins.coverage.storage

import com.epam.drill.plugins.coverage.*
import kotlinx.dnq.*
import kotlinx.dnq.query.*

interface ScopeManager {
    val allScopes: Sequence<FinishedScope>

    fun allScopesByBuild(buildVersion: String): Sequence<FinishedScope>

    operator fun get(scopeId: String): FinishedScope?

    fun save(finishedScope: FinishedScope): FinishedScope

    fun clean()

    fun remove(scopeId: String): FinishedScope?
}

class XodusScopeManager(agentId: String) : ScopeManager {
    private val storage = XodusStorageManager.storage(agentId)

    override val allScopes: Sequence<FinishedScope>
        get() = storage.transactional {
            XdFinishedScope.all().toList().map { it.toFinishedScope() }.asSequence()
        }

    init {
        XdModel.registerNodes(
            XdFinishedScope,
            XdFinishedSession,
            XdTestTypeSummary,
            XdExecClassData,
            XdScopeSummary
        )
    }

    override fun allScopesByBuild(buildVersion: String): Sequence<FinishedScope> =
        storage.transactional {
            XdFinishedScope.filter { it.buildVersion eq buildVersion }
                .toList()
                .map { it.toFinishedScope() }
                .asSequence()
        }

    override operator fun get(scopeId: String) = storage.transactional { getXd(scopeId)?.toFinishedScope() }

    override fun save(finishedScope: FinishedScope): FinishedScope =
        storage.transactional {
            val scope = getXd(finishedScope.id)
            val xdScope = scope?.copy(finishedScope) ?: xdFinishedScopeFrom(finishedScope)
            xdScope.toFinishedScope()
        }

    override fun clean() {
        storage.transactional {
            XdFinishedScope.all().iterator().forEach { it.delete() }
        }
    }

    override fun remove(scopeId: String): FinishedScope? {
        val xdFinishedScope = getXd(scopeId)
        val finishedScope = storage.transactional { xdFinishedScope?.toFinishedScope() }
        storage.transactional { xdFinishedScope?.delete() }
        return finishedScope
    }

    private fun getXd(scopeId: String): XdFinishedScope? = storage.transactional {
        XdFinishedScope.filter { it.id eq scopeId }.firstOrNull()
    }
}

fun Sequence<FinishedScope>.summaries() = map { it.summary }