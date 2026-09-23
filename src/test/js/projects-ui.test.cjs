const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const script = fs.readFileSync(path.join(__dirname, '../../main/resources/public/projects-ui.js'), 'utf8');
const html = fs.readFileSync(path.join(__dirname, '../../main/resources/public/index.html'), 'utf8');

function node() {
  const classes = new Set();
  return {
    value: '', innerHTML: '', textContent: '', dataset: {}, listeners: {}, attrs: {},
    classList: { add: x => classes.add(x), remove: x => classes.delete(x),
      toggle: (x, on) => on ? classes.add(x) : classes.delete(x), contains: x => classes.has(x) },
    addEventListener(type, callback) { this.listeners[type] = callback; },
    setAttribute(key, value) { this.attrs[key] = value; },
    removeAttribute(key) { delete this.attrs[key]; },
    focus() { this.focused = true; },
    querySelectorAll() { return []; },
    querySelector() { return node(); },
    closest() { return this; },
    appendChild() {}, remove() {}, reportValidity() { return true; }, reset() {},
    parentElement: { setAttribute() {} }
  };
}

function harness(search = '') {
  const nodes = new Map();
  const get = id => { if (!nodes.has(id)) nodes.set(id, node()); return nodes.get(id); };
  const registryFields = new Map();
  const editorFields = new Map();
  for (const name of ['query', 'quarter', 'status', 'health', 'attention', 'sort', 'direction']) registryFields.set(name, node());
  for (const name of ['name', 'owner', 'status', 'health', 'deliveryModel', 'progress', 'startDate',
    'quarter', 'deadline', 'milestone', 'risk', 'dependency', 'kpiName', 'kpiTarget', 'summary']) editorFields.set(name, node());
  const registryForm = get('registry-filters');
  registryForm.elements = { namedItem: name => registryFields.get(name) };
  const editor = get('create-project-form');
  editor.elements = [...editorFields.values()];
  editor.elements.namedItem = name => editorFields.get(name);
  editor.reset = () => editorFields.forEach(input => { input.value = ''; });
  editor.querySelectorAll = () => [];
  const milestoneForm = get('milestone-form');
  const milestoneFields = new Map(['milestoneId', 'name', 'plannedDate', 'position', 'completed', 'completedDate']
    .map(key => [key, node()]));
  milestoneForm.elements = { namedItem: name => milestoneFields.get(name) };
  milestoneForm.querySelectorAll = () => [];
  milestoneForm.querySelector = () => node();
  get('project-detail-content').querySelector = selector => {
    if (selector === '#milestone-form') return milestoneForm;
    return get(selector.slice(1));
  };
  get('project-dialog').querySelectorAll = () => [get('project-dialog-cancel'), get('project-dialog-confirm')];
  const location = { pathname: '/', search };
  const history = {
    pushState(_data, _title, url) { location.search = url.includes('?') ? '?' + url.split('?')[1] : ''; },
    replaceState(_data, _title, url) { location.search = url.includes('?') ? '?' + url.split('?')[1] : ''; }
  };
  const window = { location, history, scrollY: 0, scrollTo() {}, addEventListener() {} };
  const state = { activeView: 'overview', deliveryModels: [], selectedProjectId: null };
  const elements = new Proxy({}, { get: (_target, key) => get(String(key)) });
  const document = { getElementById: get, createElement: node, activeElement: null };
  const calls = [];
  const context = vm.createContext({ window, state, elements, document, URLSearchParams, Date, Number,
    setTimeout, clearTimeout, requestAnimationFrame: callback => callback(),
    FormData: class {
      constructor(form) { this.values = Object.entries(form.data ?? {}); }
      [Symbol.iterator]() { return this.values[Symbol.iterator](); }
      entries() { return this.values[Symbol.iterator](); }
      get(name) { return this.values.find(([key]) => key === name)?.[1] ?? null; }
    },
    escapeHtml: x => String(x ?? '').replaceAll('&', '&amp;').replaceAll('<', '&lt;'),
    escapeAttr: x => String(x ?? '').replaceAll('"', '&quot;'),
    displayLabel: x => ({ red: 'Проблемный', yellow: 'Предупреждение', green: 'В норме',
      active: 'Активен', done: 'Завершён' })[x] ?? x,
    showView(view) { state.activeView = view; window.PMProjectsUI?.onView(view); },
    async loadBoard() {},
    async fetchJson(url, options) { calls.push({ url, options }); throw Error('offline'); }
  });
  vm.runInContext(script, context);
  return { ui: window.PMProjectsUI, context, state, window, get, calls, editor, editorFields,
    registryForm, registryFields, milestoneForm, milestoneFields };
}

const item = (overrides = {}) => ({ id: 'p1', name: 'Проект', summary: 'Описание', quarter: 'Q3',
  status: 'active', health: 'red', progress: null, deadline: '2026-10-20',
  assessment: { requiresAttention: true, attentionReasons: ['Красный health'] }, ...overrides });
const card = (overrides = {}) => ({ ...item(), version: 0, owner: 'Команда', deliveryModel: 'kanban',
  startDate: null, milestone: 'Legacy', risk: 'Риск', dependency: 'Зависимость',
  kpiName: 'KPI', kpiTarget: 'Цель', board: { viewAvailable: true, taskCount: 0 },
  milestoneCount: 0, milestones: [], ...overrides });
const formProject = (overrides = {}) => ({ name: 'Проект', owner: 'Команда', status: 'active',
  health: 'red', deliveryModel: 'kanban', progress: '', startDate: '', quarter: 'Q3',
  deadline: '2026-10-20', milestone: 'Legacy', risk: 'Риск', dependency: 'Зависимость',
  kpiName: 'KPI', kpiTarget: 'Цель', summary: 'Описание', ...overrides });
const snapshot = (items = [item()]) => ({ totalCount: items.length, filteredCount: items.length,
  filterOptions: { quarters: ['Q3'], statuses: ['active'], healthValues: ['red'] }, items });

test('registry URL and filters survive route restoration', () => {
  const h = harness('?view=projects&query=crm&quarter=Q3&health=red&sort=progress&direction=desc');
  assert.match(h.ui.registryUrl(), /query=crm/);
  assert.match(h.ui.registryUrl(), /quarter=Q3/);
  assert.match(h.ui.registryUrl(), /sort=progress/);
  assert.equal(h.registryFields.get('query').value, 'crm');
  assert.equal(h.registryFields.get('direction').value, 'desc');
});

test('null progress is unknown, zero is real zero, budget is absent from new surfaces', () => {
  const h = harness();
  assert.equal(h.ui.progress(null), 'Не указан');
  assert.equal(h.ui.progress(0), '0%');
  assert.doesNotMatch(html, /Указанный бюджет|name="budget"|data-field="budget"/);
  assert.doesNotMatch(script, /<dt>Бюджет|<span>Бюджет/);
});

test('registry renders server attention reasons, task count and unknown details', async () => {
  const h = harness();
  h.context.fetchJson = async url => url.includes('/card') ? card({ startDate: '2026-09-01', board: { taskCount: 3 } }) : snapshot();
  h.ui.onView('projects');
  await new Promise(resolve => setImmediate(resolve));
  assert.match(h.get('projects-list').innerHTML, /Красный health/);
  assert.match(h.get('projects-list').innerHTML, /3/);
  assert.match(h.get('projects-list').innerHTML, /01\.09\.2026/);
  assert.match(h.get('projects-list').innerHTML, /Не указан/);
});

test('progress sort uses existing registry API and keeps missing values last', () => {
  const h = harness();
  const sorted = h.ui.sortItems([item({ id: 'a', name: 'A', progress: null }),
    item({ id: 'b', name: 'B', progress: 0 }), item({ id: 'c', name: 'C', progress: 70 })],
  { sort: 'progress', direction: 'desc' });
  assert.equal(sorted.map(x => x.id).join(','), 'c,b,a');
});

test('successful PATCH sends only changed fields and expectedVersion', async () => {
  const h = harness();
  h.ui.startEditProject(card({ version: 4 }));
  h.editor.data = formProject({ name: 'Новое имя' });
  h.context.fetchJson = async (url, options) => {
    if (options?.method === 'PATCH') {
      assert.equal(url, '/api/projects/p1');
      const body = JSON.parse(options.body);
      assert.equal(body.expectedVersion, 4);
      assert.equal(body.name, 'Новое имя');
      assert.equal(body.budget, undefined);
      assert.equal(body.progress, undefined);
      return card({ name: 'Новое имя', version: 5 });
    }
    return snapshot();
  };
  await h.ui.submitProject({ preventDefault() {} });
  assert.match(h.get('project-detail-content').innerHTML, /Новое имя/);
  assert.match(h.get('project-detail-content').innerHTML, /Версия записи: 5/);
});

test('explicit clearing of nullable progress and startDate is sent as null', () => {
  const h = harness();
  const changed = h.ui.changedFields(card({ version: 2, progress: 30, startDate: '2026-09-01' }),
    { progress: null, startDate: null });
  assert.equal(changed.expectedVersion, 2);
  assert.equal(changed.progress, null);
  assert.equal(changed.startDate, null);
});

test('create POST omits budget and preserves an unspecified progress value', async () => {
  const h = harness();
  h.editor.data = formProject();
  let posted;
  h.context.fetchJson = async (url, options) => {
    if (options?.method === 'POST') { posted = JSON.parse(options.body); return { id: 'p1' }; }
    if (url.includes('/card')) return card();
    return snapshot();
  };
  await h.ui.submitProject({ preventDefault() {} });
  assert.equal(posted.budget, undefined);
  assert.equal(posted.progress, null);
  assert.equal(posted.startDate, null);
  assert.match(h.get('project-detail-content').innerHTML, /Карточка проекта/);
});

test('field validation errors remain attached to inputs', async () => {
  const h = harness();
  h.ui.startEditProject(card());
  h.editor.data = formProject({ name: 'Плохое имя' });
  h.context.fetchJson = async (_url, options) => {
    if (options?.method === 'PATCH') throw Object.assign(Error('Invalid'),
      { status: 400, fieldErrors: { name: 'Название недопустимо' } });
    return snapshot();
  };
  await h.ui.submitProject({ preventDefault() {} });
  assert.equal(h.editorFields.get('name').attrs['aria-invalid'], 'true');
  assert.match(h.get('submitMessage').textContent, /Invalid/);
});

test('409 exposes reload action without retrying the mutation', async () => {
  const h = harness();
  h.ui.startEditProject(card());
  h.editor.data = formProject({ name: 'Несохранённое имя' });
  let patches = 0;
  h.context.fetchJson = async (_url, options) => {
    if (options?.method === 'PATCH') { patches++; throw Object.assign(Error('conflict'), { status: 409 }); }
    return snapshot();
  };
  await h.ui.submitProject({ preventDefault() {} });
  assert.equal(patches, 1);
  assert.match(h.get('submitMessage').innerHTML, /Загрузить актуальную версию/);
});

test('milestone CRUD consumes each returned project version', async () => {
  const h = harness();
  let version = 0;
  const requests = [];
  h.context.fetchJson = async (url, options) => {
    if (!options) return card({ version, milestoneCount: version ? 1 : 0,
      milestones: version ? [{ id: 'm1', name: 'Точка', plannedDate: '2026-10-20', completed: false, completedDate: null, position: 0 }] : [] });
    requests.push({ url, method: options.method, body: options.body ? JSON.parse(options.body) : null });
    version++;
    return card({ version, milestoneCount: 1,
      milestones: [{ id: 'm1', name: 'Точка', plannedDate: '2026-10-20', completed: false, completedDate: null, position: 0 }] });
  };
  await h.ui.openCard('p1');
  h.milestoneForm.data = { milestoneId: '', name: 'Точка', plannedDate: '2026-10-20', position: '0', completedDate: '' };
  await h.milestoneForm.listeners.submit({ preventDefault() {}, currentTarget: h.milestoneForm });
  assert.equal(requests[0].body.expectedVersion, 0);
  h.milestoneForm.data.milestoneId = 'm1';
  await h.milestoneForm.listeners.submit({ preventDefault() {}, currentTarget: h.milestoneForm });
  assert.equal(requests[1].body.expectedVersion, 1);
  assert.equal(requests[1].method, 'PATCH');
  const button = { dataset: { id: 'm1', milestoneAction: 'delete' }, closest() { return this; },
    hasAttribute() { return false; }, focus() {} };
  h.get('project-detail-content').listeners.click({ target: button });
  await h.get('project-dialog-confirm').listeners.click();
  assert.equal(requests[2].method, 'DELETE');
  assert.match(requests[2].url, /expectedVersion=2/);
});

test('deletion-impact blocks confirmation when tasks or milestones exist', async () => {
  const h = harness();
  h.context.fetchJson = async () => ({ taskCount: 2, milestoneCount: 1, deletionAllowed: false });
  await h.ui.deleteProject(card(), node());
  assert.match(h.get('project-dialog-content').innerHTML, /Задачи: 2/);
  assert.match(h.get('project-dialog-content').innerHTML, /Контрольные точки: 1/);
  assert.equal(h.get('project-dialog-confirm').classList.contains('hidden'), true);
});

test('empty registry and initial error have distinct states', async () => {
  const h = harness();
  h.context.fetchJson = async () => snapshot([]);
  h.ui.onView('projects');
  await new Promise(resolve => setImmediate(resolve));
  assert.match(h.get('projects-list').innerHTML, /Проектов пока нет/);
  const failed = harness();
  failed.ui.onView('projects');
  await new Promise(resolve => setImmediate(resolve));
  assert.match(failed.get('projects-list').innerHTML, /Данные недоступны/);
});

test('failed registry refresh keeps previous rows visibly stale', async () => {
  const h = harness();
  h.context.fetchJson = async url => url.includes('/card') ? card() : snapshot();
  h.ui.onView('projects');
  await new Promise(resolve => setImmediate(resolve));
  h.context.fetchJson = async () => { throw Error('offline'); };
  h.ui.onView('projects');
  await new Promise(resolve => setImmediate(resolve));
  assert.match(h.get('projects-list').innerHTML, /Проект/);
  assert.match(h.get('project-navigation-message').innerHTML, /устаревший список/);
  assert.match(h.get('project-navigation-message').innerHTML, /Повторить/);
});

test('missing project and unknown values are explicit in the card', async () => {
  const h = harness();
  h.context.fetchJson = async () => { throw Object.assign(Error('missing'), { status: 404 }); };
  await h.ui.openCard('missing');
  assert.match(h.get('project-detail-content').innerHTML, /Проект не найден/);
  h.context.fetchJson = async () => card({ status: 'legacy', health: 'purple', progress: null,
    assessment: { dataQualityIssues: ['Done + red'] } });
  await h.ui.openCard('p1');
  assert.match(h.get('project-detail-content').innerHTML, /Неизвестный статус: legacy/);
  assert.match(h.get('project-detail-content').innerHTML, /Неизвестное состояние: purple/);
  assert.match(h.get('project-detail-content').innerHTML, /Не указан/);
  assert.match(h.get('project-detail-content').innerHTML, /Контрольных точек пока нет/);
});

test('late registry response cannot replace newer filter result', async () => {
  const h = harness();
  const pending = [];
  h.context.fetchJson = url => new Promise(resolve => pending.push({ url, resolve }));
  h.ui.onView('projects');
  h.registryFields.get('health').value = 'red';
  h.registryForm.listeners.change({ target: { name: 'health', value: 'red' } });
  pending[1].resolve(snapshot([item({ name: 'Новый' })]));
  await new Promise(resolve => setImmediate(resolve));
  pending[2].resolve(card());
  await new Promise(resolve => setImmediate(resolve));
  pending[0].resolve(snapshot([item({ name: 'Старый' })]));
  await new Promise(resolve => setImmediate(resolve));
  assert.match(h.get('projects-list').innerHTML, /Новый/);
  assert.doesNotMatch(h.get('projects-list').innerHTML, /Старый/);
});
