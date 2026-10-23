/* C.3d: security risk fields and merged-state validation. Level/assessment remain server projections. */
(() => {
  const {normalize,validDate}=window.PMArchitectureEditorValidation;
  const levels={LOW:"Низкая",MEDIUM:"Средняя",HIGH:"Высокая",CRITICAL:"Критическая"};
  const fields={
    name:{title:"Название",max:255},description:{title:"Описание",max:2000,nullable:true,multiline:true},
    category:{title:"Категория",codes:{ACCESS:"Доступ",DATA:"Данные",AVAILABILITY:"Доступность",INTEGRATION:"Интеграция",PROCESS:"Процесс",OTHER:"Другое"}},
    probability:{title:"Вероятность",codes:levels},impact:{title:"Влияние",codes:levels},owner:{title:"Владелец",max:255,nullable:true},
    treatmentDueDate:{title:"Срок обработки",date:true,nullable:true},treatmentPlan:{title:"План обработки",max:2000,nullable:true,multiline:true},
    status:{title:"Статус",codes:{OPEN:"Открыт",IN_PROGRESS:"В работе",ACCEPTED:"Принят",MITIGATED:"Снижен",CLOSED:"Закрыт"}},
    closedDate:{title:"Дата закрытия",date:true,nullable:true},acceptanceJustification:{title:"Обоснование принятия",max:2000,nullable:true,multiline:true}
  };
  const esc=value=>escapeHtml(String(value??""));
  const error=name=>`<small class="field-error" id="passport-error-${name}"></small>`;
  function model(card,recordId) {
    if(!card.passport)return null;
    const record=recordId===null?null:(card.risks??[]).find(row=>row.id===recordId);
    if(recordId!==null&&!record)return null;
    const snapshot=aggregate=>{const row=recordId===null?null:(aggregate.risks??[]).find(r=>r.id===recordId);return Object.fromEntries(Object.entries(fields).map(([name,f])=>[name,row?.[name]??(f.nullable?null:"")]));};
    return {kind:"risks",record,create:!record,fields,snapshot,title:record?`Редактировать риск: ${record.name}`:"Добавить риск ИБ",
      markup(original) {
        return Object.entries(fields).map(([name,f])=>{
          const attrs=`name="${name}" aria-describedby="passport-error-${name}"`,title=esc(f.title)+(f.nullable?"":" *");
          const input=f.codes?`<select ${attrs} required><option value="">Выберите значение</option>${Object.entries(f.codes).map(([code,label])=>`<option value="${code}" ${original[name]===code?'selected':''}>${label}</option>`).join('')}</select>`
            :f.multiline?`<textarea ${attrs} maxlength="${f.max}">${esc(original[name])}</textarea>`
            :`<input ${attrs} ${f.nullable?'':'required'} ${f.date?'placeholder="ГГГГ-ММ-ДД" maxlength="10"':`maxlength="${f.max}"`} value="${esc(original[name])}">`;
          return `<label class="${f.multiline?'passport-wide':''}">${title}${input}${error(name)}</label>`;
        }).join('')+'<p class="passport-wide">Для «Принят» обязательно обоснование. Принятие риска не удостоверяет согласование. Для «Закрыт» нужна дата закрытия; для остальных статусов её нужно явно очистить. Обоснование при смене статуса сохраняется. Даты вводятся как ГГГГ-ММ-ДД. Уровень риска и готовность рассчитывает сервер.</p><div class="form-actions"><button type="submit" class="btn btn-primary btn-small">Сохранить риск</button><button type="button" class="btn btn-ghost btn-small" data-passport-cancel>Отмена</button></div>';
      },
      values:form=>Object.fromEntries(Object.keys(fields).map(name=>[name,normalize(form.elements.namedItem(name).value)])),
      validate(values,showError) {
        let valid=true;const invalid=(name,message)=>{showError(name,message);valid=false;};
        for(const [name,f] of Object.entries(fields)){
          const value=values[name];
          if(f.codes&&!Object.hasOwn(f.codes,value))invalid(name,'Выберите допустимое значение.');
          else if(!f.nullable&&value===null)invalid(name,'Заполните обязательное поле.');
          if(f.max&&value?.length>f.max)invalid(name,`Не более ${f.max} символов.`);
          if(f.date&&!validDate(value))invalid(name,'Укажите существующую дату в формате ГГГГ-ММ-ДД.');
        }
        if(values.status==='ACCEPTED'&&!values.acceptanceJustification)invalid('acceptanceJustification','Для принятого риска укажите обоснование принятия.');
        if(values.status==='CLOSED'&&!values.closedDate)invalid('closedDate','Для закрытого риска укажите дату закрытия.');
        if(values.status!=='CLOSED'&&values.closedDate!==null)invalid('closedDate','Для этого статуса явно очистите дату закрытия.');
        return valid;
      },
      display:(name,value)=>value===null||value===''?'Не указано':esc(fields[name].codes?.[value]??value).replace(/\n/g,'<br>')};
  }
  window.PMArchitectureRiskModel={model};
})();
