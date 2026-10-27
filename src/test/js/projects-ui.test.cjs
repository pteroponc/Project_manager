const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const script = fs.readFileSync(path.join(__dirname, '../../main/resources/public/projects-ui.js'), 'utf8');
const architectureScript = fs.readFileSync(path.join(__dirname, '../../main/resources/public/architecture-ui.js'), 'utf8');
const { architecture, missingArchitecture } = require('./fixtures/architecture.cjs');
const html = fs.readFileSync(path.join(__dirname, '../../main/resources/public/index.html'), 'utf8');

function node() {
  const classes = new Set();
  return {
    value: '', innerHTML: '', textContent: '', dataset: {}, listeners: {}, attrs: {},
    classList: { add: x => classes.add(x), remove: x => classes.delete(x),
      toggle: (x, on) => on ? classes.add(x) : classes.delete(x), contains: x => classes.has(x) },
    addEventListener(type, callback) { this.listeners[type] = callback; },
    setAttribute(key, value) { this.attrs[key] = value; },
    hasAttribute(key) { return Object.hasOwn(this.attrs, key); },
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
    state:null, movements:[],
    pushState(data, _title, url) { this.state=data;location.search = url.includes('?') ? '?' + url.split('?')[1] : ''; },
    replaceState(data, _title, url) { this.state=data;location.search = url.includes('?') ? '?' + url.split('?')[1] : ''; },
    go(delta) { this.movements.push(delta); }
  };
  const window = { location, history, scrollY: 0, scrollTo() {}, events: {}, addEventListener(type, callback) { this.events[type] = callback; } };
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
  vm.runInContext(architectureScript, context);
  vm.runInContext(script, context);
  return { ui: window.PMProjectsUI, architecture: window.PMArchitectureUI, context, state, window, get, calls, editor, editorFields,
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

test('dashboard summary opens the existing card and returns to dashboards', async () => {
  const h = harness();
  h.context.fetchJson = async () => card();
  await h.ui.openCard('p1', 'dashboards');
  assert.equal(h.state.activeView, 'project-card');
  assert.match(h.get('back-to-registry').textContent, /дашбордам/);
  h.get('back-to-registry').listeners.click();
  assert.equal(h.state.activeView, 'dashboards');
});

test('dashboard URL restores on load and browser history', () => {
  const h = harness('?view=dashboards');
  assert.equal(h.state.activeView, 'dashboards');
  h.state.activeView = 'project-card';
  h.window.location.search = '?view=dashboards';
  h.window.events.popstate({state:{pmRouteIndex:0}});
  assert.equal(h.state.activeView, 'dashboards');
});

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

function architectureTab(h, index) {
  h.get('architecture-content').listeners.click({target:{closest: selector => selector === '[data-architecture-tab]' ? {dataset:{architectureTab:String(index)}} : null}});
  return h.get('architecture-content').innerHTML;
}

test('architecture entry and return preserve every registry filter and use GET only', async () => {
  const h = harness('?view=projects&query=crm&quarter=Q3&status=active&health=red&attention=only&sort=progress&direction=desc');
  const calls = [];
  h.context.fetchJson = async (url, options) => { calls.push({url,options}); return url.endsWith('/architecture') ? architecture() : card(); };
  await h.ui.openCard('p1');
  assert.match(h.get('project-detail-content').innerHTML,/data-card-action="architecture"/);
  const target = node(); target.dataset.cardAction = 'architecture';
  await h.get('project-detail-content').listeners.click({target:{closest:()=>target}});
  assert.equal(h.state.activeView,'project-architecture');
  assert.match(h.window.location.search,/view=project-architecture/);
  await h.get('back-from-architecture').listeners.click();
  assert.equal(h.state.activeView,'project-card');
  for (const value of ['query=crm','quarter=Q3','status=active','health=red','attention=only','sort=progress','direction=desc']) assert.ok(h.window.location.search.includes(value));
  assert.equal(calls.filter(c=>c.url.endsWith('/architecture')).length,1);
  assert.ok(calls.every(c=>!c.options?.method || c.options.method==='GET'));
});

test('architecture renders the server assessment, all reasons, upcoming and accepted risks without recomputation', async () => {
  const h = harness();
  const data = architecture();
  data.assessment.status='READY'; data.assessment.statusLabel='Подпись, возвращённая сервером';
  h.context.fetchJson = async()=>data;
  await h.architecture.open('p1');
  const output=h.get('architecture-content').innerHTML;
  assert.match(output,/Подпись, возвращённая сервером/);
  for (const reason of data.assessment.reasons) assert.ok(output.includes(reason.message));
  assert.match(output,/Принятые высокие риски \(1\)/);
  assert.match(output,/Временный процесс до завершения пилота/);
  assert.match(output,/Согласование принятия не проверено/);
  assert.match(output,/27\.10\.2026/);
  assert.match(output,/23\.10\.2026/);
  assert.match(output,/Europe\/Moscow/);
  assert.match(output,/не является официальным допуском/);
  assert.match(output,/Компоненты<\/dt><dd>1/);
});

test('all six architecture sections render DTO fields and hide technical metadata', async()=>{
  const h=harness(); const data=architecture();
  data.risks[0].probability='LOW'; data.risks[0].impact='LOW'; data.risks[0].level='CRITICAL'; // Renderer must use level as supplied.
  h.context.fetchJson=async()=>data; await h.architecture.open('p1');
  const outputs=Array.from({length:6},(_,i)=>architectureTab(h,i));
  assert.match(outputs[1],/1\.0/); assert.match(outputs[1],/Управленческое описание решения\.<br>Вторая строка/);
  assert.match(outputs[1],/Промышленная/); assert.match(outputs[1],/Персональные/); assert.match(outputs[1],/UTC/);
  assert.match(outputs[2],/Java/); assert.match(outputs[2],/Серверное приложение/); assert.match(outputs[2],/Порядок<\/dt><dd>0/);
  assert.match(outputs[3],/OAuth 2\.0/); assert.match(outputs[3],/Команда каталога/);
  assert.match(outputs[4],/Согласование архитектуры/); assert.match(outputs[4],/Допуск к промышленной эксплуатации/);
  assert.match(outputs[5],/Уровень \(сервер\)<\/dt><dd>Критический/);
  for(const output of outputs) {
    assert.doesNotMatch(output.replace(/data-child-id="[^"]*"/g,''),/98123[7-9]|98124[0-2]|f071002a|cmp-test|risk-test|instanceId|expectedVersion|technical-version/);
    assert.doesNotMatch(output,/<form|data-edit|data-delete|Создать паспорт|Инициализировать|Сохранить/);
  }
});

test('risk actions select UUID after reorder and apply full response without GET; absent passport hides creation',async()=>{
  const {riskArchitecture}=require('./fixtures/architecture.cjs');const h=harness(),data=riskArchitecture(),calls=[],opened=[];data.risks.reverse();let accept;
  h.window.PMPassportEditor={allowNavigation:()=>true,startChild:(_card,kind,id,callback)=>{opened.push({kind,id});accept=callback;}};
  h.context.fetchJson=async(url,options)=>{calls.push({url,options});return data;};await h.architecture.open('p1');architectureTab(h,5);
  for(const row of data.risks){h.get('architecture-content').listeners.click({target:{closest:s=>s==='[data-child-kind]'?{dataset:{childKind:'risks',childId:row.id}}:null}});assert.deepEqual(opened.at(-1),{kind:'risks',id:row.id});}
  const result=riskArchitecture();result.risks[0].name='Ответ записи риска';accept(result);assert.match(h.get('architecture-content').innerHTML,/Ответ записи риска/);assert.equal(calls.length,1);
  h.context.fetchJson=async()=>missingArchitecture('p1');await h.architecture.open('p1');assert.doesNotMatch(architectureTab(h,5),/Добавить риск|data-child-kind/);
});

test('deletion selects every child UUID after reorder and uses full response, including absent passport, without GET',async()=>{
  const {riskArchitecture}=require('./fixtures/architecture.cjs');const h=harness(),data=riskArchitecture(),opened=[],calls=[];let accept;
  for(const kind of ['components','integrations','checks','risks'])data[kind].reverse();
  h.window.PMPassportEditor={allowNavigation:()=>true,startDeletion:(_card,kind,id,callback)=>{opened.push({kind,id});accept=callback;}};
  h.context.fetchJson=async(url,options)=>{calls.push({url,options});return data;};await h.architecture.open('p1');
  for(const kind of ['components','integrations','checks','risks'])for(const row of [data[kind][0],data[kind].at(-1)]){h.get('architecture-content').listeners.click({target:{closest:s=>s==='[data-architecture-remove]'?{dataset:{architectureRemove:kind,childId:row.id}}:null}});assert.deepEqual(opened.at(-1),{kind,id:row.id});}
  const response=riskArchitecture();response.components[0].name='Ответ DELETE';accept(response);assert.equal(calls.length,1);
  h.get('architecture-content').listeners.click({target:{closest:s=>s==='[data-architecture-remove]'?{dataset:{architectureRemove:'passport'}}:null}});assert.deepEqual(opened.at(-1),{kind:'passport',id:null});accept(missingArchitecture('p1'));assert.match(h.get('architecture-content').innerHTML,/Создать паспорт/);assert.doesNotMatch(h.get('architecture-content').innerHTML,/data-architecture-remove/);assert.equal(calls.length,1);
});

test('old deletion response cannot replace a new snapshot of the same or another project',async()=>{
  for(const nextId of ['p1','p2']){const h=harness();let accept;h.window.PMPassportEditor={allowNavigation:()=>true,startDeletion:(_d,_k,_i,callback)=>{accept=callback;}};h.context.fetchJson=async()=>architecture();await h.architecture.open('p1');h.get('architecture-content').listeners.click({target:{closest:s=>s==='[data-architecture-remove]'?{dataset:{architectureRemove:'components',childId:'cmp-test'}}:null}});h.context.fetchJson=async()=>architecture(nextId);await h.architecture.open(nextId);const before=h.get('architecture-content').innerHTML;accept(missingArchitecture('p1'));assert.equal(h.get('architecture-content').innerHTML,before);}
});

test('accepted HIGH/CRITICAL uses server level, justification, UNVERIFIED and all server reasons without recalculation',async()=>{
  const {riskArchitecture}=require('./fixtures/architecture.cjs');
  for(const level of ['HIGH','CRITICAL']){const h=harness(),data=riskArchitecture();Object.assign(data.risks[0],{probability:'LOW',impact:'LOW',level});Object.assign(data.assessment,{status:'ATTENTION_REQUIRED',statusLabel:'Оценка только сервера'});data.assessment.acceptedHighRisks[0].level=level;
    h.context.fetchJson=async()=>data;await h.architecture.open('p1');const summary=h.get('architecture-content').innerHTML;assert.match(summary,/Оценка только сервера/);assert.match(summary,/Согласование принятия не проверено/);assert.ok(summary.includes(data.risks[0].acceptanceJustification));for(const reason of data.assessment.reasons)assert.ok(summary.includes(reason.message));assert.match(summary,/не является официальным допуском/);
    const html=architectureTab(h,5);assert.match(html,new RegExp('Уровень \\(сервер\\)<\\/dt><dd>'+(level==='HIGH'?'Высокий':'Критический')));assert.match(html,/Согласование принятия не проверено/);assert.ok(html.includes(data.risks[0].acceptanceJustification));
  }
});

test('initialize is explicit only for passport with an empty set; partial sets cannot be completed',async()=>{
  for(const count of [0,1,9,10]){
    const h=harness(),calls=[],opened=[];const data=architecture();data.checks=data.checks.slice(0,count);
    h.window.PMPassportEditor={allowNavigation:()=>true,startChild:(_card,kind,id,accept)=>opened.push({kind,id,accept})};
    h.context.fetchJson=async(url,options)=>{calls.push({url,options});return data;};await h.architecture.open('p1');
    const html=architectureTab(h,4);assert.equal(html.includes('Создать набор проверок'),count===0);assert.equal(calls.length,1);assert.equal(calls[0].options,undefined);
    h.get('architecture-content').listeners.click({target:{closest:s=>s==='[data-child-kind]'?{dataset:{childKind:'checks'}}:null}});
    assert.equal(opened.length,count===0?1:0);assert.equal(calls.length,1);
    if(!count){const saved=architecture();opened[0].accept(saved);assert.doesNotMatch(h.get('architecture-content').innerHTML,/Создать набор проверок/);assert.match(h.get('architecture-content').innerHTML,/проверки \(10\)/);assert.equal(calls.length,1);}
  }
  const h=harness();h.context.fetchJson=async()=>missingArchitecture('p1');await h.architecture.open('p1');assert.doesNotMatch(architectureTab(h,4),/Создать набор проверок|data-child-kind/);
});

test('child editor entries select UUID after reordering, require a passport and consume a full response without GET',async()=>{
  const {childArchitecture}=require('./fixtures/architecture.cjs');
  const h=harness(),calls=[],opened=[];let accept;
  h.window.PMPassportEditor={allowNavigation:()=>true,startChild:(data,kind,id,callback)=>{opened.push({data,kind,id});accept=callback;}};
  const data=childArchitecture();data.components.reverse();data.integrations.reverse();
  h.context.fetchJson=async(url,options)=>{calls.push({url,options});return data;};await h.architecture.open('p1');
  for(const kind of ['components','integrations']) {
    const id=data[kind][1].id;
    h.get('architecture-content').listeners.click({target:{closest:s=>s==='[data-child-kind]'?{dataset:{childKind:kind,childId:id}}:null}});
    assert.equal(opened.at(-1).id,id);assert.equal(opened.at(-1).kind,kind);
    const response=childArchitecture();response[kind][0].name='Сохранённый полный ответ';accept(response);
    assert.match(h.get('architecture-content').innerHTML,/Сохранённый полный ответ/);
  }
  assert.equal(calls.length,1);
  h.context.fetchJson=async()=>missingArchitecture('p1');await h.architecture.open('p1');
  for(const tab of [2,3])assert.doesNotMatch(architectureTab(h,tab),/data-child-kind/);
  const count=opened.length;h.get('architecture-content').listeners.click({target:{closest:s=>s==='[data-child-kind]'?{dataset:{childKind:'components'}}:null}});assert.equal(opened.length,count);
});

test('late child response is ignored after a fresh read or a different project',async()=>{
  const {childArchitecture}=require('./fixtures/architecture.cjs');
  for(const nextId of ['p1','p2']) {
    const h=harness();let accept;h.window.PMPassportEditor={allowNavigation:()=>true,startChild:(_d,_k,_i,callback)=>{accept=callback;}};
    h.context.fetchJson=async()=>childArchitecture();await h.architecture.open('p1');
    h.get('architecture-content').listeners.click({target:{closest:s=>s==='[data-child-kind]'?{dataset:{childKind:'components'}}:null}});
    h.context.fetchJson=async()=>childArchitecture(nextId);await h.architecture.open(nextId);const before=h.get('architecture-content').innerHTML;
    accept(childArchitecture());assert.equal(h.get('architecture-content').innerHTML,before);
  }
});

test('architecture loading clears previous content and distinguishes missing passport from a missing project',async()=>{
  const h=harness(); let resolve;
  h.context.fetchJson=()=>new Promise(r=>{resolve=r;});
  const pending=h.architecture.open('p1');
  assert.match(h.get('architecture-content').innerHTML,/Загружаем/);
  assert.equal(h.get('project-architecture').attrs['aria-busy'],'true');
  resolve(architecture('p1',{passport:null,dataClasses:[],environments:[],components:[],integrations:[],checks:[],risks:[]})); await pending;
  assert.match(h.get('architecture-content').innerHTML,/Паспорт отсутствует/);
  assert.match(architectureTab(h,1),/ещё не создан/);
  assert.match(h.get('architecture-content').innerHTML,/Создать паспорт/);
  assert.doesNotMatch(h.get('architecture-content').innerHTML,/data-delete|Инициализировать/);
  h.context.fetchJson=async()=>{throw Object.assign(Error('SQL hidden'),{status:404});};
  await h.architecture.open('missing');
  assert.match(h.get('architecture-content').innerHTML,/Проект не найден/);
  assert.doesNotMatch(h.get('architecture-content').innerHTML,/SQL|Паспорт отсутствует/);
});

test('empty architecture lists do not initialize or create records',async()=>{
  const h=harness(); const calls=[];
  h.context.fetchJson=async(url,options)=>{calls.push({url,options}); return architecture('p1',{components:[],integrations:[],checks:[],risks:[]});};
  await h.architecture.open('p1');
  for (const [tabIndex,message] of [[2,'Компонентов пока нет'],[3,'Интеграций пока нет'],[4,'не инициализирует'],[5,'Рисков ИБ пока нет']]) assert.ok(architectureTab(h,tabIndex).includes(message));
  assert.equal(calls.length,1); assert.equal(calls[0].options,undefined);
});

test('architecture error is safe and retry fetches the same project without stale content',async()=>{
  const h=harness(); let calls=0;
  h.context.fetchJson=async url=>{assert.equal(url,'/api/projects/p1/architecture'); if(++calls===1) throw Object.assign(Error('<sql> C:/private JavaClass'),{status:500}); return architecture();};
  await h.architecture.open('p1');
  assert.match(h.get('architecture-content').innerHTML,/Не удалось загрузить/);
  assert.doesNotMatch(h.get('architecture-content').innerHTML,/private|JavaClass|<sql>/);
  await h.get('architecture-content').listeners.click({target:{closest:s=>s==='[data-architecture-retry]'?{}:null}});
  assert.match(h.get('architecture-content').innerHTML,/Приём заявок/);
  assert.equal(calls,2); assert.equal(h.get('project-architecture').attrs['aria-busy'],'false');
});

test('late architecture success, failure and finally cannot replace a newer project',async()=>{
  for(const rejectOld of [false,true]) {
    const h=harness(); const pending=[];
    h.context.fetchJson=()=>new Promise((resolve,reject)=>pending.push({resolve,reject}));
    const first=h.architecture.open('old'); const second=h.architecture.open('new');
    pending[1].resolve(architecture('new',{project:{id:'new',name:'Новый проект'}})); await second;
    const expected=h.get('architecture-content').innerHTML;
    if(rejectOld) pending[0].reject(Error('late')); else pending[0].resolve(architecture('old',{project:{id:'old',name:'Старый проект'}}));
    await first;
    assert.equal(h.get('architecture-content').innerHTML,expected);
    assert.equal(h.get('project-architecture').attrs['aria-busy'],'false');
  }
});

test('leaving architecture invalidates requests, including reopen of the same project',async()=>{
  const h=harness(); const pending=[];
  h.context.fetchJson=()=>new Promise(resolve=>pending.push(resolve));
  const first=h.architecture.open('p1');
  h.context.showView('overview');
  const second=h.architecture.open('p1');
  pending[0](architecture('p1',{project:{id:'p1',name:'Устаревший'}})); await first;
  assert.match(h.get('architecture-content').innerHTML,/Загружаем/);
  assert.equal(h.get('project-architecture').attrs['aria-busy'],'true');
  pending[1](architecture('p1',{project:{id:'p1',name:'Актуальный'}})); await second;
  assert.match(h.get('architecture-content').innerHTML,/Актуальный/);
});

test('late project card failures do not replace architecture navigation',async()=>{
  const h=harness(); let reject;
  h.context.fetchJson=url=>url.endsWith('/card')?new Promise((_r,j)=>{reject=j;}):Promise.resolve(architecture('p2'));
  const old=h.ui.openCard('p1'); await h.architecture.open('p2'); reject(Object.assign(Error('late'),{status:404})); await old;
  assert.equal(h.state.activeView,'project-architecture');
  assert.doesNotMatch(h.get('project-detail-content').innerHTML,/Проект не найден/);
});

test('architecture deep route and browser history preserve registry filters',async()=>{
  const h=harness('?query=crm&quarter=Q3');
  h.context.fetchJson=async()=>architecture('p2');
  h.window.location.search='?view=project-architecture&project=p2&query=crm&quarter=Q3';
  h.window.events.popstate(); await new Promise(resolve=>setImmediate(resolve));
  assert.equal(h.state.activeView,'project-architecture');
  assert.match(h.window.location.search,/query=crm/);
  assert.match(h.get('architecture-content').innerHTML,/Приём заявок/);
  h.window.location.search='?view=overview&query=crm'; h.window.events.popstate();
  assert.equal(h.state.activeView,'overview');
});

test('architecture escapes every free text surface, keeps dates date-only and never mutates the DTO',async()=>{
  const h=harness(); const data=architecture(); const attack='<img src=x onerror="attack()">&<script>attack()</script>';
  data.project.name=attack; data.project.owner=attack; data.passport.architect=attack; data.passport.solutionSummary=attack;
  data.components[0].name=attack; data.components[0].purpose=attack; data.integrations[0].name=attack; data.integrations[0].comment=attack;
  data.checks[0].owner=attack; data.checks[0].comment=attack; data.risks[0].name=attack; data.risks[0].acceptanceJustification=attack;
  data.assessment.statusLabel=attack; data.assessment.reasons[0].message=attack; data.assessment.acceptedHighRisks[0].justification=attack;
  const before=JSON.stringify(data); h.context.fetchJson=async()=>data; await h.architecture.open('p1');
  for(let i=0;i<6;i++) {const output=architectureTab(h,i); assert.doesNotMatch(output,/<img|<script/); assert.match(output,/&lt;img/);}
  assert.equal(JSON.stringify(data),before);
  assert.match(architectureTab(h,4),/20\.10\.2026/);
});

test('wrong-project architecture payload is rejected with a generic error',async()=>{
  const h=harness(); h.context.fetchJson=async()=>architecture('other'); await h.architecture.open('p1');
  assert.match(h.get('architecture-content').innerHTML,/Не удалось загрузить/);
  assert.doesNotMatch(h.get('architecture-content').innerHTML,/Приём заявок|Invalid architecture/);
});

test('history rollback preserves a dirty passport and waits for explicit discard even before rollback completes',async()=>{
  const h=harness();h.context.fetchJson=async url=>url.endsWith('/architecture')?architecture():card();
  await h.ui.openCard('p1');await h.architecture.open('p1');
  let continuation,blocked=true;
  h.window.PMPassportEditor={needsWarning:()=>blocked,allowNavigation:action=>{if(!blocked)return true;continuation=()=>{blocked=false;action();};return false;}};
  h.window.location.search='?view=project-card&project=p1';h.window.events.popstate({state:{pmRouteIndex:1}});
  assert.deepEqual(h.window.history.movements,[1]);assert.equal(h.state.activeView,'project-architecture');
  continuation();assert.deepEqual(h.window.history.movements,[1]);
  h.window.events.popstate({state:{pmRouteIndex:2}});assert.deepEqual(h.window.history.movements,[1,-1]);
  h.window.events.popstate({state:{pmRouteIndex:1}});await new Promise(resolve=>setImmediate(resolve));
  assert.equal(h.state.activeView,'project-card');
});

test('old editor response cannot replace another project or a newer read of the same project',async()=>{
  for(const nextId of ['p1','p2']) {
    const h=harness();let accept;
    h.window.PMPassportEditor={allowNavigation:()=>true,start:(_card,callback)=>{accept=callback;}};
    h.context.fetchJson=async()=>architecture();await h.architecture.open('p1');
    h.get('architecture-content').listeners.click({target:{closest:selector=>selector==='[data-passport-edit]'?{}:null}});
    h.context.fetchJson=async()=>architecture(nextId,{project:{id:nextId,name:'Новейший снимок'}});
    await h.architecture.open(nextId);const expected=h.get('architecture-content').innerHTML;
    accept(architecture('p1',{project:{id:'p1',name:'Устаревшая запись'}}));
    assert.equal(h.get('architecture-content').innerHTML,expected);
  }
});
