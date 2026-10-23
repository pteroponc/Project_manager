"use strict";
// Isolated presentation fixtures. No fetch, storage, production scripts or calculation formula.
const $ = selector => document.querySelector(selector);
const escapeText = value => String(value).replace(/[&<>"']/g, c => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
const checkNames = ["Согласование архитектуры", "Требования ИБ", "Классификация данных", "Модель угроз", "Оценка безопасности", "Контроль доступа", "Журналирование и мониторинг", "Резервное копирование и восстановление", "Нагрузочное тестирование", "Допуск к промышленной эксплуатации"];
const sections = ["Сводка", "Паспорт", "Компоненты", "Интеграции", "Проверки", "Риски ИБ"];
let scenario = "entry", tab = 0, dirty = false, editKind = "passport", forceConflict = false;
let initialized = false, created = false, localArchitect = "Мария Орлова";
let toastTimer;
const badge = (text, tone = "gray") => `<span class="badge ${tone}">${escapeText(text)}</span>`;
const button = (text, action, extra = "") => `<button type="button" class="btn ${extra}" data-action="${action}">${text}</button>`;
const note = () => `<p class="assessment-note">Оценка на 23.10.2026 · Europe/Moscow<br>Расчёт не является официальным допуском к запуску.</p>`;
const blank = () => ["empty", "delete"].includes(scenario) || created;
const missing = () => scenario === "missing" && !created;
const ready = () => scenario === "ready";
const attention = () => scenario === "attention";
const counts = () => blank() ? [0, 0, initialized ? 10 : 0, 0] : [2, 1, 10, 1];
const selectedStatus = () => missing() || blank() ? ["Оценка не завершена", "gray"] : ready() ? ["Архитектура и ИБ: проверки завершены", "green"] : attention() ? ["Требует внимания", "amber"] : ["Заблокировано", "red"];

function render() {
  const entry = scenario === "entry";
  $("#kicker").textContent = entry ? "Карточка проекта" : "Архитектура и безопасность";
  $("#crumb-tail").hidden = entry;
  if (entry) { $("#screen").innerHTML = entryScreen(); return; }
  if (scenario === "loading") {
    $("#screen").innerHTML = `<section class="panel" aria-busy="true"><p class="loading-label" role="status"><span class="spinner" aria-hidden="true"></span>Загружаем архитектуру и ИБ…</p><p class="section-note">Сведения пока недоступны. Действия с записями появятся после загрузки.</p><div class="skeleton tall"></div><div class="skeleton"></div><div class="skeleton"></div>${button("Показать результат загрузки (демо)", "loaded")}</section>`;
    return;
  }
  if (scenario === "error") {
    $("#screen").innerHTML = `<section class="panel empty"><div class="empty-icon" aria-hidden="true">!</div><h2>Не удалось загрузить архитектуру и ИБ</h2><p>Попробуйте ещё раз. Сохранённые сведения не изменены.</p>${button("Повторить загрузку", "retry", "primary")}<p class="helper">В макете повтор переключает на заполненный пример.</p></section>`;
    return;
  }
  const navigation = `<nav class="tabs" aria-label="Разделы архитектуры">${sections.map((s, i) => `<button type="button" data-tab="${i}" ${tab === i ? 'aria-current="page"' : ""}>${s}</button>`).join("")}</nav>`;
  const contents = missing() ? missingScreen() : [summary, passport, () => records("component"), () => records("integration"), () => records("check"), () => records("risk")][tab]();
  $("#screen").innerHTML = navigation + `<div id="section-content">${contents}</div>`;
}

function entryScreen() {
  return `<div class="entry-grid"><section class="panel"><div class="section-head"><h2>О проекте</h2>${badge("В работе", "blue")}</div><p>Единый процесс приёма и обработки внутренних заявок.</p><hr class="divider"><dl class="facts"><div><dt>Состояние проекта</dt><dd class="entry-health">● В норме</dd></div><div><dt>Прогресс проекта</dt><dd>68 %<div class="progress" aria-hidden="true"><span></span></div></dd></div><div><dt>Срок завершения</dt><dd>15 декабря 2026</dd></div><div><dt>Задачи / контрольные точки</dt><dd>12 / 4</dd></div></dl><p class="section-note">Показатели проекта рассчитаны отдельно от готовности архитектуры и ИБ.</p></section><section class="panel entry-arch"><p class="eyebrow">Новый раздел в карточке</p><h2>Архитектура и ИБ</h2><p>${badge("Заблокировано", "red")}</p><p>Проверка перед запуском требует изменений. Ещё четыре причины оценки доступны в сводке.</p>${button("Открыть архитектуру и ИБ →", "open", "primary")}</section><section class="panel wide"><h2>Контрольные точки</h2><div class="next-item"><strong>Завершение пилота</strong><span class="muted">10 ноября 2026 · Не выполнена</span></div><p class="helper muted">Карточка показана как контекст входа. Её рабочие действия в этом макете не подключены.</p></section></div>`;
}

function reasons() {
  if (ready()) return [];
  if (blank()) return [
    ["gray", "Сведения паспорта неполны", "Не заполнены требования доступности и восстановления, дата пересмотра.", 1],
    ["gray", "Архитектура не утверждена", "Паспорт находится на стадии проектирования.", 1],
    ["gray", initialized ? "Проверки не завершены" : "Проверки не инициализированы", initialized ? "Все десять обязательных проверок ещё не начаты; сроки не заданы." : "Отсутствуют все десять типов; оценка не может быть завершена.", 4]
  ];
  if (attention()) return [["amber", "Высокий риск отмечен как принятый", "Резервный процесс обработки · Полномочия принятия не проверены.", 5]];
  return [
    ["red", "Проверка перед запуском требует изменений", "Допуск к промышленной эксплуатации · Обязательная проверка.", 4],
    ["amber", "Высокий риск отмечен как принятый", "Резервный процесс обработки · Полномочия принятия не проверены.", 5],
    ["gray", "Проверки не завершены", "Для завершения оценки необходимо закрыть все обязательные проверки.", 4],
    ["gray", "Проверка не завершена корректно", "Допуск к промышленной эксплуатации · Статус «Требуются изменения».", 4],
    ["gray", "Интеграция высокой критичности не готова", "Обмен с каталогом услуг · Высокая критичность, в работе.", 3]
  ];
}

function summary() {
  const [label, tone] = selectedStatus(), rs = reasons(), c = counts();
  return `<div class="summary"><section class="panel summary-main ${ready() ? "ready" : attention() ? "attention" : blank() ? "neutral" : ""}"><p class="eyebrow">Архитектурная готовность</p>${badge(label, tone)}<h2>${ready() ? "Проверки завершены" : attention() ? "Есть принятый высокий риск" : blank() ? "Заполните паспорт и проверки" : "До запуска нужны изменения"}</h2><p class="muted">${ready() ? "Паспорт утверждён; все десять проверок корректно завершены. Причин других статусов нет." : attention() ? "Остальные проверки завершены. Отметка о принятии риска не подтверждает согласование." : blank() ? "Черновик можно сохранять частями. Оценка появится по мере заполнения." : "Обязательный допуск требует доработки. Причины более низкого приоритета также сохранены в оценке."}</p><div class="facts-inline"><div><strong>${blank() ? "0" : ready() || attention() ? "10" : "9"} / 10</strong><span>${"проверок завершено"}</span></div><div><strong>${rs.length}</strong><span>групп причин в макете</span></div><div><strong>1.0</strong><span>версия документа</span></div></div>${note()}</section><section class="panel"><div class="section-head"><h2>Ближайшие проверки</h2></div><p class="muted helper">23 октября — 6 ноября включительно</p>${!blank() && !ready() && !attention() ? `<div class="next-item"><strong>Допуск к промышленной эксплуатации</strong>${badge("Требуются изменения", "red")}<p>План: 27 октября · Илья Соколов</p></div>` : '<p class="section-note">В ближайшие 14 дней нет назначенных незавершённых проверок.</p>'}<p class="helper muted">Близость срока сама по себе не ухудшает готовность.</p></section><section class="panel wide"><div class="section-head"><h2>Причины оценки<span class="count">${rs.length}</span></h2><span class="muted helper">Все приоритеты</span></div>${rs.length ? rs.map(r => `<article class="reason"><span class="reason-mark ${r[0]}" aria-hidden="true"></span><div><h3>${r[1]}</h3><p>${r[2]}</p></div><button class="row-arrow" data-tab="${r[3]}" aria-label="Перейти: ${r[1]}">→</button></article>`).join("") : '<div class="notice success">Причин, препятствующих завершению оценки, нет. Это не официальный допуск к запуску.</div>'}</section><section class="panel wide"><div class="section-head"><h2>Состав паспорта</h2><button class="text-button" data-tab="1">Открыть паспорт →</button></div><div class="actions">${["Компоненты", "Интеграции", "Проверки", "Риски ИБ"].map((name, i) => `<button class="btn" data-tab="${i + 2}">${name} <span class="count">${c[i]}</span></button>`).join("")}</div></section></div>`;
}

function missingScreen() {
  return `<section class="panel empty"><div class="empty-icon" aria-hidden="true">＋</div>${badge("Оценка не завершена")}<h2>Архитектурного паспорта пока нет</h2><p>Создайте паспорт, чтобы описать решение, интеграции, проверки и ИБ-риски. Просмотр проекта ничего не создаёт.</p>${button("Создать паспорт", "create-passport", "primary")}<p class="helper">Причина оценки: паспорт отсутствует. Удалять пока нечего.</p>${note()}</section>`;
}

function passport() {
  const draft = blank();
  return `<section class="panel"><div class="section-head"><div><h2>Паспорт решения</h2><p class="muted helper">Пользовательская версия документа 1.0</p></div><div class="actions">${button("Редактировать", "edit-passport", "small")}${button("Удалить паспорт", "delete-passport", "small destructive")}</div></div><dl class="facts"><div><dt>Стадия</dt><dd>${badge(draft ? "В проектировании" : "Утверждена", draft ? "gray" : "green")}</dd></div><div><dt>Критичность</dt><dd>Высокая</dd></div><div><dt>Тип решения</dt><dd>Новое решение</dd></div><div><dt>Ответственный архитектор</dt><dd id="architect-display">${escapeText(localArchitect ?? "Не указан")}</dd></div><div class="wide"><dt>Краткое описание</dt><dd>Внутренний сервис приёма заявок с единым каталогом услуг и прозрачным контролем исполнения.</dd></div><div><dt>Классы данных</dt><dd class="chips">${draft ? "Не указаны" : '<span class="chip">Внутренние</span><span class="chip">Персональные</span>'}</dd></div><div><dt>Целевые среды</dt><dd class="chips">${draft ? "Не указаны" : '<span class="chip">Тестирование</span><span class="chip">Предпромышленная</span><span class="chip">Промышленная</span>'}</dd></div><div><dt>Требования доступности</dt><dd>${draft ? "Не указаны" : "Доступность в рабочие часы, целевой уровень 99,5 %."}</dd></div><div><dt>Требования восстановления</dt><dd>${draft ? "Не указаны" : "Восстановление обработки в течение четырёх часов."}</dd></div><div><dt>Дата пересмотра</dt><dd>${draft ? "Не указана" : "20 октября 2026"}</dd></div></dl><hr class="divider"><p class="helper muted">Создан: 12 октября 2026 · Изменён: 20 октября 2026 · Время UTC<br>Сведения предназначены для управленческого описания. Секреты и технические адреса здесь не хранятся.</p></section>`;
}

function demoRecords(kind) {
  if (kind === "component") return [
    {name:"Сервис заявок",type:"Серверное приложение",purpose:"Обработка заявок и маршрутизация исполнения",platform:"Java",criticality:"Высокая",external:"Нет",position:0},
    {name:"Хранилище заявок",type:"База данных",purpose:"Хранение состояния и истории обработки",platform:"реляционная СУБД",criticality:"Высокая",external:"Нет",position:1}
  ];
  if (kind === "integration") return [{name:"Обмен с каталогом услуг",criticality:"Высокая",source:"Единое окно заявок",target:"Каталог услуг",direction:"Исходящая",interaction:"API",auth:"OAuth 2.0",readiness:ready() || attention() ? "Готова" : "В работе",dataClasses:["Внутренние"],owner:"команда корпоративных сервисов",comment:"согласование регламента обработки изменений."}];
  if (kind === "check") return checkNames.map((name, i) => ({name,checkType:name,required:"Обязательная",status:blank() ? "Не начата" : i === 9 && !ready() && !attention() ? "Требуются изменения" : "Пройдена",owner:blank() ? null : "Илья Соколов",plannedDate:blank() ? null : i === 9 ? "2026-10-27" : "2026-10-20",actualDate:blank() || i === 9 && !ready() && !attention() ? null : "2026-10-20",comment:i === 9 && !blank() && !ready() && !attention() ? "завершить согласование регламента сопровождения." : null}));
  if (kind === "risk") return [{name:"Резервный процесс обработки",category:"Процесс",probability:"Средняя",impact:"Высокое",status:ready() ? "Снижен" : "Принят",owner:"Анна Смирнова",description:null,dueDate:"2026-10-30",closedDate:null,plan:"провести тренировку ручной обработки заявок.",justification:ready() ? null : "временный резервный процесс согласован внутри команды до завершения пилота."}];
  return [];
}
function recordActions(kind, index = 0) { return `<div class="actions"><button class="text-button" data-edit="${kind}" data-index="${index}">Редактировать</button><button class="text-button delete-link" data-delete="${kind}" data-index="${index}">Удалить</button></div>`; }
function records(kind) {
  const title = {component:"Компоненты", integration:"Интеграции", check:"Архитектурные и ИБ-проверки", risk:"Риски ИБ"}[kind];
  const addText = {component:"Добавить компонент", integration:"Добавить интеграцию", check:"Инициализировать 10 проверок", risk:"Добавить риск"}[kind];
  const isEmpty = blank() && !(kind === "check" && initialized);
  let rows = [];
  if (!isEmpty && kind === "component") rows = [
    `<h3>Сервис заявок</h3><p>Серверное приложение · Обработка заявок и маршрутизация исполнения</p><div class="record-meta"><span>Платформа: Java</span><span>Критичность: высокая</span><span>Внутренний компонент</span></div>`,
    `<h3>Хранилище заявок</h3><p>База данных · Хранение состояния и истории обработки</p><div class="record-meta"><span>Платформа: реляционная СУБД</span><span>Критичность: высокая</span><span>Внутренний компонент</span></div>`
  ];
  if (!isEmpty && kind === "integration") rows = [`<div class="record-head"><h3>Обмен с каталогом услуг</h3>${badge(ready() || attention() ? "Готова" : "В работе", ready() || attention() ? "green" : "blue")}</div><p>Единое окно заявок → Каталог услуг</p><div class="record-meta"><span>Исходящая · API</span><span>OAuth 2.0</span><span>Высокая критичность</span></div><div class="chips"><span class="chip">Внутренние</span></div><p>Ответственная сторона: команда корпоративных сервисов</p><p>Комментарий: согласование регламента обработки изменений.</p>`];
  if (!isEmpty && kind === "check") rows = checkNames.map((name, i) => `<div class="record-head"><h3>${name}</h3>${badge(blank() ? "Не начата" : i === 9 && !ready() && !attention() ? "Требуются изменения" : "Пройдена", blank() ? "gray" : i === 9 && !ready() && !attention() ? "red" : "green")}</div><div class="record-meta"><span>Обязательная</span><span>Владелец: ${blank() ? "не указан" : "Илья Соколов"}</span><span>План: ${blank() ? "не указан" : i === 9 ? "27.10.2026" : "20.10.2026"}</span><span>Факт: ${blank() || i === 9 && !ready() && !attention() ? "не указан" : "20.10.2026"}</span></div>${i === 9 && !blank() && !ready() && !attention() ? '<p>Комментарий: завершить согласование регламента сопровождения.</p>' : ""}`);
  if (!isEmpty && kind === "risk") rows = [`<div class="record-head"><h3>Резервный процесс обработки</h3>${badge("Высокий уровень", "amber")}</div><div class="record-meta"><span>Категория: процесс</span><span>Вероятность: средняя</span><span>Влияние: высокое</span></div><p>Владелец: Анна Смирнова · Срок обработки: 30.10.2026</p><p>План: провести тренировку ручной обработки заявок.</p>${ready() ? badge("Снижен", "green") : badge("Принят", "amber")}${!ready() ? '<div class="notice warning"><strong>Согласование принятия не проверено</strong><p>Обоснование: временный резервный процесс согласован внутри команды до завершения пилота.</p><p>Отметка о принятии не удостоверяет полномочия и не позволяет завершить оценку.</p></div>' : ""}<p class="helper">Уровень вычисляется сервером из вероятности и влияния; в форме он не редактируется.</p>`];
  const empty = rows.length === 0;
  return `<section class="panel"><div class="section-head"><div><h2>${title}<span class="count">${rows.length}</span></h2><p class="muted helper">${kind === "component" ? "Критичность компонентов информативна." : kind === "check" ? "Все десять типов нужны для завершения оценки." : "Демонстрационные сведения паспорта."}</p></div>${kind !== "check" ? button(addText, `add-${kind}`, "primary small") : ""}</div>${kind === "check" && !empty ? '<div class="notice">Проверки создаются только явной инициализацией полностью пустого набора. Недостающие типы автоматически не добавляются.</div><br>' : ""}${empty ? `<div class="empty"><div class="empty-icon" aria-hidden="true">${kind === "check" ? "✓" : "＋"}</div><h3>${kind === "check" ? "Проверки ещё не созданы" : "Записей пока нет"}</h3><p>${kind === "check" ? "Создайте десять обязательных проверок со статусом «Не начата». При просмотре они не появляются автоматически." : "Добавьте первую запись, когда сведения будут готовы."}</p>${button(addText, kind === "check" ? "initialize" : `add-${kind}`, "primary")}</div>` : `<div class="${kind === "component" ? "records-grid" : "records"}">${rows.map((row, i) => `<article class="record">${row}${recordActions(kind, i)}</article>`).join("")}</div>`}</section>`;
}

function showToast(text) { $("#toast").textContent = text; $("#toast").hidden = false; clearTimeout(toastTimer); toastTimer = setTimeout(() => { $("#toast").hidden = true; }, 6500); }
function field(label, name, value = "", type = "text", wide = false) { return `<label class="${wide ? "wide" : ""}">${label}${type === "textarea" ? `<textarea name="${name}" maxlength="2000">${escapeText(value)}</textarea>` : `<input name="${name}" type="${type}" value="${escapeText(value)}" ${type === "text" ? 'maxlength="255"' : ""}>`}<span class="field-error" id="error-${name}"></span></label>`; }
function selectField(label, name, values, current) { return `<label>${label}<select name="${name}">${values.map(v => `<option ${v === current ? "selected" : ""}>${v}</option>`).join("")}</select></label>`; }
function setFieldset(title, name, values, picked) { return `<fieldset class="wide"><legend>${title}</legend>${values.map(v => `<label class="check"><input type="checkbox" name="${name}" value="${v}" ${picked.includes(v) ? "checked" : ""}>${v}</label>`).join("")}</fieldset>`; }
function openEditor(kind = "passport", isNew = false, errors = false, record = null) {
  editKind = kind; dirty = errors || scenario === "conflict"; forceConflict = scenario === "conflict";
  $("#conflict").hidden = !forceConflict; $("#comparison").hidden = true; $("#form-error").textContent = "";
  $("#save").disabled = forceConflict;
  $("#editor-title").textContent = `${isNew ? "Создать" : "Редактировать"} ${ {passport:"паспорт",component:"компонент",integration:"интеграцию",check:"проверку",risk:"риск ИБ"}[kind]}`;
  const criticality = () => selectField("Критичность *", "criticality", ["Низкая", "Средняя", "Высокая", "Критическая"], "Высокая");
  let fields;
  if (kind === "passport") fields = criticality() + selectField("Стадия *", "stage", ["Не начата", "В проектировании", "На рассмотрении", "Утверждена", "Требуются изменения"], isNew ? "В проектировании" : "Утверждена") + selectField("Тип решения *", "solutionType", ["Новое решение", "Изменение существующего", "Интеграция", "Миграция"], "Новое решение") + field("Ответственный архитектор", "architect", errors ? "" : forceConflict ? "Мария Орлова — моя правка" : localArchitect) + field("Краткое описание", "summary", "Внутренний сервис приёма заявок.\nЕдиный каталог и контроль исполнения.", "textarea", true) + setFieldset("Классы данных", "dataClasses", ["Публичные", "Внутренние", "Конфиденциальные", "Персональные", "Платёжные", "Коммерческая тайна"], isNew ? [] : ["Внутренние", "Персональные"]) + setFieldset("Целевые среды", "environments", ["Разработка", "Тестирование", "Предпромышленная", "Промышленная", "Другая"], isNew ? [] : ["Тестирование", "Предпромышленная", "Промышленная"]) + field("Требования доступности", "availability", "Доступность в рабочие часы, целевой уровень 99,5 %.", "textarea") + field("Требования восстановления", "recovery", "Восстановление в течение четырёх часов.", "textarea") + field("Дата пересмотра", "reviewDate", "2026-10-20", "date");
  else if (kind === "check") fields = `<label class="wide">Тип проверки<input value="Допуск к промышленной эксплуатации" disabled></label>` + selectField("Обязательность", "required", ["Обязательная", "Неприменимая"], "Обязательная") + selectField("Статус", "status", ["Не начата", "В работе", "На рассмотрении", "Пройдена", "Требуются изменения", "Заблокирована", "Не требуется"], "Требуются изменения") + field("Владелец", "owner", "Илья Соколов") + field("Плановая дата", "plannedDate", "2026-10-27", "date") + field("Фактическая дата", "actualDate", "", "date") + field("Комментарий / обоснование неприменимости", "comment", "Завершить согласование регламента сопровождения.", "textarea", true);
  else if (kind === "component") fields = field("Название *", "name", isNew ? "" : "Сервис заявок") + selectField("Тип *", "type", ["Серверное приложение", "Клиентское приложение", "База данных", "Очередь", "Внешний сервис", "Инфраструктура"], "Серверное приложение") + criticality() + field("Платформа", "platform", "Java") + field("Назначение", "purpose", "Обработка заявок", "textarea", true) + selectField("Внешний компонент", "external", ["Нет", "Да"], "Нет") + field("Порядок", "position", "0", "number");
  else if (kind === "integration") fields = field("Название *", "name", isNew ? "" : "Обмен с каталогом услуг") + criticality() + field("Система-источник *", "source", "Единое окно заявок") + field("Система-получатель *", "target", "Каталог услуг") + selectField("Направление *", "direction", ["Входящая", "Исходящая", "Двусторонняя"], "Исходящая") + selectField("Взаимодействие *", "interaction", ["API", "События", "Файловый обмен", "Пакетное", "Другое"], "API") + selectField("Аутентификация *", "auth", ["Без аутентификации", "Сервисная учётная запись", "OAuth 2.0", "mTLS", "Сертификат", "Другая"], "OAuth 2.0") + selectField("Готовность *", "readiness", ["Запланирована", "В работе", "Готова", "Заблокирована"], "В работе") + setFieldset("Передаваемые классы данных · минимум один", "dataClasses", ["Публичные", "Внутренние", "Конфиденциальные", "Персональные", "Платёжные", "Коммерческая тайна"], ["Внутренние"]) + field("Ответственная сторона", "owner", "Команда корпоративных сервисов") + field("Комментарий", "comment", "", "textarea", true);
  else fields = field("Название *", "name", isNew ? "" : "Резервный процесс обработки") + selectField("Категория *", "category", ["Доступ", "Данные", "Доступность", "Интеграция", "Процесс", "Другое"], "Процесс") + selectField("Вероятность *", "probability", ["Низкая", "Средняя", "Высокая", "Критическая"], "Средняя") + selectField("Влияние *", "impact", ["Низкое", "Среднее", "Высокое", "Критическое"], "Высокое") + selectField("Статус *", "status", ["Открыт", "В работе", "Принят", "Снижен", "Закрыт"], "Принят") + field("Владелец", "owner", "Анна Смирнова") + field("Описание", "description", "", "textarea", true) + field("Срок обработки", "dueDate", "2026-10-30", "date") + field("Дата закрытия", "closedDate", "", "date") + field("План обработки", "plan", "Провести тренировку ручного процесса.", "textarea", true) + field("Обоснование принятия", "justification", "Временный резервный процесс согласован внутри команды до завершения пилота.", "textarea", true);
  $("#editor-fields").innerHTML = fields;
  if (record) {
    $("#editor-title").textContent += ` · ${record.name}`;
    if (kind === "check") $("#editor-fields input:disabled").value = record.checkType;
    for (const input of $("#edit-form").elements) {
      if (!Object.hasOwn(record, input.name)) continue;
      if (input.type === "checkbox") input.checked = (record[input.name] ?? []).includes(input.value);
      else input.value = record[input.name] ?? "";
    }
  }
  if (kind === "passport") {
    $('[name="architect"]').value = isNew ? "" : forceConflict ? "Мария Орлова — моя правка" : localArchitect ?? "";
    $("#error-architect").innerHTML = '<button type="button" class="text-button" id="clear-architect">Очистить реквизит</button><span class="muted"> · необязательно; пустое поле означает отсутствие значения</span>';
  }
  $("#dirty").textContent = dirty ? "Есть несохранённые изменения" : "Нет несохранённых изменений";
  if (errors) { const input = $('[name="name"]'); input.value = ""; input.setAttribute("aria-invalid", "true"); input.setAttribute("aria-describedby", "error-name"); $("#error-name").textContent = "Укажите название компонента: обязательное поле не может быть пустым."; $("#form-error").textContent = "Исправьте отмеченное поле. Остальной ввод сохранён."; }
  $("#editor").showModal();
}

function confirmation(title, body, action, actionText = "Удалить") {
  $("#confirm-title").textContent = title; $("#confirm-body").innerHTML = body;
  $("#confirm-actions").innerHTML = button("Отмена", "dismiss") + (action ? button(actionText, action, "destructive solid") : button("Понятно", "dismiss", "primary"));
  $("#confirmation").showModal();
}
function deletePassport() {
  if (!blank()) confirmation("Паспорт нельзя удалить", `<p>Сначала явно удалите дочерние записи и очистите классы данных и среды в паспорте.</p><ul><li>Компоненты: 2</li><li>Интеграции: 1</li><li>Проверки: 10</li><li>Риски ИБ: 1</li><li>Классы данных паспорта: 2</li><li>Среды: 3</li></ul><div class="notice warning">Ничего не будет удалено или очищено автоматически. Счётчики повторно проверяются при удалении.</div>`, null);
  else confirmation("Удалить пустой паспорт?", "<p>Дочерних записей, классов данных и сред нет. Будет удалён только паспорт; проект сохранится.</p><div class='notice warning'>Отменить это действие нельзя. При повторном создании появится новый паспорт.</div>", "confirm-delete-passport", "Да, удалить паспорт");
}
function chooseScenario(value) {
  clearTimeout(toastTimer); $("#toast").hidden = true;
  scenario = value; tab = 0; initialized = false; created = false; localArchitect = "Мария Орлова";
  $("#scenario").value = value;
  document.querySelectorAll("dialog[open]").forEach(d => d.close()); dirty = false;
  if (["edit", "conflict", "blocked", "delete"].includes(value)) tab = 1;
  render();
  if (value === "edit") { tab = 2; render(); openEditor("component", false, true, demoRecords("component")[0]); }
  if (value === "conflict") openEditor("passport");
  if (value === "blocked" || value === "delete") deletePassport();
}

document.addEventListener("click", event => {
  const close = event.target.closest("[data-close]");
  if (close) { closeEditor(close.closest("dialog")); return; }
  const tabButton = event.target.closest("[data-tab]");
  if (tabButton) { tab = Number(tabButton.dataset.tab); render(); return; }
  const edit = event.target.closest("[data-edit]");
  if (edit) { openEditor(edit.dataset.edit, false, false, demoRecords(edit.dataset.edit)[Number(edit.dataset.index)]); return; }
  const del = event.target.closest("[data-delete]");
  if (del) { const record = demoRecords(del.dataset.delete)[Number(del.dataset.index)]; confirmation(`Удалить «${record.name}»?`, `<p>Удаляется только выбранная запись <strong>${escapeText(record.name)}</strong>${del.dataset.delete === "integration" ? " и её передаваемые классы данных" : ""}. Другие записи сохранятся.</p>${del.dataset.delete === "check" ? '<div class="notice warning">Набор проверок станет неполным. Удалённая проверка не восстановится при просмотре; инициализация непустого набора недоступна.</div>' : ""}`, `confirm-delete-${del.dataset.delete}-${del.dataset.index}`, "Да, удалить"); return; }
  const action = event.target.closest("[data-action]")?.dataset.action;
  if (!action) return;
  if (action === "dismiss") { $("#confirmation").close(); return; }
  if (["open", "loaded", "retry"].includes(action)) { chooseScenario("full"); if (action === "retry") showToast("Повторная загрузка: показан демонстрационный ответ."); }
  if (action === "create-passport") openEditor("passport", true);
  if (action === "edit-passport") openEditor();
  if (action === "delete-passport") deletePassport();
  if (action.startsWith("add-")) openEditor(action.slice(4), true);
  if (action === "initialize") confirmation("Инициализировать проверки?", "<p>Будут созданы ровно десять типов: обязательные, со статусом «Не начата», без владельцев, дат и комментариев.</p><p>Операция доступна только при полностью пустом наборе.</p>", "confirm-initialize", "Создать 10 проверок");
  if (action === "confirm-initialize") { $("#confirmation").close(); initialized = true; render(); showToast("Демонстрация: создан набор из десяти проверок. Данные не отправлялись."); }
  if (action === "confirm-delete-passport") { $("#confirmation").close(); chooseScenario("missing"); showToast("Демонстрация: пустой паспорт удалён, проект сохранён."); }
  if (action.startsWith("confirm-delete-") && action !== "confirm-delete-passport") { $("#confirmation").close(); showToast("Демонстрация подтверждения удаления завершена. Записи примера не менялись."); }
  if (action === "discard") { $("#confirmation").close(); $("#editor").close(); dirty = false; }
});
function closeEditor(dialog) {
  if (dialog.id !== "editor" || !dirty) { dialog.close(); return; }
  confirmation("Оставить форму без сохранения?", "<p>Введённые изменения будут потеряны. Можно вернуться к редактированию.</p>", "discard", "Не сохранять");
}
$("#editor").addEventListener("cancel", event => { if (dirty) { event.preventDefault(); closeEditor($("#editor")); } });
$("#edit-form").addEventListener("input", () => { dirty = true; $("#dirty").textContent = "Есть несохранённые изменения"; });
$("#edit-form").addEventListener("submit", event => {
  event.preventDefault(); if (forceConflict) return;
  const name = $('[name="name"]');
  if (name && !name.value.trim()) { name.setAttribute("aria-invalid", "true"); name.setAttribute("aria-describedby", "error-name"); $("#error-name").textContent = "Укажите название."; name.focus(); return; }
  if (editKind === "passport") {
    const architect = $('[name="architect"]');
    if (missing()) { created = true; scenario = "empty"; $("#scenario").value = "empty"; tab = 1; }
    localArchitect = architect.value.trim() || null;
  }
  dirty = false; $("#editor").close(); render();
  showToast("Демонстрация сохранения формы. Оценки — готовые примеры серверного ответа; пересчёт и отправка не выполняются.");
});
document.addEventListener("click", event => { if (event.target.id === "clear-architect") { $('[name="architect"]').value = ""; dirty = true; $("#dirty").textContent = "Есть несохранённые изменения · архитектор будет очищен"; } });
$("#compare").addEventListener("click", () => { $("#comparison").hidden = false; $("#form-error").textContent = "После сверки закройте форму и откройте актуальную запись. Этот макет не повторяет конфликтующий запрос."; });
$("#scenario").addEventListener("change", event => chooseScenario(event.target.value));
$("#back").addEventListener("click", () => chooseScenario("entry"));
const initial = new URLSearchParams(location.search).get("scenario");
chooseScenario([...$("#scenario").options].some(o => o.value === initial) ? initial : "entry");
