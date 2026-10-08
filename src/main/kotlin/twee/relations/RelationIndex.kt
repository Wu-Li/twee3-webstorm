package twee.relations

import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import twee.language.TweeFileType
import twee.psi.TweeFile
import java.io.DataInput
import java.io.DataOutput

class RelationIndex : FileBasedIndexExtension<String, List<RelationFact>>() {
    override fun getName() = ID
    override fun getVersion() = 1
    override fun dependsOnFileContent() = true
    override fun getKeyDescriptor() = EnumeratorStringDescriptor.INSTANCE
    override fun getValueExternalizer() = RelationExternalizer
    override fun getInputFilter() = FileBasedIndex.InputFilter { it.fileType == TweeFileType }
    override fun getIndexer() = DataIndexer<String, List<RelationFact>, FileContent> { content ->
        val file = content.psiFile as? TweeFile
        val facts = file?.let(RelationExtractor::extract).orEmpty()
        facts.groupBy { it.indexKey } + mapOf(ALL to facts)
    }
    companion object {
        @JvmField val ID = com.intellij.util.indexing.ID.create<String, List<RelationFact>>("twee.relations")
        const val ALL = "*"
    }
}

object RelationExternalizer : DataExternalizer<List<RelationFact>> {
    private fun writeString(out: DataOutput, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8); out.writeInt(bytes.size); out.write(bytes)
    }
    private fun readString(input: DataInput): String {
        val length = input.readInt()
        require(length >= 0)
        val bytes = ByteArray(length); input.readFully(bytes); return bytes.toString(Charsets.UTF_8)
    }
    override fun save(out: DataOutput, value: List<RelationFact>) {
        out.writeInt(value.size)
        for (fact in value) {
            out.writeInt(fact.kind.ordinal); writeString(out, fact.name)
            out.writeInt(fact.start); out.writeInt(fact.end); out.writeInt(fact.passage); out.writeInt(fact.owner)
            writeString(out, fact.scope); out.writeInt(fact.body); out.writeBoolean(fact.candidate)
        }
    }
    override fun read(input: DataInput): List<RelationFact> = List(input.readInt()) {
        RelationFact(RelationFact.Kind.entries[input.readInt()], readString(input), input.readInt(), input.readInt(),
            input.readInt(), input.readInt(), readString(input), input.readInt(), input.readBoolean())
    }
}
