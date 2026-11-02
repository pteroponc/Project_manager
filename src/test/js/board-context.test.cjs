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
  'renderBoardColumns', 'renderBoardCard', 'matchesBoardFilters', 'renderBoardInsights',
  'canonicalBoardCards', 'boardOrderSnapshot', 'sameBoardOrder', 'boardOrderUrl',
  'refreshBoardAfterOrder', 'reorderBoardCard',
  'taskFormValues', 'taskDraftForm', 'captureTaskDraft', 'discardTaskDraft', 'restoreTaskDraft',
  'taskOperationBlocked', 'syncTaskControls', 'runTaskMutation', 'restoreTaskFocus',
  'handleTaskKeydown', 'startEditBoardCard', 'startCreateBoardCard', 'closeCreateTaskPanel',
  'openTaskModal', 'closeTaskModal'];
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
  const attrs = new Map();
  return { textContent: '', get innerHTML() { return html; }, set innerHTML(value) { html = value; childNodes.length = 0; },
    disabled: false, listeners: {}, childNodes, selectors, dataset: {}, elements: [],
    isConnected: true, tabIndex: 0, getClientRects: () => [1],
    focus() { this.focusCount = (this.focusCount ?? 0) + 1; }, scrollIntoView() {},
    contains(target) { return target === this || childNodes.includes(target); },
    appendChild(child) { childNodes.push(child); },
    value: '', setAttribute(name, value) { attrs.set(name, value); },
    getAttribute: name => attrs.get(name), hasAttribute: name => attrs.has(name), removeAttribute: name => attrs.delete(name), querySelectorAll: (selector) => selectors.get(selector) ?? [],
    classList: { add: (value) => classes.add(value), remove: (value) => classes.delete(value),
      toggle: (value, on) => on ? classes.add(value) : classes.delete(value),
      contains: (value) => classes.has(value) },
    querySelector(selector) { if (selectors.has(selector)) return selectors.get(selector); if (!children.has(selector)) children.set(selector, node()); return children.get(selector); },
    addEventListener(type, listener) { this.listeners[type] = listener; },
    reset() { this.resetCount = (this.resetCount ?? 0) + 1; this.elements.forEach(f => { f.value = ''; f.checked = false; }); }
  };
}

function board(projectId, model, cards = []) {
  return { projectId, projectName: projectId, deliveryModel: model, boardTitle: model,
    columns: [{ key: 'review', cards }, { key: 'done', cards: [] }],
    storedPositions: Object.fromEntries(cards.map((item, index) => [item.id, index])) };
}

function card(id = 'card-a') {
  return { id, title: id, description: 'Description', owner: 'QA', dueDate: '2026-10-20',
    columnKey: 'review', priority: 'medium', labels: '', estimate: 2, blocked: false, version: 0 };
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
    taskOperations: {}, taskDraft: null, taskConflict: null, createdBoards: [], projectsRequest: 0, boardCatalogLoaded: false, boardCatalogLoading: false, boardCatalogError: false,
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
    document: { createElement: node, activeElement: null }, cssEscape: String, isTaskOverdue: () => false, formatDate: String,
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


async function loadedBoard() {
  const h = harness();
  const ready = h.context.openProjectBoard('A', 'kanban');
  h.calls[0].resolve(board('A', 'kanban', [card()])); await ready;
  return h;
}

function draftFor(h, kind = 'edit') {
  const form = node();
  form.elements = [
    { name: 'title', type: 'text', value: 'Draft title' },
    { name: 'columnKey', type: 'select-one', value: 'review' },
    { name: 'blocked', type: 'checkbox', checked: false }
  ];
  if (kind === 'create') h.elements.createTaskForm = form;
  else {
    h.elements.boardColumns.selectors.set('[data-card-form="card-a"]', form);
    h.elements.boardColumns.querySelector('[data-card-id="card-a"]').selectors.set('form', form);
  }
  h.state.taskDraft = { kind, ...h.context.activeBoardContext(), cardId: kind === 'edit' ? 'card-a' : null,
    expectedVersion: kind === 'edit' ? 0 : null,
    baseline: { title: 'Original', columnKey: 'review', blocked: false },
    values: { title: 'Draft title', columnKey: 'review', blocked: false }, dirty: true };
  return form;
}

test('double create submit sends one POST and keeps pending through refresh', async () => {
  const h = await loadedBoard();
  const first = h.context.createBoardCard('review', formData());
  await h.context.createBoardCard('review', formData());
  assert.equal(h.calls.length, 2);
  assert.equal(h.elements.createTaskButton.disabled, true);
  h.calls[1].resolve(card()); await new Promise(setImmediate);
  assert.equal(h.calls[2].options, undefined);
  await h.context.createBoardCard('review', formData());
  assert.equal(h.calls.length, 3);
  h.calls[2].resolve(board('A', 'kanban', [card()])); await first;
  assert.equal(h.state.taskOperations.A, undefined);
  assert.equal(h.messages.at(-1), 'Задача создана');
});

test('PUT blocks another PUT, DELETE and POST until definitive error; form remains', async () => {
  const h = await loadedBoard(); const form = draftFor(h);
  const save = h.context.saveBoardCard('card-a', formData());
  await h.context.saveBoardCard('card-a', formData());
  await h.context.deleteBoardCard(card());
  await h.context.createBoardCard('review', formData());
  assert.equal(h.calls.length, 2);
  h.calls[1].reject(Object.assign(Error('Invalid'), { status: 400 })); await save;
  assert.equal(h.state.taskOperations.A, undefined);
  assert.equal(form.elements[0].value, 'Draft title');
  assert.equal(h.state.taskDraft.dirty, true);
  assert.match(h.messages.at(-1), /Введённые данные сохранены/);
});

test('409 keeps edit draft and original version, offers reread and never retries automatically', async () => {
  const h = await loadedBoard(); const form = draftFor(h);
  const save = h.context.saveBoardCard('card-a', formData());
  assert.equal(JSON.parse(h.calls[1].options.body).expectedVersion, 0);
  h.calls[1].reject(Object.assign(Error('Version conflict'), {
    status: 409, code: 'BOARD_CARD_VERSION_CONFLICT', currentVersion: 1
  }));
  await save;
  assert.equal(h.calls.length, 2);
  assert.equal(h.state.taskDraft.expectedVersion, 0);
  assert.equal(h.state.taskDraft.conflict, true);
  assert.equal(form.elements[0].value, 'Draft title');
  assert.equal(h.elements.taskReload.classList.contains('hidden'), false);
  assert.match(h.messages.at(-1), /Черновик сохранён/);
  const fresh = card(); fresh.version = 1; fresh.title = 'Other editor';
  const reread = h.context.loadBoard('A', 'kanban');
  h.calls[2].resolve(board('A', 'kanban', [fresh])); await reread;
  assert.equal(h.state.taskDraft.expectedVersion, 0);
  await h.context.saveBoardCard('card-a', formData());
  assert.equal(h.calls.length, 3);
});

test('conflict reread shows the server card beside the retained draft through board filters', async () => {
  const h = await loadedBoard(); const form = draftFor(h);
  h.state.taskDraft.conflict = true;
  h.state.boardFilters.query = 'not matching';
  const latest = card(); latest.version = 3; latest.title = 'Server title';
  h.state.currentBoard = board('A', 'kanban', [latest]);
  assert.equal(h.context.matchesBoardFilters(latest), true);
  h.context.restoreTaskDraft();
  const markup = h.elements.boardColumns.querySelector('[data-card-id="card-a"]').innerHTML;
  assert.match(markup, /Текущая задача, версия 3/);
  assert.match(markup, /Server title/);
  assert.equal(form.elements[0].value, 'Draft title');
  assert.equal(h.state.taskDraft.expectedVersion, 0);
  await h.context.saveBoardCard('card-a', formData());
  assert.equal(h.calls.length, 1);
  h.window.confirm = () => false;
  assert.equal(h.context.openProjectBoard('B', 'scrum'), false);
  assert.equal(h.state.taskDraft.values.title, 'Draft title');
});

test('deleted card recovery preserves the draft in a separate full-width area and disables save', async () => {
  const h = await loadedBoard(); const form = draftFor(h);
  h.state.taskDraft.originalCard = card();
  h.state.taskDraft.originalColumn = 'review';
  h.state.taskDraft.conflict = true;
  const before = { ...h.state.taskDraft.values };
  const reread = h.context.loadBoard('A', 'kanban');
  h.calls[1].resolve(board('A', 'kanban')); await reread;
  const recovered = h.elements.boardColumns.childNodes.at(-1);
  assert.match(recovered.className, /board-card-draft-recovery/);
  assert.equal(recovered.dataset.cardId, 'card-a');
  assert.equal(recovered.querySelector('[type="submit"]').disabled, true);
  assert.equal(JSON.stringify(h.state.taskDraft.values), JSON.stringify(before));
  assert.equal(form.elements[0].value, 'Draft title');
  assert.equal(h.state.taskDraft.cardMissing, true);
  assert.equal(h.messages.at(-1), 'Задача удалена другим пользователем. Ваш черновик сохранён для копирования. Сохранение недоступно');
  h.window.confirm = () => false;
  assert.equal(h.context.openProjectBoard('B', 'scrum'), false);
  assert.equal(h.state.taskDraft.values.title, 'Draft title');
});

test('version conflict keeps comparison message and existing edit layout', async () => {
  const h = await loadedBoard(); draftFor(h);
  h.state.taskDraft.conflict = true;
  const latest = card(); latest.version = 2;
  const reread = h.context.loadBoard('A', 'kanban');
  h.calls[1].resolve(board('A', 'kanban', [latest])); await reread;
  assert.match(h.messages.at(-1), /сравните его с текущими данными задачи/);
  assert.equal(h.state.taskDraft.cardMissing, false);
  assert.equal(h.elements.boardColumns.childNodes.some((item) => /board-card-draft-recovery/.test(item.className)), false);
});

test('removed card without a preceding version conflict still reports deletion and preserves the draft', async () => {
  const h = await loadedBoard(); const form = draftFor(h);
  h.state.taskDraft.originalCard = card();
  h.state.taskDraft.originalColumn = 'review';
  const reread = h.context.loadBoard('A', 'kanban');
  h.calls[1].resolve(board('A', 'kanban')); await reread;
  assert.equal(h.state.taskDraft.conflict, undefined);
  assert.equal(h.state.taskDraft.cardMissing, true);
  assert.equal(h.messages.at(-1), 'Задача удалена другим пользователем. Ваш черновик сохранён для копирования. Сохранение недоступно');
  assert.equal(form.elements[0].value, 'Draft title');
  assert.equal(h.elements.boardColumns.childNodes.at(-1).querySelector('[type="submit"]').disabled, true);
  assert.match(h.elements.boardColumns.querySelector('[data-card-id="card-a"]').innerHTML, /Задача удалена/);
});

test('recovered draft layout spans Kanban columns and headings keep whole words', () => {
  const css = fs.readFileSync(path.join(__dirname, '../../main/resources/public/styles.css'), 'utf8');
  assert.match(css, /\.board-card-draft-recovery\s*\{[^}]*grid-column:\s*1\s*\/\s*-1/);
  assert.match(css, /\.board-card-draft-recovery \.board-card-form\s*\{[^}]*grid-template-columns:\s*repeat\(2/);
  assert.match(css, /@media \(max-width:\s*520px\)\s*\{\s*\.board-card-draft-recovery \.board-card-form\s*\{[^}]*grid-template-columns:\s*minmax\(0,\s*1fr\)/);
  assert.match(css, /\.board-column h3\s*\{[^}]*overflow-wrap:\s*normal;[^}]*word-break:\s*normal/);
  assert.match(css, /@media \(min-width:\s*781px\) and \(max-width:\s*1100px\)\s*\{\s*\.board-column header\s*\{[^}]*flex-wrap:\s*wrap/);
});

test('stale move sends the read version and requires a new read before a fresh move', async () => {
  const h = await loadedBoard();
  const move = h.context.moveBoardCard('card-a', 'done');
  assert.equal(JSON.parse(h.calls[1].options.body).expectedVersion, 0);
  h.calls[1].reject(Object.assign(Error('Conflict'), {
    status: 409, code: 'BOARD_CARD_VERSION_CONFLICT', currentVersion: 1
  })); await move;
  assert.equal(h.calls.length, 2);
  assert.equal(h.elements.taskReload.classList.contains('hidden'), false);
  const latest = card(); latest.version = 1; latest.title = 'Other editor';
  const reread = h.context.loadBoard('A', 'kanban');
  h.calls[2].resolve(board('A', 'kanban', [latest])); await reread;
  const freshMove = h.context.moveBoardCard('card-a', 'done');
  assert.equal(JSON.parse(h.calls[3].options.body).expectedVersion, 1);
  h.calls[3].reject(Object.assign(Error('Invalid'), { status: 400 })); await freshMove;
});

test('network-uncertain edit cannot retry blindly and a stale explicit retry gets 409', async () => {
  const h = await loadedBoard(); const form = draftFor(h);
  const save = h.context.saveBoardCard('card-a', formData());
  h.calls[1].reject(Error('connection lost')); await save;
  assert.equal(h.state.taskOperations.A.phase, 'unknown');
  await h.context.saveBoardCard('card-a', formData());
  assert.equal(h.calls.length, 2);
  const latest = card(); latest.version = 1; latest.title = 'Committed remotely';
  const reread = h.context.loadBoard('A', 'kanban');
  h.calls[2].resolve(board('A', 'kanban', [latest])); await reread;
  assert.equal(form.elements[0].value, 'Draft title');
  assert.equal(h.state.taskDraft.expectedVersion, 0);
  const retry = h.context.saveBoardCard('card-a', formData());
  assert.equal(JSON.parse(h.calls[3].options.body).expectedVersion, 0);
  h.calls[3].reject(Object.assign(Error('Conflict'), {
    status: 409, code: 'BOARD_CARD_VERSION_CONFLICT'
  })); await retry;
  assert.equal(h.state.taskDraft.conflict, true);
  assert.equal(form.elements[0].value, 'Draft title');
});

test('DELETE repeat is blocked before a second confirmation and request', async () => {
  const h = await loadedBoard();
  const first = h.context.deleteBoardCard(card());
  await h.context.deleteBoardCard(card());
  assert.equal(h.confirmations, 1); assert.equal(h.calls.length, 2);
  h.calls[1].reject(Object.assign(Error('Not found'), { status: 404 })); await first;
  assert.equal(h.state.taskOperations.A, undefined);
});

test('unknown mutation outcome requires successful reread, failed reread cannot unblock', async () => {
  const h = await loadedBoard(); draftFor(h, 'create');
  const first = h.context.createBoardCard('review', formData());
  h.calls[1].reject(Error('connection lost')); await first;
  assert.equal(h.state.taskOperations.A.phase, 'unknown');
  await h.context.createBoardCard('review', formData()); assert.equal(h.calls.length, 2);
  const reread = h.context.loadBoard('A', 'kanban');
  h.calls[2].reject(Error('offline')); await reread;
  assert.equal(h.state.taskOperations.A.phase, 'unknown');
  assert.equal(h.state.taskDraft.values.title, 'Draft title');
  const retry = h.context.loadBoard('A', 'kanban');
  h.calls[3].resolve(board('A', 'kanban', [card()])); await retry;
  assert.equal(h.state.taskOperations.A, undefined);
  assert.equal(h.elements.createTaskForm.elements[0].value, 'Draft title');
});

test('confirmed write plus refresh failure reports success separately and blocks blind retry', async () => {
  const h = await loadedBoard();
  const save = h.context.saveBoardCard('card-a', formData());
  h.calls[1].resolve(card()); await new Promise(setImmediate);
  h.calls[2].reject(Error('refresh unavailable')); await save;
  assert.match(h.messages.at(-1), /Задача обновлена.*Не удалось обновить доску/);
  assert.equal(h.state.taskOperations.A.phase, 'unknown');
});

test('old operation cannot clear the pending operation of another project', async () => {
  const h = await loadedBoard();
  const a = h.context.saveBoardCard('card-a', formData());
  const next = h.context.openProjectBoard('B', 'scrum');
  h.calls[2].resolve(board('B', 'scrum', [card('card-b')])); await next;
  const b = h.context.saveBoardCard('card-b', formData());
  const operation = h.state.taskOperations.B;
  h.calls[1].resolve(card()); await a;
  assert.equal(h.state.taskOperations.B, operation);
  assert.equal(h.elements.createTaskButton.disabled, true);
  h.calls[3].reject(Object.assign(Error('Invalid'), { status: 400 })); await b;
});

test('reopen while pending cannot reconcile an unfinished mutation', async () => {
  const h = await loadedBoard();
  const save = h.context.saveBoardCard('card-a', formData());
  const reopened = h.context.openProjectBoard('A', 'kanban');
  h.calls[2].resolve(board('A', 'kanban', [card()])); await reopened;
  assert.equal(h.state.taskOperations.A.phase, 'pending');
  h.calls[1].resolve(card()); await save;
  assert.equal(h.state.taskOperations.A.phase, 'unknown');
  assert.equal(h.elements.createTaskButton.disabled, true);
});

test('filter rerender preserves inline draft even when filter excludes its original card', async () => {
  const h = await loadedBoard(); const form = draftFor(h);
  h.state.boardFilters.query = 'no matching task';
  h.context.renderBoardColumns();
  assert.equal(h.context.matchesBoardFilters(card()), true);
  assert.equal(form.elements[0].value, 'Draft title');
  assert.equal(h.state.taskDraft.dirty, true);
});

test('same board refresh restores edit fields and binds draft to new generation', async () => {
  const h = await loadedBoard(); const form = draftFor(h);
  const oldRequest = h.state.taskDraft.request;
  const refresh = h.context.loadBoard('A', 'kanban');
  h.calls[1].resolve(board('A', 'kanban', [card()])); await refresh;
  assert.equal(form.elements[0].value, 'Draft title');
  assert.ok(h.state.taskDraft.request > oldRequest);
  assert.equal(h.state.taskDraft.projectId, 'A');
});

test('dirty project switch and navigation cancellation leave context and draft untouched', async () => {
  const h = await loadedBoard(); draftFor(h);
  h.window.confirm = () => false;
  assert.equal(h.context.openProjectBoard('B', 'scrum'), false);
  assert.equal(h.context.showView('projects'), false);
  assert.equal(h.state.selectedProjectId, 'A'); assert.equal(h.calls.length, 1);
  assert.equal(h.state.taskDraft.values.title, 'Draft title');
  h.window.confirm = () => true;
  const next = h.context.openProjectBoard('B', 'scrum');
  assert.equal(h.state.taskDraft, null);
  h.calls[1].resolve(board('B', 'scrum')); await next;
});

test('Escape asks before discarding an edit, respects cancellation, then closes it', async () => {
  const h = await loadedBoard(); draftFor(h);
  h.elements.taskModal.classList.add('hidden');
  let prevented = 0; const event = { key: 'Escape', preventDefault() { prevented++; } };
  h.window.confirm = () => false; h.context.handleTaskKeydown(event);
  assert.equal(h.state.taskDraft.dirty, true);
  h.window.confirm = () => true; h.context.handleTaskKeydown(event);
  assert.equal(h.state.taskDraft, null); assert.equal(prevented, 2);
});

test('modal traps Tab/Shift+Tab and Escape restores focus to connected trigger', async () => {
  const h = await loadedBoard(); const trigger = node(), first = node(), last = node();
  h.context.document.activeElement = trigger;
  h.context.openTaskModal(card(), 'Завершено');
  assert.equal(h.elements.taskModalBlocked.textContent, 'Не заблокирована');
  h.elements.taskModal.selectors.set('button, [href], input, select, textarea, [tabindex]', [first, last]);
  h.elements.taskModal.childNodes.push(first, last);
  let prevented = 0;
  h.context.document.activeElement = last;
  h.context.handleTaskKeydown({ key: 'Tab', preventDefault() { prevented++; } });
  assert.equal(first.focusCount, 1);
  h.context.document.activeElement = first;
  h.context.handleTaskKeydown({ key: 'Tab', shiftKey: true, preventDefault() { prevented++; } });
  assert.equal(last.focusCount, 1);
  h.context.handleTaskKeydown({ key: 'Escape', preventDefault() { prevented++; } });
  assert.equal(trigger.focusCount, 1); assert.equal(prevented, 3);
  trigger.isConnected = false; h.context.restoreTaskFocus(trigger); assert.equal(trigger.focusCount, 1);
});

test('pending controls retain their original disabled state and do not disable a GET retry', async () => {
  const h = await loadedBoard();
  const enabled = node(), disabled = node(), retry = node(); disabled.disabled = true;
  retry.dataset.action = 'retry-board';
  h.elements.boardColumns.selectors.set('button, input, select, textarea', [enabled, disabled, retry]);
  const save = h.context.saveBoardCard('card-a', formData());
  assert.equal(enabled.disabled, true); assert.equal(disabled.disabled, true);
  assert.equal(retry.disabled, false);
  h.calls[1].reject(Object.assign(Error('Invalid'), { status: 400 })); await save;
  assert.equal(enabled.disabled, false); assert.equal(disabled.disabled, true);
});

test('404 refresh preserves draft and requires confirmed discard before another project', async () => {
  const h = await loadedBoard(); draftFor(h);
  const refresh = h.context.loadBoard('A', 'kanban');
  h.calls[1].reject(Object.assign(Error('missing'), { status: 404 })); await refresh;
  assert.equal(h.state.taskDraft.values.title, 'Draft title');
  assert.equal(h.state.currentBoard, null);
  h.window.confirm = () => false;
  assert.equal(h.context.openProjectBoard('B', 'scrum'), false);
  assert.equal(h.state.selectedProjectId, 'A');
});

test('deleted project keeps draft in memory and cannot submit it to another project', async () => {
  const h = await loadedBoard(); draftFor(h);
  h.context.onProjectDeleted('A');
  assert.equal(h.state.taskDraft.projectId, 'A'); assert.equal(h.state.currentBoard, null);
  h.window.confirm = () => false;
  assert.equal(h.context.openProjectBoard('B', 'scrum'), false);
  await h.context.saveBoardCard('card-a', formData());
  assert.equal(h.calls.filter(c => c.options?.method).length, 0);
  h.calls[1].resolve([{ id: 'B', name: 'B' }]); await new Promise(setImmediate);
});

test('card removed by refresh keeps editable values but cannot send PUT for the missing card', async () => {
  const h = await loadedBoard(); const form = draftFor(h);
  h.state.taskDraft.originalCard = card(); h.state.taskDraft.originalColumn = 'review';
  const refresh = h.context.loadBoard('A', 'kanban');
  h.calls[1].resolve(board('A', 'kanban')); await refresh;
  assert.equal(h.state.taskDraft.values.title, 'Draft title');
  assert.equal(form.elements[0].value, 'Draft title');
  await h.context.saveBoardCard('card-a', formData());
  assert.equal(h.calls.length, 2);
});

test('late refresh success cannot overwrite messages in a reopened generation', async () => {
  const h = await loadedBoard();
  const save = h.context.saveBoardCard('card-a', formData());
  h.calls[1].resolve(card()); await new Promise(setImmediate);
  const reopen = h.context.openProjectBoard('A', 'kanban');
  h.calls[3].resolve(board('A', 'kanban', [card()])); await reopen;
  const before = h.messages.length;
  h.calls[2].resolve(board('A', 'kanban', [card()])); await save;
  assert.equal(h.messages.length, before);
});

test('move while a save is pending neither sends another PUT nor discards the draft', async () => {
  const h = await loadedBoard(); draftFor(h);
  const save = h.context.saveBoardCard('card-a', formData());
  await h.context.moveBoardCard('card-a', 'done');
  assert.equal(h.calls.length, 2); assert.equal(h.confirmations, 0);
  assert.equal(h.state.taskDraft.dirty, true);
  h.calls[1].reject(Object.assign(Error('Invalid'), { status: 400 })); await save;
});

test('reopening the current model refreshes without asking to discard its draft', async () => {
  const h = await loadedBoard(); const form = draftFor(h);
  h.window.confirm = () => { throw Error('A refresh must preserve the draft'); };
  const refresh = h.context.openProjectBoard('A', 'kanban');
  h.calls[1].resolve(board('A', 'kanban', [card()])); await refresh;
  assert.equal(form.elements[0].value, 'Draft title');
  assert.equal(h.state.taskDraft.dirty, true);
});

async function loadedOrderBoard(cards = [card('a'), card('b'), card('c')]) {
  const h = harness();
  const opening = h.context.openProjectBoard('A', 'kanban');
  h.calls[0].resolve(board('A', 'kanban', cards)); await opening;
  return h;
}

function orderResponse(h, ids = h.state.currentBoard.columns[0].cards.map((item) => item.id)) {
  return { projectId: 'A', columnKey: 'review', cards: ids.map((id) => ({
    id, position: h.state.currentBoard.storedPositions[id],
    version: h.state.currentBoard.columns[0].cards.find((item) => item.id === id).version
  })) };
}

function orderForBoard(value) {
  return { projectId: value.projectId, columnKey: 'review', cards: value.columns[0].cards.map((item) => ({
    id: item.id, position: value.storedPositions[item.id], version: item.version
  })) };
}

test('Kanban renders canonical position/id order with keyboard buttons and correct boundaries', async () => {
  const h = await loadedOrderBoard([card('b'), card('a'), card('c')]);
  h.state.currentBoard.storedPositions = { a: 4, b: 4, c: 9 };
  assert.deepEqual(Array.from(h.context.canonicalBoardCards(h.state.currentBoard.columns[0]), (item) => item.id),
    ['a', 'b', 'c']);
  const first = h.context.renderBoardCard(card('a'), 'review', { index: 0, count: 3, orderAvailable: true });
  const middle = h.context.renderBoardCard(card('b'), 'review', { index: 1, count: 3, orderAvailable: true });
  const last = h.context.renderBoardCard(card('c'), 'review', { index: 2, count: 3, orderAvailable: true });
  assert.match(first, /data-action="order-up"[^>]*disabled/);
  assert.doesNotMatch(middle, /data-action="order-up"[^>]*disabled/);
  assert.match(last, /data-action="order-down"[^>]*disabled/);
  assert.match(middle, /aria-label="Переместить задачу b вверх в колонке"/);
  const single = h.context.renderBoardCard(card('a'), 'review', { index: 0, count: 1, orderAvailable: true });
  assert.match(single, /data-action="order-up"[^>]*disabled/);
  assert.match(single, /data-action="order-down"[^>]*disabled/);
  await h.context.reorderBoardCard('a', 'review', 'up');
  assert.equal(h.calls.length, 1);
});

test('up and down send a complete GET snapshot and display only server-confirmed order and versions', async () => {
  const h = await loadedOrderBoard();
  const up = h.context.reorderBoardCard('b', 'review', 'up');
  assert.match(h.calls[1].url, /\/columns\/review\/order$/);
  assert.equal(h.calls.length, 2);
  h.calls[1].resolve(orderResponse(h)); await new Promise(setImmediate);
  assert.equal(h.calls[2].options.method, 'POST');
  assert.match(h.calls[2].url, /\/cards\/b\/reorder$/);
  assert.deepEqual(JSON.parse(h.calls[2].options.body), {
    direction: 'up', expectedOrder: orderResponse(h).cards
  });
  assert.deepEqual(Array.from(h.state.currentBoard.columns[0].cards, (item) => item.id), ['a', 'b', 'c']);
  h.calls[2].resolve({ projectId: 'A', columnKey: 'review', changed: true, cards: [
    { id: 'b', position: 0, version: 1 }, { id: 'a', position: 1, version: 1 },
    { id: 'c', position: 2, version: 0 }
  ] }); await up;
  assert.deepEqual(Array.from(h.state.currentBoard.columns[0].cards, (item) => item.id), ['b', 'a', 'c']);
  assert.equal(h.state.currentBoard.columns[0].cards[0].version, 1);
  const down = h.context.reorderBoardCard('b', 'review', 'down');
  h.calls[3].resolve(orderResponse(h, ['b', 'a', 'c'])); await new Promise(setImmediate);
  assert.equal(JSON.parse(h.calls[4].options.body).direction, 'down');
  h.calls[4].resolve({ projectId: 'A', columnKey: 'review', changed: true, cards: [
    { id: 'a', position: 0, version: 2 }, { id: 'b', position: 1, version: 2 },
    { id: 'c', position: 2, version: 0 }
  ] }); await down;
  assert.deepEqual(Array.from(h.state.currentBoard.columns[0].cards, (item) => item.id), ['a', 'b', 'c']);
});

test('hidden cards disable ordering and never turn a filtered view into expectedOrder', async () => {
  const h = await loadedOrderBoard();
  h.state.boardFilters.query = 'a description';
  const visible = h.state.currentBoard.columns[0].cards.filter(h.context.matchesBoardFilters);
  assert.equal(visible.length, 1);
  h.context.renderBoardColumns();
  const html = h.elements.boardColumns.childNodes[0].innerHTML;
  assert.match(html, /фильтр скрывает часть карточек/);
  assert.match(html, /data-action="order-up"[^>]*disabled/);
  assert.match(html, /data-action="order-down"[^>]*disabled/);
  await h.context.reorderBoardCard('a', 'review', 'down');
  assert.equal(h.calls.length, 1);
});

test('repeated press is blocked during GET and POST, with no optimistic move', async () => {
  const h = await loadedOrderBoard();
  const pending = h.context.reorderBoardCard('b', 'review', 'up');
  await h.context.reorderBoardCard('b', 'review', 'up');
  assert.equal(h.calls.length, 2);
  h.calls[1].resolve(orderResponse(h)); await new Promise(setImmediate);
  await h.context.reorderBoardCard('b', 'review', 'up');
  assert.equal(h.calls.length, 3);
  assert.deepEqual(Array.from(h.state.currentBoard.columns[0].cards, (item) => item.id), ['a', 'b', 'c']);
  h.calls[2].resolve({ projectId: 'A', columnKey: 'review', changed: true, cards: [
    { id: 'b', position: 0, version: 1 }, { id: 'a', position: 1, version: 1 },
    { id: 'c', position: 2, version: 0 }
  ] }); await pending;
});

test('stale GET snapshot rereads the board without POST or automatic retry', async () => {
  const h = await loadedOrderBoard();
  const pending = h.context.reorderBoardCard('b', 'review', 'up');
  h.calls[1].resolve(orderResponse(h, ['a', 'c', 'b'])); await new Promise(setImmediate);
  assert.equal(h.calls.length, 3);
  assert.equal(h.calls[2].options, undefined);
  const fresh = board('A', 'kanban', [card('a'), card('c'), card('b')]);
  h.calls[2].resolve(fresh); await new Promise(setImmediate);
  assert.equal(h.calls.length, 4);
  h.calls[3].resolve(orderForBoard(fresh)); await pending;
  assert.equal(h.calls.length, 4);
  assert.match(h.messages.at(-1), /нажмите кнопку снова/);
});

test('409 and 404 refresh current board, preserve dirty draft, and never retry reorder', async () => {
  for (const status of [409, 404]) {
    const h = await loadedOrderBoard();
    const form = draftFor(h);
    const pending = h.context.reorderBoardCard('b', 'review', 'up');
    h.calls[1].resolve(orderResponse(h)); await new Promise(setImmediate);
    h.calls[2].reject(Object.assign(Error('conflict'), { status })); await new Promise(setImmediate);
    assert.equal(h.calls.length, 4);
    const fresh = board('A', 'kanban', [card('a'), card('b'), card('c')]);
    h.calls[3].resolve(fresh); await new Promise(setImmediate);
    h.calls[4].resolve(orderForBoard(fresh)); await pending;
    assert.equal(h.calls.filter((call) => call.options?.method === 'POST').length, 1);
    assert.equal(h.state.taskDraft.values.title, 'Draft title');
    assert.equal(form.elements[0].value, 'Draft title');
  }
});

test('400, 404, 409, network error and invalid response block repeat until reread succeeds', async () => {
  for (const status of [400, 404, 409, undefined, 'invalid-response']) {
    const h = await loadedOrderBoard(); draftFor(h);
    const pending = h.context.reorderBoardCard('b', 'review', 'up');
    h.calls[1].resolve(orderResponse(h)); await new Promise(setImmediate);
    if (status === 'invalid-response') h.calls[2].resolve({ projectId: 'A', columnKey: 'review', changed: false, cards: [] });
    else h.calls[2].reject(Object.assign(Error('failure'), { status }));
    await new Promise(setImmediate);
    assert.equal(h.calls.length, 4);
    h.calls[3].reject(Error('refresh offline')); await pending;
    assert.equal(h.state.taskOperations.A.phase, 'unknown');
    assert.equal(h.state.currentBoard, null);
    assert.equal(h.state.taskDraft.values.title, 'Draft title');
    await h.context.reorderBoardCard('b', 'review', 'up');
    assert.equal(h.calls.length, 4);
    const fresh = board('A', 'kanban', [card('a'), card('b'), card('c')]);
    const reread = h.context.loadBoard('A', 'kanban');
    h.calls[4].resolve(fresh); await new Promise(setImmediate);
    assert.equal(h.calls.length, 6);
    h.calls[5].resolve(orderForBoard(fresh)); await reread;
    assert.equal(h.state.taskOperations.A, undefined);
    assert.equal(h.state.taskDraft.values.title, 'Draft title');
    const retry = h.context.reorderBoardCard('b', 'review', 'up');
    assert.equal(h.calls.length, 7);
    h.calls[6].reject(Object.assign(Error('bad'), { status: 400 }));
    await new Promise(setImmediate);
    h.calls[7].reject(Error('offline')); await retry;
  }
});

test('board refresh alone cannot unlock reorder when authoritative order GET fails', async () => {
  const h = await loadedOrderBoard();
  const pending = h.context.reorderBoardCard('b', 'review', 'up');
  h.calls[1].resolve(orderResponse(h)); await new Promise(setImmediate);
  h.calls[2].reject(Object.assign(Error('invalid'), { status: 400 })); await new Promise(setImmediate);
  const fresh = board('A', 'kanban', [card('a'), card('b'), card('c')]);
  h.calls[3].resolve(fresh); await new Promise(setImmediate);
  h.calls[4].reject(Error('order unavailable')); await pending;
  assert.equal(h.state.taskOperations.A.phase, 'unknown');
  assert.equal(h.state.currentBoard, null);
  await h.context.reorderBoardCard('b', 'review', 'up');
  assert.equal(h.calls.length, 5);
  const retry = h.context.loadBoard('A', 'kanban');
  h.calls[5].resolve(fresh); await new Promise(setImmediate);
  h.calls[6].resolve(orderForBoard(fresh)); await retry;
  assert.equal(h.state.taskOperations.A, undefined);
});

test('late POST after project switch never changes new board or its message', async () => {
  const h = await loadedOrderBoard();
  const pending = h.context.reorderBoardCard('b', 'review', 'up');
  h.calls[1].resolve(orderResponse(h)); await new Promise(setImmediate);
  const switching = h.context.openProjectBoard('B', 'scrum');
  h.calls[3].resolve(board('B', 'scrum')); await switching;
  const messageCount = h.messages.length;
  h.calls[2].resolve({ projectId: 'A', columnKey: 'review', changed: true, cards: [
    { id: 'b', position: 0, version: 1 }, { id: 'a', position: 1, version: 1 },
    { id: 'c', position: 2, version: 0 }
  ] }); await pending;
  assert.equal(h.state.currentBoard.projectId, 'B');
  assert.equal(h.messages.length, messageCount);
});

test('late order GET after board switch never sends POST', async () => {
  const h = await loadedOrderBoard();
  const pending = h.context.reorderBoardCard('b', 'review', 'up');
  const switching = h.context.openProjectBoard('B', 'scrum');
  h.calls[2].resolve(board('B', 'scrum')); await switching;
  h.calls[1].resolve(orderResponse(h, [])); await pending;
  assert.equal(h.calls.length, 3);
  assert.equal(h.state.currentBoard.projectId, 'B');
});

test('successful reorder retains an edited card draft and marks its old version for comparison', async () => {
  const h = await loadedOrderBoard([card('card-a'), card('b')]);
  const form = draftFor(h);
  const pending = h.context.reorderBoardCard('b', 'review', 'up');
  h.calls[1].resolve(orderResponse(h)); await new Promise(setImmediate);
  h.calls[2].resolve({ projectId: 'A', columnKey: 'review', changed: true, cards: [
    { id: 'b', position: 0, version: 1 }, { id: 'card-a', position: 1, version: 1 }
  ] }); await pending;
  assert.equal(h.state.taskDraft.values.title, 'Draft title');
  assert.equal(form.elements[0].value, 'Draft title');
  assert.equal(h.state.taskDraft.expectedVersion, 0);
  assert.equal(h.state.taskDraft.conflict, true);
  await h.context.saveBoardCard('card-a', formData());
  assert.equal(h.calls.length, 3);
});
