package com.epam.drill.plugins.test2code.jvm

import com.epam.drill.plugins.test2code.util.*
import kotlinx.collections.immutable.*
import org.objectweb.asm.*

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
        arguments.filterIsInstance(Handle::class.java).firstOrNull()?.let {
            val existing = lambdas[fullMethodName] ?: persistentSetOf()
            lambdas.put(fullMethodName, existing + signature(it.owner, it.name, it.desc))
        }
    }
}
