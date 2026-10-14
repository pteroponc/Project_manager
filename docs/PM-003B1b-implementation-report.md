# PM-003B1b — отчёт для ревью

Дата: 14.10.2026, Europe/Moscow. Ветка: `codex-dev`.
Исходный HEAD: `d62724f5556e428d40b94d830d18e6df8d57d581`; исходное дерево было чистым.
Коммит и push не выполнены. Рабочая файловая БД и её копии не открывались.

## Реализация

Добавлены восемь JPA-сущностей и восемь репозиториев, а также три `@Embeddable`-ключа.
Состав столбцов, SQL-типы, длины, nullable, PK/FK, UNIQUE и семь именованных индексов
соответствуют разделу 2 проектного документа. `@Version` задан для паспорта, компонента,
интеграции, проверки и риска. Пять версий имеют `BIGINT NOT NULL DEFAULT 0` и CHECK >= 0;
позиция компонента имеет CHECK >= 0. 25 CHECK-ограничений покрывают закрытые каталоги,
версии и позицию. Уровень риска `level` отсутствует в сущности и схеме.

Связи не имеют каскадов или orphanRemoval. Наборы хранятся отдельными строками с составным PK.
Паспорт связан с существующим проектом общим PK/FK; остальные записи — с паспортом, а
классы интеграции — с интеграцией. Даты представлены `LocalDate`, UTC-моменты — `Instant`.
Lifecycle callbacks и автоматическое изменение дат при чтении не добавлены.

V004 находится в `db/manual`, вне ресурсов Spring. Полная precondition проверяет четыре
старые таблицы, столбцы и defaults, ключи, правила FK, индексы и отсутствие новых объектов.
Маркер `pm_architecture_schema_migrations` создаётся первым DDL, запись `V004` вставляется
после создания восьми таблиц. Postcondition проверяет старую и новую схему, точные выражения
CHECK, ключи, именованные индексы и маркер. `IF NOT EXISTS` нет. V002 и V003 не изменены.

Три существующих теста, запускающие полный Hibernate validate, дополнены применением
последующих миграций на памяти: новая JPA-модель требует всю схему V004. Их прежние
проверки V002/V003 и конкурентной сериализации сохранены.

## Проверки

| Проверка | Результат |
|---|---|
| `mvn clean verify` | Успешно: 96 тестов, 0 failures, 0 errors |
| `ArchitectureSchemaMigrationIntegrationTest` | 13 тестов, 0 failures, 0 errors |
| JS: `overview.test.cjs`, `projects-ui.test.cjs` | 29 passed, 0 failed |
| `node --check` для `app.js` и `projects-ui.js` | exit 0 |
| `git diff --check` и отдельная проверка новых файлов | Ошибок пробелов нет |

Новые тесты используют уникальные одноразовые `jdbc:h2:mem:pmtest_archschema_*`.
Полная регрессия сохраняет защиту PM-000 и использует только тестовые in-memory подключения.

Проверено полное совпадение прежних значений после V002 и всех старых строк после V003/V004,
включая проекты, карточки, поезда, точки, ID, версии, даты, нули и максимальный INTEGER-бюджет.
Восемь новых таблиц после миграции пусты: паспорта и проверки автоматически не создаются.

11 негативных вариантов: нет V003; изменён тип поезда; отсутствует FK точки; лишний столбец;
FK карточек с cascade; неправильный индекс; неправильный default версии; частичная новая
таблица; только первая таблица маркера; уже применённая V004; неправильный состав PK.
При отказе сравниваются все старые строки и `SCRIPT NODATA` до/после — DDL не произошло.

На мигрированной памяти Hibernate validate проходит. Через все восемь репозиториев
сохранены и прочитаны сущности, nullable-поля и наборы. Три последовательных чтения сравнивают
полный снимок старых и новых таблиц, дат, версий и маркера. Отдельная явная правка компонента
повышает только его версию, версия паспорта остаётся 0.

Прямо проверены восемь FK с NO ACTION/RESTRICT, UNIQUE типа проверки, состав индексов
и CHECK-выражения. Нарушения FK, каталогов, версии, позиции, NOT NULL, длины ID и составного
PK отклоняются. SQL-удаление проекта, паспорта и интеграции с зависимостями запрещено.
JPA-удаление паспорта с детьми также отклоняется, а снимок после rollback остаётся прежним.
Сообщение FK при этом ожидаемо присутствует в тестовом логе отрицательного сценария.

## Границы и ограничения

API, формула готовности, сервисы изменения, расширенный DELETE и frontend не реализованы.
Автоматический мигратор, стартовые SQL и изменения защиты PM-000 отсутствуют.
Кардинальность «хотя бы один класс интеграции» и проверки согласованности межполей остаются
для будущей атомарной валидации PM-003B3; каталог/PK/FK уже проверяются базой.

V004 рассчитана на точную схему после V003 и H2 2.3.232. Схема рабочей базы не обследовалась.
Новая сборка с validate требует V004, поэтому её запуск на рабочей базе до согласованной
миграции запрещён. Ручная репетиция на копии и рабочее применение требуют отдельного решения.
Инструкция описывает остановку всех записей и восстановление проверенного backup после
частичного DDL; автоматического обратного SQL нет.

## Полный состав: 26 файлов

```text
 M src/test/java/project_manager/safety/BoardCardsProjectFkMigrationIntegrationTest.java
 M src/test/java/project_manager/safety/ProjectCardSerializationIntegrationTest.java
 M src/test/java/project_manager/safety/ProjectSchemaMigrationIntegrationTest.java
?? db/manual/V004__architecture_security.sql
?? docs/PM-003B1b-implementation-report.md
?? docs/PM-003B1b-manual-migration.md
?? src/main/java/project_manager/domain/ArchitectureCheckEntity.java
?? src/main/java/project_manager/domain/ArchitectureComponentEntity.java
?? src/main/java/project_manager/domain/ArchitectureDataClassEntity.java
?? src/main/java/project_manager/domain/ArchitectureDataClassId.java
?? src/main/java/project_manager/domain/ArchitectureEnvironmentEntity.java
?? src/main/java/project_manager/domain/ArchitectureEnvironmentId.java
?? src/main/java/project_manager/domain/ArchitectureIntegrationEntity.java
?? src/main/java/project_manager/domain/ArchitecturePassportEntity.java
?? src/main/java/project_manager/domain/IntegrationDataClassEntity.java
?? src/main/java/project_manager/domain/IntegrationDataClassId.java
?? src/main/java/project_manager/domain/SecurityRiskEntity.java
?? src/main/java/project_manager/repository/ArchitectureCheckRepository.java
?? src/main/java/project_manager/repository/ArchitectureComponentRepository.java
?? src/main/java/project_manager/repository/ArchitectureDataClassRepository.java
?? src/main/java/project_manager/repository/ArchitectureEnvironmentRepository.java
?? src/main/java/project_manager/repository/ArchitectureIntegrationRepository.java
?? src/main/java/project_manager/repository/ArchitecturePassportRepository.java
?? src/main/java/project_manager/repository/IntegrationDataClassRepository.java
?? src/main/java/project_manager/repository/SecurityRiskRepository.java
?? src/test/java/project_manager/safety/ArchitectureSchemaMigrationIntegrationTest.java
```

ZIP для ревью содержит эти файлы с сохранением путей. Базы, копии, сборки, логи и
временные генераторы в ZIP не включены.
