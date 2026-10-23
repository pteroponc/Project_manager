const {test}=require('node:test');
const assert=require('node:assert/strict');
const {setup}=require('./architecture-editor-harness.cjs');
const {riskArchitecture,missingArchitecture}=require('./fixtures/architecture.cjs');
function start(h,card=riskArchitecture(),id=card.risks[0]?.id??null){h.api.startChild(card,'risks',id,result=>h.accepted.push(result));return card;}
function success(h,result=riskArchitecture()){h.context.fetchJson=async(url,options)=>{h.calls.push({url,options});return result;};}
function failure(h,status,code,fieldErrors){h.context.fetchJson=async(url,options)=>{h.calls.push({url,options});throw Object.assign(Error('SQL private Java path'),{status,code,fieldErrors});};}
function fill(h){for(const [name,value] of Object.entries({name:'Новый риск',category:'DATA',probability:'MEDIUM',impact:'HIGH',status:'OPEN'}))h.set(name,value);}
test('minimal POST sends only five mandatory fields and original passport tokens; full aggregate is applied without GET',async()=>{
  const h=setup(),card=start(h,riskArchitecture(),null);fill(h);const before=JSON.stringify(card);success(h);await h.save();
  assert.deepEqual(h.body(),{expectedPassportVersion:981238,expectedPassportInstanceId:card.passport.instanceId,name:'Новый риск',category:'DATA',probability:'MEDIUM',impact:'HIGH',status:'OPEN'});assert.equal(h.calls[0].options.method,'POST');assert.equal(h.calls[0].url,'/api/projects/p1/architecture/risks');assert.equal(h.calls.length,1);assert.equal(h.accepted.length,1);assert.equal(JSON.stringify(card),before);
});
test('complete POST preserves all eleven fields, multiline NFC and justification at CLOSED',async()=>{
  const h=setup();start(h,riskArchitecture(),null);fill(h);for(const [name,value] of Object.entries({description:'  Cafe\u0301\r\nОписание  ',owner:'Ответственный',treatmentDueDate:'2027-01-01',treatmentPlan:'Первая строка\nВторая строка',status:'CLOSED',closedDate:'2026-10-24',acceptanceJustification:'Прежнее обоснование'}))h.set(name,value);success(h);await h.save();
  assert.deepEqual(h.body(),{expectedPassportVersion:981238,expectedPassportInstanceId:'f071002a-3da5-4bc6-b472-6ba147514ea2',name:'Новый риск',description:'Café\nОписание',category:'DATA',probability:'MEDIUM',impact:'HIGH',owner:'Ответственный',treatmentDueDate:'2027-01-01',treatmentPlan:'Первая строка\nВторая строка',status:'CLOSED',closedDate:'2026-10-24',acceptanceJustification:'Прежнее обоснование'});
});
test('two UUIDs select the intended record after reordering and use its name and own version',async()=>{
  for(const index of [0,1]){const h=setup(),card=riskArchitecture(),row=card.risks[index];card.risks.reverse();start(h,card,row.id);assert.equal(h.field('name').value,row.name);assert.ok(h.get('passport-editor-title').textContent.includes(row.name));h.set('name','Изменённый риск');success(h);await h.save();assert.equal(h.calls[0].url,`/api/projects/p1/architecture/risks/${row.id}`);assert.equal(h.calls[0].options.method,'PATCH');assert.deepEqual(h.body(),{expectedVersion:row.version,name:'Изменённый риск'});}
});
test('missing passport/foreign UUID cannot open the form, unchanged PATCH sends nothing',async()=>{
  const h=setup();start(h,missingArchitecture('p1'),null);assert.equal(h.form.innerHTML,'');start(h,riskArchitecture(),'foreign-id');assert.equal(h.form.innerHTML,'');start(h);await h.save();assert.equal(h.calls.length,0);assert.match(h.get('passport-write-message').textContent,/Изменений нет/);
});
test('PATCH clears every nullable field explicitly when leaving CLOSED, with no passport or computed fields',async()=>{
  const h=setup(),card=riskArchitecture(),row=card.risks[1];row.acceptanceJustification='Сохранялось обоснование';start(h,card,row.id);
  for(const name of ['description','owner','treatmentDueDate','treatmentPlan','closedDate','acceptanceJustification'])h.set(name,'');h.set('status','OPEN');success(h);await h.save();
  assert.deepEqual(h.body(),{expectedVersion:17,description:null,owner:null,treatmentDueDate:null,treatmentPlan:null,status:'OPEN',closedDate:null,acceptanceJustification:null});
});
test('all status transitions validate merged CLOSED date and ACCEPTED justification without automatic changes',async()=>{
  for(const before of ['OPEN','IN_PROGRESS','ACCEPTED','MITIGATED','CLOSED'])for(const status of ['OPEN','IN_PROGRESS','ACCEPTED','MITIGATED','CLOSED'])for(const closed of [null,'2026-10-24'])for(const justification of [null,'Обоснование']){
    const h=setup(),card=riskArchitecture();Object.assign(card.risks[0],{status:before,closedDate:before==='CLOSED'?'2026-10-20':null,acceptanceJustification:before==='ACCEPTED'?'Исходное обоснование':null});start(h,card);h.set('owner','Изменение');h.set('status',status);h.set('closedDate',closed??'');h.set('acceptanceJustification',justification??'');success(h);await h.save();
    const valid=(status!=='ACCEPTED'||justification!==null)&&(status==='CLOSED'?closed!==null:closed===null);assert.equal(h.calls.length,valid?1:0,JSON.stringify({before,status,closed,justification}));assert.equal(h.field('status')?.value??status,status);if(!valid){assert.equal(h.field('closedDate').value,closed??'');assert.equal(h.field('acceptanceJustification').value,justification??'');}
  }
});
test('changing any probability/impact of an ACCEPTED risk still requires justification at all 16 combinations',async()=>{
  for(const probability of ['LOW','MEDIUM','HIGH','CRITICAL'])for(const impact of ['LOW','MEDIUM','HIGH','CRITICAL'])for(const justified of [false,true]){
    const h=setup();start(h);h.set('probability',probability);h.set('impact',impact);h.set('owner','Правка');h.set('acceptanceJustification',justified?'Обоснование':'  ');success(h);await h.save();assert.equal(h.calls.length,justified?1:0);if(!justified)assert.match(h.get('passport-error-acceptanceJustification').textContent,/обоснование/);
  }
});
test('leaving ACCEPTED retains justification, leaving CLOSED requires explicit clearing; unrelated CLOSED edit retains date',async()=>{
  const h=setup(),card=start(h);h.set('status','MITIGATED');success(h);await h.save();assert.deepEqual(h.body(),{expectedVersion:981242,status:'MITIGATED'});
  const a=setup();start(a,card,card.risks[1].id);a.set('status','OPEN');success(a);await a.save();assert.equal(a.calls.length,0);assert.equal(a.field('closedDate').value,'2026-10-21');a.set('closedDate','');await a.save();assert.deepEqual(a.body(),{expectedVersion:17,status:'OPEN',closedDate:null});
  const b=setup();start(b,card,card.risks[1].id);b.set('owner','Другой владелец');success(b);await b.save();assert.deepEqual(b.body(),{expectedVersion:17,owner:'Другой владелец'});
});
test('required fields, closed catalogs and all text limits reject locally and preserve invalid input',async()=>{
  for(const name of ['name','category','probability','impact','status']){const h=setup();start(h,riskArchitecture(),null);fill(h);h.set(name,'');success(h);await h.save();assert.equal(h.calls.length,0);}
  for(const name of ['category','probability','impact','status']){const h=setup();start(h);h.set(name,'UNKNOWN');success(h);await h.save();assert.equal(h.calls.length,0);}
  for(const [name,max] of [['name',255],['owner',255],['description',2000],['treatmentPlan',2000],['acceptanceJustification',2000]]){const h=setup();start(h);h.set(name,'x'.repeat(max+1));success(h);await h.save();assert.equal(h.calls.length,0);assert.equal(h.field(name).value.length,max+1);}
});
test('both dates validate calendar exactly with no today/order restriction and retain invalid values',async()=>{
  for(const name of ['closedDate','treatmentDueDate'])for(const [date,valid] of [['2026-02-29',false],['2024-02-29',true],['1900-02-29',false],['2000-02-29',true],['2026-04-31',false],['2026-13-01',false],['2026-1-01',false],['0000-02-29',true],['9999-12-31',true]]){
    const h=setup(),card=riskArchitecture();start(h,card,name==='closedDate'?card.risks[1].id:card.risks[0].id);h.set(name,date);success(h);await h.save();assert.equal(h.calls.length,valid?1:0,name+date);if(!valid)assert.equal(h.field(name).value,date);
  }
});
test('POST and PATCH bind original versions at opening; edits of different records never depend on passport version',async()=>{
  const a=setup(),b=setup(),c=setup(),card=riskArchitecture(),instance=card.passport.instanceId;start(a,card);start(b,card,card.risks[1].id);start(c,card,null);fill(c);card.passport.version++;card.passport.instanceId='22c1bf4a-ceaa-4f7b-9b26-5a3a0d7245a3';card.risks[0].version++;a.set('owner','Первый');b.set('owner','Второй');success(a);success(b);success(c);await Promise.all([a.save(),b.save(),c.save()]);assert.deepEqual(a.body(),{expectedVersion:981242,owner:'Первый'});assert.deepEqual(b.body(),{expectedVersion:17,owner:'Второй'});assert.equal(c.body().expectedPassportVersion,981238);assert.equal(c.body().expectedPassportInstanceId,instance);
});
test('level/acceptanceVerification are not fields and server assessment is applied untouched even for intentionally inconsistent fixture levels',async()=>{
  const h=setup();start(h);h.set('probability','LOW');h.set('impact','LOW');for(const name of ['level','acceptanceVerification','id','version','projectId'])assert.equal(h.field(name),undefined);
  const result=riskArchitecture();Object.assign(result.risks[0],{probability:'LOW',impact:'LOW',level:'CRITICAL',acceptanceVerification:'UNVERIFIED'});result.assessment.status='ATTENTION_REQUIRED';result.assessment.statusLabel='Серверная оценка';success(h,result);await h.save();assert.deepEqual(h.body(),{expectedVersion:981242,probability:'LOW',impact:'LOW'});assert.equal(h.accepted[0],result);assert.equal(h.accepted[0].risks[0].level,'CRITICAL');assert.equal(h.calls.length,1);
});
test('400 attaches safe field messages and preserves all text; 409 compares without rebasing record or passport tokens',async()=>{
  const h=setup();start(h);h.set('owner','Мой ввод');failure(h,400,'VALIDATION_ERROR',{owner:'SQL'});await h.save();assert.match(h.get('passport-error-owner').textContent,/Проверьте/);assert.equal(h.field('owner').value,'Мой ввод');assert.doesNotMatch(h.get('passport-write-message').textContent,/SQL|Java|private/);
  for(const create of [false,true]){const a=setup(),card=start(a,riskArchitecture(),create?null:riskArchitecture().risks[0].id);if(create)fill(a);else a.set('owner','Мой ввод');failure(a,409,create?'PASSPORT_VERSION_CONFLICT':'RISK_VERSION_CONFLICT');await a.save();const body=a.body(),latest=riskArchitecture();latest.passport.version++;latest.risks[0].version++;latest.risks[0].owner='Чужой владелец';a.context.fetchJson=async()=>latest;await a.get('passport-compare').listeners.click();await a.save();assert.equal(a.calls.length,1);assert.deepEqual(a.body(),body);assert.match(a.get('passport-comparison').innerHTML,/Исходные токены/);if(!create)assert.match(a.get('passport-comparison').innerHTML,/Чужой владелец/);assert.equal(card.passport.version,981238);}
});
test('deleted risk differs from a recreated passport on comparison; POST instance conflict never retries',async()=>{
  for(const recreated of [false,true]){const h=setup();start(h);h.set('owner','Сохранить');failure(h,404,'RISK_NOT_FOUND');await h.save();assert.match(h.get('passport-write-message').textContent,/запись удалена/);const latest=riskArchitecture();latest.risks=[];if(recreated)latest.passport.instanceId='22c1bf4a-ceaa-4f7b-9b26-5a3a0d7245a3';h.context.fetchJson=async()=>latest;await h.get('passport-compare').listeners.click();assert.match(h.get('passport-comparison').innerHTML,recreated?/новый экземпляр/:/запись удалена/);await h.save();assert.equal(h.calls.length,1);assert.equal(h.field('owner').value,'Сохранить');}
  const h=setup();start(h,riskArchitecture(),null);fill(h);failure(h,409,'PASSPORT_INSTANCE_CONFLICT');await h.save();await h.save();assert.equal(h.calls.length,1);assert.match(h.get('passport-write-message').textContent,/создан заново/);
});
test('POST/PATCH double press sends once; network leaves unknown outcome, input and beforeunload warning',async()=>{
  for(const create of [false,true]){const h=setup();start(h,riskArchitecture(),create?null:riskArchitecture().risks[0].id);if(create)fill(h);else h.set('owner','Мой ввод');let reject;h.context.fetchJson=(url,options)=>{h.calls.push({url,options});return new Promise((_r,j)=>{reject=j;});};const pending=h.save();await h.save();assert.equal(h.calls.length,1);reject(Error('offline'));await pending;await h.save();assert.equal(h.calls.length,1);assert.match(h.get('passport-write-message').textContent,/мог сохранить/);let warned=false;h.events.beforeunload({preventDefault(){warned=true;}});assert.equal(warned,true);}
});
test('navigation requires explicit discard and risk drafts do not replace passport/component/check drafts silently',()=>{
  for(const target of ['passport','components','checks']){const h=setup(),card=start(h);h.set('owner','Не потерять');if(target==='passport')h.start(card);else h.api.startChild(card,target,card[target][0].id,()=>{});assert.equal(h.get('passport-leave-dialog').open,true);h.get('passport-stay').listeners.click();assert.equal(h.field('owner').value,'Не потерять');h.discard();}
});
test('late success/error from risk POST/PATCH cannot alter a new form or project',async()=>{
  for(const create of [false,true])for(const fail of [false,true]){const h=setup();start(h,riskArchitecture(),create?null:riskArchitecture().risks[0].id);if(create)fill(h);else h.set('owner','Первый');let finish,reject;h.context.fetchJson=()=>new Promise((r,j)=>{finish=r;reject=j;});const pending=h.save();h.discard();start(h,riskArchitecture('p2'));h.set('owner','Второй');if(fail)reject(Object.assign(Error('late'),{status:400,fieldErrors:{owner:'Invalid'}}));else finish(riskArchitecture());await pending;assert.equal(h.field('owner').value,'Второй');assert.equal(h.get('passport-write-message').textContent,'');assert.equal(h.accepted.length,0);}
});
test('late comparison cannot alter a newer form; using fresh data requires explicit discard',async()=>{
  const h=setup();start(h);h.set('owner','Мой ввод');failure(h,409,'RISK_VERSION_CONFLICT');await h.save();let finish;h.context.fetchJson=()=>new Promise(r=>{finish=r;});const pending=h.get('passport-compare').listeners.click();h.discard();start(h,riskArchitecture('p2'));finish(riskArchitecture());await pending;assert.equal(h.get('passport-comparison').innerHTML,'');
  h.discard();start(h);h.set('owner','Мой ввод');failure(h,409);await h.save();h.context.fetchJson=async()=>riskArchitecture();await h.get('passport-compare').listeners.click();h.get('passport-use-latest').listeners.click();assert.equal(h.accepted.length,0);h.get('passport-stay').listeners.click();assert.equal(h.field('owner').value,'Мой ввод');h.get('passport-use-latest').listeners.click();h.get('passport-discard').listeners.click();assert.equal(h.accepted.length,1);
});
test('risk form and comparison escape text and preserve original DTO, including justification',async()=>{
  const h=setup(),card=riskArchitecture();card.risks[0].name='<script>Имя</script>';card.risks[0].acceptanceJustification='<b>Обоснование</b>';const before=JSON.stringify(card);start(h,card);assert.doesNotMatch(h.form.innerHTML,/<b>|<script>/);h.set('owner','<i>Мой</i>');failure(h,409);await h.save();h.context.fetchJson=async()=>card;await h.get('passport-compare').listeners.click();assert.doesNotMatch(h.get('passport-comparison').innerHTML,/<b>|<script>|<i>/);assert.match(h.get('passport-comparison').innerHTML,/&lt;b&gt;/);assert.equal(JSON.stringify(card),before);
});
