# Chapter 10: Static symbols and relations

Status: Pending

Plan milestone: 6. Dependencies: 04, 06, 07, 08.

## Goal

Extract macro/variable occurrences, bindings and static edges.

## Work

Index only file-derived occurrences. Normalize named macros but retain custom-variable identity. Model hook inheritance, macro parameter scopes, loop/lambda shadowing, assignment destinations and property mutation. Separate custom macro bodies from enclosing execution; retain every physical occurrence and ambiguous/dynamic candidates.

## Acceptance

Tests cover aliases, set/put/move/bind, reads/writes, custom macro calls, scope shadowing, no-known-write reads, multiple bindings and unresolved computed destinations. Verify rules against primary Harlowe sources before implementation.

## Execution evidence

Not yet executed.
