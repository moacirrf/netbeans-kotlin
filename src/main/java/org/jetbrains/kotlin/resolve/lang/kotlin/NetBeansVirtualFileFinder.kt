/**
 * *****************************************************************************
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
 ******************************************************************************
 */
package org.jetbrains.kotlin.resolve.lang.kotlin

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import java.io.InputStream
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.cli.jvm.compiler.CliVirtualFileFinder
import org.jetbrains.kotlin.cli.jvm.index.JvmDependenciesIndex
import org.jetbrains.kotlin.load.java.structure.JavaClass
import org.jetbrains.kotlin.load.kotlin.KotlinBinaryClassCache
import org.jetbrains.kotlin.load.kotlin.KotlinClassFinder
import org.jetbrains.kotlin.load.kotlin.MetadataFinderFactory
import org.jetbrains.kotlin.load.kotlin.VirtualFileFinder
import org.jetbrains.kotlin.load.kotlin.VirtualFileFinderFactory
import org.jetbrains.kotlin.log.KotlinLogger
import org.jetbrains.kotlin.metadata.deserialization.MetadataVersion
import org.jetbrains.kotlin.model.KotlinEnvironment
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.projectsextensions.KotlinProjectHelper.getFullClassPath
import org.jetbrains.kotlin.resolve.lang.java.computeClassId
import org.jetbrains.kotlin.resolve.lang.java.structure.NetBeansJavaClass
import org.jetbrains.kotlin.serialization.deserialization.KotlinMetadataFinder
import org.netbeans.api.project.Project
import org.openide.filesystems.FileObject
import org.openide.filesystems.FileStateInvalidException

class NetBeansVirtualFileFinder(
    private val project: Project,
    private val scope: GlobalSearchScope
) : VirtualFileFinder() {

    val index: JvmDependenciesIndex
        get() = KotlinEnvironment.getEnvironment(project).index

    private val cliFinder: CliVirtualFileFinder by lazy {
        CliVirtualFileFinder(index, scope, false, null)
    }

    private fun isClassFileName(name: String?): Boolean {
        if (name == null) return false

        val suffixClass = ".class".toCharArray()
        val suffixClass2 = ".CLASS".toCharArray()
        val nameLength = name.length
        val suffixLength = suffixClass.size
        if (nameLength < suffixLength) return false

        for (i in 0 until suffixLength) {
            val c = name[nameLength - i - 1]
            val suffixIndex = suffixLength - i - 1

            if (c != suffixClass[suffixIndex] && c != suffixClass2[suffixIndex]) return false
        }
        return true
    }

    private fun getJarPath(file: FileObject) = file.fileSystem?.displayName

    override fun findSourceOrBinaryVirtualFile(classId: ClassId): VirtualFile? = findVirtualFileWithHeader(classId)

    override fun findVirtualFileWithHeader(classId: ClassId): VirtualFile? {
        val proxy = project.getFullClassPath()

        val classFqName = if (classId.isNestedClass) {
            val className = classId.shortClassName.asString()
            val fqName = classId.asSingleFqName().asString()
            StringBuilder(
                fqName.substring(0, fqName.length - className.length - 1).replace(".", "/")
            ).append("$").append(className).append(".class").toString()
        } else "${classId.asSingleFqName().asString().replace(".", "/")}.class"

        val resource = proxy?.findResource(classFqName) ?: return if (isClassFileName(classFqName)) {
            KotlinEnvironment.getEnvironment(project).getVirtualFile(classFqName)
        } else null

        val path = resource.toURL().path
        if (path.contains("!/")) {
            try {
                val pathToJar = getJarPath(resource) ?: return null
                if (!java.io.File(java.net.URLDecoder.decode(pathToJar, "UTF-8")).isFile) return null
                val splittedPath = path.split("!/")
                if (splittedPath.size < 2) return null

                return KotlinEnvironment.getEnvironment(project).getVirtualFileInJar(pathToJar, splittedPath[1])
            } catch (ex: FileStateInvalidException) {
                KotlinLogger.INSTANCE.logException("Can't get file in jar", ex)
                return null
            }
        }

        if (isClassFileName(path)) {
            return KotlinEnvironment.getEnvironment(project).getVirtualFile(path)
        }
        return cliFinder.findVirtualFileWithHeader(classId)
    }

    private fun classFileName(jClass: JavaClass): String {
        val outerClass = jClass.outerClass ?: return jClass.name.asString()
        return "${classFileName(outerClass)}$${jClass.name.asString()}"
    }

    override fun findKotlinClassOrContent(javaClass: JavaClass, metadataVersion: MetadataVersion): KotlinClassFinder.Result? {
        javaClass.fqName ?: return null
        val classId = (javaClass as? NetBeansJavaClass)?.elementHandle?.computeClassId(project) ?: return null
        var file: VirtualFile? = findVirtualFileWithHeader(classId) ?: return null

        if (javaClass.outerClass != null) {
            val classFileName = "${classFileName(javaClass)}.class"
            file = file!!.parent?.findChild(classFileName)
            if (file == null) {
                return null
            }
        }
        return KotlinBinaryClassCache.getKotlinBinaryClassOrClassFileContent(file!!, metadataVersion)
    }

    override fun findBuiltInsData(packageFqName: FqName): InputStream? =
        cliFinder.findBuiltInsData(packageFqName)

    override fun findMetadata(classId: ClassId): InputStream? =
        cliFinder.findMetadata(classId)

    override fun findMetadataTopLevelClassesInPackage(packageFqName: FqName): Set<String>? =
        cliFinder.findMetadataTopLevelClassesInPackage(packageFqName)

    override fun hasMetadataPackage(fqName: FqName): Boolean =
        cliFinder.hasMetadataPackage(fqName)
}

class NetBeansVirtualFileFinderFactory(private val project: Project) : VirtualFileFinderFactory, MetadataFinderFactory {
    override fun create(scope: GlobalSearchScope): VirtualFileFinder = NetBeansVirtualFileFinder(project, scope)

    override fun create(project: com.intellij.openapi.project.Project, module: ModuleInfo): VirtualFileFinder =
        NetBeansVirtualFileFinder(this.project, GlobalSearchScope.allScope(project))
}