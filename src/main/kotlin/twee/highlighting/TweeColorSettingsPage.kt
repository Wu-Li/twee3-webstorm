package twee.highlighting

import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import javax.swing.Icon

class TweeColorSettingsPage : ColorSettingsPage {
    override fun getDisplayName() = "Twee / Harlowe 3"
    override fun getIcon(): Icon? = null
    override fun getHighlighter() = TweeSyntaxHighlighter()
    override fun getAdditionalHighlightingTagToDescriptorMap() = mapOf("special" to TweeSyntaxHighlighter.SPECIAL)
    override fun getColorDescriptors() = ColorDescriptor.EMPTY_ARRAY
    override fun getAttributeDescriptors() = arrayOf(
        AttributesDescriptor("Passage name", TweeSyntaxHighlighter.PASSAGE),
        AttributesDescriptor("Special passage or tag", TweeSyntaxHighlighter.SPECIAL),
        AttributesDescriptor("Tags", TweeSyntaxHighlighter.TAG),
        AttributesDescriptor("Metadata", TweeSyntaxHighlighter.METADATA),
        AttributesDescriptor("Macro", TweeSyntaxHighlighter.MACRO),
        AttributesDescriptor("Story variable", TweeSyntaxHighlighter.VARIABLE),
        AttributesDescriptor("Temporary variable", TweeSyntaxHighlighter.TEMP),
        AttributesDescriptor("Hook", TweeSyntaxHighlighter.HOOK),
        AttributesDescriptor("String", TweeSyntaxHighlighter.STRING),
        AttributesDescriptor("Number", TweeSyntaxHighlighter.NUMBER),
        AttributesDescriptor("Boolean", TweeSyntaxHighlighter.KEYWORD),
        AttributesDescriptor("Operator and type word", TweeSyntaxHighlighter.OPERATOR),
        AttributesDescriptor("Property", TweeSyntaxHighlighter.PROPERTY),
        AttributesDescriptor("Comment", TweeSyntaxHighlighter.COMMENT),
        AttributesDescriptor("Passage link", TweeSyntaxHighlighter.LINK),
        AttributesDescriptor("HTML", TweeSyntaxHighlighter.HTML),
        AttributesDescriptor("HTML attribute", TweeSyntaxHighlighter.ATTRIBUTE),
        AttributesDescriptor("HTML entity", TweeSyntaxHighlighter.ENTITY),
        AttributesDescriptor("Punctuation", TweeSyntaxHighlighter.PUNCTUATION)
    )
    override fun getDemoText() = """
        :: <special>Start</special> [intro] {"position":"100,200"}
        Prose isn't a string (or a macro).
        (set: ${'$'}visits to (max: 1, 2))
        (if: ${'$'}visits is 1)[(print: "Welcome")]
        |details>[{A named hook}] ?details _temporary
        (print: ${'$'}visits's 1st) (print: true)
        [[Continue->Next]] <b title="hello">HTML &amp; text</b>
        <!-- Comments do not contain (macros:) -->
        :: Script [<special>script</special>]
        const greeting = "Hello";
        :: Style [<special>stylesheet</special>]
        body { color: red; }
        :: <special>StoryData</special>
        {"ifid":"example", "format":"Harlowe", "format-version":"3.3.9"}
    """.trimIndent()
}
