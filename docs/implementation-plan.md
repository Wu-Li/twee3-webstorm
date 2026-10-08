# Twee 3 WebStorm Implementation Plan

Native IntelliJ language support for Harlowe 3

Repository Wu-Li/twee3-webstorm | Reviewed 7 October 2026 | Commit 8c7715183b3387292406295523ab54f6a058c062

## Recommendation and scope

Build a native Kotlin plugin around a tolerant Twee and Harlowe lexer, IntelliJ PSI syntax nodes, and shared symbol indexes. Reuse the fork's grammar, passage conventions, diagnostic rules, tagging behavior, and fixtures as specifications. The TypeScript extension depends on VS Code APIs and cannot run directly inside WebStorm.

Deliver the five requested features: existing Harlowe 3 highlighting and error checks, clickable passage links, source tracing for passages and macros and variables, passage tagging, and HTML build and run. Treat tracing as static navigation through IntelliJ's Hierarchy and Find Usages tools. Implement a Tweego run configuration that compiles the selected story and opens successful output in a browser.

The error-checking boundary is strict. This fork has generic Twee and StoryData checks, but no Harlowe macro or argument validator. Keep that behavior. Navigation and tracing require new parsing and reference infrastructure; they must not introduce unknown-macro, undefined-variable, missing-passage, argument-type, or general Harlowe syntax inspections. [R2], [R3], [R4], [R5]

- Target WebStorm first and verify IntelliJ IDEA with the JavaScript plugin as a second supported host. Initial minimum host: WebStorm 2025.3.6, build 253.33813.27; verify IntelliJ IDEA 2025.3.6.1, build 253.33813.55, as the peer host.
- Include both .twee and .tw files. A passage's name is independent of its filename, and a file can contain multiple passages.
- Build persistent indexes from file content only; apply one selected story scope to navigation, tags, tracing queries, and compilation. Store its source roots and exclusions in project settings; run configurations may choose a different story scope explicitly.
- Exclude SugarCube and Chapbook support, the visual Story Map, packing and passage relocation, general formatting, snippets, runtime debugging, watch mode, live reload, and hosting. These are outside the five requested features.

## Repository findings

The Harlowe and generic grammar files parse successfully as JSON. Their main risks are inconsistent recognition rules: TextMate passage boundaries require a literal space after ::, while semantic header parsing accepts arbitrary or missing whitespace. Script/style body patterns recognize a single special tag, while semantic parsing sees those tags in multi-tag headers. Macro opener and macro-name coloring regexes also accept different characters. Capture these cases as baseline fixtures; do not quietly turn regex quirks into additional language rules. [R2], [R4]

The reviewed toolchain uses Kotlin 2.3.20 and Gradle 9.7.1. Kotlin's published fully supported range for that compiler ends at Gradle 9.3.0. This is a compatibility risk, not evidence of a failed build. The target IDE branch bundles Kotlin stdlib 2.2.20, so compile against its API level and inspect the resulting plugin dependencies. [R1], [J6], [J7]

| Area | Observed implementation | Planning consequence |
| --- | --- | --- |
| IntelliJ scaffold | Gradle targets IDEA 2025.3.6.1. plugin.xml registers only MyToolWindow; Kotlin contains sample bundle and random-number UI. [R1] | The project is initialized, but no Twee language support is wired into IntelliJ. |
| Harlowe highlighting | defs/harlowe-3/grammar.json provides body TextMate scopes. parse-text.ts supplies semantic header tokens. [R2], [R4] | Port both layers; the JSON grammar is a specification, not an IntelliJ parser. |
| Error checking | Generic header whitespace and StoryData JSON checks; separate IFID and format-field notifications. SugarCube diagnostics are language-gated. [R3], [R5] | Preserve only checks that currently apply to Harlowe documents. |
| Passage links | Story Map extracts bracket links and can open passages. Editor definition handling delegates to SugarCube macros. [R6], [R6a], [R7] | Harlowe editor passage references require a native implementation. |
| Source tracing | No Call Hierarchy or references provider is registered. Harlowe has no macro-definition catalog. [R7] | Implement scoped symbols, references, usages search, and a hierarchy provider. |
| Tagging | Headers carry tags; passage list groups by tag; Story Map sidebar adds/removes tags for selected passages. [R4], [R6], [R8] | Carry these operations into a small native Passages tool window. |
| Build and run | npm build/serve belong to the Vue Story Map. Getting-started documentation invokes Tweego manually. [R9] | Story compilation and browser launch are new IntelliJ integrations. |
| Tests and CI | tests/*.tw are example fixtures. No npm test script, Kotlin test sources, or .github workflow appears in the reviewed tree. [R10] | Create automated characterization and native language tests before porting behavior. |

## Architecture and shared data

Use native Kotlin PSI rather than a Node bridge or a new language server. PSI references directly support editor navigation and usages; the hierarchy provider can reuse those references. A separate language server would still need IntelliJ-specific tagging and run integration, while the existing repository contains no Harlowe server to reuse. [J1], [J2]

Keep passage parsing, symbol extraction, and link-target recognition in shared code. Highlighting, index builders, hierarchy, and tags must agree about boundaries and offsets. Preserve raw spelling alongside decoded passage names so escaped brackets and braces do not break navigation or editing. Resolve a reference by passage name within its story scope, never by guessing a filename.

| Component | Responsibility |
| --- | --- |
| twee.language and twee.parser | Register Language/FileType/ParserDefinition; tokenize headers and Harlowe bodies; build tolerant PSI for passages, tags, links, macros, hooks, variable accesses, assignments, and scopes. |
| twee.highlighting and twee.inspections | Map existing categories to theme attributes; apply semantic header colors; implement only inherited checks. |
| twee.index and twee.resolve | Stub passage declarations and tags. Index per-file symbol occurrences and static relation candidates. Apply story scope and name semantics during queries. |
| twee.hierarchy and twee.usages | Resolve caret targets; show static incoming/outgoing relations, variable reads/writes, source locations, and normal IDE usages. |
| twee.passages and twee.tags | Show passages grouped by tag, edit tags through undoable PSI/document commands, and navigate to passage headers. |
| twee.settings and twee.run | Persist story scope, inherited check switches, local compiler path, run configuration, and browser preference; build through one shared service. |

- Passage PSI implements PsiNameIdentifierOwner and retains tags and metadata ranges. Scope identity distinguishes separate stories that reuse the same passage names.
- Store only file-derived declarations and occurrences in persistent indexes. Cross-file resolution and project settings belong in query services, not indexers. Bump index/stub versions when schemas change. [J3]
- Global variable keys include story identity and exact variable name. Temporary variable keys additionally include their binding scope. Named macro calls use normalized macro names; variable-valued calls retain variable identity.
- Represent graph relations with their kind and source range: link, passage display, passage transition, macro invocation, variable read, or variable write. Preserve every physical occurrence even when the hierarchy groups several into one row.
- Keep physical PSI smart pointers for navigation and rebuild transient logical symbols after edits. Avoid retaining entire PSI trees or treating stored offsets as authoritative across changes.
- Use background, cancellable read actions for searches; keep UI updates on the UI thread. Index-dependent queries must defer or show indexing status during dumb mode. Local highlighting and generic checks should continue.
- Revalidate candidates against committed live PSI, including unsaved documents. Cache with PSI/document and settings modification dependencies. Collect candidate files before resolving them; avoid nested index access and full-project reparsing per keystroke.

## Feature 1 Harlowe highlighting and existing checks

Implement a restartable native lexer with explicit nested macro, string, hook, collapsed-block, comment, and embedded-region states. A small Kotlin state wrapper around generated JFlex tokens is acceptable; nested state restoration must pass incremental-edit tests. A tolerant parser consumes incomplete input and resumes at the next passage. Ordinary prose parentheses and apostrophes must remain prose where the inherited grammar treats them that way. [J4]

Port the existing body categories: macro names and punctuation; nested expressions; operators and type words; quoted strings; numbers and booleans; story and temporary variables; property tokens; HTML entities; hook references, hooks and named hooks; collapsed whitespace; bracket links; HTML and HTML comments; JSON, JavaScript and CSS regions. Port header categories for ::, passage name, tags, metadata, and special names/tags. Use ColorSettingsPage and standard theme fallback keys, with silent annotations for semantic distinctions. [R2], [R4], [J5]

Preserve embedded-language coloration with lexer-based layers. Full JavaScript/CSS/JSON injection can activate extra host inspections; keep it outside this release unless those diagnostics are explicitly controlled to preserve the agreed checking scope. Include the JavaScript bundled dependency if its lexer APIs are used. [J8]

| Existing check or behavior | Native treatment | Acceptance requirement |
| --- | --- | --- |
| Missing whitespace after :: | Configurable warning; enabled by default. | Match the inherited trigger, severity, message meaning, and disabled behavior. |
| Malformed StoryData JSON | Editor error on the StoryData body. | Use JSON.parse-style acceptance; preserve sensible source ranges. Do not add field-type or schema errors. |
| Missing or invalid IFID | Configurable project validation notification. | Match the existing UUID acceptance behavior; no extra version or format criteria. |
| Missing format or format-version | Configurable project validation notification. | Keep the inherited field-presence checks. Build setup errors remain separate. |
| Malformed ordinary header metadata/tags/name | Tolerant parsing with raw fallback. | Rejected headers remain raw text and are excluded from passage declarations/indexes. Add no new editor errors. |
| invalid.illegal grammar scopes | Equivalent lexical styling where observable. | Do not convert zero-width recovery captures into new unclosed-macro/hook errors. |
| Unknown macros, undefined variables, bad macro arguments, unresolved links | Navigation may be unavailable; no new validator. | Explicit negative tests confirm no new Harlowe warning/error appears. |

- Keep inherited switches and defaults in project settings. Recompute StoryData on relevant content/settings changes; deduplicate notifications instead of alerting on every keystroke.
- Use StoryData's Harlowe major version and an explicit Harlowe override when metadata is absent. A non-Harlowe story can retain generic Twee structure, without claiming support for another body language.
- Build a development-only token/diagnostic characterization harness using the inherited grammar and fixtures. Assert categories and ranges rather than identical colors across unrelated themes.
- Record all intentional parity differences in a small reviewable list. The default is to preserve observed highlighting coverage and checking behavior, including known edge cases; new Harlowe rules require separate scope.

## Feature 2 Clickable passage references

Give link-target PSI a soft, polyvariant passage reference. Ctrl/Cmd-click and Go to Declaration must open the owning .twee or .tw file at the passage's header. The display label does not determine the destination. An unresolved link adds no new inspection; duplicate passage names expose all candidates through the IDE's chooser. [J1]

| Link source | Target text |
| --- | --- |
| [[Target]] | Target |
| [[Label->Target]] | Target |
| [[Target<-Label]] | Target |
| [[Label\|Target]] | Target |

- Take the four inherited bracket-link forms from getLinkedPassageNames, but implement them using real PSI ranges rather than its whole-text split. Keep the clickable range on the target, including the whole content for an unlabelled link. [R6a]
- Normalize escaping consistently with passage headers, preserve passage-name case, and test spaces, punctuation, Unicode, and CRLF. Use VirtualFile identity for paths rather than manual drive-letter string handling.
- Do not create links from comments, macro string text containing bracket syntax, JavaScript/CSS bodies, or unrelated HTML attribute text. If quoted markup is an argument to a runtime macro, its later execution is dynamic unless a specific static rule exists.
- For feature 3, also recognize literal passage arguments in display/go-to/redirect and link-goto/link-reveal-goto at their documented argument positions. Use the same passage reference class for navigation and trace edges; never mark every quoted macro argument as a passage.
- Keep computed destinations visible as unresolved dynamic relations in hierarchy metadata. Do not guess a target by variable name or evaluate story code.
- Acceptance: navigate within one file and across files; restrict lookups to the active story; handle duplicates and missing targets; update immediately after passage/file renames, deletion, and unsaved edits.

## Feature 3 IDE source tracing

Register com.intellij.callHierarchyProvider for Twee using HierarchyProvider. Its target comes from a physical passage header, link destination token, macro-name occurrence, variable binding, or variable access at the caret. A destination resolves to its passage target; duplicates remain selectable candidates. A custom HierarchyBrowserBaseEx uses the IDE's Hierarchy tool window and builds views from the shared relation service. Invoke the normal IDE action through the extension point; avoid the internal BrowseCallHierarchyAction implementation. [J9]

Show incoming and outgoing passage/macro relations. Variable roots show Reads and Writes views in the same native hierarchy browser, with labels explaining data access. A variable read is not an ordinary function call. A custom macro variable exposes Calls and Reads/Writes modes: ($m:) opens Calls by default; ordinary $m accesses open Reads/Writes, with a mode switch preserving access to both. Find Usages is additional access to the same symbols and physical occurrences; it does not replace the requested hierarchy integration.

| Root | Incoming or reads | Outgoing or writes |
| --- | --- | --- |
| Passage | Passages or macro bodies linking to, displaying, or transitioning to it. | Static target passages and macro invocations contained in it; group relation kinds. |
| Named macro occurrence | Passages/custom macro bodies invoking the normalized name. | Terminal when only a Harlowe engine name is known; no invented engine implementation. |
| Custom macro variable | Invocation occurrences that resolve to its direct macro binding or candidate bindings. | Passage references, macro invocations, and variable accesses inside its code hook. |
| Story variable $name | Reads grouped by containing passage or macro body. | Writes grouped by containing passage or macro body; destination assignment does not establish execution order. |
| Temporary variable _name | Reads in the resolved passage/hook/lambda/macro scope. | Writes and binding locations within that scope; same spelling elsewhere is a separate symbol. |

- Normalize ordinary macro names using Harlowe's case, hyphen, and underscore equivalence. An initial _name instead denotes a temporary-variable custom call and must retain that variable identity. Keep variable names case-sensitive. Occurrence-backed macro symbols permit tracing without adding a builtin validity catalog. [J10]
- Recognize direct custom macro creation such as (set: $m to (macro: ...)) and calls such as ($m: ...). Make the code hook the callable body, not executable content of its containing passage at declaration time.
- Implement Harlowe temporary scopes deliberately: nested hooks inherit existing temporary bindings, loop/lambda binders can shadow them, and a macro code hook has its own parameter scope without capturing outer temporary variables. [J10]
- Classify reads/writes in assignment destinations and expressions, including set/put/move, macro parameters, for/lambda binders, and bind-based updates. Property mutation must count as access to its base variable. Distinguish clear writes from conservative read/write candidates.
- Use multiResolve for ambiguous custom macro bindings. Simple direct references may resolve precisely; reassignment, aliases, macro values passed through parameters, computed targets, and conditional execution must remain candidate or dynamic edges. No whole-program runtime evaluation or reaching-value claim is implied.
- Add FindUsagesProvider, a WordsScanner, ElementDescriptionProvider, UsageTypeProvider, ReadWriteAccessDetector, and a FindUsagesHandlerFactory registered through com.intellij.findUsagesHandlerFactory for logical variable/macro symbols. Add a bounded ReferencesSearch executor for escaped/multiword passage names and normalized macro aliases that default word search can miss. [J2]
- Represent variable symbols with all occurrences and binding/write candidates, rather than declaring the first encountered assignment to be their universal definition. A caret read with no known write must still open Reads/Writes views.
- Hierarchy results retain file and passage labels, occurrence counts, and click-through source locations. Support recursion and cycles with cycle nodes, lazy children, duplicate elimination, cancellation, and refresh after edits.
- Acceptance: incoming and outgoing passage links match; builtin-name usage grouping works across aliases; custom macro bodies and callers are separated; variable writes and reads are classified; temporary shadowing is respected; ambiguous/dynamic cases never assert a definitive runtime chain.

## Feature 4 Passage tagging

Replace MyToolWindowFactory with a native Passages tool window that groups passages by tag and includes Untagged. Selecting a row opens the passage header. A compact tag editor supports add/remove with suggestions from existing story tags and applies an operation to one or multiple selected passages. This carries over the existing passage list and Story Map tag operations without rebuilding the map. [R6], [R8]

- Parse tags in the header's bracket section, preserving the passage name, escaping, metadata JSON, body content, line endings, tag order, and unaffected whitespace. Store each tag's range for safe edits.
- Use WriteCommandAction and targeted PSI/document replacement, then commit documents. For a batch, apply edits in descending offsets per file and expose the operation as one undoable command. Do not port the old raw string replacement of whole headers.
- Prevent duplicate additions and reject an empty or structurally invalid tag at the tag editor boundary. This protects the edit command and is not a new Harlowe editor inspection.
- Multi-selection shows tags applied to all vs some passages. Add leaves existing tags intact; remove affects only selected passages containing the tag. Refresh from the authoritative document model.
- Keep special tag meaning: script/stylesheet select embedded body coloring; Harlowe startup/header/footer and ordinary tags remain literal story tags. Tag edits must update compiler input without translating or rewriting tag semantics.
- Acceptance: group and navigate; add/remove one or multiple tags; suggest existing tags; edit unsaved documents; undo/redo; preserve metadata/body bytes; show empty-tag and mixed-selection states.

## Feature 5 Build HTML and run

Use a user-installed Tweego executable and locally installed Harlowe 3 format. Implement a native configuration type, factory, persisted options, SettingsEditor, and CommandLineState. Both Build HTML and the Run toolbar use one build service; Run compiles once and opens the resulting HTML only after success. A custom ProgramRunner is unnecessary for this workflow. [J11]

| Configuration | Rule |
| --- | --- |
| Story | Named source scope with explicit input roots/files and exclusions, shared with navigation and tagging. |
| Tweego | Resolve local executable; machine-specific path stays in local settings, with PATH fallback. |
| Working directory | Project-relative story root, resolved to an absolute path at execution. |
| Output | Configurable HTML path; default dist/index.html under the selected story root. |
| Story format | Use compatible StoryData selection or an explicitly selected installed Harlowe 3 format ID. Never fall back to SugarCube; show the effective format/version. |
| Environment | Optional format directories via TWEEGO_PATH and explicit environment overrides. |
| Browser | Configured IDE/default external browser. Build-only does not launch it. |

- Save the selected story's dirty input documents before compiling. Validate executable, input scope, output directory, and local format availability; show setup failures in the run console/settings rather than adding Harlowe inspections.
- Build an argument vector with GeneralCommandLine. Never use a shell-concatenated command; paths with spaces, Unicode and metacharacters must survive on Windows, macOS and Linux. Run off the UI thread with a cancellable process handler. Serialize or reject simultaneous builds targeting the same final output path.
- Discover formats with Tweego's --list-formats. StoryData selects by format name and major version; it does not strictly pin a patch. Use an installed format ID through --format when exact-version execution is required. There is no --format-version option. [J12]
- Resolve the selected roots to eligible explicit inputs after exclusions, so output files, compiler/storyformat installations and other stories are not accidentally recompiled. Include the requested CSS/JS source files where the story scope includes them; let Tweego perform its documented bundling.
- Do not feed every external image/audio asset to Tweego as a side effect of directory scanning. Keep relative assets in a known location relative to output, or apply an explicit configured copy mapping to the output directory. Preserve referenced paths; no general asset bundler is part of this release.
- Compile to a fresh staging HTML file adjacent to the output; on exit code zero and confirmed output, promote it to the configured path and refresh VFS. Failure or cancellation removes staging files, keeps the last good HTML, and never opens it as if it were new.
- Show stdout/stderr and exit code in the native console. Add source hyperlinks only for reliably parsed compiler locations; preserve unrecognized output verbatim. Browser execution exposes Harlowe runtime errors in the story itself, not a promised IDE debugger.
- For Run, open the final file through the IDE browser API after successful promotion. Verify generated Harlowe starts at the configured StoryData start passage and can follow links. One-shot file-based run is the release baseline; HTTP serving and automatic rebuild/reload are excluded.
- Acceptance: missing compiler/format, malformed input, paths with spaces, custom start, multiple source files, included JS/CSS, relative assets, cancellation, nonzero exit, stale output, build-only behavior, and successful browser launch.

## Implementation order and review milestones

Execution is tracked in [modular chapters](chapters/README.md). That tracker splits these milestones into smaller units without changing the scope or release gates.

Complete each milestone with a small demonstrable slice. Navigation and tracing depend on the language model; the compiler integration can proceed independently after story-scope settings are defined. Tag editing depends on stable header ranges, not on completion of hierarchy.

| Milestone | Changes | Completion evidence |
| --- | --- | --- |
| 1 Baseline and toolchain | Freeze grammar/check fixtures; target WS 2025.3.6; pin a compatible Gradle/Kotlin pair; declare JVM 21 and API 2.2; replace metadata placeholders. | Clean scaffold build, project configuration verification, and actual WS sandbox launch. |
| 2 Language model | File type, lexer/parser, passage PSI, headers/tags/metadata, body nodes and scopes; restart-safe highlighting foundation. | Parsing and lexer restart assertions pass, including incomplete edits and recovery at later passages. |
| 3 Highlighting and existing checks | Color categories and embedded lexers; semantic header colors; inherited generic checks and settings. | Characterization comparison and negative diagnostic tests pass in light/dark themes. |
| 4 Index and passage navigation | Passage stubs, story scopes, references, lookup/search rules, live-document invalidation. | Cross-file click-through and duplicate/missing/escaped-name cases pass. |
| 5 Tagging | Native Passages tool window and undoable single/batch tag edits. | Header preservation, grouping, suggestions, and undo/redo pass. |
| 6 Usages and hierarchy | Macro/variable occurrences and bindings, relation extraction, native hierarchy provider and usages adapters. | Static caller/callee and reads/writes scenarios pass, including recursion, scopes and dynamic ambiguity. |
| 7 HTML build and run | Tweego configuration/service, console, staging/promote, browser launch and asset paths. | Process failure/success tests and a real Harlowe browser smoke test pass. |
| 8 Release verification | Installable ZIP, host matrix, CI workflow, docs, notices and end-to-end checks. | Fresh WS install, required Gradle checks, verifier results and five-feature smoke checklist are attached. |

## Files to change and create

Leave legacy TypeScript/Vue sources as reference during the port; Gradle packages only the native deliverable and selected resources. No production Node.js, npm, Vue, socket.io or VS Code runtime should be required. Preserve existing identifiers and conventions when adapting supplied code.

| Location | Planned change |
| --- | --- |
| build.gradle.kts | Switch compile/test host to webstorm(2025.3.6); add JavaScript bundled dependency for embedded lexers; JVM21/API2.2; explicit verifier hosts; register generator tasks if JFlex/Grammar-Kit is used. |
| settings.gradle.kts and wrapper | Retain IntelliJ Platform Gradle plugin 2.19.0 and Kotlin2.3.20 initially; use Gradle9.3.0 after compatibility verification. Pin wrapper checksum and generated-wrapper files. |
| gradle.properties and .gitignore | Set kotlin.stdlib.default.dependency=false; keep IDE-bundled coroutines; ignore Gradle/Kotlin/build artifacts. Retain existing group/version conventions. |
| META-INF/plugin.xml | Preserve twee.twee3-webstorm ID. Replace sample tool window and metadata; register file type, parser/highlighter, checks, usages, hierarchy, indexes, settings, run type and Build HTML action. Declare com.intellij.modules.platform, com.intellij.modules.lang and JavaScript dependencies. |
| src/main/kotlin/twee/... | Create focused language, parser, highlighting, index, resolve, usages, hierarchy, tags, passages, settings and run packages. Replace sample UI/bundle with real feature text. |
| src/test/kotlin/twee/... | Native fixture tests for parsing, checks, references, hierarchy, tags, settings and process execution. |
| src/test/testData/... | Adapt Harlowe/Twee/Arrows fixtures; add multi-file stories, escaping, scopes, dynamic macros, tags and compiler inputs. |
| tests and defs/harlowe-3 | Keep immutable upstream reference material and provenance; use dev-only parity tooling without shipping VS Code dependencies. |
| README.md and docs | Replace template directions with install/support scope, compiler setup, five workflows, source-trace limits, and developer verification instructions. |
| .github/workflows/verify.yml | Add reproducible build/tests/project checks and explicit host verifier matrix. Archive plugin ZIP and failure reports; no automatic Marketplace publication. |
| LICENSE and notices | Retain Cyrus Firheir MIT notice for reused material and identify any new dependency notices. Avoid importing SugarCube code into the release. |

## Verification and release criteria

Use IntelliJ light fixture tests for language, PSI, highlighting/checks and reference behavior. Keep compiler command construction and output handling testable with a controlled fake executable, then run a real Tweego/Harlowe integration fixture. The repository's existing examples provide useful input but do not prove behavior on their own.

- Language parity: headers/escaping/tags/metadata; CRLF/no final newline; nested macros/hooks/strings; ordinary prose; script/style/JSON/HTML; malformed editing and later-passage recovery; no new Harlowe diagnostics.
- Navigation: four bracket-link forms, literal passage macro arguments, multiple passages per file, case-sensitive names, Unicode and escaping, duplicates, wrong story scope, missing targets, live edits and file moves.
- Tracing: static forward/reverse edges, multiple call sites, builtin aliases, macro-variable declarations and calls, parameter scopes, nested-hook inheritance, loop/lambda shadowing, assignment reads/writes, recursion and unresolved dynamic calls.
- Tags: untouched body/metadata preservation, single/batch edits, duplicate prevention, suggestions, partial selections, undo/redo, index refresh and special tags.
- Run: executable/format discovery; settings persistence; argv/environment/working directory; source exclusions; UTF-8/spaces; real compiler success; nonzero failure/cancel; no stale browser launch; relative assets and configured start.
- Performance: use a repeatable large-story fixture and record initial indexing, incremental edit, lookup and hierarchy timings. A small edit must not reparse all project files; candidate-only queries must remain cancellable and avoid long UI-thread work.
- Packaging: run check, verifyPluginProjectConfiguration, verifyPluginStructure, buildPlugin and verifyPlugin. Inspect the ZIP for duplicate Kotlin/coroutines and legacy runtime assets. Task existence alone is not compatibility evidence. [J13]
- Host matrix: verify WS2025.3.6 and IDEA2025.3.6.1 with the declared JavaScript dependency, plus the stable releases explicitly intended for support when implementation ships. Launch WS and install the ZIP; verifier success does not replace UI tests.
- Declare since-build253.33813.27 for the selected WS minimum; follow current SDK guidance to omit until-build. Record only actually verified products/releases as tested support, and keep newer-version verification in CI. Do not interpret the open upper bound as a compatibility guarantee. [J13]
- Release smoke story: startup custom macro sets a story variable; one passage invokes it and links to a passage in another file; both passages carry tags. Confirm highlighting, inherited errors, click-through, hierarchy/reads/writes, tagging undo, HTML compilation, browser launch and working story links.

## Risks and decisions

| Risk | Decision or mitigation |
| --- | --- |
| Harlowe validator expectations | Document that only inherited generic checks exist. Do not estimate or implement a new validator under this feature. |
| Lexer nesting and parity quirks | Characterize edge cases first; test state restoration after edits; keep a reviewed list of necessary parity differences. |
| Dynamic macros and passage targets | Use candidate/dynamic labels and multi-resolve. Trace source relations, not runtime values or execution paths. |
| Temporary scopes | Make hooks/lambdas/loops/macro code hooks explicit; verify scope rules before global indexing. |
| Multiple stories and unsaved edits | Share selected story scope and live PSI across all features; validate settings-based cache invalidation. |
| Host/API compatibility | Target WS directly; validate public hierarchy APIs and toolchain in the earliest sandbox milestone. |
| Embedded inspection creep | Layer lexer coloration without enabling unrestricted injected-language inspections. |
| External compiler and formats | Configure local installation and effective format; make missing tooling actionable; never silently use Tweego's SugarCube default. |
| Asset and browser behavior | Keep output-relative resource paths predictable and test file-based run. Server/live reload needs a later explicit feature. |
| Raw header replacement | Use exact PSI/document ranges and undoable commands; preserve metadata/body and retest after tag-induced range changes. |

## Repository evidence

All repository references below point to the reviewed commit. Line ranges identify the behavior supporting the findings.

- **R1** [Scaffold and toolchain](https://github.com/Wu-Li/twee3-webstorm/tree/8c7715183b3387292406295523ab54f6a058c062) — build.gradle.kts, settings.gradle.kts, gradle.properties, wrapper properties, plugin.xml and sample Kotlin files
- **R2** [Harlowe grammar](https://github.com/Wu-Li/twee3-webstorm/blob/8c7715183b3387292406295523ab54f6a058c062/defs/harlowe-3/grammar.json) — Body categories, L14–81; boundaries/macros, L83–151; embedded/link rules, L371–432
- **R3** [Generic diagnostics](https://github.com/Wu-Li/twee3-webstorm/blob/8c7715183b3387292406295523ab54f6a058c062/src/diagnostics.ts) — Header warning/StoryData JSON, L7–39; SugarCube diagnostic gate, L41
- **R4** [Header parsing and semantic tokens](https://github.com/Wu-Li/twee3-webstorm/blob/8c7715183b3387292406295523ab54f6a058c062/src/parse-text.ts) — Header regex, L15; tags/metadata/name handling, L45–139; legend/provider, L309–338
- **R5** [StoryData configuration](https://github.com/Wu-Li/twee3-webstorm/blob/8c7715183b3387292406295523ab54f6a058c062/src/twee-project.ts) — Field checks and notifications, L15–57; override/format selection, L62–74
- **R6** [Passage model and grouping](https://github.com/Wu-Li/twee3-webstorm/blob/8c7715183b3387292406295523ab54f6a058c062/src/passage.ts) — Tag groups/Untagged, L69–105; passage model and jumpToPassage, L145–201
- **R6a** [Story Map links and writes](https://github.com/Wu-Li/twee3-webstorm/blob/8c7715183b3387292406295523ab54f6a058c062/src/story-map/socket.ts) — getLinkedPassageNames, L10–18; header rewrite, L102–128
- **R7** [Extension registration](https://github.com/Wu-Li/twee3-webstorm/blob/8c7715183b3387292406295523ab54f6a058c062/src/extension.ts) — Providers, L208–234; definition implementation is sugarcube-2/macros.ts, L990–1069
- **R8** [Inherited tag editor](https://github.com/Wu-Li/twee3-webstorm/blob/8c7715183b3387292406295523ab54f6a058c062/story-map/src/components/Sidebar.vue) — Tag controls/suggestions, L48–83; selection states, L146–165; add/remove, L328–335
- **R9** [Manual Tweego setup](https://github.com/Wu-Li/twee3-webstorm/blob/8c7715183b3387292406295523ab54f6a058c062/docs/getting-started.md) — Compiler setup and commands, L264–352; source layouts, L369–405; package.json L1–10 contains Vue scripts
- **R10** [Example fixture directory](https://github.com/Wu-Li/twee3-webstorm/tree/8c7715183b3387292406295523ab54f6a058c062/tests) — Harlowe.tw, Twee.tw and ArrowsTest.tw; no automated test assertions in these fixtures

## Primary technical references

- **J1** [PSI References](https://plugins.jetbrains.com/docs/intellij/psi-references.html) — Navigation, soft/polyvariant references and reverse references search
- **J2** [Find Usages](https://plugins.jetbrains.com/docs/intellij/find-usages.html) — Named elements, word scanner, scopes and usages presentation
- **J3** [Stub and file based indexes](https://plugins.jetbrains.com/docs/intellij/stub-indexes.html) — File-derived stubs, serialization/versioning; companion file-based-indexes.html covers occurrence indexing
- **J4** [Implementing Lexer](https://plugins.jetbrains.com/docs/intellij/implementing-lexer.html) — Incremental restart state and complete token coverage
- **J5** [Syntax and Error Highlighting](https://plugins.jetbrains.com/docs/intellij/syntax-highlighting-and-error-highlighting.html) — Native highlighter, text attributes, parser/annotator behavior
- **J6** [Configuring Kotlin Support](https://plugins.jetbrains.com/docs/intellij/using-kotlin.html) — IDE Kotlin API/std-library compatibility and bundled coroutines
- **J7** [Kotlin Gradle configuration](https://kotlinlang.org/docs/gradle-configure-project.html) — Kotlin2.3.20–2.3.21 fully supported Gradle range7.6.3–9.3.0
- **J8** [WebStorm Plugin Development](https://plugins.jetbrains.com/docs/intellij/webstorm.html) — webstorm() build target, JavaScript and platform dependencies
- **J9** [HierarchyProvider source](https://github.com/JetBrains/intellij-community/blob/idea/253.32098.37/platform/lang-api/src/com/intellij/ide/hierarchy/HierarchyProvider.java) — Public provider interface on platform253; HierarchyBrowserBaseEx and BrowseHierarchyActionBase are in platform/lang-impl/src/com/intellij/ide/hierarchy
- **J10** [Harlowe manual](https://twine2.neocities.org/) — Macro names, temporary variables, macro code hooks, assignment and passage-target macro semantics
- **J11** [Run Configurations Tutorial](https://plugins.jetbrains.com/docs/intellij/run-configurations-tutorial.html) — Configuration type/factory/options/editor and CommandLineState
- **J12** [Tweego documentation](https://www.motoslave.net/tweego/docs/) — Inputs, --format, --list-formats, TWEEGO_PATH and StoryData version selection
- **J13** [IntelliJ Platform Gradle tasks](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-tasks.html) — Packaging, project/structure checks, Plugin Verifier and compatibility range guidance

[R1]: https://github.com/Wu-Li/twee3-webstorm/tree/8c7715183b3387292406295523ab54f6a058c062
[R2]: https://github.com/Wu-Li/twee3-webstorm/blob/8c7715183b3387292406295523ab54f6a058c062/defs/harlowe-3/grammar.json
[R3]: https://github.com/Wu-Li/twee3-webstorm/blob/8c7715183b3387292406295523ab54f6a058c062/src/diagnostics.ts
[R4]: https://github.com/Wu-Li/twee3-webstorm/blob/8c7715183b3387292406295523ab54f6a058c062/src/parse-text.ts
[R5]: https://github.com/Wu-Li/twee3-webstorm/blob/8c7715183b3387292406295523ab54f6a058c062/src/twee-project.ts
[R6]: https://github.com/Wu-Li/twee3-webstorm/blob/8c7715183b3387292406295523ab54f6a058c062/src/passage.ts
[R6a]: https://github.com/Wu-Li/twee3-webstorm/blob/8c7715183b3387292406295523ab54f6a058c062/src/story-map/socket.ts
[R7]: https://github.com/Wu-Li/twee3-webstorm/blob/8c7715183b3387292406295523ab54f6a058c062/src/extension.ts
[R8]: https://github.com/Wu-Li/twee3-webstorm/blob/8c7715183b3387292406295523ab54f6a058c062/story-map/src/components/Sidebar.vue
[R9]: https://github.com/Wu-Li/twee3-webstorm/blob/8c7715183b3387292406295523ab54f6a058c062/docs/getting-started.md
[R10]: https://github.com/Wu-Li/twee3-webstorm/tree/8c7715183b3387292406295523ab54f6a058c062/tests
[J1]: https://plugins.jetbrains.com/docs/intellij/psi-references.html
[J2]: https://plugins.jetbrains.com/docs/intellij/find-usages.html
[J3]: https://plugins.jetbrains.com/docs/intellij/stub-indexes.html
[J4]: https://plugins.jetbrains.com/docs/intellij/implementing-lexer.html
[J5]: https://plugins.jetbrains.com/docs/intellij/syntax-highlighting-and-error-highlighting.html
[J6]: https://plugins.jetbrains.com/docs/intellij/using-kotlin.html
[J7]: https://kotlinlang.org/docs/gradle-configure-project.html
[J8]: https://plugins.jetbrains.com/docs/intellij/webstorm.html
[J9]: https://github.com/JetBrains/intellij-community/blob/idea/253.32098.37/platform/lang-api/src/com/intellij/ide/hierarchy/HierarchyProvider.java
[J10]: https://twine2.neocities.org/
[J11]: https://plugins.jetbrains.com/docs/intellij/run-configurations-tutorial.html
[J12]: https://www.motoslave.net/tweego/docs/
[J13]: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-tasks.html
