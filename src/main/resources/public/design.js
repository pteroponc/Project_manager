(() => {
  const escape = (value) => String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");

  const daysUntil = (value) => {
    if (!value) return Number.POSITIVE_INFINITY;
    const target = new Date(value);
    if (Number.isNaN(target.getTime())) return Number.POSITIVE_INFINITY;
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    target.setHours(0, 0, 0, 0);
    return Math.ceil((target.getTime() - today.getTime()) / 86400000);
  };

  const formatDate = (value) => {
    if (!value) return "—";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return value;
    return new Intl.DateTimeFormat("ru-RU", { day: "2-digit", month: "short" }).format(date);
  };

  const riskWeight = (project) => {
    if (project.health === "red") return 3;
    if (project.health === "yellow") return 2;
    if (daysUntil(project.deadline) < 0) return 3;
    if (daysUntil(project.deadline) <= 14) return 2;
    return 1;
  };

  const issueText = (project) => {
    const days = daysUntil(project.deadline);
    if (days < 0) return `Срок просрочен на ${Math.abs(days)} д.`;
    if (project.health === "red") return project.risk || "Критичный статус проекта";
    if (project.health === "yellow") return project.risk || "Требуется управленческий контроль";
    if (days <= 14) return `До срока осталось ${days} д.`;
    return project.risk || "Требуется внимание";
  };

  function buildShell() {
    const shell = document.querySelector(".page-shell");
    const hero = document.querySelector(".hero");
    const workspace = document.querySelector(".workspace");
    const stats = document.getElementById("stats-grid");
    if (!shell || !hero || !workspace || !stats || document.querySelector(".app-sidebar")) return;

    const sidebar = document.createElement("aside");
    sidebar.className = "app-sidebar";
    sidebar.innerHTML = `
      <div class="sidebar-brand">
        <div class="brand-mark">PM</div>
        <div><strong>Project Manager</strong><span>Portfolio workspace</span></div>
      </div>
      <nav class="sidebar-nav" aria-label="Основная навигация">
        <a class="active" href="#stats-grid"><span>Обзор</span></a>
        <a href="#projects-list"><span>Проекты</span></a>
        <a href="#stats-grid"><span>Дашборды</span></a>
        <a href="#board-panel"><span>Доски</span></a>
        <a href="#release-checks-panel"><span>Релизы</span></a>
        <a href="#project-form"><span>Документы</span></a>
      </nav>
      <div class="sidebar-foot"><strong>Рабочее пространство</strong>Состояние портфеля и точки управленческого внимания.</div>
    `;
    shell.prepend(sidebar);

    const heroCopy = hero.querySelector(".hero-copy");
    if (heroCopy) {
      const eyebrow = heroCopy.querySelector(".eyebrow");
      const title = heroCopy.querySelector("h1");
      const text = heroCopy.querySelector(".hero-text");
      if (eyebrow) eyebrow.textContent = "Обзор портфеля";
      if (title) title.textContent = "Портфель проектов";
      if (text) text.textContent = "Что требует внимания сегодня: состояние инициатив, сроки, риски и контрольные точки.";
      const row = document.createElement("div");
      row.className = "overview-title-row";
      heroCopy.parentNode.insertBefore(row, heroCopy);
      row.appendChild(heroCopy);
      const live = document.createElement("div");
      live.className = "overview-updated";
      live.innerHTML = '<span class="live-dot"></span><span>Данные портфеля</span>';
      row.appendChild(live);
    }

    const attention = document.createElement("section");
    attention.className = "attention-grid";
    attention.id = "executive-focus";
    attention.innerHTML = `
      <section class="panel attention-panel">
        <div class="section-head">
          <div><p class="section-kicker">Фокус руководителя</p><h2>Требует внимания</h2></div>
          <span class="pill danger" id="attention-count">0</span>
        </div>
        <div class="executive-list" id="attention-list"><div class="executive-empty">Загружаем проекты…</div></div>
      </section>
      <section class="panel deadlines-panel">
        <div class="section-head">
          <div><p class="section-kicker">Ближайшие даты</p><h2>Контрольные точки</h2></div>
          <span class="pill" id="deadline-count">0</span>
        </div>
        <div class="executive-list" id="deadline-focus-list"><div class="executive-empty">Загружаем сроки…</div></div>
      </section>`;
    stats.insertAdjacentElement("afterend", attention);
  }

  function render(projects) {
    const attentionList = document.getElementById("attention-list");
    const deadlineList = document.getElementById("deadline-focus-list");
    if (!attentionList || !deadlineList) return;

    const attention = projects
      .filter((p) => p.health !== "green" || daysUntil(p.deadline) <= 14)
      .sort((a, b) => riskWeight(b) - riskWeight(a) || daysUntil(a.deadline) - daysUntil(b.deadline));

    document.getElementById("attention-count").textContent = String(attention.length);
    attentionList.innerHTML = attention.length
      ? attention.slice(0, 4).map((p) => `
        <article class="attention-item">
          <span class="attention-signal ${riskWeight(p) >= 3 ? "red" : "yellow"}"></span>
          <div><strong>${escape(p.name)}</strong><p>${escape(issueText(p))}</p></div>
          <span class="attention-owner">${escape(p.owner || "—")}</span>
        </article>`).join("")
      : '<div class="executive-empty"><strong>Критичных вопросов нет</strong>Все инициативы находятся в рабочем коридоре.</div>';

    const deadlines = projects
      .filter((p) => p.deadline)
      .sort((a, b) => daysUntil(a.deadline) - daysUntil(b.deadline))
      .slice(0, 5);
    document.getElementById("deadline-count").textContent = String(deadlines.length);
    deadlineList.innerHTML = deadlines.length
      ? deadlines.map((p) => {
          const days = daysUntil(p.deadline);
          const tone = days < 0 ? "red" : days <= 14 ? "yellow" : "green";
          const label = days < 0 ? `Просрочено ${Math.abs(days)} д.` : days === 0 ? "Сегодня" : `Через ${days} д.`;
          return `<article class="deadline-focus-item">
            <span class="deadline-date">${escape(formatDate(p.deadline))}</span>
            <div><strong>${escape(p.name)}</strong><p>${escape(p.milestone || "Контрольная точка")}</p></div>
            <span class="deadline-state ${tone}">${escape(label)}</span>
          </article>`;
        }).join("")
      : '<div class="executive-empty">Контрольные точки пока не заданы.</div>';
  }

  function refreshFromAppState() {
    if (typeof state !== "undefined" && Array.isArray(state.projects)) {
      render(state.projects);
    }
  }

  buildShell();
  refreshFromAppState();

  const list = document.getElementById("projects-list");
  if (list) {
    new MutationObserver(() => refreshFromAppState()).observe(list, { childList: true, subtree: true });
  }
})();
