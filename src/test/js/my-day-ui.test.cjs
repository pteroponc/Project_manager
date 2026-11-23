const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const dir = path.join(__dirname, '../../main/resources/public');
const script = fs.readFileSync(path.join(dir, 'my-day-ui.js'), 'utf8');
const flush = () => new Promise(resolve => setImmediate(resolve));

test('renders each task once, filters user priority, opens by ID and reloads on return', async () => {
  function node() {
    return {
      children: [], listeners: {},
      addEventListener(name, callback) { this.listeners[name] = callback; },
      append(...children) { this.children.push(...children); },
      replaceChildren(...children) { this.children = children; }
    };
  }
  const nodes = new Map();
  const get = id => {
    if (!nodes.has(id)) nodes.set(id, node());
    return nodes.get(id);
  };
  const calls = [];
  const opened = [];
  const window = { PMBoardContext: { openTask: (...args) => opened.push(args) } };
  const task = { id: 'task/1', title: '<b>Title</b>', projectId: null, state: 'ready',
    priority: 'high', highPriority: true, dueDate: '2026-11-24', reason: 'UPCOMING' };
  const other = { ...task, id: 'task/2', projectId: 'p1', projectName: 'Project', priority: 'low',
    highPriority: false, reason: 'TODAY' };
  let tasks = [task, other];
  let milestone = { name: 'Контрольная точка', projectName: 'Project', plannedDate: '2026-11-22', plannedDateRelation: 'PAST' };
  vm.runInNewContext(script, {
    document: { createElement: node, getElementById: get }, window,
    async fetch(url) {
      calls.push(url);
      return { ok: true, async json() { return { calculationDate: '2026-11-23', tasks, milestone }; } };
    }
  });
  const line = group => get('my-day-' + group).children[0].children[0];
  window.PMMyDayUI.onView('my-day');
  await flush();
  const milestoneBlock = get('my-day-milestone');
  assert.equal(milestoneBlock.children[1].textContent, 'Плановая дата прошла — проверьте состояние вехи');
  assert.equal(line('upcoming').children[0].textContent, '<b>Title</b>');
  assert.match(line('upcoming').children[1].textContent, /Без проекта.*24\.11\.2026/);
  line('upcoming').children[0].listeners.click();
  line('today').children[0].listeners.click();
  assert.deepEqual(opened, [[null, null, 'task/1'], ['p1', null, 'task/2']]);
  assert.equal(line('undated'), undefined);
  get('my-day-priority').checked = true;
  get('my-day-priority').listeners.change();
  assert.equal(line('today'), undefined);
  assert.ok(line('upcoming'));
  tasks = [{ ...task, reason: 'TODAY', dueDate: '2026-11-23' }];
  milestone = { ...milestone, plannedDate: '2026-11-24', plannedDateRelation: 'FUTURE' };
  window.PMMyDayUI.onView('boards');
  window.PMMyDayUI.onView('my-day');
  await flush();
  assert.deepEqual(calls, ['/api/my-day', '/api/my-day']);
  assert.equal(line('upcoming'), undefined);
  assert.match(line('today').children[1].textContent, /23\.11\.2026/);
  assert.equal(milestoneBlock.children[1].textContent, 'Будущая плановая дата');
  milestone = null;
  await get('my-day-refresh').listeners.click();
  assert.equal(milestoneBlock.children[0].textContent, 'Нет актуальной вехи с подтверждённой датой.');
});

test('My Day is the initially visible section with an independent accessible navigation entry', () => {
  const html = fs.readFileSync(path.join(dir, 'index.html'), 'utf8');
  assert.match(html, /class="nav-item active"[^>]+data-view="my-day"/);
  assert.match(html, /class="view-section active my-day" data-view-section="my-day"/);
  assert.doesNotMatch(html, /class="[^"]*\bactive\b[^"]*"[^>]+data-view-section="overview"/);
});
