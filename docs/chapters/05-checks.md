# Chapter 05: Inherited checks and preferences

Status: Pending

Plan milestone: 3. Dependencies: 01, 04.

## Goal

Port only generic Twee/StoryData diagnostics and validation notifications.

## Work

Add warning/check switches with inherited defaults, malformed JSON editor checks and deduplicated IFID/format notifications. Preserve UUID acceptance and raw malformed-header fallback. Do not add Harlowe validators.

## Acceptance

Positive and disabled-switch tests match the baseline. Unknown macros, variables, missing links and bad arguments produce no new warning/error. Verify notification refresh and deduplication.

## Execution evidence

Not yet executed.
