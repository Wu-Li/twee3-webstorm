package twee.language

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

object TweeLanguage : Language("Twee")

object TweeFileType : LanguageFileType(TweeLanguage) {
    override fun getName() = "Twee"
    override fun getDescription() = "Twee story source"
    override fun getDefaultExtension() = "twee"
    override fun getIcon(): Icon? = null
}
