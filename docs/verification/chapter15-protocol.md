# Chapter 15 native evidence protocol

Current evidence is in [chapter15-evidence.json](chapter15-evidence.json). Null measurements mean not run. The native test classes and browser smoke have not been compiled or executed in this environment.

## Five-feature smoke

Use [the Chapter 14 setup](../tweego-run.md) and its checked-in story. Record host/build, OS, JDK, plugin commit, Tweego version, effective format ID/version, screenshots and actual outcomes for every row.

| Feature | Action and expected observation | Current evidence |
| --- | --- | --- |
| Highlighting and inherited checks | Inspect headers, nested macro/code hook, variable, link, JS/CSS contexts. Toggle an inherited check and confirm refresh without new Harlowe validators. | Pending native/UI |
| Navigation | From Arrival follow the source reference to Across files in ending.tw; a same-named passage in another selected-out story must not compete. | Integration test authored, unexecuted |
| Tracing | From `$greet` inspect the candidate code hook in startup.tw; use Reads/Writes for `$visits`; follow outgoing/incoming passage edges and physical sites. | Integration test authored, unexecuted |
| Tags | Add/remove a tag in Passages, preserve bodies and metadata, undo/redo, and confirm group/query refresh. | Existing and integrated tests authored, unexecuted |
| Build/run | Build-only leaves browser closed. Run starts at Arrival, displays startup macro text and visit count, links across files, bundles JS/CSS and loads the moon asset. Failure/cancel never launches old HTML. | Fixture/checklist prepared, unexecuted |

The startup macro returns its string using `(output-data:)`, following the [Harlowe manual](https://twine2.neocities.org/#macro_output-data). Startup is a runtime behavior to verify in the browser, not a claim made by static source tracing.

## Performance and responsiveness

1. Generate the large story using [tools/performance](../../tools/performance/README.md). Preserve its manifest, repository commit, host/build, hardware/CPU/RAM, JDK, heap, OS and plugin set with the results.
2. For cold initial indexing use a newly opened generated project in a fresh development sandbox. Record elapsed time from project open to smart/index-ready state with the IDE indexing diagnostics and profiler trace. Do not relabel fixture creation or remaining index wait as cold indexing.
3. Run the native performance fixture at default and expanded sizes. Preserve raw test XML and `TWEE_PERF` output. Record first and warmed lookup separately. Repeat three independent runs; report all runs, median and p95 with sample counts, without dropping slow samples.
4. Edit one link in one file without saving. Commit PSI through normal editor behavior; measure until navigation/hierarchy reflect the edit. Inspect the profiler for unrelated-file parsing. The native cached-fact identity assertion is a regression check, not a substitute for profiling.
5. Expand incoming/outgoing hierarchy and variable Reads/Writes for high-fanout symbols. Record first-expansion and repeated-expansion latency and capture the UI-thread trace while typing/scrolling. Report any freezes and largest observed UI task; no responsiveness pass claim without this evidence.
6. Cancel a running query and build, then close the project while work is running. Record cancellation latency, task/process termination, absence of leaked listeners/jobs, and continued UI responsiveness. The harness's pre-canceled query only tests cancellation propagation; it is not a mid-flight cancellation latency measurement.
7. Repeat real compiler/browser checks on Windows, macOS and Linux. Keep compatibility claims limited to measured hosts. Every required gate remains pending until its logs/artifacts exist.

Do not merge/release on the strength of generator tests, baseline checks, source review or authored fixtures alone. Chapter 16 implementation may proceed under the user's deferred-verification authorization.
