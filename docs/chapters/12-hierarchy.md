# Chapter 12: Native hierarchy browser

Status: Implemented; native/UI verification pending

Plan milestone: 6. Dependencies: 10, 11.

## Implemented

- Registered public `HierarchyProvider` through `callHierarchyProvider` and implemented `HierarchyBrowserBaseEx` with the host's Hierarchy tool window, lazy background tree structures, normal source navigation, refresh and occurrence navigation. No internal BrowseCallHierarchyAction is invoked.
- Caret roots cover passage headers, literal passage destinations, named macros, variable reads/writes and custom calls. Duplicate passage destinations are exposed as separate candidate rows with file and line locations. Missing/static and computed/dynamic targets have explicit terminal rows.
- Four toolbar views expose Incoming calls/links, Outgoing calls/links, Reads and Writes. Custom-call caret roots start in Incoming; ordinary variable occurrences start in Reads. All modes remain available on the same root. Reads/Writes are labelled as variable access, not function calls.
- Grouped relation rows retain occurrence counts and expandable physical source-site rows. Incoming links show source passage/custom-body owners. Outgoing custom variables show candidate code hooks; their descendants come from the isolated callable body. Engine macro nodes have no invented source body. All custom binding results remain candidates, even when only one is known.
- Smart-pointer targets, ancestor keys and cycle markers bound recursion while retaining source navigation. Indexed query states remain visible. Document/VFS, story settings, StoryData context and indexing transitions debounce refresh; listeners are tied to browser disposal. Added a story-context publication topic so views refresh after asynchronous format selection completes.

## Checks executed

- `npm test --prefix tools/characterization`: 7/7 inherited baseline checks pass. These do not execute the Kotlin hierarchy implementation.
- `git diff --check`: passed.
- Plugin XML parse and registered native class source presence: passed.
- Reviewed primary IntelliJ branch-253 source for HierarchyProvider, HierarchyBrowserBaseEx, HierarchyBrowserBase, HierarchyTreeStructure and HierarchyNodeDescriptor, including lazy construction, refresh and built-in double-click/Enter navigation. This is source review, not binary compatibility evidence.

## Verification pending

Six new `TweeHierarchyTest` fixtures cover grouped incoming/outgoing links/story isolation, cycle termination with source sites, duplicate/dynamic destinations, custom body candidates and engine terminals, variable Reads/Writes/unknown bindings, and unsaved edits/scope changes. They have **not** been compiled or executed.

JDK 21/Gradle and WebStorm prerequisites remain unavailable as recorded in Chapter 02. Verify the actual Call Hierarchy action, caret routing, duplicate candidate selection, toolbar modes, source navigation, recursive trees, deletion/rename refresh, cancellation, indexing transitions and disposal in the IDE. Deep/large graph responsiveness remains part of Chapter 15. No runtime execution ordering, evaluated aliases or definitive custom binding is claimed. Do not merge/release until required verification passes.

Next implementation: Chapter 13, Tweego build service.
