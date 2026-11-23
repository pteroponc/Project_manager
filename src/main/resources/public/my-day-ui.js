(() => {
  "use strict";

  const byId = id => document.getElementById(`my-day-${id}`);
  const groups = {
    OVERDUE: ["overdue", "Расчётно просрочена"],
    TODAY: ["today", "Срок сегодня"],
    UPCOMING: ["upcoming", "Будущий срок"],
    PRIORITY_WITHOUT_DATE: ["undated", "Высокий или критичный приоритет без срока"],
    INVALID_DATE: ["invalid", "Расчётный срок не определён"]
  };
  const stateLabels = { backlog: "Бэклог", ready: "К выполнению", "in-progress": "В работе" };
  const priorityLabels = { low: "Низкий", medium: "Средний", high: "Высокий", critical: "Критичный" };
  const milestoneDateLabels = {
    PAST: "Плановая дата прошла — проверьте состояние вехи",
    TODAY: "Плановая дата сегодня",
    FUTURE: "Будущая плановая дата"
  };
  let snapshot;
  let request = 0;

  function element(tag, value, classes) {
    const node = document.createElement(tag);
    node.textContent = value;
    if (classes) node.className = classes;
    return node;
  }

  function dateLabel(value) {
    const [year, month, day] = value.split("-");
    return `${day}.${month}.${year}`;
  }

  function row(task, reason) {
    const item = element("li", "", "my-day-task");
    const open = element("button", task.title, "text-link my-day-open-task");
    open.type = "button";
    open.addEventListener("click", () => window.PMBoardContext.openTask(task.projectId, null, task.id));
    const project = task.projectName ?? "Без проекта";
    const priority = priorityLabels[task.priority] || task.priority || "Не указан";
    const due = task.dueDate ? dateLabel(task.dueDate) : task.recordedDueDate?.trim() || "Не указан";
    item.append(open, element("p", `${project} · ${stateLabels[task.state]} · Приоритет: ${priority} · Срок: ${due}`),
      element("p", reason, "hint-text"));
    return item;
  }

  function render() {
    if (!snapshot) return;
    byId("date").textContent = `Расчёт на ${dateLabel(snapshot.calculationDate)} · Москва`;
    const priorityOnly = byId("priority").checked;
    byId("priority-count").textContent = `Открытых задач с высоким или критичным приоритетом: ${snapshot.tasks.filter(task => task.highPriority).length}.`;
    const tasks = priorityOnly ? snapshot.tasks.filter(task => task.highPriority) : snapshot.tasks;
    for (const [key, [id, reason]] of Object.entries(groups)) {
      const container = byId(id);
      const items = tasks.filter(task => task.reason === key);
      if (!items.length) {
        container.replaceChildren(...(key === "INVALID_DATE" ? [] : [element("p", "Нет задач.", "hint-text")]));
        continue;
      }
      const list = element("ul", "", "my-day-task-list");
      list.append(...items.map(task => row(task, reason)));
      container.replaceChildren(...(key === "INVALID_DATE" ? [element("h3", reason), list] : [list]));
    }
    const milestone = snapshot.milestone;
    if (milestone) {
      byId("milestone").replaceChildren(
        element("p", `${dateLabel(milestone.plannedDate)} · ${milestone.name} · ${milestone.projectName}`),
        element("p", milestoneDateLabels[milestone.plannedDateRelation], "hint-text"));
    } else {
      byId("milestone").replaceChildren(element("p", "Нет актуальной вехи с подтверждённой датой.", "hint-text"));
    }
    byId("legacy").hidden = !snapshot.hasUnknownStates;
  }

  async function load() {
    const current = ++request;
    byId("refresh").disabled = true;
    byId("status").textContent = "Загружаем сводку…";
    try {
      const response = await fetch("/api/my-day", { cache: "no-store" });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const data = await response.json();
      if (current !== request) return;
      snapshot = data;
      render();
      byId("status").textContent = data.tasks.length ? "" : "Сейчас нет задач для этой сводки.";
    } catch (_error) {
      if (current === request) byId("status").textContent = snapshot
        ? "Не удалось обновить сводку. Показаны данные последней загрузки."
        : "Не удалось загрузить сводку. Нажмите «Обновить», чтобы повторить.";
    } finally {
      if (current === request) byId("refresh").disabled = false;
    }
  }

  byId("refresh").addEventListener("click", load);
  byId("priority").addEventListener("change", render);
  window.PMMyDayUI = { onView(view) { if (view === "my-day") void load(); } };
})();
