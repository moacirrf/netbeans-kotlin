package org.jetbrains.kotlin.project.templates

import junit.framework.TestCase

class KotlinAntProjectWizardIteratorTest : TestCase("Kotlin Ant Project Wizard Iterator Test") {

    fun testGenerateProjectXml() {
        val xml = KotlinAntProjectWizardIterator.generateProjectXml("TestAntApp")
        assertTrue(xml.contains("<name>TestAntApp</name>"))
        assertTrue(xml.contains("<type>org.netbeans.modules.java.j2seproject</type>"))
    }

    fun testGenerateProjectProperties() {
        val props = KotlinAntProjectWizardIterator.generateProjectProperties("TestAntApp", "com.example.MainApp")
        assertTrue(props.contains("application.title=TestAntApp"))
        assertTrue(props.contains("main.class=com.example.MainApp"))
        assertTrue(props.contains("run.classpath=\${javac.classpath}:\${build.classes.dir}:\${kotlinc.classpath}"))
    }

    fun testGenerateBuildXml() {
        val buildXml = KotlinAntProjectWizardIterator.generateBuildXml("TestAntApp", "com.example.MainApp")
        assertTrue(buildXml.contains("<project name=\"TestAntApp\" default=\"default\" basedir=\".\">"))
        assertTrue(buildXml.contains("<target name=\"-pre-compile\">"))
        assertTrue(buildXml.contains("Compiling Kotlin sources"))
    }

    fun testGenerateManifest() {
        val manifest = KotlinAntProjectWizardIterator.generateManifest()
        assertTrue(manifest.contains("Manifest-Version: 1.0"))
    }

    fun testGenerateMainKtWithPackage() {
        val mainKt = KotlinAntProjectWizardIterator.generateMainKt("com.example")
        assertTrue(mainKt.contains("package com.example"))
        assertTrue(mainKt.contains("fun main(args: Array<String>)"))
        assertTrue(mainKt.contains("println(\"Hello from Kotlin with Ant!\")"))
    }

    fun testGenerateMainKtWithoutPackage() {
        val mainKt = KotlinAntProjectWizardIterator.generateMainKt("")
        assertFalse(mainKt.contains("package "))
        assertTrue(mainKt.contains("fun main(args: Array<String>)"))
    }

    fun testGenerateMainAppJavaWithPackage() {
        val mainApp = KotlinAntProjectWizardIterator.generateMainAppJava("com.example")
        assertTrue(mainApp.contains("package com.example;"))
        assertTrue(mainApp.contains("public class MainApp {"))
        assertTrue(mainApp.contains("MainKt.main(args);"))
    }

    fun testGenerateMainAppJavaWithoutPackage() {
        val mainApp = KotlinAntProjectWizardIterator.generateMainAppJava("")
        assertFalse(mainApp.contains("package "))
        assertTrue(mainApp.contains("public class MainApp {"))
        assertTrue(mainApp.contains("MainKt.main(args);"))
    }
}
