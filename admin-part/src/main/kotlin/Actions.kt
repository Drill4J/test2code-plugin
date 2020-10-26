package com.epam.drill.plugins.test2code

import com.epam.drill.plugin.api.end.*
import com.epam.drill.plugins.test2code.common.api.*

internal object StatusCodes {
    const val OK = 200
    const val BAD_REQUEST = 400
    const val NOT_FOUND = 404
    const val CONFLICT = 409
}

internal fun AgentAction.toActionResult() = ActionResult(
    code = 200,
    agentAction = this,
    data = this
)
