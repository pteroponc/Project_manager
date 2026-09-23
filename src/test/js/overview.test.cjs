const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

const source = fs.readFileSync(path.join(__dirname, '../../main/resources/public/app.js'), 'utf8');
const html = fs.readFileSync(path.join(__dirname, '../../main/resources/public/index.html'), 'utf8');
const css = fs.readFileSync(path.join(__dirname, '../../main/resources/public/styles.css'), 'utf8');
const names = ['loadDashboard', 'loadOverview', 'populateOverviewFilters', 'renderStats',
  'overviewProjectLink', 'overviewDate', 'bindOverviewLinks', 'overviewEmpty', 'renderProjectOverview',
  'renderPortfolioSlice', 'renderOverviewQuality', 'openProjectFromOverview', 'showView',
  'formatNumber', 'formatCurrency', 'displayLabel', 'escapeHtml', 'setSummary', 'loadTrainSnapshot'];
// Execute the actual production functions; DOM adapters below only record their effects.
const functions = names.map(name => {
  const start = source.search(new RegExp('^(?:async )?function ' + name + '\\(', 'm'));
  assert.ok(start >= 0, name);
  return source.slice(start, source.indexOf('\n}', start) + 2);
}).join('\n');

function node() {
  return { textContent: '', innerHTML: '', value: 'all', options: [], attrs: {},
    setAttribute(k, v) { this.attrs[k] = v; },
    replaceChildren(...v) { this.options = v; }, add(v) { this.options.push(v); },
    querySelectorAll() { return []; }, classList: { toggle() {} },
    focus() { this.focused = true; }, scrollIntoView() { this.scrolled = true; } };
}
function setup() {
  const nodes = new Map();
  const get = id => { if (!nodes.has(id)) nodes.set(id, node()); return nodes.get(id); };
  const elements = new Proxy({ viewSections: [], viewButtons: [] }, {
    get(target, key) { return key in target ? target[key] : get(key); }
  });
  const state = { filters: { quarter: 'all', health: 'all', status: 'all' }, overviewRequest: 0,
    trainsRequest: 0, projects: [], selectedProjectId: 'board-project', activeView: 'overview' };
  const context = vm.createContext({ state, elements, document: { getElementById: get, querySelector: () => get('card') },
    window: { scrollTo() {} }, CSS: { escape: v => v }, URLSearchParams, Intl, Date,
    Option: function(text, value) { this.text = text; this.value = value; },
    LABELS: { status: { active: 'Активен', done: 'Завершён' }, health: {}, deliveryModel: {}, priority: {}, releaseStatus: {} },
    fetchJson: async () => { throw Error('offline'); }, closeProjectForm() { get('form').closed = true; }, renderProjects() {}, renderReleaseTrains() {} });
  vm.runInContext(functions, context);
  return { context, state, elements, get };
}
function snapshot(overrides = {}) {
  return { calculationDate: '2026-09-19', totalProjectCount: 1, projectCount: 1, activeCount: 1,
    attentionCount: 0, totalBudget: 0, budgetCoverage: 1, averageProgress: 0, progressCoverage: 1,
    quarters: ['Q3', 'Q4'], statuses: ['active', 'done'], healthValues: ['green', 'red'],
    statusCounts: { active: 1 }, healthCounts: { green: 1 }, attentionProjects: [], milestones: [], dueSoonCount: 0, dataQualityProjects: [], ...overrides };
}

test('KPI distinguishes unknown from real zero and uses attentionCount', () => {
  const { context, elements } = setup();
  context.renderStats(snapshot({ attentionCount: 2, averageProgress: null, totalBudget: null }));
  assert.equal(elements.statRiskyCount.textContent, '2');
  assert.equal(elements.statAverageProgress.textContent, 'Нет данных');
  assert.equal(elements.sliceBudgetTotal.textContent, '');
  context.renderStats(snapshot());
  assert.equal(elements.statAverageProgress.textContent, '0%');
  assert.equal(elements.sliceBudgetTotal.textContent, '');
});

test('late response cannot overwrite the latest filter result or board selection', async () => {
  const { context, state, elements } = setup();
  const pending = [];
  context.fetchJson = url => new Promise(resolve => pending.push({ url, resolve }));
  const first = context.loadOverview();
  state.filters.status = 'done';
  const second = context.loadOverview();
  pending[1].resolve(snapshot({ projectCount: 2 })); await second;
  pending[0].resolve(snapshot({ projectCount: 99 })); await first;
  assert.equal(state.overview.projectCount, 2);
  assert.equal(elements.statProjectCount.textContent, '2');
  assert.equal(state.selectedProjectId, 'board-project');
  assert.match(pending[1].url, /status=done/);
});

test('failed first load reports unavailable data and retry recovers', async () => {
  const { context, state, elements, get } = setup();
  await context.loadOverview();
  assert.match(elements.summary.textContent, /Данные недоступны/);
  assert.equal(state.overview, undefined);
  assert.equal(get('overview-content').attrs['aria-busy'], 'false');
  context.fetchJson = async () => snapshot();
  await context.loadOverview();
  assert.match(elements.overviewAsOf.textContent, /19.09.2026/);
  assert.doesNotMatch(elements.summary.textContent, /Не удалось/);
  assert.equal(elements.summary.attrs['data-tone'], 'quiet');
});

test('failed refresh labels retained data as stale', async () => {
  const { context, state, elements } = setup();
  state.overview = snapshot(); state.overviewLoadedAt = '12:00:00';
  await context.loadOverview();
  assert.match(elements.summary.textContent, /предыдущий снимок/);
  assert.match(elements.summary.textContent, /12:00:00/);
  assert.match(elements.summary.textContent, /фильтры ещё не применены/);
  assert.equal(elements.summary.attrs['data-tone'], 'warning');
});

test('empty filtered result retains selected quarter and global options', () => {
  const { context, state, elements } = setup();
  state.filters.quarter = 'Q3';
  context.populateOverviewFilters(snapshot({ projectCount: 0 }));
  assert.equal(elements.quarterFilter.value, 'Q3');
  assert.equal(elements.quarterFilter.options.some(o => o.value === 'Q4'), true);
  assert.equal(state.filters.quarter, 'Q3');
});

test('attention renders server reasons and escapes user content', () => {
  const { context, elements } = setup();
  context.renderProjectOverview(snapshot({ attentionCount: 1, attentionProjects: [{
    id: 'x', name: '<img onerror=bad>', owner: 'QA', status: 'active', deadline: '2026-09-18',
    attentionReasons: ['Красное состояние', 'Просрочка'], risk: '<script>bad</script>'
  }] }));
  assert.match(elements.overviewProjectList.innerHTML, /Красное состояние/);
  assert.match(elements.overviewProjectList.innerHTML, /Просрочка/);
  assert.doesNotMatch(elements.overviewProjectList.innerHTML, /<script>|<img/);
});

test('date-only rendering never uses browser timezone and both lists have links', () => {
  const { context, elements } = setup();
  assert.equal(context.overviewDate('2026-09-19'), '19.09.2026');
  context.renderPortfolioSlice(snapshot({ dueSoonCount: 1, milestones: [{ id: 'x', name: 'X',
    status: 'active', deadline: '2026-10-03', daysUntilDeadline: 14, milestone: '' }] }));
  assert.match(elements.portfolioSliceTable.innerHTML, /data-project-id="x"/);
  assert.match(elements.portfolioSliceTable.innerHTML, /Через 14 дн/);
  assert.match(elements.portfolioSliceTable.innerHTML, /Контрольная точка не указана/);
});

test('empty portfolio, empty filter and no attention are different messages', () => {
  const { context } = setup();
  assert.match(context.overviewEmpty(snapshot({ totalProjectCount: 0, projectCount: 0 }), 'OK'), /пока нет/);
  assert.match(context.overviewEmpty(snapshot({ projectCount: 0 }), 'OK'), /Сбросить/);
  assert.equal(context.overviewEmpty(snapshot(), 'Отклонений нет'), 'Отклонений нет');
});

test('overview project link opens the common project card and keeps overview filters', async () => {
  const { context, state } = setup();
  state.filters = { quarter: 'Q3', status: 'active', health: 'red' };
  const calls = [];
  context.window.PMProjectsUI = { openCard: async (...args) => calls.push(args) };
  await context.openProjectFromOverview('x');
  assert.equal(JSON.stringify(calls), JSON.stringify([['x', 'overview']]));
  context.showView('overview');
  assert.equal(state.filters.quarter, 'Q3');
  assert.equal(state.filters.health, 'red');
});

test('overview delegates missing project handling to the common card view', async () => {
  const { context } = setup();
  let opened;
  context.window.PMProjectsUI = { openCard: async id => { opened = id; } };
  await context.openProjectFromOverview('missing');
  assert.equal(opened, 'missing');
});

test('train failure does not block a successful overview snapshot', async () => {
  const { context, state, elements } = setup();
  context.loadProjectCatalog = async () => {};
  context.fetchJson = async url => {
    if (url.includes('/portfolio/overview')) return snapshot();
    throw Error('HTTP 500');
  };
  await context.loadDashboard();
  assert.equal(state.overview.projectCount, 1);
  assert.match(elements.releaseTrainList.textContent, /Не удалось/);
});

test('HTML contains approved overview heading and keeps GitHub outside overview', () => {
  assert.match(html, /Ближайшие сроки/);
  assert.match(html, /Контрольные точки/);
  assert.match(html, /Состояние портфеля/);
  assert.match(html, /Качество данных/);
  assert.doesNotMatch(html, /Указанный бюджет/);
  assert.match(html, /<h1>Обзор<\/h1>/);
  assert.match(html, /id="overview-as-of">Состояние портфеля/);
  assert.match(html, /id="release-checks-panel" data-view-section="release"/);
  assert.doesNotMatch(html, /id="release-checks-panel"[^>]+data-view-section="overview"/);
  const sidebar = html.match(/<aside class="sidebar[\s\S]*?<\/aside>/)?.[0] ?? '';
  assert.doesNotMatch(sidebar, /Spring Boot \/ H2|API готов/);
  assert.doesNotMatch(html, /Причины рассчитаны на сервере|Отдельная дата контрольной точки/);
  const ids = [...html.matchAll(/\bid="([^"]+)"/g)].map(m => m[1]);
  assert.equal(new Set(ids).size, ids.length);
  assert.match(html, /data-stat="attentionCount">—/);
});

test('portfolio distribution explicitly renders unknown health and status', () => {
  const { context, get } = setup();
  context.renderStats(snapshot({ projectCount: 2, healthCounts: { green: 1, unknown: 1 },
    statusCounts: { active: 1, unknown: 1 } }));
  assert.match(get('overview-health-legend').innerHTML, /Неизвестное состояние/);
  assert.match(get('overview-status-counts').innerHTML, /Неизвестный статус/);
  assert.match(get('overview-health-bar').innerHTML, /data-health="unknown"/);
});

test('approved desktop and mobile overview layout rules are present', () => {
  assert.match(css, /grid-template-columns:\s*214px minmax\(0, 1fr\)/);
  assert.match(css, /background:\s*#11251f/);
  assert.match(css, /background:\s*#20483c/);
  assert.match(css, /grid-template-columns:\s*repeat\(4, minmax\(0, 1fr\)\)/);
  assert.match(css, /border:\s*1px solid #dde5e2/);
  assert.match(css, /@media \(max-width: 520px\)[\s\S]*grid-template-columns:\s*repeat\(2, minmax\(0, 1fr\)\)/);
  assert.doesNotMatch(css, /\.metric-card-budget\s*\{\s*grid-column:\s*1 \/ -1/);
});
