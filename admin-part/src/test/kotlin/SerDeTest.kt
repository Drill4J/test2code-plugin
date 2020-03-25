package com.epam.drill.plugins.test2code

import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import kotlin.test.*

class SerDeTest {
    @Test
    fun `action StartNewSession`() {
        val action = StartNewSession(payload = StartPayload(testType = "MANUAL"))
        apiSerDe.apply {
            val str = stringify(actionSerializer, action)
            val parsedAction = parse(actionSerializer, str)
            assertEquals(action, parsedAction)
        }
    }
}
