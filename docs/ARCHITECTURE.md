# Архитектура AS-IS

Дата: 14.11.2026. AS-IS baseline: `a1f5b8d6f8b2c8b43c6685c78a52abc2f210f07e` (`codex-dev`).

Источник: принятый архив PM-DOC-001.1, 06-architecture-as-is.md, 12-gaps-and-questions.md. Это краткое описание, не новая архитектура.

Java 21, Spring Boot 3.5.6, Maven, MVC, Bean Validation, JPA/Hibernate, H2. Frontend — статический HTML/CSS/vanilla JS, модули используют общий app state и same-origin API. Слои controller → service → repository. Проект — центральная сущность, портфель и проектная сводка используют связанные сервисы и оценки.

Защита изменений: транзакции, ProjectWriteLock, JPA @Version/CAS в новых контрактах, UUID экземпляра паспорта; legacy PUT имеет ограниченную защиту от устаревших клиентских записей. Два формата ошибок API.

Пилот: отдельный VPS, systemd, Nginx, HTTPS, Basic Auth, UFW, backend loopback, H2 in-memory, отключённая Console. Данные сбрасываются при restart. Обычный локальный профиль отличается от защищённого demo. Инструкции deployment остаются в deploy/demo/.

TO-BE: не утверждена. Предстоит определить только минимальные изменения для Главной, метрик, управленческих задач, обязательств, ИБ, отчётности и одного ИИ-сценария. Не выбирались провайдер ИИ, новая БД, инфраструктура или миграции.
