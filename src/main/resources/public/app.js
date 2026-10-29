const BOARDS_STORAGE_KEY = "project-manager-created-boards";

const LABELS = {
  health: {
    green: "В норме",
    yellow: "Предупреждение",
    red: "Проблемный"
  },
  status: {
    active: "Активен",
    planned: "Запланирован",
    paused: "На паузе",
    done: "Завершен"
  },
  deliveryModel: {
    kanban: "Канбан",
    scrum: "Скрам",
    waterfall: "Каскад"
  },
  priority: {
    critical: "Критичный",
    high: "Высокий",
    medium: "Средний",
    low: "Низкий"
  },
  releaseStatus: {
    planning: "Планирование",
    boarding: "Комплектация",
    frozen: "Код заморожен",
    qa: "Тестирование",
    released: "Выпущен"
  }
};

const state = {
  projects: [],
  releaseTrains: [],
  releaseTrainSnapshot: null,
  createdBoards: loadCreatedBoards(),
  filters: {
    quarter: "all",
    health: "all",
    status: "all"
  },
  overview: null,
  overviewRequest: 0,
  projectsRequest: 0,
  boardCatalogLoading: false,
  boardCatalogLoaded: false,
  boardCatalogError: false,
  trainsRequest: 0,
  trainEditorRequest: 0,
  trainEditorLoading: false,
  editingReleaseTrainId: null,
  trainMutationPending: false,
  boardRequest: 0,
  boardLoading: false,
  boardProjectSelectionRequired: false,
  createTaskContext: null,
  overviewLoadedAt: null,
  activeView: "overview",
  selectedProjectId: null,
  deliveryModels: [],
  editingProjectId: null,
  currentBoard: null,
  activeBoardModel: "kanban",
  boardFilters: {
    query: "",
    priority: "all",
    blocked: false
  },
  boardDrilledIn: false,
  selectedBoardCardId: null,
  draggingBoardCardId: null
};

ensureTaskModalMarkup();
ensureCreateTaskPanelMarkup();

const elements = {
  quarterFilter: document.getElementById("quarter-filter"),
  healthFilter: document.getElementById("health-filter"),
  statusFilter: document.getElementById("status-filter"),
  resetOverviewFilters: document.getElementById("reset-overview-filters"),
  overviewAsOf: document.getElementById("overview-as-of"),
  overviewDataQuality: document.getElementById("overview-data-quality"),
  projectsList: document.getElementById("projects-list"),
  projectsCount: document.getElementById("projects-count"),
  boardColumns: document.getElementById("board-columns"),
  boardTitle: document.getElementById("board-title"),
  boardModel: document.getElementById("board-model"),
  boardDeck: document.getElementById("board-deck"),
  createdBoardList: document.getElementById("created-board-list"),
  boardProjectSelect: document.getElementById("board-project-select"),
  boardProjectRefresh: document.getElementById("board-project-refresh"),
  boardProjectMessage: document.getElementById("board-project-message"),
  boardMessage: document.getElementById("board-message"),
  createTaskButton: document.getElementById("create-task-button"),
  createTaskPanel: document.getElementById("create-task-panel"),
  createTaskForm: document.getElementById("create-task-form"),
  createTaskColumn: document.getElementById("create-task-column"),
  createTaskTitle: document.getElementById("create-task-title"),
  createTaskOwner: document.getElementById("create-task-owner"),
  createTaskDueDate: document.getElementById("create-task-due-date"),
  boardInsights: document.getElementById("board-insights"),
  boardFilters: document.getElementById("board-filters"),
  boardSearch: document.getElementById("board-search"),
  boardPriorityFilter: document.getElementById("board-priority-filter"),
  boardBlockedFilter: document.getElementById("board-blocked-filter"),
  boardTaskCount: document.getElementById("board-task-count"),
  boardPointCount: document.getElementById("board-point-count"),
  boardOverdueCount: document.getElementById("board-overdue-count"),
  boardBlockedCount: document.getElementById("board-blocked-count"),
  cancelTaskCreate: document.getElementById("cancel-task-create"),
  summary: document.getElementById("portfolio-summary"),
  sliceMilestonesOnTrack: document.getElementById("slice-milestones-on-track"),
  portfolioSliceTable: document.getElementById("portfolio-slice-table"),
  overviewProjectList: document.getElementById("overview-project-list"),
  overviewHealthyCount: document.getElementById("overview-healthy-count"),
  overviewProblemCount: document.getElementById("overview-problem-count"),
  releaseTrainList: document.getElementById("release-train-list"),
  releaseTrainCount: document.getElementById("release-train-count"),
  releaseTrainReadiness: document.getElementById("release-train-readiness"),
  releaseTrainBlocked: document.getElementById("release-train-blocked"),
  releaseTrainCapacity: document.getElementById("release-train-capacity"),
  releaseTrainForm: document.getElementById("release-train-form"),
  releaseTrainMessage: document.getElementById("release-train-message"),
  releaseTrainFormTitle: document.getElementById("release-train-form-title"),
  releaseTrainSubmit: document.getElementById("release-train-submit"),
  releaseTrainCancelEdit: document.getElementById("release-train-cancel-edit"),
  refreshButton: document.getElementById("refresh-button"),
  form: document.getElementById("create-project-form"),
  formKicker: document.getElementById("form-kicker"),
  formTitle: document.getElementById("form-title"),
  formStatus: document.getElementById("form-status"),
  addProjectLink: document.getElementById("add-project-link"),
  closeProjectFormButton: document.getElementById("close-project-form"),
  submitMessage: document.getElementById("submit-message"),
  submitButton: document.getElementById("submit-button"),
  cancelEditButton: document.getElementById("cancel-edit-button"),
  projectIdField: document.getElementById("project-id-field"),
  deliveryModelField: document.getElementById("delivery-model-field"),
  taskModal: document.getElementById("task-modal"),
  taskModalColumn: document.getElementById("task-modal-column"),
  taskModalTitle: document.getElementById("task-modal-title"),
  taskModalDescription: document.getElementById("task-modal-description"),
  taskModalOwner: document.getElementById("task-modal-owner"),
  taskModalDueDate: document.getElementById("task-modal-due-date"),
  taskModalPriority: document.getElementById("task-modal-priority"),
  taskModalEstimate: document.getElementById("task-modal-estimate"),
  taskModalLabels: document.getElementById("task-modal-labels"),
  taskModalBlocked: document.getElementById("task-modal-blocked"),
  taskModalClose: document.getElementById("task-modal-close"),
  taskModalEdit: document.getElementById("task-modal-edit"),
  taskModalDelete: document.getElementById("task-modal-delete"),
  statProjectCount: document.querySelector('[data-stat="projectCount"]'),
  statActiveCount: document.querySelector('[data-stat="activeCount"]'),
  statRiskyCount: document.querySelector('[data-stat="attentionCount"]'),
  statAverageProgress: document.querySelector('[data-stat="averageProgress"]'),
  releaseChecksUpdated: document.getElementById("release-checks-updated"),
  releaseTag: document.getElementById("release-tag"),
  releaseAssets: document.getElementById("release-assets"),
  releaseLink: document.getElementById("release-link"),
  releaseChecksMessage: document.getElementById("release-checks-message"),
  releaseCheckList: document.getElementById("release-check-list"),
  refreshReleaseChecks: document.getElementById("refresh-release-checks"),
  projectDashboardGrid: document.getElementById("project-dashboard-grid"),
  dashboardHealthSummary: document.getElementById("dashboard-health-summary"),
  dashboardDeliverySummary: document.getElementById("dashboard-delivery-summary"),
  viewSections: document.querySelectorAll("[data-view-section]"),
  viewButtons: document.querySelectorAll("[data-view]")
};

elements.refreshReleaseChecks?.addEventListener("click", () => loadReleaseChecks(true));

function ensureTaskModalMarkup() {
  if (document.getElementById("task-modal")) {
    return;
  }

  const modal = document.createElement("div");
  modal.className = "modal-overlay hidden";
  modal.id = "task-modal";
  modal.setAttribute("role", "dialog");
  modal.setAttribute("aria-modal", "true");
  modal.setAttribute("aria-labelledby", "task-modal-title");
  modal.innerHTML = `
    <section class="task-modal panel">
      <div class="section-head">
        <div>
          <p class="section-kicker" id="task-modal-column">Задача</p>
          <h2 id="task-modal-title">Карточка доски</h2>
        </div>
        <button class="btn btn-ghost btn-small" type="button" id="task-modal-close">Закрыть</button>
      </div>
      <p class="task-modal-description" id="task-modal-description"></p>
      <dl class="task-modal-details">
        <div><dt>Owner</dt><dd id="task-modal-owner"></dd></div>
        <div><dt>Due date</dt><dd id="task-modal-due-date"></dd></div>
      </dl>
      <div class="task-modal-actions">
        <button class="btn btn-primary" type="button" id="task-modal-edit">Редактировать</button>
        <button class="btn btn-danger" type="button" id="task-modal-delete">Удалить</button>
      </div>
    </section>
  `;
  document.body.appendChild(modal);
}

function ensureCreateTaskPanelMarkup() {
  if (document.getElementById("create-task-panel")) {
    return;
  }

  const boardPanel = document.getElementById("board-panel");
  const boardColumns = document.getElementById("board-columns");
  if (!boardPanel || !boardColumns) {
    return;
  }

  const panel = document.createElement("section");
  panel.className = "create-task-panel hidden";
  panel.id = "create-task-panel";
  panel.innerHTML = `
    <form class="board-card-form" id="create-task-form">
      <label>
        <span>Колонка</span>
        <select name="columnKey" id="create-task-column" required></select>
      </label>
      <label>
        <span>Название</span>
        <input name="title" id="create-task-title" placeholder="Новая задача" required />
      </label>
      <label>
        <span>Описание</span>
        <textarea name="description" rows="3" placeholder="Что нужно сделать" required></textarea>
      </label>
      <label>
        <span>Owner</span>
        <input name="owner" id="create-task-owner" required />
      </label>
      <label>
        <span>Due date</span>
        <input name="dueDate" id="create-task-due-date" type="date" required />
      </label>
      <div class="board-card-actions">
        <button class="btn btn-primary btn-small" type="submit">Создать</button>
        <button class="btn btn-ghost btn-small" type="button" id="cancel-task-create">Отмена</button>
      </div>
    </form>
  `;
  boardPanel.insertBefore(panel, boardColumns);
}

const scrollButtons = document.querySelectorAll("[data-scroll]");

scrollButtons.forEach((button) => {
  button.addEventListener("click", () => {
    const target = document.querySelector(button.dataset.scroll);
    target?.scrollIntoView({ behavior: "smooth", block: "start" });
  });
});

elements.viewButtons.forEach((button) => {
  button.addEventListener("click", () => {
    if (showView(button.dataset.view) === false) return;
    if (button.dataset.openProjectForm === "true") {
      resetFormMode();
      openProjectForm();
    }
  });
});

window.PMBoardContext = { openProjectBoard, onProjectDeleted };

document.getElementById("filters-form")?.addEventListener("change", async (event) => {
  state.filters[event.target.name] = event.target.value;
  await loadOverview();
});
elements.resetOverviewFilters?.addEventListener("click", async () => {
  state.filters = { quarter: "all", health: "all", status: "all" };
  if (elements.quarterFilter) elements.quarterFilter.value = "all";
  if (elements.healthFilter) elements.healthFilter.value = "all";
  if (elements.statusFilter) elements.statusFilter.value = "all";
  await loadOverview();
});

elements.refreshButton?.addEventListener("click", loadOverview);
document.getElementById("return-overview")?.addEventListener("click", () => showView("overview"));
elements.boardFilters?.addEventListener("input", () => {
  state.boardFilters.query = elements.boardSearch.value.trim().toLowerCase();
  state.boardFilters.priority = elements.boardPriorityFilter.value;
  state.boardFilters.blocked = elements.boardBlockedFilter.checked;
  renderBoardColumns();
});
elements.createTaskButton?.addEventListener("click", () => {
  const firstColumn = state.currentBoard?.columns?.[0];
  if (firstColumn) {
    startCreateBoardCard(firstColumn.key);
  }
});
elements.boardProjectSelect.addEventListener("change", () => {
  void selectBoardProject(elements.boardProjectSelect.value);
});
elements.boardProjectRefresh.addEventListener("click", () => { void loadProjectCatalog(); });
elements.boardDeck?.querySelectorAll("[data-board-model]").forEach((button) => {
  button.addEventListener("click", async () => {
    await createBoard(button.dataset.boardModel);
  });
});
elements.form.addEventListener("submit", handleProjectSubmit);
elements.releaseTrainForm?.addEventListener("submit", handleReleaseTrainSubmit);
elements.releaseTrainCancelEdit?.addEventListener("click", resetReleaseTrainEditor);
elements.releaseTrainList?.addEventListener("click", (event) => {
  const button = event.target.closest("[data-release-action]");
  if (!button || !elements.releaseTrainList.contains(button)) return;
  if (button.dataset.releaseAction === "edit") void openReleaseTrainEditor(button.dataset.trainId);
  if (button.dataset.releaseAction === "delete") void deleteReleaseTrain(button.dataset.trainId);
});
elements.addProjectLink?.addEventListener("click", () => {
  resetFormMode();
  openProjectForm();
});
elements.closeProjectFormButton?.addEventListener("click", () => {
  resetFormMode();
  closeProjectForm();
});
elements.cancelEditButton.addEventListener("click", () => {
  resetFormMode();
  closeProjectForm();
});
elements.createTaskForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  await createBoardCard(elements.createTaskColumn.value, new FormData(elements.createTaskForm),
    state.createTaskContext);
});
elements.cancelTaskCreate.addEventListener("click", closeCreateTaskPanel);
elements.taskModalClose.addEventListener("click", closeTaskModal);
elements.taskModal.addEventListener("click", (event) => {
  if (event.target === elements.taskModal) {
    closeTaskModal();
  }
});
elements.taskModalEdit.addEventListener("click", () => {
  const found = findBoardCard(state.selectedBoardCardId);
  if (found) {
    closeTaskModal();
    startEditBoardCard(found.card, found.columnKey);
  }
});
elements.taskModalDelete.addEventListener("click", async () => {
  const found = findBoardCard(state.selectedBoardCardId);
  if (found) {
    await deleteBoardCard(found.card);
  }
});

init().catch((error) => {
  console.error(error);
  setFormState("Ошибка инициализации", true);
  setSummary("Не удалось загрузить данные API. Повторите загрузку.", "error");
});

async function init() {
  showView(state.activeView);
  await Promise.allSettled([loadDeliveryModels(), loadDashboard(), loadReleaseChecks()]);
}

async function loadDeliveryModels() {
  const response = await fetchJson("/api/delivery-models");
  state.deliveryModels = response.items ?? [];
  elements.deliveryModelField.innerHTML = state.deliveryModels
    .map((item) => `<option value="${item}">${item}</option>`)
    .join("");
  resetFormMode();
}

function showView(view, options = {}) {
  if (window.PMPassportEditor?.allowNavigation(() => showView(view, options)) === false) return false;
  const nextView = view || "overview";
  const previousView = state.activeView;
  if (previousView === "boards" && nextView !== "boards") invalidateBoardContext();
  if (previousView === "release" && nextView !== "release") {
    ++state.trainEditorRequest;
    setReleaseTrainEditorLoading(false);
    if (elements.releaseTrainMessage.textContent === "Загружаем релизный поезд...") {
      elements.releaseTrainMessage.textContent = "Выберите поезд для редактирования.";
    }
  }
  state.activeView = nextView;

  elements.viewSections.forEach((section) => {
    section.classList.toggle("active", section.dataset.viewSection === nextView);
  });

  elements.viewButtons.forEach((button) => {
    button.classList.toggle("active", button.dataset.view === nextView);
  });

  window.PMNavigationUI?.onView(nextView);

  window.PMProjectsUI?.onView?.(nextView);
  window.PMArchitecturePortfolioUI?.onView?.(nextView);

  if (nextView === "boards" && previousView !== "boards" && !options.skipBoardReload) {
    if (state.boardDrilledIn && state.selectedProjectId) {
      void loadBoard(state.selectedProjectId, state.activeBoardModel);
    } else {
      renderBoardLanding();
    }
  }

  window.scrollTo({ top: 0, behavior: "smooth" });
  return true;
}

function invalidateBoardContext() {
  ++state.boardRequest;
  state.boardLoading = false;
  state.currentBoard = null;
  state.createTaskContext = null;
  state.draggingBoardCardId = null;
  closeTaskModal();
  closeCreateTaskPanel();
  elements.createTaskButton.disabled = true;
  elements.boardInsights?.classList.add("hidden");
  elements.boardFilters?.classList.add("hidden");
}

function isCurrentBoardRequest(request, projectId, model) {
  return request === state.boardRequest && state.activeView === "boards"
    && state.boardDrilledIn && state.selectedProjectId === projectId
    && state.activeBoardModel === model;
}

function activeBoardContext() {
  const board = state.currentBoard;
  if (!board || state.boardLoading || !isCurrentBoardRequest(state.boardRequest,
      board.projectId, board.deliveryModel)) return null;
  return { request: state.boardRequest, projectId: board.projectId, model: board.deliveryModel };
}

function isCurrentBoardContext(context) {
  return Boolean(context && !state.boardLoading && state.currentBoard
    && isCurrentBoardRequest(context.request, context.projectId, context.model)
    && state.currentBoard.projectId === context.projectId
    && state.currentBoard.deliveryModel === context.model);
}

function openProjectBoard(projectId, model) {
  if (showView("boards", { skipBoardReload: true }) === false) return false;
  state.selectedProjectId = projectId;
  state.activeBoardModel = model;
  state.boardDrilledIn = true;
  state.boardProjectSelectionRequired = false;
  renderBoardProjectPicker();
  return loadBoard(projectId, model);
}

function selectBoardProject(projectId) {
  if (projectId && !state.projects.some((project) => project.id === projectId)
      && state.currentBoard?.projectId !== projectId) return;
  if (projectId) return openProjectBoard(projectId, state.activeBoardModel);
  invalidateBoardContext();
  state.selectedProjectId = null;
  state.boardDrilledIn = false;
  state.boardProjectSelectionRequired = true;
  renderBoardLanding();
}

function renderBoardProjectPicker() {
  const projects = [...state.projects];
  if (state.currentBoard && !projects.some((project) => project.id === state.currentBoard.projectId)) {
    projects.push({ id: state.currentBoard.projectId, name: state.currentBoard.projectName });
  }
  elements.boardProjectSelect.innerHTML = '<option value="">Выберите проект</option>'
    + projects.map((project) => `<option value="${escapeAttr(project.id)}">${escapeHtml(project.name)}</option>`).join("");
  elements.boardProjectSelect.value = state.selectedProjectId ?? "";
  elements.boardProjectSelect.disabled = state.boardCatalogLoading && !projects.length;
  elements.boardProjectRefresh.disabled = state.boardCatalogLoading;
  elements.boardProjectMessage.textContent = state.boardCatalogLoading
    ? "Загружаем список проектов..."
    : state.boardCatalogError
      ? "Не удалось обновить проекты. Повторите загрузку; ранее загруженный список сохранён."
      : !projects.length && state.boardCatalogLoaded
        ? "Проектов пока нет. Создайте проект в реестре."
        : "Выберите проект и модель. Во всех моделях используется один набор задач.";
  elements.boardDeck?.querySelectorAll("[data-board-model]").forEach((button) => {
    button.setAttribute("aria-pressed", String(state.boardDrilledIn && button.dataset.boardModel === state.activeBoardModel));
  });
}

function setBoardMessage(message, isError = false) {
  elements.boardMessage.textContent = message;
  elements.boardMessage.setAttribute("data-tone", isError ? "error" : "success");
}

function onProjectDeleted(projectId) {
  ++state.projectsRequest;
  state.projects = state.projects.filter((project) => project.id !== projectId);
  if (state.selectedProjectId === projectId) {
    invalidateBoardContext();
    state.selectedProjectId = null;
    state.boardDrilledIn = false;
    state.boardProjectSelectionRequired = true;
    renderBoardLanding("Проект удалён. Выберите другой проект в списке выше.");
  } else {
    renderCreatedBoards();
  }
  renderBoardProjectPicker();
  void loadProjectCatalog();
}

async function loadDashboard() {
  await Promise.allSettled([loadOverview(), loadProjectCatalog(), loadTrainSnapshot()]);
}

async function loadProjectCatalog() {
  const request = ++state.projectsRequest;
  state.boardCatalogLoading = true;
  state.boardCatalogError = false;
  renderBoardProjectPicker();
  try {
    const projects = await fetchJson("/api/projects");
    if (request !== state.projectsRequest) return;
    state.projects = projects;
    if (state.selectedProjectId && !projects.some(p => p.id === state.selectedProjectId)
        && state.boardDrilledIn) {
      invalidateBoardContext();
      state.selectedProjectId = null;
      state.boardDrilledIn = false;
      state.boardProjectSelectionRequired = true;
    }
    state.boardCatalogLoading = false;
    state.boardCatalogLoaded = true;
    renderBoardProjectPicker();
    renderProjectDashboards(projects);
    renderCreatedBoards();
    if (!state.boardDrilledIn) renderBoardLanding();
  } catch (error) {
    if (request !== state.projectsRequest) return;
    state.boardCatalogLoading = false;
    state.boardCatalogError = true;
    renderBoardProjectPicker();
    setFormState("Не удалось обновить список проектов. " + error.message, true);
  }
}

async function loadTrainSnapshot() {
  const request = ++state.trainsRequest;
  try {
    const snapshot = await fetchJson("/api/release-trains/snapshot");
    if (request !== state.trainsRequest) return false;
    state.releaseTrainSnapshot = snapshot;
    state.releaseTrains = snapshot.trains ?? [];
    renderReleaseTrains(snapshot);
    return true;
  } catch (error) {
    if (request !== state.trainsRequest) return false;
    if (!state.releaseTrainSnapshot) {
      elements.releaseTrainList.textContent = "Не удалось обновить релизные поезда. Повторите загрузку.";
    }
    return false;
  }
}

async function loadOverview() {
  const request = ++state.overviewRequest;
  const filters = { ...state.filters };
  setSummary(state.overview
    ? "Обновляем… Пока показан предыдущий снимок."
    : "Загружаем портфель…", "quiet");
  document.getElementById("overview-content").setAttribute("aria-busy", "true");
  try {
    const snapshot = await fetchJson("/api/portfolio/overview?" + new URLSearchParams(filters));
    if (request !== state.overviewRequest) return;
    state.overview = snapshot;
    elements.overviewAsOf.textContent = "Состояние портфеля на " + overviewDate(snapshot.calculationDate);
    state.overviewLoadedAt = new Intl.DateTimeFormat("ru-RU", {
      timeZone: "Europe/Moscow", hour: "2-digit", minute: "2-digit", second: "2-digit"
    }).format(new Date());
    populateOverviewFilters(snapshot);
    renderStats(snapshot);
    renderProjectOverview(snapshot);
    renderPortfolioSlice(snapshot);
    setSummary("Данные портфеля обновлены " + state.overviewLoadedAt + ".", "quiet");
    renderOverviewQuality(snapshot);
  } catch (error) {
    if (request !== state.overviewRequest) return;
    setSummary("Не удалось загрузить портфель. Нажмите «Обновить». " +
      (state.overview ? "Показан предыдущий снимок; последнее обновление " +
        state.overviewLoadedAt + ". Текущие фильтры ещё не применены." : "Данные недоступны."),
      state.overview ? "warning" : "error");
  } finally {
    if (request === state.overviewRequest) {
      document.getElementById("overview-content").setAttribute("aria-busy", "false");
    }
  }
}

function populateOverviewFilters(snapshot) {
  const populate = (element, values, selected, allLabel, labels = {}) => {
    const options = [...new Set([...values, ...(selected === "all" ? [] : [selected])])];
    element.replaceChildren(new Option(allLabel, "all"));
    options.forEach(value => element.add(new Option((labels[value] ?? value) || "Не указано", value)));
    element.value = selected;
  };
  populate(elements.quarterFilter, snapshot.quarters, state.filters.quarter, "Все кварталы");
  populate(elements.statusFilter, snapshot.statuses, state.filters.status, "Все статусы", LABELS.status);
  populate(elements.healthFilter, snapshot.healthValues, state.filters.health, "Все состояния", LABELS.health);
}

function renderStats(snapshot) {
  elements.statProjectCount.textContent = formatNumber(snapshot.projectCount);
  elements.statActiveCount.textContent = formatNumber(snapshot.activeCount);
  elements.statRiskyCount.textContent = formatNumber(snapshot.attentionCount);
  document.getElementById("attention-metric-card").classList.toggle("metric-card-alert", snapshot.attentionCount > 0);
  elements.statAverageProgress.textContent = snapshot.averageProgress == null ? "Нет данных" : snapshot.averageProgress + "%";
  document.getElementById("overview-progress-coverage").textContent =
    snapshot.projectCount ? "По " + snapshot.progressCoverage + " из " + snapshot.projectCount + " проектов" : "";
  const healthLabels = { ...LABELS.health, unknown: "Неизвестное состояние" };
  const healthOrder = ["green", "yellow", "red", "unknown"];
  const healthEntries = healthOrder
    .filter(key => snapshot.healthCounts[key])
    .map(key => [key, snapshot.healthCounts[key]]);
  document.getElementById("overview-health-bar").innerHTML = snapshot.projectCount
    ? healthEntries.map(([key, count]) => '<span data-health="' + key + '" style="--share:' +
      (count / snapshot.projectCount * 100) + '%" title="' + escapeHtml(healthLabels[key]) +
      ': ' + count + '"></span>').join("")
    : '<span class="portfolio-health-bar-empty"></span>';
  document.getElementById("overview-health-legend").innerHTML = snapshot.projectCount
    ? healthEntries.map(([key, count]) => '<span><i data-health="' + key + '"></i>' +
      escapeHtml(healthLabels[key]) + '<strong>' + count + '</strong></span>').join("")
    : '<span class="empty-state compact">Нет проектов в выбранном срезе.</span>';

  const statusLabels = { ...LABELS.status, unknown: "Неизвестный статус" };
  const statusOrder = ["active", "planned", "paused", "done", "unknown"];
  document.getElementById("overview-status-counts").innerHTML = snapshot.projectCount
    ? statusOrder.filter(key => snapshot.statusCounts[key]).map(key =>
      '<span>' + escapeHtml(statusLabels[key]) + '<strong>' + snapshot.statusCounts[key] + '</strong></span>').join("")
    : '<span class="empty-state compact">Нет данных о статусах.</span>';
}

function renderProjectDashboards(projects) {
  if (!elements.projectDashboardGrid) {
    return;
  }

  const total = projects.length;
  const totalBudget = projects.reduce((sum, project) => sum + Number(project.budget || 0), 0);
  const averageBudget = total > 0 ? Math.round(totalBudget / total) : 0;
  const averageProgress = total > 0
    ? Math.round(projects.reduce((sum, project) => sum + Number(project.progress || 0), 0) / total)
    : 0;
  const activeCount = projects.filter((project) => project.status === "active").length;
  const greenCount = projects.filter((project) => project.health === "green").length;
  const riskCount = projects.filter((project) => project.health === "yellow" || project.health === "red").length;
  const overdueCount = projects.filter((project) => isProjectOverdue(project)).length;
  const dueSoonCount = projects.filter((project) => {
    const days = daysUntil(project.deadline);
    return project.status !== "done" && days >= 0 && days <= 30;
  }).length;
  const deliveryModels = groupCount(projects, "deliveryModel");
  const statusCounts = groupCount(projects, "status");
  const healthCounts = groupCount(projects, "health");
  const deliveryCount = Object.keys(deliveryModels).length;

  elements.dashboardHealthSummary.textContent = `${greenCount} стабильных / ${riskCount} с риском`;
  elements.dashboardDeliverySummary.textContent = `${deliveryCount} ${declOfNum(deliveryCount, ["модель", "модели", "моделей"])}`;

  if (!projects.length) {
    elements.projectDashboardGrid.innerHTML = '<div class="empty-state compact">Для выбранного среза пока нет проектных данных.</div>';
    return;
  }

  const progressBuckets = {
    start: projects.filter((project) => Number(project.progress || 0) < 30).length,
    middle: projects.filter((project) => Number(project.progress || 0) >= 30 && Number(project.progress || 0) < 70).length,
    finish: projects.filter((project) => Number(project.progress || 0) >= 70).length
  };
  const budgetLeaders = projects
    .slice()
    .sort((a, b) => Number(b.budget || 0) - Number(a.budget || 0))
    .slice(0, 4);
  const deadlineWatch = projects
    .filter((project) => {
      const days = daysUntil(project.deadline);
      return project.status !== "done" && Number.isFinite(days) && days >= 0;
    })
    .sort((a, b) => a.deadline.localeCompare(b.deadline))
    .slice(0, 4);
  const riskWatch = projects
    .filter((project) => project.health !== "green" || milestoneTone(project) !== "green")
    .sort((a, b) => riskWeight(b) - riskWeight(a))
    .slice(0, 4);

  elements.projectDashboardGrid.innerHTML = `
    <div class="dashboard-kpi-strip dashboard-card-wide">
      ${renderDashboardKpi("Всего проектов", formatNumber(total), `${activeCount} активных`)}
      ${renderDashboardKpi("Бюджет", formatCurrency(totalBudget), `Средний: ${formatCurrency(averageBudget)}`)}
      ${renderDashboardKpi("Средний прогресс", `${averageProgress}%`, progressComment(averageProgress))}
      ${renderDashboardKpi("Риски", formatNumber(riskCount), "По состоянию проектов")}
    </div>
    <article class="dashboard-card">
      <div class="dashboard-card-head">
        <span class="metric-label">Состояние</span>
        <strong>${greenCount}/${total} стабильных</strong>
      </div>
      ${renderDistributionBars(healthCounts, total, ["green", "yellow", "red"])}
    </article>
    <article class="dashboard-card">
      <div class="dashboard-card-head">
        <span class="metric-label">Модели поставки</span>
        <strong>${deliveryCount} модели</strong>
      </div>
      ${renderDistributionBars(deliveryModels, total, ["kanban", "scrum", "waterfall"])}
    </article>
    <article class="dashboard-card">
      <div class="dashboard-card-head">
        <span class="metric-label">Крупные бюджеты</span>
        <strong>${formatCurrency(totalBudget)}</strong>
      </div>
      ${renderProjectRows(budgetLeaders, (project) => formatCurrency(project.budget), totalBudget)}
    </article>
    <article class="dashboard-card">
      <div class="dashboard-card-head">
        <span class="metric-label">Статусы</span>
        <strong>${activeCount} активных</strong>
      </div>
      ${renderDistributionBars(statusCounts, total, ["active", "planned", "paused", "done"])}
    </article>
    <article class="dashboard-card">
      <div class="dashboard-card-head">
        <span class="metric-label">Прогресс</span>
        <strong>${averageProgress}% средний</strong>
      </div>
      ${renderDistributionBars(progressBuckets, total, ["start", "middle", "finish"], {
        start: "0-29%",
        middle: "30-69%",
        finish: "70-100%"
      })}
    </article>
    <article class="dashboard-card">
      <div class="dashboard-card-head">
        <span class="metric-label">Сроки</span>
        <strong>${dueSoonCount} в 30 дней</strong>
      </div>
      <div class="dashboard-timeline-summary">
        <div data-tone="${overdueCount ? "red" : "green"}">
          <span>Просрочено</span>
          <strong>${overdueCount}</strong>
        </div>
        <div data-tone="${dueSoonCount ? "yellow" : "green"}">
          <span>Скоро дедлайн</span>
          <strong>${dueSoonCount}</strong>
        </div>
      </div>
    </article>
    <article class="dashboard-card dashboard-card-wide">
      <div class="dashboard-card-head">
        <span class="metric-label">Ближайшие сроки</span>
        <strong>${deadlineWatch.length} ближайших</strong>
      </div>
      ${renderDeadlineRows(deadlineWatch)}
    </article>
    <article class="dashboard-card dashboard-card-wide">
      <div class="dashboard-card-head">
        <span class="metric-label">Контроль рисков</span>
        <strong>${riskWatch.length} под контролем</strong>
      </div>
      ${riskWatch.length ? renderRiskRows(riskWatch) : '<div class="empty-state compact">Критичных рисков в выбранном срезе нет.</div>'}
    </article>
  `;
}

function renderDashboardKpi(label, value, caption) {
  return `
    <article class="dashboard-kpi-card">
      <span>${escapeHtml(label)}</span>
      <strong>${escapeHtml(value)}</strong>
      <small>${escapeHtml(caption)}</small>
    </article>
  `;
}

function renderDistributionBars(counts, total, order, labels = {}) {
  return `
    <div class="dashboard-bars">
      ${order
        .filter((key) => counts[key])
        .map((key) => {
          const count = counts[key];
          const percent = total > 0 ? Math.round((count / total) * 100) : 0;
          return `
            <div class="dashboard-bar-row">
              <span>${escapeHtml(labels[key] ?? displayLabel(key))}</span>
              <div class="dashboard-bar"><span style="width: ${percent}%"></span></div>
              <strong>${count}</strong>
            </div>
          `;
        })
        .join("")}
    </div>
  `;
}

function renderProjectRows(projects, valueLabel, totalBudget) {
  return `
    <div class="dashboard-project-rows">
      ${projects
        .map((project) => {
          const percent = totalBudget > 0 ? Math.round((Number(project.budget || 0) / totalBudget) * 100) : Number(project.progress || 0);
          return `
            <div class="dashboard-project-row">
              <div>
                <strong>${escapeHtml(project.name)}</strong>
                <small>${escapeHtml(project.owner)} / ${escapeHtml(project.quarter)}</small>
              </div>
              <span>${valueLabel(project)}</span>
              <div class="dashboard-bar"><span style="width: ${percent}%"></span></div>
            </div>
          `;
        })
        .join("")}
    </div>
  `;
}

function renderDeadlineRows(projects) {
  return `
    <div class="dashboard-project-rows">
      ${projects
        .map((project) => `
          <div class="deadline-row" data-tone="${deadlineTone(project)}">
            <div>
              <strong>${escapeHtml(project.name)}</strong>
              <small>${escapeHtml(project.milestone)}</small>
            </div>
            <span>${formatDate(project.deadline)}</span>
          </div>
        `)
        .join("")}
    </div>
  `;
}

function renderRiskRows(projects) {
  return `
    <div class="dashboard-project-rows">
      ${projects
        .map((project) => `
          <div class="risk-row" data-tone="${milestoneTone(project)}">
            <div>
              <strong>${escapeHtml(project.name)}</strong>
              <small>${escapeHtml(project.risk)}</small>
            </div>
            <span>${escapeHtml(displayLabel(project.health))}</span>
          </div>
        `)
        .join("")}
    </div>
  `;
}

function overviewProjectLink(project) {
  return '<button type="button" class="text-link overview-project-link" data-project-id="' +
    escapeHtml(project.id) + '">' + escapeHtml(project.name) + '</button>';
}

function overviewDate(value) {
  // Date-only values are formatted without Date or the browser's time zone.
  return /^\d{4}-\d{2}-\d{2}$/.test(value ?? "") ? value.split("-").reverse().join(".") : (value || "Не указан");
}

function bindOverviewLinks(container) {
  container.querySelectorAll(".overview-project-link").forEach(link =>
    link.addEventListener("click", () => openProjectFromOverview(link.dataset.projectId)));
}

function overviewEmpty(snapshot, otherwise) {
  if (!snapshot.totalProjectCount) return "Проектов пока нет. Создайте проект в разделе «Проекты».";
  if (!snapshot.projectCount) return "Нет проектов по выбранным фильтрам. Нажмите «Сбросить».";
  return otherwise;
}

function renderProjectOverview(snapshot) {
  elements.overviewProblemCount.textContent = snapshot.attentionCount + " требуют внимания";
  elements.overviewProjectList.innerHTML = snapshot.attentionProjects.length
    ? snapshot.attentionProjects.map(project => '<article class="overview-entry overview-project-compact">' +
      '<div class="overview-entry-main">' + overviewProjectLink(project) + '<span>' +
      escapeHtml(project.owner) + " · " + escapeHtml(displayLabel(project.status)) + '</span></div>' +
      '<p class="overview-entry-primary">' + project.attentionReasons.map(escapeHtml).join(" · ") + '</p>' +
      '<p class="overview-entry-secondary">Срок: ' + escapeHtml(overviewDate(project.deadline)) +
      (project.risk ? " · " + escapeHtml(project.risk) : "") + '</p></article>').join("")
    : '<p class="empty-state compact">' + overviewEmpty(snapshot,
      "По выбранным критериям отклонений не выявлено." +
      (snapshot.dataQualityProjects.length ? " Проверьте качество данных рядом." : "")) + "</p>";
  bindOverviewLinks(elements.overviewProjectList);
}

function renderPortfolioSlice(snapshot) {
  elements.sliceMilestonesOnTrack.textContent = snapshot.dueSoonCount + " в ближайшие 14 дней";
  elements.portfolioSliceTable.innerHTML = snapshot.milestones.length
    ? snapshot.milestones.map(project => '<article class="overview-entry overview-project-compact">' +
      '<div class="overview-entry-main">' + overviewProjectLink(project) + '<span>' +
      escapeHtml(displayLabel(project.status)) + '</span></div>' +
      '<p class="overview-entry-primary">' + escapeHtml(project.milestone || "Контрольная точка не указана") + '</p>' +
      '<p class="overview-entry-secondary">' + escapeHtml(overviewDate(project.deadline)) + " · " +
      (project.daysUntilDeadline === 0 ? "Сегодня" : "Через " + project.daysUntilDeadline + " дн.") +
      '</p></article>').join("")
    : '<p class="empty-state compact">' + overviewEmpty(snapshot, "В ближайшие 14 дней сроков проектов нет.") + "</p>";
  bindOverviewLinks(elements.portfolioSliceTable);
}

function renderOverviewQuality(snapshot) {
  elements.overviewDataQuality.innerHTML = snapshot.dataQualityProjects.length
    ? "<div class=\"section-head\"><h2>Качество данных</h2><span class=\"pill warning\">" +
      snapshot.dataQualityProjects.length + "</span></div>" + snapshot.dataQualityProjects.map(project =>
      '<article class="overview-entry overview-quality-entry">' + overviewProjectLink(project) + "<ul>" +
      project.dataQualityIssues.map(issue => "<li>" + escapeHtml(issue) + "</li>").join("") + "</ul></article>").join("")
    : '<h2>Качество данных</h2><p class="empty-state compact">Неполных или противоречивых данных не обнаружено.</p>';
  bindOverviewLinks(elements.overviewDataQuality);
}

async function openProjectFromOverview(projectId) {
  await window.PMProjectsUI.openCard(projectId, "overview");
}

function renderReleaseTrains(snapshot) {
  const trains = snapshot?.trains ?? [];
  const capacity = Number(snapshot?.totalCapacityPoints || 0);
  const committed = Number(snapshot?.totalCommittedPoints || 0);

  elements.releaseTrainCount.textContent = `${trains.length} ${declOfNum(trains.length, ["поезд", "поезда", "поездов"])}`;
  elements.releaseTrainReadiness.textContent = `${snapshot?.averageReadiness ?? 0}% готовность`;
  elements.releaseTrainBlocked.textContent = `${snapshot?.blockedItems ?? 0} блокеров`;
  elements.releaseTrainCapacity.textContent = `${committed} / ${capacity} SP`;

  if (!trains.length) {
    elements.releaseTrainList.innerHTML = '<div class="empty-state compact">Для выбранного среза релизных поездов пока нет.</div>';
    return;
  }

  elements.releaseTrainList.innerHTML = trains
    .map((train) => {
      const capacityPercent = capacityPercentFor(train);
      return `
        <article class="release-train-card" data-status="${escapeAttr(train.status)}">
          <div class="release-train-top">
            <div>
              <p class="project-meta">${escapeHtml(train.quarter)} / ${escapeHtml(train.cadence)}</p>
              <h3>${escapeHtml(train.name)}</h3>
            </div>
            <span class="pill ${releaseTrainTone(train)}">${escapeHtml(displayLabel(train.status))}</span>
          </div>
          <div class="release-track" aria-label="Этапы релизного потока">
            ${renderReleaseStage("Заморозка кода", train.codeFreezeDate, train.status, ["planning", "boarding"])}
            ${renderReleaseStage("Заморозка тестирования", train.qaFreezeDate, train.status, ["frozen"])}
            ${renderReleaseStage("Запуск", train.goLiveDate, train.status, ["qa", "released"])}
          </div>
          <div class="release-train-progress">
            <div>
              <span>Готовность</span>
              <strong>${train.readiness}%</strong>
            </div>
            <div class="progress-bar"><span style="width: ${Number(train.readiness || 0)}%"></span></div>
          </div>
          <dl class="release-train-details">
            <div><dt>Состав</dt><dd>${escapeHtml(train.scope)}</dd></div>
            <div><dt>Емкость</dt><dd>${train.committedPoints} / ${train.capacityPoints} SP (${capacityPercent}%)</dd></div>
            <div><dt>Блокеры</dt><dd>${train.blockedItems}</dd></div>
            <div><dt>Дата релиза</dt><dd>${formatDate(train.plannedReleaseDate)}</dd></div>
          </dl>
          <div class="release-train-note">
            <strong>Риск</strong>
            <p>${escapeHtml(train.risk)}</p>
          </div>
          <div class="release-train-note">
            <strong>Решение до заморозки</strong>
            <p>${escapeHtml(train.decision)}</p>
          </div>
          <div class="release-train-actions">
            <button class="btn btn-secondary btn-small" type="button" data-release-action="edit" data-train-id="${escapeAttr(train.id)}" ${state.trainMutationPending ? "disabled" : ""}>Редактировать</button>
            <button class="btn btn-ghost btn-small" type="button" data-release-action="delete" data-train-id="${escapeAttr(train.id)}" ${state.trainMutationPending || state.trainEditorLoading ? "disabled" : ""}>Удалить</button>
          </div>
        </article>
      `;
    })
    .join("");
}

function applyReleaseTrainChange(train, deletedId) {
  ++state.trainsRequest;
  const trains = state.releaseTrains.filter((item) => item.id !== (deletedId ?? train.id));
  if (train) trains.push(train);
  state.releaseTrains = trains;
  const snapshot = {
    trains,
    totalCapacityPoints: trains.reduce((sum, item) => sum + item.capacityPoints, 0),
    totalCommittedPoints: trains.reduce((sum, item) => sum + item.committedPoints, 0),
    blockedItems: trains.reduce((sum, item) => sum + item.blockedItems, 0),
    averageReadiness: trains.length
      ? Math.round(trains.reduce((sum, item) => sum + item.readiness, 0) / trains.length) : 0
  };
  state.releaseTrainSnapshot = snapshot;
  renderReleaseTrains(snapshot);
}

function renderReleaseStage(title, date, status, activeStatuses) {
  const active = activeStatuses.includes(status);
  return `
    <div class="release-stage ${active ? "active" : ""}">
      <span>${escapeHtml(title)}</span>
      <strong>${formatDate(date)}</strong>
    </div>
  `;
}

async function loadBoard(projectId, boardModel = state.activeBoardModel) {
  const request = ++state.boardRequest;
  state.activeBoardModel = boardModel;
  state.boardLoading = true;
  state.currentBoard = null;
  setBoardMessage("");
  state.createTaskContext = null;
  closeTaskModal();
  closeCreateTaskPanel();
  elements.createTaskButton.disabled = true;
  elements.boardInsights?.classList.add("hidden");
  elements.boardFilters?.classList.add("hidden");
  elements.boardTitle.textContent = "Загружаем доску...";
  elements.boardModel.textContent = boardModel;
  elements.boardColumns.innerHTML = '<div class="empty-state compact">Загружаем задачи проекта...</div>';
  try {
    const board = await fetchJson(`/api/projects/${encodeURIComponent(projectId)}/board?model=${encodeURIComponent(boardModel)}`);
    if (!isCurrentBoardRequest(request, projectId, boardModel)) return;
    if (board.projectId !== projectId || board.deliveryModel !== boardModel) {
      throw new Error("Ответ доски не соответствует выбранному проекту и модели");
    }
    state.boardLoading = false;
    state.currentBoard = board;
    rememberBoardVisit(board);
    renderBoardProjectPicker();
    elements.boardTitle.textContent = `${board.projectName} - ${board.boardTitle}`;
    elements.boardModel.textContent = board.deliveryModel;
    elements.boardDeck?.classList.add("compact");
    if (!board.columns?.length) {
      renderEmptyBoard();
      return;
    }
    elements.createTaskButton.disabled = false;
    elements.boardInsights?.classList.remove("hidden");
    elements.boardFilters?.classList.remove("hidden");
    renderBoardInsights();
    renderBoardColumns();
    if (![...board.columns, ...(board.unassignedColumns ?? [])].some((column) => column.cards?.length)) {
      setBoardMessage("На доске пока нет задач. Создайте первую задачу.");
    }
  } catch (error) {
    if (!isCurrentBoardRequest(request, projectId, boardModel)) return;
    state.boardLoading = false;
    state.currentBoard = null;
    if (error.status === 404) {
      state.selectedProjectId = null;
      state.boardDrilledIn = false;
      state.boardProjectSelectionRequired = true;
      renderBoardLanding("Проект не найден. Выберите другой проект в списке выше.");
      return;
    }
    elements.boardColumns.innerHTML = '<div class="empty-state compact">Не удалось открыть доску. ' +
      '<button class="text-link" type="button" data-action="retry-board">Повторить</button></div>';
    elements.boardColumns.querySelector('[data-action="retry-board"]')?.addEventListener("click", () => {
      if (isCurrentBoardRequest(request, projectId, boardModel)) void loadBoard(projectId, boardModel);
    });
  }
}

function renderBoardColumns() {
  const board = state.currentBoard;
  const renderedContext = activeBoardContext();
  if (!board?.columns?.length || !renderedContext) {
    return;
  }
  elements.boardColumns.innerHTML = "";

  board.columns.forEach((column) => {
    const columnElement = document.createElement("section");
    const wipExceeded = column.wipLimit && (column.cards?.length ?? 0) > column.wipLimit;
    columnElement.className = `board-column ${wipExceeded ? "wip-exceeded" : ""}`;
    columnElement.dataset.columnKey = column.key;
    const visibleCards = (column.cards ?? []).filter(matchesBoardFilters);
    const cards = visibleCards
      .map((card) => renderBoardCard(card, column.key))
      .join("");

    columnElement.innerHTML = `
      <header>
        <h3>${escapeHtml(column.title)}</h3>
        <div class="board-column-actions">
          <span class="pill ${wipExceeded ? "danger" : ""}">${visibleCards.length}${visibleCards.length !== (column.cards?.length ?? 0) ? ` из ${column.cards?.length ?? 0}` : ""}${column.wipLimit ? ` / ${column.wipLimit}` : ""}</span>
          <button class="btn btn-ghost btn-small" type="button" data-action="new-card" data-column-key="${escapeAttr(column.key)}">Новая задача</button>
        </div>
      </header>
      ${cards || '<div class="empty-state compact">В этой колонке пока нет задач.</div>'}
    `;
    elements.boardColumns.appendChild(columnElement);
  });

  const unassigned = board.unassignedColumns ?? [];
  if (unassigned.length) {
    const section = document.createElement("section");
    section.className = "panel board-unassigned";
    const count = unassigned.reduce((sum, column) => sum + column.cards.length, 0);
    section.innerHTML = `<h3>Несопоставленные задачи: ${count}</h3>
      <p>Сохранённые статусы не входят в эту модель. Выберите колонку вручную; автоматический перенос отключён.</p>
      ${unassigned.map((column) => `<section class="board-unassigned-group"><h4>Сохранённая колонка: ${escapeHtml(column.key)}</h4>
        ${column.cards.filter(matchesBoardFilters).map((card) => renderBoardCard(card, column.key)).join("")
          || '<p class="hint-text">Нет задач по выбранным фильтрам.</p>'}</section>`).join("")}`;
    elements.boardColumns.appendChild(section);
  }

  elements.boardColumns.querySelectorAll('[data-action="edit-card"]').forEach((button) => {
    button.addEventListener("click", () => {
      if (!isCurrentBoardContext(renderedContext)) return;
      const card = findBoardCard(button.dataset.cardId);
      if (card) {
        startEditBoardCard(card.card, card.columnKey);
      }
    });
  });

  elements.boardColumns.querySelectorAll('[data-action="open-card"]').forEach((button) => {
    button.addEventListener("click", () => {
      if (!isCurrentBoardContext(renderedContext)) return;
      const card = findBoardCard(button.dataset.cardId);
      if (card) {
        openTaskModal(card.card, card.columnTitle);
      }
    });
  });

  elements.boardColumns.querySelectorAll('[data-action="new-card"]').forEach((button) => {
    button.addEventListener("click", () => {
      if (!isCurrentBoardContext(renderedContext)) return;
      startCreateBoardCard(button.dataset.columnKey);
    });
  });

  elements.boardColumns.querySelectorAll('[data-action="move-card"]').forEach((select) => {
    const found = findBoardCard(select.dataset.cardId);
    const mapped = state.currentBoard.columns.some((column) => column.key === found?.columnKey);
    select.innerHTML = (mapped ? "" : '<option value="" disabled selected>Выберите колонку для переноса</option>')
      + (state.currentBoard?.columns ?? [])
      .map((column) => `<option value="${escapeAttr(column.key)}">${escapeHtml(column.title)}</option>`)
      .join("");
    if (found) {
      select.value = mapped ? found.columnKey : "";
    }
    select.addEventListener("change", async () => {
      if (isCurrentBoardContext(renderedContext)) {
        await moveBoardCard(select.dataset.cardId, select.value, renderedContext);
      }
    });
  });

  elements.boardColumns.querySelectorAll('[data-action="move-prev"], [data-action="move-next"]').forEach((button) => {
    button.addEventListener("click", async () => {
      if (!isCurrentBoardContext(renderedContext)) return;
      const offset = button.dataset.action === "move-prev" ? -1 : 1;
      const nextColumnKey = adjacentColumnKey(button.dataset.columnKey, offset);
      if (nextColumnKey) {
        await moveBoardCard(button.dataset.cardId, nextColumnKey, renderedContext);
      }
    });
  });

  elements.boardColumns.querySelectorAll(".board-card[draggable='true']").forEach((cardElement) => {
    cardElement.addEventListener("dragstart", (event) => {
      if (!isCurrentBoardContext(renderedContext)) { event.preventDefault(); return; }
      state.draggingBoardCardId = cardElement.dataset.cardId;
      cardElement.classList.add("dragging");
      event.dataTransfer.effectAllowed = "move";
      event.dataTransfer.setData("text/plain", cardElement.dataset.cardId);
    });
    cardElement.addEventListener("dragend", () => {
      state.draggingBoardCardId = null;
      cardElement.classList.remove("dragging");
      elements.boardColumns.querySelectorAll(".board-column.drag-over").forEach((column) => {
        column.classList.remove("drag-over");
      });
    });
  });

  elements.boardColumns.querySelectorAll(".board-column").forEach((columnElement) => {
    columnElement.addEventListener("dragover", (event) => {
      if (!isCurrentBoardContext(renderedContext)) return;
      event.preventDefault();
      columnElement.classList.add("drag-over");
      event.dataTransfer.dropEffect = "move";
    });
    columnElement.addEventListener("dragleave", (event) => {
      if (!columnElement.contains(event.relatedTarget)) {
        columnElement.classList.remove("drag-over");
      }
    });
    columnElement.addEventListener("drop", async (event) => {
      event.preventDefault();
      if (!isCurrentBoardContext(renderedContext)) return;
      columnElement.classList.remove("drag-over");
      const cardId = event.dataTransfer.getData("text/plain") || state.draggingBoardCardId;
      if (cardId) {
        await moveBoardCard(cardId, columnElement.dataset.columnKey, renderedContext);
      }
    });
  });
}

function matchesBoardFilters(card) {
  const haystack = `${card.title} ${card.description} ${card.owner} ${card.labels ?? ""}`.toLowerCase();
  return (!state.boardFilters.query || haystack.includes(state.boardFilters.query))
    && (state.boardFilters.priority === "all" || card.priority === state.boardFilters.priority)
    && (!state.boardFilters.blocked || card.blocked);
}

function renderBoardInsights() {
  const columns = [...(state.currentBoard?.columns ?? []), ...(state.currentBoard?.unassignedColumns ?? [])];
  const cards = columns.flatMap((column) => column.cards ?? []);
  elements.boardTaskCount.textContent = cards.length;
  elements.boardPointCount.textContent = cards.reduce((sum, card) => sum + Number(card.estimate || 0), 0);
  elements.boardOverdueCount.textContent = columns
    .reduce((count, column) => count + (column.cards ?? [])
      .filter((card) => isTaskOverdue(card, column.key, state.currentBoard.deliveryModel)).length, 0);
  elements.boardBlockedCount.textContent = cards.filter((card) => card.blocked).length;
}

function renderEmptyBoard() {
  state.currentBoard = null;
  elements.boardTitle.textContent = "Выберите проект";
  elements.boardModel.textContent = "board";
  if (elements.createTaskButton) {
    elements.createTaskButton.disabled = true;
  }
  elements.boardInsights?.classList.add("hidden");
  elements.boardFilters?.classList.add("hidden");
  closeCreateTaskPanel();
  elements.boardColumns.innerHTML = '<div class="empty-state compact">Доска появится после выбора инициативы.</div>';
}

function renderBoardLanding(message) {
  state.currentBoard = null;
  setBoardMessage("");
  const project = state.projects.find((item) => item.id === state.selectedProjectId);
  elements.boardTitle.textContent = project ? `Раздел досок: ${project.name}` : "Раздел досок";
  elements.boardModel.textContent = project?.deliveryModel ?? "board";
  elements.boardDeck?.classList.remove("compact");
  if (elements.createTaskButton) {
    elements.createTaskButton.disabled = true;
  }
  elements.boardInsights?.classList.add("hidden");
  elements.boardFilters?.classList.add("hidden");
  closeCreateTaskPanel();
  renderCreatedBoards();
  renderBoardProjectPicker();
  elements.boardColumns.innerHTML = message
    ? `<div class="empty-state compact">${escapeHtml(message)} <button class="text-link" type="button" data-action="choose-board-project">Открыть проекты</button></div>`
    : project
    ? '<div class="empty-state compact">Выберите Kanban, Scrum или Waterfall выше, чтобы открыть внутреннюю доску инициативы.</div>'
    : '<div class="empty-state compact">Выберите проект в списке выше, затем откройте Kanban, Scrum или Waterfall. <button class="text-link" type="button" data-action="choose-board-project">Открыть проекты</button></div>';
  elements.boardColumns.querySelector('[data-action="choose-board-project"]')?.addEventListener("click", () => showView("projects"));
}

async function createBoard(boardModel) {
  const projectId = state.selectedProjectId;
  if (!projectId) {
    renderBoardLanding();
    setBoardMessage("Сначала выберите проект в списке выше", true);
    return;
  }

  await openProjectBoard(projectId, boardModel);
}

function rememberBoardVisit(board) {
  const entry = { id: `${board.projectId}-${board.deliveryModel}`, projectId: board.projectId,
    model: board.deliveryModel, createdAt: new Date().toISOString() };
  state.createdBoards = [entry, ...state.createdBoards.filter((item) => item.id !== entry.id)].slice(0, 12);
  saveCreatedBoards();
  renderCreatedBoards();
}

async function openCreatedBoard(boardId) {
  const board = state.createdBoards.find((item) => item.id === boardId);
  if (!board || !state.projects.some((project) => project.id === board.projectId)) {
    renderCreatedBoards();
    return;
  }

  renderCreatedBoards();
  await openProjectBoard(board.projectId, board.model);
}

function renderCreatedBoards() {
  if (!elements.createdBoardList) {
    return;
  }

  const visibleBoards = state.createdBoards.filter((board) =>
    state.projects.some((project) => project.id === board.projectId)
  );

  if (!visibleBoards.length) {
    elements.createdBoardList.innerHTML = '<div class="empty-state compact">История необязательна. Выберите проект и модель выше.</div>';
    return;
  }

  elements.createdBoardList.innerHTML = visibleBoards
    .map((board) => `
      <button class="created-board-card" type="button" data-board-id="${escapeAttr(board.id)}">
        <span>${escapeHtml(boardTitleLabel(board.model))}</span>
        <strong>${escapeHtml(state.projects.find((project) => project.id === board.projectId)?.name ?? "Проект")}</strong>
        <small>${formatDate(board.createdAt)}</small>
      </button>
    `)
    .join("");

  elements.createdBoardList.querySelectorAll("[data-board-id]").forEach((button) => {
    button.addEventListener("click", async () => {
      await openCreatedBoard(button.dataset.boardId);
    });
  });
}

function renderBoardCard(card, columnKey) {
  const hasPreviousColumn = Boolean(adjacentColumnKey(columnKey, -1));
  const hasNextColumn = Boolean(adjacentColumnKey(columnKey, 1));
  const overdue = isTaskOverdue(card, columnKey, state.currentBoard?.deliveryModel);
  const labels = String(card.labels ?? "").split(",").map((label) => label.trim()).filter(Boolean);
  return `
    <article class="board-card ${card.blocked ? "is-blocked" : ""}" data-card-id="${escapeAttr(card.id)}" draggable="true">
      <div class="board-card-badges">
        <span class="priority-badge" data-priority="${escapeAttr(card.priority)}">${escapeHtml(card.priority)}</span>
        ${card.blocked ? '<span class="status-badge blocked">Блокер</span>' : ""}
        ${overdue ? '<span class="status-badge overdue">Просрочено</span>' : ""}
        <span class="status-badge estimate">${Number(card.estimate || 0)} SP</span>
      </div>
      <div class="board-card-head">
        <strong>${escapeHtml(card.title)}</strong>
        <button class="btn btn-secondary btn-small" type="button" data-action="open-card" data-card-id="${escapeAttr(card.id)}">Открыть</button>
      </div>
      <p>${escapeHtml(card.description)}</p>
      ${labels.length ? `<div class="board-card-labels">${labels.map((label) => `<span>${escapeHtml(label)}</span>`).join("")}</div>` : ""}
      <p>${escapeHtml(card.owner)} - ${formatDate(card.dueDate)}</p>
      <div class="board-card-actions">
        <select class="board-card-status" data-action="move-card" data-card-id="${escapeAttr(card.id)}" aria-label="Перенести задачу ${escapeAttr(card.title)}"></select>
        <div class="board-card-move-actions" aria-label="Быстрый перенос задачи">
          <button class="btn btn-ghost btn-small" type="button" data-action="move-prev" data-card-id="${escapeAttr(card.id)}" data-column-key="${escapeAttr(columnKey)}" aria-label="Перенести в предыдущий статус" title="Перенести в предыдущий статус" ${hasPreviousColumn ? "" : "disabled"}>&larr;</button>
          <button class="btn btn-ghost btn-small" type="button" data-action="move-next" data-card-id="${escapeAttr(card.id)}" data-column-key="${escapeAttr(columnKey)}" aria-label="Перенести в следующий статус" title="Перенести в следующий статус" ${hasNextColumn ? "" : "disabled"}>&rarr;</button>
        </div>
        <button class="btn btn-ghost btn-small" type="button" data-action="edit-card" data-card-id="${escapeAttr(card.id)}">Редактировать</button>
      </div>
    </article>
  `;
}

function adjacentColumnKey(columnKey, offset) {
  const columns = state.currentBoard?.columns ?? [];
  const index = columns.findIndex((column) => column.key === columnKey);
  if (index < 0) return null;
  return columns[index + offset]?.key ?? null;
}

async function moveBoardCard(cardId, columnKey, expectedContext = activeBoardContext()) {
  if (!isCurrentBoardContext(expectedContext)
      || !state.currentBoard.columns.some((column) => column.key === columnKey)) return;
  const found = findBoardCard(cardId);
  if (!found || found.columnKey === columnKey) {
    return;
  }

  const payload = {
    title: found.card.title,
    description: found.card.description,
    owner: found.card.owner,
    dueDate: found.card.dueDate,
    columnKey,
    priority: found.card.priority,
    labels: found.card.labels,
    estimate: Number(found.card.estimate || 0),
    blocked: Boolean(found.card.blocked)
  };
  setBoardMessage("Переносим задачу...", false);

  try {
    await fetchJson(boardCardUrl(cardId, expectedContext), {
      method: "PUT",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify(payload)
    });
    if (!isCurrentBoardContext(expectedContext)) return;
    setBoardMessage(`Задача "${found.card.title}" перенесена`, false);
    await loadBoard(expectedContext.projectId, expectedContext.model);
  } catch (error) {
    if (!isCurrentBoardContext(expectedContext)) return;
    console.error(error);
    setBoardMessage(error.message || "Не удалось перенести задачу", true);
    renderBoardColumns();
  }
}

function findBoardCard(cardId) {
  for (const column of [...(state.currentBoard?.columns ?? []), ...(state.currentBoard?.unassignedColumns ?? [])]) {
    const card = (column.cards ?? []).find((item) => item.id === cardId);
    if (card) {
      return { card, columnKey: column.key, columnTitle: column.title };
    }
  }
  return null;
}

function openTaskModal(card, columnTitle) {
  state.selectedBoardCardId = card.id;
  elements.taskModalColumn.textContent = columnTitle;
  elements.taskModalTitle.textContent = card.title;
  elements.taskModalDescription.textContent = card.description;
  elements.taskModalOwner.textContent = card.owner;
  elements.taskModalDueDate.textContent = formatDate(card.dueDate);
  elements.taskModalPriority.textContent = card.priority;
  elements.taskModalEstimate.textContent = `${card.estimate ?? 0} SP`;
  elements.taskModalLabels.textContent = card.labels || "Без тегов";
  elements.taskModalBlocked.textContent = card.blocked ? "Заблокирована" : "В работе";
  elements.taskModal.classList.remove("hidden");
}

function closeTaskModal() {
  state.selectedBoardCardId = null;
  elements.taskModal.classList.add("hidden");
}

function closeCreateTaskPanel() {
  state.createTaskContext = null;
  elements.createTaskPanel.classList.add("hidden");
  elements.createTaskForm.reset();
}

function startCreateBoardCard(columnKey) {
  const context = activeBoardContext();
  if (!context || !state.currentBoard.columns?.length) {
    return;
  }
  const project = state.projects.find((item) => item.id === state.currentBoard?.projectId);
  elements.createTaskColumn.innerHTML = state.currentBoard.columns
    .map((column) => `<option value="${escapeAttr(column.key)}">${escapeHtml(column.title)}</option>`)
    .join("");
  elements.createTaskColumn.value = columnKey || state.currentBoard.columns[0].key;
  elements.createTaskOwner.value = project?.owner ?? "";
  elements.createTaskDueDate.value = project?.deadline ?? "";
  state.createTaskContext = context;
  elements.createTaskPanel.classList.remove("hidden");
  elements.createTaskPanel.scrollIntoView({ behavior: "smooth", block: "nearest" });
  elements.createTaskTitle.focus();
}

function startEditBoardCard(card, columnKey) {
  const context = activeBoardContext();
  if (!context || findBoardCard(card.id)?.columnKey !== columnKey) return;
  const cardElement = elements.boardColumns.querySelector(`[data-card-id="${cssEscape(card.id)}"]`);
  if (!cardElement) {
    return;
  }
  const mapped = state.currentBoard.columns.some((column) => column.key === columnKey);
  const columnOptions = (mapped ? "" : '<option value="" selected disabled>Выберите колонку для переноса</option>')
    + (state.currentBoard?.columns ?? [])
    .map((column) => `<option value="${escapeAttr(column.key)}" ${column.key === columnKey ? "selected" : ""}>${escapeHtml(column.title)}</option>`)
    .join("");

  cardElement.innerHTML = `
    <form class="board-card-form" data-card-form="${escapeAttr(card.id)}">
      <label>
        <span>Колонка</span>
        <select name="columnKey" required>${columnOptions}</select>
      </label>
      <label>
        <span>Название</span>
        <input name="title" value="${escapeAttr(card.title)}" required />
      </label>
      <label>
        <span>Описание</span>
        <textarea name="description" rows="3" required>${escapeHtml(card.description)}</textarea>
      </label>
      <label>
        <span>Owner</span>
        <input name="owner" value="${escapeAttr(card.owner)}" required />
      </label>
      <label>
        <span>Due date</span>
        <input name="dueDate" type="date" value="${escapeAttr(card.dueDate)}" required />
      </label>
      <label>
        <span>Приоритет</span>
        <select name="priority" required>
          ${["critical", "high", "medium", "low"].map((priority) => `<option value="${priority}" ${card.priority === priority ? "selected" : ""}>${priority}</option>`).join("")}
        </select>
      </label>
      <label>
        <span>Story points</span>
        <input name="estimate" type="number" min="0" max="100" value="${Number(card.estimate || 0)}" required />
      </label>
      <label>
        <span>Теги</span>
        <input name="labels" value="${escapeAttr(card.labels ?? "")}" placeholder="frontend, discovery, api" />
      </label>
      <label class="check-field">
        <input name="blocked" type="checkbox" ${card.blocked ? "checked" : ""} />
        <span>Задача заблокирована</span>
      </label>
      <div class="board-card-actions">
        <button class="btn btn-primary btn-small" type="submit">Сохранить</button>
        <button class="btn btn-ghost btn-small" type="button" data-action="cancel-card-edit">Отмена</button>
      </div>
    </form>
  `;

  const form = cardElement.querySelector("form");
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    await saveBoardCard(card.id, new FormData(form), context);
  });
  cardElement.querySelector('[data-action="cancel-card-edit"]').addEventListener("click", () => {
    if (isCurrentBoardContext(context)) void loadBoard(context.projectId, context.model);
  });
}

async function createBoardCard(columnKey, formData, context = activeBoardContext()) {
  if (!isCurrentBoardContext(context) || !state.currentBoard.columns.some((column) => column.key === columnKey)) return;
  const payload = boardCardPayload(formData, columnKey);
  setBoardMessage("Создаем задачу...", false);

  try {
    const created = await fetchJson(boardCardsUrl(context), {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify(payload)
    });
    if (!isCurrentBoardContext(context)) return;
    setBoardMessage(`Задача "${created.title}" создана`, false);
    closeCreateTaskPanel();
    await loadBoard(context.projectId, context.model);
  } catch (error) {
    if (!isCurrentBoardContext(context)) return;
    console.error(error);
    setBoardMessage(error.message || "Не удалось создать задачу", true);
  }
}

async function deleteBoardCard(card) {
  const context = activeBoardContext();
  if (!context || !findBoardCard(card.id)) return;
  const approved = window.confirm(`Удалить задачу "${card.title}"?`);
  if (!approved || !isCurrentBoardContext(context) || !findBoardCard(card.id)) return;
  setBoardMessage("Удаляем задачу...", false);

  try {
    await fetchJson(boardCardUrl(card.id, context), {
      method: "DELETE"
    });
    if (!isCurrentBoardContext(context)) return;
    closeTaskModal();
    setBoardMessage(`Задача "${card.title}" удалена`, false);
    await loadBoard(context.projectId, context.model);
  } catch (error) {
    if (!isCurrentBoardContext(context)) return;
    console.error(error);
    setBoardMessage(error.message || "Не удалось удалить задачу", true);
  }
}

async function saveBoardCard(cardId, formData, context = activeBoardContext()) {
  if (!isCurrentBoardContext(context) || !findBoardCard(cardId)) return;
  const payload = boardCardPayload(formData);
  if (!state.currentBoard.columns.some((column) => column.key === payload.columnKey)) return;
  setBoardMessage("Сохраняем задачу...", false);

  try {
    await fetchJson(boardCardUrl(cardId, context), {
      method: "PUT",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify(payload)
    });
    if (!isCurrentBoardContext(context)) return;
    setBoardMessage("Задача обновлена", false);
    await loadBoard(context.projectId, context.model);
  } catch (error) {
    if (!isCurrentBoardContext(context)) return;
    console.error(error);
    setBoardMessage(error.message || "Не удалось сохранить задачу", true);
  }
}

function boardCardPayload(formData, columnKey) {
  const payload = Object.fromEntries(formData.entries());
  payload.columnKey = columnKey ?? payload.columnKey;
  payload.estimate = Number(payload.estimate || 0);
  payload.blocked = formData.has("blocked");
  payload.labels = payload.labels ?? "";
  return payload;
}

const RELEASE_TRAIN_FIELDS = ["name", "status", "cadence", "quarter", "plannedReleaseDate",
  "codeFreezeDate", "qaFreezeDate", "goLiveDate", "capacityPoints", "committedPoints",
  "readiness", "blockedItems", "scope", "risk", "decision"];
const RELEASE_TRAIN_NUMBERS = ["capacityPoints", "committedPoints", "readiness", "blockedItems"];

function releaseTrainPayload() {
  const payload = Object.fromEntries(new FormData(elements.releaseTrainForm).entries());
  for (const field of RELEASE_TRAIN_FIELDS) {
    if (!String(payload[field] ?? "").trim()) throw new Error("Заполните все поля релизного поезда");
  }
  if (!Object.hasOwn(LABELS.releaseStatus, payload.status)) throw new Error("Выберите допустимый статус");
  for (const field of RELEASE_TRAIN_NUMBERS) {
    const number = Number(payload[field]);
    if (!Number.isInteger(number) || number < 0 || number > (field === "readiness" ? 100 : 2147483647)) {
      throw new Error("Проверьте числовые показатели релизного поезда");
    }
    payload[field] = number;
  }
  return payload;
}

function setReleaseTrainPending(pending) {
  state.trainMutationPending = pending;
  updateReleaseTrainControls();
}

function setReleaseTrainEditorLoading(loading) {
  state.trainEditorLoading = loading;
  updateReleaseTrainControls();
}

function updateReleaseTrainControls() {
  const blocked = state.trainMutationPending || state.trainEditorLoading;
  elements.releaseTrainSubmit.disabled = blocked;
  elements.releaseTrainCancelEdit.disabled = state.trainMutationPending;
  elements.releaseTrainCancelEdit.classList.toggle("hidden",
    !state.editingReleaseTrainId && !state.trainEditorLoading);
  elements.releaseTrainForm.querySelectorAll("input, select, textarea").forEach((field) => {
    field.disabled = blocked;
  });
  elements.releaseTrainList.querySelectorAll("[data-release-action]").forEach((button) => {
    button.disabled = state.trainMutationPending
      || (state.trainEditorLoading && button.dataset.releaseAction === "delete");
  });
}

function resetReleaseTrainEditor() {
  if (state.trainMutationPending) return;
  ++state.trainEditorRequest;
  state.trainEditorLoading = false;
  state.editingReleaseTrainId = null;
  elements.releaseTrainForm.reset();
  elements.releaseTrainFormTitle.textContent = "Новый релизный поезд";
  elements.releaseTrainSubmit.textContent = "Запланировать поезд";
  elements.releaseTrainCancelEdit.classList.add("hidden");
  elements.releaseTrainMessage.textContent = "Новый поезд появится в релизном срезе сразу после сохранения.";
  updateReleaseTrainControls();
}

async function openReleaseTrainEditor(id) {
  if (state.trainMutationPending || state.activeView !== "release") return;
  const request = ++state.trainEditorRequest;
  setReleaseTrainEditorLoading(true);
  elements.releaseTrainMessage.textContent = "Загружаем релизный поезд...";
  try {
    const train = await fetchJson(`/api/release-trains/${encodeURIComponent(id)}`);
    if (request !== state.trainEditorRequest || state.activeView !== "release") return;
    if (train.id !== id) throw new Error("Ответ относится к другому релизному поезду");
    for (const field of RELEASE_TRAIN_FIELDS) {
      elements.releaseTrainForm.elements.namedItem(field).value = train[field] ?? "";
    }
    state.editingReleaseTrainId = id;
    elements.releaseTrainFormTitle.textContent = `Редактирование: ${train.name}`;
    elements.releaseTrainSubmit.textContent = "Сохранить изменения";
    elements.releaseTrainCancelEdit.classList.remove("hidden");
    elements.releaseTrainMessage.textContent = "Изменения сохраняются в существующий релизный поезд.";
    setReleaseTrainEditorLoading(false);
    elements.releaseTrainForm.scrollIntoView({ behavior: "smooth", block: "start" });
    elements.releaseTrainForm.elements.namedItem("name").focus();
  } catch (error) {
    if (request !== state.trainEditorRequest || state.activeView !== "release") return;
    const retainedEditor = state.editingReleaseTrainId
      ? `Остаётся открыта форма поезда «${elements.releaseTrainForm.elements.namedItem("name").value}»; данные сохранены.`
      : "Сохранён режим создания нового поезда.";
    elements.releaseTrainMessage.textContent = (error.status === 404
      ? "Релизный поезд не найден. " : "Не удалось открыть релизный поезд: " + error.message + ". ")
      + "Переключение не выполнено. " + retainedEditor;
    if (error.status === 404) void loadTrainSnapshot();
  } finally {
    if (request === state.trainEditorRequest) setReleaseTrainEditorLoading(false);
  }
}

async function handleReleaseTrainSubmit(event) {
  event.preventDefault();
  if (state.trainMutationPending || state.trainEditorLoading) return;
  let payload;
  try {
    payload = releaseTrainPayload();
  } catch (error) {
    elements.releaseTrainMessage.textContent = error.message;
    return;
  }
  const id = state.editingReleaseTrainId;
  const editorRequest = ++state.trainEditorRequest;
  setReleaseTrainPending(true);
  elements.releaseTrainMessage.textContent = "Сохраняем релизный поезд...";
  try {
    const saved = await fetchJson(id ? `/api/release-trains/${encodeURIComponent(id)}` : "/api/release-trains", {
      method: id ? "PUT" : "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
    applyReleaseTrainChange(saved);
    const refreshed = await loadTrainSnapshot();
    if (editorRequest === state.trainEditorRequest && state.activeView === "release") {
      setReleaseTrainPending(false);
      resetReleaseTrainEditor();
      elements.releaseTrainMessage.textContent = `Релизный поезд "${saved.name}" ${id ? "обновлён" : "добавлен"}`
        + (refreshed ? "" : ". Серверный список пока не обновлён; повторите загрузку страницы.");
    }
  } catch (error) {
    if (editorRequest === state.trainEditorRequest && state.activeView === "release") {
      elements.releaseTrainMessage.textContent = error.status === 404
        ? "Релизный поезд не найден. Данные формы сохранены; обновите список."
        : error.status === 409
          ? "Конфликт изменений. Данные формы сохранены; обновите список перед повтором."
          : error.message || "Не удалось сохранить релизный поезд";
    }
    if (error.status === 404) void loadTrainSnapshot();
  } finally {
    setReleaseTrainPending(false);
    if (elements.releaseTrainMessage.textContent === "Сохраняем релизный поезд...") {
      elements.releaseTrainMessage.textContent = "Запрос завершён. Откройте поезд заново для проверки результата; данные формы сохранены.";
    }
  }
}

async function deleteReleaseTrain(id) {
  if (state.trainMutationPending || state.trainEditorLoading || state.activeView !== "release") return;
  const train = state.releaseTrains.find((item) => item.id === id);
  if (!train || !window.confirm(`Удалить релизный поезд "${train.name}"?`)) return;
  if (state.trainMutationPending || state.trainEditorLoading || state.activeView !== "release") return;
  const editorRequest = ++state.trainEditorRequest;
  setReleaseTrainPending(true);
  elements.releaseTrainMessage.textContent = "Удаляем релизный поезд...";
  try {
    await fetchJson(`/api/release-trains/${encodeURIComponent(id)}`, { method: "DELETE" });
    applyReleaseTrainChange(null, id);
    const refreshed = await loadTrainSnapshot();
    if (editorRequest === state.trainEditorRequest && state.activeView === "release") {
      setReleaseTrainPending(false);
      if (state.editingReleaseTrainId === id) resetReleaseTrainEditor();
      elements.releaseTrainMessage.textContent = `Релизный поезд "${train.name}" удалён`
        + (refreshed ? "" : ". Серверный список пока не обновлён; повторите загрузку страницы.");
    }
  } catch (error) {
    if (editorRequest === state.trainEditorRequest && state.activeView === "release") {
      elements.releaseTrainMessage.textContent = error.status === 404
        ? "Релизный поезд уже отсутствует. Обновите список."
        : error.status === 409
          ? "Удаление отклонено из-за конфликта. Обновите список и повторите."
          : "Не удалось удалить релизный поезд: " + error.message;
    }
    if (error.status === 404) void loadTrainSnapshot();
  } finally {
    setReleaseTrainPending(false);
    if (elements.releaseTrainMessage.textContent === "Удаляем релизный поезд...") {
      elements.releaseTrainMessage.textContent = "Запрос завершён. Проверьте список перед повторным удалением.";
    }
  }
}

async function handleProjectSubmit(event) {
  await window.PMProjectsUI.submitProject(event);
}

function startEditProject(project) {
  window.PMProjectsUI.startEditProject(project);
}

function openProjectForm() {
  window.PMProjectsUI.openProjectForm();
}

function closeProjectForm() {
  window.PMProjectsUI.closeProjectForm();
}

async function deleteProject(project) {
  await window.PMProjectsUI.deleteProject(project);
}

function resetFormMode() {
  window.PMProjectsUI?.resetFormMode();
}

async function loadReleaseChecks(forceRefresh = false) {
  if (!elements.releaseCheckList) {
    return;
  }

  elements.releaseChecksUpdated.textContent = "Обновляем...";
  try {
    const suffix = forceRefresh ? "?refresh=true" : "";
    const snapshot = await fetchJson(`/api/release-checks${suffix}`);
    renderReleaseChecks(snapshot);
  } catch (error) {
    renderReleaseChecks({
      available: false,
      message: error.message || "Не удалось загрузить release checks.",
      checks: []
    });
  }
}

function renderReleaseChecks(snapshot) {
  const release = snapshot.latestRelease;
  elements.releaseChecksUpdated.textContent = snapshot.updatedAt
    ? `Обновлено ${formatDateTime(snapshot.updatedAt)}`
    : "Нет данных";
  elements.releaseChecksMessage.textContent = snapshot.message || "Нет данных о релизе.";

  if (release?.tagName) {
    elements.releaseTag.textContent = release.tagName;
    elements.releaseAssets.textContent = `${release.assetCount ?? 0} файлов`;
    elements.releaseLink.href = release.url;
    elements.releaseLink.classList.remove("hidden");
  } else {
    elements.releaseTag.textContent = "Релиз не найден";
    elements.releaseAssets.textContent = "--";
    elements.releaseLink.removeAttribute("href");
    elements.releaseLink.classList.add("hidden");
  }

  const checks = snapshot.checks ?? [];
  if (!checks.length) {
    elements.releaseCheckList.innerHTML = '<div class="empty-state compact">GitHub пока не вернул статусы проверок.</div>';
    return;
  }

  elements.releaseCheckList.innerHTML = checks
    .map((check) => {
      const meta = releaseCheckMeta(check.state);
      const action = check.url
        ? `<a href="${escapeAttr(check.url)}" target="_blank" rel="noreferrer">Открыть workflow</a>`
        : '<span>Ожидает запуска</span>';
      return `
        <article class="release-check" data-tone="${meta.tone}">
          <span class="release-check-indicator" aria-hidden="true"></span>
          <div>
            <strong>${escapeHtml(check.title)}</strong>
            <p>${escapeHtml(check.description)}</p>
          </div>
          <span class="release-check-state">${meta.label}</span>
          <div class="release-check-action">
            ${action}
            <small>${check.updatedAt ? escapeHtml(formatDateTime(check.updatedAt)) : ""}</small>
          </div>
        </article>
      `;
    })
    .join("");
}

function releaseCheckMeta(state) {
  return {
    success: { tone: "green", label: "Пройдено" },
    failure: { tone: "red", label: "Ошибка" },
    cancelled: { tone: "red", label: "Отменено" },
    timed_out: { tone: "red", label: "Тайм-аут" },
    in_progress: { tone: "yellow", label: "Выполняется" },
    queued: { tone: "yellow", label: "В очереди" },
    not_run: { tone: "neutral", label: "Не запускалось" }
  }[state] ?? { tone: "neutral", label: "Нет данных" };
}

function formatDateTime(value) {
  if (!value) {
    return "--";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat("ru-RU", {
    day: "2-digit",
    month: "short",
    hour: "2-digit",
    minute: "2-digit"
  }).format(date);
}

async function fetchJson(url, options) {
  const response = await fetch(url, options);
  if (response.status === 204) {
    return null;
  }
  if (!response.ok) {
    let message = `HTTP ${response.status}`;
    let details = null;
    try {
      details = await response.json();
      message = details.message || details.error || JSON.stringify(details);
    } catch {
      message = await response.text() || message;
    }
    const error = new Error(message);
    error.status = response.status;
    error.code = details?.code;
    error.fieldErrors = details?.fieldErrors;
    error.currentVersion = details?.currentVersion;
    error.impact = details?.impact;
    throw error;
  }
  return response.json();
}

function boardCardsUrl(context) {
  return `/api/projects/${encodeURIComponent(context.projectId)}/board/cards?model=${encodeURIComponent(context.model)}`;
}

function boardCardUrl(cardId, context) {
  return `/api/projects/${encodeURIComponent(context.projectId)}/board/cards/${encodeURIComponent(cardId)}?model=${encodeURIComponent(context.model)}`;
}

function boardTitleLabel(model) {
  return {
    kanban: "Канбан-доска",
    scrum: "Скрам-доска",
    waterfall: "Waterfall-доска"
  }[model] ?? "Доска";
}

function releaseTrainTone(train) {
  if (train.blockedItems > 0 || train.readiness < 55) {
    return "danger";
  }
  if (train.status === "released" || train.readiness >= 80) {
    return "success";
  }
  return "warning";
}

function capacityPercentFor(train) {
  const capacity = Number(train.capacityPoints || 0);
  if (!capacity) {
    return 0;
  }
  return Math.round((Number(train.committedPoints || 0) / capacity) * 100);
}

function loadCreatedBoards() {
  try {
    const raw = localStorage.getItem(BOARDS_STORAGE_KEY);
    const boards = raw ? JSON.parse(raw) : [];
    return Array.isArray(boards) ? boards.filter((board) => board && typeof board.id === "string"
      && typeof board.projectId === "string" && ["kanban", "scrum", "waterfall"].includes(board.model)
      && typeof board.createdAt === "string").slice(0, 12) : [];
  } catch {
    return [];
  }
}

function saveCreatedBoards() {
  try {
    localStorage.setItem(BOARDS_STORAGE_KEY, JSON.stringify(state.createdBoards));
  } catch {
    // History is optional; storage denial or quota must never prevent opening a server board.
  }
}

function buildSummary(portfolio, projects) {
  return `В срезе ${projects.length} ${declOfNum(projects.length, ["проект", "проекта", "проектов"])}. Бюджет: ${formatCurrency(
    portfolio.totalBudget ?? 0
  )}. Активные: ${portfolio.activeCount ?? 0}, с риском: ${portfolio.riskyCount ?? 0}.`;
}

function setSummary(message, tone = "quiet") {
  if (elements.summary) {
    elements.summary.textContent = message;
    elements.summary.setAttribute("data-tone", tone);
  }
}

function setFormState(message, isError) {
  if (elements.submitMessage) {
    elements.submitMessage.textContent = message;
  }
  if (elements.formStatus) {
    elements.formStatus.textContent = isError ? "Ошибка" : state.editingProjectId ? "Режим правки" : "API готов";
    elements.formStatus.classList.toggle("success", !isError && !state.editingProjectId);
  }
}

function displayLabel(value) {
  const key = String(value ?? "");
  return LABELS.health[key]
    ?? LABELS.status[key]
    ?? LABELS.deliveryModel[key]
    ?? LABELS.priority[key]
    ?? LABELS.releaseStatus[key]
    ?? key;
}

function formatCurrency(value) {
  return new Intl.NumberFormat("ru-RU", {
    style: "currency",
    currency: "RUB",
    maximumFractionDigits: 0
  }).format(Number(value || 0));
}

function formatNumber(value) {
  return new Intl.NumberFormat("ru-RU").format(Number(value || 0));
}

function formatDate(value) {
  if (!value) {
    return "--";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat("ru-RU", {
    day: "2-digit",
    month: "short",
    year: "numeric",
    ...(/^\d{4}-\d{2}-\d{2}$/.test(String(value)) ? { timeZone: "UTC" } : {})
  }).format(date);
}

function milestoneTone(project) {
  const daysLeft = daysUntil(project.deadline);
  if (!Number.isFinite(daysLeft)) {
    return project.health === "red" ? "red" : "yellow";
  }
  const progress = Number(project.progress || 0);
  if (project.health === "red" || isProjectOverdue(project)) {
    return "red";
  }
  if (project.health === "yellow" || (project.status !== "done" && daysLeft <= 14) || progress < 35) {
    return "yellow";
  }
  return "green";
}

function deadlineTone(project) {
  const days = daysUntil(project.deadline);
  return days < 0 ? "red" : days <= 14 ? "yellow" : "green";
}

function groupCount(items, field) {
  return items.reduce((acc, item) => {
    const key = item[field] || "unknown";
    acc[key] = (acc[key] ?? 0) + 1;
    return acc;
  }, {});
}

function riskWeight(project) {
  const healthWeight = {
    red: 30,
    yellow: 20,
    green: 0
  }[project.health] ?? 10;
  const milestoneWeight = {
    red: 20,
    yellow: 10,
    green: 0
  }[milestoneTone(project)] ?? 5;
  return healthWeight + milestoneWeight + (100 - Number(project.progress || 0)) / 10;
}

function daysUntil(value) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(String(value ?? ""))) {
    return Number.POSITIVE_INFINITY;
  }
  const [year, month, day] = value.split("-").map(Number);
  const deadline = Date.UTC(year, month - 1, day);
  if (new Date(deadline).toISOString().slice(0, 10) !== value) {
    return Number.POSITIVE_INFINITY;
  }
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: "Europe/Moscow", year: "numeric", month: "2-digit", day: "2-digit"
  }).formatToParts(new Date());
  const part = (type) => Number(parts.find((item) => item.type === type).value);
  return Math.round((deadline - Date.UTC(part("year"), part("month") - 1, part("day"))) / 86_400_000);
}

function isProjectOverdue(project) {
  return project.status !== "done" && daysUntil(project.deadline) < 0;
}

function isTaskOverdue(card, columnKey, boardModel) {
  const finalColumn = { kanban: "done", scrum: "retrospective", waterfall: "release" }[boardModel];
  return columnKey !== finalColumn && daysUntil(card.dueDate) < 0;
}

function progressComment(progress) {
  if (progress >= 70) {
    return "Портфель близко к поставке";
  }
  if (progress >= 40) {
    return "Основная работа в движении";
  }
  return "Нужен разгон исполнения";
}

function milestoneStatusLabel(tone) {
  return {
    green: "в графике",
    yellow: "требует контроля",
    red: "под угрозой"
  }[tone] ?? "требует контроля";
}

function projectProblemState(project) {
  const tone = milestoneTone(project);
  if (tone === "red") {
    return {
      tone: "red",
      label: "Проблемный",
      reason: project.risk || "Есть критичный риск по статусу, срокам или прогрессу."
    };
  }
  if (tone === "yellow") {
    return {
      tone: "yellow",
      label: "Проблемный",
      reason: project.risk || "Нужен контроль сроков, прогресса или контрольной точки."
    };
  }
  return {
    tone: "green",
    label: "Не проблемный",
    reason: project.milestone || "Проект идет без заметных отклонений."
  };
}

function declOfNum(number, words) {
  const value = Math.abs(number) % 100;
  const digit = value % 10;
  if (value > 10 && value < 20) {
    return words[2];
  }
  if (digit > 1 && digit < 5) {
    return words[1];
  }
  if (digit === 1) {
    return words[0];
  }
  return words[2];
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function escapeAttr(value) {
  return escapeHtml(value);
}

function cssEscape(value) {
  if (window.CSS?.escape) {
    return CSS.escape(value);
  }
  return String(value).replaceAll('"', '\\"');
}

