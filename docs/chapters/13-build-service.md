# Chapter 13: Tweego build service

Status: Implemented; native/process/UI verification pending

Plan milestone: 7. Dependencies: 06.

## Implemented

- `TweegoBuildService` exposes a cancellable background entry point and a blocking background-only adapter for Chapter 14. It snapshots the selected/overridden named story, saves its dirty input documents on the EDT, commits PSI, and checks the same scope's StoryData. Build failures are returned and streamed to the caller, without adding inspections or browser side effects.
- Working directory defaults to the first configured story root (its parent for an explicit file); a project-relative override supports multi-root stories. Output defaults to `dist/index.html` under that directory. Local compiler path takes precedence over PATH discovery. Environment combines inherited values, configured format directories and final explicit overrides.
- Discovery uses `--list-formats`; Tweego 2.x writes this listing to stderr and exits 1. A recognized listing with Harlowe 3 non-proofing entries is required. Automatic selection chooses the highest listed stable Harlowe 3 minor/patch; an explicit installed Harlowe 3 ID selects exactly that ID. Compilation always passes `--format`, never a SugarCube fallback or nonexistent `--format-version` option. The effective ID/version is streamed to the console callback.
- Shared story enumeration includes only eligible explicit `.tw`, `.twee`, `.js` and `.css` inputs. Additional per-build exclusions include resolved PATH compiler installation and implicit/explicit format directories, alongside existing generated-output, user-exclusion and other-story rules. Assets are neither crawled nor copied.
- `GeneralCommandLine` passes argv directly, with UTF-8, a fixed environment and working directory. `CapturingProcessHandler` runs off the EDT and streams stdout/stderr plus exit codes. Cancellation terminates the process before staging cleanup.
- A process-wide reservation rejects overlapping builds for the same normalized output path. Each compile writes a unique initially empty adjacent staging HTML file. Only exit zero plus nonempty regular fresh output allows atomic replacement and VFS refresh. Failure/cancellation cleans staging and preserves the previous output. Filesystems without atomic replacement fail closed. Symlink/directory outputs and input conflicts are rejected.

## Checks executed

- `npm test --prefix tools/characterization`: 7/7 inherited baseline checks pass. These do not exercise the new Kotlin service.
- `git diff --check`: passed.
- Plugin XML parse and registered native class source presence: passed.
- Reviewed [Tweego usage.go](https://github.com/tmedwards/tweego/blob/master/usage.go), [config.go](https://github.com/tmedwards/tweego/blob/master/config.go) and [formats.go](https://github.com/tmedwards/tweego/blob/master/formats.go) for listing layout/exit status, format search paths and CLI options. Reviewed branch-253 IntelliJ `CapturingProcessHandler` for cancellable execution. Source review is not runtime verification.

## Verification pending

Twelve new fake-runner/filesystem tests cover argv spaces/Unicode/metacharacters, explicit JS/CSS inputs, format discovery/selection, missing compiler/format, environment precedence, PATH resolution, unsuccessful or missing/empty output, cancellation before discovery and after compilation, staging cleanup/retry, conflicting and independent simultaneous targets, successful promotion and invalid destinations/inputs. A new native scope fixture checks additional installation exclusions and selected JS/CSS. These tests have **not been compiled or executed**.

JDK 21, Gradle/native tests and WebStorm sandbox verification remain unavailable as recorded in Chapter 02. Verify dirty-document saves, actual process start/termination, Windows/macOS/Linux argv and filesystem behavior, real Tweego/Harlowe output with custom start and relative assets, and UI disposal/cancellation. The current format-list parser accepts stable three-component Harlowe 3 versions; prerelease listing compatibility is not claimed. Required checks must pass before merge/release.

Next implementation: Chapter 14, Build HTML and Run integration.
