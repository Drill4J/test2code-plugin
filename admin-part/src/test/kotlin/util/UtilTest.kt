package com.epam.drill.plugins.test2code.util

import java.net.*
import kotlin.test.*

class UtilTest {
    @Test
    fun `urlDecode - empty input`() {
        val input = ""
        val encoded = URLEncoder.encode(input, Charsets.UTF_8.name())
        val decoded = encoded.urlDecode()
        assertEquals(input, decoded)
    }

    @Test
    fun `urlDecode - url compatible input`() {
        val input = "input"
        val encoded = URLEncoder.encode(input, Charsets.UTF_8.name())
        val decoded = encoded.urlDecode()
        assertSame(input, decoded)
    }

    @Test
    fun `urlDecode - incorrect input`() {
        val input = "%%%%%%something%"
        val decoded = input.urlDecode()
        assertSame(input, decoded)
    }

    @Test
    fun `urlDecode - cyrillic characters`() {
        val input = "пример названия теста 1"
        val encoded = URLEncoder.encode(input, Charsets.UTF_8.name())
        val decoded = encoded.urlDecode()
        assertEquals(input, decoded)
    }

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
