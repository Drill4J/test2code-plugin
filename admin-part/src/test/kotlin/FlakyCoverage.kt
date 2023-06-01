package com.epam.drill.plugins.test2code

import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.ExecClassData
import com.epam.drill.plugins.test2code.coverage.BundleCounter
import com.epam.drill.plugins.test2code.coverage.percentage
import com.epam.drill.plugins.test2code.jvm.bundle
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import java.io.*

class FlakyCoverage {

    private val classBytes: Map<String, ByteArray> =
        this::class.java.classLoader.getResourceAsStream("state/classBytes.ser")!!.use { fileInputStream ->
            ObjectInputStream(fileInputStream).use { it.readObject() as Map<String, ByteArray> }
        }

    val execData: Sequence<ExecClassData> =
        this::class.java.classLoader.getResourceAsStream("state/execData.ser")!!.use { inputStream ->
            InputStreamReader(inputStream).use {
                Json.decodeFromString<List<ExecClassData>>(it.readText()).asSequence()
            }
        }

    val packageTree: PackageTree =
        this::class.java.classLoader.getResourceAsStream("state/packageTree.ser")!!.use { inputStream ->
            InputStreamReader(inputStream).use {
                Json.decodeFromString<PackageTree>(it.readText())
            }
        }

    private val probeIds: Map<String, Long> =
        this::class.java.classLoader.getResourceAsStream("state/probeIds.ser")!!.use { fileInputStream ->
            ObjectInputStream(fileInputStream).use { it.readObject() as Map<String, Long> }
        }

    @Test
    fun `should import coverage`(): Unit = runBlocking {
        val result = HashSet<Double>()
        for (i in 1..10000) {
            val bundle: BundleCounter = execData.bundle(probeIds, classBytes)
            val coverageCount = bundle.count.copy(total = packageTree.totalCount)
            val totalCoveragePercent = coverageCount.percentage()
            result.add(totalCoveragePercent)
        }
        result.forEach {
            println(it)
        }
    }

}