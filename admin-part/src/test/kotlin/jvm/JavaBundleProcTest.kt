package jvm

import SmthForth
import MethodWithFewIf
import SmthSix
import com.*
import com.epam.drill.plugins.test2code.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.drill.plugins.test2code.coverage.*
import com.epam.drill.plugins.test2code.jvm.*
import instrumentation.epam.drill.test2code.jacoco.*
import instrumentation.epam.drill.test2code.jacoco.JvmClassAnalyzer.Companion.analyzeByMethods
import org.jacoco.core.analysis.*
import kotlin.reflect.*
import kotlin.test.*

class JavaBundleProcTest {
    //todo refactoring tests

    private fun<T: Any> bytes(kClass: KClass<T>) = kClass.java.readBytes()

    //todo signature for method= "()V" ?
    @Test
    fun `analyzeClass constructor and method`() {
        val analyzeClass = JvmClassAnalyzer.analyzeClass(bytes(ConstructorAndMethod::class))
        if (analyzeClass != null) {
            assertEquals(2, analyzeClass.methods.size)
            assertEquals(22, analyzeClass.totalInstruction)
            val initMethod = analyzeClass.methods["<init>"]
            assertEquals(3, initMethod?.totalInstruction)
            assertEquals(1, initMethod?.probRangeToInstruction?.size)
            val methodCoverage = analyzeClass.methods["ifElseMethod"]
            assertEquals(19, methodCoverage?.totalInstruction)
            assertEquals(4, methodCoverage?.probRangeToInstruction?.size)
        } else throw error("class is null")
    }

    @Test
    fun `coverage method with branches SmthSecond`() {
        val analyzeClass = JvmClassAnalyzer.analyzeClass(bytes(ConstructorAndMethod::class))
        if (analyzeClass != null) {
            val bundleProc = createProc(listOf(analyzeClass))
            val execClassData = ExecClassData(
                id = 1L,
                className = analyzeClass.jvmClassName,
                probes = probesOf(false, false, false, true, true)
            )
            val bundle = bundleProc.bundle(sequenceOf(execClassData))
            assertEquals(Count(1, 1).toDto(), bundle.classCount.toDto())
            assertEquals(Count(1, 2).toDto(), bundle.methodCount.toDto())
            assertEquals(Count(15, 22).toDto(), bundle.count.toDto())
        } else throw error("class is null")
    }

    @Test
    fun `coverage method with constructor`() {
        val analyzeClass = JvmClassAnalyzer.analyzeClass(bytes(ConstructorAndMethod::class))
        if (analyzeClass != null) {
            val bundleProc = createProc(listOf(analyzeClass))
            val execClassData = ExecClassData(
                id = 1L,
                className = analyzeClass.jvmClassName,
                probes = probesOf(true, false, false, false, false)
            )
            val bundle = bundleProc.bundle(sequenceOf(execClassData))
            assertEquals(Count(1, 1).toDto(), bundle.classCount.toDto())
            assertEquals(Count(1, 2).toDto(), bundle.methodCount.toDto())//todo
            assertEquals(Count(3, 22).toDto(), bundle.count.toDto())//todo wrong value
        } else throw error("class is null")
    }

    @Test
    fun `coverage method with 100 percent`() {
        val analyzeClass = JvmClassAnalyzer.analyzeClass(bytes(ConstructorAndMethod::class))
        if (analyzeClass != null) {
            val bundleProc = createProc(listOf(analyzeClass))
            val execClassData = ExecClassData(
                id = 1L,
                className = analyzeClass.jvmClassName,
                probes = probesOf(true, true, true, true, true)
            )
            val bundle = bundleProc.bundle(sequenceOf(execClassData))
            assertEquals(Count(1, 1).toDto(), bundle.classCount.toDto())
            assertEquals(Count(2, 2).toDto(), bundle.methodCount.toDto())
            assertEquals(Count(22, 22).toDto(), bundle.count.toDto())
        } else throw error("class is null")
    }


    //todo
    @Test
    fun `coverage method with twice branches SmthForth`() {
        val analyzeClass = JvmClassAnalyzer.analyzeClass(bytes(SmthForth::class))
        if (analyzeClass != null) {
            val bundleProc = createProc(listOf(analyzeClass))
            val execClassData = ExecClassData(
                id = 1L,
                className = analyzeClass.className,
                probes = probesOf(
                    false,
                    false, false, true, false, false, true, true,
                    false,
                )
            )
            val bundle = bundleProc.bundle(sequenceOf(execClassData))
            assertEquals(Count(1, 1).toDto(), bundle.classCount.toDto())
            assertEquals(Count(1, 3).toDto(), bundle.methodCount.toDto())
            //todo ok?
            assertEquals(Count(30, 38).toDto(), bundle.count.toDto())
        } else throw error("class is null")
    }

    @Test
    fun `coverage method with branches and return SmthSix`() {
        val analyzeClass = JvmClassAnalyzer.analyzeClass(bytes(SmthSix::class))
        if (analyzeClass != null) {
            val bundleProc = createProc(listOf(analyzeClass))
            val execClassData = ExecClassData(
                id = 1L,
                className = analyzeClass.className,
                probes = probesOf(
                    false,
                    false, false, true, false, true, false,
                    false,
                )
            )
            //3 + 8=11+8 =19+8.
            val bundle = bundleProc.bundle(sequenceOf(execClassData))
            assertEquals(Count(1, 1).toDto(), bundle.classCount.toDto())
            assertEquals(Count(1, 3).toDto(), bundle.methodCount.toDto())
            //todo
            assertEquals(Count(27, 39).toDto(), bundle.count.toDto())
        } else throw error("class is null")
    }

    @Test
    fun `coverage method with if SmthSeven`() {
        val analyzeClass = JvmClassAnalyzer.analyzeClass(bytes(MethodWithFewIf::class))
        if (analyzeClass != null) {
            val bundleProc = createProc(listOf(analyzeClass))
            val execClassData = ExecClassData(
                id = 1L,
                className = analyzeClass.className,
                probes = probesOf(
                    false,
                    true, false, false, true, false, false, true, false, false, true,
                    false
                )
            )
            val bundle = bundleProc.bundle(sequenceOf(execClassData))
            assertEquals(Count(1, 1).toDto(), bundle.classCount.toDto())
            assertEquals(Count(1, 3).toDto(), bundle.methodCount.toDto())
            //todo need to find Count(X, Y)
            val methodCoverage = analyzeClass.methods["ifElseMethod"]
            methodCoverage?.totalInstruction
            assertEquals(Count(29, 38).toDto(), bundle.count.toDto())
        } else throw error("class is null")
    }

    @Test
    fun `calculate bundle 1 method`() {
        assertTrue(true)
        val jvmClassName = "org/springframework/samples/petclinic/owner/PetValidator"
        val classCoverage = ClassCoverage(jvmClassName, ICoverageNode.ElementType.CLASS, jvmClassName)
        classCoverage.method("<init>", "()V", "()V").also {
            it.probRangeToInstruction[0] = 2
            it.totalInstruction = 2
        }
        classCoverage.analyzeByMethods()
        val probes = probesOf(true)

        val analyzedClasses = listOf(classCoverage)
        val bundleProc = createProc(analyzedClasses)
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
        val classCoverage = ClassCoverage(jvmClassName, ICoverageNode.ElementType.CLASS, jvmClassName)
        classCoverage.method("smth", "()V", "()V").also {
            it.probRangeToInstruction[0] = 3
            it.totalInstruction = 3
        }
        classCoverage.method("<init>", "()V", "()V").also {
            it.probRangeToInstruction[1] = 2
            it.totalInstruction = 2
        }

        classCoverage.analyzeByMethods()
        val analyzedClasses = listOf(classCoverage)
        val bundleProc = createProc(analyzedClasses)
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
        val bundleProc = createProc(analyzedClasses)
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

    private fun createProc(analyzedClasses: List<ClassCoverage>): JavaBundleProc {
        val context = CoverContext(
            agentType = "JAVA",//todo
            packageTree = PackageTree(),
            methods = emptyList(),
            build = CachedBuild("build1"),
            analyzedClasses = analyzedClasses,
        )
        return JavaBundleProc(context)
    }

    private fun classCoverage(jvmClassName: String): ClassCoverage {
        val classCoverage = ClassCoverage(jvmClassName, ICoverageNode.ElementType.CLASS, jvmClassName)
        classCoverage.method("smth", "()V", "()V").also {
            it.probRangeToInstruction[0] = 3
            it.totalInstruction = 3
        }
        classCoverage.method("<init>", "()V", "()V").also {
            it.probRangeToInstruction[1] = 2
            it.totalInstruction = 2
        }
        classCoverage.analyzeByMethods()
        return classCoverage
    }
}
