const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '../../main/resources/public/app.js'), 'utf8');
const names = ['formatDate', 'daysUntil', 'isProjectOverdue', 'isTaskOverdue', 'milestoneTone',
  'deadlineTone', 'renderDeadlineRows', 'renderBoardInsights', 'renderBoardCard',
  'isCurrentBoardRequest', 'activeBoardContext', 'isCurrentBoardContext',
  'moveBoardCard', 'renderProjectDashboards'];
const functions = names.map((name) => {
  const start = source.search(new RegExp('^(?:async )?function ' + name + '\\(', 'm'));
  assert.ok(start >= 0, name);
  return source.slice(start, source.indexOf('\n}', start) + 2);
}).join('\n');

function harness(instant) {
  const RealDate = Date;
  class FixedDate extends RealDate {
    constructor(...args) { super(...(args.length ? args : [instant])); }
    static now() { return new RealDate(instant).getTime(); }
  }
  const elements = new Proxy({}, { get(target, key) {
    return target[key] ??= { textContent: '', innerHTML: '' };
  } });
  const state = { currentBoard: null, activeView: 'boards', selectedProjectId: 'p1',
    activeBoardModel: 'kanban', boardDrilledIn: true, boardRequest: 0, boardLoading: false };
  const context = vm.createContext({ Date: FixedDate, Intl, Number, state, elements,
    escapeAttr: String, escapeHtml: String,
    adjacentColumnKey: () => null, setFormState() {}, boardCardUrl: (id) => `/cards/${id}`,
    groupCount: () => ({}), renderDashboardKpi: (...args) => args.join(':'),
    renderDistributionBars: () => '', renderProjectRows: () => '', renderRiskRows: () => '',
    formatNumber: String, formatCurrency: String, declOfNum: () => 'модели',
    progressComment: () => '', riskWeight: () => 0 });
  vm.runInContext(functions, context);
  return { context, state, elements };
}

test('Moscow calendar day wins at both UTC midnight boundaries', () => {
  const before = harness('2026-10-28T20:59:59Z').context;
  const after = harness('2026-10-28T21:00:00Z').context;
  assert.equal(before.daysUntil('2026-10-28'), 0);
  assert.equal(after.daysUntil('2026-10-28'), -1);
  assert.equal(after.daysUntil('2026-10-29'), 0);
  assert.equal(after.daysUntil('2026-10-30'), 1);
});

test('date-only arithmetic ignores browser negative UTC offset and rejects missing dates', () => {
  const previous = process.env.TZ;
  try {
    process.env.TZ = 'America/Los_Angeles';
    const { context } = harness('2026-10-29T06:30:00Z');
    assert.equal(context.daysUntil('2026-10-28'), -1);
    assert.equal(context.daysUntil('2026-10-29'), 0);
    assert.equal(context.daysUntil('2026-10-30'), 1);
    assert.match(context.formatDate('2026-10-29'), /29/);
    assert.equal(context.daysUntil(null), Infinity);
    assert.equal(context.daysUntil('2026-02-30'), Infinity);
  } finally {
    if (previous === undefined) delete process.env.TZ;
    else process.env.TZ = previous;
  }
});

test('terminal columns and missing due dates never mark a task overdue', () => {
  const { context, state } = harness('2026-10-28T21:30:00Z');
  const late = { dueDate: '2026-10-28' };
  for (const [model, final] of [['kanban', 'done'], ['scrum', 'retrospective'], ['waterfall', 'release']]) {
    state.currentBoard = { deliveryModel: model };
    assert.equal(context.isTaskOverdue(late, 'review', model), true);
    assert.equal(context.isTaskOverdue(late, final, model), false);
    assert.doesNotMatch(context.renderBoardCard(late, final), /Просрочено/);
    assert.match(context.renderBoardCard(late, 'review'), /Просрочено/);
  }
  assert.equal(context.isTaskOverdue({ dueDate: '' }, 'review', 'kanban'), false);
});

test('board badge and counter agree after moving into and out of a final column', async () => {
  const { context, state, elements } = harness('2026-10-28T21:30:00Z');
  const card = { id: 'c1', title: 'Task', description: '', owner: 'QA', dueDate: '2026-10-28',
    priority: 'medium', estimate: 1, blocked: false };
  const board = (columnKey) => ({ projectId: 'p1', deliveryModel: 'kanban', columns: [
    { key: 'review', cards: columnKey === 'review' ? [card] : [] },
    { key: 'done', cards: columnKey === 'done' ? [card] : [] }
  ] });
  let columnKey = 'review';
  state.currentBoard = board(columnKey);
  context.findBoardCard = () => ({ card, columnKey });
  context.fetchJson = async (_url, options) => { columnKey = JSON.parse(options.body).columnKey; };
  context.loadBoard = async () => { state.currentBoard = board(columnKey); context.renderBoardInsights(); };
  const check = () => {
    context.renderBoardInsights();
    const badge = /Просрочено/.test(context.renderBoardCard(card, columnKey));
    assert.equal(Number(elements.boardOverdueCount.textContent), Number(badge));
  };
  check();
  await context.moveBoardCard('c1', 'done'); check();
  assert.equal(elements.boardOverdueCount.textContent, 0);
  await context.moveBoardCard('c1', 'review'); check();
  assert.equal(elements.boardOverdueCount.textContent, 1);
  assert.equal(card.dueDate, '2026-10-28');
});

test('dashboard matches PM-001: completed projects are not overdue', () => {
  const { context, elements } = harness('2026-10-28T21:30:00Z');
  const completed = { name: 'Done', status: 'done', health: 'green', progress: 100,
    deadline: '2026-10-28', deliveryModel: 'kanban' };
  const active = { name: 'Active', status: 'active', health: 'green', progress: 50,
    deadline: '2026-10-28', deliveryModel: 'kanban' };
  assert.equal(context.isProjectOverdue(completed), false);
  assert.equal(context.milestoneTone(completed), 'green');
  assert.equal(context.isProjectOverdue(active), true);
  context.renderProjectDashboards([completed, active]);
  assert.match(elements.projectDashboardGrid.innerHTML, /<span>Просрочено<\/span>\s*<strong>1<\/strong>/);
  context.renderProjectDashboards([completed]);
  assert.match(elements.projectDashboardGrid.innerHTML, /<span>Просрочено<\/span>\s*<strong>0<\/strong>/);
});

test('completed future deadline stays out of due-soon count and upcoming rows', () => {
  const { context, elements } = harness('2026-10-28T21:30:00Z');
  const completed = { name: 'Completed', status: 'done', health: 'green', progress: 100,
    deadline: '2026-11-12', deliveryModel: 'kanban' };
  const active = { ...completed, name: 'Active', status: 'active' };
  context.renderProjectDashboards([completed]);
  assert.match(elements.projectDashboardGrid.innerHTML, /<span>Скоро дедлайн<\/span>\s*<strong>0<\/strong>/);
  assert.match(elements.projectDashboardGrid.innerHTML, /<strong>0 ближайших<\/strong>/);
  context.renderProjectDashboards([completed, active]);
  assert.match(elements.projectDashboardGrid.innerHTML, /<span>Скоро дедлайн<\/span>\s*<strong>1<\/strong>/);
  assert.match(elements.projectDashboardGrid.innerHTML, /<strong>1 ближайших<\/strong>/);
  assert.match(elements.projectDashboardGrid.innerHTML, /class="deadline-row"[^>]*>[\s\S]*?Active/);
  assert.doesNotMatch(elements.projectDashboardGrid.innerHTML, /class="deadline-row"[^>]*>[\s\S]*?Completed/);
  assert.equal(completed.status, 'done');
  assert.equal(completed.deadline, '2026-11-12');
});

test('due-soon boundaries 0, 14 and 30 days follow Moscow day rollover', () => {
  const projects = [0, 14, 30, 31].map((days) => {
    const date = new Date(Date.UTC(2026, 9, 29 + days)).toISOString().slice(0, 10);
    return { name: `Due ${days}`, status: 'active', health: 'green', progress: 50,
      deadline: date, deliveryModel: 'kanban' };
  });
  const before = harness('2026-10-28T20:59:59Z');
  before.context.renderProjectDashboards(projects);
  assert.match(before.elements.projectDashboardGrid.innerHTML, /<span>Скоро дедлайн<\/span>\s*<strong>2<\/strong>/);
  const after = harness('2026-10-28T21:00:00Z');
  after.context.renderProjectDashboards(projects);
  assert.match(after.elements.projectDashboardGrid.innerHTML, /<span>Скоро дедлайн<\/span>\s*<strong>3<\/strong>/);
  assert.equal(after.context.daysUntil(projects[0].deadline), 0);
  assert.equal(after.context.daysUntil(projects[1].deadline), 14);
  assert.equal(after.context.daysUntil(projects[2].deadline), 30);
  assert.equal(after.context.daysUntil(projects[3].deadline), 31);
  assert.equal(after.context.deadlineTone(projects[0]), 'yellow');
  assert.equal(after.context.deadlineTone(projects[1]), 'yellow');
  assert.equal(after.context.deadlineTone(projects[2]), 'green');
});

test('deadline urgency does not inherit health color', () => {
  const { context, elements } = harness('2026-10-28T21:30:00Z');
  const project = { name: 'Red health future date', status: 'active', health: 'red',
    progress: 100, deadline: '2026-11-28', deliveryModel: 'kanban' };
  assert.equal(context.deadlineTone(project), 'green');
  context.renderProjectDashboards([project]);
  assert.match(elements.projectDashboardGrid.innerHTML, /<span>Скоро дедлайн<\/span>\s*<strong>1<\/strong>/);
  assert.match(elements.projectDashboardGrid.innerHTML, /class="deadline-row" data-tone="green"/);
  assert.match(elements.dashboardHealthSummary.textContent, /1 с риском/);
  assert.match(elements.projectDashboardGrid.innerHTML, /Риски:1:По состоянию проектов/);
});
