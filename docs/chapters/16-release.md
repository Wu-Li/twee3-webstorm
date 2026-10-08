# Chapter 16: Packaging, CI and supported hosts

Status: CI/package tooling and documentation implemented; release verification pending

Plan milestone: 8. Dependencies: 15.

## Implemented

- Added `.github/workflows/verify.yml`: read-only permissions, pinned action commits, branch/PR/manual triggers, inherited/tooling checks, JDK 21/Xvfb and explicit WebStorm 2025.3.6 / IDEA 2025.3.6.1 verifier matrix. Each matrix job runs `check`, `verifyPluginProjectConfiguration`, `buildPlugin`, `verifyPluginStructure` and `verifyPlugin`; reports and ZIP candidates are retained on success/failure. No merge, release or Marketplace publication step exists.
- Added optional `-PverifierHost=WS|IDEA` for CI isolation; local default still verifies both reviewed hosts. Reproducible archive order/timestamps and packaged root LICENSE/NOTICE resources are configured.
- Added a ZIP/JAR inspector that records archive hashes and fails on bundled Kotlin/coroutines, duplicate classes/entries, inherited runtime assets, unsafe paths, missing native classes/metadata/dependencies, incorrect minimum build or missing MIT attribution. Tests use synthetic archives and do not imply an actual distribution passed.
- Replaced the scaffold README with actual setup/features/limits and configured-versus-verified host status; marked the inherited VS Code guide as reference material. Added NOTICE, an unreleased changelog entry and [release gates](../verification/release-gates.md).

## Checks executed

- Inherited characterization: 7/7 passed.
- Fixture-generator tests: 3/3 passed.
- Package-inspector tests: 7/7 passed, including native fixture acceptance, runtime/legacy rejection, duplicate classes, metadata/license failures, malformed/unsafe archives and empty-distribution failure.
- Workflow YAML parse, action SHA syntax and explicit host matrix inspection: passed. This is not a GitHub Actions execution result.
- Plugin XML parse and `git diff --check`: passed.
- Action refs were resolved to commit pins using their official GitHub repositories. Reviewed the official IntelliJ Gradle plugin verifier configuration documentation. These source checks do not establish runtime compatibility.

## Verification pending

No local Gradle gate, real distribution inspection, verifier run, IDE launch, installed-ZIP smoke or compiler/browser test has passed here. JDK 21/Gradle/dependency/display prerequisites remain unavailable as recorded in Chapter 02. The existing wrapper JAR has not been regenerated. CI execution and any failures it reveals must be inspected after publication; workflow presence alone is not a passing gate.

WebStorm 2025.3.6 and IDEA 2025.3.6.1 remain **configured, unverified targets**. No current-stable or newer version is claimed as tested. Minimum build remains 253.33813.27 with no upper bound; this does not guarantee newer-host compatibility.

All implementation chapters now have their code/tooling slices. Next work is verification and fixes within the existing chapters: inspect CI, resolve actual build/API/test failures, complete Chapter 02 wrapper/build/launch evidence and Chapters 15–16 native/compiler/browser/performance/release gates. Do not mark the plan complete, merge or release until required verification passes. Do not add placeholder chapters to bypass those gates.
