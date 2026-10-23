/* PM-003C.2: read-only rendering of the server architecture aggregate. */
(() => {
  const content = document.getElementById("architecture-content");
  const panel = document.getElementById("project-architecture");
  const tabs = ["Сводка", "Паспорт", "Компоненты", "Интеграции", "Проверки", "Риски ИБ"];
  const dictionaries = {
    level: {LOW:"Низкий",MEDIUM:"Средний",HIGH:"Высокий",CRITICAL:"Критический"},
    stage: {NOT_STARTED:"Не начата",IN_DESIGN:"В проектировании",IN_REVIEW:"На рассмотрении",APPROVED:"Утверждена",CHANGES_REQUIRED:"Требуются изменения"},
    solution: {NEW_SOLUTION:"Новое решение",EXISTING_SOLUTION_CHANGE:"Изменение существующего решения",INTEGRATION:"Интеграция",MIGRATION:"Миграция"},
    data: {PUBLIC:"Публичные",INTERNAL:"Внутренние",CONFIDENTIAL:"Конфиденциальные",PERSONAL:"Персональные",PAYMENT:"Платёжные",COMMERCIAL_SECRET:"Коммерческая тайна"},
    environment: {DEV:"Разработка",TEST:"Тестирование",PREPROD:"Предпромышленная",PROD:"Промышленная",OTHER:"Другая"},
    component: {FRONTEND:"Клиентское приложение",BACKEND:"Серверное приложение",DATABASE:"База данных",QUEUE:"Очередь",EXTERNAL_SERVICE:"Внешний сервис",INFRASTRUCTURE:"Инфраструктура"},
    direction: {INBOUND:"Входящая",OUTBOUND:"Исходящая",BIDIRECTIONAL:"Двусторонняя"},
    interaction: {API:"API",EVENT:"События",FILE_EXCHANGE:"Файловый обмен",BATCH:"Пакетное",OTHER:"Другое"},
    auth: {NONE:"Без аутентификации",SERVICE_ACCOUNT:"Сервисная учётная запись",OAUTH2:"OAuth 2.0",MTLS:"mTLS",CERTIFICATE:"Сертификат",OTHER:"Другая"},
    integration: {PLANNED:"Запланирована",IN_PROGRESS:"В работе",READY:"Готова",BLOCKED:"Заблокирована"},
    check: {ARCHITECTURE_APPROVAL:"Согласование архитектуры",SECURITY_REQUIREMENTS:"Требования ИБ",DATA_CLASSIFICATION:"Классификация данных",THREAT_MODEL:"Модель угроз",SECURITY_ASSESSMENT:"Оценка безопасности",ACCESS_CONTROL:"Контроль доступа",LOGGING_MONITORING:"Журналирование и мониторинг",BACKUP_RECOVERY:"Резервное копирование и восстановление",LOAD_TESTING:"Нагрузочное тестирование",PRODUCTION_ADMISSION:"Допуск к промышленной эксплуатации"},
    checkStatus: {NOT_REQUIRED:"Не требуется",NOT_STARTED:"Не начата",IN_PROGRESS:"В работе",IN_REVIEW:"На рассмотрении",PASSED:"Пройдена",CHANGES_REQUIRED:"Требуются изменения",BLOCKED:"Заблокирована"},
    category: {ACCESS:"Доступ",DATA:"Данные",AVAILABILITY:"Доступность",INTEGRATION:"Интеграция",PROCESS:"Процесс",OTHER:"Другое"},
    risk: {OPEN:"Открыт",IN_PROGRESS:"В работе",ACCEPTED:"Принят",MITIGATED:"Снижен",CLOSED:"Закрыт"},
    readiness: {BLOCKED:"Заблокировано",ATTENTION_REQUIRED:"Требует внимания",NOT_ASSESSED:"Оценка не завершена",READY:"Архитектура и ИБ: проверки завершены"},
    entity: {PASSPORT:"Паспорт",CHECK:"Проверка",INTEGRATION:"Интеграция",RISK:"Риск ИБ"}
  };
  let projectId = null, snapshot = null, activeTab = 0, request = 0;
  const esc = value => escapeHtml(String(value));
  const text = value => value == null || value === "" ? "Не указано" : esc(value).replace(/\r?\n/g, "<br>");
  const label = (catalog, code) => code == null ? "Не указано" : Object.hasOwn(dictionaries[catalog],code) ? dictionaries[catalog][code] : "Неизвестное значение";
  const list = value => Array.isArray(value) ? value : [];
  const date = value => value == null ? "Не указана" : /^\d{4}-\d{2}-\d{2}$/.test(value) ? `${value.slice(8,10)}.${value.slice(5,7)}.${value.slice(0,4)}` : "Дата недоступна";
  const instant = value => {
    if (!value) return "Не указано";
    const moment = new Date(value);
    return Number.isNaN(moment.getTime()) ? "Дата недоступна" : esc(new Intl.DateTimeFormat("ru-RU", {timeZone:"UTC",dateStyle:"medium",timeStyle:"short"}).format(moment)) + " UTC";
  };
  const facts = rows => `<dl class="project-details">${rows.map(([title, value]) => `<div><dt>${title}</dt><dd>${value}</dd></div>`).join("")}</dl>`;
  const block = (title, body) => `<section class="project-milestones"><div class="section-head"><h2>${title}</h2></div>${body}</section>`;
  const empty = message => `<p class="empty-state compact">${message}</p>`;
  const codes = (values, catalog) => list(values).length ? list(values).map(code => label(catalog, code)).join(" · ") : "Не указаны";

  function onView(view) {
    if (view === "project-architecture") return;
    ++request;
    snapshot = null;
    panel.setAttribute("aria-busy", "false");
  }

  async function open(id, {keepTab = false} = {}) {
    projectId = id;
    if (!keepTab) activeTab = 0;
    snapshot = null;
    showView("project-architecture");
    window.PMProjectsUI?.architectureRoute(id);
    const current = ++request;
    panel.setAttribute("aria-busy", "true");
    content.innerHTML = '<p class="empty-state compact" role="status">Загружаем архитектуру и ИБ…</p>';
    try {
      const loaded = await fetchJson(`/api/projects/${encodeURIComponent(id)}/architecture`);
      if (current !== request || projectId !== id || state.activeView !== "project-architecture") return;
      if (loaded?.project?.id !== id || !loaded.assessment) throw new Error("Invalid architecture response");
      snapshot = loaded;
      render();
    } catch (error) {
      if (current !== request || state.activeView !== "project-architecture") return;
      content.innerHTML = error.status === 404
        ? '<p class="empty-state compact" role="status">Проект не найден.</p>'
        : '<div class="empty-state compact" role="alert">Не удалось загрузить архитектуру и ИБ. <button type="button" class="text-link" data-architecture-retry>Повторить</button></div>';
    } finally {
      if (current === request) panel.setAttribute("aria-busy", "false");
    }
  }

  function entityName(reason) {
    const collection = {CHECK:snapshot.checks,INTEGRATION:snapshot.integrations,RISK:snapshot.risks}[reason.entityType];
    const record = list(collection).find(row => row.id === reason.entityId);
    return record ? reason.entityType === "CHECK" ? label("check", record.checkType) : text(record.name) : label("entity", reason.entityType);
  }

  function summary() {
    const a = snapshot.assessment;
    const counts = [["Компоненты",snapshot.components],["Интеграции",snapshot.integrations],["Проверки",snapshot.checks],["Риски ИБ",snapshot.risks],["Классы данных",snapshot.dataClasses],["Среды",snapshot.environments]];
    const reasons = list(a.reasons).map(r => `<li><strong>${label("readiness",r.status)}</strong> · ${entityName(r)}: ${text(r.message)}</li>`).join("");
    const upcoming = list(a.upcomingChecks).map(c => `<article class="milestone-row"><div><strong>${label("check", c.checkType)}</strong><p>${date(c.plannedDate)} · ${label("checkStatus", c.status)}</p></div></article>`).join("");
    const accepted = list(a.acceptedHighRisks).map(r => {
      const risk = list(snapshot.risks).find(row => row.id === r.id);
      return `<article class="milestone-row"><div><h3>${risk ? text(risk.name) : "Риск ИБ"}</h3><p>Уровень: ${label("level",r.level)}</p><p>${text(r.justification)}</p><p>${r.acceptanceVerification === "UNVERIFIED" ? "Согласование принятия не проверено" : "Подтверждение согласования недоступно"}</p></div></article>`;
    }).join("");
    return block("Архитектурная готовность", facts([
      ["Оценка сервера",text(a.statusLabel)], ["Расчётная дата",date(a.calculationDate)], ["Часовой пояс",text(a.timeZone)]
    ]) + `<p>${a.officialGoLiveApproval === false ? "Расчёт не является официальным допуском к запуску." : "Сведения об официальном допуске требуют отдельной проверки."}</p>`) +
      block(`Причины оценки (${list(a.reasons).length})`, reasons ? `<ul>${reasons}</ul>` : empty("Сервер не вернул причин, препятствующих завершению оценки.")) +
      block("Состав паспорта", facts(counts.map(([name, rows]) => [name,String(list(rows).length)]))) +
      block("Ближайшие проверки", upcoming || empty("Сервер не вернул ближайших незавершённых проверок.")) +
      block(`Принятые высокие риски (${list(a.acceptedHighRisks).length})`, accepted || empty("Принятых высоких рисков в оценке нет."));
  }

  function passport() {
    const p = snapshot.passport;
    if (!p) return block("Паспорт отсутствует", empty("Архитектурный паспорт ещё не создан. Просмотр ничего не создаёт."));
    return block("Паспорт решения", facts([
      ["Версия документа",text(p.documentVersion)], ["Критичность",label("level",p.criticality)], ["Стадия",label("stage",p.stage)],
      ["Тип решения",label("solution",p.solutionType)], ["Ответственный архитектор",text(p.architect)], ["Краткое описание",text(p.solutionSummary)],
      ["Классы данных",codes(snapshot.dataClasses,"data")], ["Среды",codes(snapshot.environments,"environment")],
      ["Требования доступности",text(p.availabilityRequirement)], ["Требования восстановления",text(p.recoveryRequirement)],
      ["Дата пересмотра",date(p.lastReviewDate)], ["Создан",instant(p.createdAt)], ["Изменён",instant(p.updatedAt)]
    ]));
  }

  function components() { return block(`Компоненты (${list(snapshot.components).length})`, list(snapshot.components).map(c => block(text(c.name),facts([
    ["Тип",label("component",c.componentType)], ["Назначение",text(c.purpose)], ["Платформа",text(c.platform)],
    ["Критичность",label("level",c.criticality)], ["Внешний компонент",c.externalComponent === true ? "Да" : c.externalComponent === false ? "Нет" : "Не указано"], ["Порядок",text(c.position)]
  ]))).join("") || empty("Компонентов пока нет.")); }
  function integrations() { return block(`Интеграции (${list(snapshot.integrations).length})`, list(snapshot.integrations).map(i => block(text(i.name),facts([
    ["Система-источник",text(i.sourceSystem)], ["Система-получатель",text(i.targetSystem)], ["Направление",label("direction",i.direction)],
    ["Взаимодействие",label("interaction",i.interactionType)], ["Аутентификация",label("auth",i.authCategory)], ["Критичность",label("level",i.criticality)],
    ["Готовность",label("integration",i.readiness)], ["Передаваемые классы данных",codes(i.dataClasses,"data")],
    ["Ответственная сторона",text(i.responsibleParty)], ["Комментарий",text(i.comment)]
  ]))).join("") || empty("Интеграций пока нет.")); }
  function checks() { return block(`Архитектурные и ИБ-проверки (${list(snapshot.checks).length})`, list(snapshot.checks).map(c => block(label("check",c.checkType),facts([
    ["Обязательность",c.required === true ? "Обязательная" : c.required === false ? "Неприменимая" : "Не указано"], ["Статус",label("checkStatus",c.status)],
    ["Владелец",text(c.owner)], ["Плановая дата",date(c.plannedDate)], ["Фактическая дата",date(c.actualDate)], ["Комментарий / обоснование",text(c.comment)]
  ]))).join("") || empty("Проверки пока не созданы. Просмотр не инициализирует набор проверок.")); }
  function risks() { return block(`Риски ИБ (${list(snapshot.risks).length})`, list(snapshot.risks).map(r => block(text(r.name),facts([
    ["Описание",text(r.description)], ["Категория",label("category",r.category)], ["Вероятность",label("level",r.probability)], ["Влияние",label("level",r.impact)],
    ["Уровень (сервер)",label("level",r.level)], ["Статус",label("risk",r.status)], ["Владелец",text(r.owner)], ["Срок обработки",date(r.treatmentDueDate)],
    ["План обработки",text(r.treatmentPlan)], ["Дата закрытия",date(r.closedDate)], ["Обоснование принятия",text(r.acceptanceJustification)],
    ["Проверка согласования",r.acceptanceVerification === "UNVERIFIED" ? "Согласование принятия не проверено" : "Не указано"]
  ]))).join("") || empty("Рисков ИБ пока нет.")); }

  function render() {
    if (!snapshot) return;
    content.innerHTML = `<div class="project-detail-head"><div><p class="section-kicker">Архитектура и ИБ</p><h1>${text(snapshot.project.name)}</h1><p>${text(snapshot.project.owner)} · ${text(snapshot.project.quarter)}</p></div><button type="button" class="btn btn-ghost btn-small" data-architecture-retry>Обновить</button></div>
      <p>Только просмотр. Готовность архитектуры и ИБ оценивается отдельно от состояния проекта.</p>
      <nav class="project-detail-actions" aria-label="Разделы архитектуры">${tabs.map((name,i) => `<button type="button" class="btn ${activeTab === i ? "btn-primary" : "btn-secondary"} btn-small" data-architecture-tab="${i}" ${activeTab === i ? 'aria-current="page"' : ""}>${name}</button>`).join("")}</nav>
      ${!snapshot.passport ? '<p class="empty-state compact">Паспорт отсутствует. Данные не создаются автоматически.</p>' : ""}
      <div id="architecture-section-content">${[summary,passport,components,integrations,checks,risks][activeTab]()}</div>`;
  }
  content.addEventListener("click", event => {
    const retry = event.target.closest("[data-architecture-retry]");
    if (retry) return open(projectId,{keepTab:true});
    const tab = event.target.closest("[data-architecture-tab]");
    if (tab) { activeTab = Number(tab.dataset.architectureTab); render(); content.querySelector('[aria-current="page"]')?.focus(); }
  });
  document.getElementById("back-from-architecture").addEventListener("click", () => window.PMProjectsUI?.openCard(projectId,"architecture"));
  window.PMArchitectureUI = {open,onView};
})();
