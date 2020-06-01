package com.epam.drill.plugins.test2code.test.js

import com.epam.drill.common.*
import com.epam.drill.plugins.test2code.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*

val jsAgentInfo = AgentInfo(
    id = "jsag",
    name = "jsag",
    description = "",
    buildVersion = "0.1.0",
    agentType = AgentType.NODEJS,
    status = AgentStatus.ONLINE,
    agentVersion = ""
)

val ast = listOf(
    AstEntity(
        path = "foo/bar",
        name = "baz.js",
        methods = listOf(
            AstMethod(
                name = "foo",
                params = listOf("one", "two"),
                returnType = "number",
                count = 2,
                probes = listOf(1, 3)
            ),
            AstMethod(
                name = "bar",
                params = listOf(),
                returnType = "void",
                count = 2,
                probes = listOf(5, 8)
            )

        )
    )
)

val probes = listOf(
    ExecClassData(
        id = 0,
        className = "foo/bar/baz.js",
        testName = "default",
        probes = listOf(false, true, false, true)
    )
)
