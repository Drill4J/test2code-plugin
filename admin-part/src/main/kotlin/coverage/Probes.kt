package com.epam.drill.plugins.test2code.coverage

import com.epam.drill.plugins.test2code.common.api.*
import kotlinx.collections.immutable.*

internal fun Sequence<ExecClassData>.merge(): PersistentMap<Long, ExecClassData> = run {
    persistentMapOf<Long, ExecClassData>().merge(this)
}

internal fun PersistentMap<Long, ExecClassData>.merge(
    probes: Sequence<ExecClassData>
): PersistentMap<Long, ExecClassData> = if (probes.any()) {
    mutate { map ->
        probes.forEach { data ->
            data.id().let { map[it] = map[it]?.merge(data) ?: data }
        }
    }
} else this

internal fun PersistentMap<Long, ExecClassData>.intersect(
    other: Sequence<ExecClassData>
): PersistentMap<Long, ExecClassData> = if (any() && other.any()) {
    other.merge().let { merged ->
        merged.mutate { map ->
            for ((id, datum) in merged) {
                this[id]?.probes?.run { intersect(datum.probes).takeIf { true in it } }?.let {
                    map[id] = datum.copy(probes = it)
                } ?: map.remove(id)
            }
        }
    }
} else persistentMapOf()

internal fun ExecClassData.merge(other: ExecClassData): ExecClassData = copy(
    probes = probes.merge(other.probes)
)

internal fun List<Boolean>.intersect(other: List<Boolean>): List<Boolean> = mapIndexed { i, b ->
    if (i < other.size) {
        b && other[i]
    } else false
}

internal fun List<Boolean>.merge(other: List<Boolean>): List<Boolean> = mapIndexed { i, b ->
    if (i < other.size) {
        b || other[i]
    } else b
}
