// Contract fixtures for isolated frontend tests, never loaded by the application.
const checkTypes = ['ARCHITECTURE_APPROVAL','SECURITY_REQUIREMENTS','DATA_CLASSIFICATION','THREAT_MODEL','SECURITY_ASSESSMENT','ACCESS_CONTROL','LOGGING_MONITORING','BACKUP_RECOVERY','LOAD_TESTING','PRODUCTION_ADMISSION'];
function architecture(id = 'p1', overrides = {}) {
  return {
    project: {id,name:'Тестовый проект «Приём заявок»',owner:'Тестовая команда',quarter:'Q4 2026',projectStatus:'active',version:981237},
    passport: {projectId:id,instanceId:'f071002a-3da5-4bc6-b472-6ba147514ea2',version:981238,documentVersion:'1.0',criticality:'HIGH',stage:'APPROVED',solutionType:'NEW_SOLUTION',architect:'Тестовый архитектор',solutionSummary:'Управленческое описание решения.\nВторая строка.',availabilityRequirement:'Доступность в рабочие часы',recoveryRequirement:'Восстановление за четыре часа',lastReviewDate:'2026-10-20',createdAt:'2026-10-12T08:30:00Z',updatedAt:'2026-10-20T09:45:00Z'},
    dataClasses:['INTERNAL','PERSONAL'],environments:['TEST','PREPROD','PROD'],
    components:[{id:'cmp-test',projectId:id,version:981239,name:'Сервис заявок',componentType:'BACKEND',purpose:'Обработка заявок',platform:'Java',criticality:'HIGH',externalComponent:false,position:0}],
    integrations:[{id:'int-test',projectId:id,version:981240,name:'Каталог услуг',sourceSystem:'Приём заявок',targetSystem:'Каталог',direction:'OUTBOUND',interactionType:'API',authCategory:'OAUTH2',criticality:'HIGH',readiness:'IN_PROGRESS',responsibleParty:'Команда каталога',comment:'Согласование регламента',dataClasses:['INTERNAL']}],
    checks:checkTypes.map((checkType,i)=>({id:'check-'+i,projectId:id,version:981241,checkType,required:true,status:i===9?'CHANGES_REQUIRED':'PASSED',owner:'Тестовый специалист',plannedDate:i===9?'2026-10-27':'2026-10-20',actualDate:i===9?null:'2026-10-20',comment:i===9?'Завершить регламент':null})),
    risks:[{id:'risk-test',projectId:id,version:981242,name:'Резервная обработка',description:'Описание управленческого риска',category:'PROCESS',probability:'MEDIUM',impact:'HIGH',level:'HIGH',status:'ACCEPTED',owner:'Тестовая команда',treatmentDueDate:'2026-10-30',treatmentPlan:'Проверить резервный процесс',closedDate:null,acceptanceJustification:'Временный процесс до завершения пилота',acceptanceVerification:'UNVERIFIED'}],
    assessment:{status:'BLOCKED',statusLabel:'Заблокировано',calculationDate:'2026-10-23',timeZone:'Europe/Moscow',officialGoLiveApproval:false,
      reasons:[{status:'BLOCKED',code:'PRODUCTION_ADMISSION_CHANGES_REQUIRED',entityType:'CHECK',entityId:'check-9',message:'Проверка перед запуском требует изменений'},
        {status:'ATTENTION_REQUIRED',code:'ACCEPTED_HIGH_RISK',entityType:'RISK',entityId:'risk-test',message:'Высокий или критический риск отмечен как принятый; полномочия не проверены'},
        {status:'NOT_ASSESSED',code:'CHECKS_INCOMPLETE',entityType:'PASSPORT',entityId:id,message:'Проверки не завершены'},
        {status:'NOT_ASSESSED',code:'CHECK_INCOMPLETE',entityType:'CHECK',entityId:'check-9',message:'Проверка не завершена корректно'},
        {status:'NOT_ASSESSED',code:'INTEGRATION_NOT_READY',entityType:'INTEGRATION',entityId:'int-test',message:'Интеграция высокой критичности не готова'}],
      upcomingChecks:[{id:'check-9',checkType:'PRODUCTION_ADMISSION',status:'CHANGES_REQUIRED',plannedDate:'2026-10-27'}],
      acceptedHighRisks:[{id:'risk-test',level:'HIGH',justification:'Временный процесс до завершения пилота',acceptanceVerification:'UNVERIFIED'}]},
    ...overrides
  };
}
function missingArchitecture(id = 'review-missing') {
  return architecture(id,{passport:null,dataClasses:[],environments:[],components:[],integrations:[],checks:[],risks:[],assessment:{status:'NOT_ASSESSED',statusLabel:'Оценка не завершена',calculationDate:'2026-10-23',timeZone:'Europe/Moscow',officialGoLiveApproval:false,reasons:[{status:'NOT_ASSESSED',code:'PASSPORT_MISSING',entityType:'PASSPORT',entityId:id,message:'Архитектурный паспорт отсутствует'}],upcomingChecks:[],acceptedHighRisks:[]}});
}
module.exports = {architecture,missingArchitecture};
