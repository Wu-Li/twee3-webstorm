package twee.highlighting

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as Colors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.tree.IElementType
import twee.parser.HarloweLexer
import twee.parser.HarloweTypes as H
import twee.parser.TweeTypes as T

class TweeSyntaxHighlighter(private val project: Project? = null, private val enableBody: Boolean = true) : SyntaxHighlighterBase() {
    private val embedded = mutableMapOf<String, SyntaxHighlighter?>()
    private fun host(extension: String): SyntaxHighlighter? = embedded.getOrPut(extension) {
        val type = FileTypeManager.getInstance().getFileTypeByExtension(extension)
        // Protect against users associating *.js/css/json with Twee.
        if (type.name == "Twee") null else SyntaxHighlighterFactory.getSyntaxHighlighter(type, project, null)
    }
    override fun getHighlightingLexer() = HarloweLexer(enableBody) { host(it)?.highlightingLexer }
    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> {
        keys[tokenType]?.let { return arrayOf(it) }
        for (highlighter in embedded.values) {
            val attributes = highlighter?.getTokenHighlights(tokenType)
            if (attributes != null && attributes.isNotEmpty()) return attributes
        }
        return emptyArray()
    }
    companion object {
        private fun key(name: String, fallback: TextAttributesKey) = TextAttributesKey.createTextAttributesKey("TWEE_$name", fallback)
        val PASSAGE = key("PASSAGE", Colors.CLASS_NAME)
        val SPECIAL = key("SPECIAL", Colors.KEYWORD)
        val TAG = key("TAG", Colors.METADATA)
        val METADATA = key("METADATA", Colors.STRING)
        val MACRO = key("MACRO", Colors.FUNCTION_CALL)
        val VARIABLE = key("VARIABLE", Colors.GLOBAL_VARIABLE)
        val TEMP = key("TEMP", Colors.LOCAL_VARIABLE)
        val HOOK = key("HOOK", Colors.LABEL)
        val STRING = key("STRING", Colors.STRING)
        val NUMBER = key("NUMBER", Colors.NUMBER)
        val KEYWORD = key("KEYWORD", Colors.KEYWORD)
        val OPERATOR = key("OPERATOR", Colors.OPERATION_SIGN)
        val PROPERTY = key("PROPERTY", Colors.INSTANCE_FIELD)
        val COMMENT = key("COMMENT", Colors.BLOCK_COMMENT)
        val LINK = key("LINK", Colors.FUNCTION_CALL)
        val HTML = key("HTML", Colors.MARKUP_TAG)
        val ATTRIBUTE = key("ATTRIBUTE", Colors.MARKUP_ATTRIBUTE)
        val ENTITY = key("ENTITY", Colors.VALID_STRING_ESCAPE)
        val PUNCTUATION = key("PUNCTUATION", Colors.BRACKETS)
        val keys = mapOf(
            T.NAME to PASSAGE, T.TAGS to TAG, T.METADATA to METADATA, T.MARKER to PUNCTUATION,
            H.MACRO_NAME to MACRO, H.VARIABLE to VARIABLE, H.TEMP_VARIABLE to TEMP,
            H.HOOK_NAME to HOOK, H.PROPERTY to PROPERTY, H.OPERATOR to OPERATOR,
            H.BOOLEAN to KEYWORD, H.NUMBER to NUMBER, H.STRING to STRING,
            H.COMMENT to COMMENT, H.LINK_TEXT to LINK, H.HTML to HTML, H.ATTRIBUTE to ATTRIBUTE, H.ENTITY to ENTITY,
            H.MACRO_OPEN to PUNCTUATION, H.MACRO_CLOSE to PUNCTUATION,
            H.PAREN_OPEN to PUNCTUATION, H.PAREN_CLOSE to PUNCTUATION,
            H.HOOK_OPEN to PUNCTUATION, H.HOOK_CLOSE to PUNCTUATION,
            H.COLLAPSED_OPEN to PUNCTUATION, H.COLLAPSED_CLOSE to PUNCTUATION,
            H.LINK_OPEN to PUNCTUATION, H.LINK_CLOSE to PUNCTUATION, H.PUNCTUATION to PUNCTUATION
        )
    }
}

class TweeSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?) = TweeSyntaxHighlighter(project, project?.getService(twee.scope.StoryContextService::class.java)?.supportsHarlowe(virtualFile) ?: true)
}
