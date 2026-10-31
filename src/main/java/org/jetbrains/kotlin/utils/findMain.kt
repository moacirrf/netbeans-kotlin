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
package org.jetbrains.kotlin.utils

import java.io.File
import org.jetbrains.kotlin.fileClasses.JvmFileClassUtil
import org.jetbrains.kotlin.psi.*

fun KtFile.mainClassName(simple: Boolean = false): String? {
    // 1. Check top-level main function
    if (declarations.findMainFunction() != null) {
        val fqName = JvmFileClassUtil.getFileClassInfoNoResolve(this).facadeClassFqName
        return if (simple) fqName.shortName().asString() else fqName.asString()
    }

    // 2. Check main function inside objects or classes
    for (declaration in declarations) {
        if (declaration is KtClassOrObject) {
            val hasMainInBody = declaration.declarations.findMainFunction() != null ||
                    declaration.companionObjects.any { it.declarations.findMainFunction() != null }
            if (hasMainInBody) {
                val fqName = declaration.fqName
                if (fqName != null) {
                    return if (simple) fqName.shortName().asString() else fqName.asString()
                }
            }
        }
    }
    return null
}

fun KtFile.hasMain() = mainClassName() != null

private fun KtNamedFunction.isMain(): Boolean {
    if (name == "main") {
        if (valueParameters.isEmpty()) return true
        if (valueParameters.size == 1) {
            val reference = valueParameters.first().typeReference ?: return false
            val text = reference.text
            return text == "Array<String>" || text.startsWith("Array<") || text.endsWith("<String>") || text.contains("Array<")
        }
    }
    return false
}

private fun Collection<KtDeclaration>.findMainFunction(): KtNamedFunction? =
        filterIsInstance(KtNamedFunction::class.java).firstOrNull { it.isMain() }
