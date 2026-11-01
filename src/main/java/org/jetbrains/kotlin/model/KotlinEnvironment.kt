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
package org.jetbrains.kotlin.model

import com.intellij.codeInsight.ExternalAnnotationsManager
import com.intellij.codeInsight.InferredAnnotationsManager
import com.intellij.codeInsight.KotlinNullableNotNullManager
import com.intellij.codeInsight.NullableNotNullManager
import com.intellij.core.CoreApplicationEnvironment
import com.intellij.core.CoreJavaFileManager
import com.intellij.core.JavaCoreApplicationEnvironment
import com.intellij.core.JavaCoreProjectEnvironment
import com.intellij.mock.MockProject
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionsArea
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.file.impl.JavaFileManager
import java.io.File
import java.net.URLDecoder
import org.jetbrains.kotlin.caches.resolve.KotlinCacheService
import org.jetbrains.kotlin.cli.common.CliModuleVisibilityManagerImpl
import org.jetbrains.kotlin.cli.jvm.compiler.CliTraceHolder
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCliJavaFileManagerImpl
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.MockExternalAnnotationsManager
import org.jetbrains.kotlin.cli.jvm.compiler.MockInferredAnnotationsManager
import org.jetbrains.kotlin.cli.jvm.index.JavaRoot
import org.jetbrains.kotlin.cli.jvm.index.JvmDependenciesIndexImpl
import org.jetbrains.kotlin.cli.jvm.modules.CliJavaModuleFinder
import org.jetbrains.kotlin.cli.jvm.modules.CliJavaModuleResolver
import org.jetbrains.kotlin.cli.jvm.modules.JavaModuleGraph
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.filesystem.KotlinLightClassManager
import org.jetbrains.kotlin.idea.util.ImportInsertHelper
import org.jetbrains.kotlin.load.kotlin.MetadataFinderFactory
import org.jetbrains.kotlin.load.kotlin.ModuleVisibilityManager
import org.jetbrains.kotlin.load.kotlin.VirtualFileFinderFactory
import org.jetbrains.kotlin.log.KotlinLogger
import org.jetbrains.kotlin.resolve.BuiltInsReferenceResolver
import org.jetbrains.kotlin.resolve.CodeAnalyzerInitializer
import org.jetbrains.kotlin.resolve.KotlinCacheServiceImpl
import org.jetbrains.kotlin.resolve.jvm.modules.JavaModuleResolver
import org.jetbrains.kotlin.resolve.lang.kotlin.NetBeansVirtualFileFinderFactory
import org.jetbrains.kotlin.utils.KotlinImportInserterHelper
import org.jetbrains.kotlin.utils.ProjectUtils
import org.netbeans.spi.java.classpath.ClassPathProvider
import org.netbeans.api.java.platform.JavaPlatform
import org.netbeans.api.java.platform.JavaPlatformManager
import org.openide.filesystems.FileUtil
import org.netbeans.api.project.Project as NBProject

//copied from kotlin eclipse plugin to avoid RuntimeException: Could not find installation home path. 
//Please make sure bin/idea.properties is present in the installation directory
private fun setIdeaIoUseFallback() {
    val properties = System.getProperties()
    properties.setProperty("idea.io.use.nio2", java.lang.Boolean.TRUE.toString())
    properties.setProperty("idea.io.use.fallback", java.lang.Boolean.TRUE.toString())
    org.jetbrains.kotlin.cli.jvm.compiler.setupIdeaStandaloneExecution()
}

@OptIn(org.jetbrains.kotlin.CoreEnvironmentDeprecation::class)
class KotlinEnvironment private constructor(kotlinProject: NBProject, disposable: Disposable) {

    companion object {
        val CACHED_ENVIRONMENT = hashMapOf<NBProject, KotlinEnvironment>()
        
        @Synchronized fun getEnvironment(kotlinProject: NBProject): KotlinEnvironment {
            if (!CACHED_ENVIRONMENT.containsKey(kotlinProject)) {
                CACHED_ENVIRONMENT.put(kotlinProject, KotlinEnvironment(kotlinProject, Disposer.newDisposable()))
            }
            
            return CACHED_ENVIRONMENT[kotlinProject]!!
        }
        
        @Synchronized fun updateKotlinEnvironment(kotlinProject: NBProject) = getEnvironment(kotlinProject).configureClasspath(kotlinProject)
    }

    val applicationEnvironment: JavaCoreApplicationEnvironment
    private val projectEnvironment: JavaCoreProjectEnvironment
    val project: MockProject
    val roots = hashSetOf<JavaRoot>()
    
    val index by lazy { JvmDependenciesIndexImpl(roots.toList()) }
    
    val configuration = CompilerConfiguration()
    
    init {
        val startTime = System.nanoTime()

        setIdeaIoUseFallback()
                
        applicationEnvironment = createJavaCoreApplicationEnvironment(disposable)
        projectEnvironment = object : JavaCoreProjectEnvironment(disposable, applicationEnvironment) {
            override fun preregisterServices() { 
                registerProjectExtensionPoints(project.extensionArea) 
            }
            
            override fun createCoreFileManager() = KotlinCliJavaFileManagerImpl(PsiManager.getInstance(project))
        }
        project = projectEnvironment.project
        KotlinCoreEnvironment.registerProjectServices(project)
        KotlinCoreEnvironment.registerPluginExtensionPoints(project)
        
        val jdkHome = resolveJdkHome(kotlinProject)
        KotlinLogger.INSTANCE.logInfo("Using JDK home ${jdkHome ?: "<not found>"} for ${kotlinProject.projectDirectory.path}")
        
        with (project) {
            registerService(ModuleVisibilityManager::class.java, CliModuleVisibilityManagerImpl(true))
            registerService(NullableNotNullManager::class.java, KotlinNullableNotNullManager(project, kotlinProject))
            val javaFileManager = project.getService(JavaFileManager::class.java) as KotlinCliJavaFileManagerImpl
            registerService(CoreJavaFileManager::class.java, javaFileManager as CoreJavaFileManager)
            
            val cliTraceHolder = CliTraceHolder(project)
            registerService(CodeAnalyzerInitializer::class.java, cliTraceHolder)
            registerService(CliTraceHolder::class.java, cliTraceHolder)
            
            registerService(KotlinLightClassManager::class.java, KotlinLightClassManager(kotlinProject))
            registerService(BuiltInsReferenceResolver::class.java, BuiltInsReferenceResolver(project))
            registerService(KotlinCacheService::class.java, KotlinCacheServiceImpl(project, kotlinProject))
            val virtualFileFinderFactory = NetBeansVirtualFileFinderFactory(kotlinProject)
            registerService(VirtualFileFinderFactory::class.java, virtualFileFinderFactory)
            registerService(MetadataFinderFactory::class.java, virtualFileFinderFactory)
            registerService(ImportInsertHelper::class.java, KotlinImportInserterHelper())
            
            registerService(ExternalAnnotationsManager::class.java, MockExternalAnnotationsManager())
            registerService(InferredAnnotationsManager::class.java, MockInferredAnnotationsManager())
            
            val javaModuleFinder = CliJavaModuleFinder(jdkHome, configuration, javaFileManager, project, null)
            registerService(
                JavaModuleResolver::class.java,
                CliJavaModuleResolver(JavaModuleGraph(javaModuleFinder), emptyList(), emptyList(), project)
            )
        }
        
        configuration.put<String>(CommonConfigurationKeys.MODULE_NAME, project.name)
        
        configureClasspath(kotlinProject)
        
        CACHED_ENVIRONMENT.put(kotlinProject, this)
        KotlinLogger.INSTANCE.logInfo("KotlinEnvironment init: ${(System.nanoTime() - startTime)} ns")
    }
    
    private fun registerProjectExtensionPoints(area: ExtensionsArea) {
        KotlinCoreEnvironment.registerProjectExtensionPoints(area)
    }
    
    fun configureClasspath(kotlinProject: NBProject) {
        val classpath = ProjectUtils.getClasspath(kotlinProject).toMutableSet()
        KotlinLogger.INSTANCE.logInfo("Project ${kotlinProject.projectDirectory.path} classpath is $classpath")

        // Fallback: Ensure kotlin-stdlib is present so built-in declarations (kotlin.Array, kotlin.String)
        // are always available even before the build tool (e.g. Gradle) finishes its initial sync.
        val hasStdlib = classpath.any { it.contains("kotlin-stdlib") }
        if (!hasStdlib) {
            try {
                val codeSource = KotlinVersion::class.java.protectionDomain?.codeSource?.location
                if (codeSource != null) {
                    val file = File(codeSource.toURI())
                    if (file.exists()) {
                        classpath.add(file.absolutePath)
                        KotlinLogger.INSTANCE.logInfo("Added fallback kotlin-stdlib from plugin: ${file.absolutePath}")
                    }
                }
            } catch (e: Exception) {
                KotlinLogger.INSTANCE.logWarning("Could not resolve fallback kotlin-stdlib: $e")
            }
        }

        classpath.forEach {
            if (it.endsWith("!/")) {
                addToClasspath(it.split("!/")[0].substringAfter("file:"), null)
            } else {
                addToClasspath(it, null)
            }
        }
        val javaFileManager = project.getService(JavaFileManager::class.java) as? KotlinCliJavaFileManagerImpl
        val javaModuleFinder = if (javaFileManager != null) CliJavaModuleFinder(resolveJdkHome(kotlinProject), configuration, javaFileManager, project, null) else null
        if (javaFileManager != null && javaModuleFinder != null) {
            javaFileManager.initialize(
                index,
                emptyList(),
                org.jetbrains.kotlin.cli.jvm.index.SingleJavaFileRootsIndex(emptyList()),
                false,
                null
            )
        }
    }
    
    private fun createJavaCoreApplicationEnvironment(disposable: Disposable): JavaCoreApplicationEnvironment {
        return KotlinCoreEnvironment.getOrCreateApplicationEnvironmentForProduction(disposable, configuration)
    }
    
    /**
     * Resolves the JDK used by the project and publishes it in the compiler configuration.
     * Without it [CliJavaModuleFinder] has no system modules, so classes from java.base
     * (like java.util.zip.CRC32C) are reported as missing from the system module path.
     */
    private fun resolveJdkHome(kotlinProject: NBProject): File? {
        val jdkHome = getProjectJavaPlatform(kotlinProject)
            ?.installFolders
            ?.firstOrNull()
            ?.let { FileUtil.toFile(it) }
        
        if (jdkHome != null) {
            configuration.put(JVMConfigurationKeys.JDK_HOME, jdkHome)
        }
        
        return jdkHome
    }
    
    private fun getProjectJavaPlatform(kotlinProject: NBProject): JavaPlatform? {
        val provider = kotlinProject.getLookup().lookup(ClassPathProvider::class.java)
        if (provider != null) {
            try {
                // Maven (and other project types) expose their Java platform through this method,
                // which is not part of the public ClassPathProvider contract.
                val platform = provider.javaClass.getMethod("getJavaPlatform").invoke(provider) as? JavaPlatform
                if (platform != null) return platform
            } catch (e: ReflectiveOperationException) {
                KotlinLogger.INSTANCE.logInfo("Cannot get the Java platform of ${kotlinProject.projectDirectory.path}: $e")
            }
        }
        
        return JavaPlatformManager.getDefault().defaultPlatform
    }
    
    private fun addToClasspath(path: String, rootType: JavaRoot.RootType?) {
        val file = File(path)
        if (file.isFile) {
            val jarFile = applicationEnvironment.jarFileSystem.findFileByPath("$path!/") ?: return
            projectEnvironment.addJarToClassPath(file)
            val type = rootType ?: JavaRoot.RootType.BINARY
            
            roots.add(JavaRoot(jarFile, type, null))
        } else {
            val root = applicationEnvironment.localFileSystem.findFileByPath(path) ?: return
            projectEnvironment.addSourcesToClasspath(root)
            val type = rootType ?: JavaRoot.RootType.SOURCE
            
            roots.add(JavaRoot(root, type, null))
        }
    }
    
    fun getVirtualFile(location: String) = applicationEnvironment.localFileSystem.findFileByPath(location)

    fun getVirtualFileInJar(pathToJar: String, relativePath: String): VirtualFile? {
        val decodedPathToJar = URLDecoder.decode(pathToJar, "UTF-8") ?: pathToJar
        val decodedRelativePath = URLDecoder.decode(relativePath, "UTF-8") ?: relativePath
        
        return applicationEnvironment.jarFileSystem.findFileByPath("$decodedPathToJar!/$decodedRelativePath")
    }
}