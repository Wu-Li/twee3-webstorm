package twee.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.psi.FileViewProvider
import twee.language.TweeFileType
import twee.language.TweeLanguage

class TweeFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, TweeLanguage) {
    override fun getFileType() = TweeFileType
    val passages: List<TweePassage> get() = children.filterIsInstance<TweePassage>()
}
