/*******************************************************************************
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.kotlin.projectsextensions.maven

import org.jetbrains.kotlin.utils.ProjectUtils
import org.jetbrains.kotlin.utils.hasMain
import org.jetbrains.kotlin.utils.mainClassName
import org.netbeans.api.project.Project
import org.netbeans.spi.project.ActionProvider
import org.openide.filesystems.FileObject
import org.openide.loaders.DataObject
import org.openide.nodes.Node
import org.openide.util.Lookup

private fun extractFileObject(lookup: Lookup): FileObject? {
    val f = lookup.lookup(FileObject::class.java)
    if (f != null) return f
    val dobj = lookup.lookup(DataObject::class.java)
    if (dobj != null) return dobj.primaryFile
    val node = lookup.lookup(Node::class.java)
    if (node != null) {
        val nodeFo = node.lookup.lookup(FileObject::class.java)
        if (nodeFo != null) return nodeFo
        val nodeDobj = node.lookup.lookup(DataObject::class.java)
        if (nodeDobj != null) return nodeDobj.primaryFile
    }
    return null
}

fun convert(action: String, lookup: Lookup): String? {
    if (ActionProvider.COMMAND_RUN_SINGLE == action || 
        ActionProvider.COMMAND_DEBUG_SINGLE == action ||
        ActionProvider.COMMAND_PROFILE_SINGLE == action) {
        val f = extractFileObject(lookup)

        if (f != null && ("text/x-kt" == f.mimeType || f.ext == "kt")) {
            val ktFile = ProjectUtils.getKtFile(f) ?: return null
            if (!ktFile.hasMain()) return null

            return "$action.main"
        }
    }

    return null
}

fun createReplacements(action: String, lookup: Lookup): Map<String, String> {
    val f = extractFileObject(lookup)

    if (f != null && ("text/x-kt" == f.mimeType || f.ext == "kt")) {
        val ktFile = ProjectUtils.getKtFile(f) ?: return emptyMap()
        val fqName = ktFile.mainClassName() ?: return emptyMap()
        val simpleName = ktFile.mainClassName(simple = true) ?: fqName
        
        return mapOf(
            "packageClassName" to fqName,
            "className" to simpleName
        )
    }

    // Run Project / Debug Project / Profile Project
    if (ActionProvider.COMMAND_RUN == action || 
        ActionProvider.COMMAND_DEBUG == action || 
        ActionProvider.COMMAND_PROFILE == action) {
        val project = lookup.lookup(Project::class.java)
        if (project != null) {
            val sourceFiles = ProjectUtils.getSourceFiles(project)
            val mainFile = sourceFiles.firstOrNull { it.hasMain() }
            val fqName = mainFile?.mainClassName()
            if (fqName != null) {
                val simpleName = mainFile.mainClassName(simple = true) ?: fqName
                return mapOf(
                    "packageClassName" to fqName,
                    "className" to simpleName
                )
            }
        }
    }

    return emptyMap()
}