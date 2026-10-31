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

import javax.inject.Inject
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.load.java.AbstractJavaClassFinder
import org.jetbrains.kotlin.load.java.JavaClassFinder
import org.jetbrains.kotlin.load.java.structure.JavaClass
import org.jetbrains.kotlin.load.java.structure.JavaPackage
import org.jetbrains.kotlin.model.KotlinEnvironment
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.lang.java.structure.NetBeansJavaClass
import org.jetbrains.kotlin.resolve.lang.java.structure.NetBeansJavaPackage
import org.jetbrains.kotlin.resolve.lazy.KotlinCodeAnalyzer
import org.netbeans.api.project.Project

class NetBeansJavaClassFinder : AbstractJavaClassFinder() {

    private lateinit var nbProject: Project

    @Inject
    fun setProjectScope(project: Project) {
        this.nbProject = project
        super.setProjectInstance(KotlinEnvironment.getEnvironment(project).project)
    }

    fun initialize(trace: BindingTrace, codeAnalyzer: KotlinCodeAnalyzer, languageVersionSettings: LanguageVersionSettings) {
        super.initialize(trace, codeAnalyzer, languageVersionSettings, JvmTarget.DEFAULT)
    }

    override fun findClass(request: JavaClassFinder.Request): JavaClass? = findClass(request.classId)

    override fun findClasses(request: JavaClassFinder.Request): List<JavaClass> =
        listOfNotNull(findClass(request.classId))

    override fun findClass(classId: ClassId): JavaClass? {
        val element = nbProject.findType(classId.asSingleFqName().asString()) ?: return null
        return NetBeansJavaClass(element, nbProject)
    }

    override fun findPackage(fqName: FqName, mayHaveAnnotations: Boolean): JavaPackage? {
        val pack = nbProject.findPackage(fqName.asString()) ?: return null
        return NetBeansJavaPackage(pack, nbProject)
    }

    override fun knownClassNamesInPackage(packageFqName: FqName): Set<String>? =
        knownClassNamesInPackage(packageFqName.asString(), nbProject)

    override fun canComputeKnownClassNamesInPackage(): Boolean = true
}