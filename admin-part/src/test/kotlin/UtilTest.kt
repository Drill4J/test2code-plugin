package com.epam.drill.plugins.test2code

import kotlin.test.*

class UtilTest {

    @Test
    fun `gcd - greatest common divisor`() {
        assertEquals(1, 1.gcd(1))
        assertEquals(3, 3.gcd(3))
    }
}
