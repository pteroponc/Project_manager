/* Read-only project snapshot presentation; navigation belongs to the existing card and board. */
(() => {
  const esc = value => escapeHtml(value ?? "Не указано");
  const attr = value => escapeAttr(value);
  function valid(data, id) {
    const tasks = data?.tasks;
    return data?.project?.id === id && typeof data.calculationDate === "string"
      && ["kanban", "scrum", "waterfall"].includes(data.model)
      && Array.isArray(data.project.milestones) && tasks
      && ["total", "done", "blocked", "overdue", "uncertain", "unknownDates", "unmapped", "unknownBlocked"]
        .every(key => Number.isSafeInteger(tasks[key]) && tasks[key] >= 0)
      && ["columns", "unassigned", "blockedTasks", "overdueTasks"].every(key => Array.isArray(tasks[key]))
      && Array.isArray(data.milestones?.overdue) && Array.isArray(data.milestones?.dueSoon);
  }
  function date(value) {
    return /^\d{4}-\d{2}-\d{2}$/.test(value ?? "") ? value.split("-").reverse().join(".") : "Срок неизвестен";
  }
  function time(value) {
    const instant = new Date(value);
    return Number.isFinite(instant.getTime()) ? new Intl.DateTimeFormat("ru-RU", {
      timeZone: "Europe/Moscow", dateStyle: "short", timeStyle: "medium"
    }).format(instant) : "Недоступно";
  }
  const empty = message => `<p class="hint-text">${message}</p>`;
  function taskList(items) {
    return items.length ? `<ul class="summary-records">${items.map(item => `<li>
      <button class="text-link" type="button" data-summary-task="${attr(item.id)}">${esc(item.title)}</button>
      <span>${esc(item.owner)} · ${date(item.dueDate)}</span></li>`).join("")}</ul>` : empty("Таких задач нет.");
  }
  function milestoneList(items) {
    return items.length ? `<ul class="summary-records">${items.map(item => `<li>
      <button class="text-link" type="button" data-summary-milestone="${attr(item.id)}">${esc(item.name)}</button>
      <span>${date(item.plannedDate)}</span></li>`).join("")}</ul>` : empty("Таких незавершённых вех нет.");
  }
  function render(data, receivedAt) {
    if (!data) return `<section class="project-summary" aria-label="Проектная сводка"><h2>Задачи и сроки</h2>
      ${empty("Сводка недоступна. Обновите карточку для получения согласованного снимка.")}</section>`;
    const tasks = data.tasks;
    const metric = (label, value) => `<div><dt>${label}</dt><dd>${value}</dd></div>`;
    return `<section class="project-summary" aria-label="Проектная сводка"><h2>Задачи и сроки</h2>
      <p class="hint-text">Расчёт на ${date(data.calculationDate)} · Europe/Moscow.
      Получено ${time(receivedAt)} (МСК). Ответ сформирован ${time(data.generatedAt)} (МСК).</p>
      <dl class="summary-counts">${metric("Всего задач", tasks.total)}${metric("Готово", tasks.done)}
      ${metric("Заблокировано", (tasks.unknownBlocked ? "Не менее " : "") + tasks.blocked)}
      ${metric("Просрочено", (tasks.uncertain ? "Не менее " : "") + tasks.overdue)}</dl>
      ${tasks.uncertain ? `<p class="summary-warning">Просрочка неопределённа для ${tasks.uncertain} задач:
      неизвестный срок — ${tasks.unknownDates}, несопоставленное состояние — ${tasks.unmapped}.
      Причины могут пересекаться. Это не полный подсчёт просроченных.</p>` : ""}
      ${tasks.unknownBlocked ? `<p class="summary-warning">Признак блокировки неизвестен для ${tasks.unknownBlocked} задач.</p>` : ""}
      <h3>Колонки модели проекта: ${esc(displayLabel(data.model))}</h3>
      <ul class="summary-distribution">${tasks.columns.map(column => `<li>${esc(column.title)} <strong>${column.count}</strong></li>`).join("")}</ul>
      <h3>Несопоставленные состояния</h3>
      ${tasks.unassigned.length ? `<ul class="summary-distribution">${tasks.unassigned.map(column =>
        `<li>${esc(column.key)} <strong>${column.count}</strong></li>`).join("")}</ul>` : empty("Несопоставленных состояний нет.")}
      <div class="summary-lists"><section><h3>Просроченные вехи</h3>${milestoneList(data.milestones.overdue)}</section>
      <section><h3>Ближайшие вехи · 0–14 дней</h3>${milestoneList(data.milestones.dueSoon)}</section>
      <section><h3>Заблокированные задачи</h3>${taskList(tasks.blockedTasks)}</section>
      <section><h3>Просроченные задачи</h3>${taskList(tasks.overdueTasks)}</section></div>
      ${data.milestones.unknownDates ? `<p class="summary-warning">Дата неизвестна для ${data.milestones.unknownDates} незавершённых вех.</p>` : ""}
      <p class="hint-text">Блокировка может пересекаться с завершением или просрочкой. Время получения и формирования ответа не является датой изменения проекта.</p>
    </section>`;
  }
  window.PMProjectSummary = { valid, render };
})();
