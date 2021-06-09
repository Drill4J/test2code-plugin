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


import org.jacoco.core.internal.instr.InstrSupport;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodNode;
public final class LabelFlowAnalyzer extends MethodVisitor {
    public static void markLabels(final MethodNode method) {
        // We do not use the accept() method as ASM resets labels after every
        // call to accept()
        final MethodVisitor lfa = new LabelFlowAnalyzer();
        for (int i = method.tryCatchBlocks.size(); --i >= 0;) {
            method.tryCatchBlocks.get(i).accept(lfa);
        }
        method.instructions.accept(lfa);
    }

    boolean successor = false;
    boolean first = true;
    Label lineStart = null;
    public LabelFlowAnalyzer() {
        super(InstrSupport.ASM_API_VERSION);
    }

    @Override
    public void visitTryCatchBlock(final Label start, final Label end,
                                   final Label handler, final String type) {
        // Enforce probe at the beginning of the block. Assuming the start of
        // the block already is successor of some other code, adding a target
        // makes the start a multitarget. However, if the start of the block
        // also is the start of the method, no probe will be added.
        LabelInfo.setTarget(start);

        // Mark exception handler as possible target of the block
        LabelInfo.setTarget(handler);
    }

    @Override
    public void visitJumpInsn(final int opcode, final Label label) {
        LabelInfo.setTarget(label);
        if (opcode == Opcodes.JSR) {
            throw new AssertionError("Subroutines not supported.");
        }
        successor = opcode != Opcodes.GOTO;
        first = false;
    }

    @Override
    public void visitLabel(final Label label) {
        if (first) {
            LabelInfo.setTarget(label);
        }
        if (successor) {
            LabelInfo.setSuccessor(label);
        }
    }

    @Override
    public void visitLineNumber(final int line, final Label start) {
        lineStart = start;
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
        LabelInfo.resetDone(dflt);
        LabelInfo.resetDone(labels);
        setTargetIfNotDone(dflt);
        for (final Label l : labels) {
            setTargetIfNotDone(l);
        }
        successor = false;
        first = false;
    }

    private static void setTargetIfNotDone(final Label label) {
        if (!LabelInfo.isDone(label)) {
            LabelInfo.setTarget(label);
            LabelInfo.setDone(label);
        }
    }

    @Override
    public void visitInsn(final int opcode) {
        switch (opcode) {
            case Opcodes.RET:
                throw new AssertionError("Subroutines not supported.");
            case Opcodes.IRETURN:
            case Opcodes.LRETURN:
            case Opcodes.FRETURN:
            case Opcodes.DRETURN:
            case Opcodes.ARETURN:
            case Opcodes.RETURN:
            case Opcodes.ATHROW:
                successor = false;
                break;
            default:
                successor = true;
                break;
        }
        first = false;
    }

    @Override
    public void visitIntInsn(final int opcode, final int operand) {
        successor = true;
        first = false;
    }

    @Override
    public void visitVarInsn(final int opcode, final int var) {
        successor = true;
        first = false;
    }

    @Override
    public void visitTypeInsn(final int opcode, final String type) {
        successor = true;
        first = false;
    }

    @Override
    public void visitFieldInsn(final int opcode, final String owner,
                               final String name, final String desc) {
        successor = true;
        first = false;
    }

    @Override
    public void visitMethodInsn(final int opcode, final String owner,
                                final String name, final String desc, final boolean itf) {
        successor = true;
        first = false;
        markMethodInvocationLine();
    }

    @Override
    public void visitInvokeDynamicInsn(final String name, final String desc,
                                       final Handle bsm, final Object... bsmArgs) {
        successor = true;
        first = false;
        markMethodInvocationLine();
    }

    private void markMethodInvocationLine() {
        if (lineStart != null) {
            LabelInfo.setMethodInvocationLine(lineStart);
        }
    }

    @Override
    public void visitLdcInsn(final Object cst) {
        successor = true;
        first = false;
    }

    @Override
    public void visitIincInsn(final int var, final int increment) {
        successor = true;
        first = false;
    }

    @Override
    public void visitMultiANewArrayInsn(final String desc, final int dims) {
        successor = true;
        first = false;
    }

}
