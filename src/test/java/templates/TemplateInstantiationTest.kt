package templates

import freemarker.template.Configuration
import freemarker.template.Template
import junit.framework.TestCase
import java.io.InputStreamReader
import java.io.StringWriter

class TemplateInstantiationTest : TestCase("Template Instantiation Test") {

    private val templates = listOf(
        "class.kt" to "class MyTestClass",
        "content.kt" to "Author:",
        "enum.kt" to "enum class MyTestEnum",
        "interface.kt" to "interface MyTestInterface",
        "object.kt" to "object MyTestObject"
    )

    private fun loadTemplate(templateName: String): Template {
        val stream = javaClass.getResourceAsStream("/org/jetbrains/kotlin/file/templates/$templateName")
            ?: error("Template $templateName not found in resources")
        val reader = InputStreamReader(stream, Charsets.UTF_8)
        val cfg = Configuration(Configuration.VERSION_2_3_32)
        return Template(templateName, reader, cfg)
    }

    private fun processTemplate(template: Template, model: Map<String, Any?>): String {
        val writer = StringWriter()
        template.process(model, writer)
        return writer.toString()
    }

    fun testAllTemplatesStandardParameters() {
        for ((templateName, expectedSnippet) in templates) {
            val template = loadTemplate(templateName)
            val model = mapOf(
                "name" to "MyTest" + templateName.removeSuffix(".kt").replaceFirstChar { it.uppercase() },
                "package" to "org.example.demo",
                "user" to "developer",
                "date" to "Oct 31, 2026"
            )
            val result = processTemplate(template, model)
            assertFalse("Template $templateName should not produce empty output", result.isBlank())
            assertTrue("Template $templateName should contain package declaration", result.contains("package org.example.demo"))
            assertTrue("Template $templateName should contain author", result.contains("Author: developer"))
            assertTrue("Template $templateName should contain '$expectedSnippet'", result.contains(expectedSnippet))
        }
    }

    fun testDefaultPackageOmitsPackageStatement() {
        for ((templateName, _) in templates) {
            val template = loadTemplate(templateName)
            val model = mapOf(
                "name" to "TopLevel",
                "package" to "",
                "user" to "developer",
                "date" to "Oct 31, 2026"
            )
            val result = processTemplate(template, model)
            assertFalse("Template $templateName should not be empty", result.isBlank())
            assertFalse("Template $templateName should not contain 'package ' when package is empty", result.contains("package "))
        }
    }

    fun testEmptyModelDoesNotThrowAndUsesFallbacks() {
        for ((templateName, _) in templates) {
            val template = loadTemplate(templateName)
            val model = emptyMap<String, Any?>()
            val result = processTemplate(template, model)
            assertFalse("Template $templateName should not be empty even with empty model", result.isBlank())
            assertTrue("Template $templateName should fallback to default author", result.contains("Author: User"))
        }
    }

    fun testMissingOrEmptyLicenseDoesNotThrow() {
        for ((templateName, _) in templates) {
            val template = loadTemplate(templateName)
            val model = mapOf(
                "project" to mapOf("licensePath" to ""),
                "name" to "NoLicense",
                "package" to "com.test"
            )
            val result = processTemplate(template, model)
            assertFalse("Template $templateName should not be empty", result.isBlank())
            assertTrue("Template $templateName should contain package", result.contains("package com.test"))
        }
    }

    fun testReformatDoesNotCollapseTrackedEndPosition() {
        val template = loadTemplate("class.kt")
        val model = mapOf(
            "name" to "Newclass",
            "package" to "teste.kotlinteste",
            "user" to "moacirrf",
            "date" to "Oct 31, 2026"
        )
        val generated = processTemplate(template, model)
        assertFalse(generated.isBlank())

        val doc = javax.swing.text.PlainDocument()
        doc.insertString(0, generated, null)
        val endPos = doc.createPosition(doc.length)
        assertTrue("Initial end position must be > 0", endPos.offset > 0)

        org.jetbrains.kotlin.reformatting.format(doc, 0)

        assertTrue("End position must not collapse to 0 after formatting", endPos.offset > 0)
        assertEquals("End position must match doc.length", doc.length, endPos.offset)

        val textAfterFormat = doc.getText(0, endPos.offset)
        assertFalse("Text must not be empty", textAfterFormat.isBlank())
        assertTrue(textAfterFormat.contains("class Newclass"))
        assertTrue(textAfterFormat.contains("package teste.kotlinteste"))
    }
}
