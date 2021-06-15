/**
 * Copyright 2020 EPAM Systems
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
package instrumentation.epam.drill.test2code.jacoco;


import org.jacoco.core.internal.flow.IFrame;
import org.jacoco.core.internal.flow.LabelInfo;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

public class MethodAnalyzer extends MethodProbesVisitor {

    private final InstructionsBuilder builder;

    private AbstractInsnNode currentNode;

    MethodAnalyzer(final InstructionsBuilder builder) {
        this.builder = builder;
    }

    @Override
    public void accept(final MethodNode methodNode,
                       final MethodVisitor methodVisitor) {
        methodVisitor.visitCode();
        for (final TryCatchBlockNode n : methodNode.tryCatchBlocks) {
            n.accept(methodVisitor);
        }
        for (final AbstractInsnNode i : methodNode.instructions) {
            currentNode = i;
            i.accept(methodVisitor);//analyze each instruction
        }
        methodVisitor.visitEnd();
    }

    @Override
    public void visitLabel(final Label label) {
        builder.addLabel(label);
    }

    @Override
    public void visitLineNumber(final int line, final Label start) {
        builder.setCurrentLine(line);
    }

    @Override
    public void visitInsn(final int opcode) {
        builder.addInstruction(currentNode);
    }

    @Override
    public void visitIntInsn(final int opcode, final int operand) {
        builder.addInstruction(currentNode);
    }

    @Override
    public void visitVarInsn(final int opcode, final int var) {
        builder.addInstruction(currentNode);
    }

    @Override
    public void visitTypeInsn(final int opcode, final String type) {
        builder.addInstruction(currentNode);
    }

    @Override
    public void visitFieldInsn(final int opcode, final String owner,
                               final String name, final String desc) {
        builder.addInstruction(currentNode);
    }

    @Override
    public void visitMethodInsn(final int opcode, final String owner,
                                final String name, final String desc, final boolean itf) {
        builder.addInstruction(currentNode);
    }

    @Override
    public void visitInvokeDynamicInsn(final String name, final String desc,
                                       final Handle bsm, final Object... bsmArgs) {
        builder.addInstruction(currentNode);
    }

    @Override
    public void visitJumpInsn(final int opcode, final Label label) {
        builder.addInstruction(currentNode);
        builder.addJump(label, 1);
    }

    @Override
    public void visitLdcInsn(final Object cst) {
        builder.addInstruction(currentNode);
    }

    @Override
    public void visitIincInsn(final int var, final int increment) {
        builder.addInstruction(currentNode);
    }

    @Override
    public void visitTableSwitchInsn(final int min, final int max,
                                     final Label dflt, final Label... labels) {
        visitSwitchInsn(dflt, labels);
    }

    @Override
    public void visitLookupSwitchInsn(final Label dflt, final int[] keys,
                                      final Label[] labels) {
        visitSwitchInsn(dflt, labels);
    }

    private void visitSwitchInsn(final Label dflt, final Label[] labels) {
        builder.addInstruction(currentNode);
        LabelInfo.resetDone(labels);
        int branch = 0;
        builder.addJump(dflt, branch);
        LabelInfo.setDone(dflt);
        for (final Label l : labels) {
            if (!LabelInfo.isDone(l)) {
                branch++;
                builder.addJump(l, branch);
                LabelInfo.setDone(l);
            }
        }
    }

    @Override
    public void visitMultiANewArrayInsn(final String desc, final int dims) {
        builder.addInstruction(currentNode);
    }

    @Override
    public void visitProbe(final int probeId) {
        System.out.println("visitProbe probeId="+probeId);
        builder.addProbe(probeId, 0, false);//first
        builder.noSuccessor();
    }

    @Override
    public void visitJumpInsnWithProbe(final int opcode, final Label label,
                                       final int probeId, final IFrame frame) {
        builder.addInstruction(currentNode);
        System.out.println("visitJumpInsnWithProbe probeId=" + probeId + "; opcode=" + opcode);
        builder.addProbe(probeId, 1, true);
    }

    @Override
    public void visitInsnWithProbe(final int opcode, final int probeId) {
        builder.addInstruction(currentNode);
        System.out.println("visitInsnWithProbe probeId=" + probeId + "; opcode=" + opcode);
        builder.addProbe(probeId, 0, false);
    }

    @Override
    public void visitTableSwitchInsnWithProbes(final int min, final int max,
                                               final Label dflt, final Label[] labels, final IFrame frame) {
        visitSwitchInsnWithProbes(dflt, labels);
    }

    @Override
    public void visitLookupSwitchInsnWithProbes(final Label dflt,
                                                final int[] keys, final Label[] labels, final IFrame frame) {
        visitSwitchInsnWithProbes(dflt, labels);
    }

    private void visitSwitchInsnWithProbes(final Label dflt,
                                           final Label[] labels) {
        builder.addInstruction(currentNode);
        LabelInfo.resetDone(dflt);
        LabelInfo.resetDone(labels);
        int branch = 0;
        visitSwitchTarget(dflt, branch);
        for (final Label l : labels) {
            branch++;
            visitSwitchTarget(l, branch);
        }
    }

    private void visitSwitchTarget(final Label label, final int branch) {
        final int id = LabelInfo.getProbeId(label);
        if (!LabelInfo.isDone(label)) {
            if (id == LabelInfo.NO_PROBE) {
                builder.addJump(label, branch);
            } else {
                builder.addProbe(id, branch, false);
            }
            LabelInfo.setDone(label);
        }
    }

}
