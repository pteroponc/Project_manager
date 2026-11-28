# ITERATION-01.6A — этап A, отчёт для ревью

Статус: реализация подготовлена в изолированном detached worktree. Приёмка владельцем, staging, commit, push и развёртывание не проводились. Этапы B–F, W1–W3, DEC-068, DEMO-DATA-001, DOC-CONSOLIDATION-001, UX-008–012 и OFFLINE-01–08 этим результатом не закрываются.

## Входы и preflight

- Вход: `ITERATION-01.6A-RECOVERY-R1 (1).zip`, SHA256 `ea60af97193b3cc50d05ca7e430195c82f118c9ede3b8fecb4796c187e22e2ec` — совпал с командой владельца. Девять записей ZIP; 8/8 в SHA256SUMS — PASS. Во вложенных ADR.4 и REVIEW ZIP — 13/13 и 1/1 — PASS. Прочитаны CODEX-OPT014.md, START-HERE.md, TASK.md, ADR.4 и OPT-09.3-REVIEW.md. OPT-014 принят.
- Из старого worktree чтением подтверждены общий Git-каталог `C:/Users/Polina/Documents/Codex/2026-06-08/project-manager/work/project-manager/.git`, тип baseline `commit`, список зарегистрированных worktree. Переменных `GIT_DIR`, `GIT_WORK_TREE`, `GIT_INDEX_FILE` в процессе не было. Новый путь до создания отсутствовал и не был зарегистрирован.
- Новый каталог: `C:/Users/Polina/.codex/worktrees/pm-iteration-01-6a-clean/project-manager`. Команда `git --git-dir=<проверенный общий каталог> worktree add --detach <новый каталог> cf5f9369ff4521b09c987dc2667a1944f9095b26` — exit 0. `git rev-parse --is-inside-work-tree` → `true`; `--show-toplevel` → точный новый путь; `HEAD` → точный baseline.
- `git status --porcelain=v1 -b --untracked-files=all` в ограниченном sandbox ошибочно возвращал `this operation must be run in a work tree`. Тот же немодифицирующий вызов с разрешённым доступом к новому каталогу — exit 0, `## HEAD (no branch)`, без изменённых, staged и untracked файлов. Дальнейшие проверки Git выполнялись с тем же доступом. Старый worktree и основной checkout не изменялись.

Отклонение от процедуры R1: при подготовке браузерной проверки был прочитан старый `tmp/iteration-01.3a-browser.cjs` из прежнего worktree. TASK.md разрешал использовать прежний worktree только для чтения Git-метаданных. Этот файл не менялся и не входит в реализацию или архив ревью. Ограничение чтения было нарушено; факт подлежит учёту при ревью результата.

## Эталон и карта потребителей до дельты

До первой правки выполнен `ProjectDashboardIntegrationTest` на baseline (exit 0, фиксированный Clock `2026-11-10T21:30:00Z`, дата расчёта `2026-11-11` Europe/Moscow, изолированная H2 и фиксированный seed теста). Проверялись HTTP 200/404/500, старые поля `project`, `tasks`, `milestones`, прежняя семантика колонок модели и SQL равенство до/после GET для `projects`, `board_cards`, `project_milestones`.

Для точного сравнения после дельты исходный baseline commit извлечён во временную копию вне всех worktree. Один и тот же интеграционный тест с тем же seed, Clock и локальным HTTP-контекстом выполнился в baseline и после дельты (оба exit 0). Сырые ответы сохранены как `baseline-api.json` и `after-api.json` в архиве ревью. После исключения только изменяющегося `generatedAt` и нового `dash03` старые поля JSON совпали полностью (`legacyFieldsEqual=True`). Новое поле присутствует; `dash03.open=5` для этого seed. Тестовая копия baseline не является дополнительным worktree и не меняет зарегистрированные checkout.

Потребители сохранённого GET `/api/projects/{id}/dashboard`: `projects-ui.js` (один запрос выбранной карточки, защита от позднего ответа/ошибки, refresh/retry, прямой URL), `project-summary-ui.js` (чтение старых `tasks` и `milestones`), тесты `ProjectDashboardIntegrationTest` и `projects-ui.test.cjs`. Ссылка задачи использует существующий `PMBoardContext.openTask` и маршрут Доски. Портфельный `/api/portfolio/overview`, «Обзор», персональная Доска и её `legacyTasks` используют отдельные маршруты и остаются вне изменённой проекции. Новых writers и прав не добавлено.

## Дельта A

В существующий ответ GET добавлено поле `dash03` без удаления или изменения старых полей. Проекция читает задачи с точным `projectId`, учитывает уникальные ID и только `backlog`, `ready`, `in-progress`, `done`; открыты первые три. Она показывает четыре счётчика, открытые, high/critical, просроченные, неизвестные даты/приоритеты, ближайшие от сегодня и критичные без срока. Просрочка — только для открытых с датой строго раньше серверной даты Europe/Moscow; ближайшие сортируются по сроку, приоритету, ID. `legacyTasks`, личные и чужие задачи исключены из `dash03`, но исторические карточки доступны через прежний маршрут. Процент проекта из задач не вычисляется.

Карточка проекта отображает `dash03` в отдельном read-only блоке; прежняя сводка по колонкам модели сохранена. Существующая защита запроса карточки и прямой маршрут продолжают использовать один GET. Для 320 px добавлено компактное размещение счётчиков.

## Проверки

| Проверка | Результат |
|---|---|
| Baseline `ProjectDashboardIntegrationTest` до дельты | PASS, exit 0 |
| Baseline/after HTTP JSON, одинаковый seed и Clock | PASS, оба exit 0; старые поля идентичны после исключения `generatedAt`, новое `dash03` присутствует |
| После дельты `ProjectDashboardIntegrationTest` | PASS, exit 0; два проекта, scrum/waterfall, четыре состояния, даты, приоритет, изоляция и SQL read-only |
| `node --test src/test/js/projects-ui.test.cjs` | PASS, 52/52 |
| Полный `mvn -q verify` | PASS, exit 0; 545 тестов, 0 failures, 0 errors, 1 skipped (`ArchitectureBrowserServerTest.serveRealApplicationForBrowserReview`, opt-in) |
| Полный `node --test src/test/js/*.test.cjs` | PASS, 317/317 |
| Локальный HTTP на in-memory demo БД | PASS: GET выбранного проекта 200, `project.id` и `dash03` проверены; снимок в архиве ревью |
| Headless Edge, 1440 / 390 / 320 px | PASS: блок видим, горизонтального переполнения и JS исключений нет; скриншоты в архиве |
| Переход к исходной задаче → browser back → reload карточки | PASS в браузере на demo seed; после возврата и перезагрузки блок получен снова |
| `git diff --check` | PASS, exit 0 |

Права отдельных ролей и production БД — NOT VERIFIED: локальный HTTP-контекст теста одинаков до/после, но demo не воспроизводит отдельную ролевую авторизацию. Изолированная H2 после GET проверена на равенство строк в тесте; предметные записи не менялись. Приёмка и релизные гейты остаются открытыми.
