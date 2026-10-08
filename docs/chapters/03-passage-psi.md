# Chapter 03: Twee structure and passage PSI

Status: Implemented — native verification pending

Plan milestone: 2. Dependencies: 02.

## Goal

Register .tw/.twee and build tolerant structural PSI.

## Work

Implement Language/FileType/ParserDefinition, passage declarations with PsiNameIdentifierOwner, raw/decoded names, exact header/tag/metadata/body ranges. Reject malformed headers as raw text without parser errors; preserve CRLF, escaping and recovery. Do not implement references yet.

## Acceptance

Native fixture tests cover multiple passages, empty/incomplete input, malformed metadata/tags, escaped names, Unicode, CRLF, no final newline and later-passage recovery.

## Execution evidence

### 7 October 2026

- Registered .tw/.twee as native Twee files with a tolerant parser and passage PSI implementing PsiNameIdentifierOwner.
- Added line-local structural lexer with restart reconstruction, shared header recognition adapted from the inherited parser, exact name/tag/metadata/header/body ranges, decoded names, and escaped name replacement that preserves surrounding content.
- Malformed headers remain raw body/preamble text without parser error nodes; later valid passages recover independently. Header JSON recognition is strict and emits no diagnostics. A defensive 256-level JSON nesting limit is an explicit parity difference for pathological input; review it before release.
- Added seven native test methods for both extensions, empty/incomplete files, Unicode and escaped names, CRLF/no final newline, JSON/tag rejection, recovery, rename preservation, live edits and all-token-boundary lexer restarts.
- Executed inherited characterization tests: 7/7 pass. `git diff --check` and plugin.xml XML parsing pass.
- Native tests and compilation have NOT run: the known environment lacks the required Gradle/dependency downloads and JDK 21. User explicitly authorized continuing implementation with these checks deferred. Run `./gradlew check verifyPluginProjectConfiguration buildPlugin` and the native tests when that environment is available.
- No passage references or Harlowe body syntax/highlighting are implemented in this chapter; Chapter 04 is next.
