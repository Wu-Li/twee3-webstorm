# Implementation chapters

The authoritative scope and design remain in [implementation-plan.md](../implementation-plan.md). These chapters split its eight milestones into reviewable execution units; they do not relax any acceptance criteria.

## Execution protocol

- Continue on `automation/implementation-chapters`; fetch and inspect remote/local changes before editing. Never overwrite unrelated work.
- Each execution works on the first unfinished chapter whose dependencies are complete. Finish its code, meaningful tests and progress record; do not silently mark unavailable verification as passed.
- If a chapter cannot finish, record the exact blocker and resume it on the next execution. Do not skip host verification merely to advance the table.
- Commit each completed slice locally; the user explicitly authorized pushing this implementation branch on 7 October 2026. Do not merge or publish a release automatically. Update this table and its chapter evidence in the same commit.
- After chapter 16 and all release gates pass, stop the recurring task. Until then, retain the next chapter and limitations here.

## Progress

| Chapter | Plan milestone | State |
| --- | --- | --- |
| [01 Baseline fixtures](01-baseline.md) | 1 | Complete |
| [02 WebStorm scaffold and toolchain](02-toolchain.md) | 1 | Blocked: build environment / sandbox |
| [03 Twee structure and passage PSI](03-passage-psi.md) | 2 | Pending |
| [04 Harlowe lexer and highlighting](04-highlighting.md) | 2–3 | Pending |
| [05 Inherited checks and preferences](05-checks.md) | 3 | Pending |
| [06 Shared story scopes](06-story-scopes.md) | 4 | Pending |
| [07 Passage stubs and indexes](07-indexes.md) | 4 | Pending |
| [08 Passage navigation](08-navigation.md) | 4 | Pending |
| [09 Passages window and tag editing](09-tags.md) | 5 | Pending |
| [10 Static symbols and relations](10-relations.md) | 6 | Pending |
| [11 Native Find Usages](11-usages.md) | 6 | Pending |
| [12 Native hierarchy browser](12-hierarchy.md) | 6 | Pending |
| [13 Tweego build service](13-build-service.md) | 7 | Pending |
| [14 Build HTML and Run integration](14-run-integration.md) | 7 | Pending |
| [15 End-to-end and performance evidence](15-end-to-end.md) | 8 | Pending |
| [16 Packaging, CI and supported hosts](16-release.md) | 8 | Pending |

## Publication status

Chapter 01 is committed locally on `automation/implementation-chapters` in `/workspace/scratch/eae77a660784/twee3-webstorm`. Automatic approval review rejected the GitHub push on 7 October 2026: it considered remote publication unauthorized and the remote trust/privacy status unverified. The user subsequently explicitly authorized pushing the implementation branch on 7 October 2026. This supersedes the earlier publication blocker for this branch; it does not authorize merging or releasing. Continue preserving local commits and verify the remote branch after pushing.

## Current execution blocker

Chapter 02 configuration is implemented locally, but its Gradle tasks cannot start because the distribution download fails with `Network is unreachable`. JDK 21 and a display-capable WebStorm sandbox are also unavailable in the inspected environment. Pause recurring execution until these prerequisites are provided; resume Chapter 02 verification before advancing. See its execution evidence for exact checks and recovery steps.
