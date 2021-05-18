package com.epam.drill.plugins.test2code.common.api

import java.util.*

actual typealias Probes = BitSet

class ProbesStub(size: Int = 0) : BitSet(size) {
    override fun set(bitIndex: Int) {}

    override fun set(bitIndex: Int, value: Boolean) {}
}
