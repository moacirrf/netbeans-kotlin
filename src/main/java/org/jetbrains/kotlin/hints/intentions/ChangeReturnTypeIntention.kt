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
package org.jetbrains.kotlin.hints.intentions

import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analyzer.AnalysisResult
import javax.swing.text.Document
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.idea.util.approximateWithResolvableType
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDeclarationWithInitializer
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.bindingContextUtil.getTargetFunction
import org.jetbrains.kotlin.resolve.diagnostics.Diagnostics
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.makeNullable
import org.jetbrains.kotlin.descriptors.ClassDescriptorWithResolutionScopes
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.hints.atomicChange

class ChangeReturnTypeIntention(doc: Document,
                                analysisResult: AnalysisResult?,
                                psi: PsiElement) : ApplicableIntention(doc, analysisResult, psi) {

    private lateinit var function: KtFunction
    private lateinit var type: KotlinType

    private val activeDiagnostics = listOf(
            Errors.CONSTANT_EXPECTED_TYPE_MISMATCH,
            Errors.TYPE_MISMATCH,
            Errors.NULL_FOR_NONNULL_TYPE,
            Errors.TYPE_INFERENCE_EXPECTED_TYPE_MISMATCH)

    override fun isApplicable(caretOffset: Int): Boolean {
        val bindingContext = analysisResult?.bindingContext ?: return false
        val activeDiagnostic = getActiveDiagnostic(psi.textOffset, bindingContext.diagnostics) ?: return false
        val expression = activeDiagnostic.psiElement.getNonStrictParentOfType(KtExpression::class.java) ?: return false
        
        val expressionType = when (activeDiagnostic.factory) {
            Errors.TYPE_MISMATCH -> {
                val diagnosticWithParameters = Errors.TYPE_MISMATCH.cast(activeDiagnostic)
                diagnosticWithParameters.b
            }
            
            Errors.NULL_FOR_NONNULL_TYPE -> {
                val diagnosticWithParameters = Errors.NULL_FOR_NONNULL_TYPE.cast(activeDiagnostic)
                val expectedType = diagnosticWithParameters.a
                expectedType.makeNullable()
            }
            
            Errors.CONSTANT_EXPECTED_TYPE_MISMATCH -> bindingContext.getType(expression)
            
            Errors.TYPE_INFERENCE_EXPECTED_TYPE_MISMATCH -> {
                val diagnosticWithParameters = Errors.TYPE_INFERENCE_EXPECTED_TYPE_MISMATCH.cast(activeDiagnostic)
                diagnosticWithParameters.b
            }
            
            else -> null
        } ?: return false
        
        val expressionParent = expression.parent
        val ktFunction = (if (expressionParent is KtReturnExpression) {
            expressionParent.getTargetFunction(bindingContext)
        } else {
            PsiTreeUtil.getParentOfType(expression, KtFunction::class.java, true)
        }) as? KtFunction ?: return false

        return when {
            canFunctionOrGetterReturnExpression(ktFunction, expression) -> {
                val scope = ktFunction.getResolutionScope(bindingContext)
                type = expressionType.approximateWithResolvableType(scope, false)
                function = ktFunction
                true
            }
            
            expression is KtCallExpression -> {
                type = expressionType
                function = ktFunction
                true
            }
            
            else -> false
        }
    }

    private fun getActiveDiagnostic(offset: Int, diagnostics: Diagnostics): Diagnostic? {
        return diagnostics.find { diagnostic ->
            if (diagnostic.textRanges.isEmpty()) return@find false

            val range = diagnostic.textRanges.first()
            range.startOffset <= offset && offset <= range.endOffset && diagnostic.factory in activeDiagnostics
        }
    }

    override fun getDescription(): String {
        val functionName = function.name
        val renderedType = IdeDescriptorRenderers.SOURCE_CODE_SHORT_NAMES_IN_TYPES.renderType(type)
        return if (functionName != null) {
            "Change '$functionName' function return type to '$renderedType'"
        } else {
            "Change function return type to '$renderedType'"
        }
    }
    
    override fun implement() {
        val oldTypeRef = function.typeReference
        val renderedType = IdeDescriptorRenderers.SOURCE_CODE_SHORT_NAMES_IN_TYPES.renderType(type)
        
        if (oldTypeRef != null) {
            val startOffset = oldTypeRef.textRange.startOffset
            val endOffset = oldTypeRef.textRange.endOffset
            doc.atomicChange {
                remove(startOffset, endOffset - startOffset)
                insertString(startOffset, renderedType, null)
            }
        } else {
            val anchor = function.valueParameterList
            if (anchor != null) {
                doc.insertString(anchor.textRange.endOffset, ": $renderedType", null)
            }
        }
    }
    
}

// from idea/idea-core/src/org/jetbrains/kotlin/idea/core/Utils.kt but without the second parameter
fun PsiElement.getResolutionScope(bindingContext: BindingContext): LexicalScope {
    for (parent in parentsWithSelf) {
        if (parent is KtElement) {
            val scope = bindingContext[BindingContext.LEXICAL_SCOPE, parent]
            if (scope != null) return scope
        }

        if (parent is KtClassBody) {
            val classDescriptor = bindingContext[BindingContext.CLASS, parent.getParent()] as? ClassDescriptorWithResolutionScopes
            if (classDescriptor != null) {
                return classDescriptor.scopeForMemberDeclarationResolution
            }
        }
    }
    error("Not in JetFile")
}

private fun canFunctionOrGetterReturnExpression(
    functionOrGetter: KtDeclaration,
    expression: KtExpression
): Boolean {
    if (functionOrGetter is KtFunctionLiteral) {
        val body = functionOrGetter.bodyExpression
        val last = body?.lastChild
        return last is KtExpression && canEvaluateTo(last, expression)
    }
    if (functionOrGetter is KtDeclarationWithInitializer) {
        val init = functionOrGetter.initializer
        if (init != null && canEvaluateTo(init, expression)) {
            return true
        }
    }
    val returnExpr = PsiTreeUtil.getParentOfType(expression, KtReturnExpression::class.java)
    if (returnExpr != null) {
        val returned = returnExpr.returnedExpression
        if (returned != null && canEvaluateTo(returned, expression)) {
            return true
        }
    }
    return false
}

private fun canEvaluateTo(expression: KtExpression?, target: KtExpression?): Boolean {
    var cur = target ?: return false
    val root = expression ?: return false
    while (true) {
        if (root == cur) return true
        val parent = cur.parent
        if (parent is KtParenthesizedExpression) {
            cur = parent
            continue
        }
        if (parent is KtDotQualifiedExpression) {
            if (cur is KtCallExpression || cur is KtDotQualifiedExpression) {
                cur = parent
                continue
            }
        }
        val parentIf = getParentIfForBranch(cur)
        if (parentIf != null) {
            cur = parentIf
            continue
        }
        return false
    }
}

private fun getParentIfForBranch(expr: KtExpression): KtIfExpression? {
    val parentIf = PsiTreeUtil.getParentOfType(expr, KtIfExpression::class.java, true) ?: return null
    if (equalOrLastInThenOrElse(parentIf.then, expr) || equalOrLastInThenOrElse(parentIf.`else`, expr)) {
        return parentIf
    }
    return null
}

private fun equalOrLastInThenOrElse(branch: KtExpression?, expr: KtExpression): Boolean {
    if (branch == expr) return true
    if (branch is KtBlockExpression) {
        return branch.lastChild == expr
    }
    return false
}