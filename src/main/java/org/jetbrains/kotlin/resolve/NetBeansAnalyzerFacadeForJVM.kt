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

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import java.util.LinkedHashSet
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.builtins.jvm.JvmBuiltIns
import org.jetbrains.kotlin.builtins.jvm.JvmBuiltInsPackageFragmentProvider
import org.jetbrains.kotlin.cli.jvm.compiler.NoScopeRecordCliBindingTrace
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.container.get
import org.jetbrains.kotlin.context.ContextForNewModule
import org.jetbrains.kotlin.context.MutableModuleContext
import org.jetbrains.kotlin.context.ProjectContext
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentProvider
import org.jetbrains.kotlin.descriptors.impl.CompositePackageFragmentProvider
import org.jetbrains.kotlin.descriptors.impl.ModuleDependenciesImpl
import org.jetbrains.kotlin.frontend.java.di.initJvmBuiltInsForTopDownAnalysis
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.load.java.lazy.ModuleClassResolver
import org.jetbrains.kotlin.load.java.structure.JavaClass
import org.jetbrains.kotlin.load.java.structure.impl.VirtualFileBoundJavaClass
import org.jetbrains.kotlin.model.KotlinEnvironment
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.jvm.JavaDescriptorResolver
import org.jetbrains.kotlin.resolve.jvm.extensions.PackageFragmentProviderExtension
import org.jetbrains.kotlin.resolve.lazy.KotlinCodeAnalyzer
import org.jetbrains.kotlin.resolve.lazy.declarations.FileBasedDeclarationProviderFactory
import org.jetbrains.kotlin.util.KotlinFrontEndException
import org.jetbrains.kotlin.utils.ProjectUtils
import org.netbeans.api.project.Project as NBProject

private class SourceOrBinaryModuleClassResolver(private val sourceScope: GlobalSearchScope) : ModuleClassResolver {
    lateinit var compiledCodeResolver: JavaDescriptorResolver
    lateinit var sourceCodeResolver: JavaDescriptorResolver

    override fun resolveClass(javaClass: JavaClass): ClassDescriptor? {
        val resolver = if (javaClass is VirtualFileBoundJavaClass && javaClass.isFromSource) {
            sourceCodeResolver
        } else {
            compiledCodeResolver
        }
        return resolver.resolveClass(javaClass)
    }
}

object NetBeansAnalyzerFacadeForJVM {

    @Suppress("DEPRECATION_ERROR")
    fun analyzeFilesWithJavaIntegration(
        kotlinProject: NBProject,
        project: Project,
        filesToAnalyze: Collection<KtFile>
    ): AnalysisResultWithProvider {
        val filesSet = filesToAnalyze.toSet()

        val allFiles = LinkedHashSet<KtFile>(filesSet)
        val addedFiles = filesSet.mapNotNullTo(hashSetOf()) { getPath(it) }
        ProjectUtils.getSourceFilesWithDependencies(kotlinProject).filterNotTo(allFiles) {
            getPath(it) in addedFiles
        }

        val environment = KotlinEnvironment.getEnvironment(kotlinProject)

        val moduleContext = createModuleContext(
            project,
            environment.configuration,
            true
        )
        val storageManager = moduleContext.storageManager
        val module = moduleContext.module

        val providerFactory = FileBasedDeclarationProviderFactory(storageManager, allFiles)
        val trace = org.jetbrains.kotlin.cli.jvm.compiler.CliBindingTrace(project)

        val sourceScope = TopDownAnalyzerFacadeForJVM.newModuleSearchScope(project, filesToAnalyze)
        val moduleClassResolver = SourceOrBinaryModuleClassResolver(sourceScope)

        val languageVersionSettings = LanguageVersionSettingsImpl.DEFAULT
        val optionalBuiltInsModule = JvmBuiltIns(storageManager, JvmBuiltIns.Kind.FALLBACK).apply {
            initialize(builtInsModule, true)
        }.builtInsModule

        val dependencyModule = run {
            val dependenciesContext = ContextForNewModule(
                moduleContext,
                Name.special("<dependencies of ${environment.configuration.getNotNull<String>(CommonConfigurationKeys.MODULE_NAME)}>"),
                module.builtIns,
                JvmPlatforms.defaultJvmPlatform
            )

            val dependencyScope = GlobalSearchScope.notScope(sourceScope)
            val dependenciesContainer = createContainerForTopDownAnalyzerForJvm(
                dependenciesContext,
                trace,
                providerFactory,
                dependencyScope,
                LookupTracker.DO_NOTHING,
                KotlinPackagePartProvider(kotlinProject, dependencyScope),
                languageVersionSettings,
                moduleClassResolver,
                kotlinProject
            )

            moduleClassResolver.compiledCodeResolver = dependenciesContainer.get()

            dependenciesContext.setDependencies(listOfNotNull(dependenciesContext.module, optionalBuiltInsModule))
            dependenciesContext.initializeModuleContents(
                CompositePackageFragmentProvider(
                    listOf(
                        moduleClassResolver.compiledCodeResolver.packageFragmentProvider,
                        dependenciesContainer.get<JvmBuiltInsPackageFragmentProvider>()
                    ),
                    "CompositeProvider@TopDownAnalyzerForJvm for ${dependenciesContext.module}"
                )
            )

            dependenciesContext.module
        }

        val container = createContainerForTopDownAnalyzerForJvm(
            moduleContext,
            trace,
            providerFactory,
            sourceScope,
            LookupTracker.DO_NOTHING,
            KotlinPackagePartProvider(kotlinProject, sourceScope),
            languageVersionSettings,
            moduleClassResolver,
            kotlinProject
        ).apply {
            initJvmBuiltInsForTopDownAnalysis()
        }

        moduleClassResolver.sourceCodeResolver = container.get()

        val additionalProviders = arrayListOf<PackageFragmentProvider>()
        additionalProviders.add(container.get<JavaDescriptorResolver>().packageFragmentProvider)

        PackageFragmentProviderExtension.getInstances(project).mapNotNullTo(additionalProviders) { extension ->
            extension.getPackageFragmentProvider(project, module, storageManager, trace, null, LookupTracker.DO_NOTHING)
        }

        module.setDependencies(
            ModuleDependenciesImpl(
                listOfNotNull(module, dependencyModule, optionalBuiltInsModule),
                setOf(dependencyModule),
                emptyList(),
                emptySet()
            )
        )
        module.initialize(
            CompositePackageFragmentProvider(
                listOf(container.get<KotlinCodeAnalyzer>().packageFragmentProvider) + additionalProviders,
                "CompositeProvider@TopDownAnalyzerForJvm for $module"
            )
        )

        try {
            container.get<LazyTopDownAnalyzer>().analyzeDeclarations(TopDownAnalysisMode.TopLevelDeclarations, filesSet)
        } catch (e: KotlinFrontEndException) {
        }

        return AnalysisResultWithProvider(
            AnalysisResult.success(trace.bindingContext, module),
            container
        )
    }

    private fun getPath(jetFile: KtFile): String? = jetFile.virtualFile?.path

    private fun createModuleContext(
        project: Project,
        configuration: CompilerConfiguration,
        createBuiltInsFromModule: Boolean
    ): MutableModuleContext {
        val projectContext = ProjectContext(project, "TopDownAnalyzer for JVM")
        val builtIns = JvmBuiltIns(
            projectContext.storageManager,
            if (createBuiltInsFromModule) JvmBuiltIns.Kind.FROM_DEPENDENCIES else JvmBuiltIns.Kind.FROM_CLASS_LOADER
        )

        return ContextForNewModule(
            projectContext,
            Name.special("<${configuration.getNotNull<String>(CommonConfigurationKeys.MODULE_NAME)}>"),
            builtIns,
            JvmPlatforms.defaultJvmPlatform
        ).apply {
            if (createBuiltInsFromModule) {
                builtIns.builtInsModule = module
            }
        }
    }
}