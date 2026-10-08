const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const {legacy, root} = require('./legacy.cjs');
const tm = require('vscode-textmate');
const onig = require('vscode-oniguruma');
const read = name => fs.readFileSync(path.join(root, name), 'utf8');
const plain = value => JSON.parse(JSON.stringify(value));

function snapshot(name, value) {
  const file = path.join(__dirname, 'fixtures', `${name}.json`);
  if (process.env.UPDATE_BASELINE === '1') fs.writeFileSync(file, JSON.stringify(value, null, 2) + '\n');
  assert.deepEqual(plain(value), JSON.parse(fs.readFileSync(file, 'utf8')));
}

test('frozen reference provenance', () => {
  const files = ['defs/grammar.json', 'defs/harlowe-3/grammar.json', 'src/parse-text.ts', 'src/diagnostics.ts', 'src/twee-project.ts', 'src/passage.ts', 'tests/Harlowe.tw', 'tests/Twee.tw', 'tests/ArrowsTest.tw'];
  snapshot('provenance', Object.fromEntries(files.map(file => [file, crypto.createHash('sha256').update(fs.readFileSync(path.join(root, file))).digest('hex')])));
});

test('actual inherited semantic tokens and passage ranges', async () => {
  for (const file of ['tests/Harlowe.tw', 'tests/Twee.tw', 'tests/ArrowsTest.tw', 'tools/characterization/fixtures/edge-cases.twee']) {
    const {tokens, passages} = await legacy(read(file)).parse();
    snapshot(path.basename(file) + '-semantic', {tokens, passages: passages.map(({name, tags, range, stringRange}) => ({name, tags, start: range.start, end: range.end, stringRange}))});
  }
});

test('TextMate scopes and ranges from the inherited Oniguruma grammar', async () => {
  await onig.loadWASM(fs.readFileSync(require.resolve('vscode-oniguruma/release/onig.wasm')));
  const registry = new tm.Registry({
    onigLib: Promise.resolve({createOnigScanner: sources => new onig.OnigScanner(sources), createOnigString: value => new onig.OnigString(value)}),
    loadGrammar: async scope => scope === 'source.harlowe-3.twee3' ? tm.parseRawGrammar(read('defs/harlowe-3/grammar.json'), 'grammar.json') : null,
  });
  try {
    const grammar = await registry.loadGrammar('source.harlowe-3.twee3');
    for (const file of ['tests/Harlowe.tw', 'tests/Twee.tw', 'tests/ArrowsTest.tw', 'tools/characterization/fixtures/edge-cases.twee']) {
      let state = tm.INITIAL;
      const lines = read(file).split('\n').map((line, index) => {
        const result = grammar.tokenizeLine(line, state);
        state = result.ruleStack;
        return {line: index, tokens: result.tokens};
      });
      if (file.endsWith('edge-cases.twee')) {
        assert(!lines[10].tokens.some(t => t.scopes.includes('punctuation.separator.twee3')), 'no-space header is not a TextMate boundary');
        assert(lines[27].tokens.every(t => t.scopes.includes('string.quoted.double.html')), 'unterminated string leaks over the later header in the legacy grammar');
        assert(lines[8].tokens.some(t => t.scopes.includes('meta.harlowe.macros.twee3') && t.startIndex >= 26), 'digit-bearing macro opener is recognized');
      }
      snapshot(path.basename(file) + '-scopes', lines);
    }
  } finally { registry.dispose(); }
});

test('header decoding, rejection, CRLF and no final newline', async () => {
  const {passages, tokens} = await legacy('::NoSpace\r\nbody\r\n:: Escaped\\[name\\] [one two] {"x":1}\r\n:: Bad {nope}\r\n:: Last').parse();
  assert.deepEqual(passages.map(p => p.name), ['NoSpace', 'Escaped[name]', 'Last']);
  assert.deepEqual(passages[1].tags, ['one', 'two']);
  assert.equal(passages[1].meta.x, 1);
  assert(tokens.some(t => t.tokenType === 'passageMeta'));
  assert.equal(passages.at(-1).range.start.line, 4);
  assert.equal(passages.at(-1).stringRange.end - passages.at(-1).stringRange.start, ':: Last'.length - 1);
});

test('only inherited editor checks, including disabled whitespace warning', async () => {
  const source = '::NoSpace\n(unknown: $undefined, nonsense) [[Missing]]\n:: StoryData\n{"ifid": }';
  const diags = await legacy(source).diagnostics();
  assert.deepEqual(diags.map(d => [d.code, d.severity]), [[0, 1], [1, 0]]);
  assert.deepEqual(plain(diags.map(d => ({start: d.range.start, end: d.range.end}))), [
    {start: {line: 0, character: 0}, end: {line: 0, character: 3}},
    {start: {line: 2, character: 0}, end: {line: 4, character: 0}},
  ]);
  assert.match(diags[0].message, /No space between Start token/);
  assert.match(diags[1].message, /Malformed StoryData JSON/);
  const disabled = await legacy(source, {'twee3LanguageTools.twee-3.warning.spaceAfterStartToken': false}).diagnostics();
  assert.deepEqual(disabled.map(d => d.code), [1]);
  assert.deepEqual(await legacy(':: Start\n(unknown: $undefined, nonsense) [[Missing]]\n:: Bad {nope}').diagnostics(), []);
  assert.deepEqual(await legacy('::\tStart\nbody').diagnostics(), []);
});

test('StoryData uses JSON.parse acceptance without editor schema validation', async () => {
  for (const body of ['{}', '[]', 'null', 'true', '123', '"text"', '{"x":1,"x":2}']) {
    assert.deepEqual(await legacy(`:: StoryData\n${body}`).diagnostics(), [], body);
  }
  for (const body of ['{"x":1,}', '{/*comment*/}', '\ufeff{}', '']) {
    assert.equal((await legacy(`:: StoryData\n${body}`).diagnostics())[0].code, 1, body);
  }
});

test('StoryData project notifications, UUID acceptance and switches', async () => {
  const valid = {ifid: '123e4567-e89b-42d3-a456-426614174000', format: 'Harlowe', 'format-version': '3.3.9'};
  const run = (data, switches) => legacy(`:: StoryData\n${JSON.stringify(data)}`, switches).validateStoryData();
  assert.deepEqual(await run(valid), []);
  assert.deepEqual(await run({...valid, ifid: '00000000-0000-0000-0000-000000000000'}), []);
  assert.deepEqual(await run({...valid, ifid: 'bad'}), ['Malformed StoryData: Invalid IFID!']);
  assert.deepEqual(await run({}), ['Malformed StoryData: IFID not found!', 'Malformed StoryData: Story Format name not found!', 'Malformed StoryData: Story Format version not found!']);
  assert.deepEqual(await run({...valid, ifid: ''}, {'twee3LanguageTools.twee-3.error.storyData.ifid': false}), []);
  // Keep format processing executable while disabling only the corresponding notification.
  assert.deepEqual(await run({...valid, format: ''}, {'twee3LanguageTools.twee-3.error.storyData.format': false}), []);
  assert.deepEqual(await run({...valid, 'format-version': ''}, {'twee3LanguageTools.twee-3.error.storyData.formatVersion': false}), []);
});
