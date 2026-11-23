const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const publicDir = path.join(__dirname, '../../main/resources/public');
const html = fs.readFileSync(path.join(publicDir, 'index.html'), 'utf8');
const app = fs.readFileSync(path.join(publicDir, 'app.js'), 'utf8');
const board = html.slice(html.indexOf('<section class="panel boards-panel'),
  html.indexOf('<section class="view-section dashboard-workspace'));
const legacyStart = board.indexOf('<div id="legacy-project-board-ui" hidden aria-hidden="true">');
const visibleBoard = board.slice(0, legacyStart);
const legacyBoard = board.slice(legacyStart);

test('one visible Kanban view contains all four personal task states', () => {
  assert.ok(legacyStart > 0);
  assert.equal((visibleBoard.match(/class="personal-board"/g) || []).length, 1);
  assert.equal((visibleBoard.match(/id="personal-board-columns"/g) || []).length, 1);
  const stateSelect = visibleBoard.match(/<select name="columnKey">([\s\S]*?)<\/select>/);
  assert.ok(stateSelect);
  assert.deepEqual([...stateSelect[1].matchAll(/<option value="([^"]+)"/g)].map(match => match[1]),
    ['backlog', 'ready', 'in-progress', 'done']);
  assert.match(visibleBoard, /id="personal-board-legacy"/);
  assert.match(visibleBoard, /id="personal-task-create"/);
  assert.match(visibleBoard, /id="personal-task-form"/);
  assert.doesNotMatch(visibleBoard, /data-board-model=|id="board-project-select"|id="board-columns"/);
  assert.match(legacyBoard, /id="board-deck"/);
  assert.match(legacyBoard, /id="board-columns"/);
});

test('project links route to the shared view without loading a second board', () => {
  assert.match(app, /window\.PMBoardContext = \{\s*openProjectBoard: \(\) => showView\("boards"\)/);
  assert.match(app, /window\.PMPersonalBoardUI\?\.focusTask\(cardId\)/);
  assert.match(app, /!document\.getElementById\?\.\("personal-board-columns"\)/);
});
