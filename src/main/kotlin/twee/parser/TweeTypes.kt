package twee.parser

import com.intellij.psi.tree.IElementType
import twee.index.TweeFileElementType
import twee.index.TweePassageElementType
import twee.language.TweeLanguage

object TweeTypes {
    @JvmField val FILE = TweeFileElementType()
    @JvmField val PASSAGE = TweePassageElementType()
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
