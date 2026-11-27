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

function harness() {
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
      removeAttribute() {}, reset() {},
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
  const window = { confirm: message => { confirmations.push(message); return decisions.shift(); } };
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
