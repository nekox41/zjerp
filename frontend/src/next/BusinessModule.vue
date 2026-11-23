<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { api, downloadFile } from './api'

const section = ref('customers')
const q = ref('')
const busy = ref(false)
const error = ref('')
const notice = ref('')
const customers = ref([])
const partners = ref([])
const employees = ref([])
const contracts = ref([])
const projects = ref([])
const identities = ref([])
const selectedCustomer = ref(null)
const selectedPartner = ref(null)
const selectedEmployee = ref(null)
const selectedContract = ref(null)
const selectedProject = ref(null)
const qualifications = ref([])
const contractAmendments = ref([])
const projectContracts = ref([])
const contacts = ref([])
const contractDocuments = ref([])
const activeEdit = ref(false)

const sections = [
  { id:'customers', label:'客户' }, { id:'partners', label:'合作方' },
  { id:'employees', label:'员工' }, { id:'contracts', label:'合同' }, { id:'projects', label:'项目' }
]
const contractTypes = [
  { value:'maintenance',label:'维护保养' },{ value:'inspection',label:'检测' },
  { value:'rating',label:'评定' },{ value:'assessment',label:'评估' },{ value:'other',label:'其他' }
]
const employeeTypes = [
  { value:'engineer',label:'工程师' },{ value:'operator',label:'操作员' },
  { value:'employee',label:'普通员工' },{ value:'manager',label:'管理人员' }
]
const qualifiedEngineers = computed(() => employees.value.filter(x => x.employee_type === 'engineer' && x.has_engineer_qualification))
const rows = computed(() => {
  const source = { customers:customers.value,partners:partners.value,employees:employees.value,contracts:contracts.value,projects:projects.value }[section.value] || []
  const term = q.value.trim().toLowerCase()
  if (!term) return source
  return source.filter(x => [x.name,x.full_name,x.title,x.customer_name,x.contract_no,x.project_no,x.partner_code,x.customer_code,x.employee_code,x.phone].some(v => String(v || '').toLowerCase().includes(term)))
})

const forms = {
  customer:ref({customerCode:'',name:'',creditCode:'',customerType:'enterprise',industry:'',phone:'',email:'',address:'',ownerEmployeeId:'',notes:''}),
  contact:ref({name:'',positionName:'',phone:'',email:'',isPrimary:false,notes:''}),
  partner:ref({partnerCode:'',name:'',contactName:'',phone:'',email:'',address:'',notes:''}),
  employee:ref({employeeCode:'',fullName:'',employeeType:'employee',identityId:'',positionName:'',phone:'',email:''}),
  qualification:ref({qualificationName:'',qualificationType:'engineer',certificateNo:'',issuingAuthority:'',issuedOn:'',expiresOn:'',notes:''}),
  contract:ref({contractNo:'',title:'',customerId:'',contractType:'maintenance',signingMode:'direct',intermediaryId:'',intermediarySharePercent:'',amount:'0',currency:'CNY',signedOn:'',startsOn:'',endsOn:'',ownerEmployeeId:'',status:'draft',notes:''}),
  project:ref({projectNo:'',name:'',customerId:'',contractId:'',managerEmployeeId:'',technicalLeadEmployeeId:'',plannedStart:'',plannedEnd:'',description:''})
}
const customerEdit=ref({}),partnerEdit=ref({}),employeeEdit=ref({})
const contractEdit = ref({})
const projectEdit = ref({})
const contractLink = ref({contractId:'',relationType:'renewal',isCurrent:true})

async function load() {
  error.value = ''
  try {
    const [c,p,e,ct,pr,ids] = await Promise.all([
      api('/business/customers').catch(()=>[]),
      api('/business/partners').catch(()=>[]),
      api('/business/employees').catch(()=>[]),
      api('/business/contracts').catch(()=>[]),
      api('/business/projects').catch(()=>[]),
      api('/admin/identities').catch(()=>[])
    ])
    customers.value=c; partners.value=p; employees.value=e; contracts.value=ct; projects.value=pr; identities.value=ids
  } catch (e) { error.value=e.message }
}
function resetForm(kind) {
  const initial = {
    customer:{customerCode:'',name:'',creditCode:'',customerType:'enterprise',industry:'',phone:'',email:'',address:'',ownerEmployeeId:'',notes:''},
    contact:{name:'',positionName:'',phone:'',email:'',isPrimary:false,notes:''},
    partner:{partnerCode:'',name:'',contactName:'',phone:'',email:'',address:'',notes:''},
    employee:{employeeCode:'',fullName:'',employeeType:'employee',identityId:'',positionName:'',phone:'',email:''},
    qualification:{qualificationName:'',qualificationType:'engineer',certificateNo:'',issuingAuthority:'',issuedOn:'',expiresOn:'',notes:''},
    contract:{contractNo:'',title:'',customerId:'',contractType:'maintenance',signingMode:'direct',intermediaryId:'',intermediarySharePercent:'',amount:'0',currency:'CNY',signedOn:'',startsOn:'',endsOn:'',ownerEmployeeId:'',status:'draft',notes:''},
    project:{projectNo:'',name:'',customerId:'',contractId:'',managerEmployeeId:'',technicalLeadEmployeeId:'',plannedStart:'',plannedEnd:'',description:''}
  }
  forms[kind].value = {...initial[kind]}
}
function numericFields(data, keys) {
  const result={...data}
  for (const key of keys) result[key] = result[key] === '' || result[key] == null ? null : Number(result[key])
  return result
}
async function create(kind) {
  busy.value=true; error.value=''; notice.value=''
  try {
    const value={...forms[kind].value}
    let endpoint=''
    if(kind==='customer') { endpoint='/business/customers'; Object.assign(value,numericFields(value,['ownerEmployeeId'])) }
    if(kind==='contact') { endpoint='/business/customers/'+selectedCustomer.value.id+'/contacts' }
    if(kind==='partner') endpoint='/business/partners'
    if(kind==='employee') { endpoint='/business/employees'; Object.assign(value,numericFields(value,['identityId'])) }
    if(kind==='qualification') endpoint='/business/employees/'+selectedEmployee.value.id+'/qualifications'
    if(kind==='contract') {
      endpoint='/business/contracts'
      Object.assign(value,numericFields(value,['customerId','intermediaryId','ownerEmployeeId']))
      if(value.signingMode==='direct'){value.intermediaryId=null;value.intermediarySharePercent=null}
    }
    if(kind==='project') {
      endpoint='/business/projects'
      Object.assign(value,numericFields(value,['customerId','contractId','managerEmployeeId','technicalLeadEmployeeId']))
    }
    await api(endpoint,{method:'POST',body:JSON.stringify(value)})
    resetForm(kind)
    notice.value='已保存'
    await load()
    if(kind==='contact') await openCustomer(selectedCustomer.value.id)
    if(kind==='qualification') await openEmployee(selectedEmployee.value.id)
  } catch(e) { error.value=e.message }
  finally { busy.value=false }
}

async function openCustomer(id) {
  try { selectedCustomer.value=await api('/business/customers/'+id); contacts.value=selectedCustomer.value.contacts||[]; customerEdit.value={name:selectedCustomer.value.name,creditCode:selectedCustomer.value.credit_code||'',industry:selectedCustomer.value.industry||'',phone:selectedCustomer.value.phone||'',email:selectedCustomer.value.email||'',address:selectedCustomer.value.address||'',notes:selectedCustomer.value.notes||''}; selectedPartner.value=null; selectedEmployee.value=null; selectedContract.value=null; selectedProject.value=null }
  catch(e){error.value=e.message}
}
async function openPartner(id) {
  try { selectedPartner.value=await api('/business/partners/'+id); partnerEdit.value={name:selectedPartner.value.name,contactName:selectedPartner.value.contact_name||'',phone:selectedPartner.value.phone||'',email:selectedPartner.value.email||'',address:selectedPartner.value.address||'',status:selectedPartner.value.status,notes:selectedPartner.value.notes||''}; selectedCustomer.value=null; selectedEmployee.value=null; selectedContract.value=null; selectedProject.value=null }
  catch(e){error.value=e.message}
}
async function openEmployee(id) {
  try { selectedEmployee.value=employees.value.find(x=>x.id===id)||null; employeeEdit.value={fullName:selectedEmployee.value.full_name,employeeType:selectedEmployee.value.employee_type,positionName:selectedEmployee.value.position_name||'',phone:selectedEmployee.value.phone||'',email:selectedEmployee.value.email||'',status:selectedEmployee.value.status}; qualifications.value=await api('/business/employees/'+id+'/qualifications'); selectedCustomer.value=null; selectedPartner.value=null; selectedContract.value=null; selectedProject.value=null }
  catch(e){error.value=e.message}
}
async function openContract(id) {
  try {
    selectedContract.value=await api('/business/contracts/'+id)
    contractAmendments.value=await api('/business/contracts/'+id+'/amendments').catch(()=>[])
    contractDocuments.value=selectedContract.value.documents||[]
    contractEdit.value={
      title:selectedContract.value.title,contractType:selectedContract.value.contract_type,
      signingMode:selectedContract.value.signing_mode,intermediaryId:selectedContract.value.intermediary_id||'',
      intermediarySharePercent:selectedContract.value.intermediary_share_percent??'',
      amount:selectedContract.value.amount,currency:selectedContract.value.currency,
      signedOn:selectedContract.value.signed_on||'',startsOn:selectedContract.value.starts_on||'',
      endsOn:selectedContract.value.ends_on||'',status:selectedContract.value.status,notes:selectedContract.value.notes||'',
      expectedRevisionNo:selectedContract.value.revision_no
    }
    activeEdit.value=false; selectedCustomer.value=null; selectedPartner.value=null; selectedEmployee.value=null; selectedProject.value=null
  } catch(e){error.value=e.message}
}
async function saveContract() {
  if(!selectedContract.value)return
  busy.value=true;error.value=''
  try {
    const value=numericFields({...contractEdit.value},['intermediaryId'])
    if(value.signingMode==='direct'){value.intermediaryId=null;value.intermediarySharePercent=null}
    await api('/business/contracts/'+selectedContract.value.id,{method:'PUT',body:JSON.stringify(value)})
    await openContract(selectedContract.value.id); await load(); activeEdit.value=false; notice.value='合同新版本已保存'
  } catch(e){error.value=e.message}
  finally{busy.value=false}
}
async function openProject(id) {
  try {
    selectedProject.value=await api('/business/projects/'+id)
    projectContracts.value=selectedProject.value.contracts||[]
    projectEdit.value={
      managerEmployeeId:selectedProject.value.manager_employee_id||'',
      technicalLeadEmployeeId:selectedProject.value.technical_lead_employee_id||'',
      plannedStart:selectedProject.value.planned_start||'',plannedEnd:selectedProject.value.planned_end||'',
      actualStart:selectedProject.value.actual_start||'',actualEnd:selectedProject.value.actual_end||'',
      status:selectedProject.value.status,description:selectedProject.value.description||'',
      honeybeeUploaded:!!selectedProject.value.honeybee_uploaded,honeybeeUploadedOn:selectedProject.value.honeybee_uploaded_on||'',
      documentsReady:!!selectedProject.value.documents_ready,documentsReadyOn:selectedProject.value.documents_ready_on||'',
      maintenanceCardSent:!!selectedProject.value.maintenance_card_sent,maintenanceCardSentOn:selectedProject.value.maintenance_card_sent_on||''
    }
    selectedCustomer.value=null;selectedPartner.value=null;selectedEmployee.value=null;selectedContract.value=null
  } catch(e){error.value=e.message}
}
async function saveProject() {
  if(!selectedProject.value)return
  busy.value=true;error.value=''
  try {
    const value=numericFields({...projectEdit.value},['managerEmployeeId','technicalLeadEmployeeId'])
    await api('/business/projects/'+selectedProject.value.id,{method:'PUT',body:JSON.stringify(value)})
    await openProject(selectedProject.value.id); await load(); notice.value='项目资料已更新'
  } catch(e){error.value=e.message}
  finally{busy.value=false}
}
async function linkContract() {
  if(!selectedProject.value||!contractLink.value.contractId)return
  busy.value=true;error.value=''
  try {
    await api('/business/projects/'+selectedProject.value.id+'/contracts',{method:'POST',body:JSON.stringify({
      contractId:Number(contractLink.value.contractId),relationType:contractLink.value.relationType,isCurrent:contractLink.value.isCurrent
    })})
    await openProject(selectedProject.value.id);await load();contractLink.value={contractId:'',relationType:'renewal',isCurrent:true};notice.value='合同已关联，原合同历史仍保留'
  } catch(e){error.value=e.message}
  finally{busy.value=false}
}
async function uploadContractDocument(event,documentType) {
  const file=event.target.files?.[0];if(!file||!selectedContract.value)return
  busy.value=true;error.value=''
  try {
    const form=new FormData();form.append('file',file)
    const uploaded=await api('/files',{method:'POST',body:form})
    await api('/business/contracts/'+selectedContract.value.id+'/documents',{method:'POST',body:JSON.stringify({fileId:uploaded.id,documentType,label:file.name})})
    await openContract(selectedContract.value.id);notice.value='合同资料已归档'
  } catch(e){error.value=e.message}
  finally{busy.value=false;event.target.value=''}
}
async function saveCustomer(){
  if(!selectedCustomer.value)return
  try{await api('/business/customers/'+selectedCustomer.value.id,{method:'PUT',body:JSON.stringify(customerEdit.value)});await openCustomer(selectedCustomer.value.id);await load();notice.value='客户资料已更新'}
  catch(e){error.value=e.message}
}
async function savePartner(){
  if(!selectedPartner.value)return
  try{await api('/business/partners/'+selectedPartner.value.id,{method:'PUT',body:JSON.stringify(partnerEdit.value)});await openPartner(selectedPartner.value.id);await load();notice.value='合作方资料已更新'}
  catch(e){error.value=e.message}
}
async function saveEmployee(){
  if(!selectedEmployee.value)return
  try{await api('/business/employees/'+selectedEmployee.value.id,{method:'PUT',body:JSON.stringify(employeeEdit.value)});await load();await openEmployee(selectedEmployee.value.id);notice.value='员工资料已更新'}
  catch(e){error.value=e.message}
}
async function updateContact(contact) {
  try {
    await api('/business/contacts/'+contact.id,{method:'PUT',body:JSON.stringify({name:contact.name,positionName:contact.position_name,phone:contact.phone,email:contact.email,notes:contact.notes})})
    await openCustomer(selectedCustomer.value.id);notice.value='联系人已更新'
  } catch(e){error.value=e.message}
}
async function deleteContact(contact) {
  try { await api('/business/contacts/'+contact.id,{method:'DELETE'});await openCustomer(selectedCustomer.value.id);notice.value='联系人已删除' }
  catch(e){error.value=e.message}
}
async function download(id,name){try{await downloadFile(id,name)}catch(e){error.value=e.message}}
watch(section,()=>{q.value='';selectedCustomer.value=null;selectedPartner.value=null;selectedEmployee.value=null;selectedContract.value=null;selectedProject.value=null;activeEdit.value=false})
onMounted(load)
</script>

<template>
<section class="business-module">
  <div class="business-header"><div><span class="eyebrow">COMPANY WORKFLOW</span><h2>业务档案</h2><p>从客户、合作方、员工、合同到项目，统一维护关联信息与历史。</p></div></div>
  <div class="business-tabs">
    <button v-for="item in sections" :key="item.id" :class="{active:section===item.id}" @click="section=item.id">{{item.label}}</button>
  </div>
  <p v-if="error" class="message error" role="alert">{{error}}</p><p v-if="notice" class="message success" role="status">{{notice}}</p>
  <div class="business-layout">
    <section class="panel business-list">
      <div class="business-list-head"><div><h3>{{sections.find(x=>x.id===section)?.label}}档案</h3><small>{{rows.length}} 条记录</small></div><input v-model="q" placeholder="搜索名称或编号"></div>
      <div class="business-records">
        <button v-for="row in rows" :key="row.id" class="business-record"
          :class="{chosen:selectedCustomer?.id===row.id||selectedPartner?.id===row.id||selectedEmployee?.id===row.id||selectedContract?.id===row.id||selectedProject?.id===row.id}"
          @click="section==='customers'?openCustomer(row.id):section==='partners'?openPartner(row.id):section==='employees'?openEmployee(row.id):section==='contracts'?openContract(row.id):openProject(row.id)">
          <b>{{row.name||row.full_name||row.title}}</b>
          <small>{{row.customer_code||row.partner_code||row.employee_code||row.contract_no||row.project_no||''}}</small>
          <span>{{row.customer_name||row.owner_name||row.position_name||row.status||''}}</span>
        </button>
        <p v-if="!rows.length" class="empty">没有符合条件的记录</p>
      </div>
    </section>

    <section class="panel business-forms">
      <template v-if="section==='customers'">
        <h3>新建客户</h3>
        <form class="create-grid" @submit.prevent="create('customer')">
          <label>客户编号<input v-model="forms.customer.value.customerCode" required></label>
          <label>客户名称<input v-model="forms.customer.value.name" required></label>
          <label>统一社会信用代码<input v-model="forms.customer.value.creditCode"></label>
          <label>客户类型<select v-model="forms.customer.value.customerType"><option value="enterprise">企业</option><option value="government">政府/事业单位</option><option value="individual">个人</option></select></label>
          <label>行业<input v-model="forms.customer.value.industry"></label>
          <label>业务负责人<select v-model="forms.customer.value.ownerEmployeeId"><option value="">未分配</option><option v-for="e in employees" :key="e.id" :value="String(e.id)">{{e.full_name}}</option></select></label>
          <label>联系电话<input v-model="forms.customer.value.phone"></label><label>邮箱<input v-model="forms.customer.value.email"></label>
          <label class="span-2">地址<input v-model="forms.customer.value.address"></label>
          <label class="span-2">备注<input v-model="forms.customer.value.notes"></label>
          <button class="primary-button" :disabled="busy">创建客户</button>
        </form>
        <div v-if="selectedCustomer" class="detail-block">
          <h3>{{selectedCustomer.name}} · 客户详情</h3>
          <p>客户编号：{{selectedCustomer.customer_code}}　信用代码：{{selectedCustomer.credit_code||'—'}}</p>
          <form class="create-grid compact" @submit.prevent="saveCustomer">
            <label>客户名称<input v-model="customerEdit.name"></label><label>信用代码<input v-model="customerEdit.creditCode"></label>
            <label>行业<input v-model="customerEdit.industry"></label><label>联系电话<input v-model="customerEdit.phone"></label>
            <label>邮箱<input v-model="customerEdit.email"></label><label>地址<input v-model="customerEdit.address"></label>
            <label class="span-2">备注<input v-model="customerEdit.notes"></label><button class="primary-button">保存客户资料</button>
          </form>
          <h4>联系人</h4>
          <div v-for="c in contacts" :key="c.id" class="contact-row">
            <div class="contact-edit"><input v-model="c.name" aria-label="联系人姓名"><input v-model="c.position_name" aria-label="联系人岗位"><input v-model="c.phone" aria-label="联系人电话"><input v-model="c.email" aria-label="联系人邮箱"></div>
            <span v-if="c.is_primary" class="tag">主要联系人</span>
            <button class="text-button" @click="updateContact(c)">保存</button><button class="text-button danger" @click="deleteContact(c)">删除</button>
          </div>
          <form class="create-grid compact" @submit.prevent="create('contact')">
            <label>联系人姓名<input v-model="forms.contact.value.name" required></label><label>岗位<input v-model="forms.contact.value.positionName"></label>
            <label>电话<input v-model="forms.contact.value.phone"></label><label>邮箱<input v-model="forms.contact.value.email"></label>
            <label class="inline-check"><input type="checkbox" v-model="forms.contact.value.isPrimary">主要联系人</label>
            <button class="primary-button" :disabled="busy">添加联系人</button>
          </form>
          <h4>关联合同</h4><p v-for="c in selectedCustomer.contracts||[]" :key="c.id" class="log-line">{{c.contract_no}} · {{c.title}} · {{c.status}}</p>
        </div>
      </template>

      <template v-else-if="section==='partners'">
        <h3>新建合作方</h3>
        <form class="create-grid" @submit.prevent="create('partner')">
          <label>合作方编号<input v-model="forms.partner.value.partnerCode" required></label><label>合作方名称<input v-model="forms.partner.value.name" required></label>
          <label>联系人<input v-model="forms.partner.value.contactName"></label><label>联系电话<input v-model="forms.partner.value.phone"></label>
          <label>邮箱<input v-model="forms.partner.value.email"></label><label>地址<input v-model="forms.partner.value.address"></label>
          <label class="span-2">备注<input v-model="forms.partner.value.notes"></label><button class="primary-button" :disabled="busy">创建合作方</button>
        </form>
        <div v-if="selectedPartner" class="detail-block">
          <h3>{{selectedPartner.name}} · 合作记录</h3>
          <form class="create-grid compact" @submit.prevent="savePartner">
            <label>合作方名称<input v-model="partnerEdit.name"></label><label>联系人<input v-model="partnerEdit.contactName"></label>
            <label>电话<input v-model="partnerEdit.phone"></label><label>邮箱<input v-model="partnerEdit.email"></label>
            <label>地址<input v-model="partnerEdit.address"></label><label>状态<select v-model="partnerEdit.status"><option value="active">启用</option><option value="disabled">停用</option></select></label>
            <label class="span-2">备注<input v-model="partnerEdit.notes"></label><button class="primary-button">保存合作方资料</button>
          </form>
          <h4>介绍的合同</h4>
          <div v-for="c in selectedPartner.contracts||[]" :key="c.id" class="log-line">{{c.contract_no}} · {{c.title}} · {{c.intermediary_share_percent}}%</div>
        </div>
      </template>

      <template v-else-if="section==='employees'">
        <h3>新建员工档案</h3>
        <form class="create-grid" @submit.prevent="create('employee')">
          <label>员工编号<input v-model="forms.employee.value.employeeCode"></label><label>姓名<input v-model="forms.employee.value.fullName" required></label>
          <label>员工类型<select v-model="forms.employee.value.employeeType"><option v-for="x in employeeTypes" :key="x.value" :value="x.value">{{x.label}}</option></select></label>
          <label>登录账号（可选）<select v-model="forms.employee.value.identityId"><option value="">暂不关联</option><option v-for="x in identities" :key="x.id" :value="String(x.id)">{{x.display_name}} · {{x.username}}</option></select></label>
          <label>岗位<input v-model="forms.employee.value.positionName"></label><label>电话<input v-model="forms.employee.value.phone"></label>
          <label>邮箱<input v-model="forms.employee.value.email"></label><button class="primary-button" :disabled="busy">创建员工</button>
        </form>
        <div v-if="selectedEmployee" class="detail-block">
          <h3>{{selectedEmployee.full_name}} · 资格档案</h3>
          <form class="create-grid compact" @submit.prevent="saveEmployee">
            <label>姓名<input v-model="employeeEdit.fullName"></label><label>员工类型<select v-model="employeeEdit.employeeType"><option v-for="x in employeeTypes" :key="x.value" :value="x.value">{{x.label}}</option></select></label>
            <label>岗位<input v-model="employeeEdit.positionName"></label><label>电话<input v-model="employeeEdit.phone"></label>
            <label>邮箱<input v-model="employeeEdit.email"></label><label>状态<select v-model="employeeEdit.status"><option value="active">在职</option><option value="inactive">离职/停用</option></select></label>
            <button class="primary-button">保存员工资料</button>
          </form>
          <p>工程师资格：{{selectedEmployee.has_engineer_qualification?'有效':'未登记/已过期'}}</p>
          <div v-for="q in qualifications" :key="q.id" class="log-line">{{q.qualification_name}} · {{q.qualification_type==='engineer'?'工程师资格':'其他资格'}} · {{q.certificate_no||'无证书编号'}} · {{q.effective_status||q.status}} · 到期 {{q.expires_on||'长期'}}</div>
          <form class="create-grid compact" @submit.prevent="create('qualification')">
            <label>资格名称<input v-model="forms.qualification.value.qualificationName" required></label>
            <label>资格类别<select v-model="forms.qualification.value.qualificationType"><option value="engineer">工程师资格</option><option value="other">其他资格</option></select></label>
            <label>证书编号<input v-model="forms.qualification.value.certificateNo"></label><label>发证机关<input v-model="forms.qualification.value.issuingAuthority"></label>
            <label>发证日期<input v-model="forms.qualification.value.issuedOn" type="date"></label><label>有效期至<input v-model="forms.qualification.value.expiresOn" type="date"></label>
            <label class="span-2">备注<input v-model="forms.qualification.value.notes"></label><button class="primary-button" :disabled="busy">登记资格</button>
          </form>
        </div>
      </template>

      <template v-else-if="section==='contracts'">
        <h3>新建合同</h3>
        <form class="create-grid" @submit.prevent="create('contract')">
          <label>合同编号<input v-model="forms.contract.value.contractNo" required></label><label>合同名称<input v-model="forms.contract.value.title" required></label>
          <label>客户主体<select v-model="forms.contract.value.customerId" required><option value="">请选择客户</option><option v-for="x in customers" :key="x.id" :value="String(x.id)">{{x.name}}</option></select></label>
          <label>合同类型<select v-model="forms.contract.value.contractType"><option v-for="x in contractTypes" :key="x.value" :value="x.value">{{x.label}}</option></select></label>
          <label>签约方式<select v-model="forms.contract.value.signingMode"><option value="direct">直接签约</option><option value="intermediary">中间人介绍</option></select></label>
          <label v-if="forms.contract.value.signingMode==='intermediary'">合作方<select v-model="forms.contract.value.intermediaryId" required><option value="">请选择合作方</option><option v-for="x in partners" :key="x.id" :value="String(x.id)">{{x.name}}</option></select></label>
          <label v-if="forms.contract.value.signingMode==='intermediary'">分成比例（%）<input v-model="forms.contract.value.intermediarySharePercent" type="number" min="0" max="100" step=".01" required></label>
          <label>合同金额<input v-model="forms.contract.value.amount" type="number" min="0" step=".01"></label>
          <label>币种<input v-model="forms.contract.value.currency" maxlength="3"></label>
          <label>签订日期<input v-model="forms.contract.value.signedOn" type="date"></label><label>合同开始<input v-model="forms.contract.value.startsOn" type="date"></label>
          <label>合同结束<input v-model="forms.contract.value.endsOn" type="date"></label>
          <label>合同负责人<select v-model="forms.contract.value.ownerEmployeeId"><option value="">未分配</option><option v-for="x in employees" :key="x.id" :value="String(x.id)">{{x.full_name}}</option></select></label>
          <label>合同状态<select v-model="forms.contract.value.status"><option value="draft">草稿</option><option value="pending">待生效</option><option value="active">履行中</option><option value="completed">已完成</option><option value="terminated">已终止</option></select></label>
          <label class="span-2">备注<input v-model="forms.contract.value.notes"></label><button class="primary-button" :disabled="busy">创建合同</button>
        </form>
        <div v-if="selectedContract" class="detail-block">
          <div class="detail-heading"><div><h3>{{selectedContract.contract_no}} · {{selectedContract.title}}</h3><p>{{selectedContract.customer_name}} · {{selectedContract.contract_type}} · {{selectedContract.status}}</p></div><button class="secondary-button" @click="activeEdit=!activeEdit">{{activeEdit?'取消编辑':'编辑合同'}}</button></div>
          <p>签约方式：{{selectedContract.signing_mode==='intermediary'?'中间人介绍':'直接签约'}} <template v-if="selectedContract.intermediary_name">· {{selectedContract.intermediary_name}} · 分成 {{selectedContract.intermediary_share_percent}}%</template></p>
          <p>期限：{{selectedContract.starts_on||'—'}} 至 {{selectedContract.ends_on||'—'}} · 金额：{{selectedContract.amount}} {{selectedContract.currency}}</p>
          <form v-if="activeEdit" class="create-grid compact" @submit.prevent="saveContract">
            <label>合同名称<input v-model="contractEdit.title" required></label><label>合同类型<select v-model="contractEdit.contractType"><option v-for="x in contractTypes" :key="x.value" :value="x.value">{{x.label}}</option></select></label>
            <label>签约方式<select v-model="contractEdit.signingMode"><option value="direct">直接签约</option><option value="intermediary">中间人介绍</option></select></label>
            <label v-if="contractEdit.signingMode==='intermediary'">合作方<select v-model="contractEdit.intermediaryId"><option value="">请选择</option><option v-for="x in partners" :key="x.id" :value="String(x.id)">{{x.name}}</option></select></label>
            <label v-if="contractEdit.signingMode==='intermediary'">分成比例<input v-model="contractEdit.intermediarySharePercent" type="number" min="0" max="100" step=".01"></label>
            <label>金额<input v-model="contractEdit.amount" type="number" min="0" step=".01"></label><label>币种<input v-model="contractEdit.currency"></label>
            <label>签订日期<input v-model="contractEdit.signedOn" type="date"></label><label>开始日期<input v-model="contractEdit.startsOn" type="date"></label><label>结束日期<input v-model="contractEdit.endsOn" type="date"></label>
            <label>合同状态<select v-model="contractEdit.status"><option value="draft">草稿</option><option value="pending">待生效</option><option value="active">履行中</option><option value="completed">已完成</option><option value="terminated">已终止</option></select></label>
            <label class="span-2">变更原因<input v-model="contractEdit.changeReason"></label><button class="primary-button" :disabled="busy">保存为新版本 v{{Number(contractEdit.expectedRevisionNo)+1}}</button>
          </form>
          <h4>关联项目</h4><p v-for="p in selectedContract.projects||[]" :key="p.project_id" class="log-line">{{p.project_no}} · {{p.project_name}} · {{p.relation_type}} {{p.is_current?'· 当前合同':''}}</p>
          <h4>合同资料（按合同分别留存）</h4>
          <div v-for="d in contractDocuments" :key="d.id" class="log-line">{{d.document_type}} · {{d.original_name}} <button class="text-button" @click="download(d.file_id,d.original_name)">下载</button></div>
          <div class="upload-row"><label>合同文件<input type="file" accept=".pdf,.docx,.jpg,.jpeg,.png" @change="uploadContractDocument($event,'contract')"></label><label>维保公示卡<input type="file" accept=".pdf,.docx,.jpg,.jpeg,.png" @change="uploadContractDocument($event,'maintenance_card')"></label></div>
          <h4>合同版本历史</h4><details v-for="v in selectedContract.revisions||[]" :key="v.revision_no"><summary>v{{v.revision_no}} · {{v.created_at}} · {{v.change_reason}}</summary><pre>{{v.snapshot_json}}</pre></details>
        </div>
      </template>

      <template v-else>
        <h3>新建项目</h3>
        <form class="create-grid" @submit.prevent="create('project')">
          <label>项目编号<input v-model="forms.project.value.projectNo" required></label><label>项目名称<input v-model="forms.project.value.name" required></label>
          <label>所属客户<select v-model="forms.project.value.customerId" required><option value="">请选择客户</option><option v-for="x in customers" :key="x.id" :value="String(x.id)">{{x.name}}</option></select></label>
          <label>初始合同（可选）<select v-model="forms.project.value.contractId"><option value="">尚无合同</option><option v-for="x in contracts.filter(c=>!forms.project.value.customerId||c.customer_id===Number(forms.project.value.customerId))" :key="x.id" :value="String(x.id)">{{x.contract_no}} · {{x.title}}</option></select></label>
          <label>项目负责人（有效工程师）<select v-model="forms.project.value.managerEmployeeId"><option value="">未分配</option><option v-for="x in qualifiedEngineers" :key="x.id" :value="String(x.id)">{{x.full_name}}</option></select></label>
          <label>技术负责人（须与项目负责人不同）<select v-model="forms.project.value.technicalLeadEmployeeId"><option value="">未分配</option><option v-for="x in qualifiedEngineers.filter(x=>String(x.id)!==forms.project.value.managerEmployeeId)" :key="x.id" :value="String(x.id)">{{x.full_name}}</option></select></label>
          <label>计划开始<input v-model="forms.project.value.plannedStart" type="date"></label><label>计划结束<input v-model="forms.project.value.plannedEnd" type="date"></label>
          <label class="span-2">项目说明<input v-model="forms.project.value.description"></label><button class="primary-button" :disabled="busy">创建项目</button>
        </form>
        <div v-if="selectedProject" class="detail-block">
          <h3>{{selectedProject.project_no}} · {{selectedProject.name}}</h3><p>客户：{{selectedProject.customer_name}} · 当前合同：{{selectedProject.contract_no||'尚未关联'}}</p>
          <form class="create-grid compact" @submit.prevent="saveProject">
            <label>项目负责人<select v-model="projectEdit.managerEmployeeId"><option value="">未分配</option><option v-for="x in qualifiedEngineers.filter(x=>String(x.id)!==String(projectEdit.technicalLeadEmployeeId))" :key="x.id" :value="String(x.id)">{{x.full_name}}</option></select></label>
            <label>技术负责人<select v-model="projectEdit.technicalLeadEmployeeId"><option value="">未分配</option><option v-for="x in qualifiedEngineers.filter(x=>String(x.id)!==String(projectEdit.managerEmployeeId))" :key="x.id" :value="String(x.id)">{{x.full_name}}</option></select></label>
            <label>计划开始<input v-model="projectEdit.plannedStart" type="date"></label><label>计划结束<input v-model="projectEdit.plannedEnd" type="date"></label>
            <label>实际开始<input v-model="projectEdit.actualStart" type="date"></label><label>实际结束<input v-model="projectEdit.actualEnd" type="date"></label>
            <label>项目状态<select v-model="projectEdit.status"><option value="planned">计划中</option><option value="in_progress">进行中</option><option value="paused">暂停</option><option value="completed">已完成</option><option value="cancelled">已取消</option></select></label>
            <label class="span-2">项目说明<input v-model="projectEdit.description"></label>
            <button class="primary-button" :disabled="busy">保存项目资料</button>
          </form>
          <h4>合同历史（续签不覆盖旧合同）</h4>
          <div v-for="c in projectContracts" :key="c.id" class="log-line">{{c.contract_no}} · {{c.title}} · {{c.starts_on||'—'}} 至 {{c.ends_on||'—'}} · {{c.relation_type==='renewal'?'续签':c.relation_type==='supplemental'?'补充合同':'初始合同'}} <b v-if="c.is_current">当前</b></div>
          <form class="create-grid compact" @submit.prevent="linkContract">
            <label>添加合同<select v-model="contractLink.contractId" required><option value="">选择合同</option><option v-for="c in contracts.filter(x=>x.customer_id===selectedProject.customer_id)" :key="c.id" :value="String(c.id)">{{c.contract_no}} · {{c.title}}</option></select></label>
            <label>合同关系<select v-model="contractLink.relationType"><option value="initial">初始合同</option><option value="renewal">续签合同</option><option value="supplemental">补充合同</option></select></label>
            <label class="inline-check"><input type="checkbox" v-model="contractLink.isCurrent">设为当前合同</label><button class="primary-button">关联合同</button>
          </form>
          <div class="project-status-edit">
            <label class="inline-check"><input type="checkbox" v-model="projectEdit.honeybeeUploaded"> 小蜜蜂信息已上传</label>
            <label>上传日期<input type="date" v-model="projectEdit.honeybeeUploadedOn"></label>
            <label class="inline-check"><input type="checkbox" v-model="projectEdit.documentsReady"> 项目资料已完成</label>
            <label>资料完成日期<input type="date" v-model="projectEdit.documentsReadyOn"></label>
            <label class="inline-check"><input type="checkbox" v-model="projectEdit.maintenanceCardSent"> 维保公示卡已寄出</label>
            <label>寄出日期<input type="date" v-model="projectEdit.maintenanceCardSentOn"></label>
            <button class="primary-button" @click="saveProject">保存维保状态</button>
          </div>
          <p class="muted">维保状态与现场服务记录在对应模块管理；此处仅维护项目基础信息。</p>
        </div>
      </template>
    </section>
  </div>
</section>
</template>

<style scoped>
.business-module{display:grid;gap:18px}.business-header h2{margin:5px 0;font-size:23px;color:#203149}.business-header p{margin:0;color:#7d889a;font-size:12px}.business-tabs{display:flex;gap:8px;flex-wrap:wrap}.business-tabs button{border:1px solid #dce3ed;background:#fff;color:#516078;border-radius:8px;padding:9px 15px;font-size:12px}.business-tabs button.active{background:#eaf0ff;border-color:#b8c9f4;color:#2856b7;font-weight:700}.business-layout{display:grid;grid-template-columns:minmax(240px,.78fr) minmax(420px,1.5fr);gap:16px;align-items:start}.business-list,.business-forms{padding:20px;min-width:0}.business-list-head{display:flex;gap:10px;align-items:center;justify-content:space-between}.business-list-head h3,.business-forms>h3{margin:0;font-size:15px}.business-list-head small{color:#9aa4b3;font-size:11px}.business-list-head input{border:1px solid #dce2eb;border-radius:8px;padding:9px;max-width:150px;min-width:90px}.business-records{display:grid;gap:7px;margin-top:16px;max-height:680px;overflow:auto}.business-record{display:grid;text-align:left;gap:4px;border:1px solid #edf0f5;border-radius:9px;background:#fff;padding:12px;color:#27354b}.business-record:hover,.business-record.chosen{border-color:#a9bce9;background:#f8faff}.business-record small,.business-record span{font-size:11px;color:#8792a4}.create-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:13px;margin-top:16px}.create-grid.compact{margin-top:12px}.create-grid label{display:grid;gap:7px;font-size:11px;color:#65738a}.create-grid input,.create-grid select{width:100%;min-width:0;border:1px solid #dce2eb;border-radius:8px;padding:10px 11px;background:#fff;font:inherit;font-size:12px}.create-grid .primary-button{align-self:end}.span-2{grid-column:span 2}.detail-block{border-top:1px solid #edf0f5;margin-top:24px;padding-top:18px}.detail-block h3{font-size:15px;margin:0 0 10px}.detail-block h4{font-size:12px;margin:20px 0 8px;color:#3f516b}.detail-block p{font-size:12px;color:#68768b;line-height:1.8}.contact-row,.detail-heading{display:flex;gap:10px;align-items:center;justify-content:space-between;padding:9px 0;border-bottom:1px solid #edf0f5}.contact-edit{display:grid;grid-template-columns:repeat(4,minmax(70px,1fr));gap:5px;flex:1}.contact-edit input{min-width:0;border:1px solid #e1e7ef;border-radius:6px;padding:6px;font-size:10px}.contact-row>div{display:grid;gap:4px}.contact-row small{font-size:10px;color:#8792a4}.tag{font-size:10px;color:#2e7c5b;background:#eaf7f0;padding:5px 8px;border-radius:20px}.text-button{border:0;background:transparent;color:#315fca;font-size:11px;padding:5px}.text-button.danger{color:#b84e4e}.detail-heading h3{margin:0}.detail-heading p{margin:5px 0 0}.upload-row{display:flex;gap:15px;flex-wrap:wrap;margin-top:12px}.upload-row label{display:grid;gap:7px;font-size:11px;color:#65738a}.upload-row input{max-width:220px;font-size:11px}.log-line{font-size:11px;color:#68768b;padding:8px 0;border-bottom:1px solid #edf0f5;line-height:1.7}.inline-check{display:flex!important;align-items:center;gap:8px}.inline-check input{width:auto}.project-status-edit{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px;margin:14px 0;padding:12px;background:#f8fafc;border-radius:9px}.project-status-edit label{display:flex;gap:8px;align-items:center;font-size:11px;color:#62718a}.project-status-edit input[type=date]{padding:7px;border:1px solid #dce2eb;border-radius:6px}.project-status-edit .primary-button{grid-column:span 2}.project-checks{display:flex;gap:8px;flex-wrap:wrap;margin:15px 0}.project-checks span{padding:7px 10px;border-radius:7px;background:#f2f4f7;color:#788396;font-size:11px}.project-checks span.is-done{background:#eaf7f0;color:#25815b}.muted{font-size:11px;color:#8995a7}.detail-block pre{white-space:pre-wrap;overflow-wrap:anywhere;background:#f5f7fa;padding:10px;border-radius:8px;font-size:10px}
@media(max-width:950px){.business-layout{grid-template-columns:1fr}.business-list{max-height:none}.create-grid{grid-template-columns:1fr}.span-2{grid-column:auto}}
</style>
