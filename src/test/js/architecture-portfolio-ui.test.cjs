const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const publicDir = path.join(__dirname, '../../main/resources/public');
const script = fs.readFileSync(path.join(publicDir, 'architecture-portfolio-ui.js'), 'utf8');
const html = fs.readFileSync(path.join(publicDir, 'index.html'), 'utf8');

function element() {
  return {value:'', disabled:false, innerHTML:'', textContent:'', dataset:{}, listeners:{}, attrs:{},
    addEventListener(type, callback) {this.listeners[type]=callback;},
    setAttribute(key, value) {this.attrs[key]=value;},
    closest() {return this;}};
}

function harness(fetchImpl, initialView='overview', schedule=callback=>{callback(); return 1;}) {
  const nodes = new Map();
  const get = id => {if (!nodes.has(id)) nodes.set(id, element()); return nodes.get(id);};
  const fields = new Map(['query','quarter','status','readiness','sort','direction','size'].map(key => [key,element()]));
  get('architecture-portfolio-filters').elements = {namedItem:key => fields.get(key)};
  const calls=[];
  const opened=[];
  const state={activeView:initialView};
  const window={PMProjectsUI:{openCard:(id,origin)=>opened.push({id,origin})}};
  const context=vm.createContext({window,state,document:{getElementById:get},URLSearchParams,
    Number,Array,Object,String,clearTimeout,setTimeout:schedule,
    escapeHtml:value=>String(value ?? '').replaceAll('&','&amp;').replaceAll('<','&lt;')
      .replaceAll('>','&gt;').replaceAll('"','&quot;').replaceAll("'",'&#39;'),
    displayLabel:value=>({active:'Активен',planned:'Запланирован'})[value] ?? value,
    fetchJson:async url=>{calls.push(url); return fetchImpl(url);}});
  vm.runInContext(script,context);
  if (initialView === 'overview') state.activeView='dashboards';
  return {ui:window.PMArchitecturePortfolioUI,get,fields,calls,opened,state};
}

const item=(id='a', status='READY')=>({project:{id,name:'Alpha <script>',owner:'Owner',quarter:'Q1',projectStatus:'active'},
  passportPresent:true,componentCount:2,integrationCount:1,checkCount:10,securityRiskCount:1,
  assessment:{status,statusLabel:status==='READY'?'Архитектура и ИБ: проверки завершены':'Заблокировано',
    reasonCount:1,acceptedHighRiskCount:1,acceptedHighRisks:[{justification:'Business acceptance',id:'risk-secret'}],
    reasons:[{entityId:'risk-secret'}]}});
const response=(overrides={})=>({calculationDate:'2026-10-25',timeZone:'Europe/Moscow',totalCount:3,
  filteredCount:3,page:0,size:20,totalPages:1,pageItemCount:1,
  readinessCounts:{BLOCKED:1,ATTENTION_REQUIRED:0,NOT_ASSESSED:1,READY:1},items:[item()],...overrides});
const settle=()=>new Promise(resolve=>setImmediate(resolve));

test('direct dashboard route loads the portfolio after the module initializes',async()=>{
  const h=harness(async()=>response(),'dashboards');
  await settle();
  assert.equal(h.calls.length,1);
  assert.match(h.get('architecture-portfolio-list').innerHTML,/Alpha &lt;script&gt;/);
  assert.match(h.get('architecture-portfolio-list').innerHTML,/Активен/);
});

test('dashboard panel uses the real endpoint and renders only aggregate fields',async()=>{
  const h=harness(async()=>response());
  h.ui.onView('dashboards'); await settle();
  assert.match(h.calls[0], /^\/api\/architecture\/portfolio\/summary\?/);
  const params=new URL(h.calls[0],'http://localhost').searchParams;
  assert.equal(params.get('page'),'0'); assert.equal(params.get('size'),'20');
  assert.equal(params.get('readiness'),'all');
  assert.match(h.get('architecture-readiness-counts').innerHTML,/Заблокировано/);
  assert.match(h.get('architecture-readiness-counts').innerHTML,/>1<\/strong>/);
  assert.match(h.get('architecture-portfolio-total').textContent,/3 из 3/);
  assert.match(h.get('architecture-portfolio-list').innerHTML,/Alpha &lt;script&gt;/);
  assert.doesNotMatch(h.get('architecture-portfolio-list').innerHTML,/Business acceptance|risk-secret|entityId|justification|<script>/);
  assert.equal(h.get('architecture-portfolio-list').attrs['aria-busy'],'false');
});

test('filters and pages are sent to the server and page changes preserve the chosen filters',async()=>{
  const h=harness(async url=>response({page:Number(new URL(url,'http://localhost').searchParams.get('page')),
    totalPages:3,filteredCount:5}));
  h.ui.onView('dashboards'); await settle();
  h.get('architecture-portfolio-next').listeners.click(); await settle();
  assert.equal(new URL(h.calls.at(-1),'http://localhost').searchParams.get('page'),'1');
  h.fields.get('readiness').value='BLOCKED';
  h.get('architecture-portfolio-filters').listeners.change({target:{name:'readiness',value:'BLOCKED'}}); await settle();
  const params=new URL(h.calls.at(-1),'http://localhost').searchParams;
  assert.equal(params.get('page'),'0'); assert.equal(params.get('readiness'),'BLOCKED');
  h.fields.get('sort').value='deadline';
  h.get('architecture-portfolio-filters').listeners.change({target:{name:'sort',value:'deadline'}}); await settle();
  assert.equal(new URL(h.calls.at(-1),'http://localhost').searchParams.get('sort'),'deadline');
  h.get('architecture-portfolio-next').listeners.click(); await settle();
  h.get('architecture-portfolio-prev').listeners.click(); await settle();
  assert.equal(new URL(h.calls.at(-1),'http://localhost').searchParams.get('page'),'0');
});

test('changing sort while a text filter is pending includes the visible text filter',async()=>{
  const timers=[];
  const h=harness(async()=>response(),'overview',callback=>{timers.push(callback); return timers.length;});
  h.ui.onView('dashboards'); await settle();
  h.fields.get('quarter').value='Q2 2026';
  h.get('architecture-portfolio-filters').listeners.input({target:{name:'quarter',value:'Q2 2026'}});
  h.fields.get('sort').value='deadline';
  h.get('architecture-portfolio-filters').listeners.change({target:{name:'sort',value:'deadline'}});
  const params=new URL(h.calls.at(-1),'http://localhost').searchParams;
  assert.equal(params.get('quarter'),'Q2 2026');
  assert.equal(params.get('sort'),'deadline');
});

test('refresh applies a pending text filter before requesting data',async()=>{
  const timers=[];
  const h=harness(async()=>response(),'overview',callback=>{timers.push(callback); return timers.length;});
  h.ui.onView('dashboards'); await settle();
  h.fields.get('query').value='Review';
  h.get('architecture-portfolio-filters').listeners.input({target:{name:'query',value:'Review'}});
  h.get('architecture-portfolio-refresh').listeners.click();
  const params=new URL(h.calls.at(-1),'http://localhost').searchParams;
  assert.equal(params.get('query'),'Review');
  assert.equal(params.get('page'),'0');
});

test('empty state, initial failure and refresh use explicit feedback',async()=>{
  let fail=true;
  const h=harness(async()=>{if(fail) throw Error('private server trace');return response({totalCount:0,filteredCount:0,
    totalPages:0,pageItemCount:0,items:[],readinessCounts:{BLOCKED:0,ATTENTION_REQUIRED:0,NOT_ASSESSED:0,READY:0}});});
  h.ui.onView('dashboards'); await settle();
  assert.match(h.get('architecture-portfolio-message').textContent,/Не удалось загрузить/);
  assert.doesNotMatch(h.get('architecture-portfolio-message').textContent,/private server trace/);
  fail=false; h.get('architecture-portfolio-refresh').listeners.click(); await settle();
  assert.match(h.get('architecture-portfolio-list').innerHTML,/проектов нет/);
  assert.equal(h.get('architecture-portfolio-next').disabled,true);
  assert.equal(h.get('architecture-portfolio-page').textContent,'Страниц нет');
});

test('older response cannot overwrite a newer filter result',async()=>{
  const pending=[];
  const h=harness(()=>new Promise(resolve=>pending.push(resolve)));
  h.ui.onView('dashboards');
  h.get('architecture-portfolio-filters').listeners.change({target:{name:'readiness',value:'BLOCKED'}});
  pending[1](response({filteredCount:1,items:[item('b','BLOCKED')]})); await settle();
  pending[0](response()); await settle();
  assert.match(h.get('architecture-portfolio-list').innerHTML,/data-open-architecture-project="b"/);
  assert.doesNotMatch(h.get('architecture-portfolio-list').innerHTML,/data-open-architecture-project="a"/);
});

test('pagination is locked during a request and repeated clicks send only one page request',async()=>{
  const pending=[];
  const h=harness(()=>new Promise(resolve=>pending.push(resolve)));
  h.ui.onView('dashboards');
  pending[0](response({totalPages:3,filteredCount:5})); await settle();
  h.get('architecture-portfolio-next').listeners.click();
  assert.equal(h.get('architecture-portfolio-next').disabled,true);
  assert.equal(h.get('architecture-portfolio-prev').disabled,true);
  h.get('architecture-portfolio-next').listeners.click();
  h.get('architecture-portfolio-prev').listeners.click();
  assert.equal(h.calls.length,2);
  assert.equal(new URL(h.calls[1],'http://localhost').searchParams.get('page'),'1');
  pending[1](response({page:1,totalPages:3,filteredCount:5})); await settle();
  assert.equal(h.get('architecture-portfolio-prev').disabled,false);
  assert.equal(h.get('architecture-portfolio-next').disabled,false);
});

test('filter failure keeps stale pagination locked until a successful refresh',async()=>{
  let fail=false;
  const h=harness(async url=>{
    if(fail) throw Error('private API detail');
    const params=new URL(url,'http://localhost').searchParams;
    return params.get('readiness')==='BLOCKED'
      ? response({totalCount:3,filteredCount:0,totalPages:0,items:[],pageItemCount:0,
          readinessCounts:{BLOCKED:0,ATTENTION_REQUIRED:1,NOT_ASSESSED:2,READY:0}})
      : response({totalPages:3,filteredCount:5});
  });
  h.ui.onView('dashboards'); await settle();
  assert.equal(h.get('architecture-portfolio-next').disabled,false);
  fail=true;
  h.get('architecture-portfolio-filters').listeners.change({target:{name:'readiness',value:'BLOCKED'}}); await settle();
  assert.equal(h.get('architecture-portfolio-next').disabled,true);
  h.get('architecture-portfolio-next').listeners.click();
  assert.equal(h.calls.length,2);
  assert.match(h.get('architecture-portfolio-message').textContent,/предыдущие данные/);
  assert.doesNotMatch(h.get('architecture-portfolio-message').textContent,/private API detail/);
  fail=false;
  h.get('architecture-portfolio-refresh').listeners.click(); await settle();
  assert.match(h.get('architecture-readiness-counts').innerHTML,/Оценка не завершена/);
  assert.match(h.get('architecture-readiness-counts').innerHTML,/>2<\/strong>/);
  assert.equal(h.get('architecture-portfolio-total').textContent,'0 из 3 проектов');
  assert.match(h.get('architecture-portfolio-list').innerHTML,/По выбранной готовности проектов нет/);
  assert.equal(h.get('architecture-portfolio-next').disabled,true);
});

test('leaving during a request ignores its response and reloads on return',async()=>{
  const pending=[];
  const h=harness(()=>new Promise(resolve=>pending.push(resolve)));
  h.ui.onView('dashboards');
  h.state.activeView='project-card'; h.ui.onView('project-card');
  pending[0](response()); await settle();
  assert.doesNotMatch(h.get('architecture-portfolio-list').innerHTML,/Alpha/);
  h.state.activeView='dashboards'; h.ui.onView('dashboards');
  assert.equal(h.calls.length,2);
  pending[1](response()); await settle();
  assert.match(h.get('architecture-portfolio-list').innerHTML,/Alpha/);
});

test('project transition uses the existing card and keeps dashboard origin',async()=>{
  const h=harness(async()=>response());
  h.ui.onView('dashboards'); await settle();
  h.get('architecture-portfolio-list').listeners.click({target:{closest:()=>({dataset:{openArchitectureProject:'a'}})}});
  assert.deepEqual(h.opened,[{id:'a',origin:'dashboards'}]);
  assert.match(html,/data-view-section="dashboards"[^>]*aria-labelledby="architecture-portfolio-title"/);
  assert.match(html,/architecture-portfolio-ui\.js/);
});
