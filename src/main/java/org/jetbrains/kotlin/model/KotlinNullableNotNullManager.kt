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
package com.intellij.codeInsight

import com.intellij.openapi.project.Project as IdeaProject
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifierListOwner
import java.util.Optional
import org.netbeans.api.project.Project

open class KotlinNullableNotNullManager(
    ideaProject: IdeaProject,
    private val javaProject: Project
) : NullableNotNullManager(ideaProject) {

    private val notNullList = mutableListOf("org.jetbrains.annotations.NotNull", "NotNull")
    private val nullableList = mutableListOf("org.jetbrains.annotations.Nullable", "Nullable")
    private var defaultNotNullAnnotation = "org.jetbrains.annotations.NotNull"
    private var defaultNullableAnnotation = "org.jetbrains.annotations.Nullable"
    private var instrumentedNotNullsList = mutableListOf<String>()

    override fun hasHardcodedContracts(element: PsiElement): Boolean = false

    override fun isNotNull(owner: PsiModifierListOwner, checkBases: Boolean): Boolean {
        val notNullAnnotations = notNulls.toSet()
        return owner.modifierList?.annotations?.any { annotation ->
            annotation.qualifiedName in notNullAnnotations
        } ?: false
    }

    override fun isNullable(owner: PsiModifierListOwner, checkBases: Boolean): Boolean =
        !isNotNull(owner, checkBases)

    override fun getAnnotationNullability(name: String?): Optional<Nullability> {
        if (name == null) return Optional.empty()
        return when {
            name in notNullList -> Optional.of(Nullability.NOT_NULL)
            name in nullableList -> Optional.of(Nullability.NULLABLE)
            else -> Optional.empty()
        }
    }

    override fun isTypeUseAnnotationLocationRestricted(name: String?): Boolean = false

    override fun canAnnotateLocals(name: String?): Boolean = true

    override fun setNotNulls(vararg annotations: String) {
        notNullList.clear()
        notNullList.addAll(annotations)
    }

    override fun setNullables(vararg annotations: String) {
        nullableList.clear()
        nullableList.addAll(annotations)
    }

    override fun getDefaultNullable(): String = defaultNullableAnnotation

    override fun setDefaultNullable(defaultNullable: String) {
        defaultNullableAnnotation = defaultNullable
    }

    override fun getDefaultNotNull(): String = defaultNotNullAnnotation

    override fun setDefaultNotNull(defaultNotNull: String) {
        defaultNotNullAnnotation = defaultNotNull
    }

    override fun findEffectiveNullabilityInfo(owner: PsiModifierListOwner): NullabilityAnnotationInfo? = null

    override fun getAllNullabilityAnnotationsWithNickNames(): NullabilityAnnotationDataHolder {
        return object : NullabilityAnnotationDataHolder {
            override fun qualifiedNames(): Set<String> = (notNullList + nullableList).toSet()
            override fun getNullability(qualifiedName: String?): Nullability? =
                getAnnotationNullability(qualifiedName).orElse(null)
        }
    }

    override fun getNullityDefault(
        owner: PsiModifierListOwner,
        placeTargetTypes: Array<out PsiAnnotation.TargetType>,
        context: PsiElement,
        superPackage: Boolean
    ): NullabilityAnnotationInfo? = null

    override fun getNullables(): MutableList<String> = nullableList

    override fun getNotNulls(): MutableList<String> = notNullList

    override fun getInstrumentedNotNulls(): MutableList<String> = instrumentedNotNullsList

    override fun setInstrumentedNotNulls(names: MutableList<String>) {
        instrumentedNotNullsList = names
    }
}