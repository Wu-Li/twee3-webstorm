package twee.parser

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import twee.language.TweeLanguage

object TweeTypes {
    val FILE = IFileElementType(TweeLanguage)
    val PASSAGE = IElementType("PASSAGE", TweeLanguage)
    val HEADER = IElementType("HEADER", TweeLanguage)
    val BODY = IElementType("BODY", TweeLanguage)
    val MARKER = IElementType("MARKER", TweeLanguage)
    val NAME = IElementType("NAME", TweeLanguage)
    val TAGS = IElementType("TAGS", TweeLanguage)
    val METADATA = IElementType("METADATA", TweeLanguage)
    val HEADER_SPACE = IElementType("HEADER_SPACE", TweeLanguage)
    val NEWLINE = IElementType("NEWLINE", TweeLanguage)
    val TEXT = IElementType("TEXT", TweeLanguage)
}
