const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const app = fs.readFileSync(path.join(__dirname, '../../main/resources/public/app.js'), 'utf8');
const html = fs.readFileSync(path.join(__dirname, '../../main/resources/public/index.html'), 'utf8');
const css = fs.readFileSync(path.join(__dirname, '../../main/resources/public/styles.css'), 'utf8');
const names = ['showView', 'loadTrainSnapshot', 'renderReleaseTrains', 'applyReleaseTrainChange',
  'releaseTrainPayload', 'setReleaseTrainPending', 'setReleaseTrainEditorLoading',
  'updateReleaseTrainControls', 'resetReleaseTrainEditor',
  'openReleaseTrainEditor', 'handleReleaseTrainSubmit', 'deleteReleaseTrain'];
const definitions = names.map((name) => {
  const start = app.search(new RegExp('^(?:async )?function ' + name + '\\(', 'm'));
  assert.ok(start >= 0, name);
  return app.slice(start, app.indexOf('\n}', start) + 2);
}).join('\n');
const fieldsDefinition = app.slice(app.indexOf('const RELEASE_TRAIN_FIELDS'), app.indexOf('function releaseTrainPayload'));

const defaults = { name: 'RT Core', status: 'planning', cadence: '2 недели', quarter: 'Q4 2026',
  plannedReleaseDate: '2026-12-20', codeFreezeDate: '2026-12-01', qaFreezeDate: '2026-12-10',
  goLiveDate: '2026-12-21', capacityPoints: '60', committedPoints: '40', readiness: '50',
  blockedItems: '0', scope: 'Free text', risk: 'Risk', decision: 'Decision' };

function train(id = 'train-a', changes = {}) {
  return { id, ...defaults, capacityPoints: 60, committedPoints: 40, readiness: 50,
    blockedItems: 0, ...changes };
}

function snapshot(trains) {
  return { trains, totalCapacityPoints: trains.reduce((n, item) => n + item.capacityPoints, 0),
    totalCommittedPoints: trains.reduce((n, item) => n + item.committedPoints, 0),
    blockedItems: trains.reduce((n, item) => n + item.blockedItems, 0), averageReadiness: 50 };
}

function element() {
  const classes = new Set(['hidden']);
  return { textContent: '', innerHTML: '', disabled: false, classList: {
    add: (value) => classes.add(value), remove: (value) => classes.delete(value),
    contains: (value) => classes.has(value), toggle: (value, on) => on ? classes.add(value) : classes.delete(value)
  }, querySelectorAll: () => [], scrollIntoView() {} };
}

function harness(initialTrains = []) {
  const elements = new Proxy({ viewSections: [], viewButtons: [] }, {
    get(target, key) { return target[key] ??= element(); }
  });
  const form = element();
  form.fields = { ...defaults };
  const controls = new Map();
  form.elements = { namedItem(name) {
    if (!controls.has(name)) controls.set(name, { disabled: false,
      get value() { return form.fields[name]; }, set value(value) { form.fields[name] = String(value); }, focus() {} });
    return controls.get(name);
  } };
  form.querySelectorAll = () => Object.keys(defaults).map(name => form.elements.namedItem(name));
  form.reset = () => { form.fields = { ...defaults }; };
  elements.releaseTrainForm = form;
  const state = { activeView: 'release', trainsRequest: 0, trainEditorRequest: 0,
    editingReleaseTrainId: null, trainEditorLoading: false, trainMutationPending: false, releaseTrains: initialTrains,
    releaseTrainSnapshot: snapshot(initialTrains) };
  const calls = [];
  let confirmed = true;
  const context = vm.createContext({ elements, state, window: { scrollTo() {}, confirm: () => confirmed },
    FormData: class { constructor(target) { this.target = target; } entries() {
      return Object.entries(this.target.fields)[Symbol.iterator]();
    } },
    LABELS: { releaseStatus: { planning: '', boarding: '', frozen: '', qa: '', released: '' } },
    fetchJson: (url, options) => new Promise((resolve, reject) => calls.push({ url, options, resolve, reject })),
    declOfNum: () => 'поездов', capacityPercentFor: () => 50, releaseTrainTone: () => '',
    displayLabel: String, renderReleaseStage: () => '', formatDate: String,
    escapeHtml: (value) => String(value).replaceAll('<', '&lt;'), escapeAttr: String,
    encodeURIComponent, console: { error() {} } });
  vm.runInContext(fieldsDefinition + definitions, context);
  return { context, state, elements, form, calls, setConfirmed(value) { confirmed = value; } };
}

const tick = () => new Promise((resolve) => setImmediate(resolve));

test('create, view, edit, refresh and reopen use existing POST/GET/PUT APIs', async () => {
  const h = harness();
  const created = h.context.handleReleaseTrainSubmit({ preventDefault() {} });
  h.context.handleReleaseTrainSubmit({ preventDefault() {} });
  assert.equal(h.calls.length, 1);
  assert.equal(h.calls[0].options.method, 'POST');
  assert.equal(JSON.parse(h.calls[0].options.body).scope, 'Free text');
  h.calls[0].resolve(train()); await tick();
  assert.match(h.elements.releaseTrainList.innerHTML, /data-release-action="edit"/);
  assert.equal(h.state.releaseTrains.length, 1);
  h.calls[1].resolve(snapshot([train()])); await created;
  const opening = h.context.openReleaseTrainEditor('train-a');
  assert.equal(h.calls[2].url, '/api/release-trains/train-a');
  h.calls[2].resolve(train()); await opening;
  assert.equal(h.state.editingReleaseTrainId, 'train-a');
  assert.equal(h.form.fields.name, 'RT Core');
  h.form.fields.name = 'Updated';
  const updated = h.context.handleReleaseTrainSubmit({ preventDefault() {} });
  assert.equal(h.calls[3].options.method, 'PUT');
  assert.equal(h.calls[3].url, '/api/release-trains/train-a');
  h.calls[3].resolve(train('train-a', { name: 'Updated' })); await tick();
  assert.match(h.elements.releaseTrainList.innerHTML, /Updated/);
  h.calls[4].resolve(snapshot([train('train-a', { name: 'Updated' })])); await updated;
  assert.equal(h.state.editingReleaseTrainId, null);
  const reopened = h.context.openReleaseTrainEditor('train-a');
  h.calls[5].resolve(train('train-a', { name: 'Updated' })); await reopened;
  assert.equal(h.form.fields.name, 'Updated');
});

test('delete requires confirmation, blocks repeat clicks and refreshes the list', async () => {
  const h = harness([train()]);
  h.setConfirmed(false);
  await h.context.deleteReleaseTrain('train-a');
  assert.equal(h.calls.length, 0);
  h.setConfirmed(true);
  const removed = h.context.deleteReleaseTrain('train-a');
  await h.context.deleteReleaseTrain('train-a');
  assert.equal(h.calls.length, 1);
  assert.equal(h.calls[0].options.method, 'DELETE');
  h.calls[0].resolve(null); await tick();
  assert.equal(h.state.releaseTrains.length, 0);
  h.calls[1].resolve(snapshot([])); await removed;
  assert.match(h.elements.releaseTrainList.innerHTML, /пока нет/);
});

test('PUT errors retain every form field and distinguish 404, 409 and network failure', async () => {
  for (const status of [404, 409, undefined]) {
    const h = harness([train()]);
    const opening = h.context.openReleaseTrainEditor('train-a');
    h.calls[0].resolve(train()); await opening;
    h.form.fields.name = 'Unsaved';
    const saved = h.context.handleReleaseTrainSubmit({ preventDefault() {} });
    h.calls[1].reject(Object.assign(Error('offline'), { status })); await saved;
    assert.equal(h.form.fields.name, 'Unsaved');
    assert.equal(h.state.editingReleaseTrainId, 'train-a');
    assert.match(h.elements.releaseTrainMessage.textContent,
      status === 404 ? /не найден/ : status === 409 ? /Конфликт/ : /offline/);
    if (status === 404) h.calls[2].resolve(snapshot([]));
  }
});

test('DELETE errors preserve the current edit form and distinguish 404 and 409', async () => {
  for (const status of [404, 409]) {
    const h = harness([train()]);
    const opening = h.context.openReleaseTrainEditor('train-a');
    h.calls[0].resolve(train()); await opening;
    h.form.fields.scope = 'Unsaved scope';
    const removed = h.context.deleteReleaseTrain('train-a');
    h.calls[1].reject(Object.assign(Error('failed'), { status })); await removed;
    assert.equal(h.form.fields.scope, 'Unsaved scope');
    assert.equal(h.state.editingReleaseTrainId, 'train-a');
    assert.match(h.elements.releaseTrainMessage.textContent, status === 404 ? /отсутствует/ : /конфликта/);
    if (status === 404) h.calls[2].resolve(snapshot([]));
  }
});

test('validation matches required text, allowed status and integer ranges', async () => {
  const h = harness();
  for (const [field, value] of [['scope', '  '], ['status', 'unknown'],
    ['capacityPoints', '-1'], ['readiness', '101'], ['blockedItems', '1.5']]) {
    const previous = h.form.fields[field];
    h.form.fields[field] = value;
    await h.context.handleReleaseTrainSubmit({ preventDefault() {} });
    assert.equal(h.calls.length, 0);
    assert.equal(h.form.fields[field], value);
    h.form.fields[field] = previous;
  }
});

test('latest edit GET and snapshot win; leaving release suppresses a late detail response', async () => {
  const h = harness([train('a'), train('b')]);
  const a = h.context.openReleaseTrainEditor('a');
  const b = h.context.openReleaseTrainEditor('b');
  h.calls[1].resolve(train('b')); await b;
  h.calls[0].resolve(train('a')); await a;
  assert.equal(h.state.editingReleaseTrainId, 'b');
  const oldSnapshot = h.context.loadTrainSnapshot();
  const newSnapshot = h.context.loadTrainSnapshot();
  h.calls[3].resolve(snapshot([train('b')])); await newSnapshot;
  h.calls[2].resolve(snapshot([train('a')])); await oldSnapshot;
  assert.equal(h.state.releaseTrains[0].id, 'b');
  const late = h.context.openReleaseTrainEditor('a');
  h.context.showView('overview');
  h.calls[4].resolve(train('a')); await late;
  assert.equal(h.state.editingReleaseTrainId, 'b');
});

test('late mutation after leaving does not clear a form; local list survives snapshot failure', async () => {
  const h = harness([train()]);
  const opening = h.context.openReleaseTrainEditor('train-a');
  h.calls[0].resolve(train()); await opening;
  h.form.fields.name = 'Saved';
  const saving = h.context.handleReleaseTrainSubmit({ preventDefault() {} });
  h.context.showView('overview');
  h.calls[1].resolve(train('train-a', { name: 'Saved' })); await tick();
  h.calls[2].reject(Error('snapshot offline')); await saving;
  assert.equal(h.form.fields.name, 'Saved');
  assert.equal(h.state.releaseTrains[0].name, 'Saved');
  assert.match(h.elements.releaseTrainList.innerHTML, /Saved/);
  assert.doesNotMatch(h.elements.releaseTrainMessage.textContent, /Сохраняем/);
});

test('A -> B blocks submit and delete before B resolves, then submits only B', async () => {
  const h = harness([train('a'), train('b')]);
  const opened = h.context.openReleaseTrainEditor('a');
  h.calls[0].resolve(train('a')); await opened;
  h.form.fields.name = 'Keep my edit';
  const delayed = h.context.openReleaseTrainEditor('b');
  assert.equal(h.state.trainEditorLoading, true);
  assert.equal(h.state.trainMutationPending, false);
  assert.equal(h.elements.releaseTrainSubmit.disabled, true);
  assert.equal(h.form.elements.namedItem('name').disabled, true);
  assert.equal(h.elements.releaseTrainCancelEdit.disabled, false);
  await h.context.handleReleaseTrainSubmit({ preventDefault() {} });
  await h.context.deleteReleaseTrain('a');
  assert.equal(h.calls.length, 2);
  assert.equal(h.state.editingReleaseTrainId, 'a');
  assert.equal(h.form.fields.name, 'Keep my edit');
  h.calls[1].resolve(train('b', { name: 'Train B' })); await delayed;
  assert.equal(h.state.editingReleaseTrainId, 'b');
  assert.equal(h.form.fields.name, 'Train B');
  assert.equal(h.elements.releaseTrainSubmit.disabled, false);
  const saving = h.context.handleReleaseTrainSubmit({ preventDefault() {} });
  assert.equal(h.calls[2].url, '/api/release-trains/b');
  h.calls[2].reject(Error('offline')); await saving;
  assert.equal(h.form.fields.name, 'Train B');
});

test('B -> C applies C only and an old finally cannot unlock a newer GET', async () => {
  const h = harness();
  const b = h.context.openReleaseTrainEditor('b');
  const c = h.context.openReleaseTrainEditor('c');
  h.calls[0].reject(Error('old error')); await b;
  assert.equal(h.state.trainEditorLoading, true);
  assert.equal(h.elements.releaseTrainSubmit.disabled, true);
  h.calls[1].resolve(train('c', { name: 'Train C' })); await c;
  assert.equal(h.state.editingReleaseTrainId, 'c');
  const old = h.context.openReleaseTrainEditor('b');
  const newest = h.context.openReleaseTrainEditor('c');
  h.calls[3].resolve(train('c', { name: 'Latest C' })); await newest;
  h.calls[2].resolve(train('b')); await old;
  assert.equal(h.state.editingReleaseTrainId, 'c');
  assert.equal(h.form.fields.name, 'Latest C');
});

test('GET failure explicitly retains A, unlocks its form and allows retry B', async () => {
  for (const status of [404, undefined]) {
    const h = harness();
    const a = h.context.openReleaseTrainEditor('a');
    h.calls[0].resolve(train('a', { name: 'Train A' })); await a;
    h.form.fields.scope = 'Unsaved A';
    const b = h.context.openReleaseTrainEditor('b');
    h.calls[1].reject(Object.assign(Error('offline'), { status })); await b;
    assert.equal(h.state.trainEditorLoading, false);
    assert.equal(h.elements.releaseTrainSubmit.disabled, false);
    assert.equal(h.state.editingReleaseTrainId, 'a');
    assert.equal(h.form.fields.scope, 'Unsaved A');
    assert.match(h.elements.releaseTrainMessage.textContent, /Переключение не выполнено.*Train A/);
    if (status === 404) { h.calls[2].resolve(snapshot([])); await tick(); }
    const retry = h.context.openReleaseTrainEditor('b');
    h.calls.at(-1).resolve(train('b')); await retry;
    assert.equal(h.state.editingReleaseTrainId, 'b');
  }
});

test('cancel invalidates pending GET, restores create mode and ignores late success', async () => {
  const h = harness();
  const b = h.context.openReleaseTrainEditor('b');
  assert.equal(h.elements.releaseTrainCancelEdit.classList.contains('hidden'), false);
  h.context.resetReleaseTrainEditor();
  assert.equal(h.state.trainEditorLoading, false);
  assert.equal(h.elements.releaseTrainSubmit.disabled, false);
  h.calls[0].resolve(train('b')); await b;
  assert.equal(h.state.editingReleaseTrainId, null);
  assert.equal(h.form.fields.name, defaults.name);
});

test('leave during GET unlocks editor; reopening same ID uses a fresh generation', async () => {
  const h = harness();
  const old = h.context.openReleaseTrainEditor('b');
  h.context.showView('overview');
  assert.equal(h.state.trainEditorLoading, false);
  h.context.showView('release');
  const reopened = h.context.openReleaseTrainEditor('b');
  h.calls[0].resolve(train('b', { name: 'Old' })); await old;
  assert.equal(h.elements.releaseTrainSubmit.disabled, true);
  h.calls[1].resolve(train('b', { name: 'Reopened' })); await reopened;
  assert.equal(h.form.fields.name, 'Reopened');
  const repeated = h.context.openReleaseTrainEditor('b');
  const latest = h.context.openReleaseTrainEditor('b');
  h.calls[3].resolve(train('b', { name: 'Latest' })); await latest;
  h.calls[2].reject(Error('late repeated error')); await repeated;
  assert.equal(h.form.fields.name, 'Latest');
  assert.equal(h.state.trainEditorLoading, false);
});

test('cards expose touch and keyboard buttons with responsive layouts', () => {
  const h = harness([train()]);
  h.context.renderReleaseTrains(snapshot([train()]));
  assert.match(h.elements.releaseTrainList.innerHTML, /<button[^>]+type="button"[^>]+data-release-action="edit"/);
  assert.match(h.elements.releaseTrainList.innerHTML, /<button[^>]+type="button"[^>]+data-release-action="delete"/);
  assert.match(html, /id="release-train-cancel-edit"/);
  assert.match(css, /@media \(max-width: 520px\)[\s\S]*\.release-train-actions \.btn\s*\{\s*width: 100%/);
});
