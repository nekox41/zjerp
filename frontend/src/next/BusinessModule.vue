<script setup>
import { onMounted, ref, watch } from 'vue'
import { api } from './api'

const section = ref('customers')
const busy = ref(false)
const error = ref('')
const notice = ref('')
const q = ref('')
const organizations = ref([])
const employees = ref([])
const customers = ref([])
const contracts = ref([])
const projects = ref([])
const selectedCustomer = ref(null)
const contacts = ref([])
const milestones = ref([])
const forms = {
  organization: ref({name:'',parentId:''}),
  employee: ref({employeeCode:'',fullName:'',organizationId:'',positionName:'',phone:'',email:''}),
  customer: ref({customerCode:'',name:'',creditCode:'',customerType:'enterprise',industry:'',phone:'',email:'',address:'',ownerEmployeeId:'',notes:''}),
  contact: ref({name:'',positionName:'',phone:'',email:'',isPrimary:false}),
  contract: ref({contractNo:'',title:'',customerId:'',contractType:'',amount:'0',currency:'CNY',signedOn:'',startsOn:'',endsOn:'',ownerEmployeeId:'',notes:''}),
  project: ref({projectNo:'',name:'',contractId:'',managerEmployeeId:'',plannedStart:'',plannedEnd:'',description:''}),
  milestone: ref({title:'',description:'',dueOn:''})
}
const rows = ref([])
const config = {
  organizations:{title:'组织架构',endpoint:'/organizations',form:'organization',permission:'organization:write'},
  employees:{title:'员工档案',endpoint:'/employees',form:'employee',permission:'employee:write'},
  customers:{title:'客户管理',endpoint:'/customers',form:'customer',permission:'customer:write'},
  contracts:{title:'合同管理',endpoint:'/contracts',form:'contract',permission:'contract:write'},
  projects:{title:'项目管理',endpoint:'/projects',form:'project',permission:'project:write'}
}
async function load() {
  error.value=''; notice.value=''
  try {
    const [o,e,c,ct,p] = await Promise.all([
      api('/business/organizations').catch(()=>[]),
      api('/business/employees').catch(()=>[]),
      api('/business/customers').catch(()=>[]),
      api('/business/contracts').catch(()=>[]),
      api('/business/projects').catch(()=>[])
    ])
    organizations.value=o; employees.value=e; customers.value=c; contracts.value=ct; projects.value=p
    const cfg=config[section.value]
    rows.value=cfg ? await api('/business'+cfg.endpoint+'?q='+encodeURIComponent(q.value)).catch(()=>[]) : []
  } catch(e) { error.value=e.message }
}
async function submit(kind) {
  busy.value=true; error.value=''; notice.value=''
  try {
    const data={...forms[kind].value}
    Object.keys(data).forEach(k=>{if(data[k]==='') data[k]=null})
    const endpoints={organization:'/organizations',employee:'/employees',customer:'/customers',contact:'/customers/'+selectedCustomer.value.id+'/contacts',contract:'/contracts',project:'/projects',milestone:'/projects/'+selectedProject.value.id+'/milestones'}
    await api('/business'+endpoints[kind],{method:'POST',body:JSON.stringify(data)})
    notice.value='保存成功'; forms[kind].value=Object.fromEntries(Object.keys(forms[kind].value).map(k=>[k,typeof forms[k].value[k]==='boolean'?false:'']))
    await load()
    if(kind==='contact') await openCustomer(selectedCustomer.value.id)
    if(kind==='milestone') await openProject(selectedProject.value.id)
  } catch(e) { error.value=e.message } finally { busy.value=false }
}
async function openCustomer(id) {
  try { selectedCustomer.value=await api('/business/customers/'+id); contacts.value=selectedCustomer.value.contacts||[] }
  catch(e){error.value=e.message}
}
const selectedProject=ref(null)
async function openProject(id) {
  try { selectedProject.value=projects.value.find(x=>x.id===id)||{id}; milestones.value=await api('/business/projects/'+id+'/milestones') }
  catch(e){error.value=e.message}
}
async function completeMilestone(id) {
  try { await api('/business/milestones/'+id+'/complete',{method:'PUT'}); await openProject(selectedProject.value.id); notice.value='里程碑已完成' }
  catch(e){error.value=e.message}
}
async function addMember(projectId) {
  const employeeId=window.prompt('输入员工 ID')
  if(!employeeId) return
  try { await api('/business/projects/'+projectId+'/members',{method:'POST',body:JSON.stringify({employeeId,role:'项目成员'})}); notice.value='成员已分配' }
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
      <div class="business-list-head"><div><h3>{{config[section].title}}</h3><p>统一维护业务主数据与关联记录</p></div><input v-model="q" placeholder="搜索名称或编号" @keyup.enter="load"><button class="secondary-button" @click="load">搜索</button></div>
      <div class="business-records">
        <button v-for="r in rows" :key="r.id" class="business-record" @click="section==='customers'?openCustomer(r.id):section==='projects'?openProject(r.id):null">
          <b>{{r.name||r.full_name||r.title}}</b><small>{{r.customer_code||r.employee_code||r.contract_no||r.project_no||r.status||r.position_name}}</small>
          <span>{{r.customer_name||r.organization_name||r.owner_name||''}}</span>
        </button>
        <p v-if="!rows.length" class="empty">暂无记录，使用下方表单创建第一条数据。</p>
      </div>
    </div>
    <div class="business-forms panel">
      <template v-if="section==='organizations'">
        <h3>新增组织</h3><form class="create-grid" @submit.prevent="submit('organization')"><label>部门名称<input v-model="forms.organization.value.name" required></label><label>上级部门<select v-model="forms.organization.value.parentId"><option value="">无</option><option v-for="x in organizations" :value="x.id">{{x.name}}</option></select></label><button class="primary-button">创建部门</button></form>
      </template>
      <template v-else-if="section==='employees'">
        <h3>新增员工档案</h3><form class="create-grid" @submit.prevent="submit('employee')"><label>员工编号<input v-model="forms.employee.value.employeeCode"></label><label>姓名<input v-model="forms.employee.value.fullName" required></label><label>所属部门<select v-model="forms.employee.value.organizationId"><option value="">未分配</option><option v-for="x in organizations" :value="x.id">{{x.name}}</option></select></label><label>岗位<input v-model="forms.employee.value.positionName"></label><label>电话<input v-model="forms.employee.value.phone"></label><label>邮箱<input v-model="forms.employee.value.email" type="email"></label><button class="primary-button">创建员工</button></form>
      </template>
      <template v-else-if="section==='customers'">
        <h3>新增客户</h3><form class="create-grid" @submit.prevent="submit('customer')"><label>客户编号<input v-model="forms.customer.value.customerCode" required></label><label>客户名称<input v-model="forms.customer.value.name" required></label><label>统一社会信用代码<input v-model="forms.customer.value.creditCode"></label><label>客户类型<select v-model="forms.customer.value.customerType"><option value="enterprise">企业</option><option value="government">政府/事业单位</option><option value="individual">个人</option></select></label><label>行业<input v-model="forms.customer.value.industry"></label><label>负责人<select v-model="forms.customer.value.ownerEmployeeId"><option value="">未分配</option><option v-for="x in employees" :value="x.id">{{x.full_name}}</option></select></label><label>联系电话<input v-model="forms.customer.value.phone"></label><label>电子邮箱<input v-model="forms.customer.value.email"></label><label>地址<input v-model="forms.customer.value.address"></label><label>备注<input v-model="forms.customer.value.notes"></label><button class="primary-button">创建客户</button></form>
        <div v-if="selectedCustomer" class="related-card"><h4>{{selectedCustomer.name}} · 联系人</h4><p v-for="x in contacts" :key="x.id">{{x.name}} · {{x.phone||'无电话'}} · {{x.email||''}} <span v-if="x.is_primary">主要联系人</span></p><form class="create-grid compact" @submit.prevent="submit('contact')"><label>联系人姓名<input v-model="forms.contact.value.name" required></label><label>岗位<input v-model="forms.contact.value.positionName"></label><label>电话<input v-model="forms.contact.value.phone"></label><label>邮箱<input v-model="forms.contact.value.email"></label><label class="inline-check"><input type="checkbox" v-model="forms.contact.value.isPrimary">主要联系人</label><button class="primary-button">添加联系人</button></form></div>
      </template>
      <template v-else-if="section==='contracts'">
        <h3>新增合同</h3><form class="create-grid" @submit.prevent="submit('contract')"><label>合同编号<input v-model="forms.contract.value.contractNo" required></label><label>合同名称<input v-model="forms.contract.value.title" required></label><label>客户<select v-model="forms.contract.value.customerId" required><option value="">请选择客户</option><option v-for="x in customers" :value="x.id">{{x.name}}</option></select></label><label>合同类型<input v-model="forms.contract.value.contractType"></label><label>金额<input v-model="forms.contract.value.amount" type="number" min="0" step=".01"></label><label>币种<input v-model="forms.contract.value.currency" maxlength="3"></label><label>签订日期<input v-model="forms.contract.value.signedOn" type="date"></label><label>开始日期<input v-model="forms.contract.value.startsOn" type="date"></label><label>结束日期<input v-model="forms.contract.value.endsOn" type="date"></label><label>负责人<select v-model="forms.contract.value.ownerEmployeeId"><option value="">未分配</option><option v-for="x in employees" :value="x.id">{{x.full_name}}</option></select></label><button class="primary-button">创建合同</button></form>
      </template>
      <template v-else>
        <h3>新增项目</h3><form class="create-grid" @submit.prevent="submit('project')"><label>项目编号<input v-model="forms.project.value.projectNo" required></label><label>项目名称<input v-model="forms.project.value.name" required></label><label>关联合同<select v-model="forms.project.value.contractId" required><option value="">请选择合同</option><option v-for="x in contracts" :value="x.id">{{x.contract_no}} · {{x.title}}</option></select></label><label>项目经理<select v-model="forms.project.value.managerEmployeeId"><option value="">未分配</option><option v-for="x in employees" :value="x.id">{{x.full_name}}</option></select></label><label>计划开始<input v-model="forms.project.value.plannedStart" type="date"></label><label>计划结束<input v-model="forms.project.value.plannedEnd" type="date"></label><label>项目说明<input v-model="forms.project.value.description"></label><button class="primary-button">创建项目</button></form>
        <div v-if="selectedProject" class="related-card"><h4>{{selectedProject.name}} · 里程碑</h4><div v-for="m in milestones" :key="m.id" class="milestone-row"><span>{{m.title}} · {{m.due_on||'未设期限'}}</span><button v-if="m.status==='pending'" class="secondary-button" @click="completeMilestone(m.id)">标记完成</button><b v-else>{{m.status}}</b></div><form class="create-grid compact" @submit.prevent="submit('milestone')"><label>里程碑名称<input v-model="forms.milestone.value.title" required></label><label>计划日期<input v-model="forms.milestone.value.dueOn" type="date"></label><label>说明<input v-model="forms.milestone.value.description"></label><button class="primary-button">添加里程碑</button></form><button class="secondary-button" @click="addMember(selectedProject.id)">分配项目成员（输入员工 ID）</button></div>
      </template>
    </div>
  </div>
</section>
</template>
