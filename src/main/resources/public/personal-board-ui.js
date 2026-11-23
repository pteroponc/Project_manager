(() => {
  "use strict";

  const panel = document.getElementById("personal-board-columns");
  const message = document.getElementById("personal-board-message");
  const legacy = document.getElementById("personal-board-legacy");
  const legacyList = document.getElementById("personal-board-legacy-list");
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
  let editing = null;
  let tasks = new Map();
  let taskNodes = new Map();
  let pendingFocusId = null;

  function element(tag, className, value) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (value != null) node.textContent = String(value);
    return node;
  }

  function card(task, oldState = false) {
    const node = element("details", "personal-board-card");
    const summary = element("summary", "personal-board-card-summary");
    summary.append(element("strong", "", task.title));
    summary.append(element("span", "personal-board-origin", task.projectId
      ? (task.projectName || "Проект без названия") : "Самостоятельная"));
    node.append(summary);
    const body = element("div", "personal-board-card-body");
    if (task.description) body.append(element("p", "", task.description));
    body.append(element("p", "hint-text", `Состояние: ${task.columnKey}`));
    if (task.owner) body.append(element("p", "hint-text", `Ответственный: ${task.owner}`));
    if (task.dueDate) body.append(element("p", "hint-text", `Срок: ${task.dueDate}`));
    if (task.priority) body.append(element("p", "hint-text", `Приоритет: ${task.priority}`));
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
    tasks = new Map();
    taskNodes = new Map();
    panel.replaceChildren();
    for (const column of data.columns || []) {
      const group = element("section", "personal-board-column");
      group.append(element("h4", "", `${column.title} · ${(column.tasks || []).length}`));
      if (!column.tasks?.length) group.append(element("p", "hint-text", "Задач нет"));
      for (const task of column.tasks || []) {
        const node = card(task);
        tasks.set(task.id, task);
        taskNodes.set(task.id, node);
        group.append(node);
      }
      panel.append(group);
    }
    legacyList.replaceChildren();
    for (const task of data.legacyTasks || []) {
      const node = card(task, true);
      tasks.set(task.id, task);
      taskNodes.set(task.id, node);
      legacyList.append(node);
    }
    legacy.hidden = !data.legacyTasks?.length;
    if (pendingFocusId && taskNodes.has(pendingFocusId)) {
      const node = taskNodes.get(pendingFocusId);
      node.open = true;
      node.querySelector("summary")?.focus();
      node.scrollIntoView({ block: "center" });
      pendingFocusId = null;
    }
    const total = tasks.size;
    message.textContent = `Показано задач: ${total}.`;
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
      const response = await fetch("/api/board/personal", { headers: { Accept: "application/json" } });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const data = await response.json();
      if (current !== request) return;
      uncertain = !render(data);
      if (!form.hidden && editing) save.disabled = uncertain || pending;
      deleteNeedsRefresh = false;
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
    if (pending || uncertain) return false;
    pending = true;
    save.disabled = true;
    formMessage.textContent = "Сохраняем задачу…";
    try {
      const response = await fetch(url, {
        method, headers: { "Content-Type": "application/json" },
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
    if (deleteNeedsRefresh || uncertain || refresh.disabled) {
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
      const response = await fetch(url, { method: "DELETE" });
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

  async function complete(task) {
    if (!form.hidden) {
      formMessage.textContent = "Сначала сохраните или закройте открытый черновик.";
      return;
    }
    if (uncertain) { message.textContent = "Сначала перечитайте Доску."; return; }
    const payload = {
      projectId: task.projectId, title: task.title, description: task.description,
      dueDate: task.dueDate || null, priority: task.priority, columnKey: "done",
      expectedVersion: task.version
    };
    pending = true;
    message.textContent = "Завершаем задачу…";
    try {
      const response = await fetch(`/api/board/personal/tasks/${encodeURIComponent(task.id)}`, {
        method: "PUT", headers: { "Content-Type": "application/json" },
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
    onView(view) { if (view === "boards" && !pending) void load(); },
    focusTask(id) { pendingFocusId = id; if (!pending && !refresh.disabled) void load(); }
  };
  if (document.getElementById("board-panel")?.classList.contains("active")) void load();
})();
