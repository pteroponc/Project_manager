const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '../../main/resources/public/personal-board-ui.js'), 'utf8');
const flush = () => new Promise(resolve => setImmediate(resolve));
const response = (status, body) => ({ status, ok: status < 400, json: async () => body });
const sample = { id: 'task/1', title: 'Sample', columnKey: 'ready', priority: 'medium', version: 0, projectId: null };
const board = (...tasks) => ({ columns: [{ title: 'Ready', tasks }] });

function harness() {
  function node() {
    return {
      children: [], dataset: {}, events: {}, isConnected: true,
      append(...children) { this.children.push(...children); },
      prepend(child) { this.children.unshift(child); },
      replaceChildren(...children) { this.children = children; },
      addEventListener(name, callback) { this.events[name] = callback; },
      querySelector() { return null; },
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
