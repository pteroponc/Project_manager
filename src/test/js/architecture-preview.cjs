// Isolated HTTP fixture server for browser review. No Spring, datasource or database access.
// Run: node src/test/js/architecture-preview.cjs; open http://127.0.0.1:4175/?view=projects
'use strict';
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const {architecture,missingArchitecture} = require('./fixtures/architecture.cjs');
const writeMode=process.argv.includes('--passport-editor');
const stored=new Map(), writes=new Map();
const root = path.resolve(__dirname,'../../main/resources/public');
const assets = {'/':'index.html','/index.html':'index.html','/app.js':'app.js','/projects-ui.js':'projects-ui.js','/architecture-ui.js':'architecture-ui.js','/passport-editor.js':'passport-editor.js','/styles.css':'styles.css'};
const names = {'review-full':'Тестовый проект «Приём заявок»','review-empty':'Тестовый пустой паспорт','review-missing':'Тестовый проект без паспорта','review-error':'Тестовый повтор загрузки','review-slow':'Тестовый медленный ответ'};
if(writeMode) Object.assign(names,{'write-create':'Тестовый новый паспорт','write-validation':'Тестовая ошибка поля','write-version':'Тестовый конфликт версии','write-recreate':'Тестовый новый экземпляр','write-unknown':'Тестовый неопределённый результат'});
const projects = Object.entries(names).map(([id,name])=>({id,name,owner:'Тестовая команда',quarter:'Q4 2026',status:'active',health:'green',progress:0,deadline:'2026-12-15',startDate:null,summary:'Изолированные тестовые данные C.2',deliveryModel:'kanban',assessment:{requiresAttention:false,attentionReasons:[],dataQualityIssues:[]}}));
const attempts = new Map();
function send(response,status,data) {response.writeHead(status,{'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store'});response.end(JSON.stringify(data));}
http.createServer((req,res)=>{
  if(req.method!=='GET' && !(writeMode && ['POST','PATCH'].includes(req.method) && /^\/api\/projects\/[^/]+\/architecture$/.test(new URL(req.url,'http://127.0.0.1').pathname))) return send(res,405,{error:'Read-only fixture server'});
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
  const match=url.pathname.match(/^\/api\/projects\/([^/]+)\/(card|architecture)$/);
  if(match) {
    const [,id,resource]=match;
    if(!Object.hasOwn(names,id)) return send(res,404,{code:'PROJECT_NOT_FOUND',error:'Project not found'});
    const project=projects.find(p=>p.id===id);
    if(resource==='card') return send(res,200,{...project,version:0,board:{viewAvailable:true,taskCount:0},milestoneCount:0,milestones:[]});
    const data=stored.get(id) ?? (id==='review-missing'||id==='write-create'?missingArchitecture(id):architecture(id));
    data.project.name=project.name;
    if(id==='review-empty') Object.assign(data,{components:[],integrations:[],checks:[],risks:[],dataClasses:[],environments:[],passport:{...data.passport,stage:'IN_DESIGN',architect:null,solutionSummary:null,availabilityRequirement:null,recoveryRequirement:null,lastReviewDate:null},assessment:{...data.assessment,status:'NOT_ASSESSED',statusLabel:'Оценка не завершена',reasons:[{status:'NOT_ASSESSED',entityType:'PASSPORT',entityId:id,code:'REQUIRED_INFORMATION_INCOMPLETE',message:'Сведения паспорта неполны'},{status:'NOT_ASSESSED',entityType:'PASSPORT',entityId:id,code:'ARCHITECTURE_NOT_APPROVED',message:'Архитектура не утверждена'},{status:'NOT_ASSESSED',entityType:'PASSPORT',entityId:id,code:'CHECKS_INCOMPLETE',message:'Проверки не завершены'}],upcomingChecks:[],acceptedHighRisks:[]}});
    if(writeMode) stored.set(id,data);
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
}).listen(writeMode?4176:4175,'127.0.0.1',()=>console.log(`Isolated frontend fixtures: http://127.0.0.1:${writeMode?4176:4175} (no database; ${writeMode?'passport memory writes':'read-only'})`));
