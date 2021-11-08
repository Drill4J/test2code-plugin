package com.epam.drill.plugins.test2code.jvm

import com.epam.drill.plugins.test2code.util.*
import kotlinx.collections.immutable.*
import org.objectweb.asm.*

fun transform(className: String, byteArray: ByteArray) = run {
    val classReader = ClassReader(byteArray)
    val classWriter = ClassWriter(classReader, ClassWriter.COMPUTE_FRAMES)
    classReader.accept(LambdaClassVisitor(className, Opcodes.ASM7), ClassReader.EXPAND_FRAMES)
    classWriter.toByteArray()
}

class LambdaClassVisitor(private val className: String, api: Int) : ClassVisitor(api) {

    override fun visitMethod(
        access: Int,
        name: String?,
        descriptor: String?,
        signature: String?,
        exceptions: Array<out String>?
    ): MethodVisitor {
        return LambdaMethodVisitor(signature(className, name ?: "", descriptor ?: ""), Opcodes.ASM7)
    }
}

class LambdaMethodVisitor(private val fullMethodName: String, api: Int) : MethodVisitor(api) {
    override fun visitInvokeDynamicInsn(
        name: String?,
        descriptor: String?,
        handle: Handle?,
        vararg arguments: Any?
    ) {
        //if full method name contains "lambda" then modify bytecode and add it to collection
        //if inner lambda -> take new name from collection and put
        //get new bytecode -> codeToString
    }
}
