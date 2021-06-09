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


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.epam.drill.plugins.test2code.jvm.MethodCoverage;
import org.jacoco.core.analysis.ISourceNode;
import org.objectweb.asm.Label;
import org.objectweb.asm.tree.AbstractInsnNode;

class InstructionsBuilder {

    private int currentLine;
    private Instruction currentInsn;
    private final Map<AbstractInsnNode, Instruction> instructions;
    private final List<Label> currentLabel;
    private final List<Jump> jumps;
    MethodCoverage methodCoverage;

    int currentProbe = 0;
    int instructionCounter = 0;

    InstructionsBuilder(MethodCoverage methodCoverage) {
        this.currentLine = ISourceNode.UNKNOWN_LINE;
        this.currentInsn = null;
        this.instructions = new HashMap<>();
        this.currentLabel = new ArrayList<>(2);
        this.jumps = new ArrayList<>();
        this.methodCoverage = methodCoverage;
    }

    void setCurrentLine(final int line) {
        currentLine = line;
    }

    void addLabel(final Label label) {
        currentLabel.add(label);
        if (!LabelInfo.isSuccessor(label)) {
            noSuccessor();
        }
    }

    void addInstruction(final AbstractInsnNode node) {
        instructionCounter++;
        final Instruction insn = new Instruction(currentLine, methodCoverage, currentProbe);
        final int labelCount = currentLabel.size();
        if (labelCount > 0) {
            for (int i = labelCount; --i >= 0; ) {
                LabelInfo.setInstruction(currentLabel.get(i), insn);
            }
            currentLabel.clear();
        }
        if (currentInsn != null) {
            currentInsn.addBranch(insn, 0);
        }
        currentInsn = insn;
        instructions.put(node, insn);
    }

    void noSuccessor() {
        currentInsn = null;
    }

    void addJump(final Label target, final int branch) {
        jumps.add(new Jump(currentInsn, target, branch));
    }

    void addProbe(final int probeId, final int branch) {
        if (currentInsn.addBranch(probeId, instructionCounter)) {
            instructionCounter = 0;
        }
    }

    Map<AbstractInsnNode, Instruction> getInstructions() {
        for (final Jump j : jumps) {
            j.wire();
        }

        return instructions;
    }

    private static class Jump {

        private final Instruction source;
        private final Label target;
        private final int branch;

        Jump(final Instruction source, final Label target, final int branch) {
            this.source = source;
            this.target = target;
            this.branch = branch;
        }

        void wire() {
            final Instruction instruction = LabelInfo.getInstruction(target);
            if (instruction != null)
                source.addBranch(instruction, branch);
        }

    }

}
