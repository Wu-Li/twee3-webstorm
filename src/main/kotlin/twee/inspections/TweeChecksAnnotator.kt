package twee.inspections

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import twee.psi.TweeFile
import twee.settings.TweeCheckSettings

class TweeChecksAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is TweeFile) return
        val options = element.project.getService(TweeCheckSettings::class.java).state
        for (passage in element.passages) {
            val header = passage.headerRange.substring(element.text)
            if (options.spaceAfterStartToken && header.length > 2 && !header[2].isWhitespace()) {
                holder.newAnnotation(HighlightSeverity.WARNING,
                    "No space between Start token (::) and passage name. If this is a CSS pseudo-element selector, add a universal selector (*) or whitespace before ::.")
                    .range(TextRange(passage.textRange.startOffset, passage.textRange.startOffset + 3)).create()
            }
            if (passage.name != "StoryData") continue
            val result = StoryDataChecks.evaluate(passage.bodyRange.substring(element.text), options)
            if (result.malformedJson) {
                // Empty bodies need a visible anchor; never create an out-of-file range.
                val range = if (passage.bodyRange.isEmpty) passage.nameIdentifier!!.textRange else passage.bodyRange
                holder.newAnnotation(HighlightSeverity.ERROR, "Malformed StoryData JSON!").range(range).create()
            }
        }
    }
}
