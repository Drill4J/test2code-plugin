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


import java.util.HashSet;
import java.util.Set;

import com.epam.drill.plugins.test2code.jvm.ClassCoverage;
import org.jacoco.core.internal.analysis.*;
import org.jacoco.core.internal.analysis.filter.Filters;
import org.jacoco.core.internal.analysis.filter.IFilter;
import org.jacoco.core.internal.analysis.filter.IFilterContext;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.MethodNode;

public class ClassAnalyzer extends ClassProbesVisitor implements IFilterContext {
    private final StringPool stringPool = new StringPool();

    private final Set<String> classAnnotations = new HashSet<>();

    private final Set<String> classAttributes = new HashSet<>();

    private final IFilter filter;
    private final ClassCoverage classCoverage;

    public ClassAnalyzer(ClassCoverage classCoverage) {
        this.classCoverage = classCoverage;
        this.filter = Filters.all();
    }

    @Override
    public void visit(final int version, final int access, final String name,
                      final String signature, final String superName,
                      final String[] interfaces) {
    }

    @Override
    public AnnotationVisitor visitAnnotation(final String desc,
                                             final boolean visible) {
        classAnnotations.add(desc);
        return super.visitAnnotation(desc, visible);
    }

    @Override
    public void visitAttribute(final Attribute attribute) {
        classAttributes.add(attribute.type);
    }

    @Override
    public void visitSource(final String source, final String debug) {
    }

    @Override
    public MethodProbesVisitor visitMethod(final int access,
                                           final String name,
                                           final String desc,
                                           final String signature,
                                           final String[] exceptions) {

        final InstructionsBuilder builder = new InstructionsBuilder(classCoverage.method(name, desc));

        return new MethodAnalyzer(builder) {

            @Override
            public void accept(final MethodNode methodNode,
                               final MethodVisitor methodVisitor) {
                super.accept(methodNode, methodVisitor);
                int total = builder.methodCoverage.getProbRangeToInstruction().values().stream().mapToInt(count -> count).sum();
                builder.methodCoverage.setTotalInstruction(total);
                addMethodCoverage(stringPool.get(name), stringPool.get(desc), stringPool.get(signature), builder, methodNode);
            }
        };
    }

    private void addMethodCoverage(final String name, final String desc,
                                   final String signature, final InstructionsBuilder icc,
                                   final MethodNode methodNode) {
        final MethodCoverageCalculator mcc = new MethodCoverageCalculator(
                icc.getInstructions());
        filter.filter(methodNode, this, mcc);

        final MethodCoverageImpl mc = new MethodCoverageImpl(name, desc,
                signature);
        mcc.calculate(mc);

        if (mc.containsCode()) {
        }

    }

    @Override
    public FieldVisitor visitField(final int access, final String name,
                                   final String desc, final String signature, final Object value) {
        return super.visitField(access, name, desc, signature, value);
    }

    @Override
    public void visitTotalProbeCount(final int count) {
        // nothing to do
    }

    // IFilterContext implementation

    public String getClassName() {
        return "";
    }

    public String getSuperClassName() {
        return "";
    }

    public Set<String> getClassAnnotations() {
        return classAnnotations;
    }

    public Set<String> getClassAttributes() {
        return classAttributes;
    }

    public String getSourceFileName() {
        return "";
    }

    public String getSourceDebugExtension() {
        return "";
    }

}
