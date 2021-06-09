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
import org.jacoco.core.internal.instr.InstrSupport;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.MethodNode;

public abstract class MethodProbesVisitor extends MethodVisitor {

    public MethodProbesVisitor() {
        this(null);
    }
    public MethodProbesVisitor(final MethodVisitor mv) {
        super(InstrSupport.ASM_API_VERSION, mv);
    }

    public void visitProbe(final int probeId) {
    }
    @SuppressWarnings("unused")
    public void visitJumpInsnWithProbe(final int opcode, final Label label,
                                       final int probeId, final IFrame frame) {
    }

    @SuppressWarnings("unused")
    public void visitInsnWithProbe(final int opcode, final int probeId) {
    }

    @SuppressWarnings("unused")
    public void visitTableSwitchInsnWithProbes(final int min, final int max,
                                               final Label dflt, final Label[] labels, final IFrame frame) {
    }

    /**
     * Visits a LOOKUPSWITCH instruction with optional probes for each target
     * label. Implementations can be optimized based on the fact that the same
     * target labels will always have the same probe id within a call to this
     * method. The probe id for each label can be obtained with
     * {@link LabelInfo#getProbeId(Label)}.
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
     * @see MethodVisitor#visitLookupSwitchInsn(Label, int[], Label[])
     */
    @SuppressWarnings("unused")
    public void visitLookupSwitchInsnWithProbes(final Label dflt,
                                                final int[] keys, final Label[] labels, final IFrame frame) {
    }

    /**
     * This method can be overwritten to hook into the process of emitting the
     * instructions of this method as <code>visitX()</code> events.
     *
     * @param methodNode
     *            the content to emit
     * @param methodVisitor
     *            A visitor to emit the content to. Note that this is not
     *            necessarily this visitor instance but some wrapper which
     *            calculates the probes.
     */
    public void accept(final MethodNode methodNode,
                       final MethodVisitor methodVisitor) {
        methodNode.accept(methodVisitor);
    }

}
