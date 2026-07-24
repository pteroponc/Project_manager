const BOARDS_STORAGE_KEY = "project-manager-created-boards";

const state = {
  projects: [],
  createdBoards: loadCreatedBoards(),
  filters: {
    quarter: "all",
    health: "all"
  },
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
  projectsList: document.getElementById("projects-list"),
  projectsCount: document.getElementById("projects-count"),
  boardColumns: document.getElementById("board-columns"),
  boardTitle: document.getElementById("board-title"),
  boardModel: document.getElementById("board-model"),
  boardDeck: document.getElementById("board-deck"),
  createdBoardList: document.getElementById("created-board-list"),
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
  sliceBudgetTotal: document.getElementById("slice-budget-total"),
  sliceMilestonesOnTrack: document.getElementById("slice-milestones-on-track"),
  portfolioSliceTable: document.getElementById("portfolio-slice-table"),
  refreshButton: document.getElementById("refresh-button"),
  form: document.getElementById("create-project-form"),
  formKicker: document.getElementById("form-kicker"),
  formTitle: document.getElementById("form-title"),
  formStatus: document.getElementById("form-status"),
  submitMessage: document.getElementById("submit-message"),
  submitButton: document.getElementById("submit-button"),
  cancelEditButton: document.getElementById("cancel-edit-button"),
  projectIdField: document.getElementById("project-id-field"),
  deliveryModelField: document.getElementById("delivery-model-field"),
  template: document.getElementById("project-card-template"),
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
  statRiskyCount: document.querySelector('[data-stat="riskyCount"]'),
  statAverageProgress: document.querySelector('[data-stat="averageProgress"]'),
  releaseChecksUpdated: document.getElementById("release-checks-updated"),
  releaseTag: document.getElementById("release-tag"),
  releaseAssets: document.getElementById("release-assets"),
  releaseLink: document.getElementById("release-link"),
  releaseChecksMessage: document.getElementById("release-checks-message"),
  releaseCheckList: document.getElementById("release-check-list"),
  refreshReleaseChecks: document.getElementById("refresh-release-checks")
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

document.getElementById("filters-form").addEventListener("change", async (event) => {
  state.filters[event.target.name] = event.target.value;
  await loadDashboard();
});

elements.refreshButton.addEventListener("click", loadDashboard);
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
elements.boardDeck?.querySelectorAll("[data-board-model]").forEach((button) => {
  button.addEventListener("click", async () => {
    await createBoard(button.dataset.boardModel);
  });
});
elements.form.addEventListener("submit", handleProjectSubmit);
elements.cancelEditButton.addEventListener("click", resetFormMode);
elements.createTaskForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  await createBoardCard(elements.createTaskColumn.value, new FormData(elements.createTaskForm));
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
  elements.summary.textContent = "Не удалось загрузить данные API.";
});

async function init() {
  await loadDeliveryModels();
  await Promise.all([loadDashboard(), loadReleaseChecks()]);
}

async function loadDeliveryModels() {
  const response = await fetchJson("/api/delivery-models");
  state.deliveryModels = response.items ?? [];
  elements.deliveryModelField.innerHTML = state.deliveryModels
    .map((item) => `<option value="${item}">${item}</option>`)
    .join("");
  resetFormMode();
}

async function loadDashboard() {
  setSummary("Обновляем портфель и список проектов...");
  const query = new URLSearchParams(state.filters).toString();
  const [portfolio, projects] = await Promise.all([
    fetchJson(`/api/portfolio?${query}`),
    fetchJson(`/api/projects?${query}`)
  ]);

  state.projects = projects;
  if (!state.selectedProjectId || !projects.some((project) => project.id === state.selectedProjectId)) {
    state.selectedProjectId = projects[0]?.id ?? null;
  }

  populateQuarterFilter(projects);
  renderStats(portfolio);
  renderPortfolioSlice(portfolio, projects);
  renderProjects(projects);
  renderCreatedBoards();
  setSummary(buildSummary(portfolio, projects));

  if (state.selectedProjectId && state.boardDrilledIn) {
    await loadBoard(state.selectedProjectId, state.activeBoardModel);
  } else {
    renderBoardLanding();
  }
}

function populateQuarterFilter(projects) {
  const current = state.filters.quarter;
  const quarters = [...new Set(projects.map((project) => project.quarter).filter(Boolean))].sort();
  elements.quarterFilter.innerHTML = ['<option value="all">Все кварталы</option>']
    .concat(quarters.map((quarter) => `<option value="${quarter}">${quarter}</option>`))
    .join("");
  elements.quarterFilter.value = quarters.includes(current) || current === "all" ? current : "all";
  state.filters.quarter = elements.quarterFilter.value;
}

function renderStats(portfolio) {
  elements.statProjectCount.textContent = formatNumber(portfolio.projectCount ?? 0);
  elements.statActiveCount.textContent = formatNumber(portfolio.activeCount ?? 0);
  elements.statRiskyCount.textContent = formatNumber(portfolio.riskyCount ?? 0);
  elements.statAverageProgress.textContent = `${portfolio.averageProgress ?? 0}%`;
}

function renderPortfolioSlice(portfolio, projects) {
  const totalBudget = portfolio.totalBudget ?? projects.reduce((sum, project) => sum + Number(project.budget || 0), 0);
  const rows = projects
    .slice()
    .sort((a, b) => Number(b.budget || 0) - Number(a.budget || 0));
  const onTrackCount = rows.filter((project) => milestoneTone(project) === "green").length;

  elements.sliceBudgetTotal.textContent = formatCurrency(totalBudget);
  elements.sliceMilestonesOnTrack.textContent = `${onTrackCount} в графике`;

  if (!rows.length) {
    elements.portfolioSliceTable.innerHTML = '<div class="empty-state compact">Нет проектов для выбранного фильтра.</div>';
    return;
  }

  elements.portfolioSliceTable.innerHTML = `
    <div class="slice-row slice-head">
      <span>Проект</span>
      <span>Бюджет</span>
      <span>Прогресс</span>
      <span>Майлстоун</span>
      <span>Срок</span>
    </div>
    ${rows
      .map((project) => {
        const tone = milestoneTone(project);
        const budgetShare = totalBudget > 0 ? Math.round((Number(project.budget || 0) / totalBudget) * 100) : 0;
        return `
          <div class="slice-row" data-tone="${tone}">
            <span>
              <strong>${escapeHtml(project.name)}</strong>
              <small>${escapeHtml(project.owner)} · ${escapeHtml(project.health)}</small>
            </span>
            <span>
              ${formatCurrency(project.budget)}
              <small>${budgetShare}% портфеля</small>
            </span>
            <span>${project.progress ?? 0}%</span>
            <span>${escapeHtml(project.milestone)}</span>
            <span>
              ${formatDate(project.deadline)}
              <small>${milestoneStatusLabel(tone)}</small>
            </span>
          </div>
        `;
      })
      .join("")}
  `;
}

function renderProjects(projects) {
  elements.projectsCount.textContent = `${projects.length} ${declOfNum(projects.length, ["проект", "проекта", "проектов"])}`;

  if (!projects.length) {
    elements.projectsList.innerHTML = '<div class="empty-state">Для выбранного фильтра проектов нет.</div>';
    return;
  }

  elements.projectsList.innerHTML = "";

  projects.forEach((project) => {
    const fragment = elements.template.content.cloneNode(true);
    const card = fragment.querySelector(".project-card");
    const meta = fragment.querySelector(".project-meta");
    const title = fragment.querySelector("h3");
    const health = fragment.querySelector(".project-health");
    const summary = fragment.querySelector(".project-summary");
    const progressBar = fragment.querySelector(".progress-bar span");
    const progressValue = fragment.querySelector(".progress-value");

    meta.textContent = `${project.deliveryModel} / ${project.status} / ${project.quarter}`;
    title.textContent = project.name;
    health.textContent = project.health;
    health.dataset.tone = project.health;
    summary.textContent = project.summary;
    progressBar.style.width = `${project.progress}%`;
    progressValue.textContent = `${project.progress}%`;

    fragment.querySelector('[data-field="owner"]').textContent = project.owner;
    fragment.querySelector('[data-field="budget"]').textContent = formatCurrency(project.budget);
    fragment.querySelector('[data-field="deadline"]').textContent = formatDate(project.deadline);
    fragment.querySelector('[data-field="kpi"]').textContent = `${project.kpiName}: ${project.kpiTarget}`;

    if (project.id === state.selectedProjectId) {
      card.style.outline = "2px solid rgba(200, 95, 44, 0.35)";
    }

    fragment.querySelector('[data-action="board"]').addEventListener("click", async () => {
      state.selectedProjectId = project.id;
      state.boardDrilledIn = false;
      renderProjects(state.projects);
      renderBoardLanding();
      document.getElementById("board-panel")?.scrollIntoView({ behavior: "smooth", block: "start" });
    });

    fragment.querySelector('[data-action="edit"]').addEventListener("click", () => {
      startEditProject(project);
    });

    fragment.querySelector('[data-action="delete"]').addEventListener("click", async () => {
      await deleteProject(project);
    });

    elements.projectsList.appendChild(fragment);
  });
}

async function loadBoard(projectId, boardModel = state.activeBoardModel) {
  state.activeBoardModel = boardModel;
  const board = await fetchJson(`/api/projects/${projectId}/board?model=${encodeURIComponent(boardModel)}`);
  state.currentBoard = board;
  elements.boardTitle.textContent = `${board.projectName} - ${board.boardTitle}`;
  elements.boardModel.textContent = board.deliveryModel;
  elements.boardDeck?.classList.add("compact");
  if (elements.createTaskButton) {
    elements.createTaskButton.disabled = false;
  }

  if (!board.columns?.length) {
    renderEmptyBoard();
    return;
  }

  elements.boardInsights?.classList.remove("hidden");
  elements.boardFilters?.classList.remove("hidden");
  renderBoardInsights();
  renderBoardColumns();
}

function renderBoardColumns() {
  const board = state.currentBoard;
  if (!board?.columns?.length) {
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

  elements.boardColumns.querySelectorAll('[data-action="edit-card"]').forEach((button) => {
    button.addEventListener("click", () => {
      const card = findBoardCard(button.dataset.cardId);
      if (card) {
        startEditBoardCard(card.card, card.columnKey);
      }
    });
  });

  elements.boardColumns.querySelectorAll('[data-action="open-card"]').forEach((button) => {
    button.addEventListener("click", () => {
      const card = findBoardCard(button.dataset.cardId);
      if (card) {
        openTaskModal(card.card, card.columnTitle);
      }
    });
  });

  elements.boardColumns.querySelectorAll('[data-action="new-card"]').forEach((button) => {
    button.addEventListener("click", () => {
      startCreateBoardCard(button.dataset.columnKey);
    });
  });

  elements.boardColumns.querySelectorAll('[data-action="move-card"]').forEach((select) => {
    const found = findBoardCard(select.dataset.cardId);
    select.innerHTML = (state.currentBoard?.columns ?? [])
      .map((column) => `<option value="${escapeAttr(column.key)}">${escapeHtml(column.title)}</option>`)
      .join("");
    if (found) {
      select.value = found.columnKey;
    }
    select.addEventListener("change", async () => {
      await moveBoardCard(select.dataset.cardId, select.value);
    });
  });

  elements.boardColumns.querySelectorAll('[data-action="move-prev"], [data-action="move-next"]').forEach((button) => {
    button.addEventListener("click", async () => {
      const offset = button.dataset.action === "move-prev" ? -1 : 1;
      const nextColumnKey = adjacentColumnKey(button.dataset.columnKey, offset);
      if (nextColumnKey) {
        await moveBoardCard(button.dataset.cardId, nextColumnKey);
      }
    });
  });

  elements.boardColumns.querySelectorAll(".board-card[draggable='true']").forEach((cardElement) => {
    cardElement.addEventListener("dragstart", (event) => {
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
      columnElement.classList.remove("drag-over");
      const cardId = event.dataTransfer.getData("text/plain") || state.draggingBoardCardId;
      if (cardId) {
        await moveBoardCard(cardId, columnElement.dataset.columnKey);
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
  const cards = (state.currentBoard?.columns ?? []).flatMap((column) => column.cards ?? []);
  const today = new Date().toISOString().slice(0, 10);
  elements.boardTaskCount.textContent = cards.length;
  elements.boardPointCount.textContent = cards.reduce((sum, card) => sum + Number(card.estimate || 0), 0);
  elements.boardOverdueCount.textContent = cards.filter((card) => card.dueDate && card.dueDate < today).length;
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

function renderBoardLanding() {
  state.currentBoard = null;
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
  elements.boardColumns.innerHTML = project
    ? '<div class="empty-state compact">Выберите Kanban, Scrum или Waterfall выше, чтобы открыть внутреннюю доску инициативы.</div>'
    : '<div class="empty-state compact">Сначала выберите инициативу из списка, затем откройте Kanban, Scrum или Waterfall.</div>';
}

async function createBoard(boardModel) {
  const project = state.projects.find((item) => item.id === state.selectedProjectId);
  if (!project) {
    renderBoardLanding();
    setFormState("Сначала выберите инициативу для новой доски", true);
    return;
  }

  const existing = state.createdBoards.find((board) => board.projectId === project.id && board.model === boardModel);
  const board = existing ?? {
    id: `${project.id}-${boardModel}`,
    projectId: project.id,
    projectName: project.name,
    model: boardModel,
    title: boardTitleLabel(boardModel),
    createdAt: new Date().toISOString()
  };

  if (!existing) {
    state.createdBoards = [board, ...state.createdBoards];
    saveCreatedBoards();
    renderCreatedBoards();
    setFormState(`Создана доска "${board.title}" для "${project.name}"`, false);
  }

  await openCreatedBoard(board.id);
}

async function openCreatedBoard(boardId) {
  const board = state.createdBoards.find((item) => item.id === boardId);
  if (!board) {
    renderCreatedBoards();
    return;
  }

  state.selectedProjectId = board.projectId;
  state.activeBoardModel = board.model;
  state.boardDrilledIn = true;
  renderProjects(state.projects);
  renderCreatedBoards();
  await loadBoard(board.projectId, board.model);
  document.getElementById("board-panel")?.scrollIntoView({ behavior: "smooth", block: "start" });
}

function renderCreatedBoards() {
  if (!elements.createdBoardList) {
    return;
  }

  const visibleBoards = state.createdBoards.filter((board) =>
    state.projects.some((project) => project.id === board.projectId)
  );

  if (!visibleBoards.length) {
    elements.createdBoardList.innerHTML = '<div class="empty-state compact">Созданные доски появятся здесь.</div>';
    return;
  }

  elements.createdBoardList.innerHTML = visibleBoards
    .map((board) => `
      <button class="created-board-card" type="button" data-board-id="${escapeAttr(board.id)}">
        <span>${escapeHtml(boardTitleLabel(board.model))}</span>
        <strong>${escapeHtml(board.projectName)}</strong>
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
  const overdue = card.dueDate && card.dueDate < new Date().toISOString().slice(0, 10);
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
        <select class="board-card-status" data-action="move-card" data-card-id="${escapeAttr(card.id)}" aria-label="Перенести задачу"></select>
        <div class="board-card-move-actions" aria-label="Быстрый перенос задачи">
          <button class="btn btn-ghost btn-small" type="button" data-action="move-prev" data-card-id="${escapeAttr(card.id)}" data-column-key="${escapeAttr(columnKey)}" title="Перенести в предыдущий статус" ${hasPreviousColumn ? "" : "disabled"}>&larr;</button>
          <button class="btn btn-ghost btn-small" type="button" data-action="move-next" data-card-id="${escapeAttr(card.id)}" data-column-key="${escapeAttr(columnKey)}" title="Перенести в следующий статус" ${hasNextColumn ? "" : "disabled"}>&rarr;</button>
        </div>
        <button class="btn btn-ghost btn-small" type="button" data-action="edit-card" data-card-id="${escapeAttr(card.id)}">Редактировать</button>
      </div>
    </article>
  `;
}

function adjacentColumnKey(columnKey, offset) {
  const columns = state.currentBoard?.columns ?? [];
  const index = columns.findIndex((column) => column.key === columnKey);
  return columns[index + offset]?.key ?? null;
}

async function moveBoardCard(cardId, columnKey) {
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
  setFormState("Переносим задачу...", false);

  try {
    await fetchJson(boardCardUrl(cardId), {
      method: "PUT",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify(payload)
    });
    setFormState(`Задача "${found.card.title}" перенесена`, false);
    await loadBoard(state.currentBoard.projectId, state.currentBoard.deliveryModel);
  } catch (error) {
    console.error(error);
    setFormState(error.message || "Не удалось перенести задачу", true);
  }
}

function findBoardCard(cardId) {
  for (const column of state.currentBoard?.columns ?? []) {
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
  elements.createTaskPanel.classList.add("hidden");
  elements.createTaskForm.reset();
}

function startCreateBoardCard(columnKey) {
  if (!state.currentBoard?.columns?.length) {
    return;
  }
  const project = state.projects.find((item) => item.id === state.currentBoard?.projectId);
  elements.createTaskColumn.innerHTML = state.currentBoard.columns
    .map((column) => `<option value="${escapeAttr(column.key)}">${escapeHtml(column.title)}</option>`)
    .join("");
  elements.createTaskColumn.value = columnKey || state.currentBoard.columns[0].key;
  elements.createTaskOwner.value = project?.owner ?? "";
  elements.createTaskDueDate.value = project?.deadline ?? "";
  elements.createTaskPanel.classList.remove("hidden");
  elements.createTaskPanel.scrollIntoView({ behavior: "smooth", block: "nearest" });
  elements.createTaskTitle.focus();
}

function startEditBoardCard(card, columnKey) {
  const cardElement = elements.boardColumns.querySelector(`[data-card-id="${cssEscape(card.id)}"]`);
  if (!cardElement) {
    return;
  }
  const columnOptions = (state.currentBoard?.columns ?? [])
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
    await saveBoardCard(card.id, new FormData(form));
  });
  cardElement.querySelector('[data-action="cancel-card-edit"]').addEventListener("click", () => {
    loadBoard(state.currentBoard.projectId, state.currentBoard.deliveryModel);
  });
}

async function createBoardCard(columnKey, formData) {
  const payload = boardCardPayload(formData, columnKey);
  setFormState("Создаем задачу...", false);

  try {
    const created = await fetchJson(boardCardsUrl(), {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify(payload)
    });
    setFormState(`Задача "${created.title}" создана`, false);
    closeCreateTaskPanel();
    await loadBoard(state.currentBoard.projectId, state.currentBoard.deliveryModel);
  } catch (error) {
    console.error(error);
    setFormState(error.message || "Не удалось создать задачу", true);
  }
}

async function deleteBoardCard(card) {
  const approved = window.confirm(`Удалить задачу "${card.title}"?`);
  if (!approved) {
    return;
  }
  setFormState("Удаляем задачу...", false);

  try {
    await fetchJson(boardCardUrl(card.id), {
      method: "DELETE"
    });
    closeTaskModal();
    setFormState(`Задача "${card.title}" удалена`, false);
    await loadBoard(state.currentBoard.projectId, state.currentBoard.deliveryModel);
  } catch (error) {
    console.error(error);
    setFormState(error.message || "Не удалось удалить задачу", true);
  }
}

async function saveBoardCard(cardId, formData) {
  const payload = boardCardPayload(formData);
  setFormState("Сохраняем задачу...", false);

  try {
    await fetchJson(boardCardUrl(cardId), {
      method: "PUT",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify(payload)
    });
    setFormState("Задача обновлена", false);
    await loadBoard(state.currentBoard.projectId, state.currentBoard.deliveryModel);
  } catch (error) {
    console.error(error);
    setFormState(error.message || "Не удалось сохранить задачу", true);
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

async function handleProjectSubmit(event) {
  event.preventDefault();
  const formData = new FormData(elements.form);
  const payload = Object.fromEntries(formData.entries());
  delete payload.projectId;
  payload.budget = Number(payload.budget);
  payload.progress = Number(payload.progress);

  setFormState("Сохраняем проект...", false);

  try {
    const isEditing = Boolean(state.editingProjectId);
    const url = isEditing ? `/api/projects/${state.editingProjectId}` : "/api/projects";
    const saved = await fetchJson(url, {
      method: isEditing ? "PUT" : "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify(payload)
    });

    setFormState(isEditing ? `Проект "${saved.name}" обновлен` : `Проект "${saved.name}" создан`, false);
    resetFormMode();
    state.selectedProjectId = saved.id;
    await loadDashboard();
  } catch (error) {
    console.error(error);
    setFormState(error.message || "Не удалось сохранить проект", true);
  }
}

function startEditProject(project) {
  state.editingProjectId = project.id;
  elements.projectIdField.value = project.id;
  elements.formKicker.textContent = "Редактирование";
  elements.formTitle.textContent = `Редактируем: ${project.name}`;
  elements.submitButton.textContent = "Сохранить изменения";
  elements.cancelEditButton.classList.remove("hidden");
  elements.formStatus.textContent = "Режим правки";
  elements.formStatus.classList.remove("success");

  for (const [key, value] of Object.entries(project)) {
    const field = elements.form.elements.namedItem(key);
    if (field) {
      field.value = value ?? "";
    }
  }

  document.getElementById("project-form")?.scrollIntoView({ behavior: "smooth", block: "start" });
}

async function deleteProject(project) {
  const approved = window.confirm(`Удалить проект "${project.name}"?`);
  if (!approved) {
    return;
  }

  setFormState(`Удаляем проект "${project.name}"...`, false);

  try {
    await fetchJson(`/api/projects/${project.id}`, {
      method: "DELETE"
    });

    if (state.editingProjectId === project.id) {
      resetFormMode();
    }
    if (state.selectedProjectId === project.id) {
      state.selectedProjectId = null;
    }
    state.createdBoards = state.createdBoards.filter((board) => board.projectId !== project.id);
    saveCreatedBoards();
    setFormState(`Проект "${project.name}" удален`, false);
    await loadDashboard();
  } catch (error) {
    console.error(error);
    setFormState(error.message || "Не удалось удалить проект", true);
  }
}

function resetFormMode() {
  state.editingProjectId = null;
  elements.projectIdField.value = "";
  elements.form.reset();
  elements.formKicker.textContent = "Новый проект";
  elements.formTitle.textContent = "Добавить проект в портфель";
  elements.submitButton.textContent = "Создать проект";
  elements.cancelEditButton.classList.add("hidden");
  elements.formStatus.textContent = "API готов";
  elements.formStatus.classList.add("success");
  if (state.deliveryModels[0]) {
    elements.deliveryModelField.value = state.deliveryModels[0];
  }
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
    try {
      const data = await response.json();
      message = data.message || data.error || JSON.stringify(data);
    } catch {
      message = await response.text() || message;
    }
    throw new Error(message);
  }
  return response.json();
}

function boardCardsUrl() {
  return `/api/projects/${state.currentBoard.projectId}/board/cards?model=${encodeURIComponent(state.currentBoard.deliveryModel)}`;
}

function boardCardUrl(cardId) {
  return `/api/projects/${state.currentBoard.projectId}/board/cards/${encodeURIComponent(cardId)}?model=${encodeURIComponent(state.currentBoard.deliveryModel)}`;
}

function boardTitleLabel(model) {
  return {
    kanban: "Канбан-доска",
    scrum: "Скрам-доска",
    waterfall: "Waterfall-доска"
  }[model] ?? "Доска";
}

function loadCreatedBoards() {
  try {
    const raw = localStorage.getItem(BOARDS_STORAGE_KEY);
    const boards = raw ? JSON.parse(raw) : [];
    return Array.isArray(boards) ? boards : [];
  } catch {
    return [];
  }
}

function saveCreatedBoards() {
  localStorage.setItem(BOARDS_STORAGE_KEY, JSON.stringify(state.createdBoards));
}

function buildSummary(portfolio, projects) {
  return `В срезе ${projects.length} ${declOfNum(projects.length, ["проект", "проекта", "проектов"])}. Бюджет: ${formatCurrency(
    portfolio.totalBudget ?? 0
  )}. Активные: ${portfolio.activeCount ?? 0}, с риском: ${portfolio.riskyCount ?? 0}.`;
}

function setSummary(message) {
  elements.summary.textContent = message;
}

function setFormState(message, isError) {
  elements.submitMessage.textContent = message;
  elements.formStatus.textContent = isError ? "Ошибка" : state.editingProjectId ? "Режим правки" : "API готов";
  elements.formStatus.classList.toggle("success", !isError && !state.editingProjectId);
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
    year: "numeric"
  }).format(date);
}

function milestoneTone(project) {
  const deadline = new Date(project.deadline);
  if (Number.isNaN(deadline.getTime())) {
    return project.health === "red" ? "red" : "yellow";
  }
  const daysLeft = Math.ceil((deadline.getTime() - Date.now()) / 86_400_000);
  const progress = Number(project.progress || 0);
  if (project.health === "red" || daysLeft < 0) {
    return "red";
  }
  if (project.health === "yellow" || daysLeft <= 14 || progress < 35) {
    return "yellow";
  }
  return "green";
}

function milestoneStatusLabel(tone) {
  return {
    green: "в графике",
    yellow: "требует контроля",
    red: "под угрозой"
  }[tone] ?? "требует контроля";
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

