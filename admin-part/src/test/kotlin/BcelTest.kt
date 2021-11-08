package com.epam.drill.plugins.test2code

import org.apache.bcel.classfile.*
import org.apache.bcel.classfile.Deprecated
import org.apache.bcel.classfile.EmptyVisitor
import org.apache.bcel.classfile.Method
import org.apache.bcel.classfile.Visitor
import org.apache.bcel.generic.*
import org.apache.bcel.generic.FieldOrMethod
import org.junit.jupiter.api.*
import java.io.*

class BcelTest {

//    @Test
//    fun bcelTest() {
//        val classParser =
//            ClassParser("C:\\Users\\Viktoriia_Lebedeva\\IdeaProjects\\spring-petclinic\\target\\classes\\org\\springframework\\samples\\petclinic\\owner\\OwnerController.class")
//        val clazz = classParser.parse()
//        clazz.methods[10].code.accept(object : EmptyVisitor() {
//            override fun visitCode(obj: Code?) {
//                val instructionList = InstructionList(obj?.code)
//                instructionList.forEach {
//
//                }
//            }
//
//
//            override fun visitCodeException(obj: CodeException?) {
//                println("visit")
//            }
//
//            override fun visitConstantClass(obj: ConstantClass?) {
//                println("visit")
//            }
//
//            override fun visitConstantDouble(obj: ConstantDouble?) {
//                println("visit")
//            }
//
//            override fun visitConstantFieldref(obj: ConstantFieldref?) {
//                println("visit")
//            }
//
//            override fun visitConstantFloat(obj: ConstantFloat?) {
//                println("visit")
//            }
//
//            override fun visitConstantInteger(obj: ConstantInteger?) {
//                println("visit")
//            }
//
//            override fun visitConstantInterfaceMethodref(obj: ConstantInterfaceMethodref?) {
//                println("visit")
//            }
//
//            override fun visitConstantInvokeDynamic(obj: ConstantInvokeDynamic?) {
//
//                println("visit")
//            }
//
//            override fun visitConstantLong(obj: ConstantLong?) {
//                println("visit")
//            }
//
//            override fun visitConstantMethodref(obj: ConstantMethodref?) {
//                println("visit")
//            }
//
//            override fun visitConstantNameAndType(obj: ConstantNameAndType?) {
//                println("visit")
//            }
//
//            override fun visitConstantPool(obj: ConstantPool?) {
//                println("visit")
//            }
//
//            override fun visitConstantString(obj: ConstantString?) {
//                println("visit")
//            }
//
//            override fun visitConstantUtf8(obj: ConstantUtf8?) {
//                println("visit")
//            }
//
//            override fun visitConstantValue(obj: ConstantValue?) {
//                println("visit")
//            }
//
//            override fun visitDeprecated(obj: Deprecated?) {
//                println("visit")
//            }
//
//            override fun visitExceptionTable(obj: ExceptionTable?) {
//                println("visit")
//            }
//
//            override fun visitField(obj: Field?) {
//                println("visit")
//            }
//
//            override fun visitInnerClass(obj: InnerClass?) {
//                println("visit")
//            }
//
//            override fun visitInnerClasses(obj: InnerClasses?) {
//                println("visit")
//            }
//
//            override fun visitJavaClass(obj: JavaClass?) {
//                println("visit")
//            }
//
//            override fun visitLineNumber(obj: LineNumber?) {
//                println("visit")
//            }
//
//            override fun visitLineNumberTable(obj: LineNumberTable?) {
//                println("visit")
//            }
//
//            override fun visitLocalVariable(obj: LocalVariable?) {
//                println("visit")
//            }
//
//            override fun visitLocalVariableTable(obj: LocalVariableTable?) {
//                println("visit")
//            }
//
//            override fun visitMethod(obj: Method?) {
//                println("visit")
//            }
//
//            override fun visitSignature(obj: Signature?) {
//                println("visit")
//            }
//
//            override fun visitSourceFile(obj: SourceFile?) {
//                println("visit")
//            }
//
//            override fun visitSynthetic(obj: Synthetic?) {
//                println("visit")
//            }
//
//            override fun visitUnknown(obj: Unknown?) {
//                println("visit")
//            }
//
//            override fun visitStackMap(obj: StackMap?) {
//                println("visit")
//            }
//
//            override fun visitStackMapEntry(obj: StackMapEntry?) {
//                println("visit")
//            }
//
//            override fun visitAnnotation(obj: Annotations?) {
//                println("visit")
//            }
//
//            override fun visitParameterAnnotation(obj: ParameterAnnotations?) {
//                println("visit")
//            }
//
//            override fun visitAnnotationEntry(obj: AnnotationEntry?) {
//                println("visit")
//            }
//
//            override fun visitAnnotationDefault(obj: AnnotationDefault?) {
//                println("visit")
//            }
//
//            override fun visitLocalVariableTypeTable(obj: LocalVariableTypeTable?) {
//                println("visit")
//            }
//
//            override fun visitEnclosingMethod(obj: EnclosingMethod?) {
//                println("visit")
//            }
//
//            override fun visitBootstrapMethods(obj: BootstrapMethods?) {
//                println("visit")
//            }
//
//            override fun visitMethodParameters(obj: MethodParameters?) {
//                println("visit")
//            }
//
//            override fun visitConstantMethodType(obj: ConstantMethodType?) {
//                println("visit")
//            }
//
//            override fun visitConstantMethodHandle(obj: ConstantMethodHandle?) {
//                println("visit")
//            }
//
//            override fun visitParameterAnnotationEntry(obj: ParameterAnnotationEntry?) {
//                println("visit")
//            }
//
//            override fun visitConstantPackage(constantPackage: ConstantPackage?) {
//                println("visit")
//            }
//
//            override fun visitConstantModule(constantModule: ConstantModule?) {
//                println("visit")
//            }
//
//        })
//        clazz
//    }

    @Test
    fun bcel2Test() {
        val classParser = ClassParser(javaClass.getResource("/OwnerController.class")?.file)
        val clazz = classParser.parse()
        val visitor: Visitor = object : Visitor {
            override fun visitCode(obj: Code?) {
                println("visit: ${obj?.name}")
            }

            override fun visitCodeException(obj: CodeException?) {
                println("visit")
            }

            override fun visitConstantClass(obj: ConstantClass?) {
                println("visit")
            }

            override fun visitConstantDouble(obj: ConstantDouble?) {
                println("visit")
            }

            override fun visitConstantFieldref(obj: ConstantFieldref?) {
                println("visit")
            }

            override fun visitConstantFloat(obj: ConstantFloat?) {
                println("visit")
            }

            override fun visitConstantInteger(obj: ConstantInteger?) {
                println("visit")
            }

            override fun visitConstantInterfaceMethodref(obj: ConstantInterfaceMethodref?) {
                println("visit")
            }

            override fun visitConstantInvokeDynamic(obj: ConstantInvokeDynamic?) {
                println("visit")
            }

            override fun visitConstantLong(obj: ConstantLong?) {
                println("visit")
            }

            override fun visitConstantMethodref(obj: ConstantMethodref?) {
                println("visit")
            }

            override fun visitConstantNameAndType(obj: ConstantNameAndType?) {
                println("visit")
            }

            override fun visitConstantPool(obj: ConstantPool?) {
                println("visit")
            }

            override fun visitConstantString(obj: ConstantString?) {
                println("visit")
            }

            override fun visitConstantUtf8(obj: ConstantUtf8?) {
                println("visit")
            }

            override fun visitConstantValue(obj: ConstantValue?) {
                println("visit")
            }

            override fun visitDeprecated(obj: Deprecated?) {
                println("visit")
            }

            override fun visitExceptionTable(obj: ExceptionTable?) {
                println("visit")
            }

            override fun visitField(obj: Field?) {
                println("visit")
            }

            override fun visitInnerClass(obj: InnerClass?) {
                println("visit")
            }

            override fun visitInnerClasses(obj: InnerClasses?) {
                println("visit")
            }

            override fun visitJavaClass(obj: JavaClass?) {
                println("visit")
            }

            override fun visitLineNumber(obj: LineNumber?) {
                println("visit")
            }

            override fun visitLineNumberTable(obj: LineNumberTable?) {
                println("visit")
            }

            override fun visitLocalVariable(obj: LocalVariable?) {
                println("visit")
            }

            override fun visitLocalVariableTable(obj: LocalVariableTable?) {
                println("visit")
            }

            override fun visitMethod(obj: Method?) {
                println("visit: ${obj?.name}")
            }

            override fun visitSignature(obj: Signature?) {
                println("visit")
            }

            override fun visitSourceFile(obj: SourceFile?) {
                println("visit")
            }

            override fun visitSynthetic(obj: Synthetic?) {
                println("visit")
            }

            override fun visitUnknown(obj: Unknown?) {
                println("visit")
            }

            override fun visitStackMap(obj: StackMap?) {
                println("visit")
            }

            override fun visitStackMapEntry(obj: StackMapEntry?) {
                println("visit")
            }

            override fun visitAnnotation(obj: Annotations?) {
                println("visit")
            }

            override fun visitParameterAnnotation(obj: ParameterAnnotations?) {
                println("visit")
            }

            override fun visitAnnotationEntry(obj: AnnotationEntry?) {
                println("visit")
            }

            override fun visitAnnotationDefault(obj: AnnotationDefault?) {
                println("visit")
            }

            override fun visitLocalVariableTypeTable(obj: LocalVariableTypeTable?) {
                println("visit")
            }

            override fun visitEnclosingMethod(obj: EnclosingMethod?) {
                println("visit")
            }

            override fun visitBootstrapMethods(obj: BootstrapMethods?) {
                println("visit")
            }

            override fun visitMethodParameters(obj: MethodParameters?) {
                println("visit")
            }

            override fun visitConstantMethodType(obj: ConstantMethodType?) {
                println("visit")
            }

            override fun visitConstantMethodHandle(obj: ConstantMethodHandle?) {
                println("visit")
            }

            override fun visitParameterAnnotationEntry(obj: ParameterAnnotationEntry?) {
                println("visit")
            }

            override fun visitConstantPackage(constantPackage: ConstantPackage?) {
                println("visit")
            }

            override fun visitConstantModule(constantModule: ConstantModule?) {
                println("visit")
            }

        }
        DescendingVisitor(clazz, visitor).visit()

    }

}