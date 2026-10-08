# Build and run with Tweego

Native implementation is present; Gradle, real Tweego and WebStorm verification are still pending. This is a setup and acceptance checklist, not a tested compatibility claim.

1. Install Tweego and a Harlowe 3 story format locally. In **Settings | Twee stories**, configure the executable and format search directories. An empty compiler path uses PATH.
2. Configure a named story with its source roots/files, exclusions and output directory. Select it for navigation and builds. Include desired `.js` and `.css` inputs in those roots. Keep format installations and generated output outside the inputs.
3. In **Run | Edit Configurations**, add **Tweego**. Follow the selected story or choose a fixed story. A blank working directory uses the first story root (the containing directory for a file root). An override is relative to the project; the default output is `dist/index.html` relative to that working directory.
4. Leave the format ID blank to select the highest installed stable Harlowe 3 version, or enter an exact installed Harlowe 3 ID. Environment overrides are literal `NAME=value` entries, one per line, without shell quoting or expansion. An explicit `TWEEGO_PATH` replaces the inherited/configured value.
5. Select an IDE-configured browser or the IDE default. **Run** compiles once and opens the newly promoted HTML if “Open browser after successful build” is enabled. **Tools | Build Twee HTML** clones the selected Tweego configuration with browser launch disabled; with another configuration selected it builds the selected story using default Tweego options. It does not modify the saved run configuration.
6. Read compiler stdout, stderr, effective format and exit codes in the Run console. **Stop** cancels discovery/compilation. Failed or canceled builds never launch the previous HTML. Recognized `error: load …: line N:` and `warning: load …: line N:` diagnostics link to existing scoped source files; other output remains plain text.

## Relative assets

Assets are not copied or embedded automatically. Put an asset where the generated HTML's relative URL resolves. For `public/index.html` containing `assets/moon.svg`, keep the file at `public/assets/moon.svg`. Set the story's output directory exclusion to `public` (project-relative). Source JS/CSS that belong to the story are passed to Tweego for its normal bundling. Browser execution is a one-shot local-file launch; no server, watch mode, live reload or IDE runtime debugger is provided.

## Pending native acceptance fixture

Open `src/test/testData/run/story with spaces` as a WebStorm project. Define a story rooted at `source`, with output directory `public`. Create a Tweego run configuration with working directory `.` and output `public/index.html`. Use an installed Harlowe 3 format; exact patch equality with StoryData is not required.

- Run: `Arrival` must appear first, not `Start`; confirm the startup macro greeting and Visits count, then follow the link to `Across files` and back. Inspect `$greet` candidate tracing, `$visits` Reads/Writes, and entry/destination tags using the [Chapter 15 protocol](verification/chapter15-protocol.md).
- Confirm the purple top border (CSS), `window.tweeBuildFixtureLoaded === true` in browser developer tools (JS), and the moon image (relative SVG asset).
- Build HTML: confirm console output and a refreshed HTML file, with no browser launch.
- Persist/reopen the configuration; confirm story override, paths, format, environment, browser and launch preference survive.
- Repeat with missing compiler, unavailable format, malformed Twee, a deleted input and an invalid output. Confirm errors are visible and the browser is not opened.
- Build once successfully, change the source, and cancel a later long build. Confirm no stale launch, staging cleanup and preservation of the last successfully promoted HTML.
- Start two builds to the same output and confirm the conflict is reported. Repeat on Windows/macOS/Linux, including spaces and Unicode paths.

These checks have not been executed here. Record results in Chapters 14–16 before merge/release. Do not check generated fixture HTML into the repository.
