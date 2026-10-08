# Chapter 04: Harlowe lexer and highlighting

Status: Implemented — native and visual verification pending

Plan milestone: 2–3. Dependencies: 01, 03.

## Goal

Add restart-safe Harlowe lexical structure and theme colors.

## Work

Implement nested macros, strings, hooks, collapsed blocks, comments and embedded-region states; body PSI/scopes and semantic header colors. Add ColorSettingsPage and lexer-based JS/CSS/JSON layers without injected inspections. Record reviewed parity differences.

## Acceptance

Incremental restart/edit tests and baseline category/range comparisons pass. Inspect light/dark themes; ordinary prose remains prose. Verify embedded colors without extra diagnostics.

## Execution evidence — 7 October 2026

- Added a shared Harlowe lexer for parsing and highlighting: nested macro/parenthesized expression/hook/collapsed frames, strings, comments, links, named hooks, variable categories, properties, operators/type words, numeric/boolean values, HTML attributes and entities. Ordinary prose parentheses and apostrophes remain prose.
- Added body PSI nodes for macros, expressions, hooks, collapsed blocks and links. These establish lexical containment for later binding work; they do not yet resolve semantic temporary-variable bindings or references.
- Added interned immutable lexical states without source offsets, Lexer position restoration, and RestartableLexer support so the editor can restart within Harlowe contexts. Embedded host states are conservatively advertised as independently restartable only at state zero. Host lexer instances are reused to retain their own state registries.
- Added lexer-only JS/CSS/JSON delegation for special passages and inline script/style regions. No injected PSI, inspections, unresolved-name diagnostics or Harlowe validators are registered. The parser retains embedded content as opaque tokens.
- Added standard theme fallback colors, a Twee / Harlowe 3 Color Settings page, and silent semantic colors for StoryTitle/StoryData/Start and special tags.
- Added eight native test methods covering token-boundary restarts/restoration, edits versus fresh scans, actual editor incremental highlighting, 45 inherited category/range spans, tolerant PSI nesting, prose/recovery, host embedded colors, HTML/hook categories, and absence of added diagnostics/injections.
- Executed: all seven inherited characterization tests pass; plugin XML parses and registered implementation classes exist; `git diff --check` passes. The baseline tests still exercise inherited code, not the new Kotlin implementation.
- Not executed: Kotlin compilation, native test methods, light/dark theme inspection, WebStorm launch or packaging. The existing build environment limitation remains; the user authorized continuing implementation while these checks are deferred. Run `./gradlew check` (including HarloweHighlightingTest and TweeStructureTest), then the Chapter 02 build/host gates when possible.

## Deliberate parity differences requiring native/visual review

1. Shared semantic header recognition accepts tabs/no space after `::`; malformed headers remain text. The inherited TextMate boundary only recognizes a literal space. This aligns syntax, PSI and future navigation.
2. Every valid passage header resets unfinished strings, comments, hooks and embedded regions. The inherited grammar may retain an unterminated string across a later passage; tolerant editing requires recovery.
3. StoryData and script/stylesheet tags activate real host lexers, including multi-tag headers. Frozen snapshots lack external grammars and show inconsistent activation even for single tags; they cannot establish embedded color parity.
4. Complete HTML entities in prose receive an entity color. The inherited entity rule is primarily reachable inside HTML attribute strings. Unknown ampersands remain ordinary text; no illegal-token diagnostic/color is added.
5. Preamble is neutral text rather than the inherited blanket comment color. Header names/tags/metadata have native semantic colors instead of body grammar scopes on the header.
6. Token boundaries may be coalesced (notably string/comment text); the 45-span baseline comparison checks every covered character's category, not exact token splitting. Macro opener/name digit inconsistencies and the immediate-backslash string-ending rule are deliberately retained.
7. Macro punctuation and link arrows have explicit native tokens. The frozen `|` link text remains link text, as inherited. Unclosed `=...` hook/collapsed forms retain lexical scopes without warnings.

The TSV baseline spans derive from `tools/characterization/fixtures/edge-cases.twee-scopes.json`, body lines 2–9, without modifying frozen snapshots. Full parity beyond these representative spans and theme appearance remain unverified.

## References

- [IntelliJ lexer requirements](https://plugins.jetbrains.com/docs/intellij/implementing-lexer.html)
- [Highlighter and color settings registration](https://plugins.jetbrains.com/docs/intellij/syntax-highlighter-and-color-settings-page.html)
- [Editor incremental restart behavior](https://github.com/JetBrains/intellij-community/blob/master/platform/editor-ui-ex/src/com/intellij/openapi/editor/ex/util/LexerEditorHighlighter.java)
