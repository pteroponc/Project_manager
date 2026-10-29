const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.join(__dirname, '../../main/resources/public');
const script = fs.readFileSync(path.join(root, 'navigation-ui.js'), 'utf8');
const app = fs.readFileSync(path.join(root, 'app.js'), 'utf8');
const html = fs.readFileSync(path.join(root, 'index.html'), 'utf8');
const css = fs.readFileSync(path.join(root, 'styles.css'), 'utf8');
const start = app.indexOf('function showView(');
const showView = app.slice(start, app.indexOf('\n}', start) + 2);

function setup(compact = true) {
  const document = { activeElement: null, events: {}, addEventListener(type, callback) { this.events[type] = callback; } };
  function node(textContent = '') {
    const classes = new Set();
    return { textContent, dataset: {}, attrs: {}, listeners: {}, hidden: false, parentElement: null,
      classList: { toggle(name, value) { if (value) classes.add(name); else classes.delete(name); }, contains: name => classes.has(name) },
      addEventListener(type, callback) { this.listeners[type] = callback; },
      setAttribute(name, value) { this.attrs[name] = value; }, getAttribute(name) { return this.attrs[name] ?? null; },
      removeAttribute(name) { delete this.attrs[name]; }, focus() { document.activeElement = this; },
      contains(target) { for (let n = target; n; n = n.parentElement) if (n === this) return true; return false; } };
  }
  const sidebar = node(), navigation = node(), toggle = node(), current = node();
  navigation.parentElement = sidebar; toggle.parentElement = sidebar;
  const buttons = Object.entries({ overview: 'Обзор', projects: 'Проекты', dashboards: 'Дашборды',
    boards: 'Доски', release: 'Релизы', docs: 'Документы' }).map(([view, label]) => {
    const button = node(label); button.dataset.view = view; button.parentElement = navigation; return button;
  });
  navigation.querySelectorAll = () => buttons;
  document.querySelector = () => sidebar;
  document.getElementById = id => ({ 'primary-navigation': navigation, 'navigation-toggle': toggle, 'navigation-current': current })[id];
  const media = { matches: compact, addEventListener(_type, callback) { this.changed = callback; } };
  const state = { activeView: 'overview' };
  const window = { matchMedia(query) { assert.equal(query, '(max-width: 1023px)'); return media; }, scrollTo() {} };
  const elements = { viewButtons: buttons, viewSections: [], releaseTrainMessage: node() };
  const context = vm.createContext({ window, document, state, elements,
    invalidateBoardContext() {}, setReleaseTrainEditorLoading() {}, renderBoardLanding() {} });
  vm.runInContext(script + '\n' + showView, context);
  return { context, window, document, sidebar, navigation, toggle, current, buttons, media, state, node };
}

test('compact disclosure starts closed and exposes all six existing sections', () => {
  const h = setup();
  assert.equal(h.navigation.hidden, true);
  assert.equal(h.toggle.getAttribute('aria-expanded'), 'false');
  assert.equal(h.sidebar.dataset.navigationReady, 'true');
  assert.equal(h.current.textContent, 'Обзор');
  h.toggle.listeners.click();
  assert.equal(h.navigation.hidden, false);
  assert.equal(h.toggle.getAttribute('aria-expanded'), 'true');
  assert.deepEqual(h.buttons.map(b => b.textContent), ['Обзор', 'Проекты', 'Дашборды', 'Доски', 'Релизы', 'Документы']);
});

test('accepted routes close compact menu and card/passport keep Projects active', () => {
  const h = setup();
  for (const view of ['projects', 'project-card', 'project-architecture', 'dashboards', 'boards', 'release', 'docs', 'overview']) {
    h.toggle.listeners.click();
    assert.equal(h.context.showView(view), true);
    assert.equal(h.navigation.hidden, true);
    const selected = h.buttons.filter(b => b.getAttribute('aria-current') === 'page');
    assert.equal(selected.length, 1);
    assert.equal(selected[0].dataset.view, view.startsWith('project') ? 'projects' : view);
    assert.equal(selected[0].classList.contains('active'), true);
  }
});

test('dirty-passport navigation guard runs before menu state and active-section changes', () => {
  const h = setup();
  h.context.showView('project-architecture');
  let continuation;
  h.window.PMPassportEditor = { allowNavigation(action) { continuation = action; return false; } };
  h.toggle.listeners.click();
  assert.equal(h.context.showView('boards'), false);
  assert.equal(h.navigation.hidden, false);
  assert.equal(h.current.textContent, 'Проекты');
  h.window.PMPassportEditor.allowNavigation = () => true;
  continuation();
  assert.equal(h.navigation.hidden, true);
  assert.equal(h.current.textContent, 'Доски');
});

test('Escape closes disclosure and restores keyboard focus to its button', () => {
  const h = setup(); h.toggle.listeners.click(); h.buttons[2].focus();
  let prevented = false;
  h.sidebar.listeners.keydown({ key: 'Escape', preventDefault() { prevented = true; } });
  assert.equal(prevented, true);
  assert.equal(h.navigation.hidden, true);
  assert.equal(h.document.activeElement, h.toggle);
});

test('outside click closes the menu without stealing focus from a content field', () => {
  const h = setup(); h.toggle.listeners.click(); const field = h.node(); field.focus();
  h.document.events.click({ target: field });
  assert.equal(h.navigation.hidden, true);
  assert.equal(h.document.activeElement, field);
});

test('responsive boundary keeps desktop navigation visible and does not hide focused links', () => {
  const h = setup(false);
  assert.equal(h.navigation.hidden, false);
  h.buttons[1].focus(); h.media.matches = true; h.media.changed();
  assert.equal(h.navigation.hidden, true);
  assert.equal(h.document.activeElement, h.toggle);
  h.media.matches = false; h.media.changed();
  assert.equal(h.navigation.hidden, false);
  assert.equal(h.document.activeElement, h.buttons[0]);
});

test('fallback markup and CSS preserve accessible flow without an overlay or duplicate menu', () => {
  assert.match(html, /id="navigation-toggle"[^>]+aria-controls="primary-navigation"[^>]+aria-expanded="true"/);
  assert.match(html, /<nav class="sidebar-nav" id="primary-navigation" aria-label="Основные разделы">/);
  assert.equal((html.match(/class="nav-item[^\"]*"/g) || []).length, 6);
  assert.match(css, /@media \(min-width: 1024px\)/);
  assert.match(css, /@media \(max-width: 1023px\)[\s\S]*position: static/);
  assert.match(css, /\.sidebar-nav\[hidden\] \{ display: none !important/);
});

test('CSS hiding a focused toggle before media callback still restores the active desktop link', () => {
  const h = setup();
  h.toggle.focus(); h.toggle.listeners.focus();
  h.media.matches = false;
  h.document.activeElement = h.node();
  h.toggle.listeners.blur();
  h.media.changed();
  assert.equal(h.navigation.hidden, false);
  assert.equal(h.document.activeElement, h.buttons[0]);
});
