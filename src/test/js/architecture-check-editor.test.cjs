const {test}=require('node:test');
const assert=require('node:assert/strict');
const {setup}=require('./architecture-editor-harness.cjs');
const {checkArchitecture,missingArchitecture,checkTypes}=require('./fixtures/architecture.cjs');
function empty(){return {...checkArchitecture(),checks:[]};}
function start(h,card=checkArchitecture(),id=card.checks[0]?.id??null){h.api.startChild(card,'checks',id,result=>h.accepted.push(result));return card;}
function success(h,result=checkArchitecture()){h.context.fetchJson=async(url,options)=>{h.calls.push({url,options});return result;};}
function failure(h,status,code,fieldErrors){h.context.fetchJson=async(url,options)=>{h.calls.push({url,options});throw Object.assign(Error('SQL secret path'),{status,code,fieldErrors});};}
function confirm(h){h.field('confirmed').checked=true;h.form.listeners.change();}
test('initialize requires explicit confirmation, sends exactly original two tokens, applies ten server types without GET',async()=>{
  const h=setup(),card=start(h,empty()),before=JSON.stringify(card);assert.equal(h.calls.length,0);success(h);await h.save();assert.equal(h.calls.length,0);assert.match(h.get('passport-error-confirmed').textContent,/Подтвердите/);
  const result=checkArchitecture();result.checks=result.checks.map(row=>({...row,version:0,required:true,status:'NOT_STARTED',owner:null,plannedDate:null,actualDate:null,comment:null}));result.passport.version++;
  success(h,result);confirm(h);await h.save();assert.equal(h.calls.length,1);assert.equal(h.calls[0].url,'/api/projects/p1/architecture/checks/initialize');assert.equal(h.calls[0].options.method,'POST');
  assert.deepEqual(h.body(),{expectedPassportVersion:card.passport.version,expectedPassportInstanceId:card.passport.instanceId});assert.equal(h.accepted[0],result);assert.equal(h.accepted[0].checks.length,10);assert.deepEqual(new Set(result.checks.map(r=>r.checkType)),new Set(checkTypes));assert.equal(JSON.stringify(card),before);
});
test('initialize never opens for any nonempty set or missing passport; no automatic request',()=>{
  for(const count of [1,5,10]){const h=setup(),card=checkArchitecture();card.checks=card.checks.slice(0,count);h.api.startChild(card,'checks',null,()=>{});assert.equal(h.calls.length,0);assert.equal(h.form.innerHTML,'');}
  const h=setup();h.api.startChild(missingArchitecture('p1'),'checks',null,()=>{});assert.equal(h.form.innerHTML,'');
});
test('initialize confirmation stays bound to original project, passport instance and version',async()=>{
  const h=setup(),card=start(h,empty()),instance=card.passport.instanceId,version=card.passport.version;confirm(h);
  card.passport.version++;card.passport.instanceId='22c1bf4a-ceaa-4f7b-9b26-5a3a0d7245a3';card.project.id='p2';failure(h,409,'PASSPORT_INSTANCE_CONFLICT');await h.save();assert.deepEqual(h.body(),{expectedPassportVersion:version,expectedPassportInstanceId:instance});assert.equal(h.calls[0].url,'/api/projects/p1/architecture/checks/initialize');assert.match(h.get('passport-write-message').textContent,/создан заново/);await h.save();assert.equal(h.calls.length,1);
});
test('initialize handles passport version/existing-set conflicts without rebasing or completing partial sets',async()=>{
  for(const code of ['PASSPORT_VERSION_CONFLICT','CHECKS_ALREADY_EXIST']){const h=setup();start(h,empty());confirm(h);failure(h,409,code);await h.save();const original=h.body(),latest=checkArchitecture();latest.checks=latest.checks.slice(0,1);latest.passport.version++;
    h.context.fetchJson=async(url,options)=>{h.calls.push({url,options});return latest;};await h.get('passport-compare').listeners.click();await h.save();assert.equal(h.calls.filter(c=>c.options).length,1);assert.deepEqual(JSON.parse(h.calls[0].options.body),original);assert.match(h.get('passport-comparison').innerHTML,/Сейчас проверок: 1/);assert.match(h.get('passport-comparison').innerHTML,/Исходные токены/);assert.equal(h.field('confirmed').checked,true);
  }
});
test('initialization double click sends once; network outcome is unknown and never retried',async()=>{
  const h=setup();start(h,empty());confirm(h);let reject;h.context.fetchJson=(url,options)=>{h.calls.push({url,options});return new Promise((_r,j)=>{reject=j;});};const pending=h.save();await h.save();assert.equal(h.calls.length,1);reject(Error('offline'));await pending;await h.save();assert.equal(h.calls.length,1);assert.match(h.get('passport-write-message').textContent,/мог сохранить/);assert.equal(h.field('confirmed').checked,true);
});
test('UUID selection survives reorder, type is immutable, first and last checks use own versions',async()=>{
  for(const index of [0,9]){const h=setup(),card=checkArchitecture(),row=card.checks[index];card.checks.reverse();start(h,card,row.id);h.set('owner','Новый владелец');success(h);await h.save();
    assert.equal(h.calls[0].url,`/api/projects/p1/architecture/checks/${row.id}`);assert.equal(h.calls[0].options.method,'PATCH');assert.deepEqual(h.body(),{expectedVersion:row.version,owner:'Новый владелец'});assert.equal(h.field('checkType'),undefined);assert.match(h.get('passport-editor-title').textContent,index===0?/Согласование архитектуры/:/Допуск к промышленной/);assert.equal(h.calls.length,1);
  }
});
test('unchanged PATCH sends no request and leaves DTO completely unchanged',async()=>{
  const h=setup(),card=checkArchitecture(),before=JSON.stringify(card);start(h,card);await h.save();assert.equal(h.calls.length,0);assert.equal(JSON.stringify(card),before);assert.match(h.get('passport-write-message').textContent,/Изменений нет/);
});
test('PATCH clears nullable fields, sends false and full explicit not-required transition without passport tokens',async()=>{
  const h=setup(),card=checkArchitecture();start(h,card);h.field('required').checked=false;h.set('status','NOT_REQUIRED');h.set('owner','');h.set('plannedDate','');h.set('actualDate','');h.set('comment','  Проверка неприменима\nВ согласованном объёме  ');success(h);await h.save();
  assert.deepEqual(h.body(),{expectedVersion:981241,required:false,status:'NOT_REQUIRED',owner:null,plannedDate:null,actualDate:null,comment:'Проверка неприменима\nВ согласованном объёме'});
});
test('merged state rules: every status and required combination, actual date and justification',async()=>{
  const statuses=['NOT_REQUIRED','NOT_STARTED','IN_PROGRESS','IN_REVIEW','PASSED','CHANGES_REQUIRED','BLOCKED'];
  for(const required of [true,false])for(const status of statuses)for(const actual of [null,'2026-10-24'])for(const comment of [null,'Обоснование']){
    const h=setup(),card=checkArchitecture();start(h,card);h.field('required').checked=required;h.set('status',status);h.set('actualDate',actual??'');h.set('comment',comment??'');h.set('owner','Правка');success(h);await h.save();
    const valid=(required?status!=='NOT_REQUIRED':status==='NOT_REQUIRED'&&comment!==null)&&(status==='PASSED'?actual!==null:actual===null);
    assert.equal(h.calls.length,valid?1:0,JSON.stringify({required,status,actual,comment}));
    if(!valid){assert.equal(h.field('status').value,status);assert.equal(h.field('actualDate').value,actual??'');assert.equal(h.field('required').checked,required);}
  }
});
test('required=false or PASSED/BLOCKED transitions do not silently change status, date or comment',async()=>{
  const h=setup();start(h);h.field('required').checked=false;success(h);await h.save();assert.equal(h.calls.length,0);assert.equal(h.field('status').value,'PASSED');assert.equal(h.field('actualDate').value,'2026-10-20');assert.equal(h.field('comment').value,'');
  h.field('required').checked=true;h.set('status','BLOCKED');await h.save();assert.equal(h.calls.length,0);assert.match(h.get('passport-error-actualDate').textContent,/явно очистите/);
  h.set('actualDate','');await h.save();assert.deepEqual(h.body(),{expectedVersion:981241,status:'BLOCKED',actualDate:null});
});
test('calendar validation preserves invalid input and accepts leap days, past/future and year zero without today constraints',async()=>{
  for(const [date,valid] of [['2026-02-29',false],['2024-02-29',true],['1900-02-29',false],['2000-02-29',true],['2026-13-01',false],['2026-04-31',false],['2026-1-01',false],['2026-01-00',false],['0000-02-29',true],['9999-12-31',true],['2026-10-24',true]]){
    const h=setup();start(h);h.set('plannedDate',date);success(h);await h.save();assert.equal(h.calls.length,valid?1:0,date);if(!valid)assert.equal(h.field('plannedDate').value,date);
  }
});
test('PATCH uses original check version, not passport version; separate records remain independent',async()=>{
  const card=checkArchitecture(),a=setup(),b=setup();card.checks[9].version=6;start(a,card,card.checks[0].id);start(b,card,card.checks[9].id);card.passport.version++;card.checks[0].version++;a.set('owner','Первый');b.set('owner','Второй');success(a);success(b);await Promise.all([a.save(),b.save()]);assert.deepEqual(a.body(),{expectedVersion:981241,owner:'Первый'});assert.deepEqual(b.body(),{expectedVersion:6,owner:'Второй'});
});
test('400 retains complete input and safely attaches field errors; lengths and enum reject locally',async()=>{
  const h=setup();start(h);h.set('owner','Мой владелец');h.set('comment','Мой комментарий');failure(h,400,'VALIDATION_ERROR',{owner:'SQL'});await h.save();assert.match(h.get('passport-error-owner').textContent,/Проверьте/);assert.equal(h.field('comment').value,'Мой комментарий');assert.doesNotMatch(h.get('passport-write-message').textContent,/SQL|secret|path/);
  for(const [name,value] of [['owner','x'.repeat(256)],['comment','x'.repeat(2001)],['status','OTHER']]){const n=setup();start(n);n.set(name,value);success(n);await n.save();assert.equal(n.calls.length,0);}
});
test('check version conflict preserves input and original version after comparison; removed check and recreate differ',async()=>{
  for(const mode of ['version','deleted','recreated']){const h=setup(),card=start(h),id=card.checks[0].id;h.set('owner','Не потерять');failure(h,mode==='version'?409:404,mode==='version'?'CHECK_VERSION_CONFLICT':'CHECK_NOT_FOUND');await h.save();
    const latest=checkArchitecture();latest.checks[0].version++;latest.checks[0].owner='Чужой владелец';if(mode!=='version')latest.checks=latest.checks.filter(r=>r.id!==id);if(mode==='recreated')latest.passport.instanceId='22c1bf4a-ceaa-4f7b-9b26-5a3a0d7245a3';h.context.fetchJson=async()=>latest;await h.get('passport-compare').listeners.click();
    assert.match(h.get('passport-comparison').innerHTML,mode==='version'?/Чужой владелец/:mode==='deleted'?/запись удалена/:/новый экземпляр/);await h.save();assert.equal(h.calls.length,1);assert.equal(h.field('owner').value,'Не потерять');assert.equal(h.body().expectedVersion,981241);
  }
});
test('initialize and PATCH guard navigation and ignore late success/failure/comparison for new forms and projects',async()=>{
  for(const initialize of [true,false])for(const fail of [true,false]){const h=setup();start(h,initialize?empty():checkArchitecture());if(initialize)confirm(h);else h.set('owner','Мой ввод');let finish,reject;h.context.fetchJson=()=>new Promise((r,j)=>{finish=r;reject=j;});const pending=h.save();h.discard();h.start(missingArchitecture('p2'));h.set('architect','Новая форма');if(fail)reject(Object.assign(Error('late'),{status:400,fieldErrors:{owner:'Invalid'}}));else finish(checkArchitecture());await pending;assert.equal(h.field('architect').value,'Новая форма');assert.equal(h.accepted.length,0);assert.equal(h.get('passport-write-message').textContent,'');}
  const h=setup();start(h);h.set('owner','Сохранить');let action;assert.equal(h.api.allowNavigation(()=>{action=true;}),false);h.get('passport-stay').listeners.click();assert.equal(action,undefined);assert.equal(h.field('owner').value,'Сохранить');failure(h,409);await h.save();let finish;h.context.fetchJson=()=>new Promise(r=>{finish=r;});const pending=h.get('passport-compare').listeners.click();h.discard();start(h,empty());finish(checkArchitecture());await pending;assert.equal(h.get('passport-comparison').innerHTML,'');
});
test('server readiness, all reasons and officialGoLiveApproval are consumed unmodified after PATCH',async()=>{
  for(const status of ['PASSED','NOT_REQUIRED','BLOCKED','CHANGES_REQUIRED']){const h=setup(),card=checkArchitecture();start(h,card,card.checks[9].id);h.set('status',status);h.set('actualDate',status==='PASSED'?'2026-10-24':'');if(status==='NOT_REQUIRED'){h.field('required').checked=false;h.set('comment','Вне объёма');}h.set('owner','Правка');const result=checkArchitecture();result.assessment.status='READY';result.assessment.statusLabel='Тестовая подпись сервера';success(h,result);await h.save();assert.equal(h.accepted[0],result);assert.equal(h.accepted[0].assessment.officialGoLiveApproval,false);assert.equal(h.calls.length,1);}
});
test('initialization confirmation does not transfer to another project or passport editor',()=>{
  const h=setup();start(h,empty());confirm(h);h.start(missingArchitecture('p2'));assert.equal(h.get('passport-leave-dialog').open,true);h.get('passport-stay').listeners.click();assert.equal(h.field('confirmed').checked,true);
  h.start(missingArchitecture('p2'));h.get('passport-discard').listeners.click();assert.equal(h.field('confirmed'),undefined);h.discard();start(h,{...empty(),project:{...empty().project,id:'p2'}});assert.equal(h.field('confirmed').checked,false);
});
test('PATCH double click and network failure retain input, do not retry and warn before unload',async()=>{
  const h=setup();start(h);h.set('owner','Мой владелец');let reject;h.context.fetchJson=(url,options)=>{h.calls.push({url,options});return new Promise((_r,j)=>{reject=j;});};const pending=h.save();await h.save();assert.equal(h.calls.length,1);reject(Error('network'));await pending;await h.save();assert.equal(h.calls.length,1);assert.equal(h.field('owner').value,'Мой владелец');let warned=false;h.events.beforeunload({preventDefault(){warned=true;}});assert.equal(warned,true);assert.match(h.get('passport-write-message').textContent,/мог сохранить/);
});
test('check form and conflict comparison escape server and draft text without mutating the input aggregate',async()=>{
  const h=setup(),card=checkArchitecture();card.checks[0].owner='<script>Сервер</script>';const before=JSON.stringify(card);start(h,card);assert.doesNotMatch(h.form.innerHTML,/<script>/);assert.match(h.form.innerHTML,/&lt;script&gt;/);h.set('comment','<b>Мой ввод</b>');failure(h,409,'CHECK_VERSION_CONFLICT');await h.save();h.context.fetchJson=async()=>card;await h.get('passport-compare').listeners.click();assert.doesNotMatch(h.get('passport-comparison').innerHTML,/<script>|<b>/);assert.match(h.get('passport-comparison').innerHTML,/&lt;b&gt;/);assert.equal(JSON.stringify(card),before);
});
