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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.epam.drill.plugins.test2code.jvm.MethodCoverage;
import org.jacoco.core.analysis.ISourceNode;
import org.jacoco.core.internal.analysis.filter.IFilterOutput;
import org.objectweb.asm.tree.AbstractInsnNode;

class MethodCoverageCalculator implements IFilterOutput {

    private final Map<AbstractInsnNode, Instruction> instructions;

    private final Set<AbstractInsnNode> ignored;

    private final Map<AbstractInsnNode, AbstractInsnNode> merged;

    private final Map<AbstractInsnNode, Set<AbstractInsnNode>> replacements;

    MethodCoverageCalculator(final Map<AbstractInsnNode, Instruction> instructions) {
        this.instructions = instructions;
        this.ignored = new HashSet<>();
        this.merged = new HashMap<>();
        this.replacements = new HashMap<>();
    }


    void calculate(final MethodCoverage coverage) {
        applyMerges();
        applyReplacements();
        ensureCapacity(coverage);

        for (final Entry<AbstractInsnNode, Instruction> entry : instructions
                .entrySet()) {
            if (!ignored.contains(entry.getKey())) {
                final Instruction instruction = entry.getValue();
                coverage.increment(instruction.getInstructionCounter(), instruction.getBranchCounter(), instruction.getLine());
            }
        }

        coverage.incrementMethodCounter();
    }

    private void applyMerges() {
        // Merge to the representative:
        for (final Entry<AbstractInsnNode, AbstractInsnNode> entry : merged
                .entrySet()) {
            final AbstractInsnNode node = entry.getKey();
            final Instruction instruction = instructions.get(node);
            final AbstractInsnNode representativeNode = findRepresentative(
                    node);
            ignored.add(node);
            instructions.put(representativeNode,
                    instructions.get(representativeNode).merge(instruction));
            entry.setValue(representativeNode);
        }

        // Get merged value back from representative
        for (final Entry<AbstractInsnNode, AbstractInsnNode> entry : merged
                .entrySet()) {
            instructions.put(entry.getKey(),
                    instructions.get(entry.getValue()));
        }
    }

    private void applyReplacements() {
        for (final Entry<AbstractInsnNode, Set<AbstractInsnNode>> entry : replacements
                .entrySet()) {
            final Set<AbstractInsnNode> replacements = entry.getValue();
            final List<Instruction> newBranches = new ArrayList<>(
                    replacements.size());
            for (final AbstractInsnNode b : replacements) {
                newBranches.add(instructions.get(b));
            }
            final AbstractInsnNode node = entry.getKey();
            instructions.put(node,
                    instructions.get(node).replaceBranches(newBranches));
        }
    }

    private void ensureCapacity(final MethodCoverage coverage) {
        // Determine line range:
        int firstLine = ISourceNode.UNKNOWN_LINE;
        int lastLine = ISourceNode.UNKNOWN_LINE;
        for (final Entry<AbstractInsnNode, Instruction> entry : instructions
                .entrySet()) {
            if (!ignored.contains(entry.getKey())) {
                final int line = entry.getValue().getLine();
                if (line != ISourceNode.UNKNOWN_LINE) {
                    if (firstLine > line
                            || lastLine == ISourceNode.UNKNOWN_LINE) {
                        firstLine = line;
                    }
                    if (lastLine < line) {
                        lastLine = line;
                    }
                }
            }
        }

        // Performance optimization to avoid incremental increase of line array:
        coverage.ensureCapacity(firstLine, lastLine);
    }

    private AbstractInsnNode findRepresentative(AbstractInsnNode i) {
        AbstractInsnNode r;
        while ((r = merged.get(i)) != null) {
            i = r;
        }
        return i;
    }

    // === IFilterOutput API ===

    public void ignore(final AbstractInsnNode fromInclusive,
                       final AbstractInsnNode toInclusive) {
        for (AbstractInsnNode i = fromInclusive; i != toInclusive; i = i
                .getNext()) {
            ignored.add(i);
        }
        ignored.add(toInclusive);
    }

    public void merge(AbstractInsnNode i1, AbstractInsnNode i2) {
        i1 = findRepresentative(i1);
        i2 = findRepresentative(i2);
        if (i1 != i2) {
            merged.put(i2, i1);
        }
    }

    public void replaceBranches(final AbstractInsnNode source,
                                final Set<AbstractInsnNode> newTargets) {
        replacements.put(source, newTargets);
    }

}
