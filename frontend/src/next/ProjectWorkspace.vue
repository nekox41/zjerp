<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { api, uploadFile } from './api'
import ReportEditor from './ReportEditor.vue'
import PhotoWorkbench from './PhotoWorkbench.vue'

const props = defineProps({
  projectId: { type: [Number, String], required: true },
  permissions: { type: Array, default: () => [] }
})
const emit = defineEmits(['back','changed'])
const tab = ref('overview')
const project = ref(null)
const employees = ref([])
const contracts = ref([])
const scopeUnits = ref([])
const maintenanceVisits = ref([])
const serviceVisits = ref([])
const issues = ref([])
const reports = ref([])
const selectedReportId = ref(null)
const busy = ref(false)
const loading = ref(false)
const error = ref('')
const notice = ref('')
const selectedIssue = ref(null)
const issueHistoryId = ref(null)
const issueEvents = ref([])
const scopeEditId = ref(null)
const resolution = ref('')
const projectForm = ref({})
const scopeForm = ref({ unitName:'', unitType:'building', structureType:'', usageName:'', fireResistanceGrade:'', aboveGroundFloors:'', undergroundFloors:'', buildingHeight:'', totalArea:'', aboveGroundArea:'', undergroundArea:'', locationDescription:'', isSharedFacility:false, serviceScope:'', notes:'' })
const contractLinkForm = ref({ contractId:'', relationType:'initial', isCurrent:false })
const maintenanceForm = ref({ visitedOn:new Date().toISOString().slice(0,10), contractId:'', employeeIds:[], summary:'' })
const serviceForm = ref({ visitNo:'', visitType:'onsite', visitedAt:new Date().toISOString().slice(0,16), employeeIds:[], summary:'', followUp:'' })
const issueForm = ref({ title:'', description:'', severity:'medium', ownerEmployeeId:'', dueOn:'' })
const reportForm = ref({ reportNo:'', title:'', reportType:'inspection', summary:'', scope:'', methods:'', findings:'', conclusion:'' })
const can = p => props.permissions.includes(p)
const engineerEmployees = computed(() => employees.value.filter(e => e.employee_type === 'engineer' && e.status === 'active'))
const selectedReport = computed(() => reports.value.find(r => String(r.id) === String(selectedReportId.value)) || null)
const visibleTabs = computed(() => [
  {id:'overview',label:'项目概览',permission:'project:read'},
  {id:'contracts',label:'合同关系',permission:'contract:read'},
  {id:'scope',label:'检测范围',permission:'project:scope:read'},
  {id:'maintenance',label:'维护保养',permission:'maintenance:read'},
  {id:'service',label:'现场服务',permission:'service:read'},
  {id:'issues',label:'问题整改',permission:'project:read'},
  {id:'reports',label:'报告管理',permission:'report:read'},
  {id:'photos',label:'照片整理',permission:'photo:read'}
].filter(x => can(x.permission)))

async function loadAll() {
  loading.value = true; error.value = ''
  try {
    const id = props.projectId
    const safe = promise => promise.catch(() => [])
    const [p,e,c,pc,s,m,v,i,r] = await Promise.all([
      api('/business/projects/' + id),
      safe(api('/business/employees')),
      safe(api('/business/contracts')),
      safe(api('/business/projects/' + id + '/contracts')),
      safe(api('/business/projects/' + id + '/scope-units')),
      safe(api('/business/projects/' + id + '/maintenance-visits')),
      safe(api('/business/projects/' + id + '/service-visits')),
      safe(api('/business/projects/' + id + '/issues')),
      safe(api('/business/projects/' + id + '/reports'))
    ])
    project.value = { ...p, contracts:pc, contract_no:pc.find(x=>x.is_current)?.contract_no || null }; employees.value = e; contracts.value = c; scopeUnits.value = s
    maintenanceVisits.value = m; serviceVisits.value = v; issues.value = i; reports.value = r
    projectForm.value = {
      name:p.name, description:p.description || '', managerEmployeeId:p.manager_employee_id || '',
      technicalLeadEmployeeId:p.technical_lead_employee_id || '', actualStart:p.actual_start || '',
      actualEnd:p.actual_end || '', status:p.status
    }
    contractLinkForm.value = { contractId:'', relationType:'initial', isCurrent:false }
    if(selectedReportId.value && !r.some(x => String(x.id) === String(selectedReportId.value))) selectedReportId.value = null
  } catch(e) { error.value = e.message }
  finally { loading.value = false }
}
async function run(action, fn) {
  busy.value = true; error.value = ''; notice.value = ''
  try { await fn(); notice.value = action; await loadAll(); emit('changed') }
  catch(e) { error.value = e.message }
  finally { busy.value = false }
}
async function saveProject() {
  await run('项目资料已保存。', async () => {
    await api('/business/projects/' + props.projectId, {
      method:'PUT',
      body:JSON.stringify({
        name:projectForm.value.name, description:projectForm.value.description,
        ...(String(projectForm.value.managerEmployeeId || '') !== String(project.value.manager_employee_id || '') ? {managerEmployeeId:projectForm.value.managerEmployeeId || null} : {}),
        ...(String(projectForm.value.technicalLeadEmployeeId || '') !== String(project.value.technical_lead_employee_id || '') ? {technicalLeadEmployeeId:projectForm.value.technicalLeadEmployeeId || null} : {}),
        actualStart:projectForm.value.actualStart, actualEnd:projectForm.value.actualEnd,
        status:projectForm.value.status, log:'项目基本信息更新'
      })
    })
  })
}
async function saveMaintenance() {
  await run('维护保养状态已更新。', async () => {
    await api('/business/projects/' + props.projectId + '/maintenance', {
      method:'PUT',
      body:JSON.stringify({
        honeybeeUploaded:!!project.value.honeybee_uploaded,
        documentsReady:!!project.value.documents_ready,
        maintenanceCardSent:!!project.value.maintenance_card_sent
      })
    })
  })
}
async function linkContract() {
  if(!contractLinkForm.value.contractId) return
  await run('合同关联已保存。', async () => {
    await api('/business/projects/' + props.projectId + '/contracts', {
      method:'POST', body:JSON.stringify({
        contractId:Number(contractLinkForm.value.contractId),
        relationType:contractLinkForm.value.relationType,
        isCurrent:contractLinkForm.value.isCurrent
      })
    })
  })
}
function resetScopeForm() {
  scopeEditId.value=null
  scopeForm.value = { unitName:'', unitType:'building', structureType:'', usageName:'', fireResistanceGrade:'', aboveGroundFloors:'', undergroundFloors:'', buildingHeight:'', totalArea:'', aboveGroundArea:'', undergroundArea:'', locationDescription:'', isSharedFacility:false, serviceScope:'', notes:'' }
}
function editScope(unit) {
  scopeEditId.value=unit.id
  scopeForm.value={unitName:unit.unit_name,unitType:unit.unit_type,structureType:unit.structure_type || '',usageName:unit.usage_name || '',
    fireResistanceGrade:unit.fire_resistance_grade || '',aboveGroundFloors:unit.above_ground_floors || '',
    undergroundFloors:unit.underground_floors || '',buildingHeight:unit.building_height || '',totalArea:unit.total_area || '',
    aboveGroundArea:unit.above_ground_area || '',undergroundArea:unit.underground_area || '',
    locationDescription:unit.location_description || '',isSharedFacility:!!unit.is_shared_facility,
    serviceScope:unit.service_scope || '',notes:unit.notes || ''}
}
async function createScope() {
  await run(scopeEditId.value?'检测范围已更新。':'检测范围已添加。', async () => {
    const body={
      ...scopeForm.value,
      buildingHeight:scopeForm.value.buildingHeight || null,
      totalArea:scopeForm.value.totalArea || null,
      aboveGroundArea:scopeForm.value.aboveGroundArea || null,
      undergroundArea:scopeForm.value.undergroundArea || null
    }
    if(scopeEditId.value) await api('/business/scope-units/'+scopeEditId.value,{method:'PUT',body:JSON.stringify(body)})
    else await api('/business/projects/' + props.projectId + '/scope-units', {method:'POST',body:JSON.stringify(body)})
    resetScopeForm()
  })
}
async function removeScope(id) {
  if(!confirm('确定删除这个检测区域吗？')) return
  await run('检测范围已删除。', () => api('/business/scope-units/' + id, {method:'DELETE'}))
  if(scopeEditId.value===id) resetScopeForm()
}
async function showIssueHistory(issue) {
  issueHistoryId.value=issue.id
  try { issueEvents.value=await api('/business/issues/'+issue.id+'/events') }
  catch(e) { error.value=e.message }
}
async function uploadMaintenanceCard(event) {
  const file=event.target.files?.[0]; event.target.value=''
  if(!file) return
  if(!project.value?.contract_id) { error.value='请先为项目设置当前合同'; return }
  busy.value=true; error.value=''
  try {
    const saved=await uploadFile(file)
    await api('/business/contracts/'+project.value.contract_id+'/attachments',{
      method:'POST',body:JSON.stringify({fileId:saved.id,attachmentType:'maintenance_card',title:file.name})
    })
    notice.value='维护公示卡文件已归档到当前合同。'
  } catch(e) { error.value=e.message }
  finally { busy.value=false }
}
async function createMaintenanceVisit() {
  await run('维保现场记录已保存。', async () => {
    await api('/business/projects/' + props.projectId + '/maintenance-visits', {
      method:'POST', body:JSON.stringify({
        ...maintenanceForm.value, contractId:maintenanceForm.value.contractId || null,
        employeeIds:maintenanceForm.value.employeeIds.map(Number)
      })
    })
    maintenanceForm.value = { visitedOn:new Date().toISOString().slice(0,10), contractId:'', employeeIds:[], summary:'' }
  })
}
async function createServiceVisit() {
  await run('现场服务记录已保存。', async () => {
    const visitedAt=serviceForm.value.visitedAt.length===16?serviceForm.value.visitedAt+':00':serviceForm.value.visitedAt
    await api('/business/projects/' + props.projectId + '/service-visits', {
      method:'POST', body:JSON.stringify({
        ...serviceForm.value, visitedAt, employeeIds:serviceForm.value.employeeIds.map(Number)
      })
    })
    serviceForm.value = { visitNo:'', visitType:'onsite', visitedAt:new Date().toISOString().slice(0,16), employeeIds:[], summary:'', followUp:'' }
  })
}
async function createIssue() {
  await run('问题清单已建立。', async () => {
    await api('/business/projects/' + props.projectId + '/issues', {
      method:'POST', body:JSON.stringify({...issueForm.value,ownerEmployeeId:issueForm.value.ownerEmployeeId || null,dueOn:issueForm.value.dueOn || null})
    })
    issueForm.value = { title:'', description:'', severity:'medium', ownerEmployeeId:'', dueOn:'' }
  })
}
async function changeIssue(issue,status) {
  if(status==='resolved' && !resolution.value.trim()) { error.value='请先填写整改结果'; return }
  await run('整改状态已更新。', async () => {
    await api('/business/issues/' + issue.id, {
      method:'PUT', body:JSON.stringify({status,resolution:status==='resolved'?resolution.value:undefined,note:status==='in_progress'?'开始整改':undefined})
    })
    selectedIssue.value = null; resolution.value = ''
  })
}
async function createReport() {
  await run('报告草稿已创建。', async () => {
    const result = await api('/business/projects/' + props.projectId + '/reports', {
      method:'POST', body:JSON.stringify({
        reportNo:reportForm.value.reportNo,title:reportForm.value.title,reportType:reportForm.value.reportType,
        content:{summary:reportForm.value.summary,scope:reportForm.value.scope,methods:reportForm.value.methods,findings:reportForm.value.findings,conclusion:reportForm.value.conclusion}
      })
    })
    selectedReportId.value = result.id
    reportForm.value = { reportNo:'', title:'', reportType:'inspection', summary:'', scope:'', methods:'', findings:'', conclusion:'' }
  })
}
function openPhotoBatch(payload) {
  if(payload?.reportId) selectedReportId.value = payload.reportId
  tab.value = 'photos'
}
watch(() => props.projectId, loadAll)
onMounted(loadAll)
</script>

<template>
  <section class="project-workspace">
    <header class="pw-project-head">
      <button type="button" class="pw-back" @click="$emit('back')">← 返回项目列表</button>
      <div v-if="project" class="pw-project-title">
        <div><span class="eyebrow">PROJECT / {{ project.project_no }}</span><h2>{{ project.name }}</h2><p>{{ project.customer_name }} · {{ project.status }}</p></div>
        <span class="pw-current-contract">{{ project.contract_id ? (project.contract_no || '已关联合同') : '尚未关联当前合同' }}</span>
      </div>
    </header>
    <p v-if="error" class="pw-message error" role="alert">{{ error }}</p>
    <p v-if="notice" class="pw-message success" role="status">{{ notice }}</p>
    <div v-if="loading" class="pw-empty">正在加载项目资料…</div>
    <template v-else-if="project">
      <nav class="pw-tabs">
        <button v-for="item in visibleTabs" :key="item.id" :class="{active:tab===item.id}" @click="tab=item.id">{{ item.label }}</button>
      </nav>

      <section v-if="tab==='overview'" class="pw-panel">
        <div class="pw-panel-title"><div><h3>项目基本信息</h3><p>项目可以先建立，再关联一个或多个合同。</p></div><button v-if="can('project:write')" class="pw-primary" :disabled="busy" @click="saveProject">保存项目</button></div>
        <div class="pw-form">
          <label>项目名称<input v-model="projectForm.name"></label>
          <label>项目状态<select v-model="projectForm.status"><option value="planned">计划中</option><option value="in_progress">进行中</option><option value="paused">暂停</option><option value="completed">已完成</option><option value="cancelled">已取消</option></select></label>
          <label>项目负责人（工程师）<select v-model="projectForm.managerEmployeeId"><option value="">暂不指定</option><option v-for="e in engineerEmployees" :key="e.id" :value="e.id">{{ e.full_name }} · {{ e.employee_code }}</option></select></label>
          <label>技术负责人（工程师）<select v-model="projectForm.technicalLeadEmployeeId"><option value="">暂不指定</option><option v-for="e in engineerEmployees" :key="e.id" :value="e.id">{{ e.full_name }} · {{ e.employee_code }}</option></select></label>
          <label>实际开始日期<input v-model="projectForm.actualStart" type="date"></label>
          <label>实际结束日期<input v-model="projectForm.actualEnd" type="date"></label>
          <label class="wide">项目描述<textarea v-model="projectForm.description" rows="3"></textarea></label>
        </div>
        <p class="pw-hint">项目负责人和技术负责人必须是不同的在职工程师，且各自具有有效的工程师资格记录。</p>
      </section>

      <section v-else-if="tab==='contracts'" class="pw-panel">
        <div class="pw-panel-title"><div><h3>合同关系</h3><p>一个项目可以关联多个合同；续签合同可设置为当前合同。</p></div></div>
        <form class="pw-inline-form" @submit.prevent="linkContract">
          <label>选择合同<select v-model="contractLinkForm.contractId" required><option value="">请选择合同</option><option v-for="c in contracts.filter(c=>c.customer_id===project.customer_id)" :key="c.id" :value="c.id">{{ c.contract_no }} · {{ c.title }}</option></select></label>
          <label>关系类型<select v-model="contractLinkForm.relationType"><option value="initial">初始合同</option><option value="renewal">续签合同</option><option value="supplemental">补充合同</option></select></label>
          <label class="check-label"><input type="checkbox" v-model="contractLinkForm.isCurrent"> 设为当前合同</label>
          <button class="pw-primary" :disabled="busy || !can('project:write')">关联合同</button>
        </form>
        <div class="pw-table-wrap"><table><thead><tr><th>合同</th><th>客户</th><th>关系</th><th>期限</th><th>状态</th><th>当前</th></tr></thead><tbody>
          <tr v-for="c in project.contracts || []" :key="c.contract_id"><td><b>{{ c.contract_no }}</b><small>{{ c.title }}</small></td><td>{{ project.customer_name }}</td><td>{{ ({initial:'初始合同',renewal:'续签合同',supplemental:'补充合同'})[c.relation_type] }}</td><td>{{ c.starts_on || '—' }} 至 {{ c.ends_on || '—' }}</td><td>{{ c.status }}</td><td>{{ c.is_current ? '当前合同' : '历史合同' }}</td></tr>
        </tbody></table><p v-if="!project.contracts?.length" class="pw-empty">项目尚未关联合同</p></div>
      </section>

      <section v-else-if="tab==='scope'" class="pw-panel">
        <div class="pw-panel-title"><div><h3>检测范围与单体</h3><p>支持多栋楼、地下车库分期及跨楼栋共用设施。</p></div></div>
        <form class="pw-form" @submit.prevent="createScope">
          <label>单体/区域名称<input v-model="scopeForm.unitName" required placeholder="例如：9#楼、地下车库一期"></label>
          <label>类型<select v-model="scopeForm.unitType"><option value="building">建筑单体</option><option value="parking_phase">地下车库分期</option><option value="shared_facility">共用设施</option><option value="other">其他区域</option></select></label>
          <label>结构类型<input v-model="scopeForm.structureType"></label><label>使用性质<input v-model="scopeForm.usageName"></label>
          <label>耐火等级<input v-model="scopeForm.fireResistanceGrade"></label><label>地上层数<input v-model="scopeForm.aboveGroundFloors"></label>
          <label>地下层数<input v-model="scopeForm.undergroundFloors"></label><label>建筑高度（米）<input v-model="scopeForm.buildingHeight" type="number" min="0" step="0.01"></label>
          <label>总建筑面积（㎡）<input v-model="scopeForm.totalArea" type="number" min="0" step="0.01"></label>
          <label>地上建筑面积（㎡）<input v-model="scopeForm.aboveGroundArea" type="number" min="0" step="0.01"></label>
          <label>地下建筑面积（㎡）<input v-model="scopeForm.undergroundArea" type="number" min="0" step="0.01"></label>
          <label>位置描述<input v-model="scopeForm.locationDescription"></label>
          <label class="check-label"><input type="checkbox" v-model="scopeForm.isSharedFacility"> 属于跨单体共用设施</label>
          <label class="wide">服务范围/共用说明<textarea v-model="scopeForm.serviceScope" rows="2"></textarea></label>
          <button class="pw-primary" :disabled="busy || !can('project:scope:write')">{{ scopeEditId?'保存检测范围':'添加检测范围' }}</button><button v-if="scopeEditId" type="button" class="pw-secondary" @click="resetScopeForm">取消编辑</button>
        </form>
        <div class="pw-scope-grid">
          <article v-for="unit in scopeUnits" :key="unit.id" class="pw-scope-card">
            <div><span>{{ ({building:'建筑单体',parking_phase:'车库分期',shared_facility:'共用设施',other:'其他'})[unit.unit_type] }}</span><b>{{ unit.unit_name }}</b></div>
            <p>{{ unit.structure_type || '结构未填写' }} · {{ unit.usage_name || '用途未填写' }}</p>
            <p>总面积 {{ unit.total_area || '—' }} ㎡ · 高度 {{ unit.building_height || '—' }} m</p>
            <small>{{ unit.service_scope || unit.location_description || '暂无范围说明' }}</small>
            <div class="pw-scope-actions"><button v-if="can('project:scope:write')" type="button" @click="editScope(unit)">编辑</button><button v-if="can('project:scope:write')" type="button" @click="removeScope(unit.id)">删除</button></div>
          </article>
        </div>
        <p v-if="!scopeUnits.length" class="pw-empty">还没有配置检测范围</p>
      </section>

      <section v-else-if="tab==='maintenance'" class="pw-panel">
        <div class="pw-panel-title"><div><h3>维护保养状态</h3><p>小蜜蜂资料、项目资料和公示卡由工作人员人工确认。</p></div><button class="pw-primary" :disabled="busy || !can('maintenance:write')" @click="saveMaintenance">保存状态</button></div>
        <div class="pw-checks">
          <label><input type="checkbox" v-model="project.honeybee_uploaded"> 小蜜蜂资料已上传</label>
          <label><input type="checkbox" v-model="project.documents_ready"> 项目资料已准备</label>
          <label><input type="checkbox" v-model="project.maintenance_card_sent"> 公示卡已寄送</label>
        </div>
        <div class="pw-panel-title"><div><h3>维护公示卡文件</h3><p>文件归档到项目当前合同；寄送状态由上方人工确认。</p></div><label v-if="project.contract_id && can('contract:write')" class="pw-secondary">上传公示卡<input type="file" hidden accept=".docx,.pdf,.jpg,.jpeg,.png" @change="uploadMaintenanceCard"></label></div>
        <div class="pw-panel-title"><div><h3>维保现场记录</h3><p>仅在确有外地现场服务时记录，不强制按月填报。</p></div></div>
        <form class="pw-form" @submit.prevent="createMaintenanceVisit">
          <label>到场日期<input v-model="maintenanceForm.visitedOn" type="date" required></label>
          <label>关联合同<select v-model="maintenanceForm.contractId"><option value="">不关联具体合同</option><option v-for="c in project.contracts || []" :key="c.contract_id" :value="c.contract_id">{{ c.contract_no }} · {{ c.title }}</option></select></label>
          <label class="wide">参与人员<select v-model="maintenanceForm.employeeIds" multiple><option v-for="e in employees" :key="e.id" :value="e.id">{{ e.full_name }}</option></select></label>
          <label class="wide">现场内容<textarea v-model="maintenanceForm.summary" rows="3"></textarea></label>
          <button class="pw-primary" :disabled="busy || !can('maintenance:write')">保存维保记录</button>
        </form>
        <div class="pw-timeline"><article v-for="visit in maintenanceVisits" :key="visit.id"><time>{{ visit.visited_on }}</time><div><b>{{ visit.employee_names || '未指定人员' }}</b><p>{{ visit.summary || '无现场内容' }}</p><small>{{ visit.contract_no || '项目维保' }}</small></div></article></div>
      </section>

      <section v-else-if="tab==='service'" class="pw-panel">
        <div class="pw-panel-title"><div><h3>技术服务现场记录</h3><p>一个项目可有多次现场服务、远程沟通和整改复检。</p></div></div>
        <form class="pw-form" @submit.prevent="createServiceVisit">
          <label>记录编号<input v-model="serviceForm.visitNo" required></label>
          <label>服务类型<select v-model="serviceForm.visitType"><option value="onsite">现场检测/服务</option><option value="remote">远程服务</option><option value="meeting">会议沟通</option><option value="recheck">整改复检</option></select></label>
          <label>服务时间<input v-model="serviceForm.visitedAt" type="datetime-local" required></label>
          <label class="wide">参与人员<select v-model="serviceForm.employeeIds" multiple required><option v-for="e in employees" :key="e.id" :value="e.id">{{ e.full_name }}</option></select></label>
          <label class="wide">服务内容<textarea v-model="serviceForm.summary" rows="3" required></textarea></label>
          <label class="wide">后续事项<textarea v-model="serviceForm.followUp" rows="2"></textarea></label>
          <button class="pw-primary" :disabled="busy || !can('service:write')">添加现场服务</button>
        </form>
        <div class="pw-timeline"><article v-for="visit in serviceVisits" :key="visit.id"><time>{{ visit.visited_at }}</time><div><b>{{ visit.visit_no }} · {{ ({onsite:'现场服务',remote:'远程服务',meeting:'会议',recheck:'整改复检'})[visit.visit_type] }}</b><p>{{ visit.summary }}</p><small>参与人员：{{ visit.employee_names || '—' }} · {{ visit.follow_up || '无后续事项' }}</small></div></article></div>
      </section>

      <section v-else-if="tab==='issues'" class="pw-panel">
        <div class="pw-panel-title"><div><h3>问题清单与整改闭环</h3><p>记录问题、责任人、期限、整改结果和状态历史。</p></div></div>
        <form class="pw-form" @submit.prevent="createIssue">
          <label>问题标题<input v-model="issueForm.title" required></label>
          <label>严重程度<select v-model="issueForm.severity"><option value="low">低</option><option value="medium">中</option><option value="high">高</option><option value="critical">严重</option></select></label>
          <label>责任人<select v-model="issueForm.ownerEmployeeId"><option value="">待分配</option><option v-for="e in employees" :key="e.id" :value="e.id">{{ e.full_name }}</option></select></label>
          <label>整改期限<input v-model="issueForm.dueOn" type="date"></label>
          <label class="wide">问题描述<textarea v-model="issueForm.description" rows="3"></textarea></label>
          <button class="pw-primary" :disabled="busy || !can('project:write')">添加问题</button>
        </form>
        <div class="pw-issues"><article v-for="issue in issues" :key="issue.id">
          <div class="pw-issue-head"><b>{{ issue.title }}</b><span :class="'sev-'+issue.severity">{{ ({low:'低',medium:'中',high:'高',critical:'严重'})[issue.severity] }}</span><small>{{ ({open:'待处理',in_progress:'整改中',resolved:'已整改',closed:'已关闭'})[issue.status] }}</small></div>
          <p>{{ issue.description || '无详细描述' }}</p><small>责任人：{{ issue.owner_name || '待分配' }} · 期限：{{ issue.due_on || '未设置' }}</small>
          <div class="pw-actions">
            <button type="button" @click="showIssueHistory(issue)">查看状态记录</button>
            <button v-if="issue.status==='open' && can('project:write')" @click="changeIssue(issue,'in_progress')">开始整改</button>
            <button v-if="issue.status==='in_progress' && can('project:write')" @click="selectedIssue=issue; resolution=''">登记整改完成</button>
            <button v-if="issue.status==='resolved' && can('project:write')" @click="changeIssue(issue,'closed')">关闭问题</button>
          </div>
          <div v-if="issueHistoryId===issue.id" class="pw-history"><b>状态变更记录</b><div v-for="event in issueEvents" :key="event.id"><span>{{ event.from_status || '新建' }} → {{ event.to_status }}</span><small>{{ event.actor_name }} · {{ event.created_at }}</small><p>{{ event.note || '无备注' }}</p></div></div>
          <div v-if="selectedIssue?.id===issue.id" class="pw-resolution"><textarea v-model="resolution" rows="2" placeholder="填写整改措施及复检结果"></textarea><button class="pw-primary" @click="changeIssue(issue,'resolved')">确认完成</button><button @click="selectedIssue=null">取消</button></div>
        </article></div>
      </section>

      <section v-else-if="tab==='reports'" class="pw-panel">
        <ReportEditor v-if="selectedReportId" :report-id="selectedReportId" :permissions="permissions" @close="selectedReportId=null" @open-photos="openPhotoBatch" @changed="loadAll"/>
        <template v-else>
          <div class="pw-panel-title"><div><h3>技术服务报告</h3><p>报告直接归属项目；草稿、审核、签发、归档和修订版均保留版本。</p></div></div>
          <form class="pw-form" @submit.prevent="createReport">
            <label>报告编号<input v-model="reportForm.reportNo" required></label>
            <label>报告标题<input v-model="reportForm.title" required></label>
            <label>报告类型<select v-model="reportForm.reportType"><option value="inspection">检测报告</option><option value="service">技术服务报告</option><option value="maintenance">维护保养报告</option></select></label>
            <label>检测范围<input v-model="reportForm.scope"></label>
            <label class="wide">报告摘要<textarea v-model="reportForm.summary" rows="2"></textarea></label>
            <label>检测方法<textarea v-model="reportForm.methods" rows="2"></textarea></label>
            <label>主要发现<textarea v-model="reportForm.findings" rows="2"></textarea></label>
            <label class="wide">服务结论<textarea v-model="reportForm.conclusion" rows="2"></textarea></label>
            <button class="pw-primary" :disabled="busy || !can('report:write')">创建报告草稿</button>
          </form>
          <div class="pw-report-list"><article v-for="r in reports" :key="r.id" @click="selectedReportId=r.id"><div><b>{{ r.report_no }} · {{ r.title }}</b><small>修订版 {{ r.revision_no }} / 版本 {{ r.current_version_no }}</small></div><span>{{ ({draft:'草稿',submitted:'待审核',reviewing:'审核中',returned:'已退回',approved:'审核通过',issued:'已签发',archived:'已归档',withdrawn:'已撤回'})[r.status] }}</span><button type="button">打开 →</button></article></div>
        </template>
      </section>

      <section v-else-if="tab==='photos'" class="pw-panel pw-photo-panel">
        <PhotoWorkbench :project-id="projectId" :report-id="selectedReportId" :permissions="permissions" />
      </section>
    </template>
  </section>
</template>

<style scoped>
.project-workspace{display:grid;gap:15px}.pw-project-head{display:grid;gap:8px}.pw-back{border:0;background:none;color:#5d7699;justify-self:start;font-size:12px;cursor:pointer}.pw-project-title{display:flex;align-items:center;justify-content:space-between;gap:12px}.pw-project-title h2{margin:4px 0;font-size:23px;color:#23354f}.pw-project-title p{margin:0;color:#8a96a7;font-size:11px}.pw-current-contract{font-size:11px;color:#5772a2;background:#eef3fb;padding:9px 12px;border-radius:8px}.pw-tabs{display:flex;gap:4px;overflow:auto;padding:4px;background:#edf1f6;border-radius:10px}.pw-tabs button{border:0;background:transparent;padding:9px 12px;border-radius:7px;white-space:nowrap;font-size:11px;color:#697a91;cursor:pointer}.pw-tabs button.active{background:#fff;color:#315fca;box-shadow:0 1px 4px #172b4d12}.pw-panel{background:#fff;border:1px solid #e5ebf2;border-radius:14px;padding:18px;display:grid;gap:16px}.pw-panel-title{display:flex;justify-content:space-between;align-items:center;gap:12px}.pw-panel-title h3{margin:0;font-size:15px;color:#2d405b}.pw-panel-title p{margin:5px 0 0;font-size:11px;color:#8c98a8}.pw-primary{border:1px solid #315fca;background:#315fca;color:#fff;border-radius:8px;padding:9px 13px;font-size:11px;cursor:pointer}.pw-primary:disabled{opacity:.5;cursor:not-allowed}.pw-form{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.pw-form label,.pw-inline-form label{display:grid;gap:6px;font-size:11px;color:#64758c}.pw-form input,.pw-form select,.pw-form textarea,.pw-inline-form input,.pw-inline-form select,.pw-resolution textarea{width:100%;min-width:0;box-sizing:border-box;padding:9px 10px;border:1px solid #dfe6ef;border-radius:8px;background:#fff;color:#3d4e65;font:inherit}.pw-form label.wide{grid-column:1/-1}.pw-hint{margin:0;color:#8b96a7;font-size:10px}.pw-inline-form{display:grid;grid-template-columns:2fr 1fr auto auto;align-items:end;gap:10px}.check-label{display:flex!important;align-items:center;gap:8px}.check-label input{width:auto}.pw-table-wrap{overflow:auto}.pw-table-wrap table{width:100%;border-collapse:collapse}.pw-table-wrap th,.pw-table-wrap td{padding:10px;border-bottom:1px solid #edf0f4;text-align:left;font-size:11px;color:#5b6d85}.pw-table-wrap th{color:#8995a6;font-weight:600}.pw-table-wrap td small{display:block;color:#98a2b1;margin-top:4px}.pw-scope-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(230px,1fr));gap:10px}.pw-scope-card{border:1px solid #e8edf3;border-radius:10px;padding:13px;display:grid;gap:7px}.pw-scope-card div{display:grid;gap:4px}.pw-scope-card span{font-size:10px;color:#6681b3}.pw-scope-card b{font-size:13px;color:#354962}.pw-scope-card p,.pw-scope-card small{margin:0;font-size:10px;color:#8996a7}.pw-scope-actions{display:flex;justify-content:flex-end;gap:8px}.pw-scope-actions button{border:0;background:none;color:#5277ad;font-size:10px;cursor:pointer}.pw-scope-actions button:last-child{color:#b45a5a}.pw-history{padding:10px;background:#f6f8fb;border-radius:8px;display:grid;gap:7px}.pw-history>b{font-size:11px;color:#526781}.pw-history>div{border-top:1px solid #e5eaf1;padding-top:7px;display:grid;gap:4px}.pw-history span{font-size:10px;color:#5477ac}.pw-history small,.pw-history p{font-size:10px;color:#8a96a7;margin:0}.pw-checks{display:flex;gap:20px;flex-wrap:wrap}.pw-checks label{display:flex;align-items:center;gap:8px;font-size:12px;color:#536780}.pw-timeline{display:grid;gap:0}.pw-timeline article{display:grid;grid-template-columns:150px 1fr;gap:14px;padding:12px 0;border-top:1px solid #edf0f4}.pw-timeline time{font-size:11px;color:#6681a9}.pw-timeline b{font-size:12px;color:#425671}.pw-timeline p{font-size:11px;color:#697a90;margin:5px 0}.pw-timeline small{font-size:10px;color:#96a1af}.pw-issues{display:grid;gap:10px}.pw-issues article{border:1px solid #e8edf3;border-radius:10px;padding:13px;display:grid;gap:8px}.pw-issue-head{display:flex;align-items:center;gap:10px}.pw-issue-head b{font-size:12px;color:#40536e}.pw-issue-head span{font-size:10px;padding:4px 7px;border-radius:12px;background:#f1f3f6;color:#65748a}.pw-issue-head .sev-critical,.pw-issue-head .sev-high{background:#fff0ec;color:#b44e43}.pw-issue-head small{margin-left:auto;color:#8c98a8;font-size:10px}.pw-issues p{font-size:11px;color:#687b94;margin:0}.pw-issues article>small{font-size:10px;color:#96a1af}.pw-actions{display:flex;gap:8px}.pw-actions button,.pw-resolution button:not(.pw-primary){border:1px solid #dfe6ef;background:#fff;border-radius:7px;padding:6px 10px;color:#5e718d;font-size:10px}.pw-resolution{display:flex;gap:8px;align-items:flex-start}.pw-resolution textarea{flex:1}.pw-report-list{display:grid;gap:8px}.pw-report-list article{display:flex;align-items:center;gap:12px;padding:12px;border:1px solid #e8edf3;border-radius:9px;cursor:pointer}.pw-report-list article>div{flex:1;display:grid;gap:5px}.pw-report-list b{font-size:12px;color:#40536e}.pw-report-list small{font-size:10px;color:#929eae}.pw-report-list span{font-size:10px;color:#5471a0}.pw-report-list button{border:0;background:none;color:#315fca;font-size:11px}.pw-empty{padding:20px;text-align:center;color:#98a3b2;font-size:11px}.pw-message{padding:10px 12px;border-radius:8px;font-size:12px}.pw-message.error{background:#fff0ed;color:#a43e36}.pw-message.success{background:#e8f7ee;color:#2e754e}.pw-photo-panel{padding:14px}@media(max-width:750px){.pw-form{grid-template-columns:1fr}.pw-form label.wide{grid-column:auto}.pw-inline-form{grid-template-columns:1fr}.pw-project-title{align-items:flex-start;flex-direction:column}.pw-timeline article{grid-template-columns:1fr;gap:6px}.pw-resolution{flex-direction:column}}
</style>
