/* C.3e: immutable deletion intent, server impact presentation, no local aggregate edits. */
(() => {
  const counters={componentCount:"Компоненты",integrationCount:"Интеграции",checkCount:"Проверки",securityRiskCount:"Риски ИБ",dataClassCount:"Классы данных паспорта",environmentCount:"Среды"};
  const names={components:"Компонент",integrations:"Интеграция",checks:"Проверка",risks:"Риск ИБ",passport:"Архитектурный паспорт"};
  const actionNames={components:"компонент",integrations:"интеграцию",checks:"проверку",risks:"риск ИБ",passport:"паспорт"};
  const checkTypes={ARCHITECTURE_APPROVAL:"Согласование архитектуры",SECURITY_REQUIREMENTS:"Требования ИБ",DATA_CLASSIFICATION:"Классификация данных",THREAT_MODEL:"Модель угроз",SECURITY_ASSESSMENT:"Оценка безопасности",ACCESS_CONTROL:"Контроль доступа",LOGGING_MONITORING:"Журналирование и мониторинг",BACKUP_RECOVERY:"Резервное копирование и восстановление",LOAD_TESTING:"Нагрузочное тестирование",PRODUCTION_ADMISSION:"Допуск к промышленной эксплуатации"};
  const esc=value=>escapeHtml(String(value??""));
  const safeVersion=value=>Number.isSafeInteger(value)&&value>=0;
  const collections=["dataClasses","environments","components","integrations","checks","risks"];
  const usedSnapshots=new WeakSet();
  function model(card,kind,recordId) {
    if(!card.passport||!Object.hasOwn(names,kind))return null;
    const passportDeletion=kind==='passport';
    const row=passportDeletion?card.passport:(card[kind]??[]).find(r=>r.id===recordId);
    if(!row)return null;
    const tokens=Object.freeze({projectId:card.project.id,recordId:passportDeletion?null:row.id,version:row.version,passportVersion:card.passport.version,instanceId:card.passport.instanceId});
    const tokenValid=safeVersion(tokens.version)&&safeVersion(tokens.passportVersion)&&typeof tokens.instanceId==='string'&&/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(tokens.instanceId);
    const reused=usedSnapshots.has(card);
    const title=passportDeletion?'Паспорт проекта':kind==='checks'?checkTypes[row.checkType]??'Неизвестный тип проверки':row.name;
    const base=`/api/projects/${encodeURIComponent(tokens.projectId)}/architecture`;
    let impact=null;
    function validImpact(value) {
      return value?.projectId===tokens.projectId&&typeof value.passportPresent==='boolean'&&typeof value.deletionAllowed==='boolean'
        &&Array.isArray(value.blockers)&&value.blockers.every(item=>typeof item==='string')
        &&Object.keys(counters).every(name=>Number.isSafeInteger(value[name])&&value[name]>=0)
        &&(!value.deletionAllowed||(value.passportPresent&&value.blockers.length===0&&Object.keys(counters).every(name=>value[name]===0)));
    }
    function impactHTML(value) {
      if(!validImpact(value))return '<p>Не удалось подтвердить сведения о зависимостях. Удаление недоступно.</p>';
      return `<h3>Зависимости паспорта</h3><p>${value.passportPresent?'Паспорт существует.':'Паспорт отсутствует; удалять нечего.'}</p><dl class="project-details">${Object.entries(counters).map(([key,label])=>`<div><dt>${label}</dt><dd>${value[key]}</dd></div>`).join('')}</dl><h3>Причины запрета</h3>${value.blockers.length?`<ul>${value.blockers.map(reason=>`<li>${esc(reason)}</li>`).join('')}</ul>`:'<p>Сервер не вернул причин запрета.</p>'}<p>${value.deletionAllowed?'Предварительно удаление разрешено. Окончательное решение принимает DELETE после повторной проверки.':'Удаление запрещено. Зависимости автоматически не очищаются.'} Исходные токены подтверждения не изменены.</p>`;
    }
    const validCard=value=>value?.project?.id===tokens.projectId&&typeof value.project.name==='string'&&safeVersion(value.project.version)
      &&(value.passport===null||value.passport?.projectId===tokens.projectId&&typeof value.passport.instanceId==='string'&&safeVersion(value.passport.version))
      &&collections.every(name=>Array.isArray(value[name]))&&['dataClasses','environments'].every(name=>value[name].every(code=>typeof code==='string'))
      &&['components','integrations','checks','risks'].every(name=>value[name].every(row=>typeof row?.id==='string'&&row.projectId===tokens.projectId&&safeVersion(row.version)))
      &&Array.isArray(value.assessment?.reasons)&&Array.isArray(value.assessment.acceptedHighRisks)&&Array.isArray(value.assessment.upcomingChecks)
      &&value.assessment.reasons.every(reason=>typeof reason?.message==='string'&&typeof reason.code==='string'&&typeof reason.entityType==='string'&&typeof reason.status==='string')
      &&value.assessment.acceptedHighRisks.every(risk=>typeof risk?.id==='string'&&typeof risk.level==='string'&&typeof risk.justification==='string'&&typeof risk.acceptanceVerification==='string')
      &&value.assessment.upcomingChecks.every(check=>typeof check?.id==='string'&&typeof check.checkType==='string'&&typeof check.status==='string')
      &&typeof value.assessment.statusLabel==='string'&&typeof value.assessment.calculationDate==='string'&&typeof value.assessment.timeZone==='string'
      &&['BLOCKED','ATTENTION_REQUIRED','NOT_ASSESSED','READY'].includes(value.assessment.status)&&value.assessment.officialGoLiveApproval===false;
    const missing=value=>passportDeletion?!value.passport:!(value[kind]??[]).some(r=>r.id===tokens.recordId);
    return {kind,record:row,create:false,deletion:true,passportDeletion,unusableIntent:reused||!tokenValid,focus:'confirmed',fields:{confirmed:{title:'Подтверждение удаления'}},title:`Удалить ${actionNames[kind]}: ${title}`,
      snapshot:()=>({confirmed:false}),values:form=>({confirmed:form.elements.namedItem('confirmed').checked}),
      markup:()=>`<dl class="project-details passport-wide"><div><dt>Проект</dt><dd>${esc(card.project.name)}</dd></div><div><dt>Тип</dt><dd>${names[kind]}</dd></div><div><dt>Название</dt><dd>${esc(title)}</dd></div></dl><p class="passport-wide">${passportDeletion?'Будет удалён только пустой архитектурный паспорт. Проект сохраняется. Классы данных и среды предварительно убираются явным редактированием, остальные зависимости — отдельными действиями.':kind==='integrations'?'Будут удалены выбранная интеграция и только её классификация данных. Классификация других интеграций и паспорта сохраняется.':kind==='checks'?'Будет удалена выбранная проверка. Набор станет неполным; автоматического восстановления и дополнения непустого набора нет.':'Будет удалена только выбранная запись. Остальные записи сохраняются.'}</p>${passportDeletion?'<section class="passport-wide" id="architecture-delete-impact" aria-live="polite">Проверяем зависимости…</section>':''}${tokenValid?'':'<p class="passport-wide">Исходные токены недоступны или версия не может быть безопасно передана. Загрузите актуальные сведения.</p>'}<label class="check-field passport-wide"><input type="checkbox" name="confirmed" aria-describedby="passport-error-confirmed">${passportDeletion?'Подтверждаю удаление только паспорта с сохранением проекта':'Подтверждаю удаление выбранной записи'}<small class="field-error" id="passport-error-confirmed"></small></label><div class="form-actions"><button type="submit" class="btn btn-primary btn-small">${passportDeletion?'Удалить паспорт':'Удалить запись'}</button><button type="button" class="btn btn-ghost btn-small" data-passport-cancel>Отмена</button></div>`,
      canSubmit:()=>!reused&&tokenValid&&(!passportDeletion||impact?.deletionAllowed===true),
      validate(values,error) {if(reused||!tokenValid||passportDeletion&&!impact?.deletionAllowed){error('confirmed','Удаление недоступно. Загрузите актуальные сведения и откройте новое подтверждение.');return false;}if(!values.confirmed){error('confirmed','Явно подтвердите удаление.');return false;}return true;},
      impactUrl:base+'/deletion-impact',validImpact,impactHTML,
      setImpact:value=>{impact=validImpact(value)?value:null;return impact!==null;},
      resetImpact:()=>{impact=null;},
      validCard,missing,
      validResult:value=>validCard(value)&&(passportDeletion?value.passport===null&&collections.every(name=>value[name].length===0)&&value.assessment.status==='NOT_ASSESSED'&&value.assessment.reasons.some(reason=>reason.code==='PASSPORT_MISSING'):value.passport?.instanceId===tokens.instanceId&&missing(value)),
      request:()=>{usedSnapshots.add(card);const query=`expectedVersion=${tokens.version}${passportDeletion?'':`&expectedPassportVersion=${tokens.passportVersion}`}&expectedPassportInstanceId=${encodeURIComponent(tokens.instanceId)}&confirmed=true`;return {url:base+(passportDeletion?'':`/${kind}/${encodeURIComponent(tokens.recordId)}`)+'?'+query,options:{method:'DELETE'}};},
      display:(_name,value)=>value?'Удаление подтверждено':'Удаление не подтверждено',
      comparisonHTML:value=>{const current=passportDeletion?value.passport:(value[kind]??[]).find(r=>r.id===tokens.recordId);const currentTitle=!current?'Ресурс отсутствует':passportDeletion?'Паспорт проекта':kind==='checks'?checkTypes[current.checkType]??'Неизвестный тип проверки':current.name;return `<dl class="project-details"><div><dt>Название в подтверждении</dt><dd>${esc(title)}</dd></div><div><dt>Актуальное название выбранного ресурса</dt><dd>${esc(currentTitle)}</dd></div></dl>`;},
      comparisonNote:value=>`Сейчас выбранный ресурс ${missing(value)?'отсутствует':'существует'}. Старое подтверждение повторно не используется. Откройте актуальные сведения и создайте новое подтверждение удаления.`};
  }
  window.PMArchitectureDeletionModel={model};
})();
