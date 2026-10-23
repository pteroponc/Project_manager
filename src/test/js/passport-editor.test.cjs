const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'); const path=require('node:path'); const vm=require('node:vm');
const {architecture,missingArchitecture}=require('./fixtures/architecture.cjs');
const source=fs.readFileSync(path.join(__dirname,'../../main/resources/public/passport-editor.js'),'utf8');
const decode=value=>String(value??'').replaceAll('&lt;','<').replaceAll('&gt;','>').replaceAll('&quot;','"').replaceAll('&#39;',"'").replaceAll('&amp;','&');
function setup(){
  const nodes=new Map();
  function node(){const classes=new Set();return {value:'',textContent:'',_html:'',listeners:{},attrs:{},disabled:false,checked:false,open:false,
    classList:{add:x=>classes.add(x),remove:x=>classes.delete(x),contains:x=>classes.has(x)},
    addEventListener(type,fn){this.listeners[type]=fn;},setAttribute(k,v){this.attrs[k]=v;},removeAttribute(k){delete this.attrs[k];},focus(){},showModal(){this.open=true;},close(){this.open=false;},reportValidity(){return true;},
    get innerHTML(){return this._html;},set innerHTML(value){this._html=value;}};}
  const get=id=>{if(!nodes.has(id)) nodes.set(id,node());return nodes.get(id);};
  const form=get('passport-form'); let inputs=[];
  Object.defineProperty(form,'innerHTML',{get(){return this._html;},set(html){
    this._html=html;inputs=[];
    for(const match of html.matchAll(/<(input|textarea|select|button)\b([^>]*?)(?:>([\s\S]*?)<\/\1>|>)/g)){
      const [,tag,attrs,body='']=match;const input=node();input.name=attrs.match(/\bname="([^"]*)"/)?.[1]??'';input.type=attrs.match(/\btype="([^"]*)"/)?.[1]??tag;
      input.checked=/\bchecked\b/.test(attrs);input.value=decode(attrs.match(/\bvalue="([^"]*)"/)?.[1]??(tag==='textarea'?body:''));
      if(tag==='select') {const choices=[...body.matchAll(/<option([^>]*)>([^<]*)<\/option>/g)];const selected=choices.find(c=>/selected/.test(c[1]))??choices[0];input.value=selected?.[1].match(/value="([^"]*)"/)?.[1]??'';}
      inputs.push(input);
    }
  }});
  form.elements={namedItem:name=>{const items=inputs.filter(i=>i.name===name);return items.length>1?items:items[0];}};
  form.querySelectorAll=selector=>selector==='.field-error'? [...nodes].filter(([k])=>k.startsWith('passport-error-')).map(([,v])=>v)
    :selector==='[aria-invalid]'?inputs.filter(i=>i.attrs['aria-invalid'])
    :selector==='input,select,textarea'?inputs.filter(i=>i.type!=='submit'&&i.type!=='button')
    :inputs.filter(i=>i.name===selector.match(/name="([^"]+)"/)?.[1]&&(!selector.includes(':checked')||i.checked));
  form.querySelector=selector=>selector==='[type="submit"]'?inputs.find(i=>i.type==='submit'):null;
  const calls=[],events={},accepted=[];
  const window={addEventListener:(type,fn)=>{events[type]=fn;}};
  const context=vm.createContext({window,document:{getElementById:get},Number,JSON,encodeURIComponent,
    escapeHtml:v=>String(v??'').replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;'),
    fetchJson:async(url,options)=>{calls.push({url,options});throw Error('offline');}});
  vm.runInContext(source,context);
  const api=window.PMPassportEditor;
  const start=(data=architecture())=>api.start(data,result=>accepted.push(result));
  const field=name=>form.elements.namedItem(name);
  const set=(name,value)=>{field(name).value=value;form.listeners.input();};
  const save=()=>form.listeners.submit({preventDefault(){}});
  const body=()=>JSON.parse(calls.at(-1).options.body);
  const discard=()=>{get('passport-close').listeners.click();if(get('passport-leave-dialog').open)get('passport-discard').listeners.click();};
  return {api,get,form,start,field,set,save,calls,body,events,accepted,context,discard};
}
test('minimal draft POST uses project version, empty sets and omits empty nullable fields',async()=>{
  const h=setup();const data=missingArchitecture('p1');h.start(data);h.set('criticality','HIGH');
  h.context.fetchJson=async(url,options)=>{h.calls.push({url,options});return architecture();};await h.save();
  assert.equal(h.calls.length,1);assert.equal(h.calls[0].options.method,'POST');
  assert.deepEqual(h.body(),{expectedProjectVersion:981237,criticality:'HIGH',stage:'NOT_STARTED',solutionType:'NEW_SOLUTION',dataClasses:[],environments:[]});
  assert.equal(h.accepted.length,1);assert.equal(h.accepted[0].passport.documentVersion,'1.0');
});
test('unchanged PATCH and reordered sets do not send any request',async()=>{const h=setup();h.start();await h.save();assert.equal(h.calls.length,0);assert.match(h.get('passport-write-message').textContent,/Изменений нет/);});
test('PATCH sends changed fields only, clears nullable fields and sets, retaining original tokens',async()=>{
  const h=setup();h.start();h.set('architect','');h.set('lastReviewDate','');
  for(const input of h.field('dataClasses')) input.checked=false;
  for(const input of h.field('environments')) input.checked=false;
  h.context.fetchJson=async(url,options)=>{h.calls.push({url,options});return architecture();};await h.save();
  assert.deepEqual(h.body(),{expectedVersion:981238,expectedPassportInstanceId:'f071002a-3da5-4bc6-b472-6ba147514ea2',architect:null,lastReviewDate:null,dataClasses:[],environments:[]});
  assert.equal(h.calls[0].options.method,'PATCH');
});
test('text normalization happens in payload without editing input and multiline text is preserved',async()=>{
  const h=setup();h.start();h.set('solutionSummary','  Cafe\u0301\r\nВторая строка  ');
  h.context.fetchJson=async(url,options)=>{h.calls.push({url,options});throw Object.assign(Error('validation'),{status:400,fieldErrors:{solutionSummary:'invalid'}});};await h.save();
  assert.equal(h.body().solutionSummary,'Café\nВторая строка');assert.equal(h.field('solutionSummary').value,'  Cafe\u0301\r\nВторая строка  ');
  assert.match(h.get('passport-error-solutionSummary').textContent,/Проверьте/);
});
test('400 errors preserve every field and selection and allow correction',async()=>{
  const h=setup();h.start();h.set('architect','Мой архитектор');h.set('recoveryRequirement','Моё требование');h.field('dataClasses')[0].checked=true;
  h.context.fetchJson=async()=>{throw Object.assign(Error('SQL must not appear'),{status:400,fieldErrors:{architect:'Invalid value',dataClasses:'Invalid value'}});};await h.save();
  assert.equal(h.field('architect').value,'Мой архитектор');assert.equal(h.field('recoveryRequirement').value,'Моё требование');assert.equal(h.field('dataClasses')[0].checked,true);
  assert.equal(h.form.querySelector('[type="submit"]').disabled,false);assert.doesNotMatch(h.get('passport-write-message').textContent,/SQL/);
});
test('409 preserves input, disables resubmission, comparison never rebases tokens',async()=>{
  const h=setup();h.start();h.set('architect','Мой ввод');
  h.context.fetchJson=async(url,options)=>{h.calls.push({url,options});if(options)throw Object.assign(Error('conflict'),{status:409,code:'PASSPORT_VERSION_CONFLICT',currentVersion:999});return architecture('p1',{passport:{...architecture().passport,version:999,architect:'Чужой ввод'}});};
  await h.save();await h.save();assert.equal(h.calls.length,1);
  await h.get('passport-compare').listeners.click();assert.equal(h.calls.length,2);assert.equal(h.calls[1].options,undefined);
  assert.equal(h.field('architect').value,'Мой ввод');await h.save();assert.equal(h.calls.length,2);
  assert.match(h.get('passport-comparison').innerHTML,/Чужой ввод/);assert.match(h.get('passport-comparison').innerHTML,/Мой ввод/);
  h.set('architect','Уточнённый ввод');assert.match(h.get('passport-comparison').innerHTML,/Уточнённый ввод/);
  await h.save();assert.equal(h.calls.length,2);
  assert.equal(JSON.parse(h.calls[0].options.body).expectedVersion,981238);
});
test('DELETE/recreate conflict is distinguished and latest instance never enters the stale PATCH',async()=>{
  const h=setup();h.start();h.set('architect','Не потерять');
  h.context.fetchJson=async(url,options)=>{h.calls.push({url,options});if(options)throw Object.assign(Error('conflict'),{status:409,code:'PASSPORT_INSTANCE_CONFLICT'});return architecture('p1',{passport:{...architecture().passport,version:0,instanceId:'22c1bf4a-ceaa-4f7b-9b26-5a3a0d7245a3'}});};
  await h.save();assert.match(h.get('passport-write-message').textContent,/создан заново/);
  await h.get('passport-compare').listeners.click();assert.match(h.get('passport-comparison').innerHTML,/новый экземпляр/);
  await h.save();assert.equal(h.calls.filter(c=>c.options).length,1);assert.equal(JSON.parse(h.calls[0].options.body).expectedPassportInstanceId,'f071002a-3da5-4bc6-b472-6ba147514ea2');
});
test('POST conflict preserves original project version and never becomes an automatic PATCH',async()=>{
  const h=setup();h.start(missingArchitecture('p1'));h.set('criticality','HIGH');h.set('architect','Черновик');
  h.context.fetchJson=async(url,options)=>{h.calls.push({url,options});if(options)throw Object.assign(Error('exists'),{status:409,code:'PASSPORT_ALREADY_EXISTS'});return architecture();};
  await h.save();await h.get('passport-compare').listeners.click();await h.save();
  assert.equal(h.calls.length,2);assert.equal(h.calls[0].options.method,'POST');assert.equal(JSON.parse(h.calls[0].options.body).expectedProjectVersion,981237);
  assert.equal(h.field('architect').value,'Черновик');assert.equal(h.form.querySelector('[type="submit"]').disabled,true);
});
test('double submit sends one request and consumes the full response with no GET',async()=>{
  const h=setup();h.start();h.set('architect','Новый');let finish;
  h.context.fetchJson=(url,options)=>{h.calls.push({url,options});return new Promise(r=>{finish=r;});};
  const pending=h.save();await h.save();assert.equal(h.calls.length,1);assert.equal(h.form.querySelector('[type="submit"]').disabled,true);
  const saved=architecture();finish(saved);await pending;assert.equal(h.accepted[0],saved);assert.equal(h.calls.length,1);
});
test('network failure leaves uncertain state, preserves input and never retries POST/PATCH',async()=>{
  for(const create of [true,false]){const h=setup();h.start(create?missingArchitecture('p1'):architecture());h.set('criticality','CRITICAL');h.set('architect','Мой ввод');
    h.context.fetchJson=async(url,options)=>{h.calls.push({url,options});throw Error('network SQL private');};await h.save();await h.save();
    assert.equal(h.calls.length,1);assert.equal(h.field('architect').value,'Мой ввод');assert.match(h.get('passport-write-message').textContent,/мог сохранить/);assert.doesNotMatch(h.get('passport-write-message').textContent,/SQL|private/);}
});
test('cancel and navigation require explicit discard; declining keeps the form intact',()=>{
  const h=setup();h.start();h.set('architect','Мой ввод');let navigated=0;
  assert.equal(h.api.allowNavigation(()=>navigated++),false);h.get('passport-stay').listeners.click();assert.equal(h.field('architect').value,'Мой ввод');assert.equal(navigated,0);
  h.api.allowNavigation(()=>navigated++);h.get('passport-discard').listeners.click();assert.equal(navigated,1);assert.equal(h.get('passport-editor').classList.contains('hidden'),true);
});
test('beforeunload warns only for changed or pending form',()=>{
  const h=setup();h.start();let prevented=false;const event={preventDefault(){prevented=true;}};h.events.beforeunload(event);assert.equal(prevented,false);
  h.set('architect','Новый');h.events.beforeunload(event);assert.equal(prevented,true);
});
test('late success or error cannot alter a newly opened form or another project',async()=>{
  for(const fail of [false,true]){const h=setup();h.start();h.set('architect','Первый');let finish,reject;
    h.context.fetchJson=()=>new Promise((r,j)=>{finish=r;reject=j;});const pending=h.save();h.discard();h.start(architecture('p2'));h.set('architect','Второй');
    if(fail)reject(Object.assign(Error('late'),{status:400,fieldErrors:{architect:'invalid'}}));else finish(architecture());await pending;
    assert.equal(h.field('architect').value,'Второй');assert.equal(h.accepted.length,0);assert.equal(h.get('passport-write-message').textContent,'');}
});
test('late comparison cannot replace a newer form',async()=>{
  const h=setup();h.start();h.set('architect','Мой');h.context.fetchJson=async()=>{throw Object.assign(Error('conflict'),{status:409});};await h.save();let finish;
  h.context.fetchJson=()=>new Promise(r=>{finish=r;});const pending=h.get('passport-compare').listeners.click();h.discard();h.start(architecture('p2'));finish(architecture());await pending;
  assert.equal(h.get('passport-comparison').innerHTML,'');assert.equal(h.get('passport-comparison').classList.contains('hidden'),true);
});
test('comparison safely escapes server and draft text and using fresh data requires explicit discard',async()=>{
  const h=setup();h.start();h.set('architect','<b>Мой</b>');
  h.context.fetchJson=async(url,options)=>{if(options)throw Object.assign(Error('conflict'),{status:409});return architecture('p1',{passport:{...architecture().passport,architect:'<script>Чужой</script>'}});};
  await h.save();await h.get('passport-compare').listeners.click();
  const output=h.get('passport-comparison').innerHTML;assert.doesNotMatch(output,/<script>|<b>/);assert.match(output,/&lt;script&gt;/);assert.match(output,/&lt;b&gt;/);
  h.get('passport-use-latest').listeners.click();assert.equal(h.accepted.length,0);h.get('passport-stay').listeners.click();assert.equal(h.field('architect').value,'<b>Мой</b>');
  h.get('passport-use-latest').listeners.click();h.get('passport-discard').listeners.click();assert.equal(h.accepted.length,1);assert.equal(h.accepted[0].passport.architect,'<script>Чужой</script>');
});
test('unsafe numerical versions do not produce a rounded request',async()=>{const h=setup();h.start(architecture('p1',{passport:{...architecture().passport,version:9007199254740992}}));h.set('architect','Новый');await h.save();assert.equal(h.calls.length,0);assert.match(h.get('passport-write-message').textContent,/безопасно/);});
