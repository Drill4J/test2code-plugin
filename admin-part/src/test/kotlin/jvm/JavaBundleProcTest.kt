package jvm

import com.epam.drill.plugins.test2code.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.drill.plugins.test2code.coverage.*
import com.epam.drill.plugins.test2code.jvm.*
import instrumentation.epam.drill.test2code.jacoco.*
import instrumentation.epam.drill.test2code.jacoco.JvmClassAnalyzer.Companion.analyzeByMethods
import org.jacoco.core.analysis.*
import java.io.*
import kotlin.test.*

class JavaBundleProcTest {
    //todo refactoring tests

    //todo signature for method= "()V" ?
    @Test
    fun `analyzeClass second constructor and method`() {
        val className =
            "C:\\Users\\Maksim_Likhanov\\IdeaProjects\\drill-repositories\\test2code-plugin\\admin-part\\build\\classes\\java\\test\\SmthSecond.class"
        val bytes = File(className).readBytes()
        val analyzeClass = JvmClassAnalyzer.analyzeClass(bytes)
        if (analyzeClass != null) {
            assertEquals(2, analyzeClass.methods.size)
            assertEquals(22, analyzeClass.totalInstruction)
            val initMethod = analyzeClass.methods["<init>"]
            assertEquals(3, initMethod?.totalInstruction)
            assertEquals(1, initMethod?.probRangeToInstruction?.size)//count of probes
            val methodCoverage = analyzeClass.methods["ifElseMethod"]
            assertEquals(19, methodCoverage?.totalInstruction)
            assertEquals(4, methodCoverage?.probRangeToInstruction?.size)//count of probes
        } else throw error("class is null")
    }

    @Test
    fun `coverage method with branches`() {
        val className =
            "C:\\Users\\Maksim_Likhanov\\IdeaProjects\\drill-repositories\\test2code-plugin\\admin-part\\build\\classes\\java\\test\\SmthSecond.class"
        val bytes = File(className).readBytes()
        val analyzeClass = JvmClassAnalyzer.analyzeClass(bytes)
        if (analyzeClass != null) {
            val bundleProc = createProc(listOf(analyzeClass))
            val execClassData = ExecClassData(
                id = 1L,
                className = analyzeClass.className,
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
        val className =
            "C:\\Users\\Maksim_Likhanov\\IdeaProjects\\drill-repositories\\test2code-plugin\\admin-part\\build\\classes\\java\\test\\SmthSecond.class"
        val bytes = File(className).readBytes()
        val analyzeClass = JvmClassAnalyzer.analyzeClass(bytes)
        if (analyzeClass != null) {
            val bundleProc = createProc(listOf(analyzeClass))
            val execClassData = ExecClassData(
                id = 1L,
                className = analyzeClass.className,
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
        val className =
            "C:\\Users\\Maksim_Likhanov\\IdeaProjects\\drill-repositories\\test2code-plugin\\admin-part\\build\\classes\\java\\test\\SmthSecond.class"
        val bytes = File(className).readBytes()
        val analyzeClass = JvmClassAnalyzer.analyzeClass(bytes)
        if (analyzeClass != null) {
            val bundleProc = createProc(listOf(analyzeClass))
            val execClassData = ExecClassData(
                id = 1L,
                className = analyzeClass.className,
                probes = probesOf(true, true, true, true, true)
            )
            val bundle = bundleProc.bundle(sequenceOf(execClassData))
            assertEquals(Count(1, 1).toDto(), bundle.classCount.toDto())
            assertEquals(Count(2, 2).toDto(), bundle.methodCount.toDto())//todo
            assertEquals(Count(22, 22).toDto(), bundle.count.toDto())//todo wrong value
        } else throw error("class is null")
    }


    @Test
    fun `coverage method with twice branches`() {
        val className =
            "C:\\Users\\Maksim_Likhanov\\IdeaProjects\\drill-repositories\\test2code-plugin\\admin-part\\build\\classes\\java\\test\\SmthForth.class"
        val bytes = File(className).readBytes()
        val analyzeClass = JvmClassAnalyzer.analyzeClass(bytes)
        if (analyzeClass != null) {
            val bundleProc = createProc(listOf(analyzeClass))
            val execClassData = ExecClassData(
                id = 1L,
                className = analyzeClass.className,
                probes = probesOf(false,
                    false, false, true, false, false, true, true,
                    false,
                )
            )
            val bundle = bundleProc.bundle(sequenceOf(execClassData))
            assertEquals(Count(1, 1).toDto(), bundle.classCount.toDto())
            assertEquals(Count(1, 3).toDto(), bundle.methodCount.toDto())
            assertEquals(Count(30, 38).toDto(), bundle.count.toDto())
        } else throw error("class is null")
    }

    @Test
    fun `coverage method with branches and return`() {
        val className =
            "C:\\Users\\Maksim_Likhanov\\IdeaProjects\\drill-repositories\\test2code-plugin\\admin-part\\build\\classes\\java\\test\\SmthSix.class"
        val bytes = File(className).readBytes()
        val analyzeClass = JvmClassAnalyzer.analyzeClass(bytes)
        if (analyzeClass != null) {
            val bundleProc = createProc(listOf(analyzeClass))
            val execClassData = ExecClassData(
                id = 1L,
                className = analyzeClass.className,
                probes = probesOf(false,
                    false, false, true, false, true, false,
                    false,
                )
            )
            //3 + 8=11+8 =19+8.
            val bundle = bundleProc.bundle(sequenceOf(execClassData))
            assertEquals(Count(1, 1).toDto(), bundle.classCount.toDto())
            assertEquals(Count(1, 3).toDto(), bundle.methodCount.toDto())
            assertEquals(Count(27, 39).toDto(), bundle.count.toDto())
        } else throw error("class is null")
    }

    @Test
    fun `asd asd second`() {
//        val className = "C:\\Users\\Maksim_Likhanov\\IdeaProjects\\drill-repositories\\test2code-plugin\\agent-part\\src\\test\\java\\org\\springframework\\samples\\petclinic\\owner\\OwnerController.class"
//        val className = "C:\\Users\\Maksim_Likhanov\\IdeaProjects\\drill-repositories\\test2code-plugin\\agent-part\\src\\test\\java\\OwnerController.class"
        val className = "C:\\Users\\Maksim_Likhanov\\IdeaProjects\\drill-repositories\\test2code-plugin\\agent-part\\OwnerController.class"
//        val className = "C:\\Users\\Maksim_Likhanov\\IdeaProjects\\drill-repositories\\test2code-plugin\\admin-part\\build\\classes\\java\\test\\SmthSecond.class"
//        val className =
//            "C:\\Users\\Maksim_Likhanov\\IdeaProjects\\drill-repositories\\test2code-plugin\\admin-part\\build\\classes\\java\\test\\Smth.class"
        val bytes = File(className).readBytes()
        val analyzeClass = JvmClassAnalyzer.analyzeClass(bytes)
        //todo another case

    }

    /**
     * CountDto(covered=23, total=48)
     * new CountDto(covered=16, total=48)
     * {1, 8, 11, 17, 25}
     *
    {Integer@10375} 8 -> {Integer@10371} 3 //todo
    {Integer@10368} 9 -> {Integer@10365} 0
    {Integer@10374} 10 -> {Integer@10371} 3
    {Integer@10376} 11 -> {Integer@10366} 6 //todo
    {Integer@10377} 12 -> {Integer@10371} 3
    {Integer@10378} 13 -> {Integer@10372} 7
    {Integer@10379} 14 -> {Integer@10370} 4
    {Integer@10380} 15 -> {Integer@10373} 5
    {Integer@10381} 16 -> {Integer@10374} 10
    {Integer@10382} 17 -> {Integer@10372} 7 //todo
    ????+3+6+7 = 16
    sum = need to be = 23 ??

     */
    @Ignore
    @Test
    fun `case with petclinic`() {
//        val className = "C:\\Users\\Maksim_Likhanov\\IdeaProjects\\drill-repositories\\test2code-plugin\\agent-part\\src\\test\\java\\org\\springframework\\samples\\petclinic\\owner\\OwnerController.class"
//        val className = "C:\\Users\\Maksim_Likhanov\\IdeaProjects\\drill-repositories\\test2code-plugin\\agent-part\\src\\test\\java\\OwnerController.class"
        val className =
            "C:\\Users\\Maksim_Likhanov\\IdeaProjects\\drill-repositories\\test2code-plugin\\agent-part\\OwnerController.class"
        val bytes = File(className).readBytes()
        val analyzeClass = JvmClassAnalyzer.analyzeClass(bytes)
        println(analyzeClass)
        //todo
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
