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
package org.jetbrains.kotlin.reformatting

import javax.swing.SwingUtilities
import javax.swing.text.Document
import javax.swing.text.StyledDocument
import org.jetbrains.kotlin.formatting.KotlinIndentStrategy
import org.jetbrains.kotlin.log.KotlinLogger
import org.jetbrains.kotlin.navigation.netbeans.moveCaretToOffset
import org.netbeans.api.project.Project

fun format(doc: Document, offset: Int, proj: Project? = null) {
    try {
        val originalText = doc.getText(0, doc.length)
        val formattedCode = reindentCode(doc, originalText)
        if (formattedCode != originalText) {
            applyTextDiff(doc, formattedCode)
        }
        val safeOffset = Math.min(Math.max(0, offset), doc.length)
        doc.moveCursorTo(safeOffset)
    } catch (t: Throwable) {
        KotlinLogger.INSTANCE.logWarning("Kotlin reformat error: ${t.javaClass.name}: ${t.message}")
    }
}

fun applyTextDiff(doc: Document, newText: String) {
    val oldText = doc.getText(0, doc.length)
    if (oldText == newText) return

    var prefixLen = 0
    val minLen = Math.min(oldText.length, newText.length)
    while (prefixLen < minLen && oldText[prefixLen] == newText[prefixLen]) {
        prefixLen++
    }

    var oldSuffix = oldText.length
    var newSuffix = newText.length
    while (oldSuffix > prefixLen && newSuffix > prefixLen && oldText[oldSuffix - 1] == newText[newSuffix - 1]) {
        oldSuffix--
        newSuffix--
    }

    val removeLen = oldSuffix - prefixLen
    val insertStr = newText.substring(prefixLen, newSuffix)

    if (removeLen > 0) {
        doc.remove(prefixLen, removeLen)
    }
    if (insertStr.isNotEmpty()) {
        doc.insertString(prefixLen, insertStr, null)
    }
}

fun reindentCode(doc: Document, text: String): String {
    val lines = text.split("\n")
    val sb = StringBuilder()
    var currentOffset = 0
    val strategy = KotlinIndentStrategy(doc as? StyledDocument, 0)
    val unitSize = strategy.indentUnitSize

    for (i in lines.indices) {
        val line = lines[i]
        val trimmed = line.trimStart()
        if (trimmed.isEmpty()) {
            sb.append(line)
        } else {
            val nonWsOffset = currentOffset + (line.length - trimmed.length)
            val spaces = if (trimmed.startsWith("}")) {
                KotlinIndentStrategy.computeIndentSpacesForCloseBrace(text, nonWsOffset)
            } else {
                KotlinIndentStrategy.computeIndentSpaces(text, nonWsOffset, unitSize)
            }
            sb.append(strategy.createIndentString(spaces)).append(trimmed)
        }
        if (i < lines.size - 1) {
            sb.append("\n")
        }
        currentOffset += line.length + 1
    }
    return sb.toString()
}

fun Document.moveCursorTo(position: Int) = SwingUtilities.invokeLater {
    if (this is StyledDocument) {
        moveCaretToOffset(this, position)
    }
}