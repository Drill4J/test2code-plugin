/**
 * Copyright 2020 - 2022 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.drill.jacoco;

import org.jacoco.core.internal.flow.*;
import org.jacoco.core.internal.instr.*;
import org.objectweb.asm.*;

/**
 * It duplicates from {@link MethodInstrumenter}.
 * This method adapter inserts probes as requested by the
 * {@link MethodProbesVisitor} events.
 */
public class DrillMethodInstrumenter extends MethodProbesVisitor {

    private final BooleanArrayProbeInserter probeInserter;

    /**
     * Create a new instrumenter instance for the given MethodVisitor.
     *
     * @param mv            next method visitor in the chain
     * @param probeInserter call-back to insert probes where required
     * @features Class Instrumentation, Probe inserter
     */
    public DrillMethodInstrumenter(final MethodVisitor mv,
                                   final BooleanArrayProbeInserter probeInserter) {
        super(mv);
        this.probeInserter = probeInserter;
    }

    // === IMethodProbesVisitor ===

    /**
     * @param probeId id of the probe to insert
     * @features Class Instrumentation, Probe inserter
     */
    @Override
    public void visitProbe(final int probeId) {
        probeInserter.insertProbe(probeId);
    }

    /**
     *
     * @param opcode
     *            the opcode of the instruction to be visited. This opcode is
     *            either IRETURN, LRETURN, FRETURN, DRETURN, ARETURN, RETURN or
     *            ATHROW.
     * @param probeId
     *            id of the probe
     * @features Class Instrumentation, Probe inserter
     */
    @Override
    public void visitInsnWithProbe(final int opcode, final int probeId) {
        probeInserter.insertProbe(probeId);
        mv.visitInsn(opcode);
    }

    /**
     *
     * @param opcode
     *            the opcode of the type instruction to be visited. This opcode
     *            is either IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE, IF_ICMPEQ,
     *            IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE,
     *            IF_ACMPEQ, IF_ACMPNE, GOTO, IFNULL or IFNONNULL.
     * @param label
     *            the operand of the instruction to be visited. This operand is
     *            a label that designates the instruction to which the jump
     *            instruction may jump.
     * @param probeId
     *            id of the probe
     * @param frame
     *            stackmap frame status after the execution of the jump
     *            instruction. The instance is only valid with the call of this
     *            method.
     * @features Class Instrumentation, Probe inserter
     */
    @Override
    public void visitJumpInsnWithProbe(final int opcode, final Label label,
                                       final int probeId, final IFrame frame) {
        if (opcode == Opcodes.GOTO) {
            probeInserter.insertProbe(probeId);
            mv.visitJumpInsn(Opcodes.GOTO, label);
        } else {
            final Label intermediate = new Label();
            mv.visitJumpInsn(getInverted(opcode), intermediate);
            probeInserter.insertProbe(probeId);
            mv.visitJumpInsn(Opcodes.GOTO, label);
            mv.visitLabel(intermediate);
            frame.accept(mv);
        }
    }

    private int getInverted(final int opcode) {
        switch (opcode) {
            case Opcodes.IFEQ:
                return Opcodes.IFNE;
            case Opcodes.IFNE:
                return Opcodes.IFEQ;
            case Opcodes.IFLT:
                return Opcodes.IFGE;
            case Opcodes.IFGE:
                return Opcodes.IFLT;
            case Opcodes.IFGT:
                return Opcodes.IFLE;
            case Opcodes.IFLE:
                return Opcodes.IFGT;
            case Opcodes.IF_ICMPEQ:
                return Opcodes.IF_ICMPNE;
            case Opcodes.IF_ICMPNE:
                return Opcodes.IF_ICMPEQ;
            case Opcodes.IF_ICMPLT:
                return Opcodes.IF_ICMPGE;
            case Opcodes.IF_ICMPGE:
                return Opcodes.IF_ICMPLT;
            case Opcodes.IF_ICMPGT:
                return Opcodes.IF_ICMPLE;
            case Opcodes.IF_ICMPLE:
                return Opcodes.IF_ICMPGT;
            case Opcodes.IF_ACMPEQ:
                return Opcodes.IF_ACMPNE;
            case Opcodes.IF_ACMPNE:
                return Opcodes.IF_ACMPEQ;
            case Opcodes.IFNULL:
                return Opcodes.IFNONNULL;
            case Opcodes.IFNONNULL:
                return Opcodes.IFNULL;
        }
        throw new IllegalArgumentException();
    }

    /**
     *
     *
     * @param min
     *            the minimum key value.
     * @param max
     *            the maximum key value.
     * @param dflt
     *            beginning of the default handler block.
     * @param labels
     *            beginnings of the handler blocks. <code>labels[i]</code> is
     *            the beginning of the handler block for the
     *            <code>min + i</code> key.
     * @param frame
     *            stackmap frame status after the execution of the switch
     *            instruction. The instance is only valid with the call of this
     *            method.
     * @features Class Instrumentation, Probe inserter
     */
    @Override
    public void visitTableSwitchInsnWithProbes(final int min, final int max,
                                               final Label dflt, final Label[] labels, final IFrame frame) {
        // 1. Calculate intermediate labels:
        LabelInfo.resetDone(dflt);
        LabelInfo.resetDone(labels);
        final Label newDflt = createIntermediate(dflt);
        final Label[] newLabels = createIntermediates(labels);
        mv.visitTableSwitchInsn(min, max, newDflt, newLabels);

        // 2. Insert probes:
        insertIntermediateProbes(dflt, labels, frame);
    }

    /**
     *
     * @param dflt
     *            beginning of the default handler block.
     * @param keys
     *            the values of the keys.
     * @param labels
     *            beginnings of the handler blocks. <code>labels[i]</code> is
     *            the beginning of the handler block for the
     *            <code>keys[i]</code> key.
     * @param frame
     *            stackmap frame status after the execution of the switch
     *            instruction. The instance is only valid with the call of this
     *            method.
     * @features Class Instrumentation, Probe inserter
     */
    @Override
    public void visitLookupSwitchInsnWithProbes(final Label dflt,
                                                final int[] keys, final Label[] labels, final IFrame frame) {
        // 1. Calculate intermediate labels:
        LabelInfo.resetDone(dflt);
        LabelInfo.resetDone(labels);
        final Label newDflt = createIntermediate(dflt);
        final Label[] newLabels = createIntermediates(labels);
        mv.visitLookupSwitchInsn(newDflt, keys, newLabels);

        // 2. Insert probes:
        insertIntermediateProbes(dflt, labels, frame);
    }

    private Label[] createIntermediates(final Label[] labels) {
        final Label[] intermediates = new Label[labels.length];
        for (int i = 0; i < labels.length; i++) {
            intermediates[i] = createIntermediate(labels[i]);
        }
        return intermediates;
    }

    private Label createIntermediate(final Label label) {
        final Label intermediate;
        if (LabelInfo.getProbeId(label) == LabelInfo.NO_PROBE) {
            intermediate = label;
        } else {
            if (LabelInfo.isDone(label)) {
                intermediate = LabelInfo.getIntermediateLabel(label);
            } else {
                intermediate = new Label();
                LabelInfo.setIntermediateLabel(label, intermediate);
                LabelInfo.setDone(label);
            }
        }
        return intermediate;
    }

    /**
     *
     * @param label
     * @param frame
     * @features Class Instrumentation, Probe inserter
     */
    private void insertIntermediateProbe(final Label label,
                                         final IFrame frame) {
        final int probeId = LabelInfo.getProbeId(label);
        if (probeId != LabelInfo.NO_PROBE && !LabelInfo.isDone(label)) {
            mv.visitLabel(LabelInfo.getIntermediateLabel(label));
            frame.accept(mv);
            probeInserter.insertProbe(probeId);
            mv.visitJumpInsn(Opcodes.GOTO, label);
            LabelInfo.setDone(label);
        }
    }

    private void insertIntermediateProbes(final Label dflt,
                                          final Label[] labels, final IFrame frame) {
        LabelInfo.resetDone(dflt);
        LabelInfo.resetDone(labels);
        insertIntermediateProbe(dflt, frame);
        for (final Label l : labels) {
            insertIntermediateProbe(l, frame);
        }
    }

}
