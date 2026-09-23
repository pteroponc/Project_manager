/* PM-002C registry and project card. All portfolio assessments come from the API. */
(() => {
  const keys = ["query", "quarter", "status", "health", "attention", "sort", "direction"];
  const defaults = { query: "", quarter: "all", status: "all", health: "all", attention: "all", sort: "name", direction: "asc" };
  const allowed = {
    attention: ["all", "only"], sort: ["name", "deadline", "progress", "health"], direction: ["asc", "desc"]
  };
  const form = document.getElementById("registry-filters");
  const list = document.getElementById("projects-list");
  const count = document.getElementById("projects-count");
  const registryMessage = document.getElementById("project-navigation-message");
  const detail = document.getElementById("project-detail-content");
  const editor = document.getElementById("create-project-form");
  const dialog = document.getElementById("project-dialog");
  let filters = readFilters(window.location.search);
  let registry = null;
  let card = null;
  let editOriginal = null;
  let registryRequest = 0;
  let cardRequest = 0;
  let registryScroll = 0;
  let debounce;
  let dialogOpener = null;
  let dialogAction = null;
  let navigatingHistory = false;

  function readFilters(search) {
    const params = new URLSearchParams(search);
    const result = { ...defaults };
    keys.forEach(key => {
      const value = params.get(key);
      if (value != null && (!allowed[key] || allowed[key].includes(value))) result[key] = value;
    });
    return result;
  }

  function registryUrl(values = filters) {
    const params = new URLSearchParams();
    for (const key of keys) {
      if (values[key] !== defaults[key]) params.set(key, values[key]);
    }
    return "/api/projects/registry" + (params.size ? "?" + params : "");
  }

  function browserUrl(view, projectId) {
    const params = new URLSearchParams();
    if (view !== "overview") params.set("view", view);
    if (projectId) params.set("project", projectId);
    for (const key of keys) {
      if (filters[key] !== defaults[key]) params.set(key, filters[key]);
    }
    return window.location.pathname + (params.size ? "?" + params : "");
  }

  function updateUrl(view, projectId, replace = false) {
    if (navigatingHistory) return;
    const url = browserUrl(view, projectId);
    if (url !== window.location.pathname + window.location.search) {
      window.history[replace ? "replaceState" : "pushState"]({}, "", url);
    }
  }

  function field(name) { return editor.elements.namedItem(name); }
  function text(value, fallback = "Не указано") {
    return value == null || value === "" ? fallback : escapeHtml(value);
  }
  function label(value) {
    return text(displayLabel(value), "Не указано");
  }
  function statusLabel(value) {
    return ["active", "planned", "paused", "done"].includes(value)
      ? label(value) : `Неизвестный статус: ${text(value)}`;
  }
  function healthLabel(value) {
    return ["green", "yellow", "red"].includes(value)
      ? label(value) : `Неизвестное состояние: ${text(value)}`;
  }
  function date(value) {
    if (!value) return "Не указана";
    const match = String(value).match(/^(\d{4})-(\d{2})-(\d{2})$/);
    return match ? `${match[3]}.${match[2]}.${match[1]}` : escapeHtml(value);
  }
  function progress(value) { return value == null ? "Не указан" : `${value}%`; }
  function taskCount(value) { return value == null ? "Недоступно" : String(value); }
  function reasons(assessment) {
    return assessment?.attentionReasons?.length
      ? `<ul class="assessment-reasons">${assessment.attentionReasons.map(reason => `<li>${escapeHtml(reason)}</li>`).join("")}</ul>`
      : "";
  }
  function healthRank(value) { return ({ red: 0, yellow: 1, green: 2 })[value] ?? 3; }

  function sortItems(items, values = filters) {
    if (values.sort !== "progress") return items;
    const direction = values.direction === "desc" ? -1 : 1;
    return [...items].sort((a, b) => {
      if (a.progress == null) return b.progress == null ? a.name.localeCompare(b.name) : 1;
      if (b.progress == null) return -1;
      return (a.progress - b.progress) * direction || a.name.localeCompare(b.name) || a.id.localeCompare(b.id);
    });
  }

  function syncForm() {
    for (const key of keys) fieldInRegistry(key).value = filters[key];
  }
  function fieldInRegistry(key) { return form.elements.namedItem(key); }
  function options(select, values, current, allText) {
    const choices = ["all", ...values.filter(v => v && v !== "all")];
    if (current !== "all" && !choices.includes(current)) choices.push(current);
    select.innerHTML = choices.map(value => `<option value="${escapeAttr(value)}">${value === "all" ? allText : escapeHtml(displayLabel(value))}</option>`).join("");
    select.value = current;
  }
  function updateOptions(snapshot) {
    options(fieldInRegistry("quarter"), snapshot.filterOptions?.quarters ?? [], filters.quarter, "Все кварталы");
    options(fieldInRegistry("status"), snapshot.filterOptions?.statuses ?? [], filters.status, "Все статусы");
    options(fieldInRegistry("health"), snapshot.filterOptions?.healthValues ?? [], filters.health, "Все состояния");
  }

  async function loadRegistry({ preserveScroll = false } = {}) {
    const request = ++registryRequest;
    const old = registry;
    list.setAttribute("aria-busy", "true");
    registryMessage.textContent = old ? "Обновляем реестр…" : "Загружаем реестр…";
    if (!old) list.innerHTML = '<p class="empty-state compact">Загружаем проекты…</p>';
    try {
      const snapshot = await fetchJson(registryUrl({ ...filters, sort: filters.sort === "progress" ? "name" : filters.sort,
        direction: filters.sort === "progress" ? "asc" : filters.direction }));
      const enriched = await Promise.all(snapshot.items.map(async item => {
        try {
          const itemCard = await fetchJson(`/api/projects/${encodeURIComponent(item.id)}/card`);
          return { ...item, startDate: itemCard.startDate, taskCount: itemCard.board?.taskCount ?? null };
        } catch { return { ...item, startDate: null, taskCount: null, detailsUnavailable: true }; }
      }));
      if (request !== registryRequest) return;
      registry = { ...snapshot, items: sortItems(enriched) };
      updateOptions(snapshot);
      renderRegistry(registry.items);
      registryMessage.textContent = "";
      if (preserveScroll) requestAnimationFrame(() => window.scrollTo(0, registryScroll));
    } catch (error) {
      if (request !== registryRequest) return;
      if (old) registryMessage.innerHTML = 'Показан устаревший список; текущие фильтры не применены. <button type="button" class="text-link" data-retry-registry>Повторить</button>';
      else registryMessage.textContent = "Не удалось загрузить реестр.";
      if (!old) list.innerHTML = '<div class="empty-state compact">Данные недоступны. <button type="button" class="text-link" data-retry-registry>Повторить</button></div>';
    } finally {
      if (request === registryRequest) list.setAttribute("aria-busy", "false");
    }
  }

  function renderRegistry(items = registry?.items ?? []) {
    const total = registry?.totalCount ?? 0;
    const found = registry?.filteredCount ?? items.length;
    count.textContent = `${found} найдено`;
    if (!items.length) {
      list.innerHTML = `<div class="empty-state compact">${total === 0 ? "Проектов пока нет." : "По этим условиям проекты не найдены."}</div>`;
      return;
    }
    list.innerHTML = items.map(item => `<article class="registry-row" data-health="${escapeAttr(item.health || "unknown")}">
      <div class="registry-row-main"><button class="text-link registry-open" type="button" data-open-project="${escapeAttr(item.id)}">${escapeHtml(item.name)}</button>
        ${item.summary ? `<p>${escapeHtml(item.summary)}</p>` : ""}
        ${item.assessment?.requiresAttention ? `<span class="pill danger">Требует внимания</span>${reasons(item.assessment)}` : ""}</div>
      <dl class="registry-row-data">
        <div><dt>Квартал</dt><dd>${text(item.quarter)}</dd></div>
        <div><dt>Статус</dt><dd>${statusLabel(item.status)}</dd></div>
        <div><dt>Состояние</dt><dd><span class="pill" data-tone="${escapeAttr(item.health || "unknown")}">${healthLabel(item.health)}</span></dd></div>
        <div><dt>Начало</dt><dd>${item.detailsUnavailable ? "Недоступно" : date(item.startDate)}</dd></div>
        <div><dt>Срок</dt><dd>${date(item.deadline)}</dd></div>
        <div><dt>Прогресс</dt><dd>${progress(item.progress)}</dd></div>
        <div><dt>Задачи</dt><dd>${taskCount(item.taskCount)}</dd></div>
      </dl></article>`).join("");
  }

  async function openCard(id, origin = "registry") {
    if (origin === "registry") registryScroll = window.scrollY;
    closeProjectForm();
    showView("project-card");
    updateUrl("project-card", id);
    const request = ++cardRequest;
    const sameCard = card?.id === id;
    if (!sameCard) card = null;
    detail.parentElement.setAttribute("aria-busy", "true");
    if (!sameCard) detail.innerHTML = '<p class="empty-state compact">Загружаем карточку…</p>';
    try {
      const loaded = await fetchJson(`/api/projects/${encodeURIComponent(id)}/card`);
      if (request !== cardRequest || state.activeView !== "project-card") return;
      card = loaded;
      renderCard();
    } catch (error) {
      if (request !== cardRequest) return;
      if (sameCard) showCardNotice("Показана устаревшая карточка. Не удалось обновить данные.", "warning");
      else detail.innerHTML = `<div class="empty-state compact">${error.status === 404 ? "Проект не найден." : "Не удалось загрузить карточку."} <button class="text-link" type="button" data-retry-card="${escapeAttr(id)}">Повторить</button></div>`;
    } finally {
      if (request === cardRequest) detail.parentElement.setAttribute("aria-busy", "false");
    }
  }

  function showCardNotice(message, tone = "quiet") {
    const notice = document.getElementById("project-card-notice");
    if (notice) { notice.textContent = message; notice.dataset.tone = tone; }
  }

  function renderCard() {
    if (!card) return;
    const project = card;
    detail.innerHTML = `<div class="project-detail-head"><div><p class="section-kicker">Карточка проекта</p><h1>${escapeHtml(project.name)}</h1>
      <p>${text(project.summary, "Описание не указано")}</p></div><button class="btn btn-ghost btn-small" type="button" data-refresh-card>Обновить</button></div>
      <p id="project-card-notice" role="status" aria-live="polite"></p>
      <div class="project-detail-actions"><button class="btn btn-primary btn-small" type="button" data-card-action="edit">Редактировать</button>
        <button class="btn btn-secondary btn-small" type="button" data-card-action="board">Открыть доску</button>
        <button class="btn btn-ghost btn-small" type="button" data-card-action="delete">Удалить проект</button></div>
      <dl class="project-detail-facts">
        <div><dt>Квартал</dt><dd>${text(project.quarter)}</dd></div><div><dt>Статус</dt><dd>${statusLabel(project.status)}</dd></div>
        <div><dt>Состояние</dt><dd>${healthLabel(project.health)}</dd></div><div><dt>Начало</dt><dd>${date(project.startDate)}</dd></div>
        <div><dt>Срок завершения</dt><dd>${date(project.deadline)}</dd></div><div><dt>Прогресс</dt><dd>${progress(project.progress)}</dd></div>
        <div><dt>Задачи</dt><dd>${taskCount(project.board?.taskCount)}</dd></div><div><dt>Владелец</dt><dd>${text(project.owner)}</dd></div>
      </dl>
      ${project.assessment?.requiresAttention ? `<section class="project-attention"><h2>Требует внимания</h2>${reasons(project.assessment)}</section>` : ""}
      ${project.assessment?.dataQualityIssues?.length ? `<section class="project-attention"><h2>Качество данных</h2><ul class="assessment-reasons">${project.assessment.dataQualityIssues.map(issue => `<li>${escapeHtml(issue)}</li>`).join("")}</ul></section>` : ""}
      <section class="project-milestones"><div class="section-head"><h2>Контрольные точки</h2><span class="pill">${project.milestoneCount}</span></div>
        <div id="milestone-list">${project.milestones?.length ? project.milestones.map(milestoneRow).join("") : '<p class="empty-state compact">Контрольных точек пока нет.</p>'}</div>
        <form id="milestone-form" class="milestone-form"><h3 id="milestone-form-title">Новая контрольная точка</h3>
          <input type="hidden" name="milestoneId" />
          <label>Название<input name="name" maxlength="255" required /></label>
          <label>Плановая дата<input name="plannedDate" type="date" required /></label>
          <label>Позиция<input name="position" type="number" min="0" step="1" value="0" required /></label>
          <label class="check-field"><input name="completed" type="checkbox" /> Выполнена</label>
          <label>Дата выполнения<input name="completedDate" type="date" /></label>
          <div class="form-actions"><button class="btn btn-primary btn-small" type="submit">Сохранить точку</button>
          <button class="btn btn-ghost btn-small hidden" type="button" data-cancel-milestone>Отмена</button></div>
          <p id="milestone-message" role="status" aria-live="polite"></p>
        </form></section><small class="technical-version">Версия записи: ${project.version}</small>`;
    detail.querySelector("#milestone-form").addEventListener("submit", submitMilestone);
  }

  function milestoneRow(m) {
    return `<article class="milestone-row"><div><strong>${escapeHtml(m.name)}</strong>
      <p>${date(m.plannedDate)} · ${m.completed ? "Выполнена" : "Не выполнена"}${m.completedDate ? " · Факт: " + date(m.completedDate) : ""}</p></div>
      <div class="milestone-actions"><button type="button" class="text-link" data-milestone-action="edit" data-id="${escapeAttr(m.id)}">Редактировать</button>
      <button type="button" class="text-link" data-milestone-action="complete" data-id="${escapeAttr(m.id)}">${m.completed ? "Снять отметку" : "Отметить выполненной"}</button>
      <button type="button" class="text-link" data-milestone-action="delete" data-id="${escapeAttr(m.id)}">Удалить</button></div></article>`;
  }

  function resetFormMode() {
    editOriginal = null;
    state.editingProjectId = null;
    editor.reset();
    elements.formKicker.textContent = "Новый проект";
    elements.formTitle.textContent = "Создать проект";
    elements.submitButton.textContent = "Создать проект";
    elements.cancelEditButton.classList.add("hidden");
    if (state.deliveryModels[0]) elements.deliveryModelField.value = state.deliveryModels[0];
    clearFieldErrors(editor);
    elements.submitMessage.textContent = "";
  }

  function openProjectForm() {
    document.getElementById("project-form").classList.remove("is-collapsed");
    field("name").focus();
  }
  function closeProjectForm() { document.getElementById("project-form").classList.add("is-collapsed"); }

  function startEditProject(project = card) {
    if (!project) return;
    editOriginal = project;
    state.editingProjectId = project.id;
    showView("projects");
    elements.formKicker.textContent = "Редактирование";
    elements.formTitle.textContent = project.name;
    elements.submitButton.textContent = "Сохранить изменения";
    elements.cancelEditButton.classList.remove("hidden");
    for (const input of editor.elements) {
      if (!input.name || input.name === "projectId") continue;
      input.value = project[input.name] ?? "";
    }
    openProjectForm();
  }

  function readProjectForm() {
    const payload = Object.fromEntries(new FormData(editor).entries());
    delete payload.projectId;
    payload.progress = payload.progress === "" ? null : Number(payload.progress);
    payload.startDate = payload.startDate || null;
    return payload;
  }

  function changedFields(original, values) {
    const changes = { expectedVersion: original.version };
    for (const [key, value] of Object.entries(values)) {
      if (key === "budget") continue;
      if ((original[key] ?? null) !== value) changes[key] = value;
    }
    return changes;
  }

  function clearFieldErrors(target) {
    target.querySelectorAll(".field-error").forEach(node => node.remove());
    target.querySelectorAll("[aria-invalid]").forEach(node => node.removeAttribute("aria-invalid"));
  }
  function showFieldErrors(target, errors) {
    clearFieldErrors(target);
    for (const [key, message] of Object.entries(errors ?? {})) {
      const input = target.elements.namedItem(key);
      if (!input) continue;
      input.setAttribute("aria-invalid", "true");
      const note = document.createElement("small");
      note.className = "field-error";
      note.textContent = message;
      input.closest("label")?.appendChild(note);
    }
  }

  function conflictMessage(target, id) {
    target.innerHTML = 'Проект изменён в другой сессии. Изменения не сохранены. <button class="text-link" type="button" data-reload-conflict="' + escapeAttr(id) + '">Загрузить актуальную версию</button>';
  }

  async function submitProject(event) {
    event.preventDefault();
    clearFieldErrors(editor);
    if (!editor.reportValidity()) return;
    const values = readProjectForm();
    const updating = Boolean(editOriginal);
    const body = updating ? changedFields(editOriginal, values) : values;
    if (updating && Object.keys(body).length === 1) {
      elements.submitMessage.textContent = "Изменений нет.";
      return;
    }
    elements.submitMessage.textContent = "Сохраняем проект…";
    try {
      const saved = await fetchJson(updating ? `/api/projects/${encodeURIComponent(editOriginal.id)}` : "/api/projects", {
        method: updating ? "PATCH" : "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body)
      });
      closeProjectForm();
      resetFormMode();
      ++registryRequest;
      registry = null;
      if (updating) {
        card = saved;
        showView("project-card");
        updateUrl("project-card", card.id);
        renderCard();
      } else {
        await openCard(saved.id, "registry");
      }
    } catch (error) {
      showFieldErrors(editor, error.fieldErrors);
      if (error.status === 409) conflictMessage(elements.submitMessage, editOriginal.id);
      else elements.submitMessage.textContent = error.message || "Не удалось сохранить проект.";
    }
  }

  async function submitMilestone(event) {
    event.preventDefault();
    const milestoneForm = event.currentTarget;
    clearFieldErrors(milestoneForm);
    if (!milestoneForm.reportValidity()) return;
    const data = new FormData(milestoneForm);
    const id = data.get("milestoneId");
    const payload = { expectedVersion: card.version, name: String(data.get("name")).trim(),
      plannedDate: data.get("plannedDate"), position: Number(data.get("position")),
      completed: data.get("completed") === "on", completedDate: data.get("completedDate") || null };
    try {
      const updated = await fetchJson(`/api/projects/${encodeURIComponent(card.id)}/milestones${id ? "/" + encodeURIComponent(id) : ""}`, {
        method: id ? "PATCH" : "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload)
      });
      card = updated;
      renderCard();
    } catch (error) { milestoneError(error, milestoneForm); }
  }

  function milestoneError(error, milestoneForm = detail.querySelector("#milestone-form")) {
    showFieldErrors(milestoneForm, error.fieldErrors);
    const message = detail.querySelector("#milestone-message");
    if (error.status === 409) conflictMessage(message, card.id);
    else message.textContent = error.message || "Не удалось сохранить контрольную точку.";
  }

  async function mutateMilestone(id, method, payload) {
    try {
      const updated = await fetchJson(`/api/projects/${encodeURIComponent(card.id)}/milestones/${encodeURIComponent(id)}` +
        (method === "DELETE" ? `?expectedVersion=${card.version}` : ""), {
        method, ...(payload ? { headers: { "Content-Type": "application/json" }, body: JSON.stringify({ expectedVersion: card.version, ...payload }) } : {})
      });
      card = updated;
      renderCard();
    } catch (error) { milestoneError(error); }
  }

  function openDialog(title, content, action, opener, allowedAction = true) {
    dialogOpener = opener;
    dialogAction = action;
    document.getElementById("project-dialog-title").textContent = title;
    document.getElementById("project-dialog-content").innerHTML = content;
    document.getElementById("project-dialog-confirm").classList.toggle("hidden", !allowedAction);
    dialog.classList.remove("hidden");
    document.getElementById("project-dialog-cancel").focus();
  }
  function closeDialog() {
    dialog.classList.add("hidden");
    dialogAction = null;
    dialogOpener?.focus();
  }

  async function deleteProject(project = card, opener) {
    try {
      const impact = await fetchJson(`/api/projects/${encodeURIComponent(project.id)}/deletion-impact`);
      const blocked = !impact.deletionAllowed;
      const notes = [impact.taskCount ? "Сначала удалите или перенесите задачи." : "",
        impact.milestoneCount ? "Сначала удалите контрольные точки." : ""].filter(Boolean);
      openDialog("Удаление проекта", `<p>«${escapeHtml(project.name)}»</p><p>Задачи: ${impact.taskCount}. Контрольные точки: ${impact.milestoneCount}.</p>
        ${notes.map(note => `<p>${note}</p>`).join("")}${blocked ? "" : "<p>Удалить проект без возможности восстановления?</p>"}`,
      async () => {
        try {
          await fetchJson(`/api/projects/${encodeURIComponent(project.id)}`, { method: "DELETE" });
          closeDialog();
          card = null;
          registry = null;
          showView("projects");
          await loadRegistry();
        } catch (error) {
          closeDialog();
          showCardNotice(error.status === 409 ? "Удаление заблокировано: данные проекта изменились. Проверьте зависимости снова." :
            "Не удалось удалить проект: " + error.message, "error");
        }
      }, opener, !blocked);
    } catch (error) { showCardNotice("Не удалось проверить последствия удаления: " + error.message, "error"); }
  }

  function onView(view) {
    if (view === "projects") {
      updateUrl("projects", null);
      syncForm();
      loadRegistry({ preserveScroll: registryScroll > 0 });
    } else if (view !== "project-card") {
      updateUrl(view);
    }
  }

  function restoreRoute() {
    filters = readFilters(window.location.search);
    syncForm();
    const params = new URLSearchParams(window.location.search);
    const view = params.get("view");
    if (view === "projects") showView("projects");
    else if (view === "project-card" && params.get("project")) openCard(params.get("project"), "history");
    else if (navigatingHistory) showView("overview");
  }

  form.addEventListener("input", event => {
    if (event.target.name !== "query") return;
    clearTimeout(debounce);
    debounce = setTimeout(() => {
      filters.query = fieldInRegistry("query").value;
      updateUrl("projects", null, true);
      loadRegistry();
    }, 250);
  });
  form.addEventListener("change", event => {
    if (!keys.includes(event.target.name)) return;
    if (event.target.name === "query") clearTimeout(debounce);
    filters[event.target.name] = event.target.value;
    updateUrl("projects", null, true);
    loadRegistry();
  });
  form.addEventListener("submit", event => event.preventDefault());
  document.getElementById("reset-registry-filters").addEventListener("click", () => {
    filters = { ...defaults }; syncForm(); updateUrl("projects", null, true); loadRegistry();
  });
  list.addEventListener("click", event => {
    const id = event.target.closest("[data-open-project]")?.dataset.openProject;
    if (id) openCard(id);
    if (event.target.closest("[data-retry-registry]")) loadRegistry();
  });
  registryMessage.addEventListener("click", event => {
    if (event.target.closest("[data-retry-registry]")) loadRegistry();
  });
  document.getElementById("back-to-registry").addEventListener("click", () => showView("projects"));
  detail.addEventListener("click", event => {
    const target = event.target.closest("button");
    if (!target) return;
    if (target.dataset.retryCard) return openCard(target.dataset.retryCard, "history");
    if (target.hasAttribute("data-refresh-card")) return openCard(card.id, "history");
    if (target.dataset.reloadConflict) {
      if (state.activeView === "projects") { closeProjectForm(); openCard(target.dataset.reloadConflict, "history"); }
      else openCard(target.dataset.reloadConflict, "history");
      return;
    }
    if (target.dataset.cardAction === "edit") return startEditProject();
    if (target.dataset.cardAction === "delete") return deleteProject(card, target);
    if (target.dataset.cardAction === "board") {
      state.selectedProjectId = card.id;
      showView("boards");
      loadBoard(card.id, card.deliveryModel || "kanban").catch(error => {
        elements.boardColumns.textContent = "Не удалось открыть доску: " + error.message;
      });
      return;
    }
    if (target.hasAttribute("data-cancel-milestone")) return renderCard();
    const action = target.dataset.milestoneAction;
    const milestone = card?.milestones?.find(item => item.id === target.dataset.id);
    if (!milestone) return;
    if (action === "edit") {
      const milestoneForm = detail.querySelector("#milestone-form");
      for (const key of ["milestoneId", "name", "plannedDate", "position", "completedDate"]) {
        milestoneForm.elements.namedItem(key).value = key === "milestoneId" ? milestone.id : milestone[key] ?? "";
      }
      milestoneForm.elements.namedItem("completed").checked = milestone.completed;
      detail.querySelector("#milestone-form-title").textContent = "Редактировать контрольную точку";
      milestoneForm.querySelector("[data-cancel-milestone]").classList.remove("hidden");
      milestoneForm.elements.namedItem("name").focus();
    } else if (action === "complete") {
      mutateMilestone(milestone.id, "PATCH", { completed: !milestone.completed,
        completedDate: milestone.completed ? null : card.calculationDate });
    } else if (action === "delete") {
      openDialog("Удалить контрольную точку?", `<p>${escapeHtml(milestone.name)}</p>`,
        async () => { closeDialog(); await mutateMilestone(milestone.id, "DELETE"); }, target);
    }
  });
  elements.submitMessage.addEventListener("click", event => {
    const id = event.target.closest("[data-reload-conflict]")?.dataset.reloadConflict;
    if (id) { closeProjectForm(); openCard(id, "history"); }
  });
  document.getElementById("project-dialog-cancel").addEventListener("click", closeDialog);
  document.getElementById("project-dialog-confirm").addEventListener("click", () => dialogAction?.());
  dialog.addEventListener("keydown", event => {
    if (event.key === "Escape") { closeDialog(); return; }
    if (event.key !== "Tab") return;
    const buttons = [...dialog.querySelectorAll("button:not(.hidden)")];
    const first = buttons[0], last = buttons[buttons.length - 1];
    if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
  });
  window.addEventListener("popstate", () => {
    navigatingHistory = true;
    try { restoreRoute(); } finally { navigatingHistory = false; }
  });

  window.PMProjectsUI = { onView, renderRegistry, openCard, startEditProject, openProjectForm, closeProjectForm,
    submitProject, deleteProject, resetFormMode, registryUrl, readFilters, sortItems, changedFields, progress };
  restoreRoute();
})();
