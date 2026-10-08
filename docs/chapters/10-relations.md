# Chapter 10: Static symbols and relations

Status: Implemented; native/index/UI verification pending

Plan milestone: 6. Dependencies: 04, 06, 07, 08.

## Implemented

- Added shared file-derived relation facts with physical ranges, passage/callable ownership, local scope identities and candidate flags. Named macro calls normalize case, hyphens and underscores; variable calls retain case and the `$`/`_` prefix. Digit-containing names use the source opener rather than the inherited narrower name-coloring token. Leading-hyphen macro aliases now receive structural macro PSI; this deliberate lexer extension requires parity/restart verification.
- Extracted reads, writes, conservative read/write candidates, parameter/lambda bindings, direct custom macro bindings, named/custom calls, and the shared literal/dynamic passage link/display/transition targets. Every source occurrence is retained. Assignment metadata is separate from access facts so a direct macro assignment is not counted twice in a Writes query.
- Added hook inheritance, isolated custom-macro parameter/body scopes, direct for/loop attached-hook scopes, and each/where/via/making/when lambda scopes. Unknown-write reads still have logical symbol keys. Custom macro body occurrences have the code hook as their owner instead of the declaring passage.
- Added the versioned `twee.relations` file index, deterministic fact serialization, scoped live query service and file-dependent extraction cache. Persistent data has no project/story settings or cross-file resolution. Queries collect candidate files before PSI access, include unsaved documents, and return explicit pending/unsupported states. Temporary keys include story, file and binding scope; ordinary variables and named macros are story-scoped.
- Added incoming passage, outgoing owner and variable Reads/Writes query entry points. Soft polyvariant custom macro references return all matching direct code-hook candidates. Aliases, reassignment, conditional execution and parameters are not evaluated; even a single source binding is labelled as a candidate, never a proven runtime destination.

## Boundaries

Temporary declaration lookup is a static lexical model over the file, not reaching-value analysis: uncertain execution order does not establish a runtime binding. Direct loop-hook attachment is modelled; stored/computed changer combinations are not evaluated. Destructuring destinations and bound-variable updates are conservative read/write candidates. Dynamic bracket markup remains an opaque dynamic passage relation, rather than expanding runtime markup. Comments, strings and embedded languages are excluded. No validators or runtime debugger were added.

## Sources reviewed

Primary [Harlowe 3 manual](https://twine2.neocities.org/) sections on macro markup, set/put/move, for, macro code hooks, lambdas and bound variables; [IntelliJ file-based indexes](https://plugins.jetbrains.com/docs/intellij/file-based-indexes.html). Rules specifically reviewed: macro name equivalence, case-sensitive variables, hook temporary lifetime/inheritance, loop/lambda shadowing, no outer temporary capture in macro code hooks, property-source deletion in move, and bind/2bind updates.

## Checks executed

- `npm test --prefix tools/characterization`: 7/7 inherited baseline tests pass; these do not execute new Kotlin code.
- `git diff --check`: passed.
- Plugin descriptor XML parse and registered native class source presence: passed.

## Verification pending

Seven new extraction/serialization fixtures cover aliases and physical occurrences, assignments/property access/bind/2bind, hook inheritance, loop/lambda shadowing, isolated custom bodies/multiple bindings, dynamic targets and unknown-write reads, and serialization including long Unicode names. Three query fixtures cover duplicate custom bindings/story isolation, unsaved edits/scope changes, and file-local temporary identities. They have **not** been compiled or executed.

JDK 21/Gradle and WebStorm prerequisites remain unavailable as recorded in Chapter 02. Run all native fixtures, index rebuild/serialization integration, incremental lexer checks, indexing transitions, and custom-call navigation/chooser tests before merge/release. Find Usages and hierarchy UI remain Chapters 11–12.

Next implementation: Chapter 11, native Find Usages.
