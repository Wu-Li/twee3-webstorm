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

### CI follow-up: 8 October 2026

[Run 37741113409](https://github.com/Wu-Li/twee3-webstorm/actions/runs/37741113409), commit `b748fdd`, passed the baseline job. Both native jobs ran with JDK 21/Gradle 9.3.0 but stopped during Kotlin build-script compilation: the `processResources.from("LICENSE", "NOTICE")` vararg overload cannot accept the trailing configuration lambda. No native source compilation, tests, ZIP creation or verifier tasks completed. The subsequent missing-distribution inspection failures were consequences of that configuration error.

Changed the copy rule to pass one list source to the configurable `from` overload, retaining both notices under META-INF. `git diff --check` passed. Local Gradle execution remains unavailable; the corrected script requires a fresh CI result before this failure is considered verified fixed. No release gate was relaxed.

### CI follow-up: console hyperlink API

[Run 37747311541](https://github.com/Wu-Li/twee3-webstorm/actions/runs/37747311541) at `81eef11` passed baseline checks and completed `processResources` and `verifyPluginProjectConfiguration` in both native jobs. The earlier Gradle-script failure is resolved. Both jobs reached `compileKotlin` and reported one error: `FileHyperlinkInfo` cannot be constructed because it is an interface.

Changed the console filter to instantiate the public `OpenFileHyperlinkInfo` implementation with the same file and zero-based line. Reviewed its constructor in JetBrains source. `git diff --check` passed; native compilation and all downstream tests/package/verifier gates require the new CI result. No actual ZIP inspection or host compatibility pass is claimed.

### CI follow-up: stub registration and descriptor name

[Run 37753983982](https://github.com/Wu-Li/twee3-webstorm/actions/runs/37753983982) at `bb68654` confirms native source and test compilation succeeds in both matrix jobs. Both built the plugin ZIP and passed the custom package inspector. Each native test execution reported 96 tests, 80 failed; compatibility verification rejected the display name `Twee3-webstorm`. This is not a passing test suite or a verified installable plugin.

Removed `externalIdPrefix` from the Kotlin object stub holder registration. [Platform source](https://github.com/JetBrains/intellij-community/blob/idea/253.32098.37/platform/core-api/src/com/intellij/psi/stubs/StubElementTypeHolderEP.java) requires an interface for that optimization and asserts at line 65, matching the repeated CI failures. Without the attribute, the platform initializes the holder class and registers its existing serializers normally; external IDs and serialized schemas are unchanged. Changed the display name to `Twee3` to satisfy the observed descriptor rule; plugin ID and repository identity remain unchanged.

Local XML parsing and `git diff --check` passed. Existing native index/hierarchy tests and platform verification must rerun in CI. The initial hierarchy null-target failure may be independent and remains explicitly unresolved until fresh evidence arrives. No assertions or release gates were removed.

### CI follow-up: passage stub PSI contract

[Run 37760631496](https://github.com/Wu-Li/twee3-webstorm/actions/runs/37760631496) at `2df1365` passed baseline, native compilation, project configuration, build/structure and actual ZIP inspection. Both verifiers reported Compatible: WS `253.33813.27` and IU `253.33813.55`, each with nine deprecated and nine experimental API usages. These warnings remain recorded; installed-plugin and functional acceptance is still pending. Native execution in each matrix job reported 96 tests, 44 failures (previously 80).

Inspected the downloaded WebStorm artifact's JUnit XML, not only Gradle's abbreviated console failures. The repeated index assertion is now “Non-StubBasedPsiElement requests stub creation” for PASSAGE. The target branch's [StubBasedPsiElementBase source](https://github.com/JetBrains/intellij-community/blob/idea/253.32098.37/platform/core-impl/src/com/intellij/extapi/psi/StubBasedPsiElementBase.java) extends `ASTDelegatePsiElement` without implementing `StubBasedPsiElement`; [DefaultStubBuilder](https://github.com/JetBrains/intellij-community/blob/idea/253.32098.37/platform/core-impl/src/com/intellij/psi/stubs/DefaultStubBuilder.java) explicitly requires the interface. Added that interface to `TweePassage`. No stub schema, ID or version change is required; existing index tests exercise the failing path. Local `git diff --check` passed. Native execution of the correction is pending.

Other observed failures remain separate work: empty story enumeration/out-of-scope fixture files and null hierarchy/usages targets, JavaScript token restart returning BAD_CHARACTER instead of IDENTIFIER, and two structure tests expecting raw CRLF after the IDE normalizes document text. The latter requires preserving raw-lexer CRLF coverage while asserting the IDE document contract correctly. No failed test was removed or ignored.

### CI follow-up: physical story fixtures and document line endings

[Run 37767264419](https://github.com/Wu-Li/twee3-webstorm/actions/runs/37767264419) at `b31910b` removed the non-stub PSI assertion. Native compilation, baseline, build/structure, package inspection and both binary compatibility verifiers passed. The WS job reported 96 tests / 38 failures; the IDEA verifier job reported 96 / 39, with an additional highlighting lifecycle assertion in the whitespace-check test. Both execute native tests against the configured WebStorm SDK. Binary verifier warnings remain nine deprecated and nine experimental API usages per host.

Downloaded and inspected both jobs' native XML reports. Scope/query/hierarchy/tag/usages failures consistently reject or cannot enumerate story files. The [light fixture](https://github.com/JetBrains/intellij-community/blob/idea/253.32098.37/platform/testFramework/src/com/intellij/testFramework/fixtures/impl/LightTempDirTestFixtureImpl.java) creates `temp://` files, whereas story scope intentionally resolves roots through `LocalFileSystem` relative to `project.basePath`. Introduced `StoryProjectTestCase` using the platform's heavy directory-based project builder and physical temporary directory; the module source root and project base match that directory. Setup asserts the path equality and local `file` protocol. Migrated nine story-dependent suites without changing their behavioral assertions or broadening production scope to unrelated roots. Existing purely lexical/check tests stay lightweight.

Two structure failures occurred before testing their intended behavior because the fixture normalized CRLF to LF. Those tests now calculate PSI ranges against normalized document text and expect normalized text after rename. Raw CRLF remains in the structural lexer restart test and in the input supplied to the PSI tests. No test was disabled.

Local `git diff --check` passed. Heavy fixture compilation/execution and all remaining native failures require new CI. The embedded JavaScript restart BAD_CHARACTER mismatch and asynchronous PSI/model changes during highlighting remain open; the one-test difference between jobs is recorded rather than treated as a pass.

### CI follow-up: heavy fixture project path

[Run 37774291989](https://github.com/Wu-Li/twee3-webstorm/actions/runs/37774291989) at `b9a4aa4` passed baseline, compilation, build/structure, ZIP inspection and both binary compatibility verifiers, retaining the nine deprecated/nine experimental API warnings per host. Each job reported 96 tests and 47 failures. The CRLF document-expectation corrections passed. The downloaded WebStorm XML reports show 44 failures at the common story fixture's setup path assertion; these are setup failures, not new evidence about story features. The remaining three are the known JavaScript lexer restart mismatch and two highlighting/model-change assertions.

[HeavyIdeaTestFixtureImpl.generateProjectPath](https://github.com/JetBrains/intellij-community/blob/idea/253.32098.37/platform/testFramework/src/com/intellij/testFramework/fixtures/impl/HeavyIdeaTestFixtureImpl.java) resolves the sanitized project name beneath the provided directory. Corrected the fixture to pass the temporary directory's basename as that name and its parent as the supplied directory. The project, module content and fixture files should now share the same root. The setup equality assertion and local-filesystem assertion remain intact; no test was skipped or relaxed. Local `git diff --check` passed. Native verification of the corrected fixture and the resulting story behavior remains pending.
