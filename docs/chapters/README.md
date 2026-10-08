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
| [02 WebStorm scaffold and toolchain](02-toolchain.md) | 1 | CI build/tests and wrapper regeneration passed; host launch pending |
| [03 Twee structure and passage PSI](03-passage-psi.md) | 2 | Implemented; native CI passed |
| [04 Harlowe lexer and highlighting](04-highlighting.md) | 2–3 | Implemented; native CI passed, visual acceptance pending |
| [05 Inherited checks and preferences](05-checks.md) | 3 | Implemented for analyzed files; native CI passed |
| [06 Shared story scopes](06-story-scopes.md) | 4 | Implemented; native CI passed |
| [07 Passage stubs and indexes](07-indexes.md) | 4 | Implemented; native CI passed |
| [08 Passage navigation](08-navigation.md) | 4 | Implemented; native CI passed, UI acceptance pending |
| [09 Passages window and tag editing](09-tags.md) | 5 | Implemented; native CI passed, UI acceptance pending |
| [10 Static symbols and relations](10-relations.md) | 6 | Implemented; native CI passed, UI acceptance pending |
| [11 Native Find Usages](11-usages.md) | 6 | Implemented; native CI passed, UI acceptance pending |
| [12 Native hierarchy browser](12-hierarchy.md) | 6 | Implemented; native CI passed, UI acceptance pending |
| [13 Tweego build service](13-build-service.md) | 7 | Implemented; native CI passed, real process/UI acceptance pending |
| [14 Build HTML and Run integration](14-run-integration.md) | 7 | Implemented; native CI passed, compiler/browser acceptance pending |
| [15 End-to-end and performance evidence](15-end-to-end.md) | 8 | Native CI and fixture timings passed; full acceptance pending |
| [16 Packaging, CI and supported hosts](16-release.md) | 8 | CI/package tooling and docs implemented; release verification pending |

## Publication status

Chapter 01 is committed locally on `automation/implementation-chapters` in `/workspace/scratch/eae77a660784/twee3-webstorm`. Automatic approval review rejected the GitHub push on 7 October 2026: it considered remote publication unauthorized and the remote trust/privacy status unverified. The user subsequently explicitly authorized pushing the implementation branch on 7 October 2026. This supersedes the earlier publication blocker for this branch; it does not authorize merging or releasing. Continue preserving local commits and verify the remote branch after pushing.

## Deferred verification

All 16 implementation slices exist. Native compilation and all 97 tests now pass in CI on the WebStorm SDK. Both reviewed binary verifier targets pass with API warnings. Installed WebStorm/IDEA UI acceptance, actual Tweego/browser behavior, cold IDE indexing/UI profiling and cross-platform process checks remain pending. The user's report of basic local functionality is useful but does not establish these complete acceptance gates. No unimplemented chapter remains; do not invent chapters or mark the plan complete.

## Latest verification follow-up

8 October 2026: [CI run 37843834883](https://github.com/Wu-Li/twee3-webstorm/actions/runs/37843834883), implementation candidate `acec59f92bca38f831303a5ff35693fb17626b6b`, passed all four jobs: baseline, regenerated-wrapper comparison and both native/build/package/verifier jobs. The adopted Gradle 9.3.0 wrapper is confirmed by branch CI. Both jobs produced plugin ZIP SHA-256 `48bb31362aa9b1a78e5d492512a84cb6d7b11e610c73b8b7e12673303f0c8d5d`. WS 253.33813.27 and IU 253.33813.55 are binary-compatible, each retaining nine deprecated and nine experimental API warnings.

All 16 implementation slices exist and there is no outstanding observed CI failure. Recurring implementation is paused pending acceptance evidence unavailable in this workspace: installed WebStorm/IDEA five-feature smoke and startup logs, real Tweego/Harlowe browser behavior including failure/cancellation, cold-index/UI traces, and Windows/macOS/Linux process/path/output-promotion checks. Follow [the acceptance protocol](../verification/chapter15-protocol.md) and [release gates](../verification/release-gates.md), recording the exact candidate commit and ZIP hash. Resume for concrete defects or additional acceptance evidence; do not invent chapters or infer full acceptance from green CI. No merge or release. This follow-up changes documentation only; results above belong to the explicit implementation candidate.
