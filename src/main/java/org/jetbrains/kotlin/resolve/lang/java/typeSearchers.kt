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
package org.jetbrains.kotlin.resolve.lang.java

import javax.lang.model.element.TypeElement
import javax.lang.model.type.ArrayType
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.WildcardType
import org.jetbrains.kotlin.load.java.structure.JavaType
import org.jetbrains.kotlin.resolve.lang.java.structure.NetBeansJavaType
import org.netbeans.api.java.source.CompilationController
import org.netbeans.api.java.source.Task
import org.netbeans.api.java.source.TypeMirrorHandle
import org.netbeans.api.project.Project

fun hasTypeVariable(type: javax.lang.model.type.TypeMirror?): Boolean {
    if (type == null) return false
    return when (type.kind) {
        javax.lang.model.type.TypeKind.TYPEVAR -> true
        javax.lang.model.type.TypeKind.DECLARED -> (type as DeclaredType).typeArguments.any { hasTypeVariable(it) }
        javax.lang.model.type.TypeKind.ARRAY -> hasTypeVariable((type as ArrayType).componentType)
        javax.lang.model.type.TypeKind.WILDCARD -> {
            val extendsBound = (type as WildcardType).extendsBound
            val superBound = type.superBound
            (extendsBound != null && hasTypeVariable(extendsBound)) || (superBound != null && hasTypeVariable(superBound))
        }
        else -> false
    }
}

class TypeNameSearcher(val handle: TypeMirrorHandle<*>) : Task<CompilationController> {

    lateinit var name: String

    override fun run(info: CompilationController) {
        info.toResolvedPhase()

        val type = try {
            handle.resolve(info)
        } catch (t: Throwable) {
            null
        }
        name = type?.toString() ?: ""
    }
}

class BoundSearcher(val handle: TypeMirrorHandle<*>,
                    val project: Project) : Task<CompilationController> {

    var bound: JavaType? = null

    override fun run(info: CompilationController) {
        info.toResolvedPhase()

        val type = try {
            handle.resolve(info)
        } catch (t: Throwable) {
            null
        } ?: return
        val boundMirror = (type as WildcardType).extendsBound ?: type.superBound

        bound = if (boundMirror != null) {
            val target = if (hasTypeVariable(boundMirror)) info.types.erasure(boundMirror) else boundMirror
            NetBeansJavaType.create(TypeMirrorHandle.create(target), project)
        } else null
    }
}

class IsExtendsSearcher(val handle: TypeMirrorHandle<*>) : Task<CompilationController> {

    var isExtends = false

    override fun run(info: CompilationController) {
        info.toResolvedPhase()

        val type = try {
            handle.resolve(info)
        } catch (t: Throwable) {
            null
        } ?: return
        isExtends = (type as WildcardType).extendsBound != null
    }
}

class ComponentTypeSearcher(val handle: TypeMirrorHandle<*>,
                            val project: Project) : Task<CompilationController> {

    lateinit var componentType: JavaType

    override fun run(info: CompilationController) {
        info.toResolvedPhase()

        val type = try {
            handle.resolve(info)
        } catch (t: Throwable) {
            null
        } ?: throw UnsupportedOperationException("Component type not found")
        val componentTypeHandle = TypeMirrorHandle.create((type as ArrayType).componentType)
        componentType = NetBeansJavaType.create(componentTypeHandle, project)
    }
}

class IsRawSearcher(val handle: TypeMirrorHandle<*>) : Task<CompilationController> {

    var isRaw = false

    override fun run(info: CompilationController) {
        info.toResolvedPhase()

        val type = try {
            handle.resolve(info)
        } catch (t: Throwable) {
            null
        } as? DeclaredType ?: return
        val element = type.asElement() as TypeElement
        if (element.typeParameters.isEmpty()) return
        
        isRaw = type.typeArguments.isEmpty()
    }
}

class TypeArgumentsSearcher(val handle: TypeMirrorHandle<*>,
                            val project: Project) : Task<CompilationController> {
    
    val typeArguments = arrayListOf<JavaType>()
    
    override fun run(info: CompilationController) {
        info.toResolvedPhase()
        
        val type = try {
            handle.resolve(info)
        } catch (t: Throwable) {
            null
        } ?: return
        val args = (type as DeclaredType).typeArguments
                .map { 
                    val target = if (hasTypeVariable(it)) info.types.erasure(it) else it
                    NetBeansJavaType.create(TypeMirrorHandle.create(target), project) 
                }
        typeArguments.addAll(args)
    }
}