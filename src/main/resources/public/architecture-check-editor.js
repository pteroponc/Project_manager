/* C.3c: explicit initialization confirmation and check field rules; no readiness calculation. */
(() => {
  const types={ARCHITECTURE_APPROVAL:"Согласование архитектуры",SECURITY_REQUIREMENTS:"Требования ИБ",DATA_CLASSIFICATION:"Классификация данных",THREAT_MODEL:"Модель угроз",SECURITY_ASSESSMENT:"Оценка безопасности",ACCESS_CONTROL:"Контроль доступа",LOGGING_MONITORING:"Журналирование и мониторинг",BACKUP_RECOVERY:"Резервное копирование и восстановление",LOAD_TESTING:"Нагрузочное тестирование",PRODUCTION_ADMISSION:"Допуск к промышленной эксплуатации"};
  const statuses={NOT_REQUIRED:"Не требуется",NOT_STARTED:"Не начата",IN_PROGRESS:"В работе",IN_REVIEW:"На рассмотрении",PASSED:"Пройдена",CHANGES_REQUIRED:"Требуются изменения",BLOCKED:"Заблокирована"};
  const fields={required:{title:"Обязательная проверка"},status:{title:"Статус"},owner:{title:"Владелец"},plannedDate:{title:"Плановая дата"},actualDate:{title:"Фактическая дата"},comment:{title:"Комментарий / обоснование"}};
  const esc=v=>escapeHtml(String(v??""));
  const normalize=v=>String(v??"").replace(/\r\n?/g,"\n").normalize("NFC").trim()||null;
  const error=name=>`<small class="field-error" id="passport-error-${name}"></small>`;
  const attrs=name=>`name="${name}" aria-describedby="passport-error-${name}"`;
  const actions=title=>`<div class="form-actions"><button type="submit" class="btn btn-primary btn-small">${title}</button><button type="button" class="btn btn-ghost btn-small" data-passport-cancel>Отмена</button></div>`;
  function validDate(value) {
    if(value===null)return true;
    if(!/^\d{4}-\d{2}-\d{2}$/.test(value))return false;
    const [y,m,d]=value.split('-').map(Number),leap=y%4===0&&(y%100!==0||y%400===0);
    return m>=1&&m<=12&&d>=1&&d<=[31,leap?29:28,31,30,31,30,31,31,30,31,30,31][m-1];
  }
  function model(card,recordId) {
    if(!card.passport)return null;
    if(recordId===null) {
      if((card.checks??[]).length)return null;
      return {kind:"checks",record:null,create:true,initialize:true,focus:"confirmed",fields:{confirmed:{title:"Подтверждение создания набора"}},title:"Создать набор проверок",
        snapshot:()=>({confirmed:false}),values:form=>({confirmed:form.elements.namedItem('confirmed').checked}),
        markup:()=>`<p class="passport-wide">Проект: ${esc(card.project.name)}. Будут созданы ровно десять обязательных проверок со статусом «Не начата», без владельцев, дат и комментариев.</p><ul class="passport-wide">${Object.values(types).map(title=>`<li>${title}</li>`).join('')}</ul><p class="passport-wide">Просмотр ничего не создаёт. Непустой, в том числе неполный, набор не дополняется. Это действие не является официальным допуском.</p><label class="check-field passport-wide"><input type="checkbox" ${attrs('confirmed')}>Подтверждаю создание десяти проверок для этого паспорта${error('confirmed')}</label>${actions('Создать десять проверок')}`,
        validate:(values,showError)=>{if(!values.confirmed){showError('confirmed','Подтвердите создание десяти проверок.');return false;}return true;},
        display:(_name,value)=>value?"Создание подтверждено":"Создание не подтверждено",
        comparisonNote:loaded=>`Сейчас проверок: ${(loaded.checks??[]).length}. Любой непустой набор исключает инициализацию. Для другого экземпляра или версии паспорта требуется новое подтверждение.`};
    }
    const record=(card.checks??[]).find(row=>row.id===recordId);
    if(!record)return null;
    const snapshot=aggregate=>{const row=(aggregate.checks??[]).find(item=>item.id===recordId);return {required:row?.required??true,status:row?.status??"",owner:row?.owner??null,plannedDate:row?.plannedDate??null,actualDate:row?.actualDate??null,comment:row?.comment??null};};
    return {kind:"checks",record,create:false,focus:"required",fields,snapshot,title:`Редактировать проверку: ${types[record.checkType]??"Неизвестный тип"}`,
      markup:original=>`<p class="passport-wide">Тип проверки: ${esc(types[record.checkType]??"Неизвестный тип")}. Тип не изменяется.</p><label class="check-field"><input type="checkbox" ${attrs('required')} ${original.required?'checked':''}>Обязательная проверка${error('required')}</label><label>Статус *<select ${attrs('status')} required>${Object.entries(statuses).map(([code,title])=>`<option value="${code}" ${original.status===code?'selected':''}>${title}</option>`).join('')}</select>${error('status')}</label><label>Владелец<input ${attrs('owner')} maxlength="255" value="${esc(original.owner)}">${error('owner')}</label>${['plannedDate','actualDate'].map(name=>`<label>${fields[name].title}<input ${attrs(name)} placeholder="ГГГГ-ММ-ДД" maxlength="10" value="${esc(original[name])}">${error(name)}</label>`).join('')}<label class="passport-wide">Комментарий / обоснование<textarea ${attrs('comment')} maxlength="2000">${esc(original.comment)}</textarea>${error('comment')}</label><p class="passport-wide">Для «Пройдена» нужна фактическая дата. Для остальных статусов её нужно явно очистить. Необязательная проверка требует статуса «Не требуется» и обоснования. Даты вводятся как ГГГГ-ММ-ДД. Значения автоматически не исправляются.</p>${actions('Сохранить проверку')}`,
      values:form=>({required:form.elements.namedItem('required').checked,...Object.fromEntries(['status','owner','plannedDate','actualDate','comment'].map(name=>[name,normalize(form.elements.namedItem(name).value)]))}),
      validate(values,showError) {
        let valid=true;const invalid=(name,message)=>{showError(name,message);valid=false;};
        if(!Object.hasOwn(statuses,values.status))invalid('status','Выберите допустимый статус.');
        if(values.required&&values.status==='NOT_REQUIRED')invalid('status','Обязательная проверка не может иметь статус «Не требуется».');
        if(!values.required){if(values.status!=='NOT_REQUIRED')invalid('status','Для необязательной проверки выберите «Не требуется».');if(!values.comment)invalid('comment','Укажите обоснование неприменимости.');}
        if(values.status==='PASSED'&&!values.actualDate)invalid('actualDate','Для пройденной проверки укажите фактическую дату.');
        if(values.status!=='PASSED'&&values.actualDate!==null)invalid('actualDate','Для этого статуса явно очистите фактическую дату.');
        for(const name of ['plannedDate','actualDate'])if(!validDate(values[name]))invalid(name,'Укажите существующую дату в формате ГГГГ-ММ-ДД.');
        if(values.owner?.length>255)invalid('owner','Не более 255 символов.');if(values.comment?.length>2000)invalid('comment','Не более 2000 символов.');
        return valid;
      },
      display:(name,value)=>value===null||value===''?"Не указано":name==='required'?value?"Да":"Нет":esc(name==='status'?statuses[value]??"Неизвестный статус":value).replace(/\n/g,'<br>')};
  }
  window.PMArchitectureCheckModel={model};
})();
