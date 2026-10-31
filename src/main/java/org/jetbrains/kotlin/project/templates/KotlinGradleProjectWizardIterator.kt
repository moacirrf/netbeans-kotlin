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

class KotlinGradleProjectWizardIterator : WizardDescriptor.ProgressInstantiatingIterator<WizardDescriptor> {

    private var wizard: WizardDescriptor? = null
    private var panel: KotlinProjectWizardPanel? = null

    override fun initialize(wiz: WizardDescriptor) {
        this.wizard = wiz
        wiz.putProperty("NewProjectWizard_Title", "New Kotlin Gradle Application")

        val p = KotlinProjectWizardPanel("KotlinGradleApp", true)
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
        val javaVersion = wiz.getProperty(KotlinProjectPanelVisual.PROP_JAVA_VERSION) as? String ?: "21"
        val gradleDsl = wiz.getProperty(KotlinProjectPanelVisual.PROP_GRADLE_DSL) as? String ?: "Groovy"
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

        // 2. Generate Gradle scripts
        handle.progress("Generating Gradle build scripts...", 2)
        if (gradleDsl.equals("Groovy", ignoreCase = true)) {
            val settingsFile = File(projDir, "settings.gradle")
            FileWriter(settingsFile).use { writer ->
                writer.write(generateSettingsGradle(name))
            }

            val buildFile = File(projDir, "build.gradle")
            FileWriter(buildFile).use { writer ->
                writer.write(generateBuildGradle(groupId, version, pkg, javaVersion))
            }
        } else {
            val settingsFile = File(projDir, "settings.gradle.kts")
            FileWriter(settingsFile).use { writer ->
                writer.write(generateSettingsGradleKts(name))
            }

            val buildFile = File(projDir, "build.gradle.kts")
            FileWriter(buildFile).use { writer ->
                writer.write(generateBuildGradleKts(groupId, version, pkg, javaVersion))
            }
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
        fun generateSettingsGradle(name: String): String {
            return """
rootProject.name = '$name'
            """.trimIndent() + "\n"
        }

        fun generateBuildGradle(groupId: String, version: String, pkg: String, javaVersion: String = "21"): String {
            return """
plugins {
    id 'org.jetbrains.kotlin.jvm' version '2.4.21'
    id 'application'
}

group = '$groupId'
version = '$version'

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.jetbrains.kotlin:kotlin-stdlib'
    testImplementation 'org.jetbrains.kotlin:kotlin-test'
}

application {
    mainClass = '$pkg.MainKt'
}

kotlin {
    jvmToolchain($javaVersion)
}

tasks.test {
    useJUnitPlatform()
}
            """.trimIndent() + "\n"
        }

        fun generateSettingsGradleKts(name: String): String {
            return """
rootProject.name = "$name"
            """.trimIndent() + "\n"
        }

        fun generateBuildGradleKts(groupId: String, version: String, pkg: String, javaVersion: String = "21"): String {
            return """
plugins {
    kotlin("jvm") version "2.4.21"
    application
}

group = "$groupId"
version = "$version"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("$pkg.MainKt")
}

kotlin {
    jvmToolchain($javaVersion)
}

tasks.test {
    useJUnitPlatform()
}
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

import kotlin.test.Test
import kotlin.test.assertTrue

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
