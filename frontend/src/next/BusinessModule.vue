<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { api, downloadFile, uploadFile } from './api'
import ProjectWorkspace from './ProjectWorkspace.vue'

const props = defineProps({ permissions: { type: Array, default: () => [] } })
const section = ref('customers')
const query = ref('')
const rows = ref([])
const customers = ref([])
const employees = ref([])
const identities = ref([])
const intermediaries = ref([])
const contracts = ref([])
const selected = ref(null)
const editMode = ref(false)
const editForm = ref({})
const contactEditId = ref(null)
const selectedProjectId = ref(null)
const qualifications = ref([])
const contactForm = ref({ name:'', positionName:'', phone:'', email:'', isPrimary:false, notes:'' })
const qualificationForm = ref({ qualificationName:'', certificateNo:'', issuingAuthority:'', issuedOn:'', expiresOn:'', notes:'' })
const attachmentForm = ref({ attachmentType:'signed_contract', title:'' })
const forms = ref({
  customer:{ customerCode:'', name:'', creditCode:'', customerType:'enterprise', industry:'', phone:'', email:'', address:'', ownerEmployeeId:'', notes:'' },
  intermediary:{ intermediaryCode:'', name:'', contactName:'', phone:'', email:'', address:'', notes:'' },
  employee:{ employeeCode:'', fullName:'', employeeType:'engineer', identityId:'', positionName:'', phone:'', email:'' },
  contract:{ contractNo:'', title:'', customerId:'', contractType:'maintenance', signingMode:'direct', intermediaryId:'', intermediarySharePercent:'', amount:'0', currency:'CNY', signedOn:'', startsOn:'', endsOn:'', ownerEmployeeId:'', notes:'' },
  project:{ projectNo:'', name:'', customerId:'', contractId:'', managerEmployeeId:'', technicalLeadEmployeeId:'', plannedStart:'', plannedEnd:'', description:'' }
})
const busy = ref(false)
const loading = ref(false)
const error = ref('')
const notice = ref('')
const showCreate = ref(false)
const can = p => props.permissions.includes(p)
const sections = [
  {id:'customers',label:'客户',permission:'customer:read',endpoint:'/business/customers'},
  {id:'intermediaries',label:'中间人',permission:'intermediary:read',endpoint:'/business/intermediaries'},
  {id:'employees',label:'员工与资格',permission:'employee:read',endpoint:'/business/employees'},
  {id:'contracts',label:'合同',permission:'contract:read',endpoint:'/business/contracts'},
  {id:'projects',label:'项目',permission:'project:read',endpoint:'/business/projects'}
]
const activeSection = computed(() => sections.find(x=>x.id===section.value) || sections[0])
const engineers = computed(() => employees.value.filter(e=>e.employee_type==='engineer' && e.status==='active'))

async function load() {
  loading.value=true; error.value=''
  try {
    const [c,e,m,ct,p,ids] = await Promise.all([
      api('/business/customers').catch(()=>[]), api('/business/employees').catch(()=>[]),
      api('/business/intermediaries').catch(()=>[]), api('/business/contracts').catch(()=>[]),
      api('/business/projects').catch(()=>[]), api('/admin/identities').catch(()=>[])
    ])
    customers.value=c; employees.value=e; intermediaries.value=m; contracts.value=ct; identities.value=ids
    const allowed=sections.filter(x=>can(x.permission))
    if(!allowed.some(x=>x.id===section.value)) section.value=allowed[0]?.id || 'customers'
    await loadRows()
  } catch(e) { error.value=e.message }
  finally { loading.value=false }
}
async function loadRows() {
  const cfg=activeSection.value
  if(!cfg || !can(cfg.permission)) { rows.value=[]; return }
  rows.value=await api(cfg.endpoint+'?q='+encodeURIComponent(query.value)).catch(()=>[])
}
async function selectSection(id) {
  section.value=id; selected.value=null; showCreate.value=false; await loadRows()
}
function resetForm() {
  const defaults={
    customer:{ customerCode:'', name:'', creditCode:'', customerType:'enterprise', industry:'', phone:'', email:'', address:'', ownerEmployeeId:'', notes:'' },
    intermediary:{ intermediaryCode:'', name:'', contactName:'', phone:'', email:'', address:'', notes:'' },
    employee:{ employeeCode:'', fullName:'', employeeType:'engineer', identityId:'', positionName:'', phone:'', email:'' },
    contract:{ contractNo:'', title:'', customerId:'', contractType:'maintenance', signingMode:'direct', intermediaryId:'', intermediarySharePercent:'', amount:'0', currency:'CNY', signedOn:'', startsOn:'', endsOn:'', ownerEmployeeId:'', notes:'' },
    project:{ projectNo:'', name:'', customerId:'', contractId:'', managerEmployeeId:'', technicalLeadEmployeeId:'', plannedStart:'', plannedEnd:'', description:'' }
  }
  forms.value[section.value]={...defaults[section.value]}
}
async function createRecord() {
  busy.value=true; error.value=''; notice.value=''
  try {
    const body={...forms.value[section.value]}
    Object.keys(body).forEach(k=>{if(body[k]==='') body[k]=null})
    if(section.value==='contract') {
      if(body.signingMode==='direct') { body.intermediaryId=null; body.intermediarySharePercent=null }
    }
    if(section.value==='project') {
      body.customerId=Number(body.customerId)
      body.contractId=body.contractId ? Number(body.contractId) : null
      body.managerEmployeeId=body.managerEmployeeId ? Number(body.managerEmployeeId) : null
      body.technicalLeadEmployeeId=body.technicalLeadEmployeeId ? Number(body.technicalLeadEmployeeId) : null
    }
    if(section.value==='employee') body.identityId=body.identityId ? Number(body.identityId) : null
    if(section.value==='contract') {
      body.customerId=Number(body.customerId)
      body.ownerEmployeeId=body.ownerEmployeeId ? Number(body.ownerEmployeeId) : null
      body.intermediaryId=body.intermediaryId ? Number(body.intermediaryId) : null
    }
    if(section.value==='customer') body.ownerEmployeeId=body.ownerEmployeeId ? Number(body.ownerEmployeeId) : null
    const result=await api(activeSection.value.endpoint,{method:'POST',body:JSON.stringify(body)})
    notice.value='创建成功。'; showCreate.value=false; resetForm()
    await load(); if(section.value==='customers' || section.value==='intermediaries' || section.value==='contracts') await openDetail(result.id)
  } catch(e) { error.value=e.message }
  finally { busy.value=false }
}
async function openDetail(id) {
  editMode.value=false; contactEditId.value=null
  error.value=''
  try {
    const endpoint={
      customers:'/business/customers/', intermediaries:'/business/intermediaries/',
      contracts:'/business/contracts/', employees:'/business/employees/', projects:'/business/projects/'
    }[section.value]
    selected.value=await api(endpoint+id)
    if(section.value==='employees') qualifications.value=await api('/business/employees/'+id+'/qualifications')
  } catch(e) { error.value=e.message }
}
async function addContact() {
  if(!selected.value) return
  busy.value=true; error.value=''
  try {
    if(contactEditId.value) await api('/business/contacts/'+contactEditId.value,{method:'PUT',body:JSON.stringify(contactForm.value)})
    else await api('/business/customers/'+selected.value.id+'/contacts',{method:'POST',body:JSON.stringify(contactForm.value)})
    contactEditId.value=null
    contactForm.value={name:'',positionName:'',phone:'',email:'',isPrimary:false,notes:''}
    selected.value=await api('/business/customers/'+selected.value.id)
  } catch(e) { error.value=e.message }
  finally { busy.value=false }
}
function startEdit() {
  if(!selected.value) return
  editMode.value=true
  const x=selected.value
  if(section.value==='customers') editForm.value={name:x.name,creditCode:x.credit_code,industry:x.industry,phone:x.phone,email:x.email,address:x.address,status:x.status,notes:x.notes}
  else if(section.value==='intermediaries') editForm.value={name:x.name,contactName:x.contact_name,phone:x.phone,email:x.email,address:x.address,status:x.status,notes:x.notes}
  else if(section.value==='employees') editForm.value={fullName:x.full_name,employeeType:x.employee_type,identityId:x.identity_id || '',positionName:x.position_name,phone:x.phone,email:x.email,status:x.status}
  else if(section.value==='contracts') editForm.value={title:x.title,contractType:x.contract_type,signingMode:x.signing_mode,intermediaryId:x.intermediary_id || '',intermediarySharePercent:x.intermediary_share_percent ?? '',amount:x.amount,currency:x.currency,status:x.status,signedOn:x.signed_on,startsOn:x.starts_on,endsOn:x.ends_on,notes:x.notes}
}
async function saveEdit() {
  busy.value=true; error.value=''
  try {
    const routes={customers:'/business/customers/',intermediaries:'/business/intermediaries/',employees:'/business/employees/',contracts:'/business/contracts/'}
    const body={...editForm.value}
    if(section.value==='employees') body.identityId=body.identityId ? Number(body.identityId) : null
    if(section.value==='contracts') {
      if(body.signingMode==='direct'){body.intermediaryId=null;body.intermediarySharePercent=null}
      else {body.intermediaryId=body.intermediaryId?Number(body.intermediaryId):null}
    }
    await api(routes[section.value]+selected.value.id,{method:'PUT',body:JSON.stringify(body)})
    selected.value=await api(routes[section.value]+selected.value.id)
    if(section.value==='employees') qualifications.value=await api('/business/employees/'+selected.value.id+'/qualifications')
    editMode.value=false; notice.value='档案已更新。'; await loadRows()
  } catch(e) { error.value=e.message }
  finally { busy.value=false }
}
function resetContactForm() { contactEditId.value=null; contactForm.value={name:'',positionName:'',phone:'',email:'',isPrimary:false,notes:''} }
function editContact(contact) {
  contactEditId.value=contact.id
  contactForm.value={name:contact.name,positionName:contact.position_name || '',phone:contact.phone || '',email:contact.email || '',isPrimary:!!contact.is_primary,notes:contact.notes || ''}
}
async function removeContact(contact) {
  if(!confirm('确定删除联系人吗？')) return
  busy.value=true; error.value=''
  try {
    await api('/business/contacts/'+contact.id,{method:'DELETE'})
    selected.value=await api('/business/customers/'+selected.value.id)
  } catch(e) { error.value=e.message }
  finally { busy.value=false }
}
async function addQualification() {
  busy.value=true; error.value=''
  try {
    await api('/business/employees/'+selected.value.id+'/qualifications',{method:'POST',body:JSON.stringify(qualificationForm.value)})
    qualificationForm.value={qualificationName:'',certificateNo:'',issuingAuthority:'',issuedOn:'',expiresOn:'',notes:''}
    qualifications.value=await api('/business/employees/'+selected.value.id+'/qualifications')
  } catch(e) { error.value=e.message }
  finally { busy.value=false }
}
async function uploadContractAttachment(event) {
  const file=event.target.files?.[0]; event.target.value=''
  if(!file) return
  busy.value=true; error.value=''
  try {
    const saved=await uploadFile(file)
    await api('/business/contracts/'+selected.value.id+'/attachments',{method:'POST',body:JSON.stringify({
      fileId:saved.id,attachmentType:attachmentForm.value.attachmentType,title:attachmentForm.value.title || file.name
    })})
    selected.value=await api('/business/contracts/'+selected.value.id)
    notice.value='合同附件已保存。'
  } catch(e) { error.value=e.message }
  finally { busy.value=false }
}
function openProject(id) { selectedProjectId.value=id }
watch(query,loadRows)
onMounted(load)
</script>

<template>
  <section class="business-workspace">
    <ProjectWorkspace v-if="selectedProjectId" :project-id="selectedProjectId" :permissions="permissions" @back="selectedProjectId=null" @changed="load"/>
    <template v-else>
      <header class="bw-header">
        <div><span class="eyebrow">BUSINESS LIFECYCLE</span><h2>业务全生命周期</h2><p>客户、中间人、合同、项目与员工档案统一管理。</p></div>
        <button v-if="can(activeSection.id==='customers'?'customer:write':activeSection.id==='intermediaries'?'intermediary:write':activeSection.id==='employees'?'employee:write':activeSection.id==='contracts'?'contract:write':'project:write')" class="bw-primary" @click="showCreate=!showCreate">{{ showCreate ? '收起' : '＋ 新建'+activeSection.label }}</button>
      </header>

      <nav class="bw-tabs">
        <button v-for="item in sections.filter(x=>can(x.permission))" :key="item.id" :class="{active:section===item.id}" @click="selectSection(item.id)">{{ item.label }}</button>
      </nav>
      <p v-if="error" class="bw-message error" role="alert">{{ error }}</p>
      <p v-if="notice" class="bw-message success" role="status">{{ notice }}</p>

      <section v-if="showCreate" class="bw-panel">
        <div class="bw-panel-head"><h3>新建{{ activeSection.label }}</h3><button @click="showCreate=false">关闭</button></div>
        <form class="bw-form" @submit.prevent="createRecord">
          <template v-if="section==='customers'">
            <label>客户编号<input v-model="forms.customer.customerCode" required></label><label>客户名称<input v-model="forms.customer.name" required></label>
            <label>统一社会信用代码<input v-model="forms.customer.creditCode"></label><label>客户类型<select v-model="forms.customer.customerType"><option value="enterprise">企业</option><option value="government">政府/事业单位</option><option value="individual">个人</option><option value="other">其他</option></select></label>
            <label>行业<input v-model="forms.customer.industry"></label><label>联系电话<input v-model="forms.customer.phone"></label>
            <label>电子邮箱<input v-model="forms.customer.email"></label><label>客户负责人<select v-model="forms.customer.ownerEmployeeId"><option value="">暂不指定</option><option v-for="e in employees" :key="e.id" :value="e.id">{{ e.full_name }}</option></select></label>
            <label class="wide">地址<input v-model="forms.customer.address"></label><label class="wide">备注<textarea v-model="forms.customer.notes"></textarea></label>
          </template>
          <template v-else-if="section==='intermediaries'">
            <label>中间人编号<input v-model="forms.intermediary.intermediaryCode" required></label><label>中间人/合作方名称<input v-model="forms.intermediary.name" required></label>
            <label>联系人<input v-model="forms.intermediary.contactName"></label><label>联系电话<input v-model="forms.intermediary.phone"></label>
            <label>邮箱<input v-model="forms.intermediary.email"></label><label>地址<input v-model="forms.intermediary.address"></label>
            <label class="wide">备注<textarea v-model="forms.intermediary.notes"></textarea></label>
          </template>
          <template v-else-if="section==='employees'">
            <label>员工编号<input v-model="forms.employee.employeeCode" required></label><label>姓名<input v-model="forms.employee.fullName" required></label>
            <label>员工类型<select v-model="forms.employee.employeeType"><option value="engineer">工程师</option><option value="operator">操作员</option><option value="employee">普通员工</option><option value="manager">管理人员</option></select></label>
            <label>关联登录账号（可选）<select v-model="forms.employee.identityId"><option value="">暂不关联账号</option><option v-for="i in identities" :key="i.id" :value="i.id">{{ i.display_name }} · {{ i.username }}</option></select></label>
            <label>岗位<input v-model="forms.employee.positionName"></label><label>联系电话<input v-model="forms.employee.phone"></label><label>邮箱<input v-model="forms.employee.email"></label>
          </template>
          <template v-else-if="section==='contracts'">
            <label>合同编号<input v-model="forms.contract.contractNo" required></label><label>合同名称<input v-model="forms.contract.title" required></label>
            <label>甲方/客户<select v-model="forms.contract.customerId" required><option value="">选择客户</option><option v-for="c in customers" :key="c.id" :value="c.id">{{ c.name }}</option></select></label>
            <label>合同类型<select v-model="forms.contract.contractType"><option value="maintenance">维护保养</option><option value="fire_inspection">消防检测</option><option value="fire_evaluation">消防评估</option><option value="fire_assessment">消防验收评定</option><option value="other">其他</option></select></label>
            <label>签约方式<select v-model="forms.contract.signingMode"><option value="direct">甲方直接签约</option><option value="intermediary">通过中间人</option></select></label>
            <label v-if="forms.contract.signingMode==='intermediary'">中间人<select v-model="forms.contract.intermediaryId" required><option value="">选择中间人</option><option v-for="m in intermediaries.filter(x=>x.status==='active')" :key="m.id" :value="m.id">{{ m.name }}</option></select></label>
            <label v-if="forms.contract.signingMode==='intermediary'">中间人分成比例（%）<input v-model="forms.contract.intermediarySharePercent" type="number" min="0" max="100" step="0.01" required></label>
            <label>合同金额<input v-model="forms.contract.amount" type="number" min="0" step="0.01"></label><label>币种<input v-model="forms.contract.currency" maxlength="3"></label>
            <label>签订日期<input v-model="forms.contract.signedOn" type="date"></label><label>开始日期<input v-model="forms.contract.startsOn" type="date"></label><label>结束日期<input v-model="forms.contract.endsOn" type="date"></label>
            <label>合同负责人<select v-model="forms.contract.ownerEmployeeId"><option value="">暂不指定</option><option v-for="e in employees" :key="e.id" :value="e.id">{{ e.full_name }}</option></select></label>
            <label class="wide">备注<textarea v-model="forms.contract.notes"></textarea></label>
          </template>
          <template v-else>
            <label>项目编号<input v-model="forms.project.projectNo" required></label><label>项目名称<input v-model="forms.project.name" required></label>
            <label>客户/甲方<select v-model="forms.project.customerId" required><option value="">选择客户</option><option v-for="c in customers" :key="c.id" :value="c.id">{{ c.name }}</option></select></label>
            <label>当前合同（可暂不选择）<select v-model="forms.project.contractId"><option value="">暂未签约</option><option v-for="c in contracts.filter(x=>String(x.customer_id)===String(forms.project.customerId))" :key="c.id" :value="c.id">{{ c.contract_no }} · {{ c.title }}</option></select></label>
            <label>项目负责人（工程师）<select v-model="forms.project.managerEmployeeId"><option value="">暂不指定</option><option v-for="e in engineers" :key="e.id" :value="e.id">{{ e.full_name }}</option></select></label>
            <label>技术负责人（工程师）<select v-model="forms.project.technicalLeadEmployeeId"><option value="">暂不指定</option><option v-for="e in engineers" :key="e.id" :value="e.id">{{ e.full_name }}</option></select></label>
            <label>计划开始<input v-model="forms.project.plannedStart" type="date"></label><label>计划结束<input v-model="forms.project.plannedEnd" type="date"></label>
            <label class="wide">项目描述<textarea v-model="forms.project.description"></textarea></label>
          </template>
          <div class="bw-form-actions"><button class="bw-primary" :disabled="busy">保存</button><button type="button" class="bw-secondary" @click="resetForm">重置</button></div>
        </form>
      </section>

      <section class="bw-list-panel">
        <div class="bw-list-head"><div><h3>{{ activeSection.label }}列表</h3><small>{{ rows.length }} 条记录</small></div><div class="bw-search"><input v-model="query" placeholder="搜索编号、名称、联系人"><button @click="loadRows">搜索</button></div></div>
        <div v-if="loading" class="bw-empty">正在加载…</div>
        <div v-else-if="section==='projects'" class="bw-cards">
          <article v-for="item in rows" :key="item.id" class="bw-project-card" @click="openProject(item.id)">
            <div><span>{{ item.project_no }}</span><b>{{ item.name }}</b><small>{{ item.customer_name }}</small></div>
            <div class="bw-card-meta"><span>{{ item.contract_no || '未签合同' }}</span><span>{{ item.manager_name || '未分配负责人' }}</span></div>
            <div class="bw-card-foot"><small>{{ item.status }}</small><b>进入项目 →</b></div>
          </article>
        </div>
        <div v-else class="bw-table-wrap"><table><thead><tr>
          <th v-if="section==='customers'">客户编号 / 名称</th>
          <th v-else-if="section==='intermediaries'">中间人编号 / 名称</th>
          <th v-else-if="section==='employees'">员工编号 / 姓名</th>
          <th v-else>合同编号 / 名称</th>
          <th v-if="section==='customers'">联系人</th><th v-if="section==='customers'">合同数</th>
          <th v-if="section==='intermediaries'">联系人</th><th v-if="section==='intermediaries'">关联合同</th>
          <th v-if="section==='employees'">员工类型</th><th v-if="section==='employees'">岗位</th>
          <th v-if="section==='contracts'">客户</th><th v-if="section==='contracts'">签约方式</th><th v-if="section==='contracts'">金额</th><th v-if="section==='contracts'">状态</th>
          <th>操作</th>
        </tr></thead><tbody>
          <tr v-for="item in rows" :key="item.id">
            <td><b>{{ item.customer_code || item.intermediary_code || item.employee_code || item.contract_no }}</b><small>{{ item.name || item.full_name || item.title }}</small></td>
            <td v-if="section==='customers'">{{ item.contact_count || 0 }}</td><td v-if="section==='customers'">{{ item.contract_count || 0 }}</td>
            <td v-if="section==='intermediaries'">{{ item.contact_name || '—' }}</td><td v-if="section==='intermediaries'">{{ item.contract_count || 0 }}</td>
            <td v-if="section==='employees'">{{ ({engineer:'工程师',operator:'操作员',employee:'普通员工',manager:'管理人员'})[item.employee_type] }}</td><td v-if="section==='employees'">{{ item.position_name || '—' }}</td>
            <td v-if="section==='contracts'">{{ item.customer_name }}</td><td v-if="section==='contracts'">{{ item.signing_mode==='intermediary' ? '中间人签约' : '直接签约' }}</td><td v-if="section==='contracts'">{{ item.currency }} {{ item.amount }}</td><td v-if="section==='contracts'">{{ item.status }}</td>
            <td><button class="bw-link" @click="openDetail(item.id)">详情 →</button></td>
          </tr>
        </tbody></table><p v-if="!rows.length" class="bw-empty">暂无{{ activeSection.label }}记录</p></div>
      </section>

      <section v-if="selected && section!=='projects'" class="bw-detail-panel">
        <div class="bw-panel-head"><h3>{{ selected.name || selected.full_name || selected.title }} · 详情</h3><div class="bw-head-actions"><button v-if="section!=='projects' && can(section==='customers'?'customer:write':section==='intermediaries'?'intermediary:write':section==='employees'?'employee:write':'contract:write')" @click="editMode=!editMode;editMode?startEdit():null">{{ editMode?'取消编辑':'编辑档案' }}</button><button @click="selected=null">关闭</button></div></div>
        <form v-if="editMode" class="bw-form bw-edit-form" @submit.prevent="saveEdit">
          <template v-if="section==='customers'">
            <label>客户名称<input v-model="editForm.name" required></label><label>统一社会信用代码<input v-model="editForm.creditCode"></label>
            <label>行业<input v-model="editForm.industry"></label><label>电话<input v-model="editForm.phone"></label><label>邮箱<input v-model="editForm.email"></label><label>状态<select v-model="editForm.status"><option value="active">正常</option><option value="disabled">停用</option></select></label><label class="wide">地址<input v-model="editForm.address"></label><label class="wide">备注<textarea v-model="editForm.notes"></textarea></label>
          </template>
          <template v-else-if="section==='intermediaries'">
            <label>名称<input v-model="editForm.name" required></label><label>联系人<input v-model="editForm.contactName"></label><label>电话<input v-model="editForm.phone"></label><label>邮箱<input v-model="editForm.email"></label><label>状态<select v-model="editForm.status"><option value="active">合作中</option><option value="disabled">停用</option></select></label><label class="wide">地址<input v-model="editForm.address"></label><label class="wide">备注<textarea v-model="editForm.notes"></textarea></label>
          </template>
          <template v-else-if="section==='employees'">
            <label>姓名<input v-model="editForm.fullName" required></label><label>员工类型<select v-model="editForm.employeeType"><option value="engineer">工程师</option><option value="operator">操作员</option><option value="employee">普通员工</option><option value="manager">管理人员</option></select></label>
            <label>登录账号<select v-model="editForm.identityId"><option value="">不关联账号</option><option v-for="i in identities" :key="i.id" :value="i.id">{{ i.display_name }} · {{ i.username }}</option></select></label><label>岗位<input v-model="editForm.positionName"></label><label>电话<input v-model="editForm.phone"></label><label>邮箱<input v-model="editForm.email"></label><label>状态<select v-model="editForm.status"><option value="active">在职</option><option value="inactive">离职/停用</option></select></label>
          </template>
          <template v-else>
            <label>合同名称<input v-model="editForm.title" required></label><label>合同类型<select v-model="editForm.contractType"><option value="maintenance">维护保养</option><option value="fire_inspection">消防检测</option><option value="fire_evaluation">消防评估</option><option value="fire_assessment">消防验收评定</option><option value="other">其他</option></select></label>
            <label>签约方式<select v-model="editForm.signingMode"><option value="direct">直接签约</option><option value="intermediary">中间人签约</option></select></label>
            <label v-if="editForm.signingMode==='intermediary'">中间人<select v-model="editForm.intermediaryId"><option value="">选择中间人</option><option v-for="m in intermediaries" :key="m.id" :value="m.id">{{ m.name }}</option></select></label>
            <label v-if="editForm.signingMode==='intermediary'">分成比例（%）<input v-model="editForm.intermediarySharePercent" type="number" min="0" max="100" step="0.01"></label>
            <label>金额<input v-model="editForm.amount" type="number" min="0" step="0.01"></label><label>币种<input v-model="editForm.currency"></label><label>状态<select v-model="editForm.status"><option value="draft">草稿</option><option value="active">生效</option><option value="completed">已完成</option><option value="terminated">已终止</option></select></label>
            <label>签订日期<input v-model="editForm.signedOn" type="date"></label><label>开始日期<input v-model="editForm.startsOn" type="date"></label><label>结束日期<input v-model="editForm.endsOn" type="date"></label><label class="wide">备注<textarea v-model="editForm.notes"></textarea></label>
          </template>
          <div class="bw-form-actions"><button class="bw-primary" :disabled="busy">保存修改</button></div>
        </form>
        <template v-if="section==='customers'">
          <div class="bw-detail-grid"><div><small>客户编号</small><b>{{ selected.customer_code }}</b></div><div><small>统一社会信用代码</small><b>{{ selected.credit_code || '—' }}</b></div><div><small>电话</small><b>{{ selected.phone || '—' }}</b></div><div><small>地址</small><b>{{ selected.address || '—' }}</b></div></div>
          <h4>联系人</h4><div class="bw-mini-list"><div v-for="c in selected.contacts || []" :key="c.id"><b>{{ c.name }}{{ c.is_primary?'（主要联系人）':'' }}</b><span>{{ c.position_name || '' }} · {{ c.phone || '—' }} · {{ c.email || '—' }}</span><button v-if="can('customer:write')" type="button" @click="editContact(c)">编辑</button><button v-if="can('customer:write')" type="button" @click="removeContact(c)">删除</button></div></div>
          <form v-if="can('customer:write')" class="bw-inline-form" @submit.prevent="addContact"><input v-model="contactForm.name" required placeholder="联系人姓名"><input v-model="contactForm.positionName" placeholder="职位"><input v-model="contactForm.phone" placeholder="电话"><input v-model="contactForm.email" placeholder="邮箱"><label><input type="checkbox" v-model="contactForm.isPrimary"> 主要联系人</label><button class="bw-primary">{{ contactEditId?'保存联系人':'添加联系人' }}</button><button v-if="contactEditId" type="button" class="bw-secondary" @click="resetContactForm()">取消</button></form>
          <h4>关联合同</h4><div class="bw-mini-list"><div v-for="c in selected.contracts || []" :key="c.id"><b>{{ c.contract_no }} · {{ c.title }}</b><span>{{ c.status }} · {{ c.currency }} {{ c.amount }}</span></div></div>
        </template>
        <template v-else-if="section==='intermediaries'">
          <div class="bw-detail-grid"><div><small>联系人</small><b>{{ selected.contact_name || '—' }}</b></div><div><small>电话</small><b>{{ selected.phone || '—' }}</b></div><div><small>邮箱</small><b>{{ selected.email || '—' }}</b></div><div><small>状态</small><b>{{ selected.status }}</b></div></div>
          <h4>关联合同</h4><div class="bw-mini-list"><div v-for="c in selected.contracts || []" :key="c.id"><b>{{ c.contract_no }} · {{ c.title }}</b><span>{{ c.customer_name }} · {{ c.intermediary_share_percent }}% · {{ c.status }}</span></div></div>
        </template>
        <template v-else-if="section==='employees'">
          <div class="bw-detail-grid"><div><small>员工类型</small><b>{{ ({engineer:'工程师',operator:'操作员',employee:'普通员工',manager:'管理人员'})[selected.employee_type] }}</b></div><div><small>岗位</small><b>{{ selected.position_name || '—' }}</b></div><div><small>电话</small><b>{{ selected.phone || '—' }}</b></div><div><small>状态</small><b>{{ selected.status }}</b></div><div><small>登录账号</small><b>{{ selected.username || "未关联" }}</b></div></div>
          <h4>工程师资格记录</h4><div class="bw-mini-list"><div v-for="q in qualifications" :key="q.id"><b>{{ q.qualification_name }}</b><span>{{ q.certificate_no || '无证书编号' }} · 有效期至 {{ q.expires_on || '长期' }} · {{ q.effective_status || q.status }}</span></div></div>
          <form v-if="selected.employee_type==='engineer' && can('employee:write')" class="bw-form compact" @submit.prevent="addQualification">
            <label>资格名称<input v-model="qualificationForm.qualificationName" required></label><label>证书编号<input v-model="qualificationForm.certificateNo"></label><label>发证机关<input v-model="qualificationForm.issuingAuthority"></label><label>发证日期<input v-model="qualificationForm.issuedOn" type="date"></label><label>有效期至<input v-model="qualificationForm.expiresOn" type="date"></label><label>备注<input v-model="qualificationForm.notes"></label><button class="bw-primary">添加资格</button>
          </form>
        </template>
        <template v-else-if="section==='contracts'">
          <div class="bw-detail-grid"><div><small>客户</small><b>{{ selected.customer_name }}</b></div><div><small>签约方式</small><b>{{ selected.signing_mode==='intermediary'?'中间人签约':'直接签约' }}</b></div><div><small>中间人</small><b>{{ selected.intermediary_name || '—' }}</b></div><div><small>分成比例</small><b>{{ selected.intermediary_share_percent ?? '—' }}{{ selected.intermediary_share_percent!=null?'%':'' }}</b></div><div><small>金额</small><b>{{ selected.currency }} {{ selected.amount }}</b></div><div><small>期限</small><b>{{ selected.starts_on || '—' }} 至 {{ selected.ends_on || '—' }}</b></div></div>
          <h4>关联合同项目</h4><div class="bw-mini-list"><div v-for="p in selected.projects || []" :key="p.id"><b>{{ p.project_no }} · {{ p.name }}</b><span>{{ p.relation_type }} · {{ p.is_current?'当前合同':'历史合同' }}</span></div></div>
          <h4>合同附件 / 公示卡</h4><div class="bw-mini-list"><div v-for="a in selected.attachments || []" :key="a.attachment_id"><b>{{ a.title || a.original_name }}</b><span>{{ a.attachment_type }} · {{ a.original_name }}</span><button @click="downloadFile(a.file_id,a.original_name)">下载</button></div></div>
          <div class="bw-attach-row"><select v-model="attachmentForm.attachmentType"><option value="signed_contract">签署合同</option><option value="amendment">合同补充协议</option><option value="maintenance_card">维护公示卡</option><option value="other">其他</option></select><input v-model="attachmentForm.title" placeholder="附件名称（可选）"><label class="bw-upload">上传附件<input type="file" hidden accept=".docx,.pdf,.jpg,.jpeg,.png,.txt" @change="uploadContractAttachment"></label></div>
        </template>
      </section>
    </template>
  </section>
</template>

<style scoped>
.business-workspace{display:grid;gap:15px}.bw-header{display:flex;justify-content:space-between;align-items:center;gap:15px}.bw-header h2{margin:4px 0;font-size:22px;color:#25364f}.bw-header p{margin:0;color:#8995a5;font-size:11px}.bw-primary{border:1px solid #315fca;background:#315fca;color:#fff;border-radius:8px;padding:9px 13px;font-size:11px;cursor:pointer}.bw-secondary{border:1px solid #dce4ef;background:#fff;color:#60738e;border-radius:8px;padding:9px 13px;font-size:11px}.bw-tabs{display:flex;gap:4px;padding:4px;background:#edf1f6;border-radius:10px}.bw-tabs button{border:0;background:transparent;padding:9px 14px;border-radius:7px;color:#6b7c92;font-size:11px;cursor:pointer}.bw-tabs button.active{background:#fff;color:#315fca;box-shadow:0 1px 4px #172b4d12}.bw-panel,.bw-list-panel,.bw-detail-panel{background:#fff;border:1px solid #e5ebf2;border-radius:14px;padding:17px;display:grid;gap:14px}.bw-panel-head,.bw-list-head{display:flex;align-items:center;justify-content:space-between;gap:12px}.bw-panel-head h3,.bw-list-head h3{margin:0;font-size:14px;color:#3b4f69}.bw-panel-head button{border:0;background:none;color:#8a96a6;cursor:pointer}.bw-head-actions{display:flex;gap:10px;align-items:center}.bw-edit-form{padding:14px;background:#f8fafc;border-radius:10px}.bw-list-head small{display:block;color:#96a1af;font-size:10px;margin-top:4px}.bw-search{display:flex;gap:7px}.bw-search input{border:1px solid #e0e6ee;border-radius:8px;padding:8px 10px;font-size:11px}.bw-search button{border:1px solid #dfe6ef;background:#fff;border-radius:8px;padding:8px 12px;font-size:11px;color:#61748f}.bw-form{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:11px}.bw-form label{display:grid;gap:6px;color:#64758b;font-size:11px}.bw-form label.wide{grid-column:1/-1}.bw-form input,.bw-form select,.bw-form textarea,.bw-attach-row input,.bw-attach-row select{width:100%;box-sizing:border-box;border:1px solid #dfe6ef;border-radius:8px;padding:9px 10px;font:inherit;color:#3d4f68;background:#fff}.bw-form-actions{grid-column:1/-1;display:flex;gap:8px}.bw-form.compact{margin-top:12px}.bw-table-wrap{overflow:auto}.bw-table-wrap table{width:100%;border-collapse:collapse}.bw-table-wrap th,.bw-table-wrap td{padding:11px 9px;border-bottom:1px solid #edf0f4;text-align:left;font-size:11px;color:#5a6d85;white-space:nowrap}.bw-table-wrap th{color:#8d99a9;font-weight:600}.bw-table-wrap td b,.bw-table-wrap td small{display:block}.bw-table-wrap td small{font-size:10px;color:#96a1af;margin-top:4px}.bw-link{border:0;background:none;color:#315fca;cursor:pointer;font-size:11px}.bw-cards{display:grid;grid-template-columns:repeat(auto-fill,minmax(245px,1fr));gap:11px}.bw-project-card{border:1px solid #e7edf4;border-radius:11px;padding:14px;display:grid;gap:13px;cursor:pointer;transition:.15s}.bw-project-card:hover{border-color:#b8c9e7;box-shadow:0 4px 14px #1b33500d}.bw-project-card>div:first-child{display:grid;gap:5px}.bw-project-card span{font-size:10px;color:#6680b2}.bw-project-card b{font-size:13px;color:#354a66}.bw-project-card small{font-size:10px;color:#96a1af}.bw-card-meta,.bw-card-foot{display:flex;justify-content:space-between;gap:8px;font-size:10px;color:#8592a4}.bw-card-foot b{color:#315fca;font-size:10px}.bw-detail-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px}.bw-detail-grid div{display:grid;gap:5px;padding:10px;background:#f7f9fc;border-radius:8px}.bw-detail-grid small{font-size:10px;color:#8d99a9}.bw-detail-grid b{font-size:11px;color:#455b77;overflow-wrap:anywhere}.bw-detail-panel h4{margin:8px 0 0;font-size:12px;color:#4d627d}.bw-mini-list{display:grid;gap:6px}.bw-mini-list>div{display:flex;justify-content:space-between;align-items:center;gap:10px;padding:9px;border-bottom:1px solid #eff2f6;font-size:11px}.bw-mini-list b{color:#465a75}.bw-mini-list span{color:#929eae;font-size:10px}.bw-mini-list button{border:0;background:none;color:#315fca;font-size:10px}.bw-inline-form{display:flex;gap:8px;flex-wrap:wrap}.bw-inline-form input{flex:1;min-width:120px;border:1px solid #dfe6ef;border-radius:7px;padding:8px;font-size:10px}.bw-inline-form label{display:flex;align-items:center;gap:5px;font-size:10px;color:#76859a}.bw-attach-row{display:flex;gap:8px;align-items:center;flex-wrap:wrap}.bw-attach-row select{width:auto}.bw-attach-row input{flex:1;min-width:130px}.bw-upload{display:inline-flex;align-items:center;border:1px solid #dce4ef;border-radius:8px;padding:8px 12px;font-size:11px;color:#526c93;cursor:pointer}.bw-empty{padding:24px;text-align:center;color:#98a3b2;font-size:11px}.bw-message{padding:10px 12px;border-radius:8px;font-size:12px}.bw-message.error{background:#fff0ed;color:#a43e36}.bw-message.success{background:#e8f7ee;color:#2e754e}@media(max-width:720px){.bw-header{align-items:flex-start;flex-direction:column}.bw-form{grid-template-columns:1fr}.bw-form label.wide{grid-column:auto}.bw-detail-grid{grid-template-columns:repeat(2,1fr)}.bw-list-head{align-items:flex-start;flex-direction:column}}
</style>
