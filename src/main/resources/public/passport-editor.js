/* Shared C.3a-C.3d version-bound editor and navigation guard. No automatic mutation retries. */
(() => {
  const shell = document.getElementById("passport-editor"), form = document.getElementById("passport-form");
  const message = document.getElementById("passport-write-message"), compare = document.getElementById("passport-comparison");
  const leave = document.getElementById("passport-leave-dialog");
  const optional = {architect:["Ответственный архитектор",255],solutionSummary:["Краткое описание",2000],availabilityRequirement:["Требования доступности",1000],recoveryRequirement:["Требования восстановления",1000]};
  const catalogs = {
    criticality:{LOW:"Низкая",MEDIUM:"Средняя",HIGH:"Высокая",CRITICAL:"Критическая"},
    stage:{NOT_STARTED:"Не начата",IN_DESIGN:"В проектировании",IN_REVIEW:"На рассмотрении",APPROVED:"Утверждена",CHANGES_REQUIRED:"Требуются изменения"},
    solutionType:{NEW_SOLUTION:"Новое решение",EXISTING_SOLUTION_CHANGE:"Изменение существующего решения",INTEGRATION:"Интеграция",MIGRATION:"Миграция"},
    dataClasses:{PUBLIC:"Публичные",INTERNAL:"Внутренние",CONFIDENTIAL:"Конфиденциальные",PERSONAL:"Персональные",PAYMENT:"Платёжные",COMMERCIAL_SECRET:"Коммерческая тайна"},
    environments:{DEV:"Разработка",TEST:"Тестирование",PREPROD:"Предпромышленная",PROD:"Промышленная",OTHER:"Другая"}
  };
  const titles = {criticality:"Критичность",stage:"Стадия",solutionType:"Тип решения",dataClasses:"Классы данных",environments:"Целевые среды",lastReviewDate:"Дата пересмотра",...Object.fromEntries(Object.entries(optional).map(([k,v])=>[k,v[0]]))};
  const esc = value => escapeHtml(String(value ?? ""));
  const normalized = value => String(value ?? "").replace(/\r\n?/g,"\n").normalize("NFC").trim() || null;
  const equal = (a,b) => JSON.stringify(a) === JSON.stringify(b);
  const sorted = values => [...values].sort();
  const safeVersion = value => Number.isSafeInteger(value) && value >= 0;
  let draft = null, pendingNavigation = null;
  function snapshotValues(card) {
    const p = card.passport;
    return {criticality:p?.criticality ?? "",stage:p?.stage ?? "NOT_STARTED",solutionType:p?.solutionType ?? "NEW_SOLUTION",
      ...Object.fromEntries(Object.keys(optional).map(name=>[name,p?.[name] ?? null])),lastReviewDate:p?.lastReviewDate ?? null,
      dataClasses:sorted(card.dataClasses ?? []),environments:sorted(card.environments ?? [])};
  }
  function values() {
    if(draft?.model) return draft.model.values(form);
    const result = {};
    for (const name of ["criticality","stage","solutionType"]) result[name] = form.elements.namedItem(name).value;
    for (const name of [...Object.keys(optional),"lastReviewDate"]) result[name] = normalized(form.elements.namedItem(name).value);
    for (const name of ["dataClasses","environments"]) result[name] = sorted([...form.querySelectorAll(`input[name="${name}"]:checked`)].map(input=>input.value));
    return result;
  }
  function changes(original, current) { return Object.fromEntries(Object.entries(current).filter(([key,value])=>!equal(value,original[key]))); }
  function needsWarning() { return !!draft && (draft.busy || draft.uncertain || Object.keys(changes(draft.original,values())).length > 0); }
  function discard() {
    draft = null;
    shell.classList.add("hidden"); form.innerHTML = ""; compare.innerHTML = "";
    compare.classList.add("hidden"); message.textContent = "";
  }
  function allowNavigation(action) {
    if (!draft) return true;
    if (!needsWarning()) { discard(); return true; }
    if (leave.open) return false;
    pendingNavigation = action;
    document.getElementById("passport-leave-message").textContent = draft.busy || draft.uncertain
      ? "Запрос мог быть выполнен сервером. Закрытие не отменяет запись; её результат может быть неизвестен. Ваш ввод будет потерян."
      : "Есть несохранённые изменения. При закрытии формы введённые данные будут потеряны.";
    leave.showModal();
    document.getElementById("passport-stay").focus();
    return false;
  }
  function fieldError(name,messageText) {
    const target = document.getElementById("passport-error-"+name);
    if (!target) return;
    target.textContent = messageText;
    const field = form.elements.namedItem(name);
    if (field?.setAttribute) field.setAttribute("aria-invalid","true");
    else form.querySelectorAll(`input[name="${name}"]`).forEach(input=>input.setAttribute("aria-invalid","true"));
  }
  function clearErrors() {
    form.querySelectorAll(".field-error").forEach(node=>{node.textContent="";});
    form.querySelectorAll("[aria-invalid]").forEach(node=>node.removeAttribute("aria-invalid"));
  }
  function updateState() {
    if (!draft) return;
    document.getElementById("passport-dirty").textContent = draft.busy ? "Сохраняем…" : needsWarning() ? "Есть несохранённые изменения" : "Нет несохранённых изменений";
    form.querySelector('[type="submit"]').disabled = draft.busy || draft.locked;
    form.querySelectorAll("input,select,textarea").forEach(input=>{input.disabled=draft.busy;});
    document.getElementById("passport-compare").disabled = draft.busy || draft.comparing;
  }
  function start(card, accept, model = null) {
    if (!allowNavigation(()=>start(card,accept,model))) return;
    const original = model ? model.snapshot(card) : snapshotValues(card), create = model ? model.create : !card.passport;
    const version=model ? create ? card.passport.version : model.record.version : create ? card.project.version : card.passport.version;
    draft = {card,projectId:card.project.id,original,create,model,version,instanceId:card.passport?.instanceId,accept,busy:false,locked:false,uncertain:false,comparing:false};
    document.getElementById("passport-editor-title").textContent = model ? model.title : create ? "Создать архитектурный паспорт" : "Редактировать архитектурный паспорт";
    document.getElementById("architecture-editor-help").textContent = (model ? "Заполните обязательные поля. " : "Разрешён неполный черновик. ") + "Не указывайте секреты, адреса систем и эксплуатационные настройки.";
    document.getElementById("passport-conflict-actions").classList.add("hidden");
    compare.classList.add("hidden"); compare.innerHTML = ""; message.textContent = "";
    form.className = "milestone-form";
    if(model) form.innerHTML=model.markup(original);
    else {
    const error = name => `<small class="field-error" id="passport-error-${name}"></small>`;
    const selects = ["criticality","stage","solutionType"].map(name=>`<label>${titles[name]} *<select name="${name}" required aria-describedby="passport-error-${name}"><option value="">Выберите значение</option>${Object.entries(catalogs[name]).map(([code,title])=>`<option value="${code}" ${original[name]===code?"selected":""}>${title}</option>`).join("")}</select>${error(name)}</label>`).join("");
    const texts = Object.entries(optional).map(([name,[title,max]])=>`<label class="${name==='architect'?'':'passport-wide'}">${title}${name==='architect'?`<input name="${name}" maxlength="${max}" value="${esc(original[name])}" aria-describedby="passport-error-${name}">`:`<textarea name="${name}" maxlength="${max}" aria-describedby="passport-error-${name}">${esc(original[name])}</textarea>`}${error(name)}</label>`).join("");
    const sets = ["dataClasses","environments"].map(name=>`<fieldset class="passport-set"><legend>${titles[name]}</legend>${Object.entries(catalogs[name]).map(([code,title])=>`<label class="check-field"><input type="checkbox" name="${name}" value="${code}" ${original[name].includes(code)?"checked":""} aria-describedby="passport-error-${name}">${title}</label>`).join("")}${error(name)}</fieldset>`).join("");
    form.innerHTML = selects + texts + `<label>Дата пересмотра<input type="date" name="lastReviewDate" value="${esc(original.lastReviewDate)}" aria-describedby="passport-error-lastReviewDate">${error("lastReviewDate")}</label>` + sets +
      '<p class="passport-wide">Необязательное пустое поле будет очищено. Пустой набор разрешён для черновика.</p><div class="form-actions"><button type="submit" class="btn btn-primary btn-small">Сохранить паспорт</button><button type="button" class="btn btn-ghost btn-small" data-passport-cancel>Отмена</button></div>';
    }
    shell.classList.remove("hidden");
    updateState(); form.elements.namedItem(model?model.focus??"name":"criticality").focus();
  }
  async function submit(event) {
    event.preventDefault();
    const current = draft;
    if (!current || current.busy || current.locked) return;
    clearErrors(); message.textContent = "";
    const currentValues = values(); let invalid = false;
    if(current.model) invalid=!current.model.validate(currentValues,fieldError);
    else for (const name of ["criticality","stage","solutionType"]) if (!Object.hasOwn(catalogs[name],currentValues[name])) {fieldError(name,"Выберите значение.");invalid=true;}
    if (invalid || !form.reportValidity()) return;
    const version = current.version;
    if (!safeVersion(version)) { message.textContent="Невозможно безопасно передать версию записи. Сохранение не отправлено."; return; }
    const changed = changes(current.original,currentValues);
    if (!current.create && !Object.keys(changed).length) {message.textContent="Изменений нет. Запрос не отправлен.";return;}
    const body = current.model?.initialize ? {expectedPassportVersion:version,expectedPassportInstanceId:current.instanceId} : current.model ? current.create
      ? {expectedPassportVersion:version,expectedPassportInstanceId:current.instanceId,...Object.fromEntries(Object.entries(currentValues).filter(([,value])=>value!==null))}
      : {expectedVersion:version,...changed} : current.create
      ? {expectedProjectVersion:version,...Object.fromEntries(Object.entries(currentValues).filter(([,value])=>value!==null))}
      : {expectedVersion:version,expectedPassportInstanceId:current.instanceId,...changed};
    if ((current.model ? current.create : !current.create) && typeof body.expectedPassportInstanceId !== "string") { message.textContent="Сведения экземпляра паспорта недоступны. Загрузите актуальные данные.";return; }
    current.busy=true; updateState();
    try {
      const suffix=current.model?.initialize?"/checks/initialize":current.model?`/${current.model.kind}${current.create?"":"/"+encodeURIComponent(current.model.record.id)}`:"";
      const result = await fetchJson(`/api/projects/${encodeURIComponent(current.projectId)}/architecture${suffix}`,{method:current.create?"POST":"PATCH",headers:{"Content-Type":"application/json"},body:JSON.stringify(body)});
      if (draft !== current) return;
      if(result?.project?.id!==current.projectId || !result.passport || !result.assessment) throw new Error("Unknown write result");
      discard(); current.accept(result);
    } catch(error) {
      if(draft!==current) return;
      if(error.status===400) {
        for(const name of Object.keys(error.fieldErrors ?? {})) fieldError(name,"Проверьте значение поля: оно не соответствует требованиям.");
        message.textContent="Данные не сохранены. Исправьте отмеченные поля или проверьте состав введённых сведений. Весь ввод сохранён.";
      } else {
        current.locked=true;
        current.uncertain=error.status!==409 && error.status!==404;
        message.textContent=error.status===409 ? error.code==="PASSPORT_INSTANCE_CONFLICT"
          ? "Паспорт удалён и создан заново. Ввод сохранён; эта форма относится к прежнему экземпляру. Запись не повторяется."
          : error.code==="CHECKS_ALREADY_EXIST" ? "Набор проверок уже не пуст. Инициализация не выполнена и не повторяется. Загрузите актуальные сведения для сверки."
          : current.model ? "Конфликт: запись или паспорт изменены другим участником. Ввод сохранён. Исходные токены не заменяются, запись не повторяется."
          : "Конфликт: проект или паспорт изменён другим участником. Ввод сохранён. Ожидаемые версии не заменяются, запись не повторяется."
          : error.status===404 ? current.model && ["COMPONENT_NOT_FOUND","INTEGRATION_NOT_FOUND","CHECK_NOT_FOUND","RISK_NOT_FOUND"].includes(error.code) ? "Выбранная запись удалена. Ввод сохранён. Загрузите актуальные сведения для сверки." : "Проект или паспорт больше не существует. Ввод сохранён. Загрузите актуальные сведения."
          : "Не удалось подтвердить результат записи. Сервер мог сохранить данные. Ввод сохранён; запрос автоматически не повторяется. Сначала загрузите актуальные сведения для сверки.";
        document.getElementById("passport-conflict-actions").classList.remove("hidden");
      }
    } finally { if(draft===current) {current.busy=false;updateState();} }
  }
  function display(name,value) {
    if(value==null || value==="") return "Не указано";
    if(Array.isArray(value)) return value.length ? value.map(code=>esc(catalogs[name][code] ?? "Неизвестное значение")).join(" · ") : "Набор пуст";
    return esc(catalogs[name]?.[value] ?? value).replace(/\n/g,"<br>");
  }
  function renderComparison(current,loaded) {
    const instanceChanged=!!current.card.passport && !!loaded.passport && current.instanceId!==loaded.passport.instanceId;
    const removed=current.model?.record && !(loaded[current.model.kind]??[]).some(row=>row.id===current.model.record.id);
    const description=!loaded.passport?"Сейчас паспорта нет.":instanceChanged?"Это новый экземпляр паспорта после удаления и повторного создания.":removed?"Выбранная запись удалена. Сведения ниже не относятся к существующей записи.":"Актуальные сведения загружены. Исходные токены формы не изменены.";
    const latest=current.model?current.model.snapshot(loaded):snapshotValues(loaded), entered=values();
    const names=current.model?Object.keys(current.model.fields):Object.keys(titles);
    const show=current.model?current.model.display:display;
    compare.innerHTML=`<h3>Сверка с актуальными сведениями</h3><p>${description}</p>${current.model?.comparisonNote?`<p>${esc(current.model.comparisonNote(loaded))}</p>`:""}<dl class="project-details">${names.map(name=>`<div><dt>${current.model?current.model.fields[name].title:titles[name]}</dt><dd>Сейчас: ${removed||!loaded.passport?"Запись отсутствует":current.model?.create?"Для новой записи нет выбранного аналога":show(name,latest[name])}<br>Ваш ввод: ${show(name,entered[name])}</dd></div>`).join("")}</dl><p>Для новой записи сначала закройте эту форму и явно откройте актуальные сведения. Автоматического переноса правок нет.</p><button type="button" class="btn btn-secondary btn-small" id="passport-use-latest">Закрыть форму и открыть актуальные сведения</button>`;
    document.getElementById("passport-use-latest").addEventListener("click",()=>{
      if(draft!==current || !allowNavigation(()=>current.accept(loaded))) return;
      current.accept(loaded);
    });
  }
  async function loadComparison() {
    const current=draft;
    if(!current || current.busy || current.comparing) return;
    current.comparing=true;current.latest=null;updateState();compare.classList.remove("hidden");compare.textContent="Загружаем актуальные сведения для сверки…";
    try {
      const loaded=await fetchJson(`/api/projects/${encodeURIComponent(current.projectId)}/architecture`);
      if(draft!==current) return;
      if(loaded?.project?.id!==current.projectId || !loaded.assessment) throw new Error("Wrong project");
      current.latest=loaded;
      renderComparison(current,loaded);
    } catch(error) { if(draft===current) compare.textContent=error.status===404?"Проект не найден. Ввод формы сохранён.":"Не удалось загрузить актуальные сведения. Ввод формы сохранён; запись не повторялась."; }
    finally {if(draft===current) {current.comparing=false;updateState();} }
  }
  form.addEventListener("submit",submit);
  function inputChanged() { updateState(); if(draft?.latest && !draft.comparing) renderComparison(draft,draft.latest); }
  form.addEventListener("input",inputChanged); form.addEventListener("change",inputChanged);
  form.addEventListener("click",event=>{if(event.target.closest("[data-passport-cancel]")) {if(allowNavigation(()=>{})) discard();}});
  document.getElementById("passport-close").addEventListener("click",()=>{if(allowNavigation(()=>{})) discard();});
  document.getElementById("passport-compare").addEventListener("click",loadComparison);
  document.getElementById("passport-stay").addEventListener("click",()=>{pendingNavigation=null;leave.close();});
  leave.addEventListener("cancel",()=>{pendingNavigation=null;});
  document.getElementById("passport-discard").addEventListener("click",()=>{const action=pendingNavigation;pendingNavigation=null;leave.close();discard();action?.();});
  window.addEventListener("beforeunload",event=>{if(needsWarning()) {event.preventDefault();event.returnValue="";}});
  function startChild(card,kind,recordId,accept) {
    const model=kind==="risks"?window.PMArchitectureRiskModel?.model(card,recordId):kind==="checks"?window.PMArchitectureCheckModel?.model(card,recordId):window.PMArchitectureChildModel?.model(kind,card,recordId);
    if(model) start(card,accept,model);
  }
  window.PMPassportEditor={start,startChild,allowNavigation,needsWarning};
})();
