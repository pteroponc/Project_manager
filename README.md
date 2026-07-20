# Project Manager

Локальная Spring Boot панель для управления портфелем проектов: список инициатив, бюджетный срез, статусы, KPI и доски Kanban, Scrum, Waterfall.

Доски поддерживают drag-and-drop, WIP-лимиты, приоритеты, теги, story points, блокеры, контроль просроченных задач, поиск и фильтры.

## Запуск

```powershell
mvn spring-boot:run
```

После старта приложение открывается на:

```text
http://localhost:8080
```

Проверка API:

```text
GET http://localhost:8080/api/health
GET http://localhost:8080/api/projects
GET http://localhost:8080/api/portfolio
```

## Данные

Приложение использует файловую H2 базу:

```text
data/project_manager.mv.db
```

При пустой базе создаются три демо-проекта с разными health-статусами и delivery-моделями.

## Релизный трейн

GitHub Actions запускает Maven-проверку для каждого pull request в `main` и для каждого коммита в `main`. После успешной сборки `main` JAR хранится как артефакт workflow 14 дней.

Релиз создаётся только пушем тега формата `v*`. Workflow повторно запускает проверки, собирает JAR с именем тега, создаёт GitHub Release и формирует заметки по изменениям автоматически.

```powershell
git tag v0.1.0
git push origin v0.1.0
```
