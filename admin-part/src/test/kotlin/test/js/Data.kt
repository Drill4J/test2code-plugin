package com.epam.drill.plugins.test2code.test.js

import com.epam.drill.common.*
import com.epam.drill.plugins.test2code.common.api.*

val jsAgentInfo = AgentInfo(
    id = "jsag",
    name = "jsag",
    description = "",
    buildVersion = "0.1.0",
    agentType = "NODEJS",
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
                count = 1,
                probes = listOf(6)
            ),
            AstMethod(
                name = "baz",
                params = listOf(),
                returnType = "void",
                count = 2,
                probes = listOf(7, 8)
            )

        )
    )
)

val probes = listOf(
    ExecClassData(
        className = "foo/bar/baz.js",
        testName = "default",
        probes = listOf(true, true, false, true, false)
    )
)

object IncorrectProbes {
    val overCount = listOf(
        ExecClassData(
            className = "foo/bar/baz.js",
            testName = "default",
            probes = listOf(true, true, false, true, false, /*extra*/ false)
        )
    )

    val underCount = listOf(
        ExecClassData(
            className = "foo/bar/baz.js",
            testName = "default",
            probes = listOf(true, true, false, true)
        )
    )

    val notExisting = listOf(
        ExecClassData(
            className = "foo/bar/not-existing",
            testName = "default",
            probes = listOf(false, false)
        )
    )
}

