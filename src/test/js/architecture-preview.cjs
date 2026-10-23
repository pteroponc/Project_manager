// Isolated HTTP fixture server for browser review. No Spring, datasource or database access.
// Run: node src/test/js/architecture-preview.cjs; open http://127.0.0.1:4175/?view=projects
'use strict';
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const {architecture,missingArchitecture,childArchitecture,checkArchitecture,riskArchitecture,checkTypes} = require('./fixtures/architecture.cjs');
const riskMode=process.argv.includes('--risk-editor');
const checkMode=riskMode||process.argv.includes('--check-editor');
const childMode=checkMode||process.argv.includes('--child-editor');
const writeMode=childMode||process.argv.includes('--passport-editor');
const stored=new Map(), writes=new Map();
const root = path.resolve(__dirname,'../../main/resources/public');
const assets = {'/':'index.html','/index.html':'index.html','/app.js':'app.js','/projects-ui.js':'projects-ui.js','/architecture-ui.js':'architecture-ui.js','/passport-editor.js':'passport-editor.js','/styles.css':'styles.css'};
assets['/architecture-child-editor.js']='architecture-child-editor.js';
assets['/architecture-check-editor.js']='architecture-check-editor.js';
assets['/architecture-editor-validation.js']='architecture-editor-validation.js';
assets['/architecture-risk-editor.js']='architecture-risk-editor.js';
const names = {'review-full':'Тестовый проект «Приём заявок»','review-empty':'Тестовый пустой паспорт','review-missing':'Тестовый проект без паспорта','review-error':'Тестовый повтор загрузки','review-slow':'Тестовый медленный ответ'};
if(writeMode) Object.assign(names,{'write-create':'Тестовый новый паспорт','write-validation':'Тестовая ошибка поля','write-version':'Тестовый конфликт версии','write-recreate':'Тестовый новый экземпляр','write-unknown':'Тестовый неопределённый результат'});
if(childMode) Object.assign(names,{'child-full':'Тестовые компоненты и интеграции','child-validation':'Тестовая ошибка компонента','child-version':'Тестовый конфликт записи','child-passport':'Тестовый конфликт паспорта','child-deleted':'Тестовая удалённая запись','child-recreate':'Тестовый пересозданный паспорт','child-unknown':'Тестовая неопределённая запись'});
if(checkMode)Object.assign(names,{'check-empty':'Тестовый пустой набор проверок','check-full':'Тестовые проверки архитектуры и ИБ','check-partial':'Тестовый неполный набор','check-version':'Тестовый конфликт проверки','check-passport-version':'Тестовый конфликт инициализации','check-exists':'Тестовый уже созданный набор','check-recreate':'Тестовый новый экземпляр паспорта','check-deleted':'Тестовая удалённая проверка','check-validation':'Тестовая ошибка владельца','check-unknown':'Тестовый неизвестный результат'});
if(riskMode)Object.assign(names,{'risk-full':'Тестовые риски ИБ','risk-empty':'Тестовое добавление риска','risk-version':'Тестовый конфликт риска','risk-passport-version':'Тестовый конфликт паспорта','risk-deleted':'Тестовый удалённый риск','risk-recreate':'Тестовый пересозданный паспорт','risk-validation':'Тестовая ошибка поля риска','risk-unknown':'Тестовый неизвестный результат записи риска'});
const projects = Object.entries(names).map(([id,name])=>({id,name,owner:'Тестовая команда',quarter:'Q4 2026',status:'active',health:'green',progress:0,deadline:'2026-12-15',startDate:null,summary:'Изолированные тестовые данные C.2',deliveryModel:'kanban',assessment:{requiresAttention:false,attentionReasons:[],dataQualityIssues:[]}}));
const attempts = new Map();
function send(response,status,data) {response.writeHead(status,{'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store'});response.end(JSON.stringify(data));}
http.createServer((req,res)=>{
  const writePath=childMode?/^\/api\/projects\/[^/]+\/architecture(?:\/(components|integrations|checks|risks)(?:\/[^/]+)?)?$/:/^\/api\/projects\/[^/]+\/architecture$/;
  if(req.method!=='GET' && !(writeMode && ['POST','PATCH'].includes(req.method) && writePath.test(new URL(req.url,'http://127.0.0.1').pathname))) return send(res,405,{error:'Read-only fixture server'});
  const url=new URL(req.url,'http://127.0.0.1');
  if(Object.hasOwn(assets,url.pathname)) {
    const file=assets[url.pathname];
    res.writeHead(200,{'Content-Type':file.endsWith('.html')?'text/html; charset=utf-8':file.endsWith('.css')?'text/css; charset=utf-8':'text/javascript; charset=utf-8','Cache-Control':'no-store'});
    return res.end(fs.readFileSync(path.join(root,file)));
  }
  if(url.pathname==='/api/delivery-models') return send(res,200,{items:['kanban','scrum','waterfall']});
  if(url.pathname==='/api/projects') return send(res,200,projects);
  if(url.pathname==='/api/projects/registry') return send(res,200,{items:projects,totalCount:projects.length,filteredCount:projects.length,filterOptions:{quarters:['Q4 2026'],statuses:['active'],healthValues:['green']}});
  if(url.pathname==='/api/portfolio/overview') return send(res,200,{calculationDate:'2026-10-23',totalProjectCount:5,projectCount:5,activeCount:5,attentionCount:0,averageProgress:0,progressCoverage:5,quarters:['Q4 2026'],statuses:['active'],healthValues:['green'],statusCounts:{active:5},healthCounts:{green:5},attentionProjects:[],milestones:[],dueSoonCount:0,dataQualityProjects:[]});
  if(url.pathname==='/api/release-trains/snapshot') return send(res,200,{trains:[],projects:[]});
  if(url.pathname==='/api/release-checks') return send(res,200,{available:false,message:'Изолированный сервер: GitHub не подключён',checks:[]});
  const match=url.pathname.match(/^\/api\/projects\/([^/]+)\/(card|architecture)(?:\/(components|integrations|checks|risks)(?:\/([^/]+))?)?$/);
  if(match) {
    const [,id,resource,kind,recordId]=match;
    if(!Object.hasOwn(names,id)) return send(res,404,{code:'PROJECT_NOT_FOUND',error:'Project not found'});
    const project=projects.find(p=>p.id===id);
    if(resource==='card') return send(res,200,{...project,version:0,board:{viewAvailable:true,taskCount:0},milestoneCount:0,milestones:[]});
    const data=stored.get(id) ?? (id==='review-missing'||id==='write-create'?missingArchitecture(id):riskMode?riskArchitecture(id):checkMode?checkArchitecture(id):childMode?childArchitecture(id):architecture(id));
    if(riskMode&&!stored.has(id)&&id==='risk-empty'){
      data.risks=[];data.assessment.acceptedHighRisks=[];data.assessment.reasons=data.assessment.reasons.filter(r=>r.entityType!=='RISK');
    }
    if(checkMode&&!stored.has(id)){
      if(['check-empty','check-passport-version','check-exists','check-recreate'].includes(id))data.checks=[];
      if(id==='check-partial')data.checks=data.checks.slice(0,1);
    }
    data.project.name=project.name;
    if(id==='review-empty') Object.assign(data,{components:[],integrations:[],checks:[],risks:[],dataClasses:[],environments:[],passport:{...data.passport,stage:'IN_DESIGN',architect:null,solutionSummary:null,availabilityRequirement:null,recoveryRequirement:null,lastReviewDate:null},assessment:{...data.assessment,status:'NOT_ASSESSED',statusLabel:'Оценка не завершена',reasons:[{status:'NOT_ASSESSED',entityType:'PASSPORT',entityId:id,code:'REQUIRED_INFORMATION_INCOMPLETE',message:'Сведения паспорта неполны'},{status:'NOT_ASSESSED',entityType:'PASSPORT',entityId:id,code:'ARCHITECTURE_NOT_APPROVED',message:'Архитектура не утверждена'},{status:'NOT_ASSESSED',entityType:'PASSPORT',entityId:id,code:'CHECKS_INCOMPLETE',message:'Проверки не завершены'}],upcomingChecks:[],acceptedHighRisks:[]}});
    if(writeMode) stored.set(id,data);
    if(kind==='risks'){
      if(!riskMode||!['POST','PATCH'].includes(req.method))return send(res,405,{code:'BAD_REQUEST'});
      let raw='';req.on('data',chunk=>{raw+=chunk;});return req.on('end',()=>{
        let body;try{body=JSON.parse(raw);}catch{return send(res,400,{code:'BAD_REQUEST'});}
        if(!data.passport)return send(res,404,{code:'PASSPORT_NOT_FOUND'});
        const attempt=(writes.get(id)||0)+1;writes.set(id,attempt);const record=data.risks.find(row=>row.id===recordId);
        if(id==='risk-validation'&&attempt===1)return send(res,400,{code:'VALIDATION_ERROR',fieldErrors:{owner:'Invalid value'}});
        if(id==='risk-recreate'&&attempt===1){data.passport={...data.passport,instanceId:require('node:crypto').randomUUID(),version:0};data.risks=[];return send(res,req.method==='POST'?409:404,{code:req.method==='POST'?'PASSPORT_INSTANCE_CONFLICT':'RISK_NOT_FOUND'});}
        if(id==='risk-deleted'&&attempt===1&&record){data.risks=data.risks.filter(row=>row.id!==recordId);return send(res,404,{code:'RISK_NOT_FOUND'});}
        if((id==='risk-version'||id==='risk-passport-version')&&attempt===1){if(record){record.version++;record.owner='Другой тестовый владелец';}else data.passport.version++;return send(res,409,{code:record?'RISK_VERSION_CONFLICT':'PASSPORT_VERSION_CONFLICT',currentVersion:record?record.version:data.passport.version,versionResource:record?'RISK':'PASSPORT'});}
        if(req.method==='POST'){
          if(body.expectedPassportInstanceId!==data.passport.instanceId)return send(res,409,{code:'PASSPORT_INSTANCE_CONFLICT'});
          if(body.expectedPassportVersion!==data.passport.version)return send(res,409,{code:'PASSPORT_VERSION_CONFLICT'});
        }else if(!record)return send(res,404,{code:'RISK_NOT_FOUND'});
        else if(body.expectedVersion!==record.version)return send(res,409,{code:'RISK_VERSION_CONFLICT'});
        const next={...(record??{}),...body};const required=['name','category','probability','impact','status'];
        const invalid=required.find(name=>!next[name])??(next.status==='ACCEPTED'&&!next.acceptanceJustification?.trim()?'acceptanceJustification':next.status==='CLOSED'?!next.closedDate?'closedDate':null:next.closedDate?'closedDate':null);
        if(invalid)return send(res,400,{code:'VALIDATION_ERROR',fieldErrors:{[invalid]:'Invalid value'}});
        const target=record??{id:require('node:crypto').randomUUID(),projectId:id,version:0,level:'HIGH'};
        for(const name of [...required,'description','owner','treatmentDueDate','treatmentPlan','closedDate','acceptanceJustification'])if(Object.hasOwn(body,name))target[name]=body[name];else if(!record&&!required.includes(name))target[name]=null;
        target.acceptanceVerification=target.status==='ACCEPTED'?'UNVERIFIED':null;
        if(record)target.version++;else{data.risks.push(target);data.passport.version++;}
        // Fixed HIGH level for new HTTP fixture rows; no probability/impact matrix is implemented here.
        const accepted=data.risks.filter(row=>row.status==='ACCEPTED'&&['HIGH','CRITICAL'].includes(row.level));
        data.assessment.acceptedHighRisks=accepted.map(row=>({id:row.id,level:row.level,justification:row.acceptanceJustification,acceptanceVerification:'UNVERIFIED'}));
        data.assessment.reasons=[...data.assessment.reasons.filter(row=>row.entityType!=='RISK'),...accepted.map(row=>({status:'ATTENTION_REQUIRED',code:'ACCEPTED_HIGH_RISK',entityType:'RISK',entityId:row.id,message:'Высокий риск отмечен как принятый; полномочия не проверены'}))];
        if(id==='risk-unknown')return send(res,503,{code:'DATABASE_ERROR'});
        return send(res,record?200:201,data);
      });
    }
    if(kind==='checks') {
      const initialize=recordId==='initialize'&&req.method==='POST';
      if(!checkMode||(!initialize&&req.method!=='PATCH'))return send(res,405,{code:'BAD_REQUEST'});
      let raw='';req.on('data',chunk=>{raw+=chunk;});return req.on('end',()=>{
        let body;try{body=JSON.parse(raw);}catch{return send(res,400,{code:'BAD_REQUEST'});}
        if(!data.passport)return send(res,404,{code:'PASSPORT_NOT_FOUND'});
        const attempt=(writes.get(id)||0)+1;writes.set(id,attempt);
        const record=data.checks.find(row=>row.id===recordId);
        if(id==='check-validation'&&attempt===1)return send(res,400,{code:'VALIDATION_ERROR',fieldErrors:{owner:'Invalid value'}});
        if(id==='check-recreate'&&attempt===1){data.passport={...data.passport,instanceId:require('node:crypto').randomUUID(),version:0};data.checks=[];return send(res,initialize?409:404,{code:initialize?'PASSPORT_INSTANCE_CONFLICT':'CHECK_NOT_FOUND'});}
        if(id==='check-deleted'&&attempt===1&&record){data.checks=data.checks.filter(row=>row.id!==recordId);return send(res,404,{code:'CHECK_NOT_FOUND'});}
        if(id==='check-version'&&attempt===1&&record){record.version++;record.owner='Другой тестовый владелец';return send(res,409,{code:'CHECK_VERSION_CONFLICT',currentVersion:record.version,versionResource:'CHECK'});}
        if(id==='check-passport-version'&&attempt===1){data.passport.version++;return send(res,409,{code:'PASSPORT_VERSION_CONFLICT',currentVersion:data.passport.version,versionResource:'PASSPORT'});}
        if(id==='check-exists'&&attempt===1){data.checks=checkArchitecture(id).checks.slice(0,1);return send(res,409,{code:'CHECKS_ALREADY_EXIST'});}
        if(initialize){
          if(body.expectedPassportInstanceId!==data.passport.instanceId)return send(res,409,{code:'PASSPORT_INSTANCE_CONFLICT'});
          if(body.expectedPassportVersion!==data.passport.version)return send(res,409,{code:'PASSPORT_VERSION_CONFLICT'});
          if(data.checks.length)return send(res,409,{code:'CHECKS_ALREADY_EXIST'});
          data.checks=checkTypes.map(checkType=>({id:require('node:crypto').randomUUID(),projectId:id,version:0,checkType,required:true,status:'NOT_STARTED',owner:null,plannedDate:null,actualDate:null,comment:null}));data.passport.version++;
          // Fixed HTTP response fixture; production assessment remains server-side.
          data.assessment={...missingArchitecture(id).assessment,reasons:[{status:'NOT_ASSESSED',entityType:'PASSPORT',entityId:id,code:'CHECKS_INCOMPLETE',message:'Проверки не завершены'}]};
        }else{
          if(!record)return send(res,404,{code:'CHECK_NOT_FOUND'});
          if(body.expectedVersion!==record.version)return send(res,409,{code:'CHECK_VERSION_CONFLICT',currentVersion:record.version,versionResource:'CHECK'});
          const next={...record,...body};
          const invalid=next.required&&next.status==='NOT_REQUIRED'||!next.required&&next.status!=='NOT_REQUIRED'?'status':!next.required&&!next.comment?.trim()?'comment':next.status==='PASSED'?!next.actualDate?'actualDate':null:next.actualDate?'actualDate':null;
          if(invalid)return send(res,400,{code:'VALIDATION_ERROR',fieldErrors:{[invalid]:'Invalid value'}});
          for(const field of ['required','status','owner','plannedDate','actualDate','comment'])if(Object.hasOwn(body,field))record[field]=body[field];record.version++;
        }
        if(id==='check-unknown')return send(res,503,{code:'DATABASE_ERROR'});
        return send(res,initialize?201:200,data);
      });
    }
    if(kind) {
      if(!childMode || req.method==='GET')return send(res,405,{code:'BAD_REQUEST'});
      let raw='';req.on('data',chunk=>{raw+=chunk;});return req.on('end',()=>{
        let body;try{body=JSON.parse(raw);}catch{return send(res,400,{code:'BAD_REQUEST'});}
        const attempt=(writes.get(id)||0)+1;writes.set(id,attempt);
        const record=data[kind].find(row=>row.id===recordId);
        if(id==='child-validation'&&attempt===1)return send(res,400,{code:'VALIDATION_ERROR',fieldErrors:{name:'Invalid value'}});
        if(id==='child-recreate'&&attempt===1){data.passport={...data.passport,instanceId:require('node:crypto').randomUUID(),version:0};data.components=[];data.integrations=[];return send(res,req.method==='POST'?409:404,{code:req.method==='POST'?'PASSPORT_INSTANCE_CONFLICT':kind==='components'?'COMPONENT_NOT_FOUND':'INTEGRATION_NOT_FOUND'});}
        if(id==='child-deleted'&&attempt===1&&record){data[kind]=data[kind].filter(r=>r.id!==recordId);return send(res,404,{code:kind==='components'?'COMPONENT_NOT_FOUND':'INTEGRATION_NOT_FOUND'});}
        if((id==='child-version'||id==='child-passport')&&attempt===1){
          if(record){record.version++;record.name='Запись изменена другим участником';}else data.passport.version++;
          return send(res,409,{code:record?kind==='components'?'COMPONENT_VERSION_CONFLICT':'INTEGRATION_VERSION_CONFLICT':'PASSPORT_VERSION_CONFLICT',currentVersion:record?record.version:data.passport.version,versionResource:record?kind==='components'?'COMPONENT':'INTEGRATION':'PASSPORT'});
        }
        if(req.method==='POST') {
          if(body.expectedPassportInstanceId!==data.passport.instanceId)return send(res,409,{code:'PASSPORT_INSTANCE_CONFLICT'});
          if(body.expectedPassportVersion!==data.passport.version)return send(res,409,{code:'PASSPORT_VERSION_CONFLICT'});
        }else if(!record)return send(res,404,{code:kind==='components'?'COMPONENT_NOT_FOUND':'INTEGRATION_NOT_FOUND'});
        else if(body.expectedVersion!==record.version)return send(res,409,{code:kind==='components'?'COMPONENT_VERSION_CONFLICT':'INTEGRATION_VERSION_CONFLICT'});
        const required=kind==='components'?['name','componentType','criticality','externalComponent','position']:['name','sourceSystem','targetSystem','direction','interactionType','authCategory','criticality','readiness','dataClasses'];
        const next={...(record??{}),...body};
        const invalid=required.find(key=>next[key]==null||next[key]===''||(key==='dataClasses'&&!next[key].length));
        if(invalid)return send(res,400,{code:'VALIDATION_ERROR',fieldErrors:{[invalid]:'Invalid value'}});
        const fields=kind==='components'?[...required,'purpose','platform']:[...required,'responsibleParty','comment'];
        const target=record??{id:require('node:crypto').randomUUID(),projectId:id,version:0};
        for(const field of fields) if(Object.hasOwn(body,field))target[field]=body[field];else if(!record&&!required.includes(field))target[field]=null;
        if(record)target.version++;else{data[kind].push(target);data.passport.version++;}
        if(id==='child-unknown')return send(res,503,{code:'DATABASE_ERROR'});
        return send(res,record?200:201,data);
      });
    }
    if(req.method!=='GET') {
      let raw=''; req.on('data',chunk=>{raw+=chunk;});
      return req.on('end',()=>{
        let body;try{body=JSON.parse(raw);}catch{return send(res,400,{code:'BAD_REQUEST'});}
        const attempt=(writes.get(id)||0)+1;writes.set(id,attempt);
        if(id==='write-validation'&&attempt===1) return send(res,400,{code:'VALIDATION_ERROR',fieldErrors:{architect:'Invalid value'}});
        if(['write-version','write-recreate'].includes(id)&&attempt===1) {
          data.passport={...data.passport,architect:'Другой тестовый архитектор',version:id==='write-version'?data.passport.version+1:0,instanceId:id==='write-recreate'?'22c1bf4a-ceaa-4f7b-9b26-5a3a0d7245a3':data.passport.instanceId};
          return send(res,409,{code:id==='write-recreate'?'PASSPORT_INSTANCE_CONFLICT':'PASSPORT_VERSION_CONFLICT',currentVersion:data.passport.version,versionResource:'PASSPORT'});
        }
        if(req.method==='POST') {
          if(data.passport) return send(res,409,{code:'PASSPORT_ALREADY_EXISTS',currentVersion:data.passport.version,versionResource:'PASSPORT'});
          if(body.expectedProjectVersion!==data.project.version) return send(res,409,{code:'PROJECT_VERSION_CONFLICT',currentVersion:data.project.version,versionResource:'PROJECT'});
          if(!body.criticality||!body.stage||!body.solutionType) return send(res,400,{code:'VALIDATION_ERROR',fieldErrors:{criticality:'Invalid value'}});
          data.passport={projectId:id,instanceId:require('node:crypto').randomUUID(),version:0,documentVersion:'1.0',architect:null,solutionSummary:null,availabilityRequirement:null,recoveryRequirement:null,lastReviewDate:null,createdAt:'2026-10-23T12:00:00Z',updatedAt:'2026-10-23T12:00:00Z'};
          data.project.version++;
        } else {
          if(!data.passport) return send(res,404,{code:'PASSPORT_NOT_FOUND'});
          if(body.expectedPassportInstanceId!==data.passport.instanceId) return send(res,409,{code:'PASSPORT_INSTANCE_CONFLICT',currentVersion:data.passport.version,versionResource:'PASSPORT'});
          if(body.expectedVersion!==data.passport.version) return send(res,409,{code:'PASSPORT_VERSION_CONFLICT',currentVersion:data.passport.version,versionResource:'PASSPORT'});
          data.passport.version++;data.passport.updatedAt='2026-10-23T12:30:00Z';
        }
        for(const key of ['criticality','stage','solutionType','architect','solutionSummary','availabilityRequirement','recoveryRequirement','lastReviewDate']) if(Object.hasOwn(body,key)) data.passport[key]=body[key];
        for(const key of ['dataClasses','environments']) if(Object.hasOwn(body,key)) data[key]=body[key];
        // Explicit fixed assessment fixture, not a second implementation of the readiness formula.
        data.assessment={...missingArchitecture(id).assessment,reasons:[{status:'NOT_ASSESSED',entityType:'PASSPORT',entityId:id,code:'CHECKS_INCOMPLETE',message:'Проверки не завершены'}]};
        if(id==='write-unknown') return send(res,503,{code:'DATABASE_ERROR'});
        return send(res,req.method==='POST'?201:200,data);
      });
    }
    if(id==='review-error') {const count=attempts.get(id)||0;attempts.set(id,count+1);if(count===0) return send(res,503,{error:'Unavailable'});}
    if(id==='review-slow') return setTimeout(()=>send(res,200,data),8000);
    return send(res,200,data);
  }
  return send(res,404,{error:'Not found'});
}).listen(riskMode?4179:checkMode?4178:childMode?4177:writeMode?4176:4175,'127.0.0.1',()=>console.log(`Isolated frontend fixtures: http://127.0.0.1:${riskMode?4179:checkMode?4178:childMode?4177:writeMode?4176:4175} (no database; ${riskMode?'risks memory writes':checkMode?'checks memory writes':childMode?'child memory writes':writeMode?'passport memory writes':'read-only'})`));
