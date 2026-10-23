/* C.3b: field adapters for the shared guarded architecture editor. No persistence or assessment. */
(() => {
  const criticality={LOW:"Низкая",MEDIUM:"Средняя",HIGH:"Высокая",CRITICAL:"Критическая"};
  const dataClasses={PUBLIC:"Публичные",INTERNAL:"Внутренние",CONFIDENTIAL:"Конфиденциальные",PERSONAL:"Персональные",PAYMENT:"Платёжные",COMMERCIAL_SECRET:"Коммерческая тайна"};
  const component={
    name:{title:"Название",max:255},
    componentType:{title:"Тип",codes:{FRONTEND:"Клиентское приложение",BACKEND:"Серверное приложение",DATABASE:"База данных",QUEUE:"Очередь",EXTERNAL_SERVICE:"Внешний сервис",INFRASTRUCTURE:"Инфраструктура"}},
    purpose:{title:"Назначение",max:1000,nullable:true,multiline:true},platform:{title:"Платформа",max:255,nullable:true},
    criticality:{title:"Критичность",codes:criticality},externalComponent:{title:"Внешний компонент",boolean:true},position:{title:"Порядок",integer:true}
  };
  const integration={
    name:{title:"Название",max:255},sourceSystem:{title:"Источник",max:255},targetSystem:{title:"Получатель",max:255},
    direction:{title:"Направление",codes:{INBOUND:"Входящая",OUTBOUND:"Исходящая",BIDIRECTIONAL:"Двусторонняя"}},
    interactionType:{title:"Тип взаимодействия",codes:{API:"API",EVENT:"События",FILE_EXCHANGE:"Файловый обмен",BATCH:"Пакетное",OTHER:"Другое"}},
    authCategory:{title:"Аутентификация",codes:{NONE:"Без аутентификации",SERVICE_ACCOUNT:"Сервисная учётная запись",OAUTH2:"OAuth 2.0",MTLS:"mTLS",CERTIFICATE:"Сертификат",OTHER:"Другая"}},
    criticality:{title:"Критичность",codes:criticality},readiness:{title:"Готовность",codes:{PLANNED:"Запланирована",IN_PROGRESS:"В работе",READY:"Готова",BLOCKED:"Заблокирована"}},
    dataClasses:{title:"Передаваемые классы данных",codes:dataClasses,set:true},responsibleParty:{title:"Ответственная сторона",max:255,nullable:true},comment:{title:"Комментарий",max:1000,nullable:true,multiline:true}
  };
  const esc=v=>escapeHtml(String(v??""));
  const normalize=v=>String(v??"").replace(/\r\n?/g,"\n").normalize("NFC").trim() || null;
  function model(kind,card,recordId) {
    if(!card.passport || !["components","integrations"].includes(kind)) return null;
    const record=recordId == null ? null : (card[kind]??[]).find(row=>row.id===recordId);
    if(recordId != null && !record) return null;
    const fields=kind==="components"?component:integration;
    const noun=kind==="components"?"компонент":"интеграцию";
    function snapshot(aggregate) {
      const row=recordId == null ? null : (aggregate[kind]??[]).find(item=>item.id===recordId);
      return Object.fromEntries(Object.entries(fields).map(([name,f])=>[name,f.set?[...(row?.[name]??[])].sort():row?.[name]??(f.boolean?false:f.integer?0:f.nullable?null:"")]));
    }
    return {kind,record,create:!record,fields,snapshot,
      title:record?`Редактировать ${noun}: ${record.name}`:`Добавить ${noun}`,
      markup(original) {
        const error=name=>`<small class="field-error" id="passport-error-${name}"></small>`;
        return Object.entries(fields).map(([name,f])=>{
          const attrs=`name="${name}" aria-describedby="passport-error-${name}"`;
          const caption=esc(f.title)+(f.nullable?"":" *");
          if(f.set) return `<fieldset class="passport-set"><legend>${caption}</legend>${Object.entries(f.codes).map(([code,title])=>`<label class="check-field"><input type="checkbox" ${attrs} value="${code}" ${original[name].includes(code)?"checked":""}>${title}</label>`).join("")}${error(name)}</fieldset>`;
          if(f.boolean) return `<label class="check-field">${caption}<input type="checkbox" ${attrs} ${original[name]?"checked":""}>${error(name)}</label>`;
          const input=f.codes?`<select ${attrs} required><option value="">Выберите значение</option>${Object.entries(f.codes).map(([code,title])=>`<option value="${code}" ${original[name]===code?"selected":""}>${title}</option>`).join("")}</select>`
            :f.multiline?`<textarea ${attrs} maxlength="${f.max}">${esc(original[name])}</textarea>`
            :`<input ${attrs} ${f.integer?'type="number" min="0" max="2147483647" step="1"':`maxlength="${f.max}"`} ${f.nullable?"":"required"} value="${esc(original[name])}">`;
          return `<label class="${f.multiline?"passport-wide":""}">${caption}${input}${error(name)}</label>`;
        }).join("")+`<p class="passport-wide">${kind==="integrations"?"Выберите хотя бы один класс данных. ":""}Пустое необязательное поле очищается.</p><div class="form-actions"><button type="submit" class="btn btn-primary btn-small">Сохранить ${noun}</button><button type="button" class="btn btn-ghost btn-small" data-passport-cancel>Отмена</button></div>`;
      },
      values(form) {return Object.fromEntries(Object.entries(fields).map(([name,f])=>{const input=form.elements.namedItem(name);
        return [name,f.set?[...form.querySelectorAll(`input[name="${name}"]:checked`)].map(i=>i.value).sort():f.boolean?input.checked:f.integer?(input.value.trim()===""?null:Number(input.value)):normalize(input.value)];
      }));},
      validate(values,error) {let invalid=false;for(const [name,f] of Object.entries(fields)) {
        const value=values[name];let message="";
        if(f.set && (!value.length || value.some(code=>!Object.hasOwn(f.codes,code)))) message="Выберите хотя бы один класс данных.";
        else if(f.codes&&!f.set&&!Object.hasOwn(f.codes,value)) message="Выберите значение.";
        else if(f.integer&&(!Number.isInteger(value)||value<0||value>2147483647)) message="Укажите целое число от 0 до 2147483647.";
        else if(!f.nullable&&!f.codes&&!f.boolean&&!f.integer&&value===null) message="Заполните обязательное поле.";
        else if(f.max && value?.length>f.max) message=`Не более ${f.max} символов.`;
        if(message){error(name,message);invalid=true;}
      }return !invalid;},
      display(name,value) {const f=fields[name];if(value==null||value==="")return "Не указано";if(f.boolean)return value?"Да":"Нет";if(f.set)return value.length?value.map(code=>esc(f.codes[code]??"Неизвестное значение")).join(" · "):"Набор пуст";return esc(f.codes?.[value]??value).replace(/\n/g,"<br>");}
    };
  }
  window.PMArchitectureChildModel={model};
})();
