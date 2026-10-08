# Verification and release gates

Implementation is not a verified release. The project must not be merged or released until all required gates below have actual evidence for the same candidate commit. No automatic merge, tagging, Marketplace publication or release upload is configured.

| Gate | Evidence to retain | Status |
| --- | --- | --- |
| Wrapper regeneration | Gradle 9.3.0 regeneration and official wrapper/distribution checksum comparison | Regenerated in run 37836427267; artifact/file/official JAR checksums verified and files adopted; branch CI confirmation pending |
| Baseline/tooling tests | Characterization, generator and package-inspector logs | Passed locally; repeat in candidate CI |
| Native compilation/tests | `check` test XML and reports, including Chapters 03–15 tests | Compilation passed at `d455372`; 97 tests passed; all jobs green in run 37821613335 |
| Project configuration | `verifyPluginProjectConfiguration` output | Passed in both jobs of run 37821613335 at `d455372`; repeat for final candidate |
| Plugin build and structure | `buildPlugin`, `verifyPluginStructure`, candidate ZIP/SHA-256 | Passed in both jobs of run 37821613335 at `d455372`; repeat for final candidate |
| ZIP contents | `inspect_plugin.py` JSON report for the actual candidate ZIP | Passed for actual ZIPs in both jobs of run 37821613335 at `d455372`; repeat for final candidate |
| WebStorm verifier | `verifyPlugin -PverifierHost=WS`, target 2025.3.6 | Compatible at `d455372` (run 37821613335); nine deprecated/nine experimental API usages; repeat for final candidate |
| IDEA verifier | `verifyPlugin -PverifierHost=IDEA`, target 2025.3.6.1 with JavaScript dependency | Compatible at `d455372` (run 37821613335); nine deprecated/nine experimental API usages; repeat for final candidate |
| Installed-plugin smoke | Fresh WebStorm and IDEA test profiles, startup logs and five-feature outcomes | Pending |
| Real compiler/browser | Tweego/Harlowe versions, effective format, custom start/links/JS/CSS/assets, fail/cancel cases | Pending |
| Performance/UI | Raw native timings and cold-index/UI/cancellation traces | Fixture timings and pre-query cancellation passed at `d455372`; cold-index/UI traces pending |
| Cross-platform execution | Windows/macOS/Linux process, path, cancellation and promotion evidence | Pending |
| Any additional intended stable hosts | Explicit product versions, verifier and installed-plugin evidence | None claimed; choose and verify before expanding support |

## CI behavior

`verify.yml` runs on implementation-branch/main/master pushes, pull requests and manual dispatch. Jobs use read-only repository permissions and pinned action commits. A baseline job checks inherited behavior and tooling. Each native matrix job uses JDK 21 and Xvfb, runs all Gradle gates for one reviewed verifier host, then inspects its own generated ZIP. `--continue` gathers independent task failures without treating the job as successful. Artifact upload runs after success or failure and retains reports for 14 days; copy required evidence before expiration.

The two matrix jobs compile against the configured WebStorm SDK; the IDEA matrix entry is binary compatibility verification, not native test execution inside IDEA. Actual installation and JavaScript-enabled IDEA smoke are separate gates. No verifier failure is suppressed and no unsupported APIs are exempted by this workflow.

CI may expose compilation/API/test failures in code that has never run in this environment. Fix those failures on the implementation branch and rerun the relevant gates. Never substitute green baseline checks for native success. A ZIP retained from a failed job is not installability evidence.

## Package inspection

The inspector opens ZIP/JAR contents without extraction. It rejects duplicate classes/archive entries, bundled Kotlin stdlib/reflect or coroutines classes/JARs, inherited JS/TypeScript/Vue/Node/storyformat runtime assets, missing native classes, incorrect plugin ID/minimum/dependencies, unsafe paths and missing inherited MIT/NOTICE attribution. It records the actual ZIP's SHA-256. This supplements the platform structure/verifier tasks; it cannot prove IDE startup, source correctness or behavior.

Once gates pass, record exact commit, ZIP hash, tested host builds, OS and compiler/format versions here and in the README. Retain the open upper bound without claiming untested IDE compatibility. Merge and release still require explicit user authorization.
