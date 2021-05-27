package jvm

import com.epam.drill.plugins.test2code.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.drill.plugins.test2code.coverage.*
import com.epam.drill.plugins.test2code.jvm.*
import instrumentation.epam.drill.test2code.jacoco.JvmClassAnalyzer.Companion.analyzeByMethods
import kotlin.test.*

class JavaBundleProcTest {
    //todo refactoring tests
    @Test
    fun `calculate bundle 1 method`() {
        assertTrue(true)
        val jvmClassName = "org/springframework/samples/petclinic/owner/PetValidator"
        val classCoverage = ClassCoverage(jvmClassName)
        classCoverage.method("<init>", "()V").also {
            it.probRangeToInstruction[0] = 2
            it.totalInstruction = 2
        }
        classCoverage.analyzeByMethods()
        val probes = probesOf(true)
//        classCoverage.methods[0]?.toCoverageUnit("")
        val toCoverageUnit: ClassCounter = classCoverage.toCoverageUnit(probes)//todo remove or add asserts
        println("toCoverageUnit = $toCoverageUnit")

        val analyzedClasses = listOf(classCoverage)
        val context = CoverContext(
            agentType = "JAVA",//todo
            packageTree = PackageTree(),
            methods = emptyList(),
            build = CachedBuild("build1"),
            analyzedClasses = analyzedClasses,
        )
        val bundleProc = JavaBundleProc(context)
        val execClassData = ExecClassData(
            id = 1L,
            className = jvmClassName,
            probes = probes
        )
        val bundle = bundleProc.bundle(sequenceOf(execClassData))

        println(bundle)
        assertEquals(Count(1, 1).toDto(), bundle.classCount.toDto())
        assertEquals(Count(1, 1).toDto(), bundle.methodCount.toDto())
        assertEquals(Count(2, 2).toDto(), bundle.count.toDto())
    }

    @Test
    fun `calculate bundle 2 methods`() {
        assertTrue(true)
        val jvmClassName = "org/springframework/samples/petclinic/owner/PetValidator"
        val classCoverage = ClassCoverage(jvmClassName)
        classCoverage.method("smth", "()V").also {
            it.probRangeToInstruction[0] = 3
            it.totalInstruction = 3
        }
        classCoverage.method("<init>", "()V").also {
            it.probRangeToInstruction[1] = 2
            it.totalInstruction = 2
        }

        classCoverage.analyzeByMethods()
        val analyzedClasses = listOf(classCoverage)
        val context = CoverContext(
            agentType = "JAVA",//todo
            packageTree = PackageTree(),
            methods = emptyList(),
            build = CachedBuild("build1"),
            analyzedClasses = analyzedClasses,
        )
        val bundleProc = JavaBundleProc(context)
        val execClassData = ExecClassData(
            id = 1L,
            className = jvmClassName,
            probes = probesOf(true, false)
        )
        val bundle = bundleProc.bundle(sequenceOf(execClassData))

        assertEquals(Count(1, 1).toDto(), bundle.classCount.toDto())
        assertEquals(Count(1, 2).toDto(), bundle.methodCount.toDto())
        assertEquals(Count(3, 5).toDto(), bundle.count.toDto())
    }

    @Test
    fun `calculate bundle 2 classes`() {
        assertTrue(true)
        val jvmClassName = "org/springframework/samples/petclinic/owner/PetValidator"
        val jvmClassName2 = "org/springframework/samples/petclinic/owner/Pet"
        val classCoverage = classCoverage(jvmClassName)
        val classCoverage2 = classCoverage(jvmClassName2)

        val analyzedClasses = listOf(classCoverage, classCoverage2)
        val context = CoverContext(
            agentType = "JAVA",//todo
            packageTree = PackageTree(),
            methods = emptyList(),
            build = CachedBuild("build1"),
            analyzedClasses = analyzedClasses,
        )
        val bundleProc = JavaBundleProc(context)
        val execClassData = ExecClassData(
            id = 1L,
            className = jvmClassName,
            probes = probesOf(true, false)
        )
        val execClassData2 = ExecClassData(
            id = 1L,
            className = jvmClassName,
            probes = probesOf(false, true)
        )
        val bundle = bundleProc.bundle(sequenceOf(execClassData, execClassData2))

        assertEquals(Count(1, 2).toDto(), bundle.classCount.toDto())
        assertEquals(Count(2, 4).toDto(), bundle.methodCount.toDto())
        assertEquals(Count(5, 10).toDto(), bundle.count.toDto())
    }

    private fun classCoverage(jvmClassName: String): ClassCoverage {
        val classCoverage = ClassCoverage(jvmClassName)
        classCoverage.method("smth", "()V").also {
            it.probRangeToInstruction[0] = 3
            it.totalInstruction = 3
        }
        classCoverage.method("<init>", "()V").also {
            it.probRangeToInstruction[1] = 2
            it.totalInstruction = 2
        }
        classCoverage.analyzeByMethods()
        return classCoverage
    }
}
