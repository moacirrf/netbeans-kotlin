package org.jetbrains.kotlin.resolve

import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.cli.jvm.compiler.JvmPackagePartProvider
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.load.kotlin.PackagePartProvider
import org.jetbrains.kotlin.model.KotlinEnvironment
import org.netbeans.api.project.Project

class KotlinPackagePartProvider(
    val project: Project,
    scope: GlobalSearchScope = GlobalSearchScope.allScope(KotlinEnvironment.getEnvironment(project).project)
) : PackagePartProvider by JvmPackagePartProvider(LanguageVersionSettingsImpl.DEFAULT, scope).apply({
    val env = KotlinEnvironment.getEnvironment(project)
    addRoots(env.roots.toList(), env.configuration)
})