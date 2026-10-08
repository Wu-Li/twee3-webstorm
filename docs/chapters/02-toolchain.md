# Chapter 02: WebStorm scaffold and toolchain

Status: CI build/tests and wrapper regeneration passed; installed-host launch acceptance pending

Plan milestone: 1. Dependencies: 01.

## Goal

Establish a buildable and launchable native plugin scaffold.

## Work

Pin the reviewed compatible Gradle/Kotlin pair and wrapper checksum/generated files; JVM 21/API 2.2; WebStorm 2025.3.6; JavaScript dependency; explicit verifier hosts. Replace metadata placeholders and remove sample functionality. Preserve plugin ID/group/version/notices. Update AGENTS.md.

## Acceptance

Run check and verifyPluginProjectConfiguration, package the scaffold, and actually launch the WebStorm sandbox. Record missing prerequisites as blocked, never as passing.

## Execution evidence

### 7 October 2026

- Fetched origin successfully; `origin/master` remains at `77f9522`. Preserved the existing local chapter commits on `automation/implementation-chapters`.
- Configured WebStorm 2025.3.6 and JavaScript dependency; verifier targets WebStorm 2025.3.6 and unified IntelliJ IDEA 2025.3.6.1.
- Retained Kotlin 2.3.20 and IntelliJ Platform Gradle plugin 2.19.0. Set JDK/JVM 21 and Kotlin language/API 2.2; disabled the separately bundled Kotlin stdlib.
- Pinned the distribution to Gradle 9.3.0 and its official SHA-256 (`0d585f69da091fc5b2beced877feab55a3064d43b8a1d46aeb07996b0915e0e0`). The wrapper JAR/scripts have **not** been regenerated; this remains part of the chapter.
- Removed the sample random-number tool window and its unused message bundle. Replaced placeholder metadata, retained plugin ID/name/group/version and existing notices, and declared platform/lang/JavaScript dependencies.
- Set minimum build 253.33813.27 without an upper bound; this is configured compatibility, not verified host support.
- Checks passed: `npm test --prefix tools/characterization` (7/7), XML parsing of plugin.xml, and `git diff --check`.
- Attempted `./gradlew check verifyPluginProjectConfiguration buildPlugin verifyPluginStructure --no-daemon`: exit 1 during distribution download with `java.net.SocketException: Network is unreachable`. None of these Gradle tasks executed. No plugin ZIP or successful build is claimed.
- Environment inspection found OpenJDK 17 only under `/usr/lib/jvm`, no cached Gradle distribution, no installed WebStorm under `/opt`, and no `Xvfb` command. A direct distribution connectivity check also timed out after redirecting to GitHub.
- Sandbox launch and plugin verifier were not run. A working JDK 21 environment with Gradle/dependency/IDE downloads (or preinstalled equivalents) and a display-capable WebStorm sandbox is required. The earlier remote publication restriction was subsequently resolved by the user’s explicit authorization to push this implementation branch on 7 October 2026.

### Resume steps

1. Provide the required build environment; retain this local branch and its commits.
2. Regenerate wrapper files using Gradle 9.3.0 (`./gradlew wrapper --gradle-version 9.3.0 --distribution-type bin`, twice to update the scripts/JAR), retaining the distribution checksum. Verify the wrapper JAR against the official checksum `b3a875ddc1f044746e1b1a55f645584505f4a10438c1afea9f15e92a7c42ec13`.
3. Run the chapter's Gradle gates and fix any actual configuration/build failures; inspect the resulting ZIP for duplicate runtime libraries.
4. Launch `./gradlew runIde` in a display-capable environment, record actual WebStorm startup/plugin load evidence, and only then mark Chapter 02 complete. The user subsequently authorized continuing implementation before these gates pass; they remain required before merge/release.

### Configuration references

- [Official Gradle checksums](https://gradle.org/release-checksums/)
- [IntelliJ Platform extension and verifier configuration](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html)
- [IDE product types](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-types.html)
- [Kotlin support](https://plugins.jetbrains.com/docs/intellij/using-kotlin.html)

### Green CI and wrapper follow-up: 8 October 2026

Run 37821613335 at `d455372` passed all jobs: native compilation/tests, project configuration, packaging/structure, actual ZIP inspection and both binary verifiers. The WS XML confirms 97 tests with zero failures/errors and native fixture timings; verifier API warnings remain. Historical pending statements above describe earlier execution states. Installed-host/compiler/browser and cross-platform acceptance remain pending.

Added isolated Gradle 9.3.0 wrapper regeneration to CI, running generation twice and verifying the previously pinned official JAR checksum. Generated files are retained for comparison/adoption on the next execution; no automatic repository write occurs. Workflow parse and diff checks passed locally. The new job has not executed yet and wrapper regeneration is still pending.

### Wrapper settings correction

Run 37829317698 at `e0b44e0` passed baseline and both native jobs. The isolated wrapper job failed compiling its generated settings file because the project name used single quotes in Kotlin DSL. Corrected the shell command to emit `rootProject.name = "wrapper-verification"`. Workflow parsing, exact generated settings inspection, Bash syntax and diff checks passed locally; Gradle execution is pending CI. No wrapper artifact exists for the failed run, and no regenerated files have been adopted yet.

### Generated wrapper adopted: 8 October 2026

Run 37836427267 at `f990488` passed all four jobs. Artifact 11575956899 contains wrapper scripts, properties and JAR generated twice by Gradle 9.3.0 in an isolated project. Its ZIP SHA-256 is `6c756aab18258c8208dbb2d843978931f1aeb3ea2f14ff037d55f2407a1f7f6e`; all four file checksums match its manifest. The JAR matches the official pinned checksum above. Adopted those exact bytes, preserving executable Unix mode and generated Windows line endings. Properties retain the pinned distribution checksum/version; obsolete hand-retained retry properties are absent from the generated defaults.

Added a CI byte comparison between regenerated and checked-in files. Local `sh -n gradlew`, workflow parse, wrapper-job Bash syntax, checksums and `git diff --check` passed (CRLF is treated as end-of-line for the generated batch file). Build execution with the adopted wrapper is pending next CI; installed-host startup remains pending. Earlier notes above are historical evidence.

### Adopted wrapper verified

Run 37843834883 at `acec59f` passed wrapper regeneration/byte comparison and both native build/test/package/verifier jobs. Wrapper adoption is verified; installed-host startup acceptance remains pending. No additional implementation chapter remains.
