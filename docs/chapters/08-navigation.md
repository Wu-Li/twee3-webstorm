# Chapter 08: Passage navigation

Status: Implemented; native/UI verification pending

Plan milestone: 4. Dependencies: 04, 07.

## Implemented

- Link/macro PSI exposes a shared soft polyvariant `PassageReference`. Resolution uses live scoped passage queries, preserves duplicate physical targets for the IDE chooser, and lands on each passage's name/header in its owning file. Missing, indexing, uncommitted and out-of-story targets resolve silently to no candidates.
- Shared `PassageTargets` records relation kind, precise source range and either a decoded case-sensitive name or a dynamic target. It recognizes all four bracket forms, preserving inherited separator precedence and trimmed target lookup while keeping the clickable target text range.
- Macro rules cover `display`, `go-to`, `redirect`, `link-goto` and `link-reveal-goto`, including case/hyphen/underscore aliases. The first three use argument one; link macros use argument two when present and argument one for the single-argument form. Only an entire quoted literal is static; nested calls, variables, concatenation and other expressions remain dynamic. Optional changer expressions are not evaluated to infer omitted passage arguments.
- Immediate PSI children delimit macro arguments, so nested commas and quoted markup do not become unrelated passage references. Comments, strings, HTML attributes, script/stylesheet bodies and embedded script/style regions do not expose link PSI references. Physical source resolution also checks selected story membership and Harlowe context.
- Link lexing now preserves escaped bracket names and ignores escaped link openers. No inspections, runtime evaluation or refactoring UI were added.

## Checks executed

- `npm test --prefix tools/characterization`: 7/7 inherited baseline tests pass. These execute inherited TypeScript/TextMate behavior, not the new Kotlin implementation.
- `git diff --check`: passed.
- Plugin descriptor XML parse and registered native class source presence: passed.
- Source review against [IntelliJ PSI references](https://plugins.jetbrains.com/docs/intellij/psi-references.html) and [Harlowe 3 manual](https://twine2.neocities.org/#macro_link-reveal-goto), including optional passage arguments and normalized macro names.

## Verification pending

Six new `PassageNavigationTest` fixture cases cover bracket ranges/escaping/Unicode/CRLF, literal macro positions and aliases, dynamic targets, excluded contexts/no added errors, duplicates and physical header destinations/story isolation, and committed unsaved edits/file rename/move/deletion. They have **not** been compiled or executed. Gradle/JDK 21 and WebStorm sandbox prerequisites remain unavailable here as recorded in Chapter 02.

Run native fixtures, then verify Ctrl/Cmd-click, Go to Declaration, duplicate chooser, indexing transitions and Harlowe context refresh in WebStorm. Check incremental lexing for the escaped-bracket change. Completion of this implementation chapter does not establish these acceptance checks passed and does not authorize merge/release.

Next implementation: Chapter 09, Passages window and tag editing.
