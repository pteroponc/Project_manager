(() => {
  "use strict";

  const panel = document.getElementById("personal-board-columns");
  const switcher = document.getElementById("personal-board-switcher");
  const message = document.getElementById("personal-board-message");
  const boardCount = document.getElementById("personal-board-count");
  const legacy = document.getElementById("personal-board-legacy");
  const legacyList = document.getElementById("personal-board-legacy-list");
  const legacyOpen = document.getElementById("personal-board-legacy-open");
  const legacyBack = document.getElementById("personal-board-legacy-back");
  const legacyTitle = document.getElementById("personal-board-legacy-title");
  const refresh = document.getElementById("personal-board-refresh");
  const create = document.getElementById("personal-task-create");
  const form = document.getElementById("personal-task-form");
  const formTitle = document.getElementById("personal-task-form-title");
  const formMessage = document.getElementById("personal-task-form-message");
  const save = document.getElementById("personal-task-save");
  const cancel = document.getElementById("personal-task-cancel");
  if (!panel || !message || !legacy || !legacyList || !refresh || !form || !create) return;

  let request = 0;
  let formRequest = 0;
  let pending = false;
  let uncertain = false;
  let deleteNeedsRefresh = false;
  let moveNeedsRefresh = false;
  let draggedTask = null;
  let dropTarget = null;
  let editing = null;
  let tasks = new Map();
  let taskNodes = new Map();
  let pendingFocusId = null;
  let selectedColumnKey = null;
  let legacyIds = new Set();
  let showingLegacy = false;

  const priorityLabels = new Map([
    ["critical", ["Критичный", "danger"]],
    ["high", ["Высокий", "warning"]],
    ["medium", ["Средний", "info"]],
    ["low", ["Низкий", "neutral"]]
  ]);
  const boardDate = new Intl.DateTimeFormat("sv-SE", { timeZone: "Europe/Moscow", year: "numeric", month: "2-digit", day: "2-digit" });
  const desktopDrag = window.matchMedia?.("(min-width: 601px) and (any-hover: hover) and (any-pointer: fine)");

  function clearDrag() {
    draggedTask = null;
    panel.removeAttribute("data-dragging");
    highlightDropTarget(null);
  }

  function highlightDropTarget(group) {
    dropTarget?.removeAttribute("data-drop-target");
    dropTarget = group;
    if (group) group.dataset.dropTarget = "true";
  }

  function canDrag() {
    return desktopDrag?.matches && !pending && !refresh.disabled && form.hidden
      && !uncertain && !moveNeedsRefresh && !deleteNeedsRefresh;
  }

  desktopDrag?.addEventListener("change", () => {
    clearDrag();
    for (const summary of panel.querySelectorAll(".personal-board-card-summary")) {
      summary.draggable = desktopDrag.matches;
    }
  });

  function element(tag, className, value) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (value != null) node.textContent = String(value);
    return node;
  }

  function showLegacy(open) {
    showingLegacy = open && legacyIds.size > 0;
    legacy.hidden = !showingLegacy;
    panel.hidden = showingLegacy;
    if (switcher) switcher.hidden = showingLegacy;
    create.hidden = showingLegacy;
    if (legacyOpen) legacyOpen.hidden = showingLegacy || !legacyIds.size;
    const count = showingLegacy ? legacyIds.size : tasks.size - legacyIds.size;
    if (boardCount) boardCount.textContent = `Показано задач: ${count}.`;
  }

  function card(task, today, oldState = false) {
    const node = element("details", "personal-board-card");
    const summary = element("summary", "personal-board-card-summary");
    if (!oldState && desktopDrag) {
      summary.draggable = desktopDrag.matches;
      summary.addEventListener("dragstart", event => {
        if (!canDrag() || !event.dataTransfer || tasks.get(task.id) !== task) {
          event.preventDefault();
          return;
        }
        draggedTask = task;
        panel.dataset.dragging = "true";
        event.dataTransfer.effectAllowed = "move";
        event.dataTransfer.setData("text/plain", task.id);
      });
      summary.addEventListener("dragend", clearDrag);
    }
    summary.append(element("span", "personal-board-origin", task.projectId
      ? (task.projectName || "Проект без названия") : "Самостоятельная"));
    summary.append(element("strong", "", task.title));
    const metadata = element("span", "personal-board-meta");
    const priority = priorityLabels.get(task.priority);
    if (priority) {
      const badge = element("span", "personal-board-indicator", `Приоритет: ${priority[0]}`);
      badge.dataset.tone = priority[1];
      metadata.append(badge);
    }
    if (task.dueDate) {
      const active = !oldState && task.columnKey !== "done" && /^\d{4}-\d{2}-\d{2}$/.test(task.dueDate);
      const overdue = active && task.dueDate < today;
      const dueToday = active && task.dueDate === today;
      const badge = element("span", "personal-board-indicator",
        `${overdue ? "Просрочено" : dueToday ? "Срок сегодня" : "Срок"}: ${task.dueDate}`);
      badge.dataset.tone = overdue ? "danger" : dueToday ? "warning" : "neutral";
      metadata.append(badge);
    }
    if (priority || task.dueDate) summary.append(metadata);
    node.append(summary);
    const body = element("div", "personal-board-card-body");
    if (task.description) body.append(element("p", "", task.description));
    body.append(element("p", "hint-text", `Состояние: ${task.columnKey}`));
    if (task.owner) body.append(element("p", "hint-text", `Ответственный: ${task.owner}`));
    if (task.priority && !priority) body.append(element("p", "hint-text", `Приоритет: ${task.priority}`));
    if (!oldState) {
      const actions = element("div", "personal-board-card-actions");
      const edit = element("button", "btn btn-ghost btn-small", "Редактировать");
      edit.type = "button";
      edit.addEventListener("click", () => { if (!pending) void openForm(task); });
      actions.append(edit);
      const remove = element("button", "btn btn-danger btn-small", "Удалить задачу");
      remove.type = "button";
      remove.addEventListener("click", () => { void removeTask(task, remove); });
      actions.append(remove);
      if (task.columnKey !== "done") {
        const done = element("button", "btn btn-secondary btn-small", "Завершить");
        done.type = "button";
        done.addEventListener("click", () => { if (!pending) void complete(task); });
        actions.append(done);
      }
      body.append(actions);
    }
    node.append(body);
    return node;
  }

  function render(data) {
    clearDrag();
    const today = boardDate.format(new Date());
    tasks = new Map();
    taskNodes = new Map();
    panel.replaceChildren();
    switcher?.replaceChildren();
    const columns = [];
    for (const column of data.columns || []) {
      const group = element("section", "personal-board-column");
      const button = element("button", "personal-board-column-button", column.title);
      button.type = "button";
      const count = element("span", "personal-board-column-count", (column.tasks || []).length);
      button.append(count);
      columns.push({ key: column.key, group, button });
      switcher?.append(button);
      group.append(element("h4", "", `${column.title} · ${(column.tasks || []).length}`));
      const canDrop = () => draggedTask && canDrag() && draggedTask.columnKey !== column.key;
      group.addEventListener("dragover", event => {
        if (!canDrop() || !event.dataTransfer) return;
        event.preventDefault();
        event.dataTransfer.dropEffect = "move";
        highlightDropTarget(group);
      });
      group.addEventListener("dragleave", event => {
        if (dropTarget === group && !group.contains(event.relatedTarget)) highlightDropTarget(null);
      });
      group.addEventListener("drop", event => {
        if (!canDrop()) return;
        event.preventDefault();
        const task = draggedTask;
        clearDrag();
        void moveTask(task, column.key);
      });
      if (!column.tasks?.length) group.append(element("p", "hint-text", "Задач нет"));
      for (const task of column.tasks || []) {
        const node = card(task, today);
        tasks.set(task.id, task);
        taskNodes.set(task.id, node);
        group.append(node);
      }
      panel.append(group);
    }
    function selectColumn(key) {
      selectedColumnKey = columns.find(column => column.key === key)?.key ?? columns[0]?.key;
      for (const column of columns) {
        const selected = column.key === selectedColumnKey;
        column.group.dataset.selected = String(selected);
        column.button.setAttribute("aria-pressed", String(selected));
      }
    }
    for (const column of columns) {
      column.button.addEventListener("click", () => selectColumn(column.key));
    }
    const addressedColumn = columns.find(column => column.key === tasks.get(pendingFocusId)?.columnKey);
    selectColumn(addressedColumn ? addressedColumn.key : selectedColumnKey);
    legacyList.replaceChildren();
    legacyIds = new Set();
    for (const task of data.legacyTasks || []) {
      if (tasks.has(task.id)) continue;
      const node = card(task, today, true);
      legacyIds.add(task.id);
      tasks.set(task.id, task);
      taskNodes.set(task.id, node);
      legacyList.append(node);
    }
    const addressedTask = pendingFocusId && taskNodes.has(pendingFocusId);
    showLegacy(addressedTask ? legacyIds.has(pendingFocusId) : showingLegacy);
    if (addressedTask) {
      const node = taskNodes.get(pendingFocusId);
      node.open = true;
      node.querySelector("summary")?.focus();
      node.scrollIntoView({ block: "center" });
      pendingFocusId = null;
    }
    message.textContent = pendingFocusId
      ? "Исходная задача недоступна. Возможно, она была удалена."
      : "";
    pendingFocusId = null;
    message.removeAttribute("data-tone");
    if (!form.hidden && editing) {
      const current = tasks.get(editing.id);
      if (!current) {
        formMessage.textContent = "Задача отсутствует на сервере. Черновик сохранён в форме.";
        return false;
      }
      if (current.version !== editing.version) {
        formMessage.textContent = `На сервере версия ${current.version}; в форме версия ${editing.version}. Черновик сохранён. Откройте задачу заново после копирования изменений.`;
        return false;
      }
    }
    return true;
  }

  async function load() {
    const current = ++request;
    refresh.disabled = true;
    message.textContent = "Загружаем задачи…";
    try {
      const response = await fetch("/api/board/personal", {
        cache: "no-store", headers: { Accept: "application/json" }
      });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const data = await response.json();
      if (current !== request) return;
      uncertain = !render(data);
      if (!form.hidden && editing) save.disabled = uncertain || pending;
      deleteNeedsRefresh = false;
      moveNeedsRefresh = false;
      return true;
    } catch (_error) {
      if (current !== request) return;
      message.textContent = "Не удалось загрузить задачи. Повторите попытку.";
      message.dataset.tone = "error";
    } finally {
      if (current === request) refresh.disabled = false;
    }
  }

  function formPayload() {
    return {
      projectId: form.elements.projectId.value || null,
      title: form.elements.title.value,
      description: form.elements.description.value,
      dueDate: form.elements.dueDate.value || null,
      priority: form.elements.priority.value,
      columnKey: form.elements.columnKey.value,
      expectedVersion: editing ? editing.version : 0
    };
  }

  async function openForm(task = null) {
    if (!form.hidden) {
      formMessage.textContent = "Сначала сохраните или закройте открытый черновик.";
      form.elements.title.focus();
      return;
    }
    if (pending) return;
    if (moveNeedsRefresh) {
      message.textContent = "Обновите Доску перед редактированием.";
      return;
    }
    const current = ++formRequest;
    editing = task;
    uncertain = false;
    form.elements.priority.querySelector('option[value=""]')?.remove();
    form.reset();
    formTitle.textContent = task ? "Редактировать задачу" : "Новая задача";
    formMessage.textContent = "Загружаем проекты…";
    save.disabled = true;
    form.hidden = false;
    if (task) {
      form.elements.title.value = task.title;
      form.elements.description.value = task.description || "";
      form.elements.dueDate.value = task.dueDate || "";
      if (task.priority) form.elements.priority.value = task.priority;
      else {
        form.elements.priority.prepend(new Option("Не задан", ""));
        form.elements.priority.value = "";
      }
      form.elements.columnKey.value = task.columnKey;
    }
    try {
      const response = await fetch("/api/projects", { headers: { Accept: "application/json" } });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const projects = await response.json();
      if (current !== formRequest) return;
      const select = form.elements.projectId;
      select.replaceChildren(new Option("Без проекта", ""));
      for (const project of projects) select.append(new Option(project.name, project.id));
      if (task?.projectId && !projects.some(project => project.id === task.projectId)) {
        select.append(new Option(task.projectName || "Текущий проект", task.projectId));
      }
      select.value = task?.projectId || "";
      formMessage.textContent = task ? "Сохраняйте смену проекта отдельно от изменений полей задачи." : "Проект и срок можно оставить пустыми.";
      save.disabled = false;
    } catch (_error) {
      if (current === formRequest) formMessage.textContent = "Не удалось загрузить проекты. Закройте форму и повторите попытку.";
    }
    if (current === formRequest) form.elements.title.focus();
  }

  async function write(url, payload, method) {
    if (pending || uncertain || (editing && moveNeedsRefresh)) return false;
    pending = true;
    save.disabled = true;
    formMessage.textContent = "Сохраняем задачу…";
    try {
      const response = await fetch(url, {
        method, headers: { "Content-Type": "application/json", "X-PM-Write": "1" },
        body: JSON.stringify(payload)
      });
      if (!response.ok) {
        const error = await response.json().catch(() => ({}));
        if (error.code === "BOARD_CARD_DEPENDENCY_CONFLICT") {
          formMessage.textContent = "Нельзя сменить проект: у задачи есть несовместимая связь.";
        } else if (response.status === 409) {
          formMessage.textContent = `Конфликт версии. Данные в форме сохранены. Перечитайте Доску и сравните изменения (версия сервера: ${error.currentVersion ?? "неизвестна"}).`;
          uncertain = true;
        } else if (error.fieldErrors?.projectId && editing && payload.projectId !== editing.projectId) {
          formMessage.textContent = "Сохраните смену проекта отдельно от изменений полей задачи.";
        } else if (error.code === "PROJECT_NOT_FOUND") {
          formMessage.textContent = "Проект больше недоступен. Черновик сохранён.";
        } else {
          formMessage.textContent = `Сохранение отклонено (${error.code || response.status}). Исправьте данные и повторите.`;
        }
        return false;
      }
      form.hidden = true;
      selectedColumnKey = payload.columnKey;
      editing = null;
      await load();
      create.focus();
      return true;
    } catch (_error) {
      uncertain = true;
      formMessage.textContent = "Результат сохранения неизвестен. Черновик сохранён; перечитайте Доску перед новым действием.";
      return false;
    } finally {
      pending = false;
      save.disabled = uncertain;
    }
  }

  async function removeTask(task, button) {
    if (pending) return;
    if (!form.hidden) {
      formMessage.textContent = "Сначала сохраните или закройте открытый черновик.";
      form.elements.title.focus();
      return;
    }
    if (deleteNeedsRefresh || moveNeedsRefresh || uncertain || refresh.disabled) {
      message.textContent = "Перечитайте Доску перед удалением.";
      return;
    }
    if (!window.confirm(`Удалить задачу «${task.title}»?`)) {
      button.focus();
      return;
    }
    pending = true;
    button.disabled = true;
    refresh.disabled = true;
    message.textContent = "Удаляем задачу…";
    try {
      const url = `/api/board/personal/tasks/${encodeURIComponent(task.id)}?expectedVersion=${task.version}`;
      const response = await fetch(url, { method: "DELETE", headers: { "X-PM-Write": "1" } });
      if (response.status === 204) {
        deleteNeedsRefresh = true;
        if (await load()) create.focus();
        return;
      }
      const error = await response.json().catch(() => ({}));
      if (error.code === "BOARD_CARD_DEPENDENCY_CONFLICT") {
        message.textContent = "Нельзя удалить задачу: на неё ссылаются другие данные.";
      } else {
        deleteNeedsRefresh = true;
        message.textContent = response.status === 409
          ? "Конфликт версии. Перечитайте Доску перед удалением."
          : "Удаление не подтверждено. Перечитайте Доску перед повтором.";
      }
    } catch (_error) {
      deleteNeedsRefresh = true;
      message.textContent = "Результат удаления неизвестен. Перечитайте Доску перед повтором.";
    } finally {
      pending = false;
      refresh.disabled = false;
      button.disabled = false;
      if (button.isConnected) button.focus();
    }
  }

  function statePayload(task, columnKey) {
    return {
      projectId: task.projectId, title: task.title, description: task.description,
      dueDate: task.dueDate || null, priority: task.priority, columnKey,
      expectedVersion: task.version
    };
  }

  async function moveTask(task, columnKey) {
    pending = true;
    moveNeedsRefresh = true;
    message.textContent = "Перемещаем задачу…";
    message.removeAttribute("data-tone");
    try {
      let outcome;
      try {
        const response = await fetch(`/api/board/personal/tasks/${encodeURIComponent(task.id)}`, {
          method: "PUT", headers: { "Content-Type": "application/json", "X-PM-Write": "1" },
          body: JSON.stringify(statePayload(task, columnKey))
        });
        outcome = response.ok ? "accepted" : response.status === 409 ? "conflict"
          : response.status >= 500 ? "unknown" : response.status;
      } catch (_error) {
        outcome = "unknown";
      }
      const refreshed = await load();
      if (!refreshed) {
        message.textContent = "Не удалось сверить перенос с сервером. Обновите Доску перед повтором.";
      } else if (outcome === "conflict") {
        message.textContent = "Конфликт версии. Показано текущее состояние с сервера.";
      } else if (typeof outcome === "number") {
        message.textContent = `Сервер отклонил перенос (${outcome}). Доска обновлена.`;
      } else if (tasks.get(task.id)?.columnKey === columnKey) {
        message.textContent = outcome === "accepted" ? "Задача перемещена."
          : "Сверено с сервером: задача в целевой колонке.";
        return;
      } else {
        message.textContent = "Перенос не подтверждён. Показано текущее состояние с сервера.";
      }
      message.dataset.tone = "error";
    } finally {
      pending = false;
    }
  }

  async function complete(task) {
    if (!form.hidden) {
      formMessage.textContent = "Сначала сохраните или закройте открытый черновик.";
      return;
    }
    if (uncertain || moveNeedsRefresh) { message.textContent = "Сначала перечитайте Доску."; return; }
    const payload = statePayload(task, "done");
    pending = true;
    message.textContent = "Завершаем задачу…";
    try {
      const response = await fetch(`/api/board/personal/tasks/${encodeURIComponent(task.id)}`, {
        method: "PUT", headers: { "Content-Type": "application/json", "X-PM-Write": "1" },
        body: JSON.stringify(payload)
      });
      if (!response.ok) {
        message.textContent = response.status === 409
          ? "Конфликт версии. Перечитайте Доску перед завершением." : `Не удалось завершить задачу (${response.status}).`;
        return;
      }
      await load();
      create.focus();
    } catch (_error) {
      uncertain = true;
      message.textContent = "Результат завершения неизвестен. Перечитайте Доску перед повтором.";
    } finally { pending = false; }
  }

  refresh.addEventListener("click", () => { if (!pending) void load(); });
  legacyOpen?.addEventListener("click", () => {
    if (pending || !legacyIds.size) return;
    clearDrag();
    showLegacy(true);
    legacyTitle?.focus();
  });
  legacyBack?.addEventListener("click", () => {
    showLegacy(false);
    legacyOpen?.focus();
  });
  create.addEventListener("click", () => { if (!pending) void openForm(); });
  cancel.addEventListener("click", () => {
    if (pending) return;
    if (!form.hidden && form.elements.title.value && !window.confirm("Закрыть форму и потерять несохранённые изменения?")) return;
    ++formRequest;
    form.hidden = true;
    editing = null;
    uncertain = false;
    create.focus();
  });
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (pending || uncertain || save.disabled) return;
    const payload = formPayload();
    await write(editing ? `/api/board/personal/tasks/${encodeURIComponent(editing.id)}` : "/api/board/personal/tasks",
      payload, editing ? "PUT" : "POST");
  });
  window.PMPersonalBoardUI = {
    onView(view) {
      if (view === "boards" && !pending) void load();
      if (view !== "boards") pendingFocusId = null;
    },
    focusTask(id) { pendingFocusId = id; if (!pending && !refresh.disabled) void load(); }
  };
  if (document.getElementById("board-panel")?.classList.contains("active")) void load();
})();
