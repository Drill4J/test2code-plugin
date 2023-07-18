package com.epam.drill.plugins.test2code

import com.epam.drill.plugins.test2code.util.crc64
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class JacocoExtTest {

    @Test
    fun `crcr64 should return same values for equal strings`() {
        val str1 = javaClass.name.substringAfterLast(".")
        val str2 = javaClass.simpleName
        assertNotSame(str1, str2) //the strings should not be identical
        assertEquals(str1, str2)
        assertEquals(str1.crc64, str2.crc64)
    }

}