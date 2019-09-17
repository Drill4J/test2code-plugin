package com.epam.drill.plugins.coverage.storage

import com.epam.drill.plugins.coverage.*
import jetbrains.exodus.entitystore.*
import kotlinx.dnq.*
import kotlinx.dnq.link.*
import kotlinx.dnq.query.*

class XdFinishedScope(entity: Entity) : XdEntity(entity) {
    companion object : XdNaturalEntityType<XdFinishedScope>()

    var id by xdRequiredStringProp()
    var name by xdRequiredStringProp()
    var buildVersion by xdRequiredStringProp()
    var enabled by xdBooleanProp()
    var summary by xdLink0_1(XdScopeSummary, onDelete = OnDeletePolicy.CASCADE, onTargetDelete = OnDeletePolicy.CLEAR)
    val probes by xdLink0_N(XdFinishedSession, onDelete = OnDeletePolicy.CASCADE, onTargetDelete = OnDeletePolicy.CLEAR)

    fun toFinishedScope() = FinishedScope(
        id = id,
        name = name,
        buildVersion = buildVersion,
        enabled = enabled,
        summary = summary!!.toScopeSummary(),
        probes = probes.toList().map { it.toFinishedSession() }.groupBy { it.testType }
    )

    fun copy(finishedScope: FinishedScope): XdFinishedScope {
        id = finishedScope.id
        name = finishedScope.name
        buildVersion = finishedScope.buildVersion
        enabled = finishedScope.enabled
        summary = xdScopeSummaryFrom(finishedScope.summary)
        finishedScope.probes.values.flatten().forEach {
            probes.add(xdFinishedSessionFrom(it))
        }
        return this
    }
}

fun xdFinishedScopeFrom(finishedScope: FinishedScope) =
    XdFinishedScope.new {
        copy(finishedScope)
    }

class XdScopeSummary(entity: Entity) : XdEntity(entity) {
    companion object : XdNaturalEntityType<XdScopeSummary>()

    var id by xdRequiredStringProp()
    var name by xdRequiredStringProp()
    var started by xdRequiredLongProp()
    var finished by xdNullableLongProp()
    var coverage by xdRequiredDoubleProp()
    var enabled by xdBooleanProp()
    var active by xdBooleanProp()
    val coveragesByType by xdLink0_N(
        XdTestTypeSummary,
        onDelete = OnDeletePolicy.CASCADE,
        onTargetDelete = OnDeletePolicy.CLEAR
    )

    fun toScopeSummary() = ScopeSummary(
        id = id,
        name = name,
        started = started,
        finished = finished,
        coverage = coverage,
        enabled = enabled,
        active = active,
        coveragesByType = coveragesByType.toList().map { it.testType to it.toTestTypeSummary() }.toMap()
    )
}

fun xdScopeSummaryFrom(scopeSummary: ScopeSummary): XdScopeSummary =
    XdScopeSummary.new {
        id = scopeSummary.id
        name = scopeSummary.name
        started = scopeSummary.started
        finished = scopeSummary.finished
        coverage = scopeSummary.coverage
        enabled = scopeSummary.enabled
        active = scopeSummary.active
        scopeSummary.coveragesByType.values.forEach {
            coveragesByType.add(xdTestTypeSummaryFrom(it))
        }
    }

class XdTestTypeSummary(entity: Entity) : XdEntity(entity) {
    companion object : XdNaturalEntityType<XdTestTypeSummary>()

    var testType by xdRequiredStringProp()
    var coverage by xdRequiredDoubleProp()
    var testCount by xdRequiredIntProp()

    fun toTestTypeSummary() = TestTypeSummary(
        testType = testType,
        coverage = coverage,
        testCount = testCount
    )
}

fun xdTestTypeSummaryFrom(testTypeSummary: TestTypeSummary): XdTestTypeSummary =
    XdTestTypeSummary.new {
        testType = testTypeSummary.testType
        coverage = testTypeSummary.coverage
        testCount = testTypeSummary.testCount
    }


class XdFinishedSession(entity: Entity) : XdEntity(entity) {
    companion object : XdNaturalEntityType<XdFinishedSession>()

    var id by xdRequiredStringProp()
    var testType by xdRequiredStringProp()
    val probes by xdLink0_N(XdExecClassData, onDelete = OnDeletePolicy.CASCADE, onTargetDelete = OnDeletePolicy.CLEAR)

    fun toFinishedSession() = FinishedSession(
        id = id,
        testType = testType,
        probes = probes.toList().map { it.toExecClassData() }.groupBy { TypedTest(it.testName, testType) }
    )
}

fun xdFinishedSessionFrom(finishedSession: FinishedSession): XdFinishedSession =
    XdFinishedSession.new {
        id = finishedSession.id
        testType = finishedSession.testType
        finishedSession.probes.values.flatten().forEach {
            probes.add(xdExecClassDataFrom(it))
        }
    }

class XdExecClassData(entity: Entity) : XdEntity(entity) {
    companion object : XdNaturalEntityType<XdExecClassData>()

    var id by xdRequiredLongProp()
    var className by xdRequiredStringProp()
    var testName by xdRequiredStringProp()
    var probes by xdRequiredStringProp()

    fun toExecClassData() = ExecClassData(
        id = id,
        className = className,
        testName = testName,
        probes = stringToProbeList(probes)
    )
}

fun xdExecClassDataFrom(execClassData: ExecClassData): XdExecClassData =
    XdExecClassData.new {
        id = execClassData.id
        className = execClassData.className
        testName = execClassData.testName
        probes = probeListToString(execClassData.probes)
    }

fun stringToProbeList(string: String) = mutableListOf<Boolean>().apply { string.forEach { this.add(it == '1') } }
fun probeListToString(list: List<Boolean>) =
    StringBuilder().apply { list.forEach { append(if (it) '1' else '0') } }.toString()