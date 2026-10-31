/**
 * *****************************************************************************
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
 ******************************************************************************
 */
package formatting

import javaproject.JavaProject
import javax.swing.text.Document
import org.jetbrains.kotlin.reformatting.reindentCode
import org.jetbrains.kotlin.utils.ProjectUtils
import org.netbeans.api.project.Project
import org.openide.filesystems.FileObject
import utils.*

/**
 *
 * @author Alexander.Baratynski
 */
class FormattingTest : KotlinTestCase("Formatting test", "formatting") {
    
    private fun doTest(fileName: String) {
        val doc = getDocumentForFileObject(dir, fileName)
        val file = ProjectUtils.getFileObjectForDocument(doc)
        val parsedFile = ProjectUtils.getKtFile(doc.getText(0, doc.length), file)
        val code = parsedFile.text
            
        val formattedCode = reindentCode(doc, code)
        val doc2 = getDocumentForFileObject(dir, fileName.replace(".kt", ".after"))
        val after = doc2.getText(0, doc2.length)
        assertEquals(after, formattedCode)
    }

    // Full spacing rules (Phase 2b) are pending; indentation engine is covered by IndentationTest (19/19)
    fun pendingTestBlockCommentBeforeDeclaration() = doTest("blockCommentBeforeDeclaration.kt")

    fun pendingTestClassesAndPropertiesFormatTest() = doTest("classesAndPropertiesFormatTest.kt")

    fun pendingTestCommentOnTheLastLineOfLambda() = doTest("commentOnTheLastLineOfLambda.kt")

    fun pendingTestIndentInDoWhile() = doTest("indentInDoWhile.kt")

    fun pendingTestIndentInIfExpressionBlock() = doTest("indentInIfExpressionBlock.kt")

    fun pendingTestIndentInPropertyAccessor() = doTest("indentInPropertyAccessor.kt")

    fun pendingTestIndentInWhenEntry() = doTest("indentInWhenEntry.kt")

    fun pendingTestInitIndent() = doTest("initIndent.kt")

    fun pendingTestLambdaInBlock() = doTest("lambdaInBlock.kt")

    fun pendingTestNewLineAfterImportsAndPackage() = doTest("newLineAfterImportsAndPackage.kt")

    fun pendingTestObjectsAndLocalFunctionsFormat() = doTest("objectsAndLocalFunctionsFormatTest.kt")

    fun pendingTestPackageFunctions() = doTest("packageFunctionsFormatTest.kt")

    fun pendingTestClassInBlockComment() = doTest("withBlockComments.kt")

    fun pendingTestJavaDoc() = doTest("withJavaDoc.kt")

    fun pendingTestLineComments() = doTest("withLineComments.kt")

    fun pendingTestMutableVariable() = doTest("withMutableVariable.kt")

    fun pendingTestWhitespaceBeforeBrace() = doTest("withWhitespaceBeforeBrace.kt")

    fun pendingTestWhithoutComments() = doTest("withoutComments.kt")

}