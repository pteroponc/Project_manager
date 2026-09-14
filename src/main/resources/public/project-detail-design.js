(() => {
  const style = document.createElement("style");
  style.textContent = `
    .project-card { cursor: pointer; }
    .project-detail-overlay { position: fixed; inset: 0; z-index: 90; background: rgba(15,23,42,.42); display: flex; justify-content: flex-end; backdrop-filter: blur(2px); }
    .project-detail-shell { width: min(920px, calc(100vw - 72px)); height: 100%; overflow: auto; background: #f5f6f8; box-shadow: -18px 0 50px rgba(15,23,42,.16); }
    .project-detail-header { position: sticky; top: 0; z-index: 2; padding: 24px 28px 20px; border-bottom: 1px solid #e4e7ec; background: rgba(255,255,255,.96); backdrop-filter: blur(12px); }
    .project-detail-topline { display:flex; align-items:center; justify-content:space-between; gap:16px; }
    .project-detail-breadcrumb { color:#667085; font-size:11px; font-weight:700; }
    .project-detail-close { width:34px; height:34px; border:1px solid #d8dde5; border-radius:8px; background:#fff; color:#475467; cursor:pointer; }
    .project-detail-title-row { display:flex; align-items:flex-start; justify-content:space-between; gap:18px; margin-top:16px; }
    .project-detail-title-row h2 { margin:0; font-size:26px; letter-spacing:-.035em; }
    .project-detail-summary { margin:7px 0 0; max-width:650px; color:#667085; font-size:12px; line-height:1.6; }
    .project-detail-body { padding:20px 28px 40px; display:grid; gap:14px; }
    .project-detail-kpis { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:10px; }
    .detail-kpi, .detail-panel { border:1px solid #e4e7ec; border-radius:11px; background:#fff; }
    .detail-kpi { padding:15px; }
    .detail-kpi span { display:block; color:#667085; font-size:10px; font-weight:700; }
    .detail-kpi strong { display:block; margin-top:7px; font-size:17px; letter-spacing:-.025em; }
    .detail-grid { display:grid; grid-template-columns:minmax(0,1.15fr) minmax(280px,.85fr); gap:14px; }
    .detail-panel { padding:18px; }
    .detail-panel h3 { margin:0 0 14px; font-size:14px; }
    .detail-facts { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:0; margin:0; }
    .detail-facts div { padding:11px 0; border-bottom:1px solid #edf0f3; }
    .detail-facts div:nth-last-child(-n+2) { border-bottom:0; }
    .detail-facts dt { color:#98a2b3; font-size:9px; font-weight:800; letter-spacing:.06em; text-transform:uppercase; }
    .detail-facts dd { margin:5px 0 0; color:#344054; font-size:11px; font-weight:650; }
    .detail-callout { padding:13px; border-radius:9px; background:#f8fafc; border:1px solid #edf0f3; }
    .detail-callout + .detail-callout { margin-top:9px; }
    .detail-callout span { display:block; margin-bottom:5px; color:#98a2b3; font-size:9px; font-weight:800; text-transform:uppercase; letter-spacing:.05em; }
    .detail-callout p { margin:0; color:#475467; font-size:11px; line-height:1.55; }
    .detail-progress-head { display:flex; justify-content:space-between; align-items:center; margin-bottom:8px; font-size:11px; }
    .detail-progress-track { height:7px; overflow:hidden; border-radius:99px; background:#eef1f5; }
    .detail-progress-track i { display:block; height:100%; border-radius:99px; background:#315efb; }
    .detail-actions { display:flex; gap:8px; flex-wrap:wrap; margin-top:16px; }
    .detail-placeholder-list { display:grid; gap:8px; }
    .detail-placeholder { display:flex; justify-content:space-between; gap:12px; padding:11px 0; border-bottom:1px solid #edf0f3; }
    .detail-placeholder:last-child { border-bottom:0; }
    .detail-placeholder strong { font-size:11px; }
    .detail-placeholder span { color:#98a2b3; font-size:10px; }
    @media(max-width:760px){.project-detail-shell{width:100vw}.project-detail-kpis{grid-template-columns:repeat(2,1fr)}.detail-grid{grid-template-columns:1fr}.project-detail-header,.project-detail-body{padding-left:18px;padding-right:18px}}
  `;
  document.head.appendChild(style);

  const esc = (value) => String(value ?? "").replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll('"', "&quot;").replaceAll("'", "&#39;");
  const money = (value) => new Intl.NumberFormat("ru-RU", { style: "currency", currency: "RUB", maximumFractionDigits: 0 }).format(Number(value || 0));
  const date = (value) => {
    if (!value) return "—";
    const d = new Date(value);
    return Number.isNaN(d.getTime()) ? esc(value) : new Intl.DateTimeFormat("ru-RU", { day:"2-digit", month:"short", year:"numeric" }).format(d);
  };
  const healthLabel = { green:"В графике", yellow:"Требует внимания", red:"Под угрозой" };

  function findProjectFromCard(card) {
    const title = card.querySelector("h3")?.textContent?.trim();
    if (!title || !window.state?.projects) return null;
    return window.state.projects.find((project) => project.name === title) || null;
  }

  function openProject(project) {
    if (!project) return;
    document.querySelector(".project-detail-overlay")?.remove();
    const progress = Math.max(0, Math.min(100, Number(project.progress || 0)));
    const overlay = document.createElement("div");
    overlay.className = "project-detail-overlay";
    overlay.innerHTML = `
      <article class="project-detail-shell" role="dialog" aria-modal="true" aria-label="Карточка проекта">
        <header class="project-detail-header">
          <div class="project-detail-topline"><span class="project-detail-breadcrumb">Портфель / Проект</span><button class="project-detail-close" type="button" aria-label="Закрыть">×</button></div>
          <div class="project-detail-title-row"><div><h2>${esc(project.name)}</h2><p class="project-detail-summary">${esc(project.summary || "Описание проекта пока не заполнено.")}</p></div><span class="pill ${project.health === "red" ? "danger" : project.health === "yellow" ? "warning" : "success"}">${healthLabel[project.health] || esc(project.health || "Статус")}</span></div>
          <div class="detail-actions"><button class="btn btn-primary btn-small" type="button" data-detail-action="board">Открыть доску</button><button class="btn btn-secondary btn-small" type="button" data-detail-action="edit">Редактировать</button></div>
        </header>
        <div class="project-detail-body">
          <section class="project-detail-kpis">
            <div class="detail-kpi"><span>Прогресс</span><strong>${progress}%</strong></div>
            <div class="detail-kpi"><span>Срок</span><strong>${date(project.deadline)}</strong></div>
            <div class="detail-kpi"><span>Бюджет</span><strong>${money(project.budget)}</strong></div>
            <div class="detail-kpi"><span>${esc(project.kpiName || "KPI")}</span><strong>${esc(project.kpiTarget || "—")}</strong></div>
          </section>
          <section class="detail-grid">
            <div class="detail-panel"><h3>Состояние проекта</h3><div class="detail-progress-head"><span>Выполнение</span><strong>${progress}%</strong></div><div class="detail-progress-track"><i style="width:${progress}%"></i></div><dl class="detail-facts"><div><dt>Владелец</dt><dd>${esc(project.owner || "—")}</dd></div><div><dt>Статус</dt><dd>${esc(project.status || "—")}</dd></div><div><dt>Квартал</dt><dd>${esc(project.quarter || "—")}</dd></div><div><dt>Модель</dt><dd>${esc(project.deliveryModel || "—")}</dd></div><div><dt>Контрольная точка</dt><dd>${esc(project.milestone || "—")}</dd></div><div><dt>Срок</dt><dd>${date(project.deadline)}</dd></div></dl></div>
            <div class="detail-panel"><h3>Что требует контроля</h3><div class="detail-callout"><span>Риск</span><p>${esc(project.risk || "Критичные риски не указаны.")}</p></div><div class="detail-callout"><span>Зависимость</span><p>${esc(project.dependency || "Критичные зависимости не указаны.")}</p></div></div>
          </section>
          <section class="detail-grid">
            <div class="detail-panel"><h3>Ближайшая работа</h3><div class="detail-placeholder-list"><div class="detail-placeholder"><strong>Задачи проекта</strong><span>Открываются на доске</span></div><div class="detail-placeholder"><strong>Контрольная точка</strong><span>${esc(project.milestone || "Не задана")}</span></div><div class="detail-placeholder"><strong>Релиз</strong><span>Связь с релизным треком</span></div></div></div>
            <div class="detail-panel"><h3>Управленческие решения</h3><div class="detail-placeholder-list"><div class="detail-placeholder"><strong>Решения</strong><span>Следующий функциональный слой</span></div><div class="detail-placeholder"><strong>Документы</strong><span>Следующий функциональный слой</span></div><div class="detail-placeholder"><strong>Команда</strong><span>${esc(project.owner || "Владелец не указан")}</span></div></div></div>
          </section>
        </div>
      </article>`;
    document.body.appendChild(overlay);
    document.body.style.overflow = "hidden";
    const close = () => { overlay.remove(); document.body.style.overflow = ""; };
    overlay.querySelector(".project-detail-close").addEventListener("click", close);
    overlay.addEventListener("click", (e) => { if (e.target === overlay) close(); });
    overlay.querySelector('[data-detail-action="board"]').addEventListener("click", () => { close(); document.querySelector(`.project-card h3`)?.closest(".project-card"); cardAction(project.name, "board"); });
    overlay.querySelector('[data-detail-action="edit"]').addEventListener("click", () => { close(); cardAction(project.name, "edit"); });
  }

  function cardAction(name, action) {
    const card = [...document.querySelectorAll(".project-card")].find((item) => item.querySelector("h3")?.textContent?.trim() === name);
    card?.querySelector(`[data-action="${action}"]`)?.click();
  }

  document.addEventListener("click", (event) => {
    const card = event.target.closest(".project-card");
    if (!card || event.target.closest("button, a, input, select, textarea")) return;
    openProject(findProjectFromCard(card));
  });

  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") {
      document.querySelector(".project-detail-overlay")?.querySelector(".project-detail-close")?.click();
    }
  });
})();
