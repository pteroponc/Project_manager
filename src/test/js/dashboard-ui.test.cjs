const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const script=fs.readFileSync(path.join(__dirname,'../../main/resources/public/dashboard-ui.js'),'utf8');
const settle=()=>new Promise(resolve=>setImmediate(resolve));
const project=(id='p')=>({id,name:'Project <unsafe>',status:'active',health:'red',owner:'Owner',quarter:'Q1',
  budget:10,progress:20,deadline:'2026-11-01',milestone:'Checkpoint',risk:'Risk',attentionReasons:['Overdue <reason>'],dataQualityIssues:['Missing <field>'],daysUntilDeadline:1});
const response=(overrides={})=>({calculationDate:'2026-10-31',timeZone:'Europe/Moscow',totalProjectCount:2,projectCount:1,
  activeCount:1,averageProgress:20,progressCoverage:1,attentionCount:1,overdueCount:0,healthCounts:{red:1},statusCounts:{active:1},
  totalBudget:10,budgetCoverage:1,quarters:['Q1','Q2'],statuses:['active','done'],healthValues:['red','green'],
  projects:[project()],attentionProjects:[project()],milestones:[project()],dataQualityProjects:[project()],...overrides});
function node(){return {value:'all',innerHTML:'',textContent:'',dataset:{},attrs:{},listeners:{},disabled:false,tabIndex:0,
  setAttribute(k,v){this.attrs[k]=v;},addEventListener(k,fn){this.listeners[k]=fn;},focus(){this.focused=true;},scrollIntoView(){this.scrolled=true;}};}
function harness(fetchImpl,initialView='dashboards') {
  const nodes=new Map();const get=id=>{if(!nodes.has(id))nodes.set(id,node());return nodes.get(id);};
  const tabs=['portfolio','deadlines','architecture'].map(key=>{const n=node();n.dataset.controlTab=key;return n;});
  get('dashboard-workspace').querySelectorAll=()=>tabs;
  const fields=Object.fromEntries(['quarter','status','health'].map(key=>[key,node()]));
  get('control-filters').elements={namedItem:key=>fields[key]};
  const calls=[],opened=[];const state={activeView:initialView,projects:[]};
  const media={matches:true,addEventListener(type,fn){this.change=fn;}};
  const window={matchMedia:()=>media,PMProjectsUI:{openCard:(id,origin)=>opened.push({id,origin})}};
  const context=vm.createContext({window,state,document:{getElementById:get},URLSearchParams,
    escapeHtml:v=>String(v).replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;'),
    displayLabel:String,overviewDate:String,formatCurrency:String,renderDistributionBars:()=>'<bars>',groupCount:()=>({}),
    milestoneTone:()=> 'yellow',riskWeight:()=>1,daysUntil:()=>1,loadProjectCatalog(){},
    fetchJson:async url=>{calls.push(url);return fetchImpl(url);}});
  vm.runInContext(script,context);
  return {get,fields,tabs,state,media,calls,opened,ui:window.PMDashboardUI};
}
test('dashboard uses PM-001 server values and escapes project names, reasons and quality',async()=>{
  const h=harness(async()=>response());await settle();
  assert.match(h.calls[0],/^\/api\/portfolio\/overview\?/);
  assert.match(h.get('control-kpis').innerHTML,/Требуют внимания/);
  assert.match(h.get('control-kpis').innerHTML,/>20%<\/strong>/);
  assert.match(h.get('control-attention').innerHTML,/Overdue &lt;reason&gt;/);
  assert.match(h.get('control-quality').innerHTML,/Missing &lt;field&gt;/);
  assert.doesNotMatch(h.get('control-project-list').innerHTML,/<unsafe>/);
  assert.match(h.get('control-deadline-list').innerHTML,/Checkpoint/);
});
test('tabs and arrow/home/end keys change presentation only and preserve filters',async()=>{
  const h=harness(async()=>response());await settle();
  h.fields.quarter.value='Q2';h.get('control-filters').listeners.change({target:{name:'quarter',value:'Q2'}});await settle();
  const count=h.calls.length;
  h.tabs[1].listeners.click();
  assert.equal(h.get('dashboard-workspace').dataset.activeTab,'deadlines');
  h.tabs[1].listeners.keydown({key:'End',preventDefault(){}});
  assert.equal(h.tabs[2].attrs['aria-selected'],'true');assert.equal(h.tabs[2].focused,true);
  h.tabs[2].listeners.keydown({key:'Home',preventDefault(){}});
  assert.equal(h.tabs[0].tabIndex,0);assert.equal(h.tabs[1].tabIndex,-1);
  assert.equal(h.calls.length,count);assert.equal(h.fields.quarter.value,'Q2');
  h.media.matches=false;h.media.change();assert.equal(h.get('control-portfolio').attrs.role,'region');
});
test('portfolio filters reset independently and never use architecture query parameters',async()=>{
  const h=harness(async()=>response());await settle();
  h.fields.status.value='done';h.get('control-filters').listeners.change({target:{name:'status',value:'done'}});await settle();
  assert.equal(h.get('control-filter-count').textContent,'1');
  assert.equal(new URL(h.calls.at(-1),'http://localhost').searchParams.get('status'),'done');
  assert.doesNotMatch(h.calls.at(-1),/readiness|query|page|size/);
  h.get('control-reset').listeners.click();await settle();
  assert.equal(h.get('control-filter-count').textContent,'0');assert.equal(h.fields.status.value,'all');
});
test('newer filter wins when responses arrive out of order; late errors cannot replace success',async()=>{
  const pending=[];const h=harness(()=>new Promise((resolve,reject)=>pending.push({resolve,reject})));
  h.fields.quarter.value='Q2';h.get('control-filters').listeners.change({target:{name:'quarter',value:'Q2'}});
  pending[1].resolve(response({projectCount:7}));await settle();
  pending[0].reject(Error('old private error'));await settle();
  assert.match(h.get('control-message').textContent,/7 из 2/);assert.doesNotMatch(h.get('control-message').textContent,/private/);
  assert.equal(h.get('control-refresh').disabled,false);
});
test('leaving during request invalidates response, reopening issues fresh request and keeps selected tab',async()=>{
  const pending=[];const h=harness(()=>new Promise(resolve=>pending.push(resolve)));
  h.tabs[1].listeners.click();h.state.activeView='projects';h.ui.onView('projects');
  pending[0](response());await settle();assert.equal(h.get('control-attention').innerHTML,'');
  h.state.activeView='dashboards';h.ui.onView('dashboards');assert.equal(h.calls.length,2);
  pending[1](response());await settle();assert.equal(h.get('dashboard-workspace').dataset.activeTab,'deadlines');
});
test('initial error, retry, repeated refresh and stale snapshot expose explicit feedback',async()=>{
  let fail=true;const h=harness(async()=>{if(fail)throw Error('private');return response();});await settle();
  assert.match(h.get('control-message').textContent,/Данные недоступны/);
  assert.match(h.get('control-attention').innerHTML,/недоступны/);
  fail=false;h.get('control-refresh').listeners.click();h.get('control-refresh').listeners.click();
  assert.equal(h.calls.length,2);await settle();
  const before=h.get('control-kpis').innerHTML;fail=true;h.get('control-refresh').listeners.click();await settle();
  assert.match(h.get('control-message').textContent,/предыдущий снимок/);assert.equal(h.get('control-kpis').innerHTML,before);
});
test('empty selection, null progress, completed future project and no due date remain honest',async()=>{
  const completed={...project(),status:'done'};const noDate={...project('n'),deadline:null,daysUntilDeadline:null};
  const h=harness(async()=>response({averageProgress:null,projects:[completed,noDate],milestones:[completed]}));await settle();
  assert.match(h.get('control-kpis').innerHTML,/Нет данных/);
  assert.match(h.get('control-deadline-list').innerHTML,/Checkpoint/); // API planned date remains visible.
  assert.doesNotMatch(h.get('control-all-deadlines').innerHTML,/data-control-project/);
  const e=harness(async()=>response({projectCount:0,projects:[],attentionProjects:[],milestones:[],dataQualityProjects:[]}));await settle();
  assert.match(e.get('control-project-list').innerHTML,/Нет проектов по фильтрам/);
});
test('project and checkpoint links retain dashboard origin; architecture navigation sends no request',async()=>{
  const h=harness(async()=>response());await settle();
  h.get('dashboard-workspace').listeners.click({target:{closest:selector=>selector==='[data-control-project]'?{dataset:{controlProject:'p'}}:null}});
  assert.deepEqual(h.opened,[{id:'p',origin:'dashboards'}]);
  const count=h.calls.length;
  h.get('dashboard-workspace').listeners.click({target:{closest:selector=>selector==='[data-open-control-architecture]'?{}:null}});
  assert.equal(h.get('dashboard-workspace').dataset.activeTab,'architecture');assert.equal(h.calls.length,count);
  assert.equal(h.get('architecture-portfolio-title').focused,true);
});
test('architecture blockers remain a separately labelled slice and stale notices stay visible',async()=>{
  const h=harness(async()=>response());await settle();
  const before=h.get('control-kpis').innerHTML;
  h.ui.onArchitecture({readinessCounts:{BLOCKED:2,ATTENTION_REQUIRED:1,NOT_ASSESSED:3}},'Предыдущие данные');
  assert.match(h.get('control-architecture-alert').innerHTML,/2 заблокировано/);
  assert.match(h.get('control-architecture-alert').innerHTML,/Отдельный срез/);
  assert.match(h.get('control-architecture-alert').innerHTML,/Предыдущие данные/);
  assert.equal(h.get('control-kpis').innerHTML,before);
});

test('catalog enrichment sends no per-project requests and health distributions use semantic roles',async()=>{
  const h=harness(async()=>response());await settle();
  const calls=h.calls.length;
  h.ui.onCatalog([{...project(),deliveryModel:'scrum'}]);
  assert.equal(h.calls.length,calls);
  assert.match(h.get('control-project-list').innerHTML,/scrum/);
  assert.match(h.get('project-dashboard-grid').innerHTML,/data-control-tone="danger"/);
  h.ui.onArchitecture({readinessCounts:{BLOCKED:1},items:[{project:project('blocked'),assessment:{status:'BLOCKED',statusLabel:'Blocked'}}]},'');
  assert.match(h.get('control-architecture-alert').innerHTML,/data-control-project="blocked"/);
  assert.match(h.get('control-architecture-alert').innerHTML,/pill danger/);
});

test('malformed API result keeps the last valid snapshot and does not expose payload or exception',async()=>{
  let invalid=false;const h=harness(async()=>invalid?{projects:[],private:'secret'}:response());await settle();
  const saved=h.get('control-project-list').innerHTML;
  invalid=true;h.get('control-refresh').listeners.click();await settle();
  assert.equal(h.get('control-project-list').innerHTML,saved);
  assert.match(h.get('control-message').textContent,/предыдущий снимок/);
  assert.doesNotMatch(h.get('control-message').textContent,/secret|Invalid/);
});

test('failed B keeps snapshot A and names its exact filters; successful retry B updates both and clears warning',async()=>{
  let failB=true;
  const h=harness(async url=>{
    const params=new URL(url,'http://localhost').searchParams;
    if(params.get('status')==='done') {
      if(failB)throw Error('private B failure');
      return response({projectCount:9,activeCount:0,averageProgress:100});
    }
    return response();
  });await settle();
  for(const [name,value] of [['quarter','Q1'],['status','active'],['health','green']]) {
    h.fields[name].value=value;
    h.get('control-filters').listeners.change({target:{name,value}});await settle();
  }
  const saved=h.get('control-kpis').innerHTML;
  assert.match(h.get('control-message').textContent,/квартал: Q1; статус: active; состояние: green/);
  h.fields.status.value='done';
  h.get('control-filters').listeners.change({target:{name:'status',value:'done'}});
  assert.match(h.get('control-message').textContent,/Обновляем.*квартал: Q1; статус: active; состояние: green/);
  await settle();
  const failed=h.get('control-message').textContent;
  assert.match(failed,/Не удалось обновить.*предыдущий снимок/);
  assert.match(failed,/Фильтры показанных данных — квартал: Q1; статус: active; состояние: green/);
  assert.doesNotMatch(failed,/статус: done|private/);
  assert.equal(h.fields.status.value,'done'); // Desired B remains available for retry.
  assert.equal(h.get('control-kpis').innerHTML,saved);
  failB=false;h.get('control-refresh').listeners.click();await settle();
  assert.match(h.get('control-message').textContent,/9 из 2/);
  assert.match(h.get('control-message').textContent,/квартал: Q1; статус: done; состояние: green/);
  assert.doesNotMatch(h.get('control-message').textContent,/Не удалось|предыдущий|ещё не применены/);
  assert.match(h.get('control-kpis').innerHTML,/>100%<\/strong>/);
});

test('late B cannot change the accepted C data or its filter provenance',async()=>{
  const pending=[];
  const h=harness(()=>new Promise((resolve,reject)=>pending.push({resolve,reject})));
  pending[0].resolve(response());await settle();
  h.fields.quarter.value='Q1';
  h.get('control-filters').listeners.change({target:{name:'quarter',value:'Q1'}});
  pending[1].resolve(response());await settle(); // Successful A.
  h.fields.quarter.value='Q2';
  h.get('control-filters').listeners.change({target:{name:'quarter',value:'Q2'}}); // B.
  h.fields.status.value='done';
  h.get('control-filters').listeners.change({target:{name:'status',value:'done'}}); // C.
  pending[3].resolve(response({projectCount:8}));await settle();
  const accepted=h.get('control-message').textContent;
  assert.match(accepted,/8 из 2.*квартал: Q2; статус: done; состояние: все состояния/);
  pending[2].resolve(response({projectCount:3}));await settle();
  assert.equal(h.get('control-message').textContent,accepted);
  // A subsequent failed request must still identify C, not B or mutable current controls.
  h.fields.quarter.value='Q1';
  h.get('control-filters').listeners.change({target:{name:'quarter',value:'Q1'}});
  pending[4].reject(Error('private'));await settle();
  assert.match(h.get('control-message').textContent,/предыдущий снимок.*квартал: Q2; статус: done/);
});

test('late failure cannot change the latest successful filter label',async()=>{
  const pending=[];const h=harness(()=>new Promise((resolve,reject)=>pending.push({resolve,reject})));
  h.fields.quarter.value='Q2';h.get('control-filters').listeners.change({target:{name:'quarter',value:'Q2'}});
  pending[1].resolve(response());await settle();
  const accepted=h.get('control-message').textContent;
  pending[0].reject(Error('old request'));await settle();
  assert.equal(h.get('control-message').textContent,accepted);
  assert.match(accepted,/квартал: Q2; статус: все статусы; состояние: все состояния/);
});
