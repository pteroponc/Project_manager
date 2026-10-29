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
  'boardCardPayload', 'boardCardsUrl', 'boardCardUrl'];
const functions = names.map((name) => {
  const start = source.search(new RegExp('^(?:async )?function ' + name + '\\(', 'm'));
  assert.ok(start >= 0, name);
  return source.slice(start, source.indexOf('\n}', start) + 2);
}).join('\n');

function node() {
  const classes = new Set();
  const children = new Map();
  return { textContent: '', innerHTML: '', disabled: false, listeners: {},
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
    selectedProjectId: null, activeBoardModel: 'kanban', boardDrilledIn: false,
    boardRequest: 0, boardLoading: false, boardProjectSelectionRequired: false,
    currentBoard: null, createTaskContext: null, draggingBoardCardId: null };
  const calls = [];
  const messages = [];
  let confirmations = 0;
  const window = { scrollTo() {}, confirm() { confirmations++; return true; } };
  const context = vm.createContext({ state, elements, window, encodeURIComponent,
    escapeHtml: String, console: { error() {} },
    fetchJson: (url, options) => new Promise((resolve, reject) => calls.push({ url, options, resolve, reject })),
    renderProjectDashboards() {}, renderCreatedBoards() {}, renderBoardInsights() {},
    renderBoardColumns() {}, renderEmptyBoard() {},
    closeTaskModal() { state.selectedBoardCardId = null; },
    closeCreateTaskPanel() { state.createTaskContext = null; elements.createTaskForm.reset(); },
    setFormState(message) { messages.push(message); } });
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
    assert.deepEqual(h.messages, []);
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
