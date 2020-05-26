package com.epam.drill.plugins.test2code

import com.epam.drill.plugins.test2code.api.*
import kotlin.test.*

class CoverageTest {
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
    fun `count - operation minus`() {
        assertEquals(0L to 0L, zeroCount - zeroCount)
        assertEquals(1L to 2L, Count(1, 2) - zeroCount)
        assertEquals(0L to 1L, Count(1, 3) - Count(1, 3))
        assertEquals(1L to 6L, Count(1, 2) - Count(1, 3))
        assertEquals(1L to 2L, Count(1, 2) - Count(0, 3))
        assertEquals(1L to 2L, Count(1, 2) - Count(2, 0))
    }

    @Test
    fun `arrowType - zero to zero`() {
        assertNull(zeroCount.arrowType(zeroCount))
    }

    @Test
    fun `arrowType zero to non-zero`() {
        assertNull(zeroCount.arrowType(Count(1, 2)))
    }

    @Test
    fun `arrowType simple cases`() {
        val count1 = Count(1, 3)
        val count2 = Count(1, 2)
        assertEquals(ArrowType.INCREASE, count1.arrowType(count2))
        assertEquals(ArrowType.DECREASE, count2.arrowType(count1))
        assertNull(count1.arrowType(count1))
    }

    @Test
    fun `arrowType same ratio`() {
        val count1 = Count(1, 2)
        val count2 = Count(2, 4)
        assertNull(count1.arrowType(count2))
        assertNull(count2.arrowType(count1))
    }
}
