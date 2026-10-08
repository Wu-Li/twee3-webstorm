# Chapter 09: Passages window and tag editing

Status: Implemented; native/UI verification pending

Plan milestone: 5. Dependencies: 07, 08.

## Implemented

- Registered the native **Passages** tool window (the sample scaffold was already removed). Groups use the selected story's live passage query, with a distinct Untagged bucket, tag suggestions, file locations, and smart pointers for navigation. Selecting one passage opens its header; Enter/double-click focuses the editor. Multiple group appearances of the same passage count once for editing.
- Multi-selection displays tags shared by all selected passages separately from tags on only some. Add/remove acts on the selected passages, not on whole groups. Duplicate additions are no-ops; removal deletes all exact occurrences in selected headers only. Tags remain case-sensitive and retain special tag spelling/meaning.
- `TagEdits` exposes exact tag ranges and minimal edit plans. Existing escaping, tag order, header spacing, metadata and body text remain untouched. Removing the final tag retains the empty bracket container and surrounding whitespace, preserving unrelated bytes. New tags insert into the existing tag section or immediately after the passage name. The IDE handles document line-separator normalization and persistence.
- `PassageTagService` commits live documents, checks selected-story membership, writable files and guarded ranges, plans the complete batch, and applies descending offsets within each file in one global undoable write command. Stale/missing/out-of-story selections fail before any text is changed. Unsaved documents are edited without saving them.
- Debounced background refresh responds to document/VFS changes, story settings and indexing transitions. Queries wait for committed documents and smart mode; stale results are discarded. Selection/expanded groups are retained where possible, and listeners/background work expire with the tool-window content.

## Checks executed

- `npm test --prefix tools/characterization`: 7/7 inherited baseline tests pass. These do not execute the new Kotlin implementation.
- `git diff --check`: passed.
- Plugin descriptor XML parse and registered class source presence: passed.
- Source review against [IntelliJ tool windows](https://plugins.jetbrains.com/docs/intellij/tool-windows.html) and [document editing](https://plugins.jetbrains.com/docs/intellij/documents.html).

## Verification pending

Four `TagEditsTest` cases cover exact insertion/removal ranges, escaping/CRLF/header preservation, input validation and mixed-selection states. Four `PassageTagTest` native fixtures cover batch offsets/body preservation, multi-file undo/redo, committed unsaved changes/group refresh, and all-or-nothing invalid/out-of-story rejection. The existing settings snapshot test now supplies the service's project dependency. These Kotlin tests have **not** been compiled or executed.

JDK 21/Gradle and WebStorm sandbox prerequisites remain unavailable as recorded in Chapter 02. Run native tests, then verify window layout, keyboard/mouse navigation, suggestions, multi-selection, empty groups, scope changes, indexing states, undo/redo across files, read-only handling, line-separator preservation on save, and special-tag highlighting refresh. Do not merge or release before required verification passes.

Next implementation: Chapter 10, static symbols and relations.
