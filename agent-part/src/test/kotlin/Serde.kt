package com.epam.drill.plugins.test2code

import kotlin.test.*

class SerdeTest {

    @Test
    fun `serde action StartSession`() {
        val sessionId = genUuid()
        val action = StartSession(payload = StartSessionPayload(sessionId = sessionId, startPayload = StartPayload()))
        val str = commonSerDe.stringify(commonSerDe.actionSerializer, action)
        val parsedAction = commonSerDe.parse(commonSerDe.actionSerializer, str)
        assertEquals(action, parsedAction)
    }
}
