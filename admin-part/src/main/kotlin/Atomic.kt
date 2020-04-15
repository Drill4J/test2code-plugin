package com.epam.drill.plugins.test2code

import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*

class AtomicCache<K, V> : (K, (V?) -> V?) -> V? {

    private val _map = atomic(persistentHashMapOf<K, V>())

    val map get() = _map.value

    override fun invoke(key: K, mutator: (V?) -> V?) = _map.updateAndGet {
        val oldVal = it[key]
        when (val newVal = mutator(oldVal)) {
            oldVal -> it
            null -> it.remove(key)
            else -> it.put(key, newVal)
        }
    }[key]


    operator fun get(key: K): V? = map[key]

    operator fun set(key: K, value: V): V? = this(key) { value }

    fun remove(key: K) = _map.getAndUpdate { it.remove(key) }[key]

    fun clear() = _map.getAndUpdate { it.clear() }

    override fun toString(): String = map.toString()
}

val <K, V> AtomicCache<K, V>.keys get() = map.keys

val <K, V> AtomicCache<K, V>.values get() = map.values

fun <K, V> AtomicCache<K, V>.getOrPut(key: K, producer: () -> V): V = this(key) { it ?: producer() }!!

fun <K, V> AtomicCache<K, V>.count() = map.count()

fun <K, V> AtomicCache<K, V>.isEmpty() = map.isEmpty()

fun <K, V> AtomicCache<K, V>.isNotEmpty() = !isEmpty()
