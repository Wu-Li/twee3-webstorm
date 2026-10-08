package twee.integration

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PlatformTestUtil
import twee.testing.StoryProjectTestCase
import twee.hierarchy.TweeHierarchyModel
import twee.index.PassageQueryService
import twee.psi.TweeFile
import twee.relations.RelationQueryService
import twee.scope.StoryContextService
import twee.scope.StorySettings

/** Reports actual native timings when executed; has no fabricated or machine-dependent speed threshold. */
class StoryPerformanceTest : StoryProjectTestCase() {
    fun testIndexedLookupIncrementalEditHierarchyAndCancellation() {
        val fileCount = System.getenv("TWEE_PERF_FILES")?.toInt() ?: 20
        val perFile = System.getenv("TWEE_PERF_PASSAGES")?.toInt() ?: 50
        require(fileCount in 2..1000 && perFile in 2..1000)
        val count = fileCount * perFile
        val metrics = linkedMapOf<String, Double>()
        fun <T> timed(name: String, body: () -> T): T {
            val before = System.nanoTime()
            return body().also { metrics[name] = (System.nanoTime() - before) / 1_000_000.0 }
        }
        project.getService(StorySettings::class.java).loadState(StorySettings.Options(mutableListOf(
            StorySettings.Story("perf", "Performance", mutableListOf("story"), harlowe3WhenMissing = true)
        ), "perf"))
        val files = timed("fixture_creation_ms") {
            (0 until fileCount).map { file ->
                val text = (0 until perFile).joinToString("\n") { local ->
                    val number = file * perFile + local
                    ":: P$number [group${file % 10}]\n(set:\$visits to \$visits + 1)[[P${(number + 1) % count}]]\n"
                }
                myFixture.addFileToProject("story/file-$file.tw", text) as TweeFile
            }
        }
        // Indexing can already happen during fixture creation; report the remaining wait separately.
        timed("index_ready_wait_ms") { IndexingTestUtil.waitUntilIndexesAreReady(project) }
        val context = project.getService(StoryContextService::class.java)
        context.requestRefresh()
        PlatformTestUtil.waitWithEventsDispatching("Performance context", {
            context.current.storyId == "perf" && context.current.format == StoryContextService.Format.HARLOWE_3
        }, 10)
        val passages = project.getService(PassageQueryService::class.java)
        val all = timed("all_passages_ms") { passages.query(PassageQueryService.Request(PassageQueryService.Kind.ALL)) }
        assertEquals(PassageQueryService.State.READY, all.state); assertEquals(count, all.passages.size)
        val lookup = PassageQueryService.Request(PassageQueryService.Kind.NAME, "P${count - 1}")
        timed("lookup_first_ms") { assertEquals(1, passages.query(lookup).passages.size) }
        val samples = (0 until 30).map {
            val before = System.nanoTime()
            assertEquals(1, passages.query(lookup).passages.size)
            (System.nanoTime() - before) / 1_000_000.0
        }.sorted()
        metrics["lookup_warm_median_ms"] = samples[15]
        metrics["lookup_warm_p95_ms"] = samples[28]
        val relations = project.getService(RelationQueryService::class.java)
        val baseline = relations.query()
        assertEquals(RelationQueryService.State.READY, baseline.state)
        val unchanged = baseline.occurrences.filter { it.pointer.element!!.containingFile == files.last() }.map { it.fact }
        assertTrue(unchanged.isNotEmpty())
        val document = FileDocumentManager.getInstance().getDocument(files.first().virtualFile)!!
        timed("edit_commit_ms") {
            WriteCommandAction.runWriteCommandAction(project) {
                val offset = document.text.indexOf("[[P1]]")
                document.replaceString(offset, offset + 6, "[[Missing]]")
            }
            PsiDocumentManager.getInstance(project).commitDocument(document)
        }
        val refreshed = timed("incremental_relation_query_ms") { relations.query() }
        val stillCached = refreshed.occurrences.filter { it.pointer.element!!.containingFile == files.last() }.map { it.fact }
        assertEquals(unchanged.size, stillCached.size)
        assertTrue("Unchanged file facts must remain cached", unchanged.zip(stillCached).all { (before, after) -> before === after })
        val model = TweeHierarchyModel(project)
        val rows = timed("hierarchy_outgoing_ms") {
            model.children(model.at(files.first().passages.first())!!, TweeHierarchyModel.View.OUTGOING)
        }
        assertTrue(rows.rows.any { it.label.contains("Unresolved") })
        val canceled = EmptyProgressIndicator()
        var observed = false
        timed("pre_canceled_query_ms") {
            try {
                ProgressManager.getInstance().runProcess(Runnable {
                    // runProcess starts the indicator, which clears any earlier cancellation.
                    // Cancel the active process before entering the query.
                    canceled.cancel()
                    assertTrue("Query must receive a canceled indicator", canceled.isCanceled)
                    assertSame(canceled, ProgressManager.getInstance().progressIndicator)
                    passages.query(PassageQueryService.Request(PassageQueryService.Kind.ALL))
                }, canceled)
            } catch (_: ProcessCanceledException) { observed = true }
        }
        assertTrue("Canceled query must abort", observed)
        // Preserve raw output from the test report. These are fixture timings, not IDE/UI responsiveness claims.
        println("TWEE_PERF files=$fileCount passages=$count java=${System.getProperty("java.version")} os=${System.getProperty("os.name")}")
        metrics.forEach { (name, value) -> println("TWEE_PERF $name=$value") }
    }
}
