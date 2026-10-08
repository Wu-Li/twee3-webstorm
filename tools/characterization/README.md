# Inherited behavior baseline

Development-only characterization for chapter 01. Nothing here is a production plugin dependency. The original TypeScript, grammars and example files remain unchanged; their SHA-256 hashes are checked in `fixtures/provenance.json`. Baseline source commit: `77f9522b3addb141a54241509af418eb5be94b75` (implementation plan); inherited code commit: `8c7715183b3387292406295523ab54f6a058c062`.

Run from the repository root with Node.js 20 or newer:

```sh
npm ci --ignore-scripts --prefix tools/characterization
npm test --prefix tools/characterization
```

The separate lockfile pins TypeScript 5.6.2 and uuid 10.0.0 to the inherited root lockfile versions. TextMate 9.2.0 and Oniguruma 2.0.1 execute the actual grammar. The legacy UUID dependency is intentionally frozen for compatibility characterization; it is not shipped in the native plugin. Existing MIT notices in the repository apply to reused source material.

`legacy.cjs` transpiles and executes the original parser, Passage model, diagnostics and StoryData configuration functions. Its mocks supply VS Code ranges, document/state/configuration and notification APIs, not replacement parsing/validation logic. Configuration defaults are read from the extension manifest. Unexpected imports and Harlowe dispatch into SugarCube diagnostics fail immediately. Paths are normalized fixture paths, so this harness does not test filesystem or Windows path behavior.

Seven tests assert:

- Reference source hashes and grammar validity through actual loading.
- Semantic header tokens, passage names, tags and source ranges across the three inherited fixtures and the new edge fixture.
- TextMate scope names and token ranges from the real Oniguruma grammar.
- Header escaping, malformed-header rejection, CRLF and no final newline.
- Warning/error severity, ranges, message meaning, switches and absence of added Harlowe diagnostics.
- JSON.parse acceptance, including scalar values and rejection of trailing commas/comments/BOM/empty input, without schema validation.
- IFID/format notifications and disabled checks, including the legacy acceptance of the nil UUID.

## Observed quirks and limits

- Semantic headers accept no space or a tab after `::`; the TextMate passage boundary requires a literal space. This is captured, not corrected.
- Semantic parsing recognizes `script`/`stylesheet` within multi-tag headers. Grammar region patterns specify single tags; in the actual TextMate snapshots even the single-tag fixture lines remain in Harlowe scopes. Do not infer embedded activation merely from the regex text.
- Macro openers accept digits in names, while the macro-name color pattern excludes them. The edge fixture includes `foo1` alongside hyphen/underscore names.
- An unterminated quoted string can retain its scope across the later passage header in the inherited grammar. Semantic parsing still finds the later passage. Native recovery requirements are stronger: explicitly document any resulting parity difference.
- External host grammars (`source.js`, `source.css`, `source.json`, HTML) are deliberately unavailable in this isolated registry. Snapshots characterize the repository grammar's own scopes, not embedded token colors. Native embedded lexers and light/dark visual inspection remain chapter 04 acceptance gates.
- JSON exception wording varies by JavaScript engine, so tests check message meaning rather than freezing engine-specific text. The tests distinguish editor JSON syntax diagnostics from project field notifications; they do not introduce schema checks or endorse every inherited exception path.
- This adapter does not prove VS Code UI behavior, native PSI behavior, index behavior, a Gradle build, or IDE compatibility. Those have separate chapter gates.

## Snapshot review

Normal test runs never rewrite expectations. For a deliberately reviewed baseline change only:

```sh
UPDATE_BASELINE=1 npm test --prefix tools/characterization
git diff -- tools/characterization/fixtures
npm test --prefix tools/characterization
```

Do not regenerate snapshots just to silence a regression. Scope/range snapshots are comparison inputs for the Kotlin implementation; equivalence to native tokens requires an explicit mapping in chapter 04.

## Verified execution

7 October 2026: Node.js 24.19.0, clean lockfile installation, all seven tests passed. Native build/launch was not run in chapter 01. The available system Java was 17; chapter 02 must establish JDK 21 before claiming its required toolchain/host evidence.
