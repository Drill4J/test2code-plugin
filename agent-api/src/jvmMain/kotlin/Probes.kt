package com.epam.drill.plugins.test2code.common.api

import java.util.*

actual typealias Probes = BitSet

open class AgentProbes(size: Int = 0) {
    val values = BooleanArray(size)

    open fun set(index: Int) {
        if (!values[index])
            values[index] = true
    }

    fun get(index: Int): Boolean {
        return values[index]
    }

    fun reset() {
        (values.indices).forEach {
            values[it] = false
        }
    }

}

class StubAgentProbes(size: Int = 0) : AgentProbes(size) {
    override fun set(index: Int) {
    }

}
