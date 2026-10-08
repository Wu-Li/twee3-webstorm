package twee.relations

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import twee.psi.TweeFile
import twee.relations.RelationFact.Kind as K
import java.io.*

class RelationExtractorTest : BasePlatformTestCase() {
    private fun extract(body: String): List<RelationFact> = RelationExtractor.extract(
        myFixture.configureByText("relations.tw", ":: Start\n$body") as TweeFile)

    fun testAliasesCustomCaseAndPhysicalOccurrences() {
        val facts = extract("(go-to: 'A')(GO_TO: 'B')(goto: 'A')(-_-_g-o-t-o: 'C')(log10: 100)(\$Mixed:)(_Mixed:)")
        assertEquals(listOf("goto", "goto", "goto", "goto", "log10"), facts.filter { it.kind == K.NAMED_CALL }.map { it.name })
        assertEquals(listOf("\$Mixed", "_Mixed"), facts.filter { it.kind == K.CUSTOM_CALL }.map { it.name })
        assertEquals(4, facts.count { it.kind == K.TRANSITION })
        assertEquals(2, facts.count { it.kind == K.READ })
    }
    fun testAssignmentReadWriteAndPropertyMutation() {
        val facts = extract("(set: \$a to \$b)(put: \$a into \$c)(move: \$arr's 1st into \$d)(set: \$map's (str: \$key) to \$v)(move: \$plain into \$dest)(input-box: bind \$input)(input-box: 2bind \$other)")
        fun access(name: String) = facts.filter { it.name == name && it.isVariable }.map { it.kind }
        assertEquals(listOf(K.WRITE, K.READ), access("\$a"))
        assertEquals(listOf(K.READ), access("\$b"))
        assertEquals(listOf(K.WRITE), access("\$c"))
        assertEquals(listOf(K.READ_WRITE), access("\$arr"))
        assertEquals(listOf(K.READ_WRITE), access("\$map"))
        assertEquals(listOf(K.READ), access("\$key"))
        assertEquals(listOf(K.READ), access("\$plain"))
        assertEquals(listOf(K.READ_WRITE), access("\$input"))
        assertEquals(listOf(K.READ_WRITE), access("\$other"))
    }
    fun testHookInheritanceAndSiblingIsolation() {
        val facts = extract("(set: _a to 1)[(set: _a to 2)(set: _b to 1)[(print: _a + _b)]] [(set: _b to 2)] (print: _b)")
        val a = facts.filter { it.name == "_a" }
        assertEquals(1, a.map { it.scope }.distinct().size)
        val b = facts.filter { it.name == "_b" }
        assertEquals(b[0].scope, b[1].scope)
        assertEquals(3, b.map { it.scope }.distinct().size)
    }
    fun testLoopAndLambdaShadowing() {
        val facts = extract("(set: _a to 1)(for: each _a, 2,3)[(print: _a)](print: _a)(folded: _n making _sum via _sum + _n + _a, 0, 1,2)")
        val a = facts.filter { it.name == "_a" }
        assertEquals(K.BINDING, a[1].kind)
        assertEquals(a[1].scope, a[2].scope)
        assertEquals(a[0].scope, a[3].scope)
        assertEquals(a[0].scope, a[4].scope)
        assertFalse(a[0].scope == a[1].scope)
        for (name in listOf("_n", "_sum")) {
            val occurrences = facts.filter { it.name == name }
            assertEquals(K.BINDING, occurrences.first().kind)
            assertEquals(1, occurrences.map { it.scope }.distinct().size)
        }
    }
    fun testCustomBodyIsolationParametersAndMultipleBindings() {
        val facts = extract("(set: _outer to 1)(set: \$m to (macro: num-type _arg, [(print: _arg + _outer + \$story)[[A]]]))(set: \$m to (macro: [(go-to: 'B')]))(\$m: 2)")
        val bindings = facts.filter { it.kind == K.MACRO_BINDING }
        assertEquals(2, bindings.size)
        val parameter = facts.first { it.kind == K.BINDING }
        assertEquals(bindings.first().body, parameter.owner)
        val outer = facts.filter { it.name == "_outer" }
        assertFalse(outer[0].scope == outer[1].scope)
        assertTrue(facts.filter { it.kind in setOf(K.LINK, K.TRANSITION) }.all { it.owner != it.passage })
        assertTrue(facts.single { it.kind == K.CUSTOM_CALL }.candidate)
    }
    fun testDynamicAndUnknownWriteReadsWithoutFalseContexts() {
        val facts = extract("[[\$destination]](go-to: \$destination)(\$unknown:)(print: _neverWritten)<!-- \$hidden (bad:) --> (print: '\$hidden [[Hidden]]') <script>\$hidden</script>")
        assertEquals(2, facts.count { it.kind in setOf(K.LINK, K.TRANSITION) && it.candidate && it.name.isEmpty() })
        assertTrue(facts.any { it.name == "_neverWritten" && it.kind == K.READ })
        assertFalse(facts.any { it.name == "\$hidden" || it.name == "bad" })
    }
    fun testSerializationRoundTripIncludesEveryFieldAndLongNames() {
        val facts = extract("(set: \$m to (macro: num-type _x, [(display: '雪')]))(\$m: 1)") +
            RelationFact(K.LINK, "雪".repeat(30000), 1, 2, 0, 0)
        val bytes = ByteArrayOutputStream()
        RelationExternalizer.save(DataOutputStream(bytes), facts)
        assertEquals(facts, RelationExternalizer.read(DataInputStream(ByteArrayInputStream(bytes.toByteArray()))))
    }
}
