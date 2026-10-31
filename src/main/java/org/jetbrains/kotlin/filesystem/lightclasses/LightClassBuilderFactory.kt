/*******************************************************************************
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *******************************************************************************/
package org.jetbrains.kotlin.filesystem.lightclasses

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import kotlin.Pair
import org.jetbrains.kotlin.codegen.AbstractClassBuilder
import org.jetbrains.kotlin.codegen.ClassBuilder
import org.jetbrains.kotlin.codegen.ClassBuilderFactory
import org.jetbrains.kotlin.codegen.ClassBuilderMode
import org.jetbrains.kotlin.resolve.jvm.diagnostics.JvmDeclarationOrigin
import org.jetbrains.org.objectweb.asm.ClassWriter
import org.jetbrains.org.objectweb.asm.FieldVisitor
import org.jetbrains.org.objectweb.asm.MethodVisitor

class LightClassBuilderFactory : ClassBuilderFactory {

    override fun getClassBuilderMode(): ClassBuilderMode = ClassBuilderMode.KAPT3

    override fun newClassBuilder(origin: JvmDeclarationOrigin): ClassBuilder {
        return object : AbstractClassBuilder.Concrete(BinaryClassWriter()) {
            override fun newMethod(
                origin: JvmDeclarationOrigin,
                access: Int,
                name: String,
                desc: String,
                signature: String?,
                exceptions: Array<String>?
            ): MethodVisitor {
                return super.newMethod(origin, access, name, desc, signature, exceptions)
            }

            override fun newField(
                origin: JvmDeclarationOrigin,
                access: Int,
                name: String,
                desc: String,
                signature: String?,
                value: Any?
            ): FieldVisitor {
                return super.newField(origin, access, name, desc, signature, value)
            }
        }
    }

    override fun asText(builder: ClassBuilder) = throw UnsupportedOperationException("BINARIES generator asked for text")

    override fun asBytes(builder: ClassBuilder): ByteArray = (builder.visitor as ClassWriter).toByteArray()

    companion object {
        val JVM_SIGNATURE: Key<MutableSet<Pair<String, String>>> = Key.create("JVM_SIGNATURE")

        fun saveJvmSignature(element: PsiElement?, name: String, desc: String) {
            if (element == null) return
            var userData = element.getUserData(JVM_SIGNATURE)
            if (userData == null) {
                userData = hashSetOf()
                element.putUserData(JVM_SIGNATURE, userData)
            }
            userData.add(Pair(desc, name))
        }
    }
}