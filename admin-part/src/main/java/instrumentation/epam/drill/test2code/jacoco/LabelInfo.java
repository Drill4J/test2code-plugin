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


import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Label;

@SuppressWarnings("unused")
public final class LabelInfo {

    public static final int NO_PROBE = -1;

    private boolean target = false;

    private boolean multiTarget = false;

    private boolean successor = false;

    private boolean methodInvocationLine = false;

    private boolean done = false;

    private int probeid = NO_PROBE;

    private Label intermediate = null;

    private Instruction instruction = null;

    private LabelInfo() {
    }

    public static void setTarget(final Label label) {
        final LabelInfo info = create(label);
        if (info.target || info.successor) {
            info.multiTarget = true;
        } else {
            info.target = true;
        }
    }

    public static void setSuccessor(final Label label) {
        final LabelInfo info = create(label);
        info.successor = true;
        if (info.target) {
            info.multiTarget = true;
        }
    }

    public static boolean isMultiTarget(final Label label) {
        final LabelInfo info = get(label);
        return info != null && info.multiTarget;
    }
    public static boolean isSuccessor(final Label label) {
        final LabelInfo info = get(label);
        return info != null && info.successor;
    }

    public static void setMethodInvocationLine(final Label label) {
        create(label).methodInvocationLine = true;
    }

    public static boolean isMethodInvocationLine(final Label label) {
        final LabelInfo info = get(label);
        return info != null && info.methodInvocationLine;
    }
    public static boolean needsProbe(final Label label) {
        final LabelInfo info = get(label);
        return info != null && info.successor
                && (info.multiTarget || info.methodInvocationLine);
    }
    public static void setDone(final Label label) {
        create(label).done = true;
    }

    public static void resetDone(final Label label) {
        final LabelInfo info = get(label);
        if (info != null) {
            info.done = false;
        }
    }

    public static void resetDone(final Label[] labels) {
        for (final Label label : labels) {
            resetDone(label);
        }
    }

    public static boolean isDone(final Label label) {
        final LabelInfo info = get(label);
        return info != null && info.done;
    }

    public static void setProbeId(final Label label, final int id) {
        create(label).probeid = id;
    }

    public static int getProbeId(final Label label) {
        final LabelInfo info = get(label);
        return info == null ? NO_PROBE : info.probeid;
    }

    public static void setIntermediateLabel(final Label label,
                                            final Label intermediate) {
        create(label).intermediate = intermediate;
    }

    public static Label getIntermediateLabel(final Label label) {
        final LabelInfo info = get(label);
        return info == null ? null : info.intermediate;
    }


    public static void setInstruction(final Label label,
                                      final Instruction instruction) {
        create(label).instruction = instruction;
    }

    @Nullable
    public static Instruction getInstruction(final Label label) {
        final LabelInfo info = get(label);
        return info == null ? null : info.instruction;
    }

    private static LabelInfo get(final Label label) {
        final Object info = label.info;
        return info instanceof LabelInfo ? (LabelInfo) info : null;
    }

    private static LabelInfo create(final Label label) {
        LabelInfo info = get(label);
        if (info == null) {
            info = new LabelInfo();
            label.info = info;
        }
        return info;
    }

}
