# Chapter 01: Baseline fixtures

Status: Complete

Plan milestone: 1. Dependencies: none.

## Goal

Freeze the inherited grammar, semantic-header and diagnostic behavior before changing native code.

## Work

Run inherited TypeScript through a minimal VS Code adapter; capture token scopes/ranges and header ranges for Harlowe.tw, Twee.tw, ArrowsTest.tw and focused edge cases. Assert generic diagnostics, switches, JSON acceptance and project notifications. Record hashes and parity limitations.

## Acceptance

A clean npm ci and npm test under tools/characterization pass. Reference sources remain unchanged. Snapshot output is reviewed; missing host grammar coverage is stated.

## Execution evidence

7 October 2026: Added isolated, locked development-only characterization harness using the actual inherited TypeScript and TextMate/Oniguruma grammar. Four fixtures have reviewed semantic/token-range snapshots; source SHA-256 hashes freeze the baseline. Seven tests cover diagnostics, settings, JSON acceptance and UUID notifications. See [harness README](../../tools/characterization/README.md) for exact commands and coverage limits. No native build or IDE launch is claimed; chapter 02 owns these gates.

Publication: local implementation commit `cffec95`; remote push rejected by automatic approval review. See the tracker publication status.
