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


import org.jacoco.core.internal.flow.IProbeIdGenerator;
import org.jacoco.core.internal.instr.InstrSupport;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.commons.AnalyzerAdapter;

public class ClassProbesAdapter extends ClassVisitor implements IProbeIdGenerator {

    private static final MethodProbesVisitor EMPTY_METHOD_PROBES_VISITOR = new MethodProbesVisitor() {
    };

    private final ClassProbesVisitor cv;

    private final boolean trackFrames;

    private int counter = 0;

    private String name;

    public ClassProbesAdapter(final ClassProbesVisitor cv, final boolean trackFrames) {
        super(InstrSupport.ASM_API_VERSION, cv);
        this.cv = cv;
        this.trackFrames = trackFrames;
    }

    @Override
    public void visit(final int version, final int access, final String name,
                      final String signature, final String superName,
                      final String[] interfaces) {
        this.name = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public final MethodVisitor visitMethod(final int access, final String name,
                                           final String desc, final String signature,
                                           final String[] exceptions) {
        final MethodProbesVisitor methodProbes;
        final MethodProbesVisitor mv = cv.visitMethod(access, name, desc,
                signature, exceptions);
        if (mv == null) {
            // We need to visit the method in any case, otherwise probe ids
            // are not reproducible
            methodProbes = EMPTY_METHOD_PROBES_VISITOR;
        } else {
            methodProbes = mv;
        }
        return new MethodSanitizer(null, access, name, desc, signature, exceptions) {

            @Override
            public void visitEnd() {
                super.visitEnd();
                LabelFlowAnalyzer.markLabels(this);
                final MethodProbesAdapter probesAdapter = new MethodProbesAdapter(methodProbes, ClassProbesAdapter.this);
                if (trackFrames) {
                    final AnalyzerAdapter analyzer = new AnalyzerAdapter(
                            ClassProbesAdapter.this.name, access, name, desc,
                            probesAdapter);
                    probesAdapter.setAnalyzer(analyzer);
                    methodProbes.accept(this, analyzer);
                } else {
                    methodProbes.accept(this, probesAdapter);
                }
            }
        };
    }

    @Override
    public void visitEnd() {
        cv.visitTotalProbeCount(counter);
        super.visitEnd();
    }

    public int nextId() {
        return counter++;
    }

}
