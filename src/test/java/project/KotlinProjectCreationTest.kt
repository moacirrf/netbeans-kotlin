package project

import java.io.File
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.jetbrains.kotlin.project.templates.KotlinGradleProjectWizardIterator
import org.jetbrains.kotlin.project.templates.KotlinMavenProjectWizardIterator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.xml.sax.InputSource

class KotlinProjectCreationTest {

    @Test
    fun testMavenPomXmlGeneration() {
        val pom = KotlinMavenProjectWizardIterator.generatePomXml(
            groupId = "com.test",
            artifactId = "MyTestApp",
            version = "1.0.0",
            pkg = "com.test.mytestapp"
        )

        assertTrue(pom.contains("<groupId>com.test</groupId>"))
        assertTrue(pom.contains("<artifactId>MyTestApp</artifactId>"))
        assertTrue(pom.contains("<version>1.0.0</version>"))
        assertTrue(pom.contains("<exec.mainClass>com.test.mytestapp.MainKt</exec.mainClass>"))
        assertTrue(pom.contains("<kotlin.version>2.4.21</kotlin.version>"))
        assertTrue(pom.contains("kotlin-maven-plugin"))
        assertTrue(pom.contains("exec-maven-plugin"))

        // Validate XML syntax
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(InputSource(StringReader(pom)))
        assertNotNull(doc)
        assertEquals("project", doc.documentElement.nodeName)
    }

    @Test
    fun testMavenSourcesGeneration() {
        val mainKt = KotlinMavenProjectWizardIterator.generateMainKt("com.test.mytestapp")
        assertTrue(mainKt.contains("package com.test.mytestapp"))
        assertTrue(mainKt.contains("fun main(args: Array<String>)"))

        val testKt = KotlinMavenProjectWizardIterator.generateMainTestKt("com.test.mytestapp")
        assertTrue(testKt.contains("package com.test.mytestapp"))
        assertTrue(testKt.contains("@Test"))
        assertTrue(testKt.contains("class MainTest"))
    }

    @Test
    fun testGradleBuildScriptsGeneration() {
        // Kotlin DSL
        val settingsKts = KotlinGradleProjectWizardIterator.generateSettingsGradleKts("MyGradleApp")
        assertTrue(settingsKts.contains("rootProject.name = \"MyGradleApp\""))

        val buildKts = KotlinGradleProjectWizardIterator.generateBuildGradleKts(
            groupId = "com.test.gradle",
            version = "2.0.0",
            pkg = "com.test.gradle.app",
            javaVersion = "21"
        )

        assertTrue(buildKts.contains("kotlin(\"jvm\") version \"2.4.21\""))
        assertTrue(buildKts.contains("application"))
        assertTrue(buildKts.contains("group = \"com.test.gradle\""))
        assertTrue(buildKts.contains("version = \"2.0.0\""))
        assertTrue(buildKts.contains("mainClass.set(\"com.test.gradle.app.MainKt\")"))
        assertTrue(buildKts.contains("jvmToolchain(21)"))
        assertTrue(buildKts.contains("implementation(kotlin(\"stdlib\"))"))
        assertTrue(buildKts.contains("useJUnitPlatform()"))

        // Groovy DSL
        val settingsGroovy = KotlinGradleProjectWizardIterator.generateSettingsGradle("MyGradleApp")
        assertTrue(settingsGroovy.contains("rootProject.name = 'MyGradleApp'"))

        val buildGroovy = KotlinGradleProjectWizardIterator.generateBuildGradle(
            groupId = "com.test.gradle",
            version = "2.0.0",
            pkg = "com.test.gradle.app",
            javaVersion = "17"
        )

        assertTrue(buildGroovy.contains("id 'org.jetbrains.kotlin.jvm' version '2.4.21'"))
        assertTrue(buildGroovy.contains("id 'application'"))
        assertTrue(buildGroovy.contains("group = 'com.test.gradle'"))
        assertTrue(buildGroovy.contains("version = '2.0.0'"))
        assertTrue(buildGroovy.contains("mainClass = 'com.test.gradle.app.MainKt'"))
        assertTrue(buildGroovy.contains("jvmToolchain(17)"))
        assertTrue(buildGroovy.contains("implementation 'org.jetbrains.kotlin:kotlin-stdlib'"))
        assertTrue(buildGroovy.contains("useJUnitPlatform()"))
    }

    @Test
    fun testGradleSourcesGeneration() {
        val mainKt = KotlinGradleProjectWizardIterator.generateMainKt("com.test.gradle.app")
        assertTrue(mainKt.contains("package com.test.gradle.app"))
        assertTrue(mainKt.contains("fun main(args: Array<String>)"))

        val testKt = KotlinGradleProjectWizardIterator.generateMainTestKt("com.test.gradle.app")
        assertTrue(testKt.contains("package com.test.gradle.app"))
        assertTrue(testKt.contains("@Test"))
        assertTrue(testKt.contains("class MainTest"))
    }

    @Test
    fun testMavenFileTreeCreation() {
        val tempDir = File.createTempFile("mvn_kt_test", "").also {
            it.delete()
            it.mkdirs()
        }

        try {
            val pkg = "com.example.sample"
            val pkgPath = pkg.replace('.', '/')
            val mainDir = File(tempDir, "src/main/kotlin/$pkgPath").also { it.mkdirs() }
            val testDir = File(tempDir, "src/test/kotlin/$pkgPath").also { it.mkdirs() }

            File(tempDir, "pom.xml").writeText(
                KotlinMavenProjectWizardIterator.generatePomXml("com.example", "SampleApp", "1.0", pkg)
            )
            File(mainDir, "Main.kt").writeText(KotlinMavenProjectWizardIterator.generateMainKt(pkg))
            File(testDir, "MainTest.kt").writeText(KotlinMavenProjectWizardIterator.generateMainTestKt(pkg))

            assertTrue(File(tempDir, "pom.xml").length() > 0)
            assertTrue(File(mainDir, "Main.kt").length() > 0)
            assertTrue(File(testDir, "MainTest.kt").length() > 0)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testGradleFileTreeCreation() {
        val tempDir = File.createTempFile("gradle_kt_test", "").also {
            it.delete()
            it.mkdirs()
        }

        try {
            val pkg = "com.example.sample"
            val pkgPath = pkg.replace('.', '/')
            val mainDir = File(tempDir, "src/main/kotlin/$pkgPath").also { it.mkdirs() }
            val testDir = File(tempDir, "src/test/kotlin/$pkgPath").also { it.mkdirs() }

            // Test Groovy DSL (build.gradle)
            File(tempDir, "settings.gradle").writeText(
                KotlinGradleProjectWizardIterator.generateSettingsGradle("SampleApp")
            )
            File(tempDir, "build.gradle").writeText(
                KotlinGradleProjectWizardIterator.generateBuildGradle("com.example", "1.0", pkg, "21")
            )
            File(mainDir, "Main.kt").writeText(KotlinGradleProjectWizardIterator.generateMainKt(pkg))
            File(testDir, "MainTest.kt").writeText(KotlinGradleProjectWizardIterator.generateMainTestKt(pkg))

            assertTrue(File(tempDir, "settings.gradle").length() > 0)
            assertTrue(File(tempDir, "build.gradle").length() > 0)
            assertTrue(File(mainDir, "Main.kt").length() > 0)
            assertTrue(File(testDir, "MainTest.kt").length() > 0)

            // Test Kotlin DSL (.kts)
            File(tempDir, "settings.gradle.kts").writeText(
                KotlinGradleProjectWizardIterator.generateSettingsGradleKts("SampleApp")
            )
            File(tempDir, "build.gradle.kts").writeText(
                KotlinGradleProjectWizardIterator.generateBuildGradleKts("com.example", "1.0", pkg, "21")
            )
            assertTrue(File(tempDir, "settings.gradle.kts").length() > 0)
            assertTrue(File(tempDir, "build.gradle.kts").length() > 0)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
