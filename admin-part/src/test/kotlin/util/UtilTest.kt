package com.epam.drill.plugins.test2code.util

import kotlin.test.*

class UtilTest {
    @Test
    fun `percentOf - cases with zero`() {
        assertEquals(0.0, 1 percentOf 0)
        assertEquals(0.0, 1 percentOf 0)
        assertEquals(0.0, 1L percentOf 0L)
        assertEquals(0.0, 1 percentOf 0.0f)
        assertEquals(0.0, 1.0 percentOf 0.0)
        assertEquals(0.0, 0 percentOf 10)
    }

    @Test
    fun `gcd - greatest common divisor`() {
        assertEquals(1, 1.gcd(1))
        assertEquals(3, 3.gcd(3))
    }
}
