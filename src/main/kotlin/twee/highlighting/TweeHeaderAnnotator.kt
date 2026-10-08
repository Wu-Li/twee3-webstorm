package twee.highlighting

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import twee.psi.TweePassage

/** Semantic colors only: no new syntax, macro, argument or unresolved-target diagnostics. */
class TweeHeaderAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is TweePassage) return
        if (element.name in setOf("StoryTitle", "StoryData", "Start")) {
            element.nameIdentifier?.let {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(it)
                    .textAttributes(TweeSyntaxHighlighter.SPECIAL).create()
            }
        }
        if (element.tags.any { it == "script" || it == "stylesheet" }) {
            element.tagsRange?.let {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(it)
                    .textAttributes(TweeSyntaxHighlighter.SPECIAL).create()
            }
        }
    }
}
