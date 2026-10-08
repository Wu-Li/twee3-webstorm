package twee.parser

import com.intellij.psi.tree.IElementType
import twee.language.TweeLanguage

/** Categories adapted from defs/harlowe-3/grammar.json (Cyrus Firheir, MIT). */
object HarloweTypes {
    private fun type(name: String) = IElementType(name, TweeLanguage)
    val MACRO_OPEN = type("MACRO_OPEN")
    val MACRO_CLOSE = type("MACRO_CLOSE")
    val PAREN_OPEN = type("PAREN_OPEN")
    val PAREN_CLOSE = type("PAREN_CLOSE")
    val HOOK_OPEN = type("HOOK_OPEN")
    val HOOK_CLOSE = type("HOOK_CLOSE")
    val COLLAPSED_OPEN = type("COLLAPSED_OPEN")
    val COLLAPSED_CLOSE = type("COLLAPSED_CLOSE")
    val MACRO_NAME = type("MACRO_NAME")
    val VARIABLE = type("VARIABLE")
    val TEMP_VARIABLE = type("TEMP_VARIABLE")
    val HOOK_NAME = type("HOOK_NAME")
    val PROPERTY = type("PROPERTY")
    val OPERATOR = type("OPERATOR")
    val NUMBER = type("NUMBER")
    val BOOLEAN = type("BOOLEAN")
    val STRING = type("STRING")
    val COMMENT = type("COMMENT")
    val LINK_OPEN = type("LINK_OPEN")
    val LINK_CLOSE = type("LINK_CLOSE")
    val LINK_TEXT = type("LINK_TEXT")
    val PUNCTUATION = type("PUNCTUATION")
    val HTML = type("HTML")
    val ATTRIBUTE = type("ATTRIBUTE")
    val ENTITY = type("ENTITY")
    val EMBEDDED = type("EMBEDDED")
    val MACRO = type("MACRO")
    val EXPRESSION = type("EXPRESSION")
    val HOOK = type("HOOK")
    val COLLAPSED = type("COLLAPSED")
    val LINK = type("LINK")

    val opens = mapOf(MACRO_OPEN to MACRO, PAREN_OPEN to EXPRESSION,
        HOOK_OPEN to HOOK, COLLAPSED_OPEN to COLLAPSED, LINK_OPEN to LINK)
    val closes = mapOf(MACRO_CLOSE to MACRO, PAREN_CLOSE to EXPRESSION,
        HOOK_CLOSE to HOOK, COLLAPSED_CLOSE to COLLAPSED, LINK_CLOSE to LINK)
}
