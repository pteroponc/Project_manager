// Isolated HTTP fixture server for browser review. No Spring, datasource or database access.
// Run: node src/test/js/architecture-preview.cjs; open http://127.0.0.1:4175/?view=projects
'use strict';
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const {architecture,missingArchitecture} = require('./fixtures/architecture.cjs');
const root = path.resolve(__dirname,'../../main/resources/public');
const assets = {'/':'index.html','/index.html':'index.html','/app.js':'app.js','/projects-ui.js':'projects-ui.js','/architecture-ui.js':'architecture-ui.js','/styles.css':'styles.css'};
const names = {'review-full':'Тестовый проект «Приём заявок»','review-empty':'Тестовый пустой паспорт','review-missing':'Тестовый проект без паспорта','review-error':'Тестовый повтор загрузки','review-slow':'Тестовый медленный ответ'};
const projects = Object.entries(names).map(([id,name])=>({id,name,owner:'Тестовая команда',quarter:'Q4 2026',status:'active',health:'green',progress:0,deadline:'2026-12-15',startDate:null,summary:'Изолированные тестовые данные C.2',deliveryModel:'kanban',assessment:{requiresAttention:false,attentionReasons:[],dataQualityIssues:[]}}));
const attempts = new Map();
function send(response,status,data) {response.writeHead(status,{'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store'});response.end(JSON.stringify(data));}
http.createServer((req,res)=>{
  if(req.method!=='GET') return send(res,405,{error:'Read-only fixture server'});
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
    const data=id==='review-missing'?missingArchitecture(id):architecture(id);
    data.project.name=project.name;
    if(id==='review-empty') Object.assign(data,{components:[],integrations:[],checks:[],risks:[],dataClasses:[],environments:[],passport:{...data.passport,stage:'IN_DESIGN',architect:null,solutionSummary:null,availabilityRequirement:null,recoveryRequirement:null,lastReviewDate:null},assessment:{...data.assessment,status:'NOT_ASSESSED',statusLabel:'Оценка не завершена',reasons:[{status:'NOT_ASSESSED',entityType:'PASSPORT',entityId:id,code:'REQUIRED_INFORMATION_INCOMPLETE',message:'Сведения паспорта неполны'},{status:'NOT_ASSESSED',entityType:'PASSPORT',entityId:id,code:'ARCHITECTURE_NOT_APPROVED',message:'Архитектура не утверждена'},{status:'NOT_ASSESSED',entityType:'PASSPORT',entityId:id,code:'CHECKS_INCOMPLETE',message:'Проверки не завершены'}],upcomingChecks:[],acceptedHighRisks:[]}});
    if(id==='review-error') {const count=attempts.get(id)||0;attempts.set(id,count+1);if(count===0) return send(res,503,{error:'Unavailable'});}
    if(id==='review-slow') return setTimeout(()=>send(res,200,data),8000);
    return send(res,200,data);
  }
  return send(res,404,{error:'Not found'});
}).listen(4175,'127.0.0.1',()=>console.log('Isolated frontend fixtures: http://127.0.0.1:4175 (no database)'));
