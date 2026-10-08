# Chapter 13: Tweego build service

Status: Pending

Plan milestone: 7. Dependencies: 06.

## Goal

Compile the exact selected scope safely to staged output.

## Work

Implement argv construction, executable/PATH resolution, --list-formats discovery, Harlowe-only effective selection, environment and working directory. Enumerate eligible explicit inputs including selected JS/CSS and exclusions. Save dirty inputs, reject conflicting outputs, run cancellably, promote only successful fresh staging output and preserve last good HTML.

## Acceptance

Fake process tests cover spaces/Unicode/metacharacters, unavailable compiler/format, selected inputs, failure, missing output, cancellation, cleanup, simultaneous targets and successful promotion. No shell concatenation, SugarCube fallback or --format-version.

## Execution evidence

Not yet executed.
