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
package org.jetbrains.kotlin.resolve

import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.container.ComponentProvider
import org.jetbrains.kotlin.container.StorageComponentContainer
import org.jetbrains.kotlin.container.registerSingleton
import org.jetbrains.kotlin.container.useInstance
import org.jetbrains.kotlin.context.ModuleContext
import org.jetbrains.kotlin.frontend.java.di.createContainerForLazyResolveWithJava
import org.jetbrains.kotlin.incremental.components.EnumWhenTracker
import org.jetbrains.kotlin.incremental.components.ExpectActualTracker
import org.jetbrains.kotlin.incremental.components.InlineConstTracker
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.load.java.lazy.ModuleClassResolver
import org.jetbrains.kotlin.load.kotlin.PackagePartProvider
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.resolve.lang.java.NetBeansJavaClassFinder
import org.jetbrains.kotlin.resolve.lang.java.resolver.NetBeansJavaSourceElementFactory
import org.jetbrains.kotlin.resolve.lang.java.resolver.NetBeansTraceBasedJavaResolverCache
import org.jetbrains.kotlin.resolve.lazy.declarations.DeclarationProviderFactory
import org.netbeans.api.project.Project as NBProject

fun createContainerForTopDownAnalyzerForJvm(
    moduleContext: ModuleContext,
    bindingTrace: BindingTrace,
    declarationProviderFactory: DeclarationProviderFactory,
    moduleContentScope: GlobalSearchScope,
    lookupTracker: LookupTracker,
    packagePartProvider: PackagePartProvider,
    languageVersionSettings: LanguageVersionSettings,
    moduleClassResolver: ModuleClassResolver,
    project: NBProject
): StorageComponentContainer = createContainerForLazyResolveWithJava(
    jvmPlatform = JvmPlatforms.defaultJvmPlatform,
    moduleContext = moduleContext,
    bindingTrace = bindingTrace,
    declarationProviderFactory = declarationProviderFactory,
    moduleContentScope = moduleContentScope,
    moduleClassResolver = moduleClassResolver,
    targetEnvironment = CompilerEnvironment,
    lookupTracker = lookupTracker,
    expectActualTracker = ExpectActualTracker.DoNothing,
    inlineConstTracker = InlineConstTracker.DoNothing,
    enumWhenTracker = EnumWhenTracker.DoNothing,
    packagePartProvider = packagePartProvider,
    languageVersionSettings = languageVersionSettings,
    useBuiltInsProvider = true,
    configureJavaClassFinder = {
        useImpl<NetBeansJavaClassFinder>()
        useImpl<NetBeansTraceBasedJavaResolverCache>()
        useImpl<NetBeansJavaSourceElementFactory>()
        useInstance(project)
    }
)

// Copy functions from Dsl.kt as they were shrinked by proguard
inline fun <reified T : Any> StorageComponentContainer.useImpl() {
    registerSingleton(T::class.java)
}

inline fun <reified T : Any> ComponentProvider.get(): T = getService(T::class.java)