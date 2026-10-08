# Chapter 06: Shared story scopes

Status: Pending

Plan milestone: 4. Dependencies: 03.

## Goal

Define persistent story selection used by all subsequent features.

## Work

Persist named roots/files and exclusions plus selected story; expose a query service and settings modification tracker. Distinguish stories reusing names. Keep machine-local compiler settings separate. Define explicit run-scope override semantics.

## Acceptance

Persistence, scope selection, exclusions, overlapping roots and settings invalidation tests pass. Other stories and output/compiler folders cannot leak into the selected scope.

## Execution evidence

Not yet executed.
