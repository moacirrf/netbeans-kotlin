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
package org.jetbrains.kotlin.filesystem.lightclasses

import java.io.File
import java.util.regex.Pattern
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.codegen.AsmUtil
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.ConstructorDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.fileClasses.JvmFileClassUtil
import org.jetbrains.kotlin.filesystem.KotlinLightClassManager
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.load.kotlin.PackagePartClassUtils
import org.jetbrains.kotlin.load.kotlin.computeJvmDescriptor
import org.jetbrains.kotlin.load.kotlin.computeJvmDescriptorWithoutName
import org.jetbrains.kotlin.log.KotlinLogger
import org.jetbrains.kotlin.builtins.jvm.JavaToKotlinClassMap
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameUnsafe
import org.jetbrains.kotlin.resolve.descriptorUtil.isCompanionObject
import org.jetbrains.kotlin.resolve.jvm.diagnostics.JvmDeclarationOrigin
import org.jetbrains.kotlin.utils.ProjectUtils
import org.jetbrains.org.objectweb.asm.Opcodes
import org.netbeans.api.project.Project
import org.openide.filesystems.FileObject

private val ClassDescriptor.internalName: String
    get() {
        val containing = containingDeclaration
        return if (containing is ClassDescriptor) {
            "${containing.internalName}\$${name.asString()}"
        } else {
            fqNameSafe.asString().replace('.', '/')
        }
    }

object KotlinLightClassGeneration {

    private fun buildLightClasses(
        analysisResult: AnalysisResult,
        ktFiles: List<KtFile>
    ): List<ByteArray> {
        val bindingContext = analysisResult.bindingContext
        val factory = LightClassBuilderFactory()
        val result = arrayListOf<ByteArray>()

        for (ktFile in ktFiles) {
            for (declaration in ktFile.declarations) {
                if (declaration is KtClassOrObject) {
                    generateClass(declaration, bindingContext, factory, result)
                }
            }
            if (PackagePartClassUtils.getFilesWithCallables(listOf(ktFile)).isNotEmpty()) {
                generatePackagePart(ktFile, bindingContext, factory, result)
            }
        }

        return result
    }

    private fun generateClass(
        classOrObject: KtClassOrObject,
        bindingContext: BindingContext,
        factory: LightClassBuilderFactory,
        result: MutableList<ByteArray>
    ) {
        val descriptor = bindingContext[BindingContext.CLASS, classOrObject] ?: return
        val builder = factory.newClassBuilder(JvmDeclarationOrigin.NO_ORIGIN)
        val internalName = descriptor.internalName

        var access = AsmUtil.getVisibilityAccessFlag(descriptor.visibility.delegate) ?: Opcodes.ACC_PUBLIC
        access = access or when (descriptor.kind) {
            ClassKind.INTERFACE -> Opcodes.ACC_INTERFACE or Opcodes.ACC_ABSTRACT
            ClassKind.ENUM_CLASS -> Opcodes.ACC_ENUM or Opcodes.ACC_SUPER
            ClassKind.ANNOTATION_CLASS -> Opcodes.ACC_ANNOTATION or Opcodes.ACC_INTERFACE or Opcodes.ACC_ABSTRACT
            else -> Opcodes.ACC_SUPER
        }
        if (descriptor.modality == Modality.FINAL && descriptor.kind != ClassKind.ENUM_CLASS) {
            access = access or Opcodes.ACC_FINAL
        } else if (descriptor.modality == Modality.ABSTRACT) {
            access = access or Opcodes.ACC_ABSTRACT
        }
        if (descriptor.containingDeclaration is ClassDescriptor) {
            access = access or Opcodes.ACC_STATIC
        }

        var superName = if (descriptor.kind == ClassKind.ENUM_CLASS) "java/lang/Enum" else "java/lang/Object"
        val interfaces = arrayListOf<String>()
        for (supertype in descriptor.typeConstructor.supertypes) {
            val superClass = supertype.constructor.declarationDescriptor as? ClassDescriptor ?: continue
            if (superClass.kind == ClassKind.INTERFACE) {
                interfaces.add(superClass.internalName)
            } else if (descriptor.kind != ClassKind.ENUM_CLASS) {
                val name = superClass.internalName
                if (name != "kotlin/Any") {
                    superName = name
                }
            }
        }

        val classSignature = if (descriptor.declaredTypeParameters.isNotEmpty()) {
            buildString {
                append("<")
                for (tp in descriptor.declaredTypeParameters) {
                    append(tp.name.asString())
                    val upperBounds = tp.upperBounds
                    if (upperBounds.isNotEmpty()) {
                        for (ub in upperBounds) {
                            val ubClass = ub.constructor.declarationDescriptor as? ClassDescriptor
                            val javaClassId = ubClass?.let { JavaToKotlinClassMap.mapKotlinToJava(it.fqNameUnsafe) }
                            val ubName = javaClassId?.asSingleFqName()?.asString()?.replace('.', '/')
                                ?: ubClass?.internalName
                                ?: "java/lang/Object"
                            append(":L$ubName;")
                        }
                    } else {
                        append(":Ljava/lang/Object;")
                    }
                }
                append(">L$superName;")
                for (iface in interfaces) {
                    append("L$iface;")
                }
            }
        } else if (descriptor.kind == ClassKind.ENUM_CLASS) {
            "Ljava/lang/Enum<L$internalName;>;"
        } else null

        builder.defineClass(
            Opcodes.V1_8,
            access,
            internalName,
            classSignature,
            superName,
            interfaces.toTypedArray()
        )

        val containing = descriptor.containingDeclaration
        if (containing is ClassDescriptor) {
            builder.visitInnerClass(internalName, containing.internalName, descriptor.name.asString(), access)
        }
        for (decl in classOrObject.declarations) {
            if (decl is KtClassOrObject && decl !is KtEnumEntry) {
                val innerDescriptor = bindingContext[BindingContext.CLASS, decl]
                if (innerDescriptor != null) {
                    var innerAccess = AsmUtil.getVisibilityAccessFlag(innerDescriptor.visibility.delegate) ?: Opcodes.ACC_PUBLIC
                    innerAccess = innerAccess or Opcodes.ACC_STATIC
                    if (innerDescriptor.modality == Modality.FINAL) innerAccess = innerAccess or Opcodes.ACC_FINAL
                    builder.visitInnerClass(innerDescriptor.internalName, internalName, innerDescriptor.name.asString(), innerAccess)
                }
            }
        }

        if (descriptor.kind == ClassKind.OBJECT && !descriptor.isCompanionObject) {
            builder.newField(
                JvmDeclarationOrigin.NO_ORIGIN,
                Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL,
                "INSTANCE",
                "L$internalName;",
                null,
                null
            ).visitEnd()
        }

        val companion = descriptor.companionObjectDescriptor
        if (companion != null) {
            var companionAccess = Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC
            if (companion.modality == Modality.FINAL) companionAccess = companionAccess or Opcodes.ACC_FINAL
            builder.visitInnerClass(companion.internalName, internalName, companion.name.asString(), companionAccess)
            builder.newField(
                JvmDeclarationOrigin.NO_ORIGIN,
                Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL,
                companion.name.asString(),
                "L${companion.internalName};",
                null,
                null
            ).visitEnd()
        }

        if (descriptor.kind == ClassKind.ENUM_CLASS) {
            for (entry in descriptor.unsubstitutedMemberScope.getContributedDescriptors()) {
                if (entry is ClassDescriptor && entry.kind == ClassKind.ENUM_ENTRY) {
                    builder.newField(
                        JvmDeclarationOrigin.NO_ORIGIN,
                        Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL or Opcodes.ACC_ENUM,
                        entry.name.asString(),
                        "L$internalName;",
                        null,
                        null
                    ).visitEnd()
                }
            }
        }

        for (member in descriptor.unsubstitutedMemberScope.getContributedDescriptors()) {
            when (member) {
                is FunctionDescriptor -> {
                    if (member.kind.isReal) {
                        emitMethod(
                            builder,
                            member,
                            member.name.asString(),
                            isStatic = false,
                            isInterface = descriptor.kind == ClassKind.INTERFACE
                        )
                    }
                }
                is PropertyDescriptor -> {
                    if (member.kind.isReal) {
                        emitProperty(builder, member, isStatic = false, isInterface = descriptor.kind == ClassKind.INTERFACE)
                    }
                }
            }
        }

        for (constructor in descriptor.constructors) {
            emitMethod(builder, constructor, "<init>", isStatic = false, isInterface = false)
        }

        if (descriptor.kind == ClassKind.ENUM_CLASS) {
            builder.newMethod(
                JvmDeclarationOrigin.NO_ORIGIN,
                Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                "values",
                "()[L$internalName;",
                null,
                null
            ).visitEnd()
            builder.newMethod(
                JvmDeclarationOrigin.NO_ORIGIN,
                Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                "valueOf",
                "(Ljava/lang/String;)L$internalName;",
                null,
                null
            ).visitEnd()
        }

        builder.done(false)
        result.add(factory.asBytes(builder))

        for (decl in classOrObject.declarations) {
            if (decl is KtClassOrObject && decl !is KtEnumEntry) {
                generateClass(decl, bindingContext, factory, result)
            }
        }
    }

    private fun generatePackagePart(
        ktFile: KtFile,
        bindingContext: BindingContext,
        factory: LightClassBuilderFactory,
        result: MutableList<ByteArray>
    ) {
        val builder = factory.newClassBuilder(JvmDeclarationOrigin.NO_ORIGIN)
        val internalName = JvmFileClassUtil.getFileClassInternalName(ktFile)
        builder.defineClass(
            Opcodes.V1_8,
            Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_SUPER,
            internalName,
            null,
            "java/lang/Object",
            emptyArray()
        )

        for (decl in ktFile.declarations) {
            when (decl) {
                is KtNamedFunction -> {
                    val descriptor = bindingContext[BindingContext.FUNCTION, decl] ?: continue
                    emitMethod(builder, descriptor, descriptor.name.asString(), isStatic = true, isInterface = false)
                }
                is KtProperty -> {
                    val descriptor = bindingContext[BindingContext.VARIABLE, decl] as? PropertyDescriptor ?: continue
                    emitProperty(builder, descriptor, isStatic = true, isInterface = false)
                }
            }
        }

        builder.done(false)
        result.add(factory.asBytes(builder))
    }

    private fun emitMethod(
        builder: org.jetbrains.kotlin.codegen.ClassBuilder,
        descriptor: FunctionDescriptor,
        name: String,
        isStatic: Boolean,
        isInterface: Boolean
    ) {
        val jvmDesc = descriptor.computeJvmDescriptor(withReturnType = true, withName = false)
        var access = AsmUtil.getVisibilityAccessFlag(descriptor.visibility.delegate) ?: Opcodes.ACC_PUBLIC
        if (descriptor is ConstructorDescriptor) {
            val containingClass = descriptor.containingDeclaration as? ClassDescriptor
            if (containingClass?.kind == ClassKind.OBJECT) {
                access = Opcodes.ACC_PRIVATE
            } else if (containingClass?.kind == ClassKind.ENUM_CLASS) {
                access = Opcodes.ACC_PROTECTED
            }
        }
        if (isStatic) access = access or Opcodes.ACC_STATIC
        if (isInterface || descriptor.modality == Modality.ABSTRACT) {
            access = access or Opcodes.ACC_ABSTRACT
        } else if (descriptor.modality == Modality.FINAL && descriptor !is ConstructorDescriptor) {
            access = access or Opcodes.ACC_FINAL
        }
        val psi = DescriptorToSourceUtils.descriptorToDeclaration(descriptor)
        LightClassBuilderFactory.saveJvmSignature(psi, name, jvmDesc)
        val mv = builder.newMethod(JvmDeclarationOrigin.NO_ORIGIN, access, name, jvmDesc, null, null)
        mv.visitEnd()
    }

    private fun emitProperty(
        builder: org.jetbrains.kotlin.codegen.ClassBuilder,
        descriptor: PropertyDescriptor,
        isStatic: Boolean,
        isInterface: Boolean
    ) {
        val typeDesc = descriptor.computeJvmDescriptorWithoutName()
        val rawType = if (typeDesc.startsWith("()")) typeDesc.removePrefix("()") else typeDesc
        val getterDesc = "()$rawType"
        val psi = DescriptorToSourceUtils.descriptorToDeclaration(descriptor)

        if (!isInterface) {
            var fieldAccess = Opcodes.ACC_PRIVATE
            if (!descriptor.isVar) fieldAccess = fieldAccess or Opcodes.ACC_FINAL
            if (isStatic) fieldAccess = fieldAccess or Opcodes.ACC_STATIC
            builder.newField(JvmDeclarationOrigin.NO_ORIGIN, fieldAccess, descriptor.name.asString(), rawType, null, null).visitEnd()
        }

        val getterName = JvmAbi.getterName(descriptor.name.asString())
        var getterAccess = AsmUtil.getVisibilityAccessFlag(descriptor.visibility.delegate) ?: Opcodes.ACC_PUBLIC
        if (isStatic) getterAccess = getterAccess or Opcodes.ACC_STATIC
        if (isInterface || descriptor.modality == Modality.ABSTRACT) {
            getterAccess = getterAccess or Opcodes.ACC_ABSTRACT
        } else if (descriptor.modality == Modality.FINAL) {
            getterAccess = getterAccess or Opcodes.ACC_FINAL
        }
        LightClassBuilderFactory.saveJvmSignature(psi, getterName, getterDesc)
        builder.newMethod(JvmDeclarationOrigin.NO_ORIGIN, getterAccess, getterName, getterDesc, null, null).visitEnd()

        if (descriptor.isVar) {
            val setterName = JvmAbi.setterName(descriptor.name.asString())
            val setterDesc = "($rawType)V"
            var setterAccess = getterAccess
            LightClassBuilderFactory.saveJvmSignature(psi, setterName, setterDesc)
            builder.newMethod(JvmDeclarationOrigin.NO_ORIGIN, setterAccess, setterName, setterDesc, null, null).visitEnd()
        }
    }

    fun getByteCode(
        file: FileObject,
        project: Project?,
        analysisResult: AnalysisResult
    ): List<ByteArray> {
        if (project == null) return emptyList()

        val code = arrayListOf<ByteArray>()

        val manager = KotlinLightClassManager.getInstance(project)
        manager.computeLightClassesSources()

        manager.getLightClassesPaths(file).forEach {
            val lightClass = File("${project.projectDirectory.path}${ProjectUtils.FILE_SEPARATOR}$it")
            val ktFiles = manager.getSourceFiles(lightClass)
            if (ktFiles.isNotEmpty()) {
                try {
                    code.addAll(buildLightClasses(analysisResult, ktFiles))
                } catch (ex: Exception) {
                    KotlinLogger.INSTANCE.logWarning("Couldn't create light class for ${file.path}")
                    return emptyList()
                }
            }
        }

        return code
    }
}