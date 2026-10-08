# Chapter 07: Passage stubs and indexes

Status: Pending

Plan milestone: 4. Dependencies: 03, 06.

## Goal

Index file-derived passage declarations and tags.

## Work

Implement versioned stubs/indexes storing only file facts. Apply story scope at query time. Use cancellable background reads, candidate files and live committed PSI validation; handle dumb mode and unsaved documents.

## Acceptance

Tests cover duplicates, edits, moves, deletion, escaped names and scope changes. No nested index access or full-project reparsing on a small edit.

## Execution evidence

Not yet executed.
