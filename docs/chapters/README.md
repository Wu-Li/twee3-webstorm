# Implementation chapters

The authoritative scope and design remain in [implementation-plan.md](../implementation-plan.md). These chapters split its eight milestones into reviewable execution units; they retain release acceptance criteria. On 7 October 2026 the user explicitly authorized continued implementation while local build and sandbox verification are deferred.

## Execution protocol

- Continue on `automation/implementation-chapters`; fetch and inspect remote/local changes before editing. Never overwrite unrelated work.
- Each execution works on the next unimplemented chapter whose required code dependencies exist; verification-pending chapters do not block implementation. Finish its code, meaningful tests and progress record; do not silently mark unavailable verification as passed.
- Record unavailable checks as verification pending and continue implementing the next chapter. Build, native tests, host launch and release checks remain mandatory before merging or releasing; never mark them passed without execution.
- Commit each completed slice locally; the user explicitly authorized pushing this implementation branch on 7 October 2026. Do not merge or publish a release automatically. Update this table and its chapter evidence in the same commit.
- After chapter 16 and all release gates pass, stop the recurring task. Until then, retain the next chapter and limitations here.

## Progress

| Chapter | Plan milestone | State |
| --- | --- | --- |
| [01 Baseline fixtures](01-baseline.md) | 1 | Complete |
| [02 WebStorm scaffold and toolchain](02-toolchain.md) | 1 | Implemented configuration; verification and wrapper regeneration pending |
| [03 Twee structure and passage PSI](03-passage-psi.md) | 2 | Implemented; native verification pending |
| [04 Harlowe lexer and highlighting](04-highlighting.md) | 2–3 | Implemented; native/visual verification pending |
| [05 Inherited checks and preferences](05-checks.md) | 3 | Implemented for analyzed files; native verification pending |
| [06 Shared story scopes](06-story-scopes.md) | 4 | Implemented; native verification pending |
| [07 Passage stubs and indexes](07-indexes.md) | 4 | Implemented; native verification pending |
| [08 Passage navigation](08-navigation.md) | 4 | Implemented; native/UI verification pending |
| [09 Passages window and tag editing](09-tags.md) | 5 | Implemented; native/UI verification pending |
| [10 Static symbols and relations](10-relations.md) | 6 | Next |
| [11 Native Find Usages](11-usages.md) | 6 | Pending |
| [12 Native hierarchy browser](12-hierarchy.md) | 6 | Pending |
| [13 Tweego build service](13-build-service.md) | 7 | Pending |
| [14 Build HTML and Run integration](14-run-integration.md) | 7 | Pending |
| [15 End-to-end and performance evidence](15-end-to-end.md) | 8 | Pending |
| [16 Packaging, CI and supported hosts](16-release.md) | 8 | Pending |

## Publication status

Chapter 01 is committed locally on `automation/implementation-chapters` in `/workspace/scratch/eae77a660784/twee3-webstorm`. Automatic approval review rejected the GitHub push on 7 October 2026: it considered remote publication unauthorized and the remote trust/privacy status unverified. The user subsequently explicitly authorized pushing the implementation branch on 7 October 2026. This supersedes the earlier publication blocker for this branch; it does not authorize merging or releasing. Continue preserving local commits and verify the remote branch after pushing.

## Deferred verification

The user authorized continued implementation on 7 October 2026 despite missing local verification prerequisites. Chapter 02 still needs wrapper regeneration, Gradle checks and a real WebStorm launch. Chapter 03 has structural language implementation and native tests; those tests cannot yet run here. Chapter 04 adds Harlowe highlighting and tests, with native/visual verification pending. Chapter 05 adds inherited checks/settings and per-file notification lifecycle, with native verification pending. Chapter 06 adds shared scopes, project StoryData selection and background notification refresh; native/UI verification is pending. Chapter 07 adds passage stubs/indexes and live scoped queries, with native verification pending. Chapter 08 adds soft polyvariant passage navigation and shared literal/dynamic target extraction, with native/UI verification pending. Chapter 09 adds the Passages window and undoable batch tag editing; native/UI verification is pending. Continue with Chapter 10 while preserving this verification backlog. Do not merge or release until the deferred checks pass.
