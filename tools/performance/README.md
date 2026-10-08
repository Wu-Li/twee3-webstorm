# Reproducible story workloads

Run from the repository root with Python 3:

```sh
python -m unittest discover -s tools/performance -p 'test_*.py' -v
python tools/performance/generate_story.py /tmp/twee-large-story --files 200 --passages 50
```

Use a new destination each time; existing directories are refused without modification. The default fixture contains 200 chapter files, 10,003 selected passages including metadata/startup, 20,000 links, tag groups, a startup custom macro, a story variable and JS/CSS. A separate story duplicates a passage name; generated output and a format installation provide exclusion probes. `manifest.json` records workload parameters and SHA-256 hashes, without timestamps or random data. Generation is not a performance measurement.

Open the generated root in WebStorm and define two stories rooted at `story` and `other-story`. Select `story`, set output exclusion to `story/dist`, working directory to `story`, and HTML output to `dist/index.html`. `P000000` must resolve only to the selected story. Do not include the noise fixture `story/dist/stale.tw` when compiling manually. Use the build service's explicit scoped inputs.

## Native measurement harness

With JDK 21, Gradle dependencies and the IntelliJ test platform available:

```sh
./gradlew test --tests 'twee.integration.StoryWorkflowTest'
./gradlew test --tests 'twee.integration.StoryPerformanceTest' --rerun-tasks
TWEE_PERF_FILES=200 TWEE_PERF_PASSAGES=50 ./gradlew test --tests 'twee.integration.StoryPerformanceTest' --rerun-tasks
```

On Windows set the two environment variables in PowerShell, then use `gradlew.bat`. The native harness constructs its own 1,000-passage default workload; the expanded parameters produce 10,000 passages. It is intentionally distinct from the generated browser story, which includes three additional special passages and a second edge per passage. Never compare timings without recording which workload was used.

Preserve `TWEE_PERF` lines from `build/test-results/test` XML output plus the full test report. The harness reports creation, remaining index wait, all-passage query, first lookup, 30 warm lookup samples summarized as median/p95, incremental edit/commit, live relation refresh, hierarchy expansion and pre-canceled query timings. It checks that an unrelated file reuses its cached relation facts after a small edit. No machine-dependent pass threshold is fabricated.

These are in-process fixture measurements. Indexing may overlap fixture creation; `index_ready_wait_ms` is not total cold indexing time. Test calls run in the fixture's thread context; they do not prove background UI responsiveness. For cold indexing and UI evidence, follow [the native measurement protocol](../../docs/verification/chapter15-protocol.md).
