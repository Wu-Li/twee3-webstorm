package twee.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.openapi.project.Project
import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.psi.FileViewProvider
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import twee.psi.TweeFile
import twee.psi.TweePassage
import twee.resolve.PassageTargetPsi

class TweeParser : PsiParser {
    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        val file = builder.mark()
        while (!builder.eof()) {
            if (builder.tokenType != TweeTypes.MARKER) { builder.advanceLexer(); continue }
            val passage = builder.mark()
            val header = builder.mark()
            builder.advanceLexer()
            while (!builder.eof() && builder.tokenType != TweeTypes.NEWLINE) builder.advanceLexer()
            header.done(TweeTypes.HEADER)
            if (builder.tokenType == TweeTypes.NEWLINE) builder.advanceLexer()
            if (!builder.eof() && builder.tokenType != TweeTypes.MARKER) {
                val body = builder.mark()
                val stack = mutableListOf<Pair<PsiBuilder.Marker, IElementType>>()
                while (!builder.eof() && builder.tokenType != TweeTypes.MARKER) {
                    val type = builder.tokenType
                    val open = HarloweTypes.opens[type]
                    val close = HarloweTypes.closes[type]
                    if (open != null) stack.add(builder.mark() to open)
                    if (close != null) {
                        val matching = stack.indexOfLast { it.second == close }
                        if (matching >= 0) {
                            // Tolerate incomplete inner forms without synthesizing error nodes.
                            while (stack.lastIndex > matching) {
                                val (marker, kind) = stack.removeAt(stack.lastIndex); marker.done(kind)
                            }
                            builder.advanceLexer()
                            val (marker, kind) = stack.removeAt(stack.lastIndex); marker.done(kind)
                            continue
                        }
                    }
                    builder.advanceLexer()
                }
                while (stack.isNotEmpty()) {
                    val (marker, kind) = stack.removeAt(stack.lastIndex); marker.done(kind)
                }
                body.done(TweeTypes.BODY)
            }
            passage.done(TweeTypes.PASSAGE)
        }
        file.done(root)
        return builder.treeBuilt
    }
}

class TweeParserDefinition : ParserDefinition {
    override fun createLexer(project: Project?) = HarloweLexer()
    override fun createParser(project: Project?) = TweeParser()
    override fun getFileNodeType() = TweeTypes.FILE
    override fun getWhitespaceTokens() = TokenSet.EMPTY
    override fun getCommentTokens() = TokenSet.EMPTY
    override fun getStringLiteralElements() = TokenSet.EMPTY
    override fun createFile(viewProvider: FileViewProvider) = TweeFile(viewProvider)
    override fun createElement(node: ASTNode) = when (node.elementType) {
        TweeTypes.PASSAGE -> TweePassage(node)
        HarloweTypes.LINK, HarloweTypes.MACRO -> PassageTargetPsi(node)
        else -> ASTWrapperPsiElement(node)
    }
}
