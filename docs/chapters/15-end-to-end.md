# Chapter 15: End-to-end and performance evidence

Status: Harness and fixtures implemented; native/end-to-end/performance evidence pending

Plan milestone: 8. Dependencies: 05, 09, 12, 14.

## Implemented

- Extended the shared browser smoke story with a startup custom macro, global variable read/write and entry/destination tags, retaining custom start, cross-file links, JS/CSS and relative assets.
- Added `StoryWorkflowTest` integrating the shared fixture with StoryData selection, cross-story isolation, passage resolution, startup macro candidate tracing, variable reads/writes, outgoing hierarchy, tag edits/body preservation and committed unsaved link edits.
- Added `StoryPerformanceTest`: configurable repeatable native workload, query count/correctness assertions, warm median/p95 lookup timings, edit/commit and hierarchy timings, pre-canceled query propagation, and unchanged-file relation cache identity checks. Raw measurements are emitted only when this test actually runs.
- Added a deterministic large-story generator and manifest, generator regression tests, a five-feature/native performance protocol and a machine-readable evidence record. Remaining index wait, cold IDE indexing, in-process timings and actual UI responsiveness are explicitly distinguished.

## Checks executed

- `npm test --prefix tools/characterization`: 7/7 inherited baseline checks passed.
- `python -m unittest discover -s tools/performance -p 'test_*.py' -v`: 3/3 passed, covering deterministic graph/hash manifests, existing-directory preservation and invalid-size rejection.
- Generated and validated the 200-file/50-passages workload: 206 hashed files, 10,003 selected passages and 20,000 literal links. All manifest hashes verified. This is fixture validation, not native performance evidence.
- `git diff --check`: passed.
- Actual environment and available results: [chapter15-evidence.json](../verification/chapter15-evidence.json). Native measurement fields remain null.

## Verification pending

The two new native integration/performance tests have **not been compiled or executed**. No cold indexing, incremental native edit/query timings, UI responsiveness profile, real Tweego build or browser smoke result was collected. JDK 21/Gradle/WebStorm prerequisites remain unavailable as recorded in Chapter 02; available Java is still 17.

Execute [the native evidence protocol](../verification/chapter15-protocol.md), retain logs/screenshots/traces and resolve observed integration failures before acceptance. All native tests and required release checks must pass before merge/release. The user's authorization permits continuing implementation without treating these unexecuted checks as passed.

Next implementation: Chapter 16, packaging, CI and supported hosts.
