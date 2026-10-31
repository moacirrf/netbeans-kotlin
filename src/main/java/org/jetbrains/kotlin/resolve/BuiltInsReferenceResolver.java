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
package org.jetbrains.kotlin.resolve;

import com.google.common.collect.Sets;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import java.util.Collections;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.builtins.DefaultBuiltIns;
import org.jetbrains.kotlin.builtins.KotlinBuiltIns;
import org.jetbrains.kotlin.builtins.StandardNames;
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl;
import org.jetbrains.kotlin.container.DslKt;
import org.jetbrains.kotlin.container.StorageComponentContainer;
import org.jetbrains.kotlin.context.ContextKt;
import org.jetbrains.kotlin.context.MutableModuleContext;
import org.jetbrains.kotlin.descriptors.ClassDescriptor;
import org.jetbrains.kotlin.descriptors.ConstructorDescriptor;
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor;
import org.jetbrains.kotlin.descriptors.MemberDescriptor;
import org.jetbrains.kotlin.descriptors.ModuleDescriptor;
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor;
import org.jetbrains.kotlin.descriptors.PackageViewDescriptor;
import org.jetbrains.kotlin.frontend.di.InjectionKt;
import org.jetbrains.kotlin.name.Name;
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.renderer.DescriptorRenderer;
import org.jetbrains.kotlin.resolve.descriptorUtil.DescriptorUtilsKt;
import org.jetbrains.kotlin.resolve.jvm.platform.JvmPlatformAnalyzerServices;
import org.jetbrains.kotlin.resolve.lazy.ResolveSession;
import org.jetbrains.kotlin.resolve.lazy.declarations.FileBasedDeclarationProviderFactory;
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter;
import org.jetbrains.kotlin.resolve.scopes.MemberScope;
import org.jetbrains.kotlin.descriptors.FindClassInModuleKt;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.URLUtil;

import kotlin.collections.CollectionsKt;
import org.jetbrains.kotlin.model.KotlinEnvironment;
import org.jetbrains.kotlin.utils.ProjectUtils;

public class BuiltInsReferenceResolver {
    private static final String RUNTIME_SRC_JAR_PATH = ProjectUtils.buildLibPath("kotlin-stdlib-sources");

    private volatile ModuleDescriptor moduleDescriptor;
    private volatile PackageFragmentDescriptor builtinsPackageFragment = null;
    private final Project myProject;
    private volatile boolean initialized = false;

    public BuiltInsReferenceResolver(Project project) {
        myProject = project;
        // Initialization is deferred: this constructor is invoked while KotlinEnvironment
        // is still registering project services, so resolving here fails with
        // "getService(...) must not be null".
    }

    private void ensureInitialized() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    initialize();
                    initialized = true;
                }
            }
        }
    }
    
    @NotNull
    public static BuiltInsReferenceResolver getInstance(@NotNull org.netbeans.api.project.Project kotlinProject) {
        Project ideaProject = KotlinEnvironment.Companion.getEnvironment(kotlinProject).getProject();
        return ideaProject.getService(BuiltInsReferenceResolver.class);
    }

    private void initialize() {
        try {
            if (!areSourcesExist()) {
                return;
            }
            
            Set<KtFile> ktBuiltInsFiles = getBuiltInSourceFiles();
            if (ktBuiltInsFiles == null || ktBuiltInsFiles.isEmpty()) {
                return;
            }
            
            MutableModuleContext newModuleContext = ContextKt.ContextForNewModule(
                    ContextKt.ProjectContext(myProject, "BuiltInsReferenceResolver"),
                    Name.special("<built-ins resolver module>"), 
                    DefaultBuiltIns.getInstance(),
                    JvmPlatforms.INSTANCE.getDefaultJvmPlatform(),
                    Collections.emptyMap());
            newModuleContext.setDependencies(newModuleContext.getModule());
            
            FileBasedDeclarationProviderFactory declarationFactory = new FileBasedDeclarationProviderFactory(
                    newModuleContext.getStorageManager(), ktBuiltInsFiles);
            
            StorageComponentContainer container = InjectionKt.createContainerForLazyResolve(
                    newModuleContext, declarationFactory,
                    new BindingTraceContext(myProject), JvmPlatforms.INSTANCE.getDefaultJvmPlatform(),
                    JvmPlatformAnalyzerServices.INSTANCE, CompilerEnvironment.INSTANCE,
                    LanguageVersionSettingsImpl.DEFAULT);
            ResolveSession resolveSession = (ResolveSession) DslKt.getService(container, ResolveSession.class);
            
            newModuleContext.initializeModuleContents(resolveSession.getPackageFragmentProvider());
            
            PackageViewDescriptor packageView = newModuleContext.getModule().getPackage(
                    StandardNames.BUILT_INS_PACKAGE_FQ_NAME);
            List<PackageFragmentDescriptor> fragments = packageView.getFragments();
            if (!fragments.isEmpty()) {
                moduleDescriptor = newModuleContext.getModule();
                builtinsPackageFragment = fragments.get(0);
            }
        } catch (Throwable ex) {
            org.jetbrains.kotlin.log.KotlinLogger.INSTANCE.logWarning("Could not initialize BuiltInsReferenceResolver: " + ex.getMessage());
        }
    }

    @Nullable
    public Set<KtFile> getBuiltInSourceFiles() {
        VirtualFile vf = getSourceVirtualFile();
        if (vf == null) {
            return null;
        }
        
        PsiDirectory psiDirectory = PsiManager.getInstance(myProject).findDirectory(vf);
        if (psiDirectory == null) {
            return null;
        }
        return new HashSet<>(ContainerUtil.mapNotNull(psiDirectory.getFiles(), new Function<PsiFile, KtFile>() {
            @Override
            public KtFile fun(PsiFile file) {
                return file instanceof KtFile ? (KtFile) file : null;
            }
        }));
    }
    
    private void addFilesFromFolder(Set<KtFile> files, VirtualFile root){
        if (root == null) {
            return;
        }
        for (VirtualFile child : root.getChildren()){
            if (child.isDirectory()){
                addFilesFromFolder(files, child);
            }
        }
        
        PsiDirectory psiDirectory = PsiManager.getInstance(myProject).findDirectory(root);
        if (psiDirectory == null) {
            return;
        }
        files.addAll(ContainerUtil.mapNotNull(psiDirectory.getFiles(), new Function<PsiFile, KtFile>() {
            @Override
            public KtFile fun(PsiFile file) {
                return file instanceof KtFile ? (KtFile) file : null;
            }
        }));
        
    }
    
    public Set<KtFile> getAllFilesInKotlinSourceJar(){
        Set<KtFile> files = Sets.newHashSet();
        VirtualFile runtimeSrc = getSourceVirtualFile();
        if (runtimeSrc != null) {
            addFilesFromFolder(files, runtimeSrc);
        }
        return files;
    }
    
    @Nullable
    private VirtualFile getSourceVirtualFile() {
        java.io.File jarFile = new java.io.File(RUNTIME_SRC_JAR_PATH);
        if (!jarFile.isFile()) {
            return null;
        }
        String[] candidateInternalPaths = new String[] { "!/kotlin", "!/commonMain/kotlin" };
        for (String internalPath : candidateInternalPaths) {
            try {
                URL runtimeUrl = new URL("jar:file:" + jarFile.getAbsolutePath() + internalPath);
                String fromUrl = convertPathFromURL(runtimeUrl);
                VirtualFile vf = VirtualFileManager.getInstance().findFileByUrl(fromUrl);
                if (vf != null) {
                    return vf;
                }
            } catch (MalformedURLException ignored) {
            }
        }
        return null;
    }
    
    private boolean areSourcesExist() {
        return getSourceVirtualFile() != null;
    }

    //the method is a copy of com.intellij.openapi.vfs.VfsUtilCore.convertFromUrl(URL)
    private String convertPathFromURL(URL url) {
        String protocol = url.getProtocol();
        String path = url.getPath();
        if (protocol.equals("jar")) {
          if (StringUtil.startsWithConcatenation(path, "file", ":")) {
            try {
              URL subURL = new URL(path);
              path = subURL.getPath();
            }
            catch (MalformedURLException e) {
              throw new RuntimeException("URL parse unhandled exception", e);
            }
          }
          else {
            throw new RuntimeException(new IOException("URL parse error: " + url.toExternalForm()));
          }
        }
        if (SystemInfo.isWindows) {
          while (!path.isEmpty() && path.charAt(0) == '/') {
            path = path.substring(1, path.length());
          }
        }

        path = URLUtil.unescapePercentSequences(path);
        String fromUrl = protocol + "://" + path;
        return fromUrl;
    }

    @Nullable
    private DeclarationDescriptor findCurrentDescriptorForMember(@NotNull MemberDescriptor originalDescriptor) {
        DeclarationDescriptor containingDeclaration = findCurrentDescriptor(originalDescriptor.getContainingDeclaration());
        MemberScope memberScope = getMemberScope(containingDeclaration);
        if (memberScope == null) return null;

        String renderedOriginal = DescriptorRenderer.FQ_NAMES_IN_TYPES.render(originalDescriptor);
        Collection<? extends DeclarationDescriptor> descriptors;
        if (originalDescriptor instanceof ConstructorDescriptor && containingDeclaration instanceof ClassDescriptor) {
            descriptors = ((ClassDescriptor) containingDeclaration).getConstructors();
        }
        else {
            descriptors = memberScope.getContributedDescriptors(DescriptorKindFilter.ALL, MemberScope.Companion.getALL_NAME_FILTER());
        }
        for (DeclarationDescriptor member : descriptors) {
            if (renderedOriginal.equals(DescriptorRenderer.FQ_NAMES_IN_TYPES.render(member))) {
                return member;
            }
        }
        return null;
    }

    @Nullable
    public DeclarationDescriptor findCurrentDescriptor(@NotNull DeclarationDescriptor originalDescriptor) {
        ensureInitialized();
        //if there's no sources
        if (moduleDescriptor == null) {
            return null;
        }
        
        if (!isFromBuiltinModule(originalDescriptor)) {
            return null;
        }
        
        if (originalDescriptor instanceof ClassDescriptor) {
            return FindClassInModuleKt.findClassAcrossModuleDependencies(
                    moduleDescriptor, 
                    DescriptorUtilsKt.getClassId((ClassDescriptor) originalDescriptor));
        }
        
        if (originalDescriptor instanceof PackageFragmentDescriptor) {
            return StandardNames.BUILT_INS_PACKAGE_FQ_NAME.equals(((PackageFragmentDescriptor) originalDescriptor).getFqName())
                   ? builtinsPackageFragment
                   : null;
        }
        
        if (originalDescriptor instanceof MemberDescriptor) {
            return findCurrentDescriptorForMember((MemberDescriptor) originalDescriptor);
        }
        
        return null;
    }

    public static boolean isFromBuiltinModule(@NotNull DeclarationDescriptor originalDescriptor) {
        ModuleDescriptor module = DescriptorUtilsKt.getModule(originalDescriptor);
        KotlinBuiltIns builtIns = module.getBuiltIns();
        return module.equals(builtIns.getBuiltInsModule());
    }

    @Nullable
    private static MemberScope getMemberScope(@Nullable DeclarationDescriptor parent) {
        if (parent instanceof ClassDescriptor) {
            return ((ClassDescriptor) parent).getDefaultType().getMemberScope();
        }
        else if (parent instanceof PackageFragmentDescriptor) {
            return ((PackageFragmentDescriptor) parent).getMemberScope();
        }
        else {
            return null;
        }
    }
}