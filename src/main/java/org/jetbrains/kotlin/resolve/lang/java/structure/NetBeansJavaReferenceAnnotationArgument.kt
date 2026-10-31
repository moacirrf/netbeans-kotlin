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
package org.jetbrains.kotlin.resolve.lang.java.structure

import javax.lang.model.element.VariableElement
import org.jetbrains.kotlin.load.java.structure.JavaEnumValueAnnotationArgument
import org.jetbrains.kotlin.load.java.structure.JavaField
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.lang.java.*
import org.jetbrains.kotlin.resolve.lang.java.ElemHandle
import org.netbeans.api.project.Project

class NetBeansJavaReferenceAnnotationArgument(val handle: ElemHandle<VariableElement>, val project: Project) :
    NetBeansJavaAnnotationArgument(FqName(handle.getSimpleName(project))), JavaEnumValueAnnotationArgument {

    override val enumClassId: ClassId?
        get() = handle.getContainingClass(project).computeClassId(project)

    override val entryName: Name?
        get() = Name.identifier(handle.getSimpleName(project))

    fun resolve(): JavaField {
        val containingClass = handle.getContainingClass(project)
        return NetBeansJavaField(handle, NetBeansJavaClass(containingClass, project), project)
    }
}