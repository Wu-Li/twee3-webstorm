package twee.index

import com.intellij.psi.PsiFile
import com.intellij.psi.stubs.*
import com.intellij.psi.tree.IStubFileElementType
import twee.language.TweeLanguage
import twee.parser.TweeTypes
import twee.psi.TweeFile
import twee.psi.TweePassage

class TweeFileStub(file: TweeFile?) : PsiFileStubImpl<TweeFile>(file) {
    override fun getType() = TweeTypes.FILE
}

class TweeFileElementType : IStubFileElementType<TweeFileStub>(TweeLanguage) {
    override fun getExternalId() = "twee.FILE"
    override fun getStubVersion() = 1
    override fun getBuilder() = object : DefaultStubBuilder() {
        override fun createStubForFile(file: PsiFile): StubElement<*> = TweeFileStub(file as TweeFile)
    }
    override fun serialize(stub: TweeFileStub, stream: StubOutputStream) = Unit
    override fun deserialize(stream: StubInputStream, parent: StubElement<*>?) = TweeFileStub(null)
}

class TweePassageStub(parent: StubElement<*>?, val rawName: String, val passageName: String, val tags: List<String>) :
    StubBase<TweePassage>(parent, TweeTypes.PASSAGE)

class TweePassageElementType : IStubElementType<TweePassageStub, TweePassage>("PASSAGE", TweeLanguage) {
    override fun getExternalId() = "twee.PASSAGE"
    override fun createPsi(stub: TweePassageStub) = TweePassage(stub)
    override fun createStub(psi: TweePassage, parent: StubElement<*>?) = TweePassageStub(parent, psi.rawName, psi.name, psi.tags)
    override fun serialize(stub: TweePassageStub, stream: StubOutputStream) {
        stream.writeName(stub.rawName); stream.writeName(stub.passageName)
        stream.writeVarInt(stub.tags.size); stub.tags.forEach(stream::writeName)
    }
    override fun deserialize(stream: StubInputStream, parent: StubElement<*>?): TweePassageStub {
        val raw = stream.readNameString() ?: ""
        val name = stream.readNameString() ?: ""
        val tags = List(stream.readVarInt()) { stream.readNameString() ?: "" }
        return TweePassageStub(parent, raw, name, tags)
    }
    override fun indexStub(stub: TweePassageStub, sink: IndexSink) {
        sink.occurrence(TweePassageNameIndex.KEY, stub.passageName)
        stub.tags.distinct().forEach { sink.occurrence(TweePassageTagIndex.KEY, it) }
        sink.occurrence(TweeAllPassagesIndex.KEY, "all")
    }
}

class TweePassageNameIndex : StringStubIndexExtension<TweePassage>() {
    override fun getKey() = KEY
    override fun getVersion() = 1
    companion object { @JvmField val KEY = StubIndexKey.createIndexKey<String, TweePassage>("twee.passage.name") }
}
class TweePassageTagIndex : StringStubIndexExtension<TweePassage>() {
    override fun getKey() = KEY
    override fun getVersion() = 1
    companion object { @JvmField val KEY = StubIndexKey.createIndexKey<String, TweePassage>("twee.passage.tag") }
}
class TweeAllPassagesIndex : StringStubIndexExtension<TweePassage>() {
    override fun getKey() = KEY
    override fun getVersion() = 1
    companion object { @JvmField val KEY = StubIndexKey.createIndexKey<String, TweePassage>("twee.passage.all") }
}
