# Chapter 14: Build HTML and Run integration

Status: Implemented; native/compiler/browser verification pending

Plan milestone: 7. Dependencies: 13.

## Implemented

- Registered Tweego configuration type/factory with persisted `RunConfigurationOptions` and a settings editor for selected/fixed story, working directory, output, installed format ID, environment overrides, browser and launch preference. Missing story/browser IDs remain visible instead of silently selecting another entry. Compiler paths remain in local story settings.
- A `CommandLineState` uses the IDE's standard Run runner and console. Its process handler starts the shared Chapter 13 service only after console attachment, streams output and exit codes, and maps Stop/disposal to cancellation. No custom ProgramRunner was introduced. The standard IDE runner may save other open documents as part of its normal execution workflow; the service additionally verifies saves for selected inputs.
- Registered Tools | Build Twee HTML. It clones the selected Tweego configuration with browser launch disabled, preserving the saved configuration; otherwise it uses the selected story and defaults. Both paths compile through the same service.
- Browser launch uses the IDE BrowserLauncher and occurs only for a successful promoted result, an enabled launch preference and a noncanceled/nonclosed project. Explicit unavailable browsers report errors instead of silently changing browser. Build-only and failed/canceled results never launch existing output.
- Console links recognize Tweego's `load <absolute file>: line N:` diagnostics only when the file exists inside the execution story. Unknown output remains unchanged.
- Added [setup/acceptance instructions](../tweego-run.md) and a multi-file Harlowe fixture with custom start, included JS/CSS and an output-relative SVG asset. Assets are kept beside output, without an automatic copy/bundling subsystem.

## Checks executed

- `npm test --prefix tools/characterization`: 7/7 inherited baseline checks pass; these do not exercise new Kotlin code.
- `git diff --check`: passed.
- Plugin XML parse and registered native class source presence: passed.
- Fixture structure check: StoryData JSON/custom start, cross-file destination, JS/CSS presence and SVG XML parse passed; this does not execute the fixture.
- Reviewed branch-253 IntelliJ source for options serialization, CommandLineState console attachment, ProcessHandler lifecycle, the standard Run runner and BrowserLauncher API; reviewed Tweego load diagnostic source. This does not establish binary compatibility.

## Verification pending

Five new native fixtures cover persistence/clone isolation, defaults/environment validation, story/path validation, browser launch eligibility and reliable diagnostic ranges. They have **not been compiled or executed**. The real compiler/browser fixture and its manual acceptance checklist have **not been run**.

JDK 21/Gradle, native tests and WebStorm sandbox verification remain unavailable as recorded in Chapter 02. Verify actual settings UI, Run/Build-only console attachment, Stop and project disposal, browser selection, missing browser handling, hyperlink navigation, dirty saves, real Harlowe start/link/JS/CSS/asset behavior and cross-platform execution. No merge/release until required checks pass.

Next implementation: Chapter 15, end-to-end and performance evidence.
