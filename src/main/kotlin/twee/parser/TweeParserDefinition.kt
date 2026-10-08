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
                while (!builder.eof() && builder.tokenType != TweeTypes.MARKER) builder.advanceLexer()
                body.done(TweeTypes.BODY)
            }
            passage.done(TweeTypes.PASSAGE)
        }
        file.done(root)
        return builder.treeBuilt
    }
}

class TweeParserDefinition : ParserDefinition {
    override fun createLexer(project: Project?) = TweeLexer()
    override fun createParser(project: Project?) = TweeParser()
    override fun getFileNodeType() = TweeTypes.FILE
    override fun getWhitespaceTokens() = TokenSet.EMPTY
    override fun getCommentTokens() = TokenSet.EMPTY
    override fun getStringLiteralElements() = TokenSet.EMPTY
    override fun createFile(viewProvider: FileViewProvider) = TweeFile(viewProvider)
    override fun createElement(node: ASTNode) = when (node.elementType) {
        TweeTypes.PASSAGE -> TweePassage(node)
        else -> ASTWrapperPsiElement(node)
    }
}
