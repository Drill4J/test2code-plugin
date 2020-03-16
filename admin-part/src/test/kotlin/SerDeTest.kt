package com.epam.drill.plugins.test2code

import kotlin.test.*

class SerDeTest {
    @Test
    fun `action StartNewSession`() {
        val action = StartNewSession(payload = StartPayload(testType = "MANUAL"))
        adminSerDe.apply {
            val str = stringify(actionSerializer, action)
            val parsedAction = parse(actionSerializer, str)
            assertEquals(action, parsedAction)
        }
    }
}
