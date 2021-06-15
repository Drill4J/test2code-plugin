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


import com.epam.drill.plugins.test2code.jvm.MethodCoverage;
import org.jacoco.core.analysis.ICounter;
import org.jacoco.core.internal.analysis.CounterImpl;

import java.util.BitSet;
import java.util.Collection;

public class Instruction {

    private final int line;

    private int branches;

    private final BitSet coveredBranches;

    private Instruction predecessor;

    private int predecessorBranch;
    private final int currentProbe;
    MethodCoverage methodCoverage;

    public Instruction(final int line, MethodCoverage methodCoverage, int currentProbe) {
        this.line = line;
        this.branches = 0;
        this.coveredBranches = new BitSet();
        //this is new:
        this.methodCoverage = methodCoverage;
        this.currentProbe = currentProbe;
    }

    public void addBranch(final Instruction target, final int branch) {
        branches++;
        target.predecessor = this;
        target.predecessorBranch = branch;
        if (!target.coveredBranches.isEmpty()) {
            propagateExecutedBranch(this, branch);
        }
    }

// original:
    public void addBranch(final boolean executed, final int branch) {
        branches++;
        if (executed) {
            propagateExecutedBranch(this, branch);
        }
    }

    public boolean addBranch(int probeId, int instructionCounter, int branch, boolean b) {
        branches++;

        final Integer integer = methodCoverage.getProbRangeToInstruction().get(probeId);
        if (methodCoverage.getFirstProbe() == -1) {
            //todo take first probe of the method?
            methodCoverage.setFirstProbe(probeId);
            System.out.println();
            propagateExecutedBranch(this, branch);
        }
        System.out.println("addBranch probeId=" + probeId + "; count=" + instructionCounter + "; branch=" + branch + "; b=" + b);
        if (branch == 0) {
            System.out.println("branch==0");
//            propagateExecutedBranch(this, branch);
        }
        if (b) { //todo can we send smth to add default coverage?
            System.out.println("b==true");
//            propagateExecutedBranch(this, branch);
        }
        if (integer == null) {
            System.out.println("probeId=" + probeId + "; count=" + instructionCounter +"\n");
            methodCoverage.getProbRangeToInstruction().put(probeId, instructionCounter);
            return true;
        }
        return false;
    }

    private static void propagateExecutedBranch(Instruction insn, int branch) {
        // No recursion here, as there can be very long chains of instructions
        while (insn != null) {
            if (!insn.coveredBranches.isEmpty()) {
                insn.coveredBranches.set(branch);
                break;
            }
            insn.coveredBranches.set(branch);
            branch = insn.predecessorBranch;
            insn = insn.predecessor;
        }
    }

    public int getLine() {
        return line;
    }

    public Instruction merge(final Instruction other) {
        final Instruction result = new Instruction(this.line, methodCoverage, currentProbe);
        result.branches = this.branches;
        result.coveredBranches.or(this.coveredBranches);
        result.coveredBranches.or(other.coveredBranches);
        return result;
    }

    public Instruction replaceBranches(
            final Collection<Instruction> newBranches) {
        final Instruction result = new Instruction(this.line, methodCoverage, currentProbe);
        result.branches = newBranches.size();
        int idx = 0;
        for (final Instruction b : newBranches) {
            if (!b.coveredBranches.isEmpty()) {
                result.coveredBranches.set(idx++);
            }
        }
        return result;
    }


    public ICounter getInstructionCounter() {
        return coveredBranches.isEmpty() ? CounterImpl.COUNTER_1_0
                : CounterImpl.COUNTER_0_1;
    }

    public ICounter getBranchCounter() {
        if (branches < 2) {
            return CounterImpl.COUNTER_0_0;
        }
//        todo
//        final int covered = coveredBranches.cardinality();
        final int covered = Math.max(0, coveredBranches.cardinality() - 1);
        return CounterImpl.getInstance(branches - covered, covered);
    }

}
