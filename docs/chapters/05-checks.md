# Chapter 05: Inherited checks and preferences

Status: Implemented for analyzed files — native verification pending

Plan milestone: 3. Dependencies: 01, 04.

## Goal

Port only generic Twee/StoryData diagnostics and validation notifications.

## Work

Add warning/check switches with inherited defaults, malformed JSON editor checks and deduplicated IFID/format notifications. Preserve UUID acceptance and raw malformed-header fallback. Do not add Harlowe validators.

## Acceptance

Positive and disabled-switch tests match the baseline. Unknown macros, variables, missing links and bad arguments produce no new warning/error. Verify notification refresh and deduplication.

## Execution evidence

### 7 October 2026

- Added persistent project settings for the four inherited switches: header whitespace, IFID, format and format-version; all default to true. Settings UI applies changes and restarts analysis.
- Added warning only for recognized passage headers missing whitespace after ::, preserving the first-three-character range and CSS pseudo-element guidance. Malformed ordinary headers retain raw fallback.
- Added StoryData JSON syntax errors on body ranges, with the name as a visible anchor for empty bodies. Valid scalars/arrays/null are not schema errors. Syntax checking stays enabled regardless of notification switches, matching inherited behavior.
- Ported IFID acceptance exactly from the pinned uuid 10 regex, including versions 1–8, variant bits, case-insensitivity, nil and maximum UUIDs. Presence checks preserve JavaScript truthiness rather than inventing format/type validation.
- Project notifications are deduplicated per analyzed file and message set. Newer repair results supersede older queued results; settings refresh invalidates queued work; repair/removal expires prior notices; deleted files expire their entries. Notifications are posted on EDT and do not add field errors in the editor.
- Until Chapter 06 supplies selected story scopes, notification evaluation uses the first StoryData passage in each analyzed file. Closed/unanalysed project files are not scanned. Chapter 06 must bind selection/refresh to the selected story instead of treating per-file candidates as final project configuration. No Harlowe format selection/override is applied yet; that also needs the shared story context.
- Replaced the recursive header JSON recognizer with a strict iterative recognizer shared by StoryData. This removes the previous nesting-depth rejection and rejects non-ASCII digits in Unicode escapes.
- Added nine native test methods: warning/switch behavior, JSON syntax versus field checks, UUID parity, JS truthiness, negative Harlowe diagnostics, settings serialization, notification dedup/settings/removal, stale result rejection, and deeply nested JSON.
- Executed all seven inherited characterization tests: pass. XML parsing/extension class checks and `git diff --check`: pass. These do not prove Kotlin behavior.
- Not executed: native tests, compilation, IDE settings/notification UI. These remain deferred under the user's authorization. Run `./gradlew check` and inspect Settings > Languages & Frameworks > Twee checks in WebStorm when available.

The eight Chapter 04 test methods and prior Chapter 03 tests remain pending as well. No new macro, argument, variable, link or general Harlowe validator was added.
