package org.jetbrains.kotlin.project.templates

import java.io.File
import java.io.FileWriter
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

class KotlinMavenProjectWizardIterator : WizardDescriptor.ProgressInstantiatingIterator<WizardDescriptor> {

    private var wizard: WizardDescriptor? = null
    private var panel: KotlinProjectWizardPanel? = null

    override fun initialize(wiz: WizardDescriptor) {
        this.wizard = wiz
        wiz.putProperty("NewProjectWizard_Title", "New Kotlin Maven Application")

        val p = KotlinProjectWizardPanel("KotlinMavenApp", false)
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
        val groupId = wiz.getProperty(KotlinProjectPanelVisual.PROP_GROUP_ID) as String
        val version = wiz.getProperty(KotlinProjectPanelVisual.PROP_VERSION) as String
        val pkg = wiz.getProperty(KotlinProjectPanelVisual.PROP_PACKAGE) as String
        val packagePath = pkg.replace('.', '/')

        // 1. Create directories
        handle.progress("Creating directories...", 1)
        val mainKotlinDir = File(projDir, "src/main/kotlin/$packagePath")
        val mainResDir = File(projDir, "src/main/resources")
        val testKotlinDir = File(projDir, "src/test/kotlin/$packagePath")
        val testResDir = File(projDir, "src/test/resources")
        mainKotlinDir.mkdirs()
        mainResDir.mkdirs()
        testKotlinDir.mkdirs()
        testResDir.mkdirs()

        // 2. Generate pom.xml
        handle.progress("Generating pom.xml...", 2)
        val pomFile = File(projDir, "pom.xml")
        FileWriter(pomFile).use { writer ->
            writer.write(generatePomXml(groupId, name, version, pkg))
        }

        // 3. Generate Main.kt and MainTest.kt
        handle.progress("Generating source files...", 3)
        val mainFile = File(mainKotlinDir, "Main.kt")
        FileWriter(mainFile).use { writer ->
            writer.write(generateMainKt(pkg))
        }

        val testFile = File(testKotlinDir, "MainTest.kt")
        FileWriter(testFile).use { writer ->
            writer.write(generateMainTestKt(pkg))
        }

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
                    dobj.lookup.lookup(OpenCookie::class.java)?.open()
                } catch (ignored: Exception) {
                }
            }
            handle.finish()
            return Collections.singleton(dirFO)
        }

        handle.finish()
        return emptySet()
    }

    override fun current(): WizardDescriptor.Panel<WizardDescriptor> {
        return panel!!
    }

    override fun name(): String = "1 of 1"

    override fun hasNext(): Boolean = false

    override fun hasPrevious(): Boolean = false

    override fun nextPanel() {}

    override fun previousPanel() {}

    override fun addChangeListener(l: ChangeListener) {}

    override fun removeChangeListener(l: ChangeListener) {}

    companion object {
        fun generatePomXml(groupId: String, artifactId: String, version: String, pkg: String): String {
            return """
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>$groupId</groupId>
    <artifactId>$artifactId</artifactId>
    <version>$version</version>
    <packaging>jar</packaging>

    <name>$artifactId</name>

    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <kotlin.version>2.4.21</kotlin.version>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <exec.mainClass>$pkg.MainKt</exec.mainClass>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.jetbrains.kotlin</groupId>
            <artifactId>kotlin-stdlib</artifactId>
            <version>${'$'}{kotlin.version}</version>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.10.2</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <sourceDirectory>src/main/kotlin</sourceDirectory>
        <testSourceDirectory>src/test/kotlin</testSourceDirectory>
        <plugins>
            <plugin>
                <groupId>org.jetbrains.kotlin</groupId>
                <artifactId>kotlin-maven-plugin</artifactId>
                <version>${'$'}{kotlin.version}</version>
                <executions>
                    <execution>
                        <id>compile</id>
                        <phase>compile</phase>
                        <goals>
                            <goal>compile</goal>
                        </goals>
                    </execution>
                    <execution>
                        <id>test-compile</id>
                        <phase>test-compile</phase>
                        <goals>
                            <goal>test-compile</goal>
                        </goals>
                    </execution>
                </executions>
                <configuration>
                    <jvmTarget>21</jvmTarget>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.2.0</version>
                <configuration>
                    <mainClass>$pkg.MainKt</mainClass>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.5</version>
            </plugin>
        </plugins>
    </build>
</project>
            """.trimIndent() + "\n"
        }

        fun generateMainKt(pkg: String): String {
            return """
package $pkg

fun main(args: Array<String>) {
    println("Hello World!")
}
            """.trimIndent() + "\n"
        }

        fun generateMainTestKt(pkg: String): String {
            return """
package $pkg

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class MainTest {

    @Test
    fun testApp() {
        assertTrue(true)
    }
}
            """.trimIndent() + "\n"
        }
    }
}
