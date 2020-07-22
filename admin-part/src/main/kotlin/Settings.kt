package com.epam.drill.plugins.test2code

import com.epam.drill.plugin.api.message.*
import com.epam.drill.plugins.test2code.api.*

suspend fun Plugin.updateSettings(
    settings: List<Setting>
): StatusMessage = updateGateConditions(settings.filterIsInstance<ConditionSetting>())
