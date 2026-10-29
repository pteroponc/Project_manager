const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const css = fs.readFileSync(path.join(__dirname, '../../main/resources/public/styles.css'), 'utf8');
const root = css.match(/:root\s*\{([^}]+)\}/)[1];
const tokens = Object.fromEntries([...root.matchAll(/(--[\w-]+)\s*:\s*([^;]+);/g)].map(m => [m[1], m[2].trim()]));
const resolve = name => tokens[name].replace(/var\((--[\w-]+)\)/g, (_, ref) => resolve(ref));
const luminance = hex => hex.replace('#', '').match(/../g).map(channel => {
  const c = parseInt(channel, 16) / 255;
  return c <= .04045 ? c / 12.92 : ((c + .055) / 1.055) ** 2.4;
}).reduce((sum, channel, i) => sum + channel * [.2126, .7152, .0722][i], 0);
const contrast = (a, b) => {
  const values = [luminance(resolve(a)), luminance(resolve(b))];
  return (Math.max(...values) + .05) / (Math.min(...values) + .05);
};

test('approved palette and Manrope are centralized in design tokens', () => {
  for (const [name, value] of Object.entries({ '--primary': '#21A038', '--primary-hover': '#1B8A31',
    '--sidebar': '#12261A', '--bg': '#F4F7F4', '--mint': '#EAF7EE', '--text': '#1C1F1D',
    '--muted': '#5F6B63', '--warning': '#C58A1A', '--danger': '#C93C32' })) {
    assert.equal(resolve(name).toUpperCase(), value);
  }
  assert.match(resolve('--font-family'), /^"Manrope"/);
  assert.match(css, /font-family:\s*var\(--font-family\)/);
});

test('component colors use tokens and all custom-property references resolve', () => {
  const components = css.replace(/:root\s*\{[^}]+\}/, '');
  assert.doesNotMatch(components, /#[0-9a-f]{3,8}\b|rgba?\(/i);
  for (const ref of css.matchAll(/var\((--[\w-]+)/g)) {
    assert.ok(ref[1] in tokens || ref[1] === '--share', ref[1]);
  }
});

test('text tokens meet 4.5:1 for normal, hover, semantic and disabled states', () => {
  const pairs = [['--on-primary', '--primary'], ['--on-primary', '--primary-hover'],
    ['--text', '--panel'], ['--text', '--bg'], ['--muted', '--panel'], ['--muted', '--bg'],
    ['--muted', '--mint'], ['--primary-text', '--panel'], ['--primary-text', '--mint'],
    ['--success-text', '--success-soft'], ['--warning-text', '--warning-soft'],
    ['--danger-text', '--danger-soft'], ['--panel', '--danger'],
    ['--info-text', '--info-soft'], ['--sidebar-text', '--sidebar'],
    ['--sidebar-text', '--sidebar-active'], ['--sidebar-muted', '--sidebar'],
    ['--disabled-text', '--disabled-bg']];
  for (const [text, background] of pairs) {
    assert.ok(contrast(text, background) >= 4.5, `${text} / ${background}: ${contrast(text, background)}`);
  }
});

test('focus and control boundaries meet 3:1 against their surrounding surfaces', () => {
  for (const [control, background] of [['--focus-ring', '--panel'], ['--focus-ring', '--bg'],
    ['--line-strong', '--panel'], ['--line-strong', '--bg'], ['--sidebar-accent', '--sidebar'],
    ['--primary', '--panel'], ['--primary-hover', '--panel']]) {
    assert.ok(contrast(control, background) >= 3, `${control} / ${background}`);
  }
  assert.match(css, /:focus-visible\s*\{\s*outline: 3px solid var\(--focus-ring\)/);
  assert.match(css, /button:disabled:hover/);
  assert.match(css, /@media \(forced-colors: active\)/);
  assert.ok(contrast('--warning-text', '--panel') >= 3);
  assert.match(css, /data-health="yellow"\] \{ box-shadow: var\(--shadow-warning\)/);
  assert.equal(resolve('--shadow-warning'), 'inset 0 0 0 1px ' + resolve('--warning-text'));
});

test('warning, danger, success and unknown status roles remain distinct', () => {
  assert.equal(new Set(['--warning', '--danger', '--success', '--unknown'].map(resolve)).size, 4);
  for (const semantic of ['success', 'warning', 'danger']) {
    assert.match(css, new RegExp('color:\\s*var\\(--' + semantic + '-text\\)'));
  }
});
