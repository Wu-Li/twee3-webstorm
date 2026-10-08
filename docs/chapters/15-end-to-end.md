# Chapter 15: End-to-end and performance evidence

Status: Native CI and fixture measurements passed; full acceptance pending

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
- Actual environment and available results: [chapter15-evidence.json](../verification/chapter15-evidence.json). Native fixture measurements now recorded from CI at `d455372`.

## Verification pending

Both native integration/performance tests passed in run 37821613335 at `d455372`, alongside all 97 tests. The WS artifact records 20 files/1,000 passages on Linux/JDK 21.0.11: warm lookup median 0.397 ms, p95 2.941 ms; incremental relation query 52.474 ms; pre-canceled query 0.306 ms. Exact raw values and provenance are in the evidence JSON. These are one-run fixture measurements, not cold-index or UI responsiveness claims.

Complete the installed-host, real compiler/browser, cold-index/UI and cross-platform portions of [the evidence protocol](../verification/chapter15-protocol.md). All required release checks remain mandatory before merge/release.
