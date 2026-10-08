# Chapter 06: Shared story scopes

Status: Implemented — native verification pending

Plan milestone: 4. Dependencies: 03.

## Goal

Define persistent story selection used by all subsequent features.

## Work

Persist named roots/files and exclusions plus selected story; expose a query service and settings modification tracker. Distinguish stories reusing names. Keep machine-local compiler settings separate. Define explicit run-scope override semantics.

## Acceptance

Persistence, scope selection, exclusions, overlapping roots and settings invalidation tests pass. Other stories and output/compiler folders cannot leak into the selected scope.

## Execution evidence

### 7 October 2026

- Added persistent named stories with stable IDs, project-relative source directories/files, exclusions, output directories and selected-story ID. A Settings > Languages & Frameworks > Twee stories page adds/removes/edits/selects stories and exposes an explicit Harlowe 3 fallback for absent StoryData.
- Added copied settings snapshots and modification counters. Compiler path and storyformat installation directories have a separate workspace-local component; later run configuration UI will edit those values.
- Added shared GlobalSearchScope/query enumeration. Inputs are .tw/.twee/.js/.css; arbitrary assets are not compiler inputs. Deduplicate overlapping roots, apply segment-aware normalized paths, reject project escapes, prune generated/VCS/dependency/tool paths and output directories, and do not traverse symlinks. Canonical paths prevent outside-project aliases from leaking in.
- Ownership uses the most specific matching root/file across all stories. Equal-specificity ownership by different story IDs is ambiguous and excludes the file from both. Other stories' output directories remain excluded. Identical passage names in different stories can therefore be keyed by stable story ID.
- Explicit run override semantics: null follows the selected story; an existing story ID selects that exact scope without changing project settings; an unknown/deleted ID fails closed with no scope. Compiler integration will consume this API later.
- Moved project StoryData notifications out of the per-file annotator. A startup/project service enumerates the selected scope in cancellable, coalesced background read actions with committed documents. It caches membership and per-file StoryData extraction, reparsing changed files only; document/VFS/settings events refresh results, including closed files and unsaved committed edits. Older refresh results cannot replace newer ones.
- The selected scope must have exactly one StoryData passage to provide project metadata. Multiple candidates are marked ambiguous without adding a new editor error/field validator or picking an arbitrary story. Old notifications expire on selection changes, removal, ambiguity and repair.
- Harlowe 3 is selected only by supported StoryData or the explicit fallback when metadata is absent. Physical files outside the selected supported story retain generic highlighting; editor highlighters refresh when the selected story or effective format changes. Scratch/demo files retain Harlowe editing. Parser PSI remains tolerant and no new body diagnostics are introduced.
- Added eight native test methods for ownership/ambiguity, exclusions/path escapes, persistence/copying/counters, run overrides/snapshot invalidation, enumerated inputs, closed/unsaved StoryData, fallback/ambiguity, and generic body highlighting. Updated Chapter 05 notification lifecycle tests to use the separated notification service.
- Executed: seven inherited characterization tests pass; plugin XML and registered-class checks pass; `git diff --check` passes. These checks do not execute the new Kotlin implementation.
- Not executed: Kotlin compilation, native tests, settings UI, asynchronous IDE integration or performance measurement. All remain pending under the user's explicit authorization to continue implementation. Run `./gradlew check` and the deferred Chapter 02 gates when the toolchain/host environment is available.

## Manual verification when WebStorm is available

Create two source directories with repeated passage names and separate StoryData, configure both stories, then switch selection. Confirm generic versus Harlowe colors, source exclusions, notification replacement and unsaved metadata refresh. Confirm a nested second story and both output/tool directories are excluded from the first. Check the UI after removing the selected story and after creating duplicate StoryData.

Before configuring a story, physical source files use generic body colors. Select source paths and either supply Harlowe 3 StoryData or explicitly enable the fallback; no story format is silently inferred from a filename.
