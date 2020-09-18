package com.epam.drill.plugins.test2code

import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.coverage.*
import kotlin.test.*

class CoverageTest {
    @Test
    fun `count - operation minus`() {
        assertEquals(0L to 0L, zeroCount - zeroCount)
        assertEquals(1L to 2L, Count(1, 2) - zeroCount)
        assertEquals(1L to 2L, Count(1, 2) - Count(2, 0))
        assertEquals(0L to 3L, Count(1, 3) - Count(1, 3))
        assertEquals(1L to 6L, Count(1, 2) - Count(1, 3))
        assertEquals(2L to 6L, Count(4, 6) - Count(1, 3))
        assertEquals(3L to 6L, Count(1, 2) - Count(0, 3))
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
