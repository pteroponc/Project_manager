/* PM-006.2B: presentation only. Portfolio assessments remain owned by PM-001. */
(() => {
  const root = document.getElementById("dashboard-workspace");
  if (!root) return;
  const get = id => document.getElementById(id);
  const form = get("control-filters");
  const keys = ["quarter", "status", "health"];
  const filters = {quarter:"all", status:"all", health:"all"};
  const tabs = Array.from(root.querySelectorAll("[data-control-tab]"));
  const mobile = window.matchMedia("(max-width: 520px)");
  let activeTab = "portfolio";
  let snapshot = null;
  let snapshotFilters = null;
  let catalog = state.projects ?? [];
  let request = 0;
  let loading = false;
  const esc = value => escapeHtml(value ?? "");
  const empty = text => `<p class="empty-state compact">${esc(text)}</p>`;
  const tone = value => ({red:"danger", yellow:"warning", green:"success"})[value] ?? "neutral";
  const link = project => `<button type="button" class="text-link control-project-link" data-control-project="${esc(project.id)}">${esc(project.name)}</button>`;
  const badge = (text, role = "neutral") => `<span class="pill ${role}">${esc(text)}</span>`;
  const projectRows = (items, body) => items.map(project => `<article class="control-row"><div class="control-row-head">${link(project)}${badge(`${displayLabel(project.status)} · ${project.health ? displayLabel(project.health) : "Нет данных о состоянии"}`, tone(project.health))}</div>${body(project)}</article>`).join("");

  function selectTab(name, focus = false) {
    if (!tabs.some(tab => tab.dataset.controlTab === name)) return;
    activeTab = name;
    root.dataset.activeTab = name;
    for (const tab of tabs) {
      const selected = tab.dataset.controlTab === name;
      tab.setAttribute("aria-selected", String(selected));
      tab.tabIndex = selected ? 0 : -1;
      if (selected && focus) tab.focus();
    }
    // CSS hides the other panes only at the mobile breakpoint; no API/filter changes.
    for (const [id, tab] of [["control-portfolio","portfolio"], ["control-deadlines","deadlines"], ["architecture-portfolio-panel","architecture"]]) {
      get(id).setAttribute("role", mobile.matches ? "tabpanel" : "region");
      get(id).setAttribute("aria-labelledby", mobile.matches ? `control-tab-${tab}`
        : {portfolio:"control-attention-title", deadlines:"control-deadlines-title", architecture:"architecture-portfolio-title"}[tab]);
    }
  }

  function filterCount() {
    get("control-filter-count").textContent = String(keys.filter(key => filters[key] !== "all").length);
  }

  function populate(data) {
    const values = {quarter:data.quarters, status:data.statuses, health:data.healthValues};
    const labels = {quarter:"Все кварталы", status:"Все статусы", health:"Все состояния"};
    for (const key of keys) {
      const field = form.elements.namedItem(key);
      const options = [...new Set([...(values[key] ?? []), ...(filters[key] === "all" ? [] : [filters[key]])])];
      field.innerHTML = `<option value="all">${labels[key]}</option>` + options.map(value =>
        `<option value="${esc(value)}">${esc(key === "quarter" ? value || "Не указан" : displayLabel(value))}</option>`).join("");
      field.value = filters[key];
    }
  }

  function render(data) {
    const kpis = [
      ["Проекты", data.projectCount, `${data.activeCount} активных`],
      ["Требуют внимания", data.attentionCount, "По правилам портфеля"],
      ["Средний прогресс", data.averageProgress == null ? "Нет данных" : `${data.averageProgress}%`, `По ${data.progressCoverage} из ${data.projectCount}`],
      ["Просрочено", data.overdueCount, "Незавершённые проекты"]
    ];
    get("control-kpis").innerHTML = kpis.map(([label,value,caption]) => `<article class="control-kpi"><span>${label}</span><strong>${esc(value)}</strong><small>${esc(caption)}</small></article>`).join("");
    const noProjects = !data.totalProjectCount ? "Проектов пока нет. Создайте проект в реестре."
      : !data.projectCount ? "Нет проектов по фильтрам портфеля. Сбросьте условия." : null;
    get("control-attention-count").textContent = `Проектов: ${data.attentionCount}`;
    get("control-attention").innerHTML = data.attentionProjects.length ? projectRows(data.attentionProjects, project =>
      `<p>${project.attentionReasons.map(esc).join(" · ")}</p><p class="hint-text">Срок: ${esc(overviewDate(project.deadline))}${project.risk ? " · " + esc(project.risk) : ""}</p>`)
      : empty(noProjects || "Отклонений по правилам портфеля не выявлено.");
    get("control-deadline-list").innerHTML = data.milestones.length ? projectRows(data.milestones, project =>
      `<p>${esc(project.milestone || "Контрольная точка не указана")}</p><p class="hint-text">${esc(overviewDate(project.deadline))} · ${project.daysUntilDeadline === 0 ? "Сегодня" : `Через ${Number(project.daysUntilDeadline)} дн.`}</p>`)
      : empty(noProjects || "В ближайшие 14 дней плановых сроков нет.");
    // Preserve the existing future-deadline view without reinterpreting PM-001's 14-day list.
    const upcoming = data.projects.filter(project => project.status !== "done" && Number.isFinite(project.daysUntilDeadline) && project.daysUntilDeadline >= 0)
      .sort((a,b) => a.daysUntilDeadline - b.daysUntilDeadline || String(a.name).localeCompare(String(b.name)));
    get("control-all-deadlines").innerHTML = upcoming.length ? projectRows(upcoming, project =>
      `<p>${esc(project.milestone || "Без контрольной точки")} · ${esc(overviewDate(project.deadline))}</p>`) : empty(noProjects || "Предстоящих сроков нет.");
    get("control-project-list").innerHTML = data.projects.length ? projectRows(data.projects, project => {
      const model = catalog.find(item => item.id === project.id)?.deliveryModel;
      return `<p class="hint-text">${esc(project.owner)} · ${esc(project.quarter)}${model ? " · " + esc(displayLabel(model)) : ""} · ${esc(overviewDate(project.deadline))}</p>`;
    }) : empty(noProjects);
    renderDetails(data);
  }

  function renderDetails(data) {
    get("control-quality").innerHTML = `<h2>Качество данных</h2>` + (data.dataQualityProjects.length
      ? projectRows(data.dataQualityProjects, project => `<ul>${project.dataQualityIssues.map(issue => `<li>${esc(issue)}</li>`).join("")}</ul>`)
      : empty("Неполных или противоречивых данных не обнаружено."));
    const ids = new Set(data.projects.map(project => project.id));
    const projects = catalog.filter(project => ids.has(project.id));
    const watch = projects.filter(project => project.health !== "green" || milestoneTone(project) !== "green")
      .sort((a,b) => riskWeight(b)-riskWeight(a)).slice(0,4);
    const due30 = projects.filter(project => {const days=daysUntil(project.deadline); return project.status !== "done" && days >= 0 && days <= 30;}).length;
    const progress = {start:0, middle:0, finish:0};
    for (const project of data.projects) if (project.progress != null && project.progress >= 0 && project.progress <= 100)
      progress[project.progress < 30 ? "start" : project.progress < 70 ? "middle" : "finish"]++;
    const card = (title, body) => `<article class="dashboard-card control-box"><h3>${title}</h3>${body}</article>`;
    const distribution = (values, order, labels = {}, health = false) => order.filter(key => values[key]).map(key =>
      `<div class="dashboard-bar-row"><span>${esc(labels[key] ?? displayLabel(key))}</span><div class="dashboard-bar" data-control-tone="${health ? tone(key) : key === "unknown" ? "neutral" : "success"}"><span style="width:${Math.round(values[key]/data.projectCount*100)}%"></span></div><strong>${Number(values[key])}</strong></div>`).join("");
    const budgetLeaders = data.projects.filter(project => project.budget != null && project.budget >= 0)
      .slice().sort((a,b) => b.budget-a.budget).slice(0,4);
    get("project-dashboard-grid").innerHTML = !data.projectCount ? empty("Нет данных для дополнительной аналитики.") :
      card("Состояние", distribution(data.healthCounts,["green","yellow","red","unknown"],{unknown:"Нет данных"},true)) +
      card("Статусы", distribution(data.statusCounts,["active","planned","paused","done","unknown"],{unknown:"Неизвестный статус"})) +
      card("Модели поставки", projects.length ? distribution(groupCount(projects,"deliveryModel"),["kanban","scrum","waterfall"]) : empty("Каталог моделей ещё не загружен. Обновите портфель.")) +
      card("Прогресс", distribution(progress,["start","middle","finish"],{start:"0–29%",middle:"30–69%",finish:"70–100%"})) +
      card("Бюджеты", `<p>Всего: ${data.totalBudget == null ? "Нет данных" : esc(formatCurrency(data.totalBudget))} · По ${Number(data.budgetCoverage)} из ${Number(data.projectCount)} проектов</p><p class="hint-text">Средний: ${data.budgetCoverage ? esc(formatCurrency(Math.round(data.totalBudget/data.budgetCoverage))) : "Нет данных"}</p>` + projectRows(budgetLeaders,project => `<p>${esc(formatCurrency(project.budget))}</p>`)) +
      card("Наблюдение за исполнением", `<p class="hint-text">Учитывает состояние, сроки и прогресс. Оценивается отдельно от рисков ИБ и показателя «Требуют внимания».</p>` + (watch.length ? projectRows(watch, project => `<p>${badge(displayLabel(milestoneTone(project)),tone(milestoneTone(project)))} ${esc(project.risk || "Риск не указан")}</p>`) : empty(projects.length ? "Проектов для наблюдения нет." : "Каталог ещё не загружен."))) +
      card("Горизонт 30 дней", projects.length ? `<p>${due30} незавершённых проектов со сроком в 0–30 дней.</p><p class="hint-text">Дополнительный горизонт, отличается от 14 дней в оперативной сводке.</p>` : empty("Каталог ещё не загружен."));
  }

  function snapshotFilterLabel() {
    if (!snapshotFilters) return "";
    const labels = keys.map(key => {
      const value = snapshotFilters[key];
      if (key === "quarter") return `квартал: ${value === "all" ? "все кварталы" : value}`;
      if (key === "status") return `статус: ${value === "all" ? "все статусы" : displayLabel(value)}`;
      return `состояние: ${value === "all" ? "все состояния" : displayLabel(value)}`;
    });
    return `Фильтры показанных данных — ${labels.join("; ")}.`;
  }

  async function load() {
    const current = ++request;
    const requestedFilters = Object.freeze({...filters});
    loading = true;
    get("control-refresh").disabled = true;
    get("control-kpis").setAttribute("aria-busy", "true");
    get("control-message").textContent = snapshot
      ? `Обновляем портфель… Показан предыдущий снимок; новые условия ещё не применены. ${snapshotFilterLabel()}`
      : "Загружаем портфель…";
    try {
      const data = await fetchJson("/api/portfolio/overview?" + new URLSearchParams(requestedFilters));
      if (current !== request || state.activeView !== "dashboards") return;
      if (!data || !Array.isArray(data.projects) || !Array.isArray(data.attentionProjects) || !Array.isArray(data.milestones)
          || !Array.isArray(data.dataQualityProjects) || !data.healthCounts || !data.statusCounts) throw new Error("Invalid portfolio response");
      snapshot = data;
      snapshotFilters = requestedFilters;
      populate(data);
      render(data);
      get("control-message").textContent = `Портфель на ${overviewDate(data.calculationDate)} · ${data.projectCount} из ${data.totalProjectCount} проектов · Europe/Moscow. ${snapshotFilterLabel()}`;
    } catch (error) {
      if (current !== request || state.activeView !== "dashboards") return;
      get("control-message").textContent = snapshot
        ? `Не удалось обновить портфель. Показан предыдущий снимок; текущие фильтры ещё не применены. ${snapshotFilterLabel()} Повторите обновление.`
        : "Не удалось загрузить портфель. Данные недоступны. Нажмите «Обновить портфель».";
      if (!snapshot) for (const id of ["control-kpis","control-attention","control-deadline-list","control-project-list","control-quality","project-dashboard-grid"])
        get(id).innerHTML = empty("Данные портфеля недоступны.");
    } finally {
      if (current === request) {
        loading = false;
        get("control-refresh").disabled = false;
        get("control-kpis").setAttribute("aria-busy", "false");
      }
    }
  }

  for (const tab of tabs) {
    tab.addEventListener("click", () => selectTab(tab.dataset.controlTab));
    tab.addEventListener("keydown", event => {
      if (!["ArrowLeft","ArrowRight","Home","End"].includes(event.key)) return;
      event.preventDefault();
      const index = tabs.indexOf(tab);
      const next = event.key === "Home" ? 0 : event.key === "End" ? tabs.length-1
        : (index + (event.key === "ArrowRight" ? 1 : -1) + tabs.length) % tabs.length;
      selectTab(tabs[next].dataset.controlTab, true);
    });
  }
  mobile.addEventListener("change", () => selectTab(activeTab));
  form.addEventListener("submit", event => event.preventDefault());
  form.addEventListener("change", event => {
    if (!keys.includes(event.target.name)) return;
    filters[event.target.name] = event.target.value;
    filterCount(); void load();
  });
  get("control-reset").addEventListener("click", () => {
    for (const key of keys) {filters[key]="all"; form.elements.namedItem(key).value="all";}
    filterCount(); void load();
  });
  get("control-refresh").addEventListener("click", () => {
    if (!loading) {void load(); void loadProjectCatalog();}
  });
  root.addEventListener("click", event => {
    const button = event.target.closest("[data-control-project]");
    if (button) void window.PMProjectsUI?.openCard(button.dataset.controlProject, "dashboards");
    if (event.target.closest("[data-open-control-architecture]")) {
      selectTab("architecture");
      get("architecture-portfolio-title").focus();
      get("architecture-portfolio-panel").scrollIntoView({block:"start"});
    }
  });
  window.PMDashboardUI = {
    onView(view) {
      if (view !== "dashboards") {
        ++request;
        loading=false;
        get("control-refresh").disabled=false;
        get("control-kpis").setAttribute("aria-busy","false");
      } else if (!loading) void load();
    },
    onCatalog(projects) {catalog=projects; if (snapshot) render(snapshot);},
    onArchitecture(data, message) {
      const alert = get("control-architecture-alert");
      if (!data) {alert.textContent=message; return;}
      const counts=data.readinessCounts;
      const affected = (data.items ?? []).filter(item => ["BLOCKED","ATTENTION_REQUIRED"].includes(item.assessment.status));
      alert.innerHTML = `<p class="hint-text">Отдельный срез архитектуры и ИБ${message ? " · " + esc(message) : ""}</p><p>${Number(counts.BLOCKED ?? 0)} заблокировано · ${Number(counts.ATTENTION_REQUIRED ?? 0)} требуют внимания · ${Number(counts.NOT_ASSESSED ?? 0)} без завершённой оценки</p>`
        + (affected.length ? `<p class="hint-text">Проекты на текущей странице сводки готовности:</p>` + affected.map(item => `<div class="control-row">${link(item.project)} ${badge(item.assessment.statusLabel,item.assessment.status === "BLOCKED" ? "danger" : "warning")}</div>`).join("") : "")
        + `<button class="text-link" type="button" data-open-control-architecture>Открыть готовность архитектуры и ИБ</button>`;
    }
  };
  selectTab(activeTab);
  if (state.activeView === "dashboards") void load();
})();
