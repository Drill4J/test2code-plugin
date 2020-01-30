package com.epam.drill.plugins.test2code

import kotlin.test.*

class DataExtTest {

    @Test
    fun `byteArray-encode works correctly`() {
        val testArray = "some string".toByteArray()
        val encoded = testArray.encode()
        assertEquals("c29tZSBzdHJpbmc=", encoded)
    }

    @Test
    fun `encodedString-decode works correctly`() {
        val encodedString: EncodedString = "c29tZSBzdHJpbmc="
        val actual = encodedString.decode()
        val expected = "some string".toByteArray()
        assertTrue(expected contentEquals actual, "Expected <$expected>, actual <$actual>")
    }


    @Test
    fun `encodes-decodes correctly`() {
        val expected = "some string".toByteArray()
        val encodedArray = expected.encode()
        val actual = encodedArray.decode()
        assertTrue(expected contentEquals actual, "Expected <$expected>, actual <$actual>")
    }
}

