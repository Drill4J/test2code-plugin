/**
 * Copyright 2020 - 2022 EPAM Systems
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.drill.jacoco;

import java.util.HashMap;
import java.util.Map;

import org.jacoco.core.internal.flow.IFrame;
import org.jacoco.core.internal.flow.IProbeIdGenerator;
import org.jacoco.core.internal.flow.LabelInfo;
import org.jacoco.core.internal.flow.MethodProbesVisitor;
import org.jacoco.core.internal.instr.InstrSupport;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.AnalyzerAdapter;

/**
 * Adapter that creates additional visitor events for probes to be inserted into
 * a method.
 */
public final class MethodProbesAdapter extends MethodVisitor {

	private final MethodProbesVisitor probesVisitor;

	private final IProbeIdGenerator idGenerator;

	private AnalyzerAdapter analyzer;

	private final Map<Label, Label> tryCatchProbeLabels;

    /**
     * Create a new adapter instance.
     *
     * @param probesVisitor visitor to delegate to
     * @param idGenerator   generator for unique probe ids
     */
    public MethodProbesAdapter(final MethodProbesVisitor probesVisitor,
                               final IProbeIdGenerator idGenerator) {
        super(InstrSupport.ASM_API_VERSION, probesVisitor);
        this.probesVisitor = probesVisitor;
        this.idGenerator = idGenerator;
        this.tryCatchProbeLabels = new HashMap<Label, Label>();
    }

    /**
     * If an analyzer is set {@link IFrame} handles are calculated and emitted
     * to the probes methods.
     *
     * @param analyzer optional analyzer to set
     */
    public void setAnalyzer(final AnalyzerAdapter analyzer) {
        this.analyzer = analyzer;
    }

    /**
     * Try catch block instruction
     *
     * @param start   the beginning of the exception handler's scope (inclusive).
     * @param end     the end of the exception handler's scope (exclusive).
     * @param handler the beginning of the exception handler's code.
     * @param type    the internal name of the type of exceptions handled by the handler
     * @features Class Instrumentation, Probe inserter
     */
    @Override
    public void visitTryCatchBlock(final Label start, final Label end,
                                   final Label handler, final String type) {
        probesVisitor.visitTryCatchBlock(getTryCatchLabel(start),
                getTryCatchLabel(end), handler, type);
    }

    private Label getTryCatchLabel(Label label) {
        if (tryCatchProbeLabels.containsKey(label)) {
            label = tryCatchProbeLabels.get(label);
        } else if (LabelInfo.needsProbe(label)) {
            // If a probe will be inserted before the label, we'll need to use a
            // different label to define the range of the try-catch block.
            final Label probeLabel = new Label();
            LabelInfo.setSuccessor(probeLabel);
            tryCatchProbeLabels.put(label, probeLabel);
            label = probeLabel;
        }
        return label;
    }

    /**
     * Visits a label.
     *
     * @param label designates the instruction that will be visited just after it.
     * @features Class Instrumentation, Probe inserter
     */
    @Override
    public void visitLabel(final Label label) {
        if (LabelInfo.needsProbe(label)) {
            if (tryCatchProbeLabels.containsKey(label)) {
                probesVisitor.visitLabel(tryCatchProbeLabels.get(label));
            }
            probesVisitor.visitProbe(idGenerator.nextId());
        }
        probesVisitor.visitLabel(label);
    }

    /**
     * Visit a zero operand instruction.
     *
     * @param opcode type of instruction
     * @features Class Instrumentation, Probe inserter
     */
    @Override
    public void visitInsn(final int opcode) {
        switch (opcode) {
            case Opcodes.IRETURN:
            case Opcodes.LRETURN:
            case Opcodes.FRETURN:
            case Opcodes.DRETURN:
            case Opcodes.ARETURN:
            case Opcodes.RETURN:
            case Opcodes.ATHROW:
                probesVisitor.visitInsnWithProbe(opcode, idGenerator.nextId());
                break;
            default:
                probesVisitor.visitInsn(opcode);
                break;
        }
    }

    /**
     * A jump instruction is an instruction that may jump to another instruction.
     *
     * @param opcode type of instruction
     * @param label  This operand is a label that designates the instruction to which the jump instruction may jump.
     */
    @Override
    public void visitJumpInsn(final int opcode, final Label label) {
        if (LabelInfo.isMultiTarget(label)) {
            probesVisitor.visitJumpInsnWithProbe(opcode, label,
                    idGenerator.nextId(), frame(jumpPopCount(opcode)));
        } else {
            probesVisitor.visitJumpInsn(opcode, label);
        }
    }

    /**
     * Map default OpCodes const to pop count
     *
     * @param opcode value of OpCode
     * @return pop count
     */
    private int jumpPopCount(final int opcode) {
        switch (opcode) {
            case Opcodes.GOTO:
                return 0;
            case Opcodes.IFEQ:
            case Opcodes.IFNE:
            case Opcodes.IFLT:
            case Opcodes.IFGE:
            case Opcodes.IFGT:
            case Opcodes.IFLE:
            case Opcodes.IFNULL:
            case Opcodes.IFNONNULL:
                return 1;
            default: // IF_CMPxx and IF_ACMPxx
                return 2;
        }
    }

    /**
     * Lookup Switch instruction visit
     *
     * @param dflt   lablel of default value
     * @param keys   array of the keys
     * @param labels beginnings of the handler blocks
     * @features Class Instrumentation, Probe inserter
     */
    @Override
    public void visitLookupSwitchInsn(final Label dflt, final int[] keys,
                                      final Label[] labels) {
        if (markLabels(dflt, labels)) {
            probesVisitor.visitLookupSwitchInsnWithProbes(dflt, keys, labels,
                    frame(1));
        } else {
            probesVisitor.visitLookupSwitchInsn(dflt, keys, labels);
        }
    }

    /**
     * Table Switch instruction
     *
     * @param min    Min value
     * @param max    Max value
     * @param dflt   beginning of the default handler block.
     * @param labels beginnings of the handler blocks
     * @features Class Instrumentation, Probe inserter
     */
    @Override
    public void visitTableSwitchInsn(final int min, final int max,
                                     final Label dflt, final Label... labels) {
        if (markLabels(dflt, labels)) {
            probesVisitor.visitTableSwitchInsnWithProbes(min, max, dflt, labels,
                    frame(1));
        } else {
            probesVisitor.visitTableSwitchInsn(min, max, dflt, labels);
        }
    }

    /**
     * Set probe at provided labels
     *
     * @param dflt   label of default value
     * @param labels beginnings of the handler blocks
     * @return probe set up result
     */
    private boolean markLabels(final Label dflt, final Label[] labels) {
        boolean probe = false;
        LabelInfo.resetDone(labels);
        if (LabelInfo.isMultiTarget(dflt)) {
            LabelInfo.setProbeId(dflt, idGenerator.nextId());
            probe = true;
        }
        LabelInfo.setDone(dflt);
        for (final Label l : labels) {
            if (LabelInfo.isMultiTarget(l) && !LabelInfo.isDone(l)) {
                LabelInfo.setProbeId(l, idGenerator.nextId());
                probe = true;
            }
            LabelInfo.setDone(l);
        }
        return probe;
    }

    /**
     * @param popCount
     * @return Frame of the given popCount
     */
    private IFrame frame(final int popCount) {
        return DrillFrameSnapshot.create(analyzer, popCount);
    }

}
