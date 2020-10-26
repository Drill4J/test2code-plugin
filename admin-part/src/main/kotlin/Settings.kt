package com.epam.drill.plugins.test2code

import com.epam.drill.plugin.api.end.*
import com.epam.drill.plugins.test2code.api.*

suspend fun Plugin.updateSettings(
    settings: List<Setting>
): ActionResult = updateGateConditions(settings.filterIsInstance<ConditionSetting>())
