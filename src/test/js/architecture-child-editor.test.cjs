const {test}=require('node:test');
const assert=require('node:assert/strict');
const {setup}=require('./architecture-editor-harness.cjs');
const {childArchitecture,missingArchitecture}=require('./fixtures/architecture.cjs');
function start(h,kind,id=null,card=childArchitecture()) {h.api.startChild(card,kind,id,result=>h.accepted.push(result));return card;}
function success(h,data=childArchitecture()) {h.context.fetchJson=async(url,options)=>{h.calls.push({url,options});return data;};}
function failure(h,status,code,fieldErrors) {h.context.fetchJson=async(url,options)=>{h.calls.push({url,options});throw Object.assign(Error('SQL private stack'),{status,code,fieldErrors});};}
function fill(h,kind) {
  h.set('name','Новая запись');h.set('criticality','HIGH');
  if(kind==='components')h.set('componentType','BACKEND');
  else {for(const [k,v] of Object.entries({sourceSystem:'Источник',targetSystem:'Получатель',direction:'OUTBOUND',interactionType:'API',authCategory:'NONE',readiness:'PLANNED'}))h.set(k,v);h.field('dataClasses').find(i=>i.value==='INTERNAL').checked=true;}
}
for(const kind of ['components','integrations']) {
  test(`${kind}: POST sends exact original passport tokens, required fields and full response with no GET`,async()=>{
    const h=setup(),card=start(h,kind);fill(h,kind);const before=JSON.stringify(card);success(h);await h.save();
    const fields=kind==='components'?{name:'Новая запись',componentType:'BACKEND',criticality:'HIGH',externalComponent:false,position:0}
      :{name:'Новая запись',sourceSystem:'Источник',targetSystem:'Получатель',direction:'OUTBOUND',interactionType:'API',authCategory:'NONE',criticality:'HIGH',readiness:'PLANNED',dataClasses:['INTERNAL']};
    assert.deepEqual(h.body(),{expectedPassportVersion:card.passport.version,expectedPassportInstanceId:card.passport.instanceId,...fields});
    assert.equal(h.calls[0].url,`/api/projects/p1/architecture/${kind}`);assert.equal(h.calls[0].options.method,'POST');assert.equal(h.calls.length,1);assert.equal(h.accepted.length,1);assert.equal(JSON.stringify(card),before);
  });
  test(`${kind}: UUID selection survives reordering and opens both records with their names and versions`,async()=>{
    for(const index of [0,1]) {const h=setup(),card=childArchitecture(),record=card[kind][index];card[kind].reverse();start(h,kind,record.id,card);
      assert.equal(h.field('name').value,record.name);assert.match(h.get('passport-editor-title').textContent,new RegExp(record.name));
      h.set('name','Изменено');success(h);await h.save();assert.equal(h.calls[0].url,`/api/projects/p1/architecture/${kind}/${record.id}`);
      assert.deepEqual(h.body(),{expectedVersion:record.version,name:'Изменено'});assert.equal(h.calls[0].options.method,'PATCH');}
  });
  test(`${kind}: unchanged PATCH sends nothing and missing/foreign UUID cannot open an editor`,async()=>{
    const h=setup(),card=childArchitecture();start(h,kind,card[kind][0].id,card);await h.save();assert.equal(h.calls.length,0);assert.match(h.get('passport-write-message').textContent,/Изменений нет/);h.discard();
    start(h,kind,'a-foreign-id',card);assert.equal(h.get('passport-editor').classList.contains('hidden'),true);start(h,kind,null,missingArchitecture('p1'));assert.equal(h.get('passport-editor').classList.contains('hidden'),true);
  });
  test(`${kind}: required fields and closed enums reject before sending`,async()=>{
    const h=setup();start(h,kind);success(h);await h.save();assert.equal(h.calls.length,0);assert.match(h.get('passport-error-name').textContent,/обязательное/);
    fill(h,kind);h.set('criticality','INVALID');await h.save();assert.equal(h.calls.length,0);
    h.set('criticality','LOW');h.set('name','x'.repeat(256));await h.save();assert.equal(h.calls.length,0);assert.match(h.get('passport-error-name').textContent,/255/);
  });
  test(`${kind}: 400 retains text and selections and shows safe errors beside fields`,async()=>{
    const h=setup();start(h,kind);fill(h,kind);failure(h,400,'VALIDATION_ERROR',{name:'Internal SQL'});await h.save();
    assert.equal(h.field('name').value,'Новая запись');assert.equal(h.field('criticality').value,'HIGH');assert.match(h.get('passport-error-name').textContent,/Проверьте/);assert.doesNotMatch(h.get('passport-write-message').textContent,/SQL|private/);assert.equal(h.form.querySelector('[type="submit"]').disabled,false);
  });
  test(`${kind}: concurrent edits to different records use only each record version even after passport changes`,async()=>{
    const a=setup(),b=setup(),card=childArchitecture();start(a,kind,card[kind][0].id,card);start(b,kind,card[kind][1].id,card);
    a.set('name','Первый');b.set('name','Второй');const returned=childArchitecture();returned.passport.version++;
    success(a,returned);success(b,returned);await Promise.all([a.save(),b.save()]);
    assert.deepEqual(a.body(),{expectedVersion:card[kind][0].version,name:'Первый'});assert.deepEqual(b.body(),{expectedVersion:card[kind][1].version,name:'Второй'});assert.equal(a.accepted.length,1);assert.equal(b.accepted.length,1);
  });
  test(`${kind}: record/passport conflicts retain original tokens and comparison does not rebase`,async()=>{
    for(const create of [false,true]) {const h=setup(),card=childArchitecture();start(h,kind,create?null:card[kind][0].id,card);if(create)fill(h,kind);else h.set('name','Мой ввод');
      failure(h,409,create?'PASSPORT_VERSION_CONFLICT':kind==='components'?'COMPONENT_VERSION_CONFLICT':'INTEGRATION_VERSION_CONFLICT');await h.save();const original=h.body();
      const latest=childArchitecture();latest.passport.version+=10;latest[kind][0].version+=10;latest[kind][0].name='Чужой ввод';h.context.fetchJson=async(url,options)=>{h.calls.push({url,options});return latest;};
      await h.get('passport-compare').listeners.click();await h.save();assert.equal(h.calls.filter(c=>c.options).length,1);assert.deepEqual(JSON.parse(h.calls[0].options.body),original);assert.match(h.get('passport-comparison').innerHTML,/Исходные токены/);assert.equal(h.field('name').value,create?'Новая запись':'Мой ввод');
    }
  });
  test(`${kind}: deleted record differs from recreated passport and stale form never writes again`,async()=>{
    for(const recreated of [false,true]) {const h=setup(),card=childArchitecture();start(h,kind,card[kind][0].id,card);h.set('name','Не потерять');failure(h,404,kind==='components'?'COMPONENT_NOT_FOUND':'INTEGRATION_NOT_FOUND');await h.save();assert.match(h.get('passport-write-message').textContent,/запись удалена/);
      const latest=childArchitecture();latest[kind]=[];if(recreated)latest.passport.instanceId='22c1bf4a-ceaa-4f7b-9b26-5a3a0d7245a3';
      h.context.fetchJson=async()=>latest;await h.get('passport-compare').listeners.click();assert.match(h.get('passport-comparison').innerHTML,recreated?/новый экземпляр/:/запись удалена/);await h.save();assert.equal(h.calls.length,1);assert.equal(h.field('name').value,'Не потерять');
    }
  });
  test(`${kind}: recreate conflicts on POST keep instance token, network failures are unknown and never retried`,async()=>{
    for(const conflict of [true,false]){const h=setup(),card=start(h,kind);fill(h,kind);failure(h,conflict?409:undefined,conflict?'PASSPORT_INSTANCE_CONFLICT':undefined);await h.save();await h.save();assert.equal(h.calls.length,1);assert.equal(h.body().expectedPassportInstanceId,card.passport.instanceId);assert.match(h.get('passport-write-message').textContent,conflict?/создан заново/:/мог сохранить/);assert.equal(h.field('name').value,'Новая запись');}
  });
  test(`${kind}: double click sends one request; discard protects a new form from late success/failure`,async()=>{
    for(const fail of [false,true]) {const h=setup();start(h,kind);fill(h,kind);let finish,reject;h.context.fetchJson=(url,options)=>{h.calls.push({url,options});return new Promise((r,j)=>{finish=r;reject=j;});};const pending=h.save();await h.save();assert.equal(h.calls.length,1);
      h.discard();start(h,kind);h.set('name','Новая форма');if(fail)reject(Object.assign(Error('late'),{status:400,fieldErrors:{name:'Invalid'}}));else finish(childArchitecture());await pending;assert.equal(h.accepted.length,0);assert.equal(h.field('name').value,'Новая форма');assert.equal(h.get('passport-write-message').textContent,'');
    }
  });
  test(`${kind}: navigation and editor switching retain unsaved input until explicit discard`,()=>{
    const h=setup(),card=childArchitecture();start(h,kind);fill(h,kind);h.start(card);assert.equal(h.get('passport-leave-dialog').open,true);h.get('passport-stay').listeners.click();assert.equal(h.field('name').value,'Новая запись');
    h.start(card);h.get('passport-discard').listeners.click();assert.equal(h.field('architect').value,card.passport.architect);
    h.set('architect','Несохранено');start(h,kind);assert.equal(h.get('passport-leave-dialog').open,true);h.get('passport-discard').listeners.click();assert.equal(h.field('name').value,'');
  });
  test(`${kind}: late comparison cannot replace another form and comparison escapes text`,async()=>{
    const h=setup(),card=childArchitecture();start(h,kind,card[kind][0].id,card);h.set('name','<b>Мой</b>');failure(h,409,'VERSION_CONFLICT');await h.save();let finish;h.context.fetchJson=()=>new Promise(r=>{finish=r;});const pending=h.get('passport-compare').listeners.click();h.discard();start(h,kind);finish(card);await pending;assert.equal(h.get('passport-comparison').innerHTML,'');
    h.discard();start(h,kind,card[kind][0].id,card);h.set('name','<b>Мой</b>');failure(h,409);await h.save();const latest=childArchitecture();latest[kind][0].name='<script>Чужой</script>';h.context.fetchJson=async()=>latest;await h.get('passport-compare').listeners.click();assert.doesNotMatch(h.get('passport-comparison').innerHTML,/<b>|<script>/);assert.match(h.get('passport-comparison').innerHTML,/&lt;script&gt;/);
  });
}
test('component PATCH clears nullable values, preserves false and position=0, and never includes passport tokens',async()=>{
  const h=setup(),card=childArchitecture(),row=card.components[1];start(h,'components',row.id,card);h.set('purpose','');h.set('platform','');h.field('externalComponent').checked=false;h.set('position','0');success(h);await h.save();
  assert.deepEqual(h.body(),{expectedVersion:12,purpose:null,platform:null,externalComponent:false,position:0});
});
test('component order accepts only bounded integers, including zero',async()=>{
  for(const value of ['','-1','1.2','2147483648','not-a-number']){const h=setup();start(h,'components');fill(h,'components');h.set('position',value);success(h);await h.save();assert.equal(h.calls.length,0);assert.match(h.get('passport-error-position').textContent,/целое число/);}
});
test('integration PATCH replaces classes entirely and clears nullable fields; empty classification never sends',async()=>{
  const h=setup(),card=childArchitecture();start(h,'integrations',card.integrations[1].id,card);h.set('responsibleParty','');h.set('comment','');h.field('dataClasses').forEach(i=>{i.checked=false;});success(h);await h.save();assert.equal(h.calls.length,0);assert.match(h.get('passport-error-dataClasses').textContent,/хотя бы один/);
  h.field('dataClasses').find(i=>i.value==='PUBLIC').checked=true;await h.save();assert.deepEqual(h.body(),{expectedVersion:17,dataClasses:['PUBLIC'],responsibleParty:null,comment:null});
});
test('reordering integration classifications is not a change',async()=>{
  const h=setup(),card=childArchitecture();card.integrations[1].dataClasses.reverse();start(h,'integrations',card.integrations[1].id,card);await h.save();assert.equal(h.calls.length,0);
});
test('POST binds original passport tokens and PATCH binds original record version at form opening',async()=>{
  for(const kind of ['components','integrations'])for(const create of [true,false]){
    const h=setup(),card=childArchitecture(),originalVersion=create?card.passport.version:card[kind][0].version,originalInstance=card.passport.instanceId;
    start(h,kind,create?null:card[kind][0].id,card);if(create)fill(h,kind);else h.set('name','Правка');
    card.passport.version++;card.passport.instanceId='22c1bf4a-ceaa-4f7b-9b26-5a3a0d7245a3';card[kind][0].version++;
    success(h);await h.save();assert.equal(h.body()[create?'expectedPassportVersion':'expectedVersion'],originalVersion);
    if(create)assert.equal(h.body().expectedPassportInstanceId,originalInstance);else assert.equal(Object.hasOwn(h.body(),'expectedPassportInstanceId'),false);
  }
});
test('unknown PATCH result, unsafe versions and missing POST instance never trigger an automatic retry',async()=>{
  for(const kind of ['components','integrations']){
    const h=setup(),card=childArchitecture();start(h,kind,card[kind][0].id,card);h.set('name','Правка');failure(h,undefined);await h.save();await h.save();assert.equal(h.calls.length,1);assert.match(h.get('passport-write-message').textContent,/мог сохранить/);
    h.discard();card[kind][0].version=9007199254740992;start(h,kind,card[kind][0].id,card);h.set('name','Правка');await h.save();assert.equal(h.calls.length,1);assert.match(h.get('passport-write-message').textContent,/безопасно/);
    h.discard();card.passport.instanceId=null;start(h,kind,null,card);fill(h,kind);await h.save();assert.equal(h.calls.length,1);assert.match(h.get('passport-write-message').textContent,/экземпляра/);
  }
});
