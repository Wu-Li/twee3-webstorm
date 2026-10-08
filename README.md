# Twee3-webstorm

Native Twee and Harlowe 3 support for WebStorm, under development. Implementation chapters are in place. [CI at d455372](https://github.com/Wu-Li/twee3-webstorm/actions/runs/37821613335) passes native builds/tests, packaging and both configured binary verifiers. Installed-host and real compiler/browser acceptance remain pending; no release is claimed.

## Features

- Structural Twee editing, Harlowe 3 highlighting, and inherited generic Twee/StoryData checks.
- Scoped passage navigation across `.tw` and `.twee` files.
- Static Find Usages and hierarchy views for passages, macros and variables. Computed targets and custom bindings remain dynamic or candidate results.
- A Passages tool window with tag grouping and undoable batch tag editing.
- Tweego configurations, Build Twee HTML, a native execution console/Stop action, and browser launch after successful HTML compilation.

Configure a named story under **Settings | Twee stories**, including source roots, exclusions and output directory. That selection is shared by navigation, tagging, tracing and default builds. Compiler paths and format directories are local settings.

Install Tweego and Harlowe 3 separately, then follow [build/run setup](docs/tweego-run.md). **Tools | Build Twee HTML** compiles without launching a browser. A **Tweego** run configuration can select a fixed story and IDE-configured browser. Keep relative assets beside the output HTML; there is no automatic asset copier.

Only Harlowe 3 is supported by the implementation scope. Additional Harlowe syntax/argument validators, runtime debugging, watch/live reload, Story Map and general formatting are outside scope. Inherited VS Code/Vue sources remain as porting references; npm `build`/`serve` do not compile or run Twee stories.

## Configured targets, not verified support

| Target | Version/build | Verification status |
| --- | --- | --- |
| WebStorm, primary development host | 2025.3.6 / 253.33813.27 | Build/tests and binary verifier passed at `d455372`; installed-plugin UI smoke pending |
| IntelliJ IDEA with JavaScript | 2025.3.6.1 / 253.33813.55 | Binary verifier passed at `d455372`; installed-plugin UI smoke pending |

The plugin declares minimum build `253.33813.27` with no upper bound. This is configuration, not a compatibility guarantee for newer IDEs. No current-stable product release is claimed as tested; any additional intended release must be explicitly added and verified before being advertised.

## Development and verification

Use JDK 21, Gradle 9.3.0 through the wrapper, and the pinned Kotlin 2.3.20 compiler with language/API level 2.2. Wrapper regeneration is still pending as recorded in [Chapter 02](docs/chapters/02-toolchain.md).

```sh
npm ci --prefix tools/characterization
npm test --prefix tools/characterization
python3 -m unittest discover -s tools/performance -p 'test_*.py' -v
python3 -m unittest discover -s tools/packaging -p 'test_*.py' -v
./gradlew check verifyPluginProjectConfiguration buildPlugin verifyPluginStructure verifyPlugin
python3 tools/packaging/inspect_plugin.py build/distributions --report build/reports/package-inspection.json
./gradlew runIde
```

Use `gradlew.bat` on Windows. `runIde` starts a development IDE, not a story. The [CI workflow](.github/workflows/verify.yml) runs baseline/tooling tests and separate WebStorm/IDEA verifier jobs, retaining ZIPs and reports even after failures. CI artifacts are development candidates, not releases; no Marketplace publication is configured.

Install a successfully checked ZIP via the IDE's **Settings | Plugins | Install Plugin from Disk** in a fresh test profile, then follow [the smoke/performance protocol](docs/verification/chapter15-protocol.md). Review [release gates](docs/verification/release-gates.md) before merging or releasing. The [chapter tracker](docs/chapters/README.md) records actual progress and remaining verification.

## Attribution

The inherited language support and porting references retain the original MIT license, copyright (c) 2020 Cyrus Firheir. See [LICENSE](LICENSE) and [NOTICE](NOTICE); both are included in the native plugin resources. Tweego and Harlowe distributions are not bundled.
