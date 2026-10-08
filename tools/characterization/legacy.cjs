// Development-only adapter. Execute inherited sources, mocking only the VS Code host.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');
const root = path.resolve(__dirname, '../..');

function legacy(text, switches = {}) {
  const state = new Map();
  const notifications = [];
  const defaults = require(path.join(root, 'package.json')).contributes.configuration.properties;
  class Position { constructor(line, character) { Object.assign(this, {line, character}); } }
  class Range {
    constructor(a, b, c, d) {
      this.start = typeof a === 'number' ? new Position(a, b) : a;
      this.end = typeof a === 'number' ? new Position(c, d) : b;
      this[0] = this.start;
      this[1] = this.end;
    }
  }
  const vscode = {
    Position, Range,
    TreeItem: class {},
    TreeItemCollapsibleState: {None: 0},
    ThemeIcon: class {}, ThemeColor: class {}, SemanticTokensLegend: class {},
    DiagnosticSeverity: {Error: 0, Warning: 1},
    workspace: {
      getWorkspaceFolder: () => ({uri: {path: '/story'}}),
      getConfiguration: section => ({
        get: key => switches[`${section}.${key}`] ?? defaults[`${section}.${key}`]?.default,
        update: async () => {},
      }),
    },
    window: {showErrorMessage: message => notifications.push(message), showInformationMessage: () => {}},
  };
  const cache = new Map();
  function load(name) {
    if (cache.has(name)) return cache.get(name);
    const filename = path.join(root, 'src', `${name}.ts`);
    const module = {exports: {}};
    const code = ts.transpileModule(fs.readFileSync(filename, 'utf8'), {
      compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022},
    }).outputText;
    const mocks = {
      vscode,
      './utils': {normalizePath: value => value},
      './file-ops': {readFile: async () => text},
      './sugarcube-2/configuration': {LanguageID: 'twee3-sugarcube-2'},
      './sugarcube-2/macros': {diagnostics: () => { throw Error('Harlowe invoked SugarCube diagnostics'); }},
      './extension': {log: {info: () => {}}},
      uuid: require('uuid'),
    };
    const requireMock = id => {
      if (Object.hasOwn(mocks, id)) return mocks[id];
      if (id === './passage') return load('passage');
      throw Error(`Unmocked inherited dependency: ${id}`);
    };
    vm.runInThisContext(`(function(require,module,exports){${code}\n})`, {filename})(requireMock, module, module.exports);
    cache.set(name, module.exports);
    return module.exports;
  }
  const ctx = {workspaceState: {get: (key, fallback) => state.get(key) ?? fallback, update: async (key, value) => state.set(key, value)}};
  const document = {text, getText: () => text, uri: {path: '/story/input.twee'}, languageId: 'twee3-harlowe-3'};
  return {
    async parse() {
      const tokens = await load('parse-text').parseRawText(ctx, document);
      return {tokens, passages: state.get('passages')};
    },
    async diagnostics() {
      await this.parse();
      let result;
      await load('diagnostics').updateDiagnostics(ctx, document, {set: (_, value) => { result = value; }});
      return result;
    },
    async validateStoryData() {
      await this.parse();
      await load('twee-project').tweeProjectConfig(ctx);
      return notifications;
    },
  };
}
module.exports = {legacy, root};
