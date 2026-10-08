package twee.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.util.IncorrectOperationException
import twee.language.TweeFileType
import twee.parser.TweeHeader
import twee.parser.TweeTypes

class TweePassage(node: ASTNode) : ASTWrapperPsiElement(node), PsiNameIdentifierOwner {
    private val header get() = node.findChildByType(TweeTypes.HEADER)!!
    val rawName: String get() = nameIdentifier!!.text
    override fun getName(): String = TweeHeader.decode(rawName)
    override fun getNameIdentifier(): PsiElement? = header.findChildByType(TweeTypes.NAME)?.psi
    override fun getTextOffset() = nameIdentifier?.textOffset ?: super.getTextOffset()
    val headerRange: TextRange get() = header.textRange
    val tagsRange: TextRange? get() = header.findChildByType(TweeTypes.TAGS)?.textRange
    val metadataRange: TextRange? get() = header.findChildByType(TweeTypes.METADATA)?.textRange
    val bodyRange: TextRange get() = node.findChildByType(TweeTypes.BODY)?.textRange
        ?: TextRange(textRange.endOffset, textRange.endOffset)
    val tags: List<String> get() = header.findChildByType(TweeTypes.TAGS)?.text
        ?.drop(1)?.dropLast(1)?.trim()?.takeIf { it.isNotEmpty() }?.split(Regex("\\s+")) ?: emptyList()
    override fun setName(name: String): PsiElement {
        if (name.isBlank() || name != name.trim() || name.any { it == '\r' || it == '\n' })
            throw IncorrectOperationException("Passage name must be nonempty and fit on one line")
        val file = PsiFileFactory.getInstance(project).createFileFromText("rename.twee", TweeFileType, ":: ${TweeHeader.encode(name)}") as TweeFile
        val replacement = file.passages.single().nameIdentifier!!
        nameIdentifier!!.replace(replacement)
        return this
    }
}
