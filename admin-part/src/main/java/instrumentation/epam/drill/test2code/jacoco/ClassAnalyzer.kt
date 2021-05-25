@file:Suppress("unused")

package instrumentation.epam.drill.test2code.jacoco

import com.epam.drill.plugins.test2code.jvm.*
import com.epam.kodux.util.*
import org.jacoco.core.analysis.*
import org.jacoco.core.internal.analysis.*
import org.jacoco.core.internal.analysis.filter.*
import org.jacoco.core.internal.flow.*
import org.jacoco.core.internal.instr.*
import org.objectweb.asm.*
import org.objectweb.asm.commons.*
import org.objectweb.asm.tree.*
import java.lang.AssertionError
import java.util.*
import java.util.function.Consumer


class JvmClassAnalyzer {
    fun analyzeClass(source: ByteArray?): ClassCoverage? {
        val reader = InstrSupport.classReaderFor(source)
        if (reader.access and Opcodes.ACC_MODULE != 0) {
            return null
        }
        if (reader.access and Opcodes.ACC_SYNTHETIC != 0) {
            return null
        }
        val classCoverage = ClassCoverage(reader.className.weakIntern())
        val classProbesAdapter = ClassProbesAdapter(ClassAnalyzer(classCoverage), false)
        reader.accept(classProbesAdapter, 0)
        classCoverage.totalInstruction = classCoverage.methods.values.stream().map(MethodCoverage::totalInstruction)
            .reduce(0) { a: Int, b: Int -> Integer.sum(a, b) }
        val integers = HashMap<Int, Int>()
        classCoverage.methods.values.forEach(Consumer { x: MethodCoverage -> integers.putAll(x.probRangeToInstruction) })
        classCoverage.probRangeToInstruction = integers
        return classCoverage
    }
}


class ClassAnalyzer(private val classCoverage: ClassCoverage) : ClassProbesVisitor(), IFilterContext {
    private val stringPool = StringPool()
    private val classAnnotations: MutableSet<String> = HashSet()
    private val classAttributes: MutableSet<String> = HashSet()
    private val filter: IFilter = Filters.all()
    override fun visit(
        version: Int, access: Int,
        name: String,
        signature: String?,
        superName: String,
        interfaces: Array<String>,
    ) {
    }

    override fun visitAnnotation(
        desc: String,
        visible: Boolean,
    ): AnnotationVisitor? {
        classAnnotations.add(desc)
        return super.visitAnnotation(desc, visible)
    }

    override fun visitAttribute(attribute: Attribute) {
        classAttributes.add(attribute.type)
    }

    override fun visitSource(source: String, debug: String?) {}
    override fun visitMethod(
        access: Int,
        name: String,
        desc: String,
        signature: String?,
        exceptions: Array<String>?,
    ): MethodProbesVisitor {
        val builder = InstructionsBuilder(
            classCoverage.method(name, desc)
        )
        return object : MethodAnalyzer(builder) {
            override fun accept(
                methodNode: MethodNode,
                methodVisitor: MethodVisitor?,
            ) {
                super.accept(methodNode, methodVisitor)
                val total =
                    builder.methodCoverage.probRangeToInstruction.values.stream().mapToInt { count: Int? -> count!! }
                        .sum()
                builder.methodCoverage.totalInstruction = total
                addMethodCoverage(stringPool[name], stringPool[desc], stringPool[signature], builder, methodNode)
            }
        }
    }

    private fun addMethodCoverage(
        name: String,
        desc: String,
        signature: String?,
        icc: InstructionsBuilder,
        methodNode: MethodNode,
    ) {
        val mcc = MethodCoverageCalculator(
            icc.getInstructions()
        )
        filter.filter(methodNode, this, mcc)
        val mc = MethodCoverageImpl(
            name, desc,
            signature
        )
        mcc.calculate(mc)
        if (mc.containsCode()) {
        }
    }

    override fun visitField(
        access: Int,
        name: String,
        desc: String,
        signature: String?,
        value: Any?,
    ): FieldVisitor? {
        return super.visitField(access, name, desc, signature, value)
    }

    override fun visitTotalProbeCount(count: Int) {
        // nothing to do
    }

    // IFilterContext implementation
    override fun getClassName(): String {
        return ""
    }

    override fun getSuperClassName(): String {
        return ""
    }

    override fun getClassAnnotations(): Set<String> {
        return classAnnotations
    }

    override fun getClassAttributes(): Set<String> {
        return classAttributes
    }

    override fun getSourceFileName(): String {
        return ""
    }

    override fun getSourceDebugExtension(): String {
        return ""
    }

}

internal open class MethodSanitizer(
    mv: MethodVisitor?,
    access: Int,
    name: String?,
    desc: String?,
    signature: String?,
    exceptions: Array<String?>?,
) : JSRInlinerAdapter(
    InstrSupport.ASM_API_VERSION, mv, access, name, desc, signature,
    exceptions
) {
    override fun visitLocalVariable(
        name: String,
        desc: String,
        signature: String?,
        start: Label?,
        end: Label?,
        index: Int,
    ) {
        // Here we rely on the usage of the info fields by the tree API. If the
        // labels have been properly used before the info field contains a
        // reference to the LabelNode, otherwise null.
        if (start?.info != null && end?.info != null) {
            super.visitLocalVariable(name, desc, signature, start, end, index)
        }
    }

    override fun visitLineNumber(line: Int, start: Label) {
        // Here we rely on the usage of the info fields by the tree API. If the
        // labels have been properly used before the info field contains a
        // reference to the LabelNode, otherwise null.
        if (start.info != null) {
            super.visitLineNumber(line, start)
        }
    }
}


abstract class MethodProbesVisitor @JvmOverloads constructor(mv: MethodVisitor? = null) :
    MethodVisitor(InstrSupport.ASM_API_VERSION, mv) {
    open fun visitProbe(probeId: Int) {}
    open fun visitJumpInsnWithProbe(
        opcode: Int, label: Label?,
        probeId: Int, frame: IFrame?,
    ) {
    }

    open fun visitInsnWithProbe(opcode: Int, probeId: Int) {}
    open fun visitTableSwitchInsnWithProbes(
        min: Int, max: Int,
        dflt: Label?, labels: Array<out Label?>, frame: IFrame?,
    ) {
    }

    open fun visitLookupSwitchInsnWithProbes(
        dflt: Label?,
        keys: IntArray?, labels: Array<out Label?>, frame: IFrame?,
    ) {
    }

    open fun accept(
        methodNode: MethodNode,
        methodVisitor: MethodVisitor?,
    ) {
        methodNode.accept(methodVisitor)
    }
}


class MethodProbesAdapter(
    private val probesVisitor: MethodProbesVisitor,
    private val idGenerator: IProbeIdGenerator,
) : MethodVisitor(InstrSupport.ASM_API_VERSION, probesVisitor) {
    private var analyzer: AnalyzerAdapter? = null
    private val tryCatchProbeLabels: MutableMap<Label?, Label>
    fun setAnalyzer(analyzer: AnalyzerAdapter?) {
        this.analyzer = analyzer
    }

    override fun visitTryCatchBlock(
        start: Label, end: Label,
        handler: Label, type: String,
    ) {
        probesVisitor.visitTryCatchBlock(
            getTryCatchLabel(start),
            getTryCatchLabel(end), handler, type
        )
    }

    private fun getTryCatchLabel(label: Label): Label? {
        var label: Label? = label
        if (tryCatchProbeLabels.containsKey(label)) {
            label = tryCatchProbeLabels[label]
        } else if (LabelInfo.needsProbe(label)) {
            // If a probe will be inserted before the label, we'll need to use a
            // different label to define the range of the try-catch block.
            val probeLabel = Label()
            LabelInfo.setSuccessor(probeLabel)
            tryCatchProbeLabels[label] = probeLabel
            label = probeLabel
        }
        return label
    }

    override fun visitLabel(label: Label) {
        if (LabelInfo.needsProbe(label)) {
            if (tryCatchProbeLabels.containsKey(label)) {
                probesVisitor.visitLabel(tryCatchProbeLabels[label])
            }
            probesVisitor.visitProbe(idGenerator.nextId())
        }
        probesVisitor.visitLabel(label)
    }

    override fun visitInsn(opcode: Int) {
        when (opcode) {
            Opcodes.IRETURN, Opcodes.LRETURN, Opcodes.FRETURN, Opcodes.DRETURN, Opcodes.ARETURN, Opcodes.RETURN, Opcodes.ATHROW -> probesVisitor.visitInsnWithProbe(
                opcode,
                idGenerator.nextId()
            )
            else -> probesVisitor.visitInsn(opcode)
        }
    }

    override fun visitJumpInsn(opcode: Int, label: Label) {
        if (LabelInfo.isMultiTarget(label)) {
            probesVisitor.visitJumpInsnWithProbe(
                opcode, label,
                idGenerator.nextId(), frame(jumpPopCount(opcode))
            )
        } else {
            probesVisitor.visitJumpInsn(opcode, label)
        }
    }

    private fun jumpPopCount(opcode: Int): Int {
        return when (opcode) {
            Opcodes.GOTO -> 0
            Opcodes.IFEQ, Opcodes.IFNE, Opcodes.IFLT, Opcodes.IFGE, Opcodes.IFGT, Opcodes.IFLE, Opcodes.IFNULL, Opcodes.IFNONNULL -> 1
            else -> 2
        }
    }

    override fun visitLookupSwitchInsn(
        dflt: Label,
        keys: IntArray,
        labels: Array<Label>,
    ) {
        if (markLabels(dflt, labels)) {
            probesVisitor.visitLookupSwitchInsnWithProbes(
                dflt, keys, labels,
                frame(1)
            )
        } else {
            probesVisitor.visitLookupSwitchInsn(dflt, keys, labels)
        }
    }

    override fun visitTableSwitchInsn(
        min: Int, max: Int,
        dflt: Label, vararg labels: Label,
    ) {
        if (markLabels(dflt, labels)) {
            probesVisitor.visitTableSwitchInsnWithProbes(
                min, max, dflt, labels,
                frame(1)
            )
        } else {
            probesVisitor.visitTableSwitchInsn(min, max, dflt, *labels)
        }
    }

    private fun markLabels(dflt: Label, labels: Array<out Label>): Boolean {
        var probe = false
        LabelInfo.resetDone(*labels)
        if (LabelInfo.isMultiTarget(dflt)) {
            LabelInfo.setProbeId(dflt, idGenerator.nextId())
            probe = true
        }
        LabelInfo.setDone(dflt)
        for (l in labels) {
            if (LabelInfo.isMultiTarget(l) && !LabelInfo.isDone(l)) {
                LabelInfo.setProbeId(l, idGenerator.nextId())
                probe = true
            }
            LabelInfo.setDone(l)
        }
        return probe
    }

    private fun frame(popCount: Int): IFrame {
        return FrameSnapshot.create(analyzer, popCount)
    }

    init {
        tryCatchProbeLabels = HashMap()
    }
}


open class MethodAnalyzer internal constructor(private val builder: InstructionsBuilder) : MethodProbesVisitor() {
    private var currentNode: AbstractInsnNode? = null
    override fun accept(
        methodNode: MethodNode,
        methodVisitor: MethodVisitor?,
    ) {
        methodVisitor!!.visitCode()
        for (n in methodNode.tryCatchBlocks) {
            n.accept(methodVisitor)
        }
        for (i in methodNode.instructions) {
            currentNode = i
            i.accept(methodVisitor)
        }
        methodVisitor.visitEnd()
    }

    override fun visitLabel(label: Label) {
        builder.addLabel(label)
    }

    override fun visitLineNumber(line: Int, start: Label) {
        builder.setCurrentLine(line)
    }

    override fun visitInsn(opcode: Int) {
        builder.addInstruction(currentNode)
    }

    override fun visitIntInsn(opcode: Int, operand: Int) {
        builder.addInstruction(currentNode)
    }

    override fun visitVarInsn(opcode: Int, `var`: Int) {
        builder.addInstruction(currentNode)
    }

    override fun visitTypeInsn(opcode: Int, type: String) {
        builder.addInstruction(currentNode)
    }

    override fun visitFieldInsn(
        opcode: Int, owner: String,
        name: String, desc: String,
    ) {
        builder.addInstruction(currentNode)
    }

    override fun visitMethodInsn(
        opcode: Int, owner: String,
        name: String, desc: String, itf: Boolean,
    ) {
        builder.addInstruction(currentNode)
    }

    override fun visitInvokeDynamicInsn(
        name: String, desc: String,
        bsm: Handle, vararg bsmArgs: Any,
    ) {
        builder.addInstruction(currentNode)
    }

    override fun visitJumpInsn(opcode: Int, label: Label) {
        builder.addInstruction(currentNode)
        builder.addJump(label, 1)
    }

    override fun visitLdcInsn(cst: Any) {
        builder.addInstruction(currentNode)
    }

    override fun visitIincInsn(`var`: Int, increment: Int) {
        builder.addInstruction(currentNode)
    }

    override fun visitTableSwitchInsn(
        min: Int, max: Int,
        dflt: Label, vararg labels: Label,
    ) {
        visitSwitchInsn(dflt, labels)
    }

    override fun visitLookupSwitchInsn(
        dflt: Label, keys: IntArray,
        labels: Array<Label>,
    ) {
        visitSwitchInsn(dflt, labels)
    }

    private fun visitSwitchInsn(dflt: Label, labels: Array<out Label>) {
        builder.addInstruction(currentNode)
        LabelInfo.resetDone(*labels)
        var branch = 0
        builder.addJump(dflt, branch)
        LabelInfo.setDone(dflt)
        for (l in labels) {
            if (!LabelInfo.isDone(l)) {
                branch++
                builder.addJump(l, branch)
                LabelInfo.setDone(l)
            }
        }
    }

    override fun visitMultiANewArrayInsn(desc: String, dims: Int) {
        builder.addInstruction(currentNode)
    }

    override fun visitProbe(probeId: Int) {
        builder.addProbe(probeId, 0)
        builder.noSuccessor()
    }

    override fun visitJumpInsnWithProbe(
        opcode: Int, label: Label?,
        probeId: Int, frame: IFrame?,
    ) {
        builder.addInstruction(currentNode)
        builder.addProbe(probeId, 1)
    }

    override fun visitInsnWithProbe(opcode: Int, probeId: Int) {
        builder.addInstruction(currentNode)
        builder.addProbe(probeId, 0)
    }

    override fun visitTableSwitchInsnWithProbes(
        min: Int, max: Int,
        dflt: Label?, labels: Array<out Label?>, frame: IFrame?,
    ) {
        visitSwitchInsnWithProbes(dflt, labels)
    }

    override fun visitLookupSwitchInsnWithProbes(
        dflt: Label?,
        keys: IntArray?, labels: Array<out Label?>, frame: IFrame?,
    ) {
        visitSwitchInsnWithProbes(dflt, labels)
    }

    private fun visitSwitchInsnWithProbes(
        dflt: Label?,
        labels: Array<out Label?>,
    ) {
        builder.addInstruction(currentNode)
        LabelInfo.resetDone(dflt)
        LabelInfo.resetDone(*labels)
        var branch = 0
        visitSwitchTarget(dflt, branch)
        for (l in labels) {
            branch++
            visitSwitchTarget(l, branch)
        }
    }

    private fun visitSwitchTarget(label: Label?, branch: Int) {
        val id = LabelInfo.getProbeId(label)
        if (!LabelInfo.isDone(label)) {
            if (id == LabelInfo.NO_PROBE) {
                builder.addJump(label, branch)
            } else {
                builder.addProbe(id, branch)
            }
            LabelInfo.setDone(label)
        }
    }
}


class LabelInfo private constructor() {
    private var target = false
    private var multiTarget = false
    private var successor = false
    private var methodInvocationLine = false
    private var done = false
    private var probeid = NO_PROBE
    private var intermediate: Label? = null
    private var instruction: Instruction? = null

    companion object {
        const val NO_PROBE = -1

        @JvmStatic
        fun setTarget(label: Label) {
            val info = create(label)
            if (info.target || info.successor) {
                info.multiTarget = true
            } else {
                info.target = true
            }
        }

        @JvmStatic
        fun setSuccessor(label: Label) {
            val info = create(label)
            info.successor = true
            if (info.target) {
                info.multiTarget = true
            }
        }

        fun isMultiTarget(label: Label): Boolean {
            val info = Companion[label]
            return info != null && info.multiTarget
        }

        @JvmStatic
        fun isSuccessor(label: Label): Boolean {
            val info = Companion[label]
            return info != null && info.successor
        }

        @JvmStatic
        fun setMethodInvocationLine(label: Label) {
            create(label).methodInvocationLine = true
        }

        fun isMethodInvocationLine(label: Label): Boolean {
            val info = Companion[label]
            return info != null && info.methodInvocationLine
        }

        fun needsProbe(label: Label?): Boolean {
            val info = Companion[label]
            return (info != null && info.successor
                    && (info.multiTarget || info.methodInvocationLine))
        }

        @JvmStatic
        fun setDone(label: Label?) {
            create(label).done = true
        }

        fun resetDone(label: Label?) {
            val info = Companion[label]
            if (info != null) {
                info.done = false
            }
        }

        @JvmStatic
        fun resetDone(vararg labels: Label?) {
            for (label in labels) {
                resetDone(label)
            }
        }

        @JvmStatic
        fun isDone(label: Label?): Boolean {
            val info = Companion[label]
            return info != null && info.done
        }

        fun setProbeId(label: Label, id: Int) {
            create(label).probeid = id
        }

        fun getProbeId(label: Label?): Int {
            val info = Companion[label]
            return info?.probeid ?: NO_PROBE
        }

        fun setIntermediateLabel(
            label: Label,
            intermediate: Label?,
        ) {
            create(label).intermediate = intermediate
        }

        fun getIntermediateLabel(label: Label): Label? {
            val info = Companion[label]
            return info?.intermediate
        }

        @JvmStatic
        fun setInstruction(
            label: Label,
            instruction: Instruction?,
        ) {
            create(label).instruction = instruction
        }

        @JvmStatic
        fun getInstruction(label: Label): Instruction? {
            val info = Companion[label]
            return info?.instruction
        }

        private operator fun get(label: Label?): LabelInfo? {
            val info = label?.info
            return if (info is LabelInfo) info else null
        }

        private fun create(label: Label?): LabelInfo {
            var info = Companion[label]
            if (info == null) {
                info = LabelInfo()
                label?.info = info
            }
            return info
        }
    }
}


class LabelFlowAnalyzer : MethodVisitor(InstrSupport.ASM_API_VERSION) {
    private var successor = false
    var first = true
    private var lineStart: Label? = null
    override fun visitTryCatchBlock(
        start: Label, end: Label,
        handler: Label, type: String,
    ) {
        // Enforce probe at the beginning of the block. Assuming the start of
        // the block already is successor of some other code, adding a target
        // makes the start a multitarget. However, if the start of the block
        // also is the start of the method, no probe will be added.
        LabelInfo.setTarget(start)

        // Mark exception handler as possible target of the block
        LabelInfo.setTarget(handler)
    }

    override fun visitJumpInsn(opcode: Int, label: Label) {
        LabelInfo.setTarget(label)
        if (opcode == Opcodes.JSR) {
            throw AssertionError("Subroutines not supported.")
        }
        successor = opcode != Opcodes.GOTO
        first = false
    }

    override fun visitLabel(label: Label) {
        if (first) {
            LabelInfo.setTarget(label)
        }
        if (successor) {
            LabelInfo.setSuccessor(label)
        }
    }

    override fun visitLineNumber(line: Int, start: Label) {
        lineStart = start
    }

    override fun visitTableSwitchInsn(
        min: Int, max: Int,
        dflt: Label, vararg labels: Label,
    ) {
        visitSwitchInsn(dflt, labels)
    }

    override fun visitLookupSwitchInsn(
        dflt: Label, keys: IntArray,
        labels: Array<Label>,
    ) {
        visitSwitchInsn(dflt, labels)
    }

    private fun visitSwitchInsn(dflt: Label, labels: Array<out Label>) {
        LabelInfo.resetDone(dflt)
        LabelInfo.resetDone(*labels)
        setTargetIfNotDone(dflt)
        for (l in labels) {
            setTargetIfNotDone(l)
        }
        successor = false
        first = false
    }

    override fun visitInsn(opcode: Int) {
        successor = when (opcode) {
            Opcodes.RET -> throw AssertionError("Subroutines not supported.")
            Opcodes.IRETURN, Opcodes.LRETURN, Opcodes.FRETURN, Opcodes.DRETURN, Opcodes.ARETURN, Opcodes.RETURN, Opcodes.ATHROW -> false
            else -> true
        }
        first = false
    }

    override fun visitIntInsn(opcode: Int, operand: Int) {
        successor = true
        first = false
    }

    override fun visitVarInsn(opcode: Int, `var`: Int) {
        successor = true
        first = false
    }

    override fun visitTypeInsn(opcode: Int, type: String) {
        successor = true
        first = false
    }

    override fun visitFieldInsn(
        opcode: Int, owner: String,
        name: String, desc: String,
    ) {
        successor = true
        first = false
    }

    override fun visitMethodInsn(
        opcode: Int, owner: String,
        name: String, desc: String, itf: Boolean,
    ) {
        successor = true
        first = false
        markMethodInvocationLine()
    }

    override fun visitInvokeDynamicInsn(
        name: String, desc: String,
        bsm: Handle, vararg bsmArgs: Any,
    ) {
        successor = true
        first = false
        markMethodInvocationLine()
    }

    private fun markMethodInvocationLine() {
        if (lineStart != null) {
            LabelInfo.setMethodInvocationLine(lineStart!!)
        }
    }

    override fun visitLdcInsn(cst: Any) {
        successor = true
        first = false
    }

    override fun visitIincInsn(`var`: Int, increment: Int) {
        successor = true
        first = false
    }

    override fun visitMultiANewArrayInsn(desc: String, dims: Int) {
        successor = true
        first = false
    }

    companion object {
        @JvmStatic
        fun markLabels(method: MethodNode) {
            // We do not use the accept() method as ASM resets labels after every
            // call to accept()
            val lfa: MethodVisitor = LabelFlowAnalyzer()
            var i = method.tryCatchBlocks.size
            while (--i >= 0) {
                method.tryCatchBlocks[i].accept(lfa)
            }
            method.instructions.accept(lfa)
        }

        private fun setTargetIfNotDone(label: Label) {
            if (!LabelInfo.isDone(label)) {
                LabelInfo.setTarget(label)
                LabelInfo.setDone(label)
            }
        }
    }
}


class ClassProbesAdapter(cv: ClassProbesVisitor?, private val trackFrames: Boolean) :
    ClassVisitor(InstrSupport.ASM_API_VERSION, cv), IProbeIdGenerator {

    override fun visitEnd() {
        (cv as? ClassProbesVisitor)?.visitTotalProbeCount(counter)
        super.visitEnd()
    }

    private var counter = 0
    private var name: String? = null

    override fun visit(
        version: Int, access: Int, name: String,
        signature: String?,
        superName: String,
        interfaces: Array<String>,
    ) {
        this.name = name
        super.visit(version, access, name, signature, superName, interfaces)
    }

    override fun visitMethod(
        access: Int,
        name: String,
        desc: String,
        signature: String?,
        exceptions: Array<String?>?,
    ): MethodVisitor {
        val methodProbes: MethodProbesVisitor
        val mv: MethodProbesVisitor? = cv.visitMethod(
            access, name, desc,
            signature, exceptions
        ) as? MethodProbesVisitor
        methodProbes = mv ?: // We need to visit the method in any case, otherwise probe ids
                // are not reproducible
                EMPTY_METHOD_PROBES_VISITOR
        return object : MethodSanitizer(null, access, name, desc, signature, exceptions) {
            override fun visitEnd() {
                super.visitEnd()
                LabelFlowAnalyzer.markLabels(this)
                val probesAdapter = MethodProbesAdapter(methodProbes, this@ClassProbesAdapter)
                if (trackFrames) {
                    val analyzer = AnalyzerAdapter(
                        this@ClassProbesAdapter.name, access, name, desc,
                        probesAdapter
                    )
                    probesAdapter.setAnalyzer(analyzer)
                    methodProbes.accept(this, analyzer)
                } else {
                    methodProbes.accept(this, probesAdapter)
                }
            }
        }
    }

    override fun nextId(): Int {
        return counter++
    }

    companion object {
        private val EMPTY_METHOD_PROBES_VISITOR: MethodProbesVisitor = object : MethodProbesVisitor() {}
    }


}


internal class MethodCoverageCalculator(private val instructions: MutableMap<AbstractInsnNode?, Instruction?>) :
    IFilterOutput {
    private val ignored: MutableSet<AbstractInsnNode>
    private val merged: MutableMap<AbstractInsnNode, AbstractInsnNode>
    private val replacements: MutableMap<AbstractInsnNode, Set<AbstractInsnNode>>
    fun calculate(coverage: MethodCoverageImpl) {
        applyMerges()
        applyReplacements()
        ensureCapacity(coverage)
        for ((key, instruction) in instructions) {
            if (!ignored.contains(key)) {
                coverage.increment(instruction!!.instructionCounter, instruction.branchCounter, instruction.line)
            }
        }
        coverage.incrementMethodCounter()
    }

    private fun applyMerges() {
        // Merge to the representative:
        for (entry in merged
            .entries) {
            val node = entry.key
            val instruction = instructions[node]
            val representativeNode = findRepresentative(
                node
            )
            ignored.add(node)
            instructions[representativeNode] = instructions[representativeNode]!!.merge(instruction)
            entry.setValue(representativeNode)
        }

        // Get merged value back from representative
        for ((key, value) in merged) {
            instructions[key] = instructions[value]
        }
    }

    private fun applyReplacements() {
        for ((node, replacements) in replacements) {
            val newBranches: MutableList<Instruction> = ArrayList(
                replacements.size
            )
            for (b in replacements) {
                newBranches.add(instructions[b]!!)
            }
            instructions[node] = instructions[node]!!.replaceBranches(newBranches)
        }
    }

    private fun ensureCapacity(coverage: MethodCoverageImpl) {
        // Determine line range:
        var firstLine = ISourceNode.UNKNOWN_LINE
        var lastLine = ISourceNode.UNKNOWN_LINE
        for ((key, value) in instructions) {
            if (!ignored.contains(key)) {
                val line = value!!.line
                if (line != ISourceNode.UNKNOWN_LINE) {
                    if (firstLine > line
                        || lastLine == ISourceNode.UNKNOWN_LINE
                    ) {
                        firstLine = line
                    }
                    if (lastLine < line) {
                        lastLine = line
                    }
                }
            }
        }

        // Performance optimization to avoid incremental increase of line array:
        coverage.ensureCapacity(firstLine, lastLine)
    }

    private fun findRepresentative(i: AbstractInsnNode): AbstractInsnNode {
        var i = i
        var r: AbstractInsnNode
        while (merged[i].also { r = it!! } != null) {
            i = r
        }
        return i
    }

    // === IFilterOutput API ===
    override fun ignore(
        fromInclusive: AbstractInsnNode,
        toInclusive: AbstractInsnNode,
    ) {
        var i = fromInclusive
        while (i !== toInclusive) {
            ignored.add(i)
            i = i
                .next
        }
        ignored.add(toInclusive)
    }

    override fun merge(i1: AbstractInsnNode, i2: AbstractInsnNode) {
        var i1 = i1
        var i2 = i2
        i1 = findRepresentative(i1)
        i2 = findRepresentative(i2)
        if (i1 !== i2) {
            merged[i2] = i1
        }
    }

    override fun replaceBranches(
        source: AbstractInsnNode,
        newTargets: Set<AbstractInsnNode>,
    ) {
        replacements[source] = newTargets
    }

    init {
        ignored = HashSet()
        merged = HashMap()
        replacements = HashMap()
    }
}


abstract class ClassProbesVisitor @JvmOverloads constructor(cv: ClassVisitor? = null) :
    ClassVisitor(InstrSupport.ASM_API_VERSION, cv) {
    abstract override fun visitMethod(
        access: Int,
        name: String,
        desc: String,
        signature: String?,
        exceptions: Array<String>?,
    ): MethodProbesVisitor

    abstract fun visitTotalProbeCount(count: Int)
}


class Instruction(val line: Int, private var methodCoverage: MethodCoverage, private val currentProbe: Int) {
    private var branches = 0
    private val coveredBranches: BitSet = BitSet()
    private var predecessor: Instruction? = null
    private var predecessorBranch = 0
    fun addBranch(target: Instruction, branch: Int) {
        branches++
        target.predecessor = this
        target.predecessorBranch = branch
        if (!target.coveredBranches.isEmpty) {
            propagateExecutedBranch(this, branch)
        }
    }

    fun addBranch(probeId: Int, instructionCounter: Int): Boolean {
        branches++
        val integer = methodCoverage.probRangeToInstruction[probeId]
        if (integer == null) {
            methodCoverage.probRangeToInstruction[probeId] = instructionCounter
            return true
        }
        return false
    }

    fun merge(other: Instruction?): Instruction {
        val result = Instruction(line, methodCoverage, currentProbe)
        result.branches = branches
        result.coveredBranches.or(coveredBranches)
        result.coveredBranches.or(other?.coveredBranches)
        return result
    }

    fun replaceBranches(
        newBranches: Collection<Instruction>,
    ): Instruction {
        val result = Instruction(line, methodCoverage, currentProbe)
        result.branches = newBranches.size
        var idx = 0
        for (b in newBranches) {
            if (!b.coveredBranches?.isEmpty) {
                result.coveredBranches.set(idx++)
            }
        }
        return result
    }

    val instructionCounter: ICounter
        get() = if (coveredBranches.isEmpty) CounterImpl.COUNTER_1_0 else CounterImpl.COUNTER_0_1
    val branchCounter: ICounter
        get() {
            if (branches < 2) {
                return CounterImpl.COUNTER_0_0
            }
            val covered = Math.max(0, coveredBranches.cardinality() - 1)
            return CounterImpl.getInstance(branches - covered, covered)
        }

    companion object {
        private fun propagateExecutedBranch(insn: Instruction, branch: Int) {
            // No recursion here, as there can be very long chains of instructions
            var insn: Instruction? = insn
            var branch = branch
            while (insn != null) {
                if (!insn.coveredBranches.isEmpty) {
                    insn.coveredBranches.set(branch)
                    break
                }
                insn.coveredBranches.set(branch)
                branch = insn.predecessorBranch
                insn = insn.predecessor
            }
        }
    }

}


internal class InstructionsBuilder(methodCoverage: MethodCoverage) {
    private var currentLine: Int
    private var currentInsn: Instruction?
    private var instructions: MutableMap<AbstractInsnNode?, Instruction?>
    private val currentLabel: MutableList<Label>
    private val jumps: MutableList<Jump>
    var methodCoverage: MethodCoverage
    private var currentProbe = 0
    private var instructionCounter = 0
    fun setCurrentLine(line: Int) {
        currentLine = line
    }

    fun addLabel(label: Label) {
        currentLabel.add(label)
        if (!LabelInfo.isSuccessor(label)) {
            noSuccessor()
        }
    }

    fun addInstruction(node: AbstractInsnNode?) {
        instructionCounter++
        val insn = Instruction(currentLine, methodCoverage, currentProbe)
        val labelCount = currentLabel.size
        if (labelCount > 0) {
            var i = labelCount
            while (--i >= 0) {
                LabelInfo.setInstruction(currentLabel[i], insn)
            }
            currentLabel.clear()
        }
        if (currentInsn != null) {
            currentInsn!!.addBranch(insn, 0)
        }
        currentInsn = insn
        instructions[node] = insn
    }

    fun noSuccessor() {
        currentInsn = null
    }

    fun addJump(target: Label?, branch: Int) {
        jumps.add(Jump(currentInsn, target, branch))
    }

    fun addProbe(probeId: Int, branch: Int) {
        if (currentInsn!!.addBranch(probeId, instructionCounter)) {
            instructionCounter = 0
        }
    }

    fun getInstructions(): MutableMap<AbstractInsnNode?, Instruction?> {
        for (j in jumps) {
            j.wire()
        }
        return instructions
    }

    private class Jump internal constructor(
        private val source: Instruction?,
        private val target: Label?,
        private val branch: Int,
    ) {
        fun wire() {
            val instruction = LabelInfo.getInstruction(
                target!!
            )
            if (instruction != null) source!!.addBranch(instruction, branch)
        }
    }

    init {
        currentLine = ISourceNode.UNKNOWN_LINE
        currentInsn = null
        instructions = HashMap()
        currentLabel = ArrayList(2)
        jumps = ArrayList()
        this.methodCoverage = methodCoverage
    }
}

