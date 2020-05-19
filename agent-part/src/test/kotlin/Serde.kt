package com.epam.drill.plugins.test2code

import com.epam.drill.plugins.test2code.common.api.*
import java.util.*
import kotlin.test.*

class SerdeTest {

    @Test
    fun `serde action StartSession`() {
        val sessionId = UUID.randomUUID().toString()
        val action = StartSession(payload = StartSessionPayload(sessionId = sessionId, startPayload = StartPayload()))
        val str = commonSerDe.stringify(commonSerDe.actionSerializer, action)
        val parsedAction = commonSerDe.parse(commonSerDe.actionSerializer, str)
        assertEquals(action, parsedAction)
    }
}
