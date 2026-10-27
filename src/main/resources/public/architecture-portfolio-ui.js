/* PM-003D.3: the portfolio view renders only the narrow server summary. */
(() => {
  const panel = document.getElementById("architecture-portfolio-panel");
  if (!panel) return;
  const form = document.getElementById("architecture-portfolio-filters");
  const message = document.getElementById("architecture-portfolio-message");
  const counts = document.getElementById("architecture-readiness-counts");
  const total = document.getElementById("architecture-portfolio-total");
  const list = document.getElementById("architecture-portfolio-list");
  const pageLabel = document.getElementById("architecture-portfolio-page");
  const previous = document.getElementById("architecture-portfolio-prev");
  const next = document.getElementById("architecture-portfolio-next");
  const keys = ["query", "quarter", "status", "readiness", "sort", "direction", "size"];
  const defaults = {query:"", quarter:"", status:"", readiness:"all", sort:"name", direction:"asc", size:"20", page:0};
  const statuses = [
    ["BLOCKED", "Заблокировано", "danger"],
    ["ATTENTION_REQUIRED", "Требует внимания", "warning"],
    ["NOT_ASSESSED", "Оценка не завершена", ""],
    ["READY", "Проверки завершены", "success"]
  ];
  let filters = {...defaults};
  let snapshot = null;
  let request = 0;
  let loaded = false;
  let loading = false;
  let paginationReady = false;
  let debounce;
  const field = key => form.elements.namedItem(key);
  const esc = value => escapeHtml(value ?? "");

  function syncForm() {
    for (const key of keys) field(key).value = filters[key];
  }

  function syncTextFilters() {
    let changed = false;
    for (const key of ["query", "quarter", "status"]) {
      if (filters[key] !== field(key).value) changed = true;
      filters[key] = field(key).value;
    }
    return changed;
  }

  function url() {
    const params = new URLSearchParams({
      query:filters.query, quarter:filters.quarter.trim() || "all",
      status:filters.status.trim() || "all", readiness:filters.readiness,
      sort:filters.sort, direction:filters.direction, page:String(filters.page), size:filters.size
    });
    return "/api/architecture/portfolio/summary?" + params;
  }

  function syncPagination() {
    previous.disabled = loading || !paginationReady || !snapshot || snapshot.page === 0;
    next.disabled = loading || !paginationReady || !snapshot || snapshot.page + 1 >= snapshot.totalPages;
  }

  function render(data) {
    counts.innerHTML = statuses.map(([code, label]) => `<article class="metric-card panel">
      <span class="metric-label">${label}</span>
      <strong class="metric-value">${Number(data.readinessCounts[code] ?? 0)}</strong>
      <small>${code === "READY" ? "Расчётный показатель" : "Проектов"}</small>
    </article>`).join("");
    total.textContent = `${data.filteredCount} из ${data.totalCount} проектов`;
    if (!data.items.length) {
      list.innerHTML = `<p class="empty-state compact">${data.totalCount === 0
        ? "По заданным условиям проектов нет."
        : data.filteredCount === 0 ? "По выбранной готовности проектов нет."
          : "На этой странице проектов нет."}</p>`;
    } else {
      list.innerHTML = data.items.map(item => {
        const project = item.project;
        const assessment = item.assessment;
        const tone = ({BLOCKED:"danger", ATTENTION_REQUIRED:"warning", READY:"success"})[assessment.status] || "";
        return `<article class="registry-row architecture-portfolio-row">
          <div class="registry-row-main"><button class="text-link registry-open" type="button" data-open-architecture-project="${esc(project.id)}">${esc(project.name)}</button>
            <p>${esc(project.owner)} · ${esc(project.quarter)} · ${esc(displayLabel(project.projectStatus))}</p>
            <span class="pill ${tone}">${esc(assessment.statusLabel)}</span></div>
          <dl class="registry-row-data">
            <div><dt>Паспорт</dt><dd>${item.passportPresent ? "Есть" : "Нет"}</dd></div>
            <div><dt>Компоненты</dt><dd>${Number(item.componentCount)}</dd></div>
            <div><dt>Интеграции</dt><dd>${Number(item.integrationCount)}</dd></div>
            <div><dt>Проверки</dt><dd>${Number(item.checkCount)}</dd></div>
            <div><dt>Риски ИБ</dt><dd>${Number(item.securityRiskCount)}</dd></div>
          </dl>
        </article>`;
      }).join("");
    }
    syncPagination();
    pageLabel.textContent = data.totalPages ? `Страница ${data.page + 1} из ${data.totalPages}` : "Страниц нет";
  }

  async function load() {
    const current = ++request;
    loading = true;
    paginationReady = false;
    syncPagination();
    panel.setAttribute("aria-busy", "true");
    list.setAttribute("aria-busy", "true");
    message.textContent = snapshot ? "Обновляем сводку… Пока показаны предыдущие данные." : "Загружаем сводку…";
    if (!snapshot) list.innerHTML = '<p class="empty-state compact">Загружаем проекты…</p>';
    try {
      const data = await fetchJson(url());
      if (current !== request || state.activeView !== "dashboards") return;
      if (!data || !Array.isArray(data.items) || !data.readinessCounts || !Number.isInteger(data.page)
          || !Number.isInteger(data.totalPages)) throw new Error("Invalid portfolio response");
      snapshot = data;
      loaded = true;
      filters.page = data.page;
      paginationReady = true;
      render(data);
      message.textContent = `Сводка на ${esc(data.calculationDate)} · ${data.pageItemCount} на текущей странице.`;
    } catch (error) {
      if (current !== request || state.activeView !== "dashboards") return;
      message.textContent = snapshot
        ? "Не удалось обновить сводку. Показаны предыдущие данные; текущие фильтры ещё не применены."
        : "Не удалось загрузить сводку. Повторите загрузку.";
      if (!snapshot) list.innerHTML = '<p class="empty-state compact">Данные недоступны. Нажмите «Обновить».</p>';
    } finally {
      if (current === request) {
        loading = false;
        syncPagination();
        panel.setAttribute("aria-busy", "false");
        list.setAttribute("aria-busy", "false");
      }
    }
  }

  function update(key, value) {
    syncTextFilters();
    filters[key] = value;
    filters.page = 0;
    load();
  }

  form.addEventListener("input", event => {
    if (event.target.name !== "query" && event.target.name !== "quarter" && event.target.name !== "status") return;
    ++request;
    paginationReady = false;
    syncPagination();
    clearTimeout(debounce);
    debounce = setTimeout(() => update(event.target.name, event.target.value), 250);
  });
  form.addEventListener("change", event => {
    if (!keys.includes(event.target.name)) return;
    clearTimeout(debounce);
    update(event.target.name, event.target.value);
  });
  form.addEventListener("submit", event => event.preventDefault());
  document.getElementById("architecture-portfolio-reset").addEventListener("click", () => {
    clearTimeout(debounce);
    filters = {...defaults}; syncForm(); load();
  });
  document.getElementById("architecture-portfolio-refresh").addEventListener("click", () => {
    clearTimeout(debounce);
    if (syncTextFilters()) filters.page = 0;
    load();
  });
  previous.addEventListener("click", () => {
    if (!loading && paginationReady && snapshot && filters.page === snapshot.page && filters.page > 0) {
      --filters.page; load();
    }
  });
  next.addEventListener("click", () => {
    if (!loading && paginationReady && snapshot && filters.page === snapshot.page && filters.page + 1 < snapshot.totalPages) {
      ++filters.page; load();
    }
  });
  list.addEventListener("click", event => {
    const id = event.target.closest("[data-open-architecture-project]")?.dataset.openArchitectureProject;
    if (id) window.PMProjectsUI?.openCard(id, "dashboards");
  });

  function onView(view) {
    if (view === "dashboards") {
      if (syncTextFilters()) { filters.page = 0; paginationReady = false; }
      if (!loading && (!loaded || !paginationReady)) load();
    }
    else {
      ++request;
      clearTimeout(debounce);
      if (loading) loaded = false;
      loading = false;
      paginationReady = false;
      syncPagination();
      panel.setAttribute("aria-busy", "false");
      list.setAttribute("aria-busy", "false");
    }
  }

  syncForm();
  window.PMArchitecturePortfolioUI = {onView, load, url};
  if (state.activeView === "dashboards") onView("dashboards");
})();
