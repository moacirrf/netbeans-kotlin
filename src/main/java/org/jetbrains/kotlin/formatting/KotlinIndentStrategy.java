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
package org.jetbrains.kotlin.formatting;

import com.intellij.psi.tree.IElementType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.StyledDocument;
import org.jetbrains.kotlin.lexer.KotlinLexer;
import org.jetbrains.kotlin.lexer.KtTokens;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.modules.editor.indent.api.IndentUtils;
import org.netbeans.modules.editor.indent.spi.Context;
import org.openide.text.Line;

/**
 * Native indentation strategy for Kotlin based on {@link KotlinLexer}.
 * Replaces the legacy 2016 IntelliJ formatting engine.
 */
public class KotlinIndentStrategy {

    private static final char OPENING_BRACE_CHAR = '{';
    private static final char CLOSING_BRACE_CHAR = '}';
    private static final String CLOSING_BRACE_STRING = Character.toString(CLOSING_BRACE_CHAR);

    private final StyledDocument doc;
    private final int caretOffset;
    private int offset;

    public KotlinIndentStrategy(StyledDocument doc, int offset) {
        this.doc = doc;
        this.offset = offset;
        this.caretOffset = offset;
    }

    public KotlinIndentStrategy(Context context) {
        this((StyledDocument) context.document(), context.caretOffset());
    }

    public int addIndent() throws BadLocationException {
        if (offset == 1) {
            return offset;
        }
        if (offset == doc.getLength()) {
            offset--;
        }
        String text = doc.getText(0, doc.getLength());
        char commandText = text.charAt(offset);

        if (isBeforeCloseBrace(text, offset) && isAfterOpenBrace(text, offset)) {
            return autoEditAfterOpenBraceAndBeforeCloseBrace(text);
        } else if (CLOSING_BRACE_STRING.equals(String.valueOf(commandText))) {
            return autoEditBeforeCloseBrace(text);
        } else {
            return autoEdit(text);
        }
    }

    private static boolean isSpaceOrTab(char c) {
        return c == ' ' || c == '\t';
    }

    private static boolean isAfterOpenBrace(String text, int offset) {
        int cur = offset - 2;
        while (cur >= 0 && isSpaceOrTab(text.charAt(cur))) {
            cur--;
        }
        return cur >= 0 && text.charAt(cur) == OPENING_BRACE_CHAR;
    }

    private static boolean isBeforeCloseBrace(String text, int offset) {
        int cur = offset;
        while (cur < text.length() && isSpaceOrTab(text.charAt(cur))) {
            cur++;
        }
        return cur < text.length() && text.charAt(cur) == CLOSING_BRACE_CHAR;
    }

    private int autoEditAfterOpenBraceAndBeforeCloseBrace(String text) throws BadLocationException {
        int baseIndentSpaces = computeBaseIndentForNewBlock(text, caretOffset);
        int unitSize = getIndentUnitSize();
        String innerIndent = createIndentString(baseIndentSpaces + unitSize);
        String outerIndent = createIndentString(baseIndentSpaces);

        StringBuilder builder = new StringBuilder();
        builder.append(innerIndent).append('\n').append(outerIndent);

        int end = caretOffset;
        while (end < doc.getLength() && isSpaceOrTab(doc.getText(end, 1).charAt(0))) {
            end++;
        }
        int diff = end - caretOffset;
        doc.remove(caretOffset, diff);
        doc.insertString(caretOffset, builder.toString(), null);
        setDocumentOffset(innerIndent.length());

        return caretOffset + innerIndent.length();
    }

    private int autoEditBeforeCloseBrace(String text) throws BadLocationException {
        if (caretOffset >= 2 && text.charAt(caretOffset - 2) == '\n') {
            return caretOffset;
        }
        int spaces = computeIndentSpacesForCloseBrace(text, caretOffset);
        String indent = createIndentString(spaces);
        doc.insertString(caretOffset, indent, null);
        return caretOffset + indent.length();
    }

    private int autoEdit(String text) throws BadLocationException {
        int spaces = computeIndentSpaces(text, caretOffset, getIndentUnitSize());
        String indent = createIndentString(spaces);
        doc.insertString(caretOffset, indent, null);
        return caretOffset + indent.length();
    }

    private void setDocumentOffset(int column) {
        try {
            Line line = NbEditorUtilities.getLine(doc, offset, false);
            if (line != null) {
                line.show(Line.ShowOpenType.NONE, Line.ShowVisibilityType.NONE, column);
            }
        } catch (Throwable ignored) {
        }
    }

    public int getIndentUnitSize() {
        if (doc != null) {
            try {
                int size = IndentUtils.indentLevelSize(doc);
                if (size > 0) return size;
            } catch (Throwable ignored) {
            }
        }
        return 4;
    }

    public String createIndentString(int spaces) {
        if (spaces <= 0) {
            return "";
        }
        if (doc != null) {
            try {
                return IndentUtils.createIndentString(doc, spaces);
            } catch (Throwable ignored) {
            }
        }
        return " ".repeat(spaces);
    }

    public static int computeBaseIndentForNewBlock(String text, int offset) {
        List<Token> tokens = tokenize(text);
        int depth = 0;
        for (Token t : tokens) {
            if (t.start >= offset) break;
            if (t.type == KtTokens.LBRACE) depth++;
            else if (t.type == KtTokens.RBRACE) depth--;
        }
        int outerLevel = Math.max(0, depth - 1);
        return outerLevel * 4;
    }

    public static int computeIndentSpacesForCloseBrace(String text, int offset) {
        List<Token> tokens = tokenize(text);
        int depth = 0;
        for (Token t : tokens) {
            if (t.start >= offset) break;
            if (t.type == KtTokens.LBRACE) depth++;
            else if (t.type == KtTokens.RBRACE) depth--;
        }
        int level = Math.max(0, depth - 1);
        return level * 4;
    }

    public static int computeIndentSpaces(String text, int offset, int unitSize) {
        int lineStart = text.lastIndexOf('\n', offset - 1) + 1;
        if (lineStart < 0) lineStart = 0;

        List<Token> tokens = tokenize(text);

        int braceDepth = 0;
        int parenDepth = 0;
        int bracketDepth = 0;

        Token lastSignificantTokenBeforeLine = null;

        int unbracedIndents = 0;
        boolean inControlCondition = false;
        int controlParenDepth = 0;
        boolean waitingForStatement = false;
        int statementLine = -1;

        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.start >= lineStart) {
                break;
            }

            if (t.type == KtTokens.WHITE_SPACE || t.type == KtTokens.EOL_COMMENT ||
                t.type == KtTokens.BLOCK_COMMENT || t.type == KtTokens.DOC_COMMENT) {
                continue;
            }

            if (waitingForStatement && t.type != KtTokens.LBRACE) {
                unbracedIndents++;
                waitingForStatement = false;
                if (t.type != KtTokens.IF_KEYWORD && t.type != KtTokens.WHILE_KEYWORD && t.type != KtTokens.FOR_KEYWORD) {
                    statementLine = text.lastIndexOf('\n', t.start) + 1;
                }
            }

            if (t.type == KtTokens.IF_KEYWORD || t.type == KtTokens.WHILE_KEYWORD || t.type == KtTokens.FOR_KEYWORD) {
                inControlCondition = true;
                controlParenDepth = 0;
            } else if (t.type == KtTokens.ELSE_KEYWORD) {
                waitingForStatement = true;
            } else if (inControlCondition) {
                if (t.type == KtTokens.LPAR) {
                    controlParenDepth++;
                } else if (t.type == KtTokens.RPAR) {
                    controlParenDepth--;
                    if (controlParenDepth == 0) {
                        inControlCondition = false;
                        waitingForStatement = true;
                    }
                }
            } else if (unbracedIndents > 0) {
                int currentTokenLine = text.lastIndexOf('\n', t.start) + 1;
                if (currentTokenLine > statementLine && parenDepth == 0 && bracketDepth == 0) {
                    unbracedIndents = 0;
                }
            }

            if (t.type == KtTokens.LBRACE) {
                braceDepth++;
                unbracedIndents = 0;
                waitingForStatement = false;
            } else if (t.type == KtTokens.RBRACE) {
                braceDepth--;
                unbracedIndents = 0;
                waitingForStatement = false;
            } else if (t.type == KtTokens.LPAR) {
                parenDepth++;
            } else if (t.type == KtTokens.RPAR) {
                parenDepth--;
            } else if (t.type == KtTokens.LBRACKET) {
                bracketDepth++;
            } else if (t.type == KtTokens.RBRACKET) {
                bracketDepth--;
            }

            lastSignificantTokenBeforeLine = t;
        }

        if (unbracedIndents > 0 && statementLine >= 0) {
            int lastTokenLine = text.lastIndexOf('\n', lastSignificantTokenBeforeLine.start) + 1;
            if (lastTokenLine == statementLine && parenDepth == 0 && bracketDepth == 0) {
                unbracedIndents = 0;
            }
        }

        int totalUnbraced = unbracedIndents + (waitingForStatement ? 1 : 0);
        int indentLevel = braceDepth + totalUnbraced;
        int spaces = indentLevel * unitSize;

        int continuationIndent = unitSize * 2;
        if (parenDepth > 0) {
            spaces += continuationIndent;
        } else if (lastSignificantTokenBeforeLine != null) {
            IElementType lt = lastSignificantTokenBeforeLine.type;
            if (lt == KtTokens.EQ || lt == KtTokens.DOT || lt == KtTokens.SAFE_ACCESS ||
                lt == KtTokens.ARROW || lt == KtTokens.FUN_KEYWORD || lt == KtTokens.VAL_KEYWORD ||
                lt == KtTokens.VAR_KEYWORD) {
                spaces += continuationIndent;
            }
        }

        return Math.max(0, spaces);
    }

    public static List<Token> tokenize(String text) {
        KotlinLexer lexer = new KotlinLexer();
        lexer.start(text);
        List<Token> tokens = new ArrayList<>();
        while (lexer.getTokenType() != null) {
            tokens.add(new Token(lexer.getTokenType(), lexer.getTokenStart(), lexer.getTokenEnd()));
            lexer.advance();
        }
        return tokens;
    }

    public static class Token {
        public final IElementType type;
        public final int start;
        public final int end;

        public Token(IElementType type, int start, int end) {
            this.type = type;
            this.start = start;
            this.end = end;
        }
    }
}
