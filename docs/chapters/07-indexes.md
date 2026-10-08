# Chapter 07: Passage stubs and indexes

Status: Implemented — native verification pending

Plan milestone: 4. Dependencies: 03, 06.

## Goal

Index file-derived passage declarations and tags.

## Work

Implement versioned stubs/indexes storing only file facts. Apply story scope at query time. Use cancellable background reads, candidate files and live committed PSI validation; handle dumb mode and unsaved documents.

## Acceptance

Tests cover duplicates, edits, moves, deletion, escaped names and scope changes. No nested index access or full-project reparsing on a small edit.

## Execution evidence

### 7 October 2026

- Added version-1 file/passage stubs and registered their external IDs. Passage stubs serialize raw spelling, decoded case-sensitive name and tags only; no story selection, project settings, cross-file data or navigation offsets are persisted.
- Converted passage PSI to StubBasedPsiElementBase. Name/raw-name/tag access uses stub facts when available; range/rename operations deliberately load live AST.
- Added name, tag and all-passage stub indexes. Duplicate declarations remain separate; duplicate tags within one declaration produce one index entry. Malformed headers remain absent from declaration stubs.
- Added a story-scoped query service. It completes index retrieval and collects candidate files before traversing live committed PSI, then revalidates exact names/tags and scope membership. Results contain stable story identity and smart pointers, not retained PSI trees or stale offsets.
- Added committed unsaved Twee documents to candidate sets so new/renamed declarations remain discoverable. Uncommitted in-scope Twee documents return an explicit pending state. Missing selection/unknown override and dumb mode return explicit states; no all-project reparsing fallback is used.
- Added a cancellable asynchronous entry point using committed-document, smart-mode background reads. Synchronous queries require a caller-held read action and do not commit/wait. Scope selection is applied on every query; changing settings does not require rebuilding file-derived stubs.
- Added seven native regression methods covering case-sensitive duplicates/story isolation, escaped names/tags, unsaved rename/add/tag changes, moves/deletion, scope/exclusion changes, stub-backed facts/malformed-header rejection, and uncommitted-document status.
- Executed: seven inherited characterization tests pass; plugin XML and registered-class checks pass; `git diff --check` passes. These do not execute the new Kotlin indexing code.
- Not executed: Kotlin compilation, native stub/index tests, disk serialization/reload, dumb-mode integration and performance timing. These remain explicit verification debt under the user's authorization. Run `./gradlew check` (including PassageIndexTest), then test indexing/restart/reopen and the deferred build/host gates when available.

Implementation follows the [IntelliJ stub/index contracts](https://plugins.jetbrains.com/docs/intellij/stub-indexes.html). Bump file stub and affected index versions for future schema or extraction-rule changes.
