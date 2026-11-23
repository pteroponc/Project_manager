const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const publicDir = path.join(__dirname, '../../main/resources/public');

test('the shared API client adds the marker to every write and preserves other headers', async () => {
  const app = fs.readFileSync(path.join(publicDir, 'app.js'), 'utf8');
  const source = app.slice(app.indexOf('async function fetchJson('), app.indexOf('function boardCardsUrl('));
  const calls = [];
  const context = vm.createContext({ Headers, async fetch(url, options) {
    calls.push({ url, options });
    return { status: 204 };
  } });
  vm.runInContext(source, context);
  for (const method of ['POST', 'PUT', 'PATCH', 'DELETE']) {
    await context.fetchJson('/api/projects/demo', { method, headers: { 'Content-Type': 'application/json' } });
  }
  await context.fetchJson('/api/my-day');
  assert.deepEqual(calls.slice(0, 4).map(({ options }) => [
    options.method, options.headers.get('X-PM-Write'), options.headers.get('Content-Type')
  ]), ['POST', 'PUT', 'PATCH', 'DELETE'].map(method => [method, '1', 'application/json']));
  assert.equal(calls[4].options, undefined);
});

test('the start page uses system fonts without the Google font hosts', () => {
  const html = fs.readFileSync(path.join(publicDir, 'index.html'), 'utf8');
  const css = fs.readFileSync(path.join(publicDir, 'styles.css'), 'utf8');
  assert.doesNotMatch(html, /fonts\.(googleapis|gstatic)\.com/);
  assert.match(css, /--font-family:\s*system-ui/);
});
