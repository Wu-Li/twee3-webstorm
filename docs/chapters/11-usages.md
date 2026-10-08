# Chapter 11: Native Find Usages

Status: Implemented; native/index/UI verification pending

Plan milestone: 6. Dependencies: 10.

## Implemented

- Added FindUsagesProvider and a lexer-backed DefaultWordsScanner, target evaluator, logical-symbol handler factory, exact ReferencesSearch executor, usage descriptions/types and ReadWriteAccessDetector. Registered each adapter in plugin.xml.
- Logical macro/variable targets are backed by physical occurrence smart pointers rather than an invented first assignment. No-known-write reads remain searchable. Symbol identity includes the selected story; temporary identities additionally include file and binding scope. Identity is rebuilt from live file facts after edits, and deleted/renamed anchors become invalid rather than silently changing search targets.
- Native Find Usages and highlighting share an exact indexed search path. It covers normalized named macro aliases, variable read/write/call sites and escaped/multiword passage names. Passage hits use the existing soft polyvariant references, preserving duplicate declaration candidates. A resolved custom code hook maps back to its variable symbol for the handler.
- Searches apply the user-requested global/local scope in addition to the story/binding scope, retain exact ranges, deduplicate physical occurrences and exclude duplicate assignment metadata. Pending indexing/uncommitted documents cancel rather than reporting a completed empty search. Consumer cancellation and progress cancellation propagate.
- Added usage presentation for calls, reads, writes, conservative read/write candidates, links, displays and transitions. Full text/comment/string searches are disabled; the feature reports structural source occurrences. This does not add rename support, inspectors or runtime resolution claims.

## Checks executed

- `npm test --prefix tools/characterization`: 7/7 inherited baseline checks pass. These do not execute new Kotlin code.
- `git diff --check`: passed.
- Plugin XML parse and all registered native class source presence: passed.
- Reviewed [Find Usages SDK guidance](https://plugins.jetbrains.com/docs/intellij/find-usages.html) and primary IntelliJ branch-253 source for FindUsagesHandler/Base, ReadWriteAccessDetector, TargetElementEvaluator, FakePsiElement, UsageTypeProviderEx and ReferencesSearch. Source review is not binary compatibility verification.

## Verification pending

Seven new `TweeFindUsagesTest` fixtures cover escaped names/duplicate passages/story isolation, macro aliases and local scope, unknown-write reads and temporary isolation, access classification/physical deduplication, handler ranges and early cancellation, live edits/pending documents, and caret entry for unknown reads/custom code hooks. They have **not** been compiled or executed.

JDK 21/Gradle and WebStorm prerequisites remain unavailable as recorded in Chapter 02. Run all native fixtures, real Find/Show/Highlight Usages actions, read/write filters, custom-call declaration navigation, indexing/cancellation transitions and live-editor refresh. Pay particular attention to target-evaluator interaction with the host's normal declaration chooser. Do not merge or release until required verification passes.

Next implementation: Chapter 12, native hierarchy browser.
