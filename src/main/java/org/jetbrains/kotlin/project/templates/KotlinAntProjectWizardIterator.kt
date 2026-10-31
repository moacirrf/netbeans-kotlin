package org.jetbrains.kotlin.project.templates

import java.io.File
import java.util.Collections
import javax.swing.JComponent
import javax.swing.event.ChangeListener
import org.jetbrains.kotlin.project.templates.ui.KotlinProjectPanelVisual
import org.jetbrains.kotlin.project.templates.ui.KotlinProjectWizardPanel
import org.netbeans.api.progress.ProgressHandle
import org.netbeans.api.project.ProjectManager
import org.netbeans.api.project.ui.OpenProjects
import org.netbeans.spi.project.ui.support.ProjectChooser
import org.openide.WizardDescriptor
import org.openide.cookies.OpenCookie
import org.openide.filesystems.FileObject
import org.openide.filesystems.FileUtil
import org.openide.loaders.DataObject

class KotlinAntProjectWizardIterator : WizardDescriptor.ProgressInstantiatingIterator<WizardDescriptor> {

    private var wizard: WizardDescriptor? = null
    private var panel: KotlinProjectWizardPanel? = null

    override fun initialize(wiz: WizardDescriptor) {
        this.wizard = wiz
        wiz.putProperty("NewProjectWizard_Title", "New Kotlin Ant Application")

        val p = KotlinProjectWizardPanel("KotlinAntApp", false)
        this.panel = p

        val c = p.component as? JComponent
        c?.putClientProperty(WizardDescriptor.PROP_CONTENT_SELECTED_INDEX, 0)
        c?.putClientProperty(WizardDescriptor.PROP_CONTENT_DATA, arrayOf("Name and Location"))
    }

    override fun uninitialize(wiz: WizardDescriptor) {
        this.wizard = null
        this.panel = null
    }

    override fun instantiate(): Set<FileObject> {
        throw UnsupportedOperationException("Use instantiate(ProgressHandle) instead")
    }

    override fun instantiate(handle: ProgressHandle): Set<FileObject> {
        val wiz = wizard ?: return emptySet()
        handle.start(4)

        val projDir = wiz.getProperty(KotlinProjectPanelVisual.PROP_PROJECT_DIR) as File
        val name = wiz.getProperty(KotlinProjectPanelVisual.PROP_PROJECT_NAME) as String
        val pkg = wiz.getProperty(KotlinProjectPanelVisual.PROP_PACKAGE) as String
        val packagePath = pkg.replace('.', '/')
        val mainClass = if (pkg.isEmpty()) "MainApp" else "$pkg.MainApp"

        // 1. Create directories
        handle.progress("Creating directories...", 1)
        val srcDir = File(projDir, if (packagePath.isNotEmpty()) "src/$packagePath" else "src")
        val testDir = File(projDir, if (packagePath.isNotEmpty()) "test/$packagePath" else "test")
        val nbprojectDir = File(projDir, "nbproject")
        srcDir.mkdirs()
        testDir.mkdirs()
        nbprojectDir.mkdirs()

        // 2. Generate Ant project configuration
        handle.progress("Generating Ant build files...", 2)
        File(nbprojectDir, "project.xml").writeText(generateProjectXml(name))
        File(nbprojectDir, "project.properties").writeText(generateProjectProperties(name, mainClass))
        File(projDir, "build.xml").writeText(generateBuildXml(name, mainClass))
        File(projDir, "manifest.mf").writeText(generateManifest())

        // 3. Generate source files (Main.kt and Java launcher MainApp.java)
        handle.progress("Generating source files...", 3)
        val mainFile = File(srcDir, "Main.kt")
        mainFile.writeText(generateMainKt(pkg))

        val mainAppFile = File(srcDir, "MainApp.java")
        mainAppFile.writeText(generateMainAppJava(pkg))

        // 4. Open project and file
        handle.progress("Opening project in NetBeans...", 4)
        ProjectChooser.setProjectsFolder(projDir.parentFile)
        val dirFO = FileUtil.toFileObject(FileUtil.normalizeFile(projDir))

        if (dirFO != null) {
            dirFO.refresh()
            try {
                val project = ProjectManager.getDefault().findProject(dirFO)
                if (project != null) {
                    OpenProjects.getDefault().open(arrayOf(project), false)
                }
            } catch (ignored: Exception) {
            }

            val mainFO = FileUtil.toFileObject(FileUtil.normalizeFile(mainFile))
            if (mainFO != null) {
                try {
                    val dobj = DataObject.find(mainFO)
                    val openCookie = dobj.getLookup().lookup(OpenCookie::class.java)
                    openCookie?.open()
                } catch (ignored: Exception) {
                }
                return Collections.singleton(mainFO)
            }
        }

        return emptySet()
    }

    override fun current(): WizardDescriptor.Panel<WizardDescriptor> =
        panel ?: throw IllegalStateException("Panel not initialized")

    override fun name(): String = "1 of 1"

    override fun hasNext(): Boolean = false

    override fun hasPrevious(): Boolean = false

    override fun nextPanel() {}

    override fun previousPanel() {}

    override fun addChangeListener(l: ChangeListener) {}

    override fun removeChangeListener(l: ChangeListener) {}

    companion object {
        fun generateProjectXml(name: String): String {
            return """
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://www.netbeans.org/ns/project/1">
    <type>org.netbeans.modules.java.j2seproject</type>
    <configuration>
        <data xmlns="http://www.netbeans.org/ns/j2se-project/3">
            <name>$name</name>
            <source-roots>
                <root id="src.dir"/>
            </source-roots>
            <test-roots>
                <root id="test.src.dir"/>
            </test-roots>
        </data>
    </configuration>
</project>
            """.trimIndent() + "\n"
        }

        fun generateProjectProperties(name: String, mainClass: String): String {
            return """
annotation.processing.enabled=true
annotation.processing.enabled.in.editor=false
annotation.processing.run.all.processors=true
application.title=$name
build.classes.dir=${'$'}{build.dir}/classes
build.classes.excludes=**/*.java,**/*.form
build.dir=build
build.generated.dir=${'$'}{build.dir}/generated
build.generated.sources.dir=${'$'}{build.dir}/generated-sources
build.sysclasspath=ignore
build.test.classes.dir=${'$'}{build.dir}/test/classes
build.test.results.dir=${'$'}{build.dir}/test/results
debug.classpath=${'$'}{run.classpath}
debug.test.classpath=${'$'}{run.test.classpath}
dist.archive.excludes=
dist.dir=dist
dist.jar=${'$'}{dist.dir}/$name.jar
dist.javadoc.dir=${'$'}{dist.dir}/javadoc
includes=**
jar.compress=false
javac.classpath=
javac.compilerargs=
javac.deprecation=false
javac.external.vm=true
javac.processorpath=${'$'}{javac.classpath}
javac.source=21
javac.target=21
javac.test.classpath=${'$'}{javac.classpath}:${'$'}{build.classes.dir}
javac.test.processorpath=${'$'}{javac.test.classpath}
main.class=$mainClass
manifest.file=manifest.mf
meta.inf.dir=${'$'}{src.dir}/META-INF
platform.active=default_platform
run.classpath=${'$'}{javac.classpath}:${'$'}{build.classes.dir}:${'$'}{kotlinc.classpath}
run.test.classpath=${'$'}{javac.test.classpath}:${'$'}{build.test.classes.dir}
source.encoding=UTF-8
src.dir=src
test.src.dir=test
            """.trimIndent() + "\n"
        }

        fun generateBuildXml(name: String, mainClass: String): String {
            return """
<?xml version="1.0" encoding="UTF-8"?>
<project name="$name" default="default" basedir=".">
    <description>Builds, tests, and runs the project $name.</description>
    <import file="nbproject/build-impl.xml"/>

    <!-- Hook to compile Kotlin sources before Java compilation -->
    <target name="-pre-compile">
        <mkdir dir="${'$'}{build.classes.dir}"/>
        <property environment="env"/>
        <condition property="kotlinc.cmd" value="${'$'}{env.KOTLIN_HOME}/bin/kotlinc" else="kotlinc">
            <isset property="env.KOTLIN_HOME"/>
        </condition>
        <echo message="Compiling Kotlin sources in ${'$'}{src.dir}..."/>
        <exec executable="${'$'}{kotlinc.cmd}" failonerror="false" resultproperty="kotlinc.status">
            <arg value="${'$'}{src.dir}"/>
            <arg value="-d"/>
            <arg value="${'$'}{build.classes.dir}"/>
            <arg value="-jvm-target"/>
            <arg value="21"/>
        </exec>
    </target>

</project>
            """.trimIndent() + "\n"
        }

        fun generateManifest(): String {
            return "Manifest-Version: 1.0\nX-COMMENT: Main-Class will be added automatically by build\n"
        }

        fun generateMainKt(pkg: String): String {
            val pkgHeader = if (pkg.isNotEmpty()) "package $pkg\n\n" else ""
            return """
${pkgHeader}fun main(args: Array<String>) {
    println("Hello from Kotlin with Ant!")
}
            """.trimIndent() + "\n"
        }

        fun generateMainAppJava(pkg: String): String {
            val pkgHeader = if (pkg.isNotEmpty()) "package $pkg;\n\n" else ""
            return """
${pkgHeader}/**
 * Main application launcher for NetBeans Java SE Ant project integration.
 * Delegates execution to Kotlin's MainKt.main(args).
 */
public class MainApp {

    public static void main(String[] args) {
        MainKt.main(args);
    }
}
            """.trimIndent() + "\n"
        }
    }
}
