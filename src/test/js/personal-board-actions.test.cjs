const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '../../main/resources/public/personal-board-ui.js'), 'utf8');
const flush = () => new Promise(resolve => setImmediate(resolve));
const response = (status, body) => ({ status, ok: status < 400, json: async () => body });
const sample = { id: 'task/1', title: 'Sample', columnKey: 'ready', priority: 'medium', version: 0, projectId: null };
const board = (...tasks) => ({ columns: [{ key: 'ready', title: 'Ready', tasks }] });

function harness(desktop = false) {
  function node() {
    return {
      children: [], dataset: {}, events: {}, isConnected: true,
      append(...children) { this.children.push(...children); },
      prepend(child) { this.children.unshift(child); },
      replaceChildren(...children) { this.children = children; },
      addEventListener(name, callback) { this.events[name] = callback; },
      querySelector() { return null; },
      setAttribute(name, value) { this[name] = value; },
      scrollIntoView() {},
      focus() { this.focused = true; },
      removeAttribute(name) {
        if (name === 'data-dragging') delete this.dataset.dragging;
        if (name === 'data-drop-target') delete this.dataset.dropTarget;
      },
      contains(child) { return this.children.includes(child); },
      reset() {},
      classList: { contains: () => false }
    };
  }
  const elements = new Map();
  const byId = id => {
    if (!elements.has(id)) elements.set(id, node());
    return elements.get(id);
  };
  const form = byId('personal-task-form');
  form.hidden = true;
  form.elements = Object.fromEntries(['projectId', 'title', 'description', 'priority', 'columnKey', 'dueDate']
    .map(name => [name, node()]));
  const requests = [];
  const confirmations = [];
  const decisions = [];
  const window = {
    confirm: message => { confirmations.push(message); return decisions.shift(); },
    matchMedia: () => ({ matches: desktop, addEventListener() {} })
  };
  vm.runInNewContext(source, {
    document: { createElement: node, getElementById: byId }, window,
    Option: function (label, value) { Object.assign(this, { label, value }); },
    fetch: (url, options) => new Promise((resolve, reject) => requests.push({ url, options, resolve, reject }))
  });
  function find(root, title) {
    if (root.textContent === title) return root;
    for (const child of root.children) {
      const match = find(child, title);
      if (match) return match;
    }
  }
  return { window, byId, form, requests, confirmations, decisions,
    action: title => find(byId('personal-board-columns'), title) };
}

test('delete requires confirmation, suppresses duplicates, and waits for reread after a network error', async () => {
  const h = harness();
  h.window.PMPersonalBoardUI.onView('boards');
  h.requests.shift().resolve(response(200, board(sample)));
  await flush();
  const remove = h.action('Удалить задачу');
  h.decisions.push(false, true);
  remove.events.click();
  assert.equal(h.requests.length, 0);
  assert.equal(remove.focused, true);
  assert.match(h.confirmations[0], /Sample/);
  remove.events.click();
  remove.events.click();
  const request = h.requests.shift();
  assert.equal(request.url, '/api/board/personal/tasks/task%2F1?expectedVersion=0');
  assert.equal(request.options.method, 'DELETE');
  assert.equal(request.options.headers['X-PM-Write'], '1');
  assert.equal(h.requests.length, 0);
  request.reject(new Error('offline'));
  await flush();
  remove.events.click();
  assert.equal(h.requests.length, 0);
  h.byId('personal-board-refresh').events.click();
  h.requests.shift().resolve(response(200, board(sample)));
  await flush();
  h.decisions.push(true);
  h.action('Удалить задачу').events.click();
  h.requests.shift().resolve(response(204));
  await flush();
  h.requests.shift().resolve(response(200, board()));
  await flush();
  assert.equal(h.action('Удалить задачу'), undefined);
  assert.equal(h.byId('personal-task-create').focused, true);
});

const moveBoard = (task = sample) => ({
  columns: ['ready', 'done'].map(key => ({ key, title: key, tasks: task.columnKey === key ? [task] : [] }))
});

async function dragHarness(desktop = true) {
  const h = harness(desktop);
  h.window.PMPersonalBoardUI.onView('boards');
  h.requests.shift().resolve(response(200, moveBoard()));
  await flush();
  h.groups = () => h.byId('personal-board-columns').children;
  h.summary = () => h.groups()[0].children[1].children[0];
  h.event = () => ({
    dataTransfer: { setData() {} },
    preventDefault() { this.prevented = true; }
  });
  h.drop = () => {
    h.summary().events.dragstart(h.event());
    h.groups()[1].events.drop(h.event());
  };
  return h;
}

test('desktop drop highlights the target, submits once with the version, and uses server placement', async () => {
  const h = await dragHarness();
  const [source, target] = h.groups();
  h.summary().events.dragstart(h.event());
  const hover = h.event();
  target.events.dragover(hover);
  assert.equal(hover.prevented, true);
  assert.equal(target.dataset.dropTarget, 'true');
  target.events.drop(h.event());
  target.events.drop(h.event());
  assert.equal(target.dataset.dropTarget, undefined);
  assert.equal(h.groups()[0], source);
  assert.equal(h.requests.length, 1);
  const write = h.requests.shift();
  assert.equal(write.options.method, 'PUT');
  assert.equal(write.options.headers['X-PM-Write'], '1');
  assert.deepEqual(JSON.parse(write.options.body), {
    projectId: null, title: 'Sample', dueDate: null, priority: 'medium', columnKey: 'done', expectedVersion: 0
  });
  write.resolve(response(200, {}));
  await flush();
  assert.doesNotMatch(h.byId('personal-board-message').textContent, /Задача перемещена/);
  h.requests.shift().resolve(response(200, moveBoard({ ...sample, version: 1, columnKey: 'done' })));
  await flush();
  assert.match(h.byId('personal-board-message').textContent, /Задача перемещена/);
  assert.equal(h.action('Завершить'), undefined);
});

test('same column, cancellation, and mobile dragging never submit', async () => {
  const h = await dragHarness();
  h.summary().events.dragstart(h.event());
  h.groups()[0].events.drop(h.event());
  h.summary().events.dragend();
  h.groups()[1].events.drop(h.event());
  assert.equal(h.requests.length, 0);
  const mobile = await dragHarness(false);
  assert.equal(mobile.summary().draggable, false);
  mobile.drop();
  assert.equal(mobile.requests.length, 0);
});

for (const scenario of [
  { label: 'conflict', status: 409, version: 2, message: /Конфликт версии/ },
  { label: 'rejection', status: 400, version: 0, message: /отклонил перенос/ },
  { label: 'network failure and unavailable read', version: 0, failedRead: true, message: /Не удалось сверить/ },
  { label: 'lost reply after commit', version: 1, moved: true, message: /Сверено с сервером/ },
  { label: 'uncertain server error', status: 503, version: 0, message: /Перенос не подтверждён/ }
]) {
  test(`drop handles ${scenario.label} without losing or duplicating a card`, async () => {
    const h = await dragHarness();
    h.drop();
    const write = h.requests.shift();
    if (scenario.status) write.resolve(response(scenario.status, {}));
    else write.reject(new Error('network failure'));
    await flush();
    const data = moveBoard({ ...sample, version: scenario.version, columnKey: scenario.moved ? 'done' : 'ready' });
    const read = h.requests.shift();
    assert.equal(read.options.cache, 'no-store');
    read.resolve(response(scenario.failedRead ? 503 : 200, data));
    await flush();
    assert.match(h.byId('personal-board-message').textContent, scenario.message);
    assert.equal(h.groups().filter(group => group.children[1].children.length > 0).length, 1);
    assert.equal(h.requests.length, 0);
    if (scenario.moved) return;
    if (scenario.failedRead) {
      h.drop();
      assert.equal(h.requests.length, 0);
      h.byId('personal-board-refresh').events.click();
      h.requests.shift().resolve(response(200, data));
      await flush();
    }
    h.drop();
    assert.equal(JSON.parse(h.requests.shift().options.body).expectedVersion, scenario.version);
  });
}

test('column switcher retains selection and opens the addressed task in its own column', async () => {
  const h = harness();
  const data = { columns: [
    { key: 'backlog', title: 'Бэклог', tasks: [] },
    ...board(sample).columns,
    { key: 'in-progress', title: 'В работе', tasks: [] },
    { key: 'done', title: 'Готово', tasks: [] }
  ] };
  const applyRead = async () => {
    h.requests.shift().resolve(response(200, data));
    await flush();
  };
  const buttons = () => h.byId('personal-board-switcher').children;
  const groups = () => h.byId('personal-board-columns').children;
  h.window.PMPersonalBoardUI.onView('boards');
  await applyRead();
  assert.equal(buttons().length, 4);
  assert.equal(buttons()[0]['aria-pressed'], 'true');
  buttons()[3].events.click();
  assert.equal(groups()[3].dataset.selected, 'true');
  h.byId('personal-board-refresh').events.click();
  await applyRead();
  assert.equal(buttons()[3]['aria-pressed'], 'true');
  h.window.PMPersonalBoardUI.focusTask(sample.id);
  await applyRead();
  assert.deepEqual(buttons().map(button => button['aria-pressed']), ['false', 'true', 'false', 'false']);
  assert.equal(groups()[1].children[1].open, true);
});

test('project selection is submitted and a stale version preserves the selected project and draft', async () => {
  const h = harness();
  h.window.PMPersonalBoardUI.onView('boards');
  h.requests.shift().resolve(response(200, board(sample)));
  await flush();
  h.action('Редактировать').events.click();
  h.requests.shift().resolve(response(200, [{ id: 'p1', name: 'Project' }]));
  await flush();
  h.form.elements.projectId.value = 'p1';
  h.form.events.submit({ preventDefault() {} });
  const write = h.requests.shift();
  assert.equal(write.options.headers['X-PM-Write'], '1');
  assert.equal(JSON.parse(write.options.body).projectId, 'p1');
  write.resolve(response(409, { currentVersion: 1 }));
  await flush();
  h.byId('personal-board-refresh').events.click();
  h.requests.shift().resolve(response(200, board({ ...sample, version: 1 })));
  await flush();
  assert.equal(h.form.hidden, false);
  assert.equal(h.form.elements.projectId.value, 'p1');
  assert.equal(h.form.elements.title.value, 'Sample');
  assert.equal(h.byId('personal-task-save').disabled, true);
});

test('an addressed missing task is explained after the board refresh', async () => {
  const h = harness();
  h.window.PMPersonalBoardUI.onView('boards');
  h.window.PMPersonalBoardUI.focusTask('removed');
  h.requests.shift().resolve(response(200, board(sample)));
  await flush();
  assert.match(h.byId('personal-board-message').textContent, /Исходная задача недоступна/);
  assert.equal(h.requests.length, 0);
});
