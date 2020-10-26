package com.epam.drill.plugins.test2code

import com.epam.drill.plugin.api.end.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.api.routes.*
import com.epam.drill.plugins.test2code.coverage.*
import com.epam.drill.plugins.test2code.group.*
import com.epam.drill.plugins.test2code.util.*
import com.epam.kodux.*
import kotlinx.serialization.*
import kotlin.reflect.*

private object QualityGateData {
    @Serializable
    data class IdByAgent(
        val agentId: String,
        val conditionType: String
    )

    @Serializable
    data class AgentSetting(
        @Id val id: IdByAgent,
        val setting: ConditionSetting
    )

    private val pairs: List<Pair<KProperty1<StatsDto, Number>, QualityGateCondition>> = listOf(
        StatsDto::coverage.run { this to toCondition(operator = ConditionOp.GTE, value = 0.0) },
        StatsDto::risks.run { this to toCondition(operator = ConditionOp.LTE, value = 1.0) },
        StatsDto::tests.run { this to toCondition(operator = ConditionOp.LTE, value = 1.0) }
    )

    val defaults: Map<String, QualityGateCondition> = pairs.associate {
        it.first.name to it.second
    }

    val properties: Map<String, KProperty1<StatsDto, Number>> = pairs.associate {
        it.first.name to it.first
    }
}

internal suspend fun Plugin.initGateSettings() {
    val settings = state.qualityGateSettings
    QualityGateData.defaults.forEach { (key, value) ->
        settings[key] = ConditionSetting(false, value)
    }
    storeClient.getAll<QualityGateData.AgentSetting>().forEach {
        settings[it.id.conditionType] = it.setting
    }
    sendSettings()
}

internal suspend fun Plugin.updateGateConditions(
    conditionSettings: List<ConditionSetting>
): ActionResult = run {
    val settings = state.qualityGateSettings
    val unknownMeasures = conditionSettings.filter { it.condition.measure !in settings.map }
    if (unknownMeasures.none()) {
        conditionSettings.forEach { settings[it.condition.measure] = it }
        sendSettings()
        state.toStatsDto(buildVersion)?.let { stats ->
            val qualityGate = checkQualityGate(stats)
            send(buildVersion, Routes.Data().let(Routes.Data::QualityGate), qualityGate)
        }
        storeClient.executeInAsyncTransaction {
            conditionSettings.forEach { setting ->
                val measure = setting.condition.measure
                val id = QualityGateData.IdByAgent(agentInfo.id, measure)
                store(QualityGateData.AgentSetting(id, setting))
            }
        }
        ActionResult(StatusCodes.OK, "")
    } else ActionResult(StatusCodes.BAD_REQUEST, "Unknown quality gate measures: '$unknownMeasures'")
}

internal fun Plugin.checkQualityGate(stats: StatsDto): QualityGate = run {
    val conditions = state.qualityGateSettings.values
        .filter { it.enabled }
        .map { it.condition }
    val checkResults = conditions.associate { it.measure to stats.check(it) }
    val status = when (checkResults.values.toSet()) {
        emptySet<Boolean>(), setOf(true) -> GateStatus.PASSED
        else -> GateStatus.FAILED
    }
    QualityGate(
        status = status,
        results = checkResults
    )
}

private fun AgentState.toStatsDto(
    buildVersion: String
): StatsDto? = builds[buildVersion]?.run {
    coverage.toSummary(agentInfo.name, tests).toStatsDto()
}

internal fun AgentSummary.toStatsDto() = StatsDto(
    coverage = coverage.percentage(),
    risks = risks,
    tests = testsToRun.totalCount()
)

private suspend fun Plugin.sendSettings() {
    val dataRoute = Routes.Data()
    val settings = state.qualityGateSettings.values.toList()
    send(buildVersion, dataRoute.let(Routes.Data::QualityGateSettings), settings)
}

private fun <T : Number> KProperty1<StatsDto, T>.toCondition(
    operator: ConditionOp,
    value: Number
): QualityGateCondition = QualityGateCondition(
    measure = name,
    operator = operator,
    value = value.toDouble()
)


private fun StatsDto.check(condition: QualityGateCondition): Boolean = run {
    val value = QualityGateData.properties.getValue(condition.measure).get(this).toDouble()
    when (condition.operator) {
        ConditionOp.LT -> value < condition.value
        ConditionOp.LTE -> value <= condition.value
        ConditionOp.GT -> value > condition.value
        ConditionOp.GTE -> value >= condition.value
    }
}
