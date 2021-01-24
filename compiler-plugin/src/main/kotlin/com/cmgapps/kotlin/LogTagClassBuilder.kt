/*
 * Copyright (c) 2021. Christian Grach <christian.grach@cmgapps.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cmgapps.kotlin

import com.cmgapps.LogTag
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.codegen.ClassBuilder
import org.jetbrains.kotlin.codegen.DelegatingClassBuilder
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.jvm.diagnostics.JvmDeclarationOrigin
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.commons.InstructionAdapter

private val logTagFqName = FqName(LogTag::class.java.canonicalName)
private const val LOGGING_PREFIX = "***** LOGTAG "

internal class LogTagClassBuilder(
    private val delegateBuilder: ClassBuilder,
    private val classAnnotations: Annotations?,
    private val messageCollector: MessageCollector
) : DelegatingClassBuilder() {
    override fun getDelegate(): ClassBuilder = delegateBuilder

    override fun newMethod(
        origin: JvmDeclarationOrigin,
        access: Int,
        name: String,
        desc: String,
        signature: String?,
        exceptions: Array<out String>?
    ): MethodVisitor {

        val className = FqName(delegateBuilder.thisName.replace('/', '.'))
        val original = super.newMethod(origin, access, name, desc, signature, exceptions)

        val logTagAnnotation = classAnnotations?.findAnnotation(logTagFqName) ?: return original

        return object : MethodVisitor(Opcodes.ASM5, original) {
            override fun visitMethodInsn(
                opcode: Int,
                owner: String?,
                name: String?,
                descriptor: String?,
                isInterface: Boolean
            ) {
                if (owner != "timber/log/Timber") {
                    return super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
                }

                if (!LOG_METHOD_NAMES.contains(name)) {
                    return super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
                }

                // logging method not invoked static so assume `tag` is already called
                if (opcode != Opcodes.INVOKESTATIC) {
                    return super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
                }

                InstructionAdapter(this).apply {
                    aconst(className.getLogTag(logTagAnnotation, messageCollector))
                    invokestatic("timber/log/Timber", "tag", "(Ljava/lang/String;)Ltimber/log/Timber\$Tree;", false)
                    // pop the Timber.Tree so original log call can be executed
                    pop()
                }

                return super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
            }
        }
    }

    private fun FqName.getLogTag(logTagAnnotation: AnnotationDescriptor, messageCollector: MessageCollector): String {
        val logTagValue = logTagAnnotation.allValueArguments[Name.identifier("value")]?.value as? String

        if (!logTagValue.isNullOrBlank()) {
            return logTagValue
        }

        return this.shortName().asString().let {
            if (it.length > 23) {
                messageCollector.report(
                    CompilerMessageSeverity.WARNING,
                    "Class name \"$it\" exceeds max. length of 23 for a log tag. Class name will be truncated." +
                        " Add the @com.cmgapps.LogTag annotation with a custom tag to override"
                )
                it.substring(0..22)
            } else {
                it
            }
        }
    }

    companion object {
        private val LOG_METHOD_NAMES = listOf("v", "d", "i", "w", "e", "wtf", "log")
    }

    private fun MessageCollector.log(message: String?) =
        report(CompilerMessageSeverity.INFO, "$LOGGING_PREFIX$message")
}