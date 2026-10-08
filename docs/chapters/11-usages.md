# Chapter 11: Native Find Usages

Status: Pending

Plan milestone: 6. Dependencies: 10.

## Goal

Expose passage/macro/variable symbols through native usages tools.

## Work

Add FindUsagesProvider, word scanner, descriptions, UsageTypeProvider, ReadWriteAccessDetector, logical-symbol handler factory and bounded ReferencesSearch executor for escaped/multiword names and aliases. Use smart pointers and live invalidation.

## Acceptance

Find Usages returns all physical call/read/write sites within story and binding scopes, including aliases and unusual passage names; background searches cancel and respect indexing state.

## Execution evidence

Not yet executed.
