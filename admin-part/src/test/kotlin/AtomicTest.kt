package com.epam.drill.plugins.test2code

import kotlin.test.Test
import kotlin.test.assertEquals

class AtomicTest {
    @Test
    fun `basic get or put case`() {
        val cache = AtomicCache<String, Int>()
        val value = cache("1") { 1 }
        assertEquals(1, value)
        assertEquals(1, cache.count())
    }
}
