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
import com.epam.drill.plugins.test2code.jvm.MethodCoverage;
import org.jacoco.core.analysis.ICoverageNode;
import org.jacoco.core.internal.analysis.*;
import org.jacoco.core.internal.analysis.filter.Filters;
import org.jacoco.core.internal.analysis.filter.IFilter;
import org.jacoco.core.internal.analysis.filter.IFilterContext;
import org.jacoco.core.internal.instr.InstrSupport;
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
        this.classCoverage = classCoverage;//ClassCoverageImpl
        this.filter = Filters.all();
    }

    @Override
    public void visit(final int version, final int access, final String name,
                      final String signature, final String superName,
                      final String[] interfaces) {
//        coverage.setSignature(stringPool.get(signature));
//        coverage.setSuperName(stringPool.get(superName));
//        coverage.setInterfaces(stringPool.get(interfaces));
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
//        coverage.setSourceFileName(stringPool.get(source));
//        sourceDebugExtension = debug;
    }

    @Override
    public MethodProbesVisitor visitMethod(final int access,
                                           final String name,
                                           final String desc,
                                           final String signature,
                                           final String[] exceptions) {

        final InstructionsBuilder builder = new InstructionsBuilder(classCoverage.method(name, desc, signature));

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
//signature
        final MethodCoverage mc = new MethodCoverage(name, desc, signature, ICoverageNode.ElementType.METHOD);
        mcc.calculate(mc);

        if (mc.containsCode()) {
            // Only consider methods that actually contain code
            classCoverage.increment(mc);
            classCoverage.addMethod(mc);
            mc.fixRange();
        }

    }

    @Override
    public FieldVisitor visitField(final int access, final String name,
                                   final String desc, final String signature, final Object value) {
//        InstrSupport.assertNotInstrumented(name, coverage.getName());
        return super.visitField(access, name, desc, signature, value);
    }

    @Override
    public void visitTotalProbeCount(final int count) {
        // nothing to do
    }

    // IFilterContext implementation

    public String getClassName() {
//        coverage.getName()
        return "";
    }

    public String getSuperClassName() {
//        coverage.getSuperName()
        return "";
    }

    public Set<String> getClassAnnotations() {
        return classAnnotations;
    }

    public Set<String> getClassAttributes() {
        return classAttributes;
    }

    public String getSourceFileName() {
//        		return coverage.getSourceFileName();
        return "";
    }

    public String getSourceDebugExtension() {
        return "";
    }

}
