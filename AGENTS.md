# Project guidance

Read [docs/implementation-plan.md](docs/implementation-plan.md) before starting work. It defines the reviewed behavior, intended architecture, implementation order, and acceptance criteria. Keep this guidance aligned as milestones land. For incremental implementation, read [docs/chapters/README.md](docs/chapters/README.md), continue the first unfinished chapter, and record actual checks and blockers there.

## Repository layout

This repository combines an IntelliJ plugin scaffold with the inherited VS Code extension. The Kotlin scaffold currently provides sample functionality; the plan describes work still to be built.

- Native plugin code: `src/main/kotlin`.
- Resources and extension registrations: `src/main/resources`, including `META-INF/plugin.xml`.
- Porting references: inherited TypeScript in `src`, grammars in `defs`, and Twee fixtures in `tests`.
- Planned native tests: `src/test/kotlin` and `src/test/testData`.

The npm `build` and `serve` scripts build and serve the Vue Story Map. They do not compile or run Twee stories.

## Scope

Implement only:

1. Existing Harlowe 3 syntax highlighting and error checking.
2. Passage references that open the target passage's `.twee` or `.tw` file.
3. Static passage, macro, and variable tracing through native IDE hierarchy and usages tools.
4. Passage tagging.
5. HTML compilation and browser execution through Tweego.

Preserve inherited generic Twee and StoryData checks. Do not add Harlowe macro, argument, variable, link, or general syntax validators. Treat computed targets and runtime values as dynamic.

Runtime debugging, watch mode, live reload, Story Map, other story formats, and general formatting are outside scope.

Preserve existing names and conventions when adapting supplied code, the plugin ID `twee.twee3-webstorm`, and applicable MIT notices.

## Development and verification

Use JDK 21 and the toolchain configuration specified by the plan. Plugin development uses the Gradle wrapper:

```sh
./gradlew check
./gradlew runIde
./gradlew verifyPluginProjectConfiguration
./gradlew buildPlugin
./gradlew verifyPluginStructure
./gradlew verifyPlugin
```

Use `gradlew.bat` on Windows. `runIde` launches a development sandbox, not a story; the current scaffold targets IntelliJ IDEA until the planned WebStorm configuration is implemented.

Run the smallest meaningful checks for each change. Add focused regression tests where behavior changes. Complete packaging and host verification before claiming release compatibility.

Report checks actually executed, their results, and any environment limitations. Static review or an available Gradle task does not establish a successful build.
