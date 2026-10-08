# Chapter 03: Twee structure and passage PSI

Status: Pending

Plan milestone: 2. Dependencies: 02.

## Goal

Register .tw/.twee and build tolerant structural PSI.

## Work

Implement Language/FileType/ParserDefinition, passage declarations with PsiNameIdentifierOwner, raw/decoded names, exact header/tag/metadata/body ranges. Reject malformed headers as raw text without parser errors; preserve CRLF, escaping and recovery. Do not implement references yet.

## Acceptance

Native fixture tests cover multiple passages, empty/incomplete input, malformed metadata/tags, escaped names, Unicode, CRLF, no final newline and later-passage recovery.

## Execution evidence

Not yet executed.
