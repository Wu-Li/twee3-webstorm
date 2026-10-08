package twee.psi

import com.intellij.extapi.psi.StubBasedPsiElementBase
import twee.index.TweePassageStub
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.StubBasedPsiElement
import com.intellij.util.IncorrectOperationException
import twee.language.TweeFileType
import twee.parser.TweeHeader
import twee.parser.TweeTypes

class TweePassage : StubBasedPsiElementBase<TweePassageStub>, StubBasedPsiElement<TweePassageStub>, PsiNameIdentifierOwner {
    constructor(node: ASTNode) : super(node)
    constructor(stub: TweePassageStub) : super(stub, TweeTypes.PASSAGE)
    private val header get() = node.findChildByType(TweeTypes.HEADER)!!
    val rawName: String get() = stub?.rawName ?: nameIdentifier!!.text
    override fun getName(): String = stub?.passageName ?: TweeHeader.decode(rawName)
    override fun getNameIdentifier(): PsiElement? = header.findChildByType(TweeTypes.NAME)?.psi
    override fun getTextOffset() = nameIdentifier?.textOffset ?: super.getTextOffset()
    val headerRange: TextRange get() = header.textRange
    val tagsRange: TextRange? get() = header.findChildByType(TweeTypes.TAGS)?.textRange
    val metadataRange: TextRange? get() = header.findChildByType(TweeTypes.METADATA)?.textRange
    val bodyRange: TextRange get() = node.findChildByType(TweeTypes.BODY)?.textRange
        ?: TextRange(textRange.endOffset, textRange.endOffset)
    val tags: List<String> get() = stub?.tags ?: header.findChildByType(TweeTypes.TAGS)?.text
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
