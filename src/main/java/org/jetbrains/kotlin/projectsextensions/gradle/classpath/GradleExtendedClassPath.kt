/** *****************************************************************************
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
package org.jetbrains.kotlin.projectsextensions.gradle.classpath

import org.jetbrains.kotlin.log.KotlinLogger
import org.jetbrains.kotlin.projectsextensions.ClassPathExtender
import org.netbeans.api.java.classpath.ClassPath
import org.netbeans.api.project.Project
import org.netbeans.spi.java.classpath.ClassPathProvider
import org.netbeans.spi.java.classpath.support.ClassPathSupport

/**
 *
 * @author baratynskiy
 */
class GradleExtendedClassPath(val project: Project) : ClassPathExtender {

    override fun getProjectSourcesClassPath(type: String): ClassPath {
        val srcFolder = project.projectDirectory.getFileObject("src/main/kotlin")
            ?: project.projectDirectory.getFileObject("src/main/java")
            ?: project.projectDirectory.getFileObject("src")
            ?: project.projectDirectory

        val testFolder = project.projectDirectory.getFileObject("src/test/kotlin")
            ?: project.projectDirectory.getFileObject("src/test/java")
            ?: project.projectDirectory.getFileObject("src/test")

        // 1. Try modern Apache NetBeans ProjectSourcesClassPathProvider from lookup via reflection.
        // getProjectClassPath(type) includes both main and test configurations (excludeTests = false),
        // ensuring test dependencies like kotlin-test are present in the compiler environment.
        try {
            for (item in project.lookup.lookupAll(Any::class.java)) {
                // Try getProjectClassPath(String) first (includes test dependencies)
                val getProjectClassPathMethod = try {
                    item.javaClass.getMethod("getProjectClassPath", String::class.java)
                } catch (e: NoSuchMethodException) {
                    null
                }
                if (getProjectClassPathMethod != null) {
                    val array = getProjectClassPathMethod.invoke(item, type) as? Array<ClassPath>
                    if (array != null && array.isNotEmpty()) {
                        val nonEmpties = array.filter { it.entries().isNotEmpty() }
                        if (nonEmpties.isNotEmpty()) {
                            return if (nonEmpties.size == 1) nonEmpties[0] else ClassPathSupport.createProxyClassPath(*nonEmpties.toTypedArray())
                        }
                    }
                }

                // Fallback to getProjectSourcesClassPath(String)
                val getSourcesMethod = try {
                    item.javaClass.getMethod("getProjectSourcesClassPath", String::class.java)
                } catch (e: NoSuchMethodException) {
                    null
                }
                if (getSourcesMethod != null) {
                    val result = getSourcesMethod.invoke(item, type) as? ClassPath
                    if (result != null && result.entries().isNotEmpty()) {
                        return result
                    }
                }
            }
        } catch (ex: Exception) {
            KotlinLogger.INSTANCE.logWarning("Error querying ProjectSourcesClassPathProvider for Gradle: ${ex.message}")
        }

        // 2. Query ClassPathProvider from project lookup (combining main and test source folders)
        try {
            val classPathProvider = project.lookup.lookup(ClassPathProvider::class.java)
            if (classPathProvider != null) {
                // Try old 2016 Kephale plugin method "getClassPaths"
                try {
                    val method = classPathProvider.javaClass.getMethod("getClassPaths", String::class.java)
                    val result = method.invoke(classPathProvider, type) as? ClassPath
                    if (result != null && result.entries().isNotEmpty()) {
                        return result
                    }
                } catch (ignored: NoSuchMethodException) {
                }

                var cp = classPathProvider.findClassPath(srcFolder, type)
                val testCp = if (testFolder != null) classPathProvider.findClassPath(testFolder, type) else null

                if (cp != null && testCp != null && cp != testCp) {
                    return ClassPathSupport.createProxyClassPath(cp, testCp)
                } else if (cp != null && cp.entries().isNotEmpty()) {
                    return cp
                } else if (testCp != null && testCp.entries().isNotEmpty()) {
                    return testCp
                }
            }
        } catch (ex: Exception) {
            KotlinLogger.INSTANCE.logWarning("Error querying ClassPathProvider for Gradle: ${ex.message}")
        }

        // 3. Fallback: query ClassPath.getClassPath for src & test folders
        var cp = ClassPath.getClassPath(srcFolder, type)
        val testCp = if (testFolder != null) ClassPath.getClassPath(testFolder, type) else null
        if (cp != null && testCp != null && cp != testCp) {
            return ClassPathSupport.createProxyClassPath(cp, testCp)
        } else if (cp != null && cp.entries().isNotEmpty()) {
            return cp
        } else if (testCp != null && testCp.entries().isNotEmpty()) {
            return testCp
        }

        // 4. Fallback for BOOT classpath: use default Java platform bootstrap libraries
        if (type == ClassPath.BOOT) {
            try {
                val defaultPlatform = org.netbeans.api.java.platform.JavaPlatformManager.getDefault().defaultPlatform
                if (defaultPlatform != null) {
                    return defaultPlatform.bootstrapLibraries
                }
            } catch (ignored: Exception) {
            }
        }

        return ClassPath.EMPTY
    }

}