# PM-001 — отчёт об исправлениях

Дата актуализации: 21.09.2026. Ветка: codex-dev. Коммит и push не выполнялись.

## Исправление сбоя приёмочного demo

Сбой воспроизведён 21.09.2026: `GET /api/portfolio/overview` отвечал HTTP 500, хотя `/api/health` продолжал отвечать 200. В журнале H2 зафиксировано `Table "PROJECTS" not found (this database is empty)`; одновременно исчезла таблица `RELEASE_TRAINS`.

Причина: приёмочный процесс был запущен с in-memory URL без `DB_CLOSE_DELAY=-1`. После длительной паузы пул закрыл последнее соединение, H2 уничтожила базу, а JVM и HTTP-сервер продолжили работу. Фронтенд корректно показал ошибку и не маскировал её.

Исправление: `application-demo.properties` и `application-smoke.properties` содержат `DB_CLOSE_DELAY=-1`; `IsolatedDatabaseGuard` дополняет этим параметром и безопасный URL из командной строки. Тест `inMemorySchemaSurvivesTemporaryLossOfAllConnections` создаёт таблицу, закрывает все соединения и подтверждает её доступность после нового подключения. Новая сборка на URL без явного параметра успешно вернула HTTP 200 с `/api/portfolio/overview`.

Нумерация ниже объединяет восемь окончательных правил и восемь технических исправлений аудита. Это карта выполненных изменений, а не дословная цитата отсутствующего в текущем сообщении списка из 16 пунктов.

## Карта исправлений

1. Жёлтый health сам по себе не включает проект в «Требуют внимания». PortfolioOverviewService.getOverview, строки 77–85. В интерфейсе подпись «Предупреждение». Жёлтый проект с просрочкой попадает в список по причине просрочки.
2. Done исключён из просрочки: условие !done && days < 0 в PortfolioOverviewService.getOverview, строка 82.
3. Done + red — противоречие данных, а не обычный риск. PortfolioOverviewService, строка 78; dataQualityProjects в DTO; app.js: renderOverviewQuality, строка 737.
4. Близкий срок — 0–14 календарных дней включительно. PortfolioOverviewService, строка 96. Отдельные тесты на вчера, сегодня, +14 и +15.
5. GitHub полностью исключён из «Обзора» и сохранён во вкладке «Релизы» с заголовком «Сборка и релиз приложения». index.html: `release-checks-panel` относится к `data-view-section="release"`.
6. Бюджет подписан «Указанный бюджет», валюта ₽. index.html, строки 66, 400, 520; app.js: renderStats и formatCurrency. Сумма рассчитывается в long, проверено 4 млрд ₽ без переполнения.
7. Единая расчётная дата Europe/Moscow. PortfolioOverviewService: Clock.withZone, один LocalDate.now(clock) на снимок; DTO calculationDate/timeZone. app.js: overviewDate и loadOverview не пересчитывают календарный срок в часовом поясе браузера.
8. Заголовок «Сроки проектов и контрольные точки». index.html, строка 75. app.js: renderPortfolioSlice отдельно обозначает срок проекта и отсутствие собственной даты контрольной точки.
9. Достоверные KPI и различение неизвестного значения и нуля. PortfolioOverviewService: long-суммы, покрытие исходными значениями, nullable агрегаты, группы статусов; app.js: renderStats, строка 452. Проблемные задачи не показаны как 0; освоение бюджета, перерасход и прогноз не рассчитываются.
10. Независимая загрузка источников и ошибки. app.js: loadDashboard, loadOverview, loadTrainSnapshot. Ошибка поездов/GitHub не блокирует портфель. Первая ошибка показывает недоступность, ошибка обновления помечает предыдущий снимок устаревшим; повтор — кнопкой «Обновить».
11. Стабильные фильтры и защита от гонок. ProjectRepository: глобальные списки вариантов; app.js: populateOverviewFilters, счётчик overviewRequest. Ответ старого запроса не перезаписывает новый срез; выбранный вариант сохраняется при пустой выборке.
12. Переход к существующей карточке из обоих списков, фокус и возврат с фильтрами. app.js: overviewProjectLink, bindOverviewLinks, openProjectFromOverview, обработчик return-overview. GET /api/projects/{id}; форма создания закрывается; недоступный проект даёт понятную ошибку. styles.css: .view-section.active.is-collapsed.
13. Серверные причины внимания, неизвестные данные и пустые состояния. PortfolioOverview DTO: attentionReasons/dataQualityIssues; app.js: renderProjectOverview, renderOverviewQuality, overviewEmpty. Две причины не удваивают количество проектов; неизвестные значения не исправляются; пользовательский текст экранируется.
14. Единый снимок для всех портфельных блоков. PortfolioController: GET /api/portfolio/overview; PortfolioOverviewService: readOnly + REPEATABLE_READ. app.js: loadOverview использует только новый контракт для «Обзора», без старого int-агрегата PortfolioSnapshot.
15. Фильтрация в БД и сохранение остальных экранов. ProjectRepository.findFiltered, ProjectService.findProjects, ProjectController.getProjects поддерживают пересечение quarter/health/status. Старые двухаргументные методы сохранены. app.js: loadProjectCatalog загружает полный каталог отдельно, фильтрация «Обзора» не заменяет список проектов доски.
16. Автоматические и браузерные проверки обновлены. PortfolioOverviewIntegrationTest: 12 детерминированных интеграционных тестов с фиксированными часами. src/test/js/overview.test.cjs: 12 проверок реальных функций app.js с адаптером DOM. styles.css: адаптация карточек/фильтров и высоты sidebar на мобильном экране.

## Зачем изменены ProjectController и ProjectService

ProjectController принимает необязательный status со значением all по умолчанию. ProjectService передаёт три фильтра в единый запрос репозитория. Это даёт одинаковую семантику выборки проектам и портфелю, сохраняет прежние вызовы без status. CRUD, Entity и схема хранения не изменены. Сам «Обзор» получает данные через PortfolioController и PortfolioOverviewService.

PortfolioService сохраняет старый контракт /api/portfolio и двухаргументную перегрузку; добавлена передача status. Старые risky/int-показатели не используются новым «Обзором»; их семантика для прежних потребителей в этой задаче не менялась.

## Проверки и результаты

- mvn clean verify: BUILD SUCCESS, 37 Java-тестов, failures 0, errors 0, skipped 0. Финальная сборка закончилась 21.09.2026 в 13:15:28 МСК.
- node --test src/test/js/overview.test.cjs: 12 passed, 0 failed.
- Итого 49 автоматических тестов; 37 входят в Maven, 12 запускаются отдельной командой Node.
- node --check src/main/resources/public/app.js: exit 0.
- git diff --check: exit 0. Предупреждения Git о LF/CRLF не являются ошибками проверки.
- Браузер: новая сборка, явный demo-профиль, только `jdbc:h2:mem:pmdemo_pm001_design;MODE=PostgreSQL;DB_CLOSE_DELAY=-1`, bind `127.0.0.1:18087`. `/api/portfolio/overview` вернул HTTP 200; H2 Console вернула 404.
- Проверены отображение KPI, обе причины/списки в автоматике, реальный переход из списка сроков, скрытая форма создания, возврат с выбранным кварталом, изоляция ошибки GitHub.
- 1440/768/390 px: ширина документа совпадает с доступной шириной viewport (1425/753/375 с учётом scrollbar), горизонтального переполнения нет. Высота мобильной sidebar больше не принудительно равна viewport.
- Ошибки первой загрузки, устаревшие ответы, повторная загрузка, пустые состояния и недоступный проект проверены JS-тестами с DOM-адаптером; это не полноценные E2E-тесты браузера.
- Интеграционный тест сравнивает записи до и после GET. Существующие тесты защиты данных PM-000 также проходят.

Рабочая файловая БД не открывалась и не запускалась. Старый JAR не запускался. Модель хранения не менялась. Изменена только конфигурация изолированных demo/smoke datasource: добавлено удержание in-memory БД до завершения JVM. Временная сборка и логи находятся в игнорируемых `target/` и `tmp/`.

## Файлы

- src/main/java/project_manager/domain/PortfolioOverview.java — новый DTO снимка, не JPA-модель.
- src/main/java/project_manager/safety/IsolatedDatabaseGuard.java — гарантирует срок жизни изолированной in-memory БД.
- src/main/java/project_manager/repository/ProjectRepository.java — фильтры и варианты фильтров.
- src/main/java/project_manager/service/PortfolioOverviewService.java — единые правила расчёта снимка.
- src/main/java/project_manager/service/PortfolioService.java — совместимая перегрузка status для прежнего API.
- src/main/java/project_manager/service/ProjectService.java — общая фильтрация проектов.
- src/main/java/project_manager/web/PortfolioController.java — новый endpoint обзора.
- src/main/java/project_manager/web/ProjectController.java — необязательный фильтр status.
- src/main/resources/public/app.js — загрузка, фильтры, отображение, ошибки и переходы.
- src/main/resources/public/index.html — структура, заголовки, состояния и возврат.
- src/main/resources/public/styles.css — адаптивность, фокус и скрытие формы.
- src/main/resources/application-demo.properties — устойчивый in-memory URL demo-профиля.
- src/main/resources/application-smoke.properties — устойчивый in-memory URL smoke-профиля.
- src/test/java/project_manager/PortfolioOverviewIntegrationTest.java — 12 новых тестов снимка/API.
- src/test/java/project_manager/safety/TestDatabaseGuardTest.java — регрессия потери схемы после закрытия соединений.
- src/test/js/overview.test.cjs — 12 новых frontend-тестов.
- src/test/java/project_manager/ProjectManagerApiIntegrationTest.java — Git показывает файл изменённым из-за представления окончаний строк; содержательной разницы с HEAD в git diff нет, исходные три теста сохранены.
- docs/PM-001-overview.md — исходный план с указанием приоритета окончательного контракта.
- docs/PM-001-implementation-report.md — этот отчёт.

## Границы проверки

Проверки на пользовательских данных не проводились. Реальный успешный ответ GitHub недоступен в проверочном окружении; проверено отображение ошибки без отказа портфеля. Полная пользовательская приёмка всех экранов не подменяется указанными автоматическими и браузерными проверками.

Текущая модель хранит budget/progress как int: настоящий ноль нельзя отличить от исторически подставленного нуля, если он уже сохранён в БД. Отсутствие/невалидность агрегируемых данных не превращается в искусственный ноль на уровне нового снимка.

У done с будущим плановым сроком дата остаётся в блоке сроков при попадании в 14 дней: согласованное исключение done относится к просрочке. Собственной даты контрольной точки в модели нет; срок проекта не выдаётся за неё.
