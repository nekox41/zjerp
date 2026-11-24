<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { api, downloadFile } from './api'

const props=defineProps({permissions:{type:Array,default:()=>[]}})
const can=p=>props.permissions.includes(p)
const section = ref('customers')
const busy = ref(false)
const error = ref('')
const notice = ref('')
const q = ref('')
const employees = ref([])
const identities = ref([])
const projectLogs = ref([])
const qualifications = ref([])
const selectedEmployee = ref(null)
const customers = ref([])
const contracts = ref([])
const projects = ref([])
const selectedCustomer = ref(null)
const contacts = ref([])
const editingContactId=ref(null)
const contactEdit=ref({name:'',positionName:'',phone:'',email:'',notes:'',isPrimary:false})
function editContact(contact){
  editingContactId.value=contact.id
  contactEdit.value={name:contact.name||'',positionName:contact.position_name||'',phone:contact.phone||'',email:contact.email||'',notes:contact.notes||'',isPrimary:!!contact.is_primary}
}
async function saveContact(){
  if(!editingContactId.value||!selectedCustomer.value)return
  busy.value=true;error.value=''
  try{
    await api('/business/contacts/'+editingContactId.value,{method:'PUT',body:JSON.stringify(contactEdit.value)})
    editingContactId.value=null
    await openCustomer(selectedCustomer.value.id)
    notice.value='联系人已更新'
  }catch(e){error.value=e.message}finally{busy.value=false}
}
async function deleteContact(contact){
  if(!selectedCustomer.value||!confirm('确定删除联系人「'+contact.name+'」？'))return
  busy.value=true;error.value=''
  try{
    await api('/business/contacts/'+contact.id,{method:'DELETE'})
    editingContactId.value=null
    await openCustomer(selectedCustomer.value.id)
    notice.value='联系人已删除'
  }catch(e){error.value=e.message}finally{busy.value=false}
}
const forms = {
  employee: ref({employeeCode:'',fullName:'',identityId:'',positionName:'',phone:'',email:'',employeeType:'general'}),
  customer: ref({customerCode:'',name:'',creditCode:'',customerType:'enterprise',industry:'',phone:'',email:'',address:'',ownerEmployeeId:'',notes:''}),
  contact: ref({name:'',positionName:'',phone:'',email:'',isPrimary:false}),
  contract: ref({contractNo:'',title:'',customerId:'',contractType:'',serviceType:'other',amount:'0',currency:'CNY',signedOn:'',startsOn:'',endsOn:'',ownerEmployeeId:'',notes:''}),
  project: ref({projectNo:'',name:'',customerId:'',contractId:'',managerEmployeeId:'',plannedStart:'',plannedEnd:'',description:''}),
  qualification: ref({qualificationName:'',certificateNo:'',issuingAuthority:'',issuedOn:'',expiresOn:'',notes:''}),
  amendment: ref({amendmentNo:'',title:'',changeSummary:'',revisedAmount:'',effectiveOn:''})
}
const rows = ref([])
const allConfig = {
  employees:{title:'员工档案',endpoint:'/employees',form:'employee',permission:'employee:write'},
  customers:{title:'客户管理',endpoint:'/customers',form:'customer',permission:'customer:write'},
  contracts:{title:'合同管理',endpoint:'/contracts',form:'contract',permission:'contract:write'},
  projects:{title:'项目管理',endpoint:'/projects',form:'project',permission:'project:write'}
}
const config=computed(()=>Object.fromEntries(Object.entries(allConfig).filter(([k])=>can(allConfig[k].permission.replace(':write',':read')))))
function ensureSection(){
  if(!config.value[section.value])section.value=Object.keys(config.value)[0]||'customers'
}
async function load() {
  error.value=''; notice.value=''
  try {
    ensureSection()
    const [e,c,ct,p,ids] = await Promise.all([
      can('employee:read')?api('/business/employees'):Promise.resolve([]),
      can('customer:read')?api('/business/customers'):Promise.resolve([]),
      can('contract:read')?api('/business/contracts'):Promise.resolve([]),
      can('project:read')?api('/business/projects'):Promise.resolve([]),
      can('identity:read')?api('/admin/identities'):Promise.resolve([])
    ])
    employees.value=e; customers.value=c; contracts.value=ct; projects.value=p; identities.value=ids
    const cfg=config.value[section.value]
    rows.value=cfg ? await api('/business'+cfg.endpoint+'?q='+encodeURIComponent(q.value)) : []
  } catch(e) { error.value=e.message }
}
const initialForms=Object.fromEntries(Object.entries(forms).map(([k,v])=>[k,{...v.value}]))
async function submit(kind) {
  busy.value=true; error.value=''; notice.value=''
  try {
    const data={...forms[kind].value}
    Object.keys(data).forEach(k=>{if(data[k]==='') data[k]=null})
    let endpoint=null
    if(kind==='employee') endpoint='/employees'
    else if(kind==='customer') endpoint='/customers'
    else if(kind==='contract') endpoint='/contracts'
    else if(kind==='project') endpoint='/projects'
    else if(kind==='contact'&&selectedCustomer.value) endpoint='/customers/'+selectedCustomer.value.id+'/contacts'
    else if(kind==='qualification'&&selectedEmployee.value) endpoint='/employees/'+selectedEmployee.value.id+'/qualifications'
    else if(kind==='amendment'&&selectedContract.value) endpoint='/contracts/'+selectedContract.value.id+'/amendments'
    if(!endpoint) throw new Error('请先选择对应的客户、员工或合同记录')
    const required={employee:'employee:write',customer:'customer:write',contact:'customer:write',contract:'contract:write',amendment:'contract:write',project:'project:write',qualification:'employee:write'}[kind]
    if(!required||!can(required))throw new Error('当前账号没有此项修改权限')
    await api('/business'+endpoint,{method:'POST',body:JSON.stringify(data)})
    notice.value='保存成功'; forms[kind].value={...initialForms[kind]}
    await load()
    if(kind==='contact') await openCustomer(selectedCustomer.value.id)
    if(kind==='qualification') await openEmployee(selectedEmployee.value.id)
    if(kind==='amendment') await openContract(selectedContract.value.id)
    if(kind==='project'&&selectedProject.value) await openProject(selectedProject.value.id)
  } catch(e) { error.value=e.message } finally { busy.value=false }
}
async function openCustomer(id) {
  try {
    selectedCustomer.value=await api('/business/customers/'+id)
    contacts.value=selectedCustomer.value.contacts||[]
    customerEditForm.value={
      name:selectedCustomer.value.name||'',creditCode:selectedCustomer.value.credit_code||'',
      customerType:selectedCustomer.value.customer_type||'enterprise',industry:selectedCustomer.value.industry||'',
      phone:selectedCustomer.value.phone||'',email:selectedCustomer.value.email||'',address:selectedCustomer.value.address||'',
      ownerEmployeeId:selectedCustomer.value.owner_employee_id||'',status:selectedCustomer.value.status||'active',
      notes:selectedCustomer.value.notes||''
    }
    customerEditOpen.value=false
  } catch(e){error.value=e.message}
}
async function saveCustomer(){
  if(!selectedCustomer.value)return
  busy.value=true
  try{
    await api('/business/customers/'+selectedCustomer.value.id,{method:'PUT',body:JSON.stringify(customerEditForm.value)})
    await load();await openCustomer(selectedCustomer.value.id)
    customerEditOpen.value=false;notice.value='甲方客户资料已更新'
  }catch(e){error.value=e.message}finally{busy.value=false}
}
const selectedProject=ref(null)
const selectedContract=ref(null)
const contractAmendments=ref([])
const customerEditOpen=ref(false),employeeEditOpen=ref(false),contractEditOpen=ref(false)
const customerEditForm=ref({name:'',creditCode:'',customerType:'enterprise',industry:'',phone:'',email:'',address:'',ownerEmployeeId:'',status:'active',notes:''})
const employeeEditForm=ref({fullName:'',employeeType:'general',identityId:'',positionName:'',phone:'',email:'',status:'active'})
const contractEditForm=ref({title:'',contractType:'',serviceType:'other',amount:'0',currency:'CNY',signedOn:'',startsOn:'',endsOn:'',ownerEmployeeId:'',status:'draft',notes:''})
const projectEditOpen=ref(false)
const projectEditForm=ref({name:'',description:'',managerEmployeeId:'',plannedStart:'',plannedEnd:'',status:'planned'})
async function openEmployee(id) {
  try {
    selectedEmployee.value=employees.value.find(x=>x.id===id)
    if(!selectedEmployee.value) throw new Error('员工记录不存在，请刷新列表')
    employeeEditForm.value={
      fullName:selectedEmployee.value.full_name||'',employeeType:selectedEmployee.value.employee_type||'general',
      identityId:selectedEmployee.value.identity_id||'',positionName:selectedEmployee.value.position_name||'',
      phone:selectedEmployee.value.phone||'',email:selectedEmployee.value.email||'',status:selectedEmployee.value.status||'active'
    }
    employeeEditOpen.value=false
    qualifications.value=await api('/business/employees/'+id+'/qualifications')
  } catch(e){error.value=e.message}
}
async function saveEmployee(){
  if(!selectedEmployee.value)return
  busy.value=true
  try{
    await api('/business/employees/'+selectedEmployee.value.id,{method:'PUT',body:JSON.stringify(employeeEditForm.value)})
    await load();await openEmployee(selectedEmployee.value.id)
    employeeEditOpen.value=false;notice.value='员工档案已更新'
  }catch(e){error.value=e.message}finally{busy.value=false}
}
async function openContract(id) {
  try {
    selectedContract.value=await api('/business/contracts/'+id)
    contractAmendments.value=await api('/business/contracts/'+id+'/amendments')
    contractEditForm.value={
      title:selectedContract.value.title||'',contractType:selectedContract.value.contract_type||'',
      serviceType:selectedContract.value.service_type||'other',amount:String(selectedContract.value.amount??'0'),
      currency:selectedContract.value.currency||'CNY',signedOn:selectedContract.value.signed_on||'',
      startsOn:selectedContract.value.starts_on||'',endsOn:selectedContract.value.ends_on||'',
      ownerEmployeeId:selectedContract.value.owner_employee_id||'',status:selectedContract.value.status||'draft',
      notes:selectedContract.value.notes||''
    }
    contractEditOpen.value=false
  } catch(e){error.value=e.message}
}
async function saveContract(){
  if(!selectedContract.value)return
  busy.value=true
  try{
    const body={...contractEditForm.value}
    for(const key of ['signedOn','startsOn','endsOn','ownerEmployeeId'])if(body[key]==='')body[key]=null
    await api('/business/contracts/'+selectedContract.value.id,{method:'PUT',body:JSON.stringify(body)})
    await load();await openContract(selectedContract.value.id)
    contractEditOpen.value=false;notice.value='合同资料已更新'
  }catch(e){error.value=e.message}finally{busy.value=false}
}
async function openProject(id) {
  try {
    selectedProject.value=await api('/business/projects/'+id)
    projectLogs.value=selectedProject.value.logs||[]
    projectEditForm.value={
      name:selectedProject.value.name||'',description:selectedProject.value.description||'',
      managerEmployeeId:selectedProject.value.manager_employee_id||'',
      plannedStart:selectedProject.value.planned_start||'',plannedEnd:selectedProject.value.planned_end||'',
      status:selectedProject.value.status||'planned'
    }
    projectEditOpen.value=false
  } catch(e){error.value=e.message}
}
async function saveProject(){
  if(!selectedProject.value)return
  busy.value=true
  try{
    const body={...projectEditForm.value}
    if(!body.managerEmployeeId)body.managerEmployeeId=null
    if(!body.plannedStart)body.plannedStart=null
    if(!body.plannedEnd)body.plannedEnd=null
    await api('/business/projects/'+selectedProject.value.id,{method:'PUT',body:JSON.stringify(body)})
    await load();await openProject(selectedProject.value.id)
    projectEditOpen.value=false;notice.value='项目档案已更新'
  }catch(e){error.value=e.message}finally{busy.value=false}
}
watch(section,()=>{q.value='';load()})
watch(()=>props.permissions,()=>{ensureSection();load()})
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
      <div class="business-list-head"><div><h3>{{config[section]?.title||'业务管理'}}</h3><p>统一维护业务主数据与关联记录</p></div><input v-model="q" placeholder="搜索名称或编号" @keyup.enter="load"><button class="secondary-button" @click="load">搜索</button></div>
      <div class="business-records">
        <button v-for="r in rows" :key="r.id" class="business-record" @click="section==='customers'?openCustomer(r.id):section==='projects'?openProject(r.id):section==='contracts'?openContract(r.id):section==='employees'?openEmployee(r.id):null">
          <b>{{r.name||r.full_name||r.title}}</b><small>{{r.customer_code||r.employee_code||r.contract_no||r.project_no||r.status||r.position_name}}</small>
          <span>{{r.customer_name||r.organization_name||r.owner_name||''}}</span>
          <small v-if="section==='contracts'&&r.days_until_expiry!=null&&r.days_until_expiry<=30" :style="{color:r.days_until_expiry<0?'#b42318':'#916118'}">{{r.days_until_expiry<0?'已到期 '+Math.abs(r.days_until_expiry)+' 天':r.days_until_expiry===0?'今日到期':'距到期 '+r.days_until_expiry+' 天'}}</small>
        </button>
        <p v-if="!rows.length" class="empty">暂无记录，使用下方表单创建第一条数据。</p>
      </div>
    </div>
    <div class="business-forms panel">
      <template v-if="section==='employees'">
        <h3 v-if="can('employee:write')">新增员工档案</h3><form v-if="can('employee:write')" class="create-grid" @submit.prevent="submit('employee')"><label>员工编号<input v-model="forms.employee.value.employeeCode"></label><label>姓名<input v-model="forms.employee.value.fullName" required></label><label>岗位<input v-model="forms.employee.value.positionName"></label><label>员工类型<select v-model="forms.employee.value.employeeType"><option value="general">普通员工</option><option value="operator">操作员</option><option value="engineer">工程师</option><option value="manager">管理人员</option></select></label><label>电话<input v-model="forms.employee.value.phone"></label><label>邮箱<input v-model="forms.employee.value.email" type="email"></label><label>关联登录账号<select v-model="forms.employee.value.identityId"><option value="">暂不关联</option><option v-for="x in identities" :value="x.id">{{x.username}} · {{x.display_name}}</option></select></label><button class="primary-button">创建员工</button></form>
      </template>
        <div v-if="selectedEmployee" class="related-card">
          <div class="section-heading"><h4>{{selectedEmployee.full_name}} · 员工档案</h4><button v-if="can('employee:write')" class="secondary-button" @click="employeeEditOpen=!employeeEditOpen">{{employeeEditOpen?'收起编辑':'编辑档案'}}</button></div>
          <form v-if="employeeEditOpen" class="create-grid compact" @submit.prevent="saveEmployee">
            <label>姓名<input v-model="employeeEditForm.fullName" required></label>
            <label>员工类型<select v-model="employeeEditForm.employeeType"><option value="general">普通员工</option><option value="operator">操作员</option><option value="engineer">工程师</option><option value="manager">管理人员</option></select></label>
            <label>岗位<input v-model="employeeEditForm.positionName"></label><label>电话<input v-model="employeeEditForm.phone"></label>
            <label>邮箱<input v-model="employeeEditForm.email" type="email"></label>
            <label>关联登录账号<select v-model="employeeEditForm.identityId"><option value="">不关联账号</option><option v-for="x in identities" :value="x.id">{{x.username}} · {{x.display_name}}</option></select></label>
            <label>员工状态<select v-model="employeeEditForm.status"><option value="active">在职</option><option value="inactive">离职/停用</option></select></label>
            <button class="primary-button" :disabled="busy">保存员工档案</button>
          </form>
          <h4>资质证书</h4><p v-for="x in qualifications" :key="x.id" class="log-line">{{x.qualification_name}} · {{x.certificate_no||'无编号'}} · {{x.expires_on||'长期有效'}} · {{x.effective_status}}</p><form v-if="can('employee:write')" class="create-grid compact" @submit.prevent="submit('qualification')"><label>资质名称<input v-model="forms.qualification.value.qualificationName" required></label><label>证书编号<input v-model="forms.qualification.value.certificateNo"></label><label>发证机构<input v-model="forms.qualification.value.issuingAuthority"></label><label>发证日期<input v-model="forms.qualification.value.issuedOn" type="date"></label><label>有效期至<input v-model="forms.qualification.value.expiresOn" type="date"></label><button class="primary-button">添加资质</button></form></div>
      <template v-else-if="section==='customers'">
        <h3 v-if="can('customer:write')">新增客户</h3><form v-if="can('customer:write')" class="create-grid" @submit.prevent="submit('customer')"><label>客户编号<input v-model="forms.customer.value.customerCode" required></label><label>客户名称<input v-model="forms.customer.value.name" required></label><label>统一社会信用代码<input v-model="forms.customer.value.creditCode"></label><label>客户类型<select v-model="forms.customer.value.customerType"><option value="enterprise">企业</option><option value="government">政府/事业单位</option><option value="individual">个人</option></select></label><label>行业<input v-model="forms.customer.value.industry"></label><label>负责人<select v-model="forms.customer.value.ownerEmployeeId"><option value="">未分配</option><option v-for="x in employees" :value="x.id">{{x.full_name}}</option></select></label><label>联系电话<input v-model="forms.customer.value.phone"></label><label>电子邮箱<input v-model="forms.customer.value.email"></label><label>地址<input v-model="forms.customer.value.address"></label><label>备注<input v-model="forms.customer.value.notes"></label><button class="primary-button">创建客户</button></form>
        <div v-if="selectedCustomer" class="related-card">
          <div class="section-heading"><h4>{{selectedCustomer.name}} · 甲方客户档案</h4><button v-if="can('customer:write')" class="secondary-button" @click="customerEditOpen=!customerEditOpen">{{customerEditOpen?'收起编辑':'编辑客户'}}</button></div>
          <form v-if="customerEditOpen" class="create-grid compact" @submit.prevent="saveCustomer">
            <label>客户名称<input v-model="customerEditForm.name" required></label><label>统一社会信用代码<input v-model="customerEditForm.creditCode"></label>
            <label>客户类型<select v-model="customerEditForm.customerType"><option value="enterprise">企业</option><option value="government">政府/事业单位</option><option value="individual">个人</option></select></label>
            <label>行业<input v-model="customerEditForm.industry"></label><label>负责人<select v-model="customerEditForm.ownerEmployeeId"><option value="">未分配</option><option v-for="x in employees" :value="x.id">{{x.full_name}}</option></select></label>
            <label>联系电话<input v-model="customerEditForm.phone"></label><label>电子邮箱<input v-model="customerEditForm.email"></label><label>地址<input v-model="customerEditForm.address"></label>
            <label>状态<select v-model="customerEditForm.status"><option value="active">正常</option><option value="disabled">停用</option></select></label>
            <label>备注<input v-model="customerEditForm.notes"></label><button class="primary-button" :disabled="busy">保存客户资料</button>
          </form>
          <h4>联系人</h4>
          <div v-for="x in contacts" :key="x.id" class="log-line">
            {{x.name}} · {{x.position_name||'未填岗位'}} · {{x.phone||'无电话'}} · {{x.email||''}}
            <b v-if="x.is_primary">主要联系人</b>
            <button v-if="can('customer:write')" type="button" class="secondary-button" @click="editContact(x)">编辑</button>
            <button v-if="can('customer:write')" type="button" class="secondary-button" @click="deleteContact(x)">删除</button>
          </div>
          <form v-if="editingContactId" class="create-grid compact" @submit.prevent="saveContact">
            <h4>编辑联系人</h4>
            <label>姓名<input v-model="contactEdit.name" required></label>
            <label>岗位<input v-model="contactEdit.positionName"></label>
            <label>电话<input v-model="contactEdit.phone"></label>
            <label>邮箱<input v-model="contactEdit.email"></label>
            <label>备注<input v-model="contactEdit.notes"></label>
            <label class="inline-check"><input type="checkbox" v-model="contactEdit.isPrimary">主要联系人</label>
            <button class="primary-button" :disabled="busy">保存联系人</button>
            <button type="button" class="secondary-button" @click="editingContactId=null">取消</button>
          </form>
          <form v-if="can('customer:write')" class="create-grid compact" @submit.prevent="submit('contact')"><label>联系人姓名<input v-model="forms.contact.value.name" required></label><label>岗位<input v-model="forms.contact.value.positionName"></label><label>电话<input v-model="forms.contact.value.phone"></label><label>邮箱<input v-model="forms.contact.value.email"></label><label class="inline-check"><input type="checkbox" v-model="forms.contact.value.isPrimary">主要联系人</label><button class="primary-button">添加联系人</button></form></div>
      </template>
      <template v-else-if="section==='contracts'">
        <h3 v-if="can('contract:write')">新增合同</h3><form v-if="can('contract:write')" class="create-grid" @submit.prevent="submit('contract')"><label>合同编号<input v-model="forms.contract.value.contractNo" required></label><label>合同名称<input v-model="forms.contract.value.title" required></label><label>客户<select v-model="forms.contract.value.customerId" required><option value="">请选择客户</option><option v-for="x in customers" :value="x.id">{{x.name}}</option></select></label><label>合同类型<input v-model="forms.contract.value.contractType"></label><label>服务类型<select v-model="forms.contract.value.serviceType"><option value="maintenance">维护保养</option><option value="fire_inspection">消防检测</option><option value="fire_assessment">消防评定</option><option value="fire_evaluation">消防评估</option><option value="other">其他技术服务</option></select></label><label>金额<input v-model="forms.contract.value.amount" type="number" min="0" step=".01"></label><label>币种<input v-model="forms.contract.value.currency" maxlength="3"></label><label>签订日期<input v-model="forms.contract.value.signedOn" type="date"></label><label>开始日期<input v-model="forms.contract.value.startsOn" type="date"></label><label>结束日期<input v-model="forms.contract.value.endsOn" type="date"></label><label>负责人<select v-model="forms.contract.value.ownerEmployeeId"><option value="">未分配</option><option v-for="x in employees" :value="x.id">{{x.full_name}}</option></select></label><button class="primary-button">创建合同</button></form><div v-if="selectedContract" class="related-card">
          <div class="section-heading"><h4>合同详情 · {{selectedContract.contract_no}}</h4><button v-if="can('contract:write')" class="secondary-button" @click="contractEditOpen=!contractEditOpen">{{contractEditOpen?'收起编辑':'编辑合同'}}</button></div>
          <form v-if="contractEditOpen" class="create-grid compact" @submit.prevent="saveContract">
            <label>合同名称<input v-model="contractEditForm.title" required></label>
            <label>合同类型<input v-model="contractEditForm.contractType"></label>
            <label>服务类型<select v-model="contractEditForm.serviceType"><option value="maintenance">维护保养</option><option value="fire_inspection">消防检测</option><option value="fire_assessment">消防评定</option><option value="fire_evaluation">消防评估</option><option value="other">其他技术服务</option></select></label>
            <label>合同金额<input v-model="contractEditForm.amount" type="number" min="0" step=".01"></label>
            <label>币种<input v-model="contractEditForm.currency" maxlength="3"></label>
            <label>签订日期<input v-model="contractEditForm.signedOn" type="date"></label>
            <label>开始日期<input v-model="contractEditForm.startsOn" type="date"></label>
            <label>结束日期<input v-model="contractEditForm.endsOn" type="date"></label>
            <label>负责人<select v-model="contractEditForm.ownerEmployeeId"><option value="">未分配</option><option v-for="x in employees" :value="x.id">{{x.full_name}}</option></select></label>
            <label>合同状态<select v-model="contractEditForm.status"><option value="draft">草稿</option><option value="pending">待生效</option><option value="active">履行中</option><option value="completed">已完成</option><option value="terminated">已终止</option></select></label>
            <label>备注<input v-model="contractEditForm.notes"></label><button class="primary-button" :disabled="busy">保存合同资料</button>
          </form><p>{{selectedContract.title}} · {{selectedContract.customer_name}}</p><p>金额：{{selectedContract.amount}} {{selectedContract.currency}} · 状态：{{selectedContract.status}}</p><p>签订：{{selectedContract.signed_on||'未签订'}} · 履行：{{selectedContract.starts_on||'—'}} 至 {{selectedContract.ends_on||'—'}}</p><h4>合同变更</h4><p v-for="x in contractAmendments" :key="x.id" class="log-line">{{x.amendment_no}} · {{x.title}} · {{x.previous_amount}} → {{x.revised_amount}} · {{x.status}}</p><form v-if="can('contract:write')&&['active','completed'].includes(selectedContract.status)" class="create-grid compact" @submit.prevent="submit('amendment')"><label>变更编号<input v-model="forms.amendment.value.amendmentNo" required></label><label>变更名称<input v-model="forms.amendment.value.title" required></label><label>变更后金额<input v-model="forms.amendment.value.revisedAmount" type="number" min="0" step=".01" required></label><label>生效日期<input v-model="forms.amendment.value.effectiveOn" type="date"></label><label>变更说明<input v-model="forms.amendment.value.changeSummary" required></label><button class="primary-button">登记合同变更</button></form></div>
      </template>
      <template v-else>
        <h3 v-if="can('project:write')">新增项目</h3>
        <form v-if="can('project:write')" class="create-grid" @submit.prevent="submit('project')">
          <label>项目编号<input v-model="forms.project.value.projectNo" required></label>
          <label>项目名称<input v-model="forms.project.value.name" required></label>
          <label>所属甲方<select v-model="forms.project.value.customerId" required><option value="">请选择客户</option><option v-for="x in customers" :value="x.id">{{x.name}}</option></select></label>
          <label>初始关联合同（可选）<select v-model="forms.project.value.contractId"><option value="">无合同项目</option><option v-for="x in contracts" :value="x.id">{{x.contract_no}} · {{x.title}}</option></select></label>
          <label>项目负责人<select v-model="forms.project.value.managerEmployeeId"><option value="">未分配</option><option v-for="x in employees.filter(e=>e.employee_type==='engineer'&&e.status==='active'&&e.has_valid_qualification)" :value="x.id">{{x.full_name}}</option></select></label>
          <label>计划开始<input v-model="forms.project.value.plannedStart" type="date"></label>
          <label>计划结束<input v-model="forms.project.value.plannedEnd" type="date"></label>
          <label>项目说明<input v-model="forms.project.value.description"></label>
          <button class="primary-button" :disabled="busy">创建项目</button>
        </form>
        <div v-if="selectedProject" class="related-card">
          <div class="section-heading"><h4>{{selectedProject.name}} · 项目档案</h4><button v-if="can('project:write')" class="secondary-button" @click="projectEditOpen=!projectEditOpen">{{projectEditOpen?'收起编辑':'编辑项目'}}</button></div>
          <form v-if="projectEditOpen" class="create-grid compact" @submit.prevent="saveProject">
            <label>项目名称<input v-model="projectEditForm.name" required></label>
            <label>项目负责人<select v-model="projectEditForm.managerEmployeeId"><option value="">未分配</option><option v-for="x in employees.filter(e=>e.employee_type==='engineer'&&e.status==='active'&&e.has_valid_qualification)" :value="x.id">{{x.full_name}}</option></select></label>
            <label>计划开始<input v-model="projectEditForm.plannedStart" type="date"></label>
            <label>计划结束<input v-model="projectEditForm.plannedEnd" type="date"></label>
            <label>项目状态<select v-model="projectEditForm.status"><option value="planned">计划中</option><option value="in_progress">进行中</option><option value="paused">暂停</option><option value="completed">已完成</option><option value="cancelled">已取消</option></select></label>
            <label>项目说明<input v-model="projectEditForm.description"></label>
            <button class="primary-button" :disabled="busy">保存项目</button>
          </form>
          <p>编号：{{selectedProject.project_no}} · 甲方：{{selectedProject.customer_name}}</p>
          <p>状态：{{selectedProject.status}} · 负责人：{{selectedProject.manager_name||'未分配'}} · 技术负责人：{{selectedProject.technical_lead_name||'未分配'}}</p>
          <p>{{selectedProject.description||'暂无项目说明'}}</p>
          <h4>关联合同历史</h4>
          <div v-for="contract in selectedProject.contracts||[]" :key="contract.id" class="log-line">{{contract.contract_no}} · {{contract.title}} · {{contract.relation_type}} · {{contract.starts_on||'—'}} 至 {{contract.ends_on||'—'}} · {{contract.status}}</div>
          <p v-if="!selectedProject.contracts?.length" class="empty">当前尚未关联合同，可在「合作方与维保」中建立合同关系。</p>
          <h4>项目动态</h4>
          <p v-for="x in projectLogs" :key="x.id" class="log-line">{{x.created_at}} · {{x.actor_name||'系统'}} · {{x.content}}</p>
          <p v-if="!projectLogs.length" class="empty">暂无项目动态</p>
          <p class="muted">现场到场、整改问题、照片和报告请在「技术服务交付」中管理；本页只维护项目基础档案。</p>
        </div>
      </template>
    </div>
  </div>
</section>
</template>
