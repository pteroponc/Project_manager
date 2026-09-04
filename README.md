# Project Manager

Локальная Spring Boot панель для управления портфелем проектов: список инициатив, проектные дашборды, бюджетный срез, статусы, KPI, доски Kanban, Scrum, Waterfall и релизные поезда.

## Запуск

```powershell
mvn spring-boot:run
```

После старта приложение открывается на:

```text
http://localhost:8080
```

## API

```text
GET  http://localhost:8080/api/health
GET  http://localhost:8080/api/projects
GET  http://localhost:8080/api/portfolio
GET  http://localhost:8080/api/delivery-models
GET  http://localhost:8080/api/release-trains
GET  http://localhost:8080/api/release-trains/snapshot
POST http://localhost:8080/api/release-trains
```

## Доски

Доски поддерживают drag-and-drop, WIP-лимиты, приоритеты, теги, story points, блокеры, контроль просроченных задач, поиск и фильтры.

## Project Dashboards

Проектные дашборды строятся из текущего среза проектов и показывают health mix, delivery mix, status flow, лидеров по бюджету, ближайшие дедлайны и risk watch. Данные обновляются вместе с фильтрами квартала и health-статуса.

## Интерфейс

Главная страница разбита на рабочие разделы через левое меню: Overview, Projects, Dashboards, Boards, Release Train и Docs. Overview показывает только ключевой срез, а тяжёлые рабочие зоны открываются отдельно, чтобы интерфейс не превращался в длинную ленту.

## Release Train

Релизный поезд хранит cadence, квартал, planned release date, code freeze, QA freeze, go-live, capacity, committed scope, readiness, блокеры, риски и решение до freeze. На главном экране есть сводка по готовности, capacity и блокерам, а также карточки поездов с дорожкой `Code freeze -> QA freeze -> Go-live`.

## Данные

Приложение использует файловую H2 базу:

```text
data/project_manager.mv.db
```

При пустой базе создаются демо-проекты, два демо release train и данные для базовых дашбордов.

## Релизный трейн

GitHub Actions запускает Maven-проверку для каждого pull request в `main` и для каждого коммита в `main`. После успешной сборки `main` JAR хранится как артефакт workflow 14 дней.

Релиз создаётся только пушем тега формата `v*`. Workflow повторно запускает проверки, собирает JAR с именем тега, создаёт GitHub Release и формирует заметки по изменениям автоматически.

```powershell
git tag v0.1.0
git push origin v0.1.0
```
