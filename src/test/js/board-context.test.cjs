const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '../../main/resources/public/app.js'), 'utf8');
const names = ['showView', 'invalidateBoardContext', 'isCurrentBoardRequest',
  'activeBoardContext', 'isCurrentBoardContext', 'openProjectBoard', 'onProjectDeleted',
  'loadProjectCatalog', 'loadBoard', 'renderBoardLanding', 'findBoardCard',
  'moveBoardCard', 'createBoardCard', 'deleteBoardCard', 'saveBoardCard',
  'boardCardPayload', 'boardCardsUrl', 'boardCardUrl', 'selectBoardProject',
  'renderBoardProjectPicker', 'createBoard', 'rememberBoardVisit',
  'loadCreatedBoards', 'saveCreatedBoards', 'adjacentColumnKey',
  'renderBoardColumns', 'renderBoardCard', 'matchesBoardFilters', 'renderBoardInsights'];
const functions = names.map((name) => {
  const start = source.search(new RegExp('^(?:async )?function ' + name + '\\(', 'm'));
  assert.ok(start >= 0, name);
  return source.slice(start, source.indexOf('\n}', start) + 2);
}).join('\n');

function node() {
  const classes = new Set();
  const children = new Map();
  const selectors = new Map();
  const childNodes = [];
  let html = '';
  return { textContent: '', get innerHTML() { return html; }, set innerHTML(value) { html = value; childNodes.length = 0; },
    disabled: false, listeners: {}, childNodes, selectors, dataset: {},
    appendChild(child) { childNodes.push(child); },
    value: '', setAttribute() {}, querySelectorAll: (selector) => selectors.get(selector) ?? [],
    classList: { add: (value) => classes.add(value), remove: (value) => classes.delete(value),
      toggle: (value, on) => on ? classes.add(value) : classes.delete(value),
      contains: (value) => classes.has(value) },
    querySelector(selector) { if (!children.has(selector)) children.set(selector, node()); return children.get(selector); },
    addEventListener(type, listener) { this.listeners[type] = listener; },
    reset() { this.resetCount = (this.resetCount ?? 0) + 1; }
  };
}

function board(projectId, model, cards = []) {
  return { projectId, projectName: projectId, deliveryModel: model, boardTitle: model,
    columns: [{ key: 'review', cards }, { key: 'done', cards: [] }] };
}

function card(id = 'card-a') {
  return { id, title: id, description: 'Description', owner: 'QA', dueDate: '2026-10-20',
    columnKey: 'review', priority: 'medium', labels: '', estimate: 2, blocked: false };
}

function formData(overrides = {}) {
  const values = { title: 'Updated', description: 'Description', owner: 'QA',
    dueDate: '2026-10-20', columnKey: 'review', priority: 'medium', estimate: '2',
    labels: '', ...overrides };
  return { entries: () => Object.entries(values)[Symbol.iterator](), has: (key) => key in values };
}

function harness() {
  const elements = new Proxy({ viewSections: [], viewButtons: [] }, {
    get(target, key) { return target[key] ??= node(); }
  });
  const state = { activeView: 'project-card', projects: [{ id: 'A', name: 'A' }, { id: 'B', name: 'B' }],
    createdBoards: [], projectsRequest: 0, boardCatalogLoaded: false, boardCatalogLoading: false, boardCatalogError: false,
    boardFilters: { query: '', priority: 'all', blocked: false },
    selectedProjectId: null, activeBoardModel: 'kanban', boardDrilledIn: false,
    boardRequest: 0, boardLoading: false, boardProjectSelectionRequired: false,
    currentBoard: null, createTaskContext: null, draggingBoardCardId: null };
  const calls = [];
  const messages = [];
  let confirmations = 0;
  const window = { scrollTo() {}, confirm() { confirmations++; return true; } };
  const context = vm.createContext({ state, elements, window, encodeURIComponent,
    escapeHtml: String, escapeAttr: String, console: { error() {} },
    BOARDS_STORAGE_KEY: 'project-manager-created-boards',
    localStorage: { getItem: () => null, setItem() {} },
    document: { createElement: node }, isTaskOverdue: () => false, formatDate: String,
    fetchJson: (url, options) => new Promise((resolve, reject) => calls.push({ url, options, resolve, reject })),
    renderProjectDashboards() {}, renderCreatedBoards() {}, renderBoardInsights() {},
    renderBoardColumns() {}, renderEmptyBoard() {},
    closeTaskModal() { state.selectedBoardCardId = null; },
    closeCreateTaskPanel() { state.createTaskContext = null; elements.createTaskForm.reset(); },
    setFormState(message) { messages.push(message); },
    setBoardMessage(message) { messages.push(message); } });
  vm.runInContext(functions, context);
  return { context, state, elements, calls, messages, window,
    get confirmations() { return confirmations; } };
}

test('A/Kanban then B/Scrum applies B before late A and keeps one context', async () => {
  const h = harness();
  const first = h.context.openProjectBoard('A', 'kanban');
  const second = h.context.openProjectBoard('B', 'scrum');
  assert.equal(h.state.currentBoard, null);
  assert.equal(h.elements.createTaskButton.disabled, true);
  h.calls[1].resolve(board('B', 'scrum'));
  await second;
  h.calls[0].resolve(board('A', 'kanban'));
  await first;
  assert.equal(h.state.currentBoard.projectId, 'B');
  assert.equal(h.state.selectedProjectId, 'B');
  assert.equal(h.state.activeBoardModel, 'scrum');
  assert.equal(h.elements.boardTitle.textContent, 'B - scrum');
});

test('rapid model switch of one project ignores older response and error', async () => {
  const h = harness();
  const first = h.context.openProjectBoard('A', 'kanban');
  const second = h.context.openProjectBoard('A', 'waterfall');
  h.calls[1].resolve(board('A', 'waterfall')); await second;
  h.calls[0].reject(Error('old failure')); await first;
  assert.equal(h.state.currentBoard.deliveryModel, 'waterfall');
  assert.equal(h.state.activeBoardModel, 'waterfall');
  assert.doesNotMatch(h.elements.boardColumns.innerHTML, /Не удалось/);
});

test('reopening the same project and model invalidates the first request', async () => {
  const h = harness();
  const first = h.context.openProjectBoard('A', 'kanban');
  const second = h.context.openProjectBoard('A', 'kanban');
  h.calls[1].resolve({ ...board('A', 'kanban'), boardTitle: 'New' }); await second;
  h.calls[0].resolve({ ...board('A', 'kanban'), boardTitle: 'Old' }); await first;
  assert.equal(h.elements.boardTitle.textContent, 'A - New');
});

test('leaving boards suppresses late success and late failure', async () => {
  for (const fails of [false, true]) {
    const h = harness();
    const pending = h.context.openProjectBoard('A', 'kanban');
    h.context.showView('projects');
    const before = h.elements.boardColumns.innerHTML;
    if (fails) h.calls[0].reject(Error('offline'));
    else h.calls[0].resolve(board('A', 'kanban'));
    await pending;
    assert.equal(h.state.currentBoard, null);
    assert.equal(h.state.activeView, 'projects');
    assert.equal(h.elements.boardColumns.innerHTML, before);
    assert.deepEqual(h.messages.filter(Boolean), []);
  }
});

test('deleted selected project clears board and never auto-selects another', async () => {
  const h = harness();
  const opened = h.context.openProjectBoard('A', 'kanban');
  h.calls[0].resolve(board('A', 'kanban', [card()])); await opened;
  h.context.onProjectDeleted('A');
  assert.equal(h.state.currentBoard, null);
  assert.equal(h.state.selectedProjectId, null);
  assert.equal(h.state.boardDrilledIn, false);
  assert.equal(h.state.boardProjectSelectionRequired, true);
  assert.match(h.elements.boardColumns.innerHTML, /Открыть проекты/);
  h.calls[1].resolve([{ id: 'B', name: 'B' }]);
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(h.state.selectedProjectId, null);
  assert.equal(h.calls.length, 2);
});

test('project deletion invalidates a pending board GET and its late error', async () => {
  const h = harness();
  const pending = h.context.openProjectBoard('A', 'kanban');
  h.context.onProjectDeleted('A');
  h.calls[0].reject(Object.assign(Error('missing'), { status: 404 }));
  await pending;
  assert.equal(h.state.selectedProjectId, null);
  assert.equal(h.state.currentBoard, null);
  assert.match(h.elements.boardColumns.innerHTML, /Открыть проекты/);
  h.calls[1].resolve([{ id: 'B', name: 'B' }]);
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(h.state.selectedProjectId, null);
});

test('loading another board blocks old-card create, edit, move and delete requests', async () => {
  const h = harness();
  const opened = h.context.openProjectBoard('A', 'kanban');
  h.calls[0].resolve(board('A', 'kanban', [card()])); await opened;
  const oldContext = h.context.activeBoardContext();
  const loading = h.context.openProjectBoard('B', 'scrum');
  await Promise.all([
    h.context.moveBoardCard('card-a', 'done', oldContext),
    h.context.saveBoardCard('card-a', formData(), oldContext),
    h.context.createBoardCard('review', formData(), oldContext),
    h.context.deleteBoardCard(card())
  ]);
  assert.equal(h.calls.length, 2);
  assert.equal(h.confirmations, 0);
  h.calls[1].resolve(board('B', 'scrum')); await loading;
});

test('late PUT completion and failure do not reload or message a different board', async () => {
  for (const fails of [false, true]) {
    const h = harness();
    const opened = h.context.openProjectBoard('A', 'kanban');
    h.calls[0].resolve(board('A', 'kanban', [card()])); await opened;
    const put = h.context.moveBoardCard('card-a', 'done');
    assert.equal(h.calls[1].options.method, 'PUT');
    assert.match(h.calls[1].url, /projects\/A\/board\/cards\/card-a\?model=kanban/);
    const next = h.context.openProjectBoard('B', 'scrum');
    if (fails) h.calls[1].reject(Error('old PUT failure'));
    else h.calls[1].resolve(card());
    await put;
    h.calls[2].resolve(board('B', 'scrum')); await next;
    assert.equal(h.calls.length, 3);
    assert.equal(h.state.currentBoard.projectId, 'B');
    assert.equal(h.messages.some((message) => /перенесена|old PUT failure/.test(message)), false);
  }
});

test('reopening the same board invalidates an earlier PUT and rejects a foreign card', async () => {
  const h = harness();
  const opened = h.context.openProjectBoard('A', 'kanban');
  h.calls[0].resolve(board('A', 'kanban', [card()])); await opened;
  await h.context.moveBoardCard('foreign-card', 'done');
  assert.equal(h.calls.length, 1);
  const put = h.context.moveBoardCard('card-a', 'done');
  const reopened = h.context.openProjectBoard('A', 'kanban');
  h.calls[2].resolve(board('A', 'kanban', [card()])); await reopened;
  h.calls[1].resolve(card()); await put;
  assert.equal(h.calls.length, 3);
  assert.equal(h.state.currentBoard.projectId, 'A');
  assert.equal(h.messages.some((message) => /перенесена/.test(message)), false);
});

test('late DELETE completion or failure leaves the new board and modal state untouched', async () => {
  for (const fails of [false, true]) {
    const h = harness();
    const opened = h.context.openProjectBoard('A', 'kanban');
    h.calls[0].resolve(board('A', 'kanban', [card()])); await opened;
    const deletion = h.context.deleteBoardCard(card());
    assert.equal(h.calls[1].options.method, 'DELETE');
    const next = h.context.openProjectBoard('B', 'scrum');
    if (fails) h.calls[1].reject(Error('old DELETE failure'));
    else h.calls[1].resolve(null);
    await deletion;
    h.calls[2].resolve(board('B', 'scrum')); await next;
    assert.equal(h.calls.length, 3);
    assert.equal(h.state.currentBoard.projectId, 'B');
    assert.equal(h.messages.some((message) => /удалена|old DELETE failure/.test(message)), false);
  }
});

test('return through navigation reloads the selected board; 404 requests explicit project choice', async () => {
  const h = harness();
  const opened = h.context.openProjectBoard('A', 'kanban');
  h.calls[0].resolve(board('A', 'kanban')); await opened;
  h.context.showView('project-card');
  assert.equal(h.state.currentBoard, null);
  h.context.showView('boards');
  assert.equal(h.calls.length, 2);
  h.calls[1].resolve(board('A', 'kanban')); await new Promise((resolve) => setImmediate(resolve));
  assert.equal(h.state.currentBoard.projectId, 'A');
  const missing = h.context.openProjectBoard('A', 'scrum');
  h.calls[2].reject(Object.assign(Error('missing'), { status: 404 })); await missing;
  assert.equal(h.state.selectedProjectId, null);
  assert.equal(h.state.currentBoard, null);
  assert.match(h.elements.boardColumns.innerHTML, /Открыть проекты/);
});

test('current network error is retryable and cannot expose old cards', async () => {
  const h = harness();
  const opened = h.context.openProjectBoard('A', 'kanban');
  h.calls[0].reject(Error('offline')); await opened;
  assert.equal(h.state.currentBoard, null);
  assert.equal(h.elements.createTaskButton.disabled, true);
  assert.match(h.elements.boardColumns.innerHTML, /Повторить/);
  h.elements.boardColumns.querySelector('[data-action="retry-board"]').listeners.click();
  assert.equal(h.calls.length, 2);
  h.calls[1].resolve(board('A', 'kanban')); await new Promise((resolve) => setImmediate(resolve));
  assert.equal(h.state.currentBoard.projectId, 'A');
});

test('server catalog requires explicit selection and every model opens with storage denied', async () => {
  const h = harness();
  h.context.localStorage = { getItem() { throw Error('denied'); }, setItem() { throw Error('quota'); } };
  assert.equal(h.context.loadCreatedBoards().length, 0);
  const catalog = h.context.loadProjectCatalog();
  h.calls[0].resolve([{ id: 'A', name: 'Server A' }, { id: 'B', name: 'Server B' }]); await catalog;
  assert.equal(h.state.selectedProjectId, null);
  assert.match(h.elements.boardProjectSelect.innerHTML, /Server A/);
  const selected = h.context.selectBoardProject('A');
  h.calls[1].resolve(board('A', 'kanban')); await selected;
  for (const model of ['scrum', 'waterfall', 'kanban']) {
    const opening = h.context.createBoard(model);
    const call = h.calls.at(-1);
    assert.match(call.url, new RegExp('model=' + model));
    call.resolve(board('A', model)); await opening;
    assert.equal(h.state.currentBoard.deliveryModel, model);
    assert.equal(h.elements.boardProjectSelect.value, 'A');
  }
  assert.equal(h.calls.some((call) => call.options?.method), false);
});

test('picker clears context and late GET cannot reopen a deselected project', async () => {
  const h = harness();
  const opening = h.context.selectBoardProject('A');
  await h.context.selectBoardProject('');
  h.calls[0].resolve(board('A', 'kanban')); await opening;
  assert.equal(h.state.currentBoard, null);
  assert.equal(h.state.selectedProjectId, null);
  assert.equal(h.elements.boardProjectSelect.value, '');
  await h.context.selectBoardProject('unknown');
  assert.equal(h.calls.length, 1);
});

test('project card can open a newly created server project before catalog refresh and switch models', async () => {
  const h = harness();
  const opening = h.context.openProjectBoard('new-project', 'kanban');
  h.calls[0].resolve(board('new-project', 'kanban')); await opening;
  assert.match(h.elements.boardProjectSelect.innerHTML, /new-project/);
  const switched = h.context.createBoard('scrum');
  assert.match(h.calls[1].url, /projects\/new-project\/board\?model=scrum/);
  h.calls[1].resolve(board('new-project', 'scrum')); await switched;
  assert.equal(h.state.currentBoard.projectId, 'new-project');
});

test('unassigned cards are visible, inspectable and move only after explicit selection', async () => {
  const h = harness();
  const opening = h.context.openProjectBoard('A', 'scrum');
  const response = board('A', 'scrum');
  response.unassignedColumns = [{ key: 'backlog', title: 'backlog', cards: [card('unmapped')] }];
  const before = JSON.stringify(response);
  h.calls[0].resolve(response); await opening;
  assert.equal(JSON.stringify(response), before);
  assert.equal(h.calls.length, 1);
  assert.equal(h.context.findBoardCard('unmapped').columnKey, 'backlog');
  const section = h.elements.boardColumns.childNodes.at(-1);
  assert.match(section.innerHTML, /Несопоставленные задачи: 1/);
  assert.match(section.innerHTML, /data-action="open-card" data-card-id="unmapped"/);
  assert.equal(h.elements.boardTaskCount.textContent, 1);
  assert.equal(h.context.adjacentColumnKey('backlog', 1), null);
  const select = node(); select.dataset.cardId = 'unmapped';
  h.elements.boardColumns.selectors.set('[data-action="move-card"]', [select]);
  h.context.renderBoardColumns();
  assert.match(select.innerHTML, /value="" disabled selected/);
  assert.equal(select.value, '');
  assert.equal(h.calls.length, 1);
  await h.context.saveBoardCard('unmapped', formData({ columnKey: '' }));
  assert.equal(h.calls.length, 1);
  select.value = 'review';
  const moved = select.listeners.change();
  assert.equal(h.calls[1].options.method, 'PUT');
  assert.equal(JSON.parse(h.calls[1].options.body).columnKey, 'review');
  h.calls[1].resolve(card('unmapped')); await new Promise((resolve) => setImmediate(resolve));
  h.calls[2].resolve(board('A', 'scrum', [card('unmapped')])); await moved;
  assert.equal(h.context.findBoardCard('unmapped').columnKey, 'review');
});

test('catalog error retains previous choices and retry recovers without selecting a board', async () => {
  const h = harness();
  const failed = h.context.loadProjectCatalog();
  h.calls[0].reject(Error('offline')); await failed;
  assert.equal(h.state.boardCatalogLoading, false);
  assert.match(h.elements.boardProjectMessage.textContent, /Не удалось/);
  assert.match(h.elements.boardProjectSelect.innerHTML, /value="A"/);
  const retry = h.context.loadProjectCatalog();
  h.calls[1].resolve([]); await retry;
  assert.match(h.elements.boardProjectMessage.textContent, /Проектов пока нет/);
  assert.equal(h.state.currentBoard, null);
});
