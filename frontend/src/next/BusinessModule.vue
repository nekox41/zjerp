<script setup>
import { onMounted, ref, watch } from 'vue'
import { api, downloadFile } from './api'

const section = ref('customers')
const busy = ref(false)
const error = ref('')
const notice = ref('')
const q = ref('')
const employees = ref([])
const identities = ref([])
const projectMembers = ref([])
const projectLogs = ref([])
const projectIssues = ref([])
const workPackages = ref([])
const serviceRecords = ref([])
const qualifications = ref([])
const selectedEmployee = ref(null)
const selectedWorkPackage = ref(null)
const reportList = ref([])
const selectedReport = ref(null)
const reportForm = ref({reportNo:'',title:'',reportType:'service',summary:'',scope:'',methods:'',findings:'',conclusion:''})
const reportActionReason = ref('')
const reportBusy = ref(false)
const memberEmployeeId = ref('')
const customers = ref([])
const contracts = ref([])
const projects = ref([])
const selectedCustomer = ref(null)
const contacts = ref([])
const milestones = ref([])
const forms = {
  employee: ref({employeeCode:'',fullName:'',employeeType:'employee',identityId:'',positionName:'',phone:'',email:''}),
  customer: ref({customerCode:'',name:'',creditCode:'',customerType:'enterprise',industry:'',phone:'',email:'',address:'',ownerEmployeeId:'',notes:''}),
  contact: ref({name:'',positionName:'',phone:'',email:'',isPrimary:false}),
  contract: ref({contractNo:'',title:'',customerId:'',contractType:'maintenance',signingMode:'direct',intermediaryName:'',intermediarySharePercent:'',amount:'0',currency:'CNY',signedOn:'',startsOn:'',endsOn:'',ownerEmployeeId:'',notes:''}),
  project: ref({projectNo:'',name:'',customerId:'',contractId:'',managerEmployeeId:'',technicalLeadEmployeeId:'',actualStart:'',actualEnd:'',honeybeeUploaded:false,documentsReady:false,maintenanceCardSent:false,description:''}),
  milestone: ref({title:'',description:'',dueOn:''}),
  qualification: ref({qualificationName:'',certificateNo:'',issuingAuthority:'',issuedOn:'',expiresOn:'',notes:''}),
  workPackage: ref({packageNo:'',title:'',description:'',assigneeEmployeeId:'',plannedStart:'',plannedEnd:''}),
  issue: ref({title:'',description:'',severity:'medium',ownerEmployeeId:'',dueOn:''}),
  record: ref({title:'',content:'',recordType:'field',performedOn:''}),
  amendment: ref({amendmentNo:'',title:'',changeSummary:'',revisedAmount:'',effectiveOn:''})
}
const rows = ref([])
const config = {
  employees:{title:'员工档案',endpoint:'/employees',form:'employee',permission:'employee:write'},
  customers:{title:'客户管理',endpoint:'/customers',form:'customer',permission:'customer:write'},
  contracts:{title:'合同管理',endpoint:'/contracts',form:'contract',permission:'contract:write'},
  projects:{title:'项目管理',endpoint:'/projects',form:'project',permission:'project:write'}
}
async function load() {
  error.value=''; notice.value=''
  try {
    const [e,c,ct,p,ids] = await Promise.all([
      api('/business/employees').catch(()=>[]),
      api('/business/customers').catch(()=>[]),
      api('/business/contracts').catch(()=>[]),
      api('/business/projects').catch(()=>[]),
      api('/admin/identities').catch(()=>[])
    ])
    employees.value=e; customers.value=c; contracts.value=ct; projects.value=p; identities.value=ids
    const cfg=config[section.value]
    rows.value=cfg ? await api('/business'+cfg.endpoint+'?q='+encodeURIComponent(q.value)).catch(()=>[]) : []
  } catch(e) { error.value=e.message }
}
async function submit(kind) {
  busy.value=true; error.value=''; notice.value=''
  try {
    const data={...forms[kind].value}
    Object.keys(data).forEach(k=>{if(data[k]==='') data[k]=null})
    const selectedId = value => {
      if (!value?.id) throw new Error('请先打开对应记录，再执行此操作。')
      return value.id
    }
    const endpoints = {
      employee: () => '/employees',
      customer: () => '/customers',
      contact: () => '/customers/' + selectedId(selectedCustomer.value) + '/contacts',
      contract: () => '/contracts',
      project: () => '/projects',
      milestone: () => '/projects/' + selectedId(selectedProject.value) + '/milestones',
      qualification: () => '/employees/' + selectedId(selectedEmployee.value) + '/qualifications',
      workPackage: () => '/projects/' + selectedId(selectedProject.value) + '/work-packages',
      issue: () => '/projects/' + selectedId(selectedProject.value) + '/issues',
      record: () => '/work-packages/' + selectedId(selectedWorkPackage.value) + '/records',
      amendment: () => '/contracts/' + selectedId(selectedContract.value) + '/amendments'
    }
    const endpoint = endpoints[kind]
    if (!endpoint) throw new Error('不支持的操作')
    await api('/business' + endpoint(),{method:'POST',body:JSON.stringify(data)})
    notice.value='保存成功'; forms[kind].value=Object.fromEntries(Object.keys(forms[kind].value).map(k=>[k,typeof forms[k].value[k]==='boolean'?false:'']))
    if(kind==='employee') forms.employee.value.employeeType='employee'
    if(kind==='contract') { forms.contract.value.contractType='maintenance'; forms.contract.value.signingMode='direct'; forms.contract.value.currency='CNY'; forms.contract.value.amount='0' }
    await load()
    if(kind==='contact') await openCustomer(selectedCustomer.value.id)
    if(['milestone','workPackage','issue'].includes(kind)) await openProject(selectedProject.value.id)
    if(kind==='qualification') await openEmployee(selectedEmployee.value.id)
    if(kind==='amendment') await openContract(selectedContract.value.id)
    if(kind==='record') await openWorkPackage(selectedWorkPackage.value.id)
  } catch(e) { error.value=e.message } finally { busy.value=false }
}
async function openCustomer(id) {
  try { selectedCustomer.value=await api('/business/customers/'+id); contacts.value=selectedCustomer.value.contacts||[] }
  catch(e){error.value=e.message}
}
const selectedProject=ref(null)
const projectEdit=ref({managerEmployeeId:'',technicalLeadEmployeeId:'',actualStart:'',actualEnd:'',honeybeeUploaded:false,documentsReady:false,maintenanceCardSent:false,description:''})
const contractLinkForm=ref({contractId:'',relationType:'renewal',isCurrent:true})
const selectedContract=ref(null)
const contractAmendments=ref([])
async function openEmployee(id) { try { selectedEmployee.value=await api('/business/employees/'+id) } catch(e){error.value=e.message} }
async function openWorkPackage(id) { try { selectedWorkPackage.value=workPackages.value.find(x=>x.id===id)||{id}; const [records,reports]=await Promise.all([api('/business/work-packages/'+id+'/records'),api('/business/work-packages/'+id+'/reports')]); serviceRecords.value=records; reportList.value=reports; selectedReport.value=null } catch(e){error.value=e.message} }
async function createReport() { reportBusy.value=true; try { const f=reportForm.value; const created=await api('/business/work-packages/'+selectedWorkPackage.value.id+'/reports',{method:'POST',body:JSON.stringify({reportNo:f.reportNo,title:f.title,reportType:f.reportType,content:{summary:f.summary,scope:f.scope,methods:f.methods,findings:f.findings,conclusion:f.conclusion}})}); reportForm.value={reportNo:'',title:'',reportType:'service',summary:'',scope:'',methods:'',findings:'',conclusion:''}; await openWorkPackage(selectedWorkPackage.value.id); await openReport(created.id); notice.value='报告草稿已创建' } catch(e){error.value=e.message} finally{reportBusy.value=false} }
async function openReport(id) { try { selectedReport.value=await api('/business/reports/'+id); const x=selectedReport.value.content||{}; reportForm.value={reportNo:selectedReport.value.report_no,title:selectedReport.value.title,reportType:selectedReport.value.report_type,summary:x.summary||'',scope:x.scope||'',methods:x.methods||'',findings:x.findings||'',conclusion:x.conclusion||''} } catch(e){error.value=e.message} }
async function saveReport() { if(!selectedReport.value)return; reportBusy.value=true; try { const f=reportForm.value,reportId=selectedReport.value.id; selectedReport.value=await api('/business/reports/'+reportId,{method:'PUT',body:JSON.stringify({expectedVersionNo:selectedReport.value.current_version_no,title:f.title,content:{summary:f.summary,scope:f.scope,methods:f.methods,findings:f.findings,conclusion:f.conclusion}})}); await openWorkPackage(selectedWorkPackage.value.id); await openReport(reportId); notice.value='报告版本已保存' } catch(e){error.value=e.message} finally{reportBusy.value=false} }
async function uploadReportAttachment(event) { const file=event.target.files?.[0]; if(!file||!selectedReport.value)return; reportBusy.value=true; try { const form=new FormData();form.append('file',file);const uploaded=await api('/files',{method:'POST',body:form});selectedReport.value=await api('/business/reports/'+selectedReport.value.id+'/attachments',{method:'POST',body:JSON.stringify({fileId:uploaded.id})});await openReport(selectedReport.value.id);notice.value='附件已上传并关联' } catch(e){error.value=e.message} finally{reportBusy.value=false;event.target.value=''} }
async function reportAction(action) { if(!selectedReport.value)return; reportBusy.value=true;try { const body=['return','withdraw'].includes(action)?{reason:reportActionReason.value}:{};const updated=await api('/business/reports/'+selectedReport.value.id+'/'+action,{method:'POST',body:JSON.stringify(body)});reportActionReason.value='';await openWorkPackage(selectedWorkPackage.value.id);await openReport(updated.id);notice.value='报告状态已更新' }catch(e){error.value=e.message}finally{reportBusy.value=false} }
async function openContract(id) { try { selectedContract.value=await api('/business/contracts/'+id); contractAmendments.value=await api('/business/contracts/'+id+'/amendments') } catch(e){error.value=e.message} }
async function removeMember(projectId,employeeId) { try { await api('/business/projects/'+projectId+'/members/'+employeeId,{method:'DELETE'}); await openProject(projectId); notice.value='成员已移除' } catch(e){error.value=e.message} }
async function skipMilestone(id) { try { await api('/business/milestones/'+id+'/skip',{method:'PUT'}); await openProject(selectedProject.value.id); notice.value='里程碑已跳过' } catch(e){error.value=e.message} }
async function openProject(id) {
  try {
    selectedProject.value=await api('/business/projects/'+id)
    projectEdit.value={managerEmployeeId:selectedProject.value.manager_employee_id||'',technicalLeadEmployeeId:selectedProject.value.technical_lead_employee_id||'',actualStart:selectedProject.value.actual_start||'',actualEnd:selectedProject.value.actual_end||'',honeybeeUploaded:!!selectedProject.value.honeybee_uploaded,documentsReady:!!selectedProject.value.documents_ready,maintenanceCardSent:!!selectedProject.value.maintenance_card_sent,description:selectedProject.value.description||''}
    const [m,pm,pl,pi,wp]=await Promise.all([api('/business/projects/'+id+'/milestones'),api('/business/projects/'+id+'/members'),api('/business/projects/'+id+'/logs'),api('/business/projects/'+id+'/issues'),api('/business/projects/'+id+'/work-packages')])
    milestones.value=m; projectMembers.value=pm; projectLogs.value=pl; projectIssues.value=pi; workPackages.value=wp
  } catch(e){error.value=e.message}
}
async function saveProject() {
  if(!selectedProject.value?.id)return
  busy.value=true;error.value=''
  try {
    await api('/business/projects/'+selectedProject.value.id,{method:'PUT',body:JSON.stringify(projectEdit.value)})
    await openProject(selectedProject.value.id);notice.value='项目已保存'
  } catch(e){error.value=e.message} finally{busy.value=false}
}
async function linkContract() {
  if(!selectedProject.value?.id||!contractLinkForm.value.contractId)return
  busy.value=true;error.value=''
  try {
    await api('/business/projects/'+selectedProject.value.id+'/contracts',{method:'POST',body:JSON.stringify({...contractLinkForm.value,contractId:Number(contractLinkForm.value.contractId)})})
    await openProject(selectedProject.value.id)
    contractLinkForm.value={contractId:'',relationType:'renewal',isCurrent:true}
    notice.value='合同已关联'
  } catch(e){error.value=e.message} finally{busy.value=false}
}
async function completeMilestone(id) {
  try { await api('/business/milestones/'+id+'/complete',{method:'PUT'}); await openProject(selectedProject.value.id); notice.value='里程碑已完成' }
  catch(e){error.value=e.message}
}
async function addMember(projectId) {
  const employeeId=memberEmployeeId.value
  if(!employeeId) return
  try { await api('/business/projects/'+projectId+'/members',{method:'POST',body:JSON.stringify({employeeId,role:'项目成员'})}); await openProject(projectId); notice.value='成员已分配'; memberEmployeeId.value='' }
  catch(e){error.value=e.message}
}
watch(section,()=>{q.value='';load()})
onMounted(load)
</script>

<template>
<section class="business-module">
  <div class="business-tabs">
    <button v-for="(v,k) in config" :key="k" :class="{active:section===k}" @click="section=k">{{v.title}}</button>
  </div>
  <p v-if="error" class="message error">{{error}}</p><p v-if="notice" class="message success">{{notice}}</p>
  <div class="business-layout">
    <div class="business-list panel">
      <div class="business-list-head"><div><h3>{{config[section].title}}</h3></div><input v-model="q" placeholder="搜索" @keyup.enter="load"><button class="secondary-button" @click="load">搜索</button></div>
      <div class="business-records">
        <button v-for="r in rows" :key="r.id" class="business-record" @click="section==='customers'?openCustomer(r.id):section==='projects'?openProject(r.id):section==='contracts'?openContract(r.id):section==='employees'?openEmployee(r.id):null">
          <b>{{r.name||r.full_name||r.title}}</b><small>{{r.customer_code||r.employee_code||r.contract_no||r.project_no||r.status||r.position_name}}</small>
          <span>{{r.customer_name||r.organization_name||r.owner_name||''}}</span>
        </button>
        <p v-if="!rows.length" class="empty">暂无记录</p>
      </div>
    </div>
    <div class="business-forms panel">
      <template v-if="section==='employees'">
        <h3>新增员工档案</h3><form class="create-grid" @submit.prevent="submit('employee')"><label>员工编号<input v-model="forms.employee.value.employeeCode"></label><label>姓名<input v-model="forms.employee.value.fullName" required></label><label>员工类型<select v-model="forms.employee.value.employeeType"><option value="engineer">工程师</option><option value="operator">操作员</option><option value="employee">普通员工</option><option value="manager">管理人员</option></select></label><label>岗位<input v-model="forms.employee.value.positionName"></label><label>电话<input v-model="forms.employee.value.phone"></label><label>邮箱<input v-model="forms.employee.value.email" type="email"></label><label>关联登录账号<select v-model="forms.employee.value.identityId"><option value="">暂不关联</option><option v-for="x in identities" :value="x.id">{{x.username}} · {{x.display_name}}</option></select></label><button class="primary-button">创建员工</button></form>
        <div v-if="selectedEmployee" class="related-card"><h4>{{selectedEmployee.full_name}}</h4><p>员工类型：{{({engineer:'工程师',operator:'操作员',employee:'普通员工',manager:'管理人员'})[selectedEmployee.employee_type]||'普通员工'}}</p><p>电话：{{selectedEmployee.phone||'—'}} · 邮箱：{{selectedEmployee.email||'—'}}</p><p>状态：{{selectedEmployee.status==='active'?'在职':'停用'}}</p></div>
      </template>
      <template v-else-if="section==='customers'">
        <h3>新增客户</h3><form class="create-grid" @submit.prevent="submit('customer')"><label>客户编号<input v-model="forms.customer.value.customerCode" required></label><label>客户名称<input v-model="forms.customer.value.name" required></label><label>统一社会信用代码<input v-model="forms.customer.value.creditCode"></label><label>客户类型<select v-model="forms.customer.value.customerType"><option value="enterprise">企业</option><option value="government">政府/事业单位</option><option value="individual">个人</option></select></label><label>行业<input v-model="forms.customer.value.industry"></label><label>负责人<select v-model="forms.customer.value.ownerEmployeeId"><option value="">未分配</option><option v-for="x in employees" :value="x.id">{{x.full_name}}</option></select></label><label>联系电话<input v-model="forms.customer.value.phone"></label><label>电子邮箱<input v-model="forms.customer.value.email"></label><label>地址<input v-model="forms.customer.value.address"></label><label>备注<input v-model="forms.customer.value.notes"></label><button class="primary-button">创建客户</button></form>
        <div v-if="selectedCustomer" class="related-card"><h4>{{selectedCustomer.name}} · 联系人</h4><p v-for="x in contacts" :key="x.id">{{x.name}} · {{x.phone||'无电话'}} · {{x.email||''}} <span v-if="x.is_primary">主要联系人</span></p><form class="create-grid compact" @submit.prevent="submit('contact')"><label>联系人姓名<input v-model="forms.contact.value.name" required></label><label>岗位<input v-model="forms.contact.value.positionName"></label><label>电话<input v-model="forms.contact.value.phone"></label><label>邮箱<input v-model="forms.contact.value.email"></label><label class="inline-check"><input type="checkbox" v-model="forms.contact.value.isPrimary">主要联系人</label><button class="primary-button">添加联系人</button></form><h4>关联合同</h4><p v-for="c in selectedCustomer.contracts||[]" :key="c.id" class="log-line">{{c.contract_no}} · {{c.title}} · {{c.status}} · {{c.amount}} {{c.currency}}</p></div>
      </template>
      <template v-else-if="section==='contracts'">
        <h3>新增合同</h3><form class="create-grid" @submit.prevent="submit('contract')"><label>合同编号<input v-model="forms.contract.value.contractNo" required></label><label>合同名称<input v-model="forms.contract.value.title" required></label><label>客户<select v-model="forms.contract.value.customerId" required><option value="">请选择客户</option><option v-for="x in customers" :value="x.id">{{x.name}}</option></select></label><label>合同类型<select v-model="forms.contract.value.contractType"><option value="maintenance">维护保养合同</option><option value="fire_inspection">消防检测合同</option><option value="fire_evaluation">消防评定合同</option><option value="fire_assessment">消防评估合同</option><option value="other">其他技术服务合同</option></select></label><label>签约方式<select v-model="forms.contract.value.signingMode"><option value="direct">公司直接签约</option><option value="intermediary">中间人介绍</option></select></label><template v-if="forms.contract.value.signingMode==='intermediary'"><label>中间人<input v-model="forms.contract.value.intermediaryName" required></label><label>分成比例（%）<input v-model="forms.contract.value.intermediarySharePercent" type="number" min="0" max="100" step="0.01" required></label></template><label>金额<input v-model="forms.contract.value.amount" type="number" min="0" step=".01"></label><label>币种<input v-model="forms.contract.value.currency" maxlength="3"></label><label>签订日期<input v-model="forms.contract.value.signedOn" type="date"></label><label>开始日期<input v-model="forms.contract.value.startsOn" type="date"></label><label>结束日期<input v-model="forms.contract.value.endsOn" type="date"></label><label>负责人<select v-model="forms.contract.value.ownerEmployeeId"><option value="">未分配</option><option v-for="x in employees" :value="x.id">{{x.full_name}}</option></select></label><button class="primary-button">创建合同</button></form><div v-if="selectedContract" class="related-card"><h4>合同详情 · {{selectedContract.contract_no}}</h4><p>{{selectedContract.title}} · {{selectedContract.customer_name}}</p><p>类型：{{({maintenance:'维护保养合同',fire_inspection:'消防检测合同',fire_evaluation:'消防评定合同',fire_assessment:'消防评估合同',other:'其他技术服务合同'})[selectedContract.contract_type]||'其他技术服务合同'}}</p><p>金额：{{selectedContract.amount}} {{selectedContract.currency}} · 状态：{{selectedContract.status}}</p><p>签约方式：{{selectedContract.signing_mode==='intermediary'?'中间人介绍':'公司直接签约'}}<template v-if="selectedContract.signing_mode==='intermediary'"> · 中间人：{{selectedContract.intermediary_name}} · 分成：{{selectedContract.intermediary_share_percent}}%</template></p><p>签订：{{selectedContract.signed_on||'未签订'}} · 履行：{{selectedContract.starts_on||'—'}} 至 {{selectedContract.ends_on||'—'}}</p></div>
      </template>
      <template v-else>
        <h3>新增项目</h3><form class="create-grid" @submit.prevent="submit('project')"><label>项目编号<input v-model="forms.project.value.projectNo" required></label><label>项目名称<input v-model="forms.project.value.name" required></label><label>所属客户<select v-model="forms.project.value.customerId" required><option value="">请选择客户</option><option v-for="x in customers" :value="x.id">{{x.name}}</option></select></label><label>关联合同<select v-model="forms.project.value.contractId"><option value="">无合同项目</option><option v-for="x in contracts" :value="x.id">{{x.contract_no}} · {{x.title}}</option></select></label><label>项目负责人<select v-model="forms.project.value.managerEmployeeId"><option value="">未分配</option><option v-for="x in employees" :value="x.id">{{x.full_name}}</option></select></label><label>技术负责人<select v-model="forms.project.value.technicalLeadEmployeeId"><option value="">未分配</option><option v-for="x in employees" :value="x.id">{{x.full_name}}</option></select></label><label>项目开始<input v-model="forms.project.value.actualStart" type="date"></label><label>项目结束<input v-model="forms.project.value.actualEnd" type="date"></label><label class="inline-check"><input type="checkbox" v-model="forms.project.value.honeybeeUploaded">已上传小蜜蜂消防系统</label><label class="inline-check"><input type="checkbox" v-model="forms.project.value.documentsReady">项目资料已完成</label><label class="inline-check"><input type="checkbox" v-model="forms.project.value.maintenanceCardSent">维保公示卡已寄出</label><label>项目说明<input v-model="forms.project.value.description"></label><button class="primary-button">创建项目</button></form>
        <div v-if="selectedProject" class="related-card"><h4>{{selectedProject.name}} · 项目执行</h4><p>客户：{{selectedProject.customer_name||'—'}} · 合同：{{selectedProject.contract_no||'无合同'}}</p><p>项目负责人：{{selectedProject.manager_name||'未分配'}} · 技术负责人：{{selectedProject.technical_lead_name||'未分配'}}</p><p>项目时间：{{selectedProject.actual_start||'未开始'}} 至 {{selectedProject.actual_end||'未结束'}}</p><form class="create-grid compact project-edit" @submit.prevent="saveProject"><label>项目负责人<select v-model="projectEdit.managerEmployeeId"><option value="">未分配</option><option v-for="x in employees" :key="x.id" :value="x.id">{{x.full_name}}</option></select></label><label>技术负责人<select v-model="projectEdit.technicalLeadEmployeeId"><option value="">未分配</option><option v-for="x in employees" :key="x.id" :value="x.id">{{x.full_name}}</option></select></label><label>项目开始<input v-model="projectEdit.actualStart" type="date"></label><label>项目结束<input v-model="projectEdit.actualEnd" type="date"></label><label class="inline-check"><input type="checkbox" v-model="projectEdit.honeybeeUploaded">已上传小蜜蜂消防系统</label><label class="inline-check"><input type="checkbox" v-model="projectEdit.documentsReady">项目资料已完成</label><label class="inline-check"><input type="checkbox" v-model="projectEdit.maintenanceCardSent">维保公示卡已寄出</label><button class="primary-button" :disabled="busy">保存项目</button></form><div class="project-checks"><span :class="{'is-done':selectedProject.honeybee_uploaded}">小蜜蜂系统：{{selectedProject.honeybee_uploaded?'已上传':'未上传'}}</span><span :class="{'is-done':selectedProject.documents_ready}">项目资料：{{selectedProject.documents_ready?'已完成':'未完成'}}</span><span :class="{'is-done':selectedProject.maintenance_card_sent}">维保公示卡：{{selectedProject.maintenance_card_sent?'已寄出':'未寄出'}}</span></div><h4>合同记录</h4><div v-for="c in selectedProject.contracts||[]" :key="c.id" class="milestone-row"><span>{{c.contract_no}} · {{c.title}} · {{c.starts_on||'—'}} 至 {{c.ends_on||'—'}} · {{c.relation_type==='renewal'?'续签':c.relation_type==='supplemental'?'补充合同':'初始合同'}}</span><b v-if="c.is_current">当前合同</b></div><form class="create-grid compact" @submit.prevent="linkContract"><label>关联合同<select v-model="contractLinkForm.contractId" required><option value="">请选择合同</option><option v-for="c in contracts.filter(x=>x.customer_id===selectedProject.customer_id)" :key="c.id" :value="String(c.id)">{{c.contract_no}} · {{c.title}}</option></select></label><label>合同关系<select v-model="contractLinkForm.relationType"><option value="initial">初始合同</option><option value="renewal">续签合同</option><option value="supplemental">补充合同</option></select></label><label class="inline-check"><input type="checkbox" v-model="contractLinkForm.isCurrent">设为当前合同</label><button class="primary-button" :disabled="busy">关联合同</button></form></div>
      </template>
    </div>
  </div>
</section>
</template>
