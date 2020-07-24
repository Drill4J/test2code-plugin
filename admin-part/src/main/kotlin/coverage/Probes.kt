package com.epam.drill.plugins.test2code.coverage

import com.epam.drill.plugins.test2code.common.api.*
import kotlinx.collections.immutable.*

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
    data: Sequence<ExecClassData>
): PersistentMap<Long, ExecClassData> = if (data.any()) {
    mutate { map ->
        data.forEach { datum ->
            val id = datum.id()
            map[id]?.run {
                val intersection = probes.intersect(datum.probes)
                if (intersection.any { it }) {
                    map.put(id, datum.copy(probes = intersection))
                } else map.remove(id)
            }
        }
    }
} else this

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
