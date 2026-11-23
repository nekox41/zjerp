<script setup>
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { api, downloadFile } from './api'

const props = defineProps({ permissions: { type: Array, default: () => [] }, userId: { type: Number, default: 0 } })
const can = p => props.permissions.includes(p)
const visibleTabs = computed(()=>[
  {id:'visits',name:'现场服务',permission:'service:visit:read'},
  {id:'issues',name:'问题清单',permission:'project:read'},
  {id:'photos',name:'照片批次',permission:'report:photo:read'},
  {id:'reports',name:'报告与交付',permission:'report:read'}
].filter(x=>can(x.permission)))
const projects = ref([]), packages = ref([]), employees = ref([]), issues = ref([])
const visits = ref([]), batches = ref([]), reports = ref([]), deliveries = ref([])
const project = ref(null), workPackage = ref(null), batch = ref(null), report = ref(null)
const systems = ref([]), templateFile = ref(null), selectedBatches = ref([])
const loading = ref(false), busy = ref(false), error = ref(''), notice = ref('')
const tab = ref('visits')
const visitForm = ref({visitDate:new Date().toISOString().slice(0,10),visitType:'inspection',title:'现场检测',summary:'',resultSummary:'',staff:[]})
const issueForm = ref({title:'',description:'',severity:'medium',ownerEmployeeId:'',dueOn:''})
const batchForm = ref({title:'现场照片',visitDate:new Date().toISOString().slice(0,10),visitId:''})
const reportForm = ref({reportNo:'',title:'',reportType:'inspection',summary:'',scope:'',methods:'',findings:'',conclusion:''})
const deliveryForm = ref({deliveryMethod:'courier',recipientName:'',recipientPhone:'',recipientOrganization:'',recipientAddress:'',courierName:'',trackingNo:'',notes:''})
const previewUrls = ref({})
const photoDrafts = ref({})
const batchReviewReason = ref('')

async function load() {
  loading.value=true; error.value=''
  try {
    const [p,e,s] = await Promise.all([
      api('/service-workflow/projects'),
      can('employee:read')?api('/business/employees'):Promise.resolve([]),
      can('report:photo:read')?api('/service-workflow/photo-systems'):Promise.resolve([])
    ])
    projects.value=p; employees.value=e; systems.value=s
    if(project.value) await selectProject(project.value.id)
  } catch(e){error.value=e.message} finally{loading.value=false}
}
async function selectProject(id) {
  error.value=''; project.value=projects.value.find(x=>x.id===id)||null
  workPackage.value=null; batch.value=null; report.value=null
  if(!id){packages.value=[];return}
  try { packages.value=await api('/service-workflow/projects/'+id+'/work-packages') }
  catch(e){error.value=e.message}
}
async function selectPackage(id) {
  workPackage.value=packages.value.find(x=>x.id===id)||null
  batch.value=null; report.value=null; selectedBatches.value=[]
  if(!id)return
  try {
    const [v,b,r,i]=await Promise.all([
      can('service:visit:read')?api('/service-workflow/work-packages/'+id+'/visits'):Promise.resolve([]),
      can('report:photo:read')?api('/service-workflow/work-packages/'+id+'/photo-batches'):Promise.resolve([]),
      can('report:read')?api('/business/work-packages/'+id+'/reports'):Promise.resolve([]),
      can('project:read')?api('/business/projects/'+project.value.id+'/issues'):Promise.resolve([])
    ])
    visits.value=v; batches.value=b; reports.value=r; issues.value=i
  } catch(e){error.value=e.message}
}
async function createVisit() {
  busy.value=true;error.value='';try{
    await api('/service-workflow/work-packages/'+workPackage.value.id+'/visits',{method:'POST',body:JSON.stringify(visitForm.value)})
    visitForm.value={...visitForm.value,title:'补充现场服务',summary:'',resultSummary:'',staff:[]}
    await selectPackage(workPackage.value.id);notice.value='现场服务记录已保存'
  }catch(e){error.value=e.message}finally{busy.value=false}
}
async function createIssue() {
  busy.value=true;error.value='';try{
    const b={...issueForm.value};if(!b.ownerEmployeeId)b.ownerEmployeeId=null;if(!b.dueOn)b.dueOn=null
    await api('/business/projects/'+project.value.id+'/issues',{method:'POST',body:JSON.stringify(b)})
    issueForm.value={title:'',description:'',severity:'medium',ownerEmployeeId:'',dueOn:''}
    issues.value=await api('/business/projects/'+project.value.id+'/issues');notice.value='问题清单已登记'
  }catch(e){error.value=e.message}finally{busy.value=false}
}
async function linkIssueToVisit(visit) {
  const value=prompt('输入要关联的问题编号（问题清单中的 ID）：')
  if(!value)return
  try{await api('/service-workflow/visits/'+visit.id+'/issues',{method:'POST',body:JSON.stringify({issueId:Number(value),relationType:'reviewed'})});await selectPackage(workPackage.value.id);notice.value='问题已关联到本次现场服务'}
  catch(e){error.value=e.message}
}
async function changeIssue(issue,status) {
  const resolution=status==='resolved'||status==='closed'?prompt('填写整改/复核结果：'):null
  if((status==='resolved'||status==='closed')&&!resolution)return
  try { await api('/business/issues/'+issue.id,{method:'PUT',body:JSON.stringify({status,resolution})});issues.value=await api('/business/projects/'+project.value.id+'/issues') }
  catch(e){error.value=e.message}
}
async function createBatch() {
  busy.value=true;error.value='';try{
    const b=await api('/service-workflow/work-packages/'+workPackage.value.id+'/photo-batches',{method:'POST',body:JSON.stringify({...batchForm.value,visitId:batchForm.value.visitId||null})})
    await selectPackage(workPackage.value.id);await openBatch(b.id);notice.value='照片批次已创建'
  }catch(e){error.value=e.message}finally{busy.value=false}
}
async function openBatch(id) {
  try {
    batch.value=await api('/service-workflow/photo-batches/'+id)
    for(const photo of batch.value.photos){
      photoDrafts.value[photo.id]={systemCode:photo.system_code||'',takenOn:photo.taken_on||batch.value.visit_date,caption:photo.caption||'',confirmed:photo.confirmation_status==='confirmed',sortOrder:photo.sort_order}
      if(!previewUrls.value[photo.file_id]) await loadPreview(photo.file_id)
    }
  }catch(e){error.value=e.message}
}
async function loadPreview(fileId) {
  const token=sessionStorage.getItem('erp-next-token')
  const response=await fetch((import.meta.env.VITE_NEXT_API_BASE||'/next-api')+'/files/'+fileId+'/download',{headers:token?{Authorization:'Bearer '+token}:{}})
  if(!response.ok)throw new Error('照片预览加载失败')
  previewUrls.value[fileId]=URL.createObjectURL(await response.blob())
}
async function importPhotos(event) {
  const files=Array.from(event.target.files||[]);event.target.value=''
  if(!batch.value||batch.value.status!=='collecting')return
  busy.value=true;error.value='';let added=0
  try{
    for(const file of files){
      if(!['image/jpeg','image/png'].includes(file.type)){continue}
      const form=new FormData();form.append('file',file)
      const uploaded=await api('/files',{method:'POST',body:form})
      await api('/service-workflow/photo-batches/'+batch.value.id+'/photos',{method:'POST',body:JSON.stringify({fileId:uploaded.id,takenOn:batch.value.visit_date})})
      await loadPreview(uploaded.id);added++
    }
    const batchId=batch.value.id;await selectPackage(workPackage.value.id);await openBatch(batchId)
    notice.value='已导入 '+added+' 张照片；请逐张确认系统分类和拍摄日期'
  }catch(e){error.value=e.message}finally{busy.value=false}
}
async function savePhoto(photo) {
  const d=photoDrafts.value[photo.id]
  try{
    await api('/service-workflow/photos/'+photo.id,{method:'PUT',body:JSON.stringify({...d,confirmed:d.confirmed})})
    await openBatch(batch.value.id);notice.value='照片信息已保存'
  }catch(e){error.value=e.message}
}
async function movePhoto(photo,delta) {
  const ordered=[...batch.value.photos].sort((a,b)=>a.sort_order-b.sort_order)
  const index=ordered.findIndex(x=>x.id===photo.id),target=index+delta
  if(target<0||target>=ordered.length)return
  const other=ordered[target]
  const a=photoDrafts.value[photo.id],b=photoDrafts.value[other.id]
  const current=a.sortOrder;a.sortOrder=b.sortOrder;b.sortOrder=current
  try {
    await Promise.all([
      api('/service-workflow/photos/'+photo.id,{method:'PUT',body:JSON.stringify({...a,confirmed:a.confirmed})}),
      api('/service-workflow/photos/'+other.id,{method:'PUT',body:JSON.stringify({...b,confirmed:b.confirmed})})
    ])
    await openBatch(batch.value.id)
  } catch(e){error.value=e.message}
}
async function removePhoto(photo) {
  if(!confirm('确定从当前批次移除这张照片？'))return
  try{await api('/service-workflow/photos/'+photo.id,{method:'DELETE'});await openBatch(batch.value.id)}catch(e){error.value=e.message}
}
async function submitBatch() {
  try{const batchId=batch.value.id;await api('/service-workflow/photo-batches/'+batchId+'/submit',{method:'POST'});await selectPackage(workPackage.value.id);await openBatch(batchId);notice.value='照片批次已提交内部审核'}catch(e){error.value=e.message}
}
async function reviewBatch() {
  try{const batchId=batch.value.id;await api('/service-workflow/photo-batches/'+batchId+'/review',{method:'POST'});await selectPackage(workPackage.value.id);await openBatch(batchId);notice.value='照片批次审核通过'}catch(e){error.value=e.message}
}
async function createReport() {
  busy.value=true;error.value='';try{
    const f=reportForm.value
    const r=await api('/business/work-packages/'+workPackage.value.id+'/reports',{method:'POST',body:JSON.stringify({reportNo:f.reportNo,title:f.title,reportType:f.reportType,content:{summary:f.summary,scope:f.scope,methods:f.methods,findings:f.findings,conclusion:f.conclusion}})})
    reportForm.value={reportNo:'',title:'',reportType:'inspection',summary:'',scope:'',methods:'',findings:'',conclusion:''}
    await selectPackage(workPackage.value.id);await openReport(r.id);notice.value='报告草稿已创建'
  }catch(e){error.value=e.message}finally{busy.value=false}
}
async function openReport(id) {
  try{report.value=await api('/business/reports/'+id);const x=report.value.content||{};reportForm.value={reportNo:report.value.report_no,title:report.value.title,reportType:report.value.report_type,summary:x.summary||'',scope:x.scope||'',methods:x.methods||'',findings:x.findings||'',conclusion:x.conclusion||''};deliveries.value=await api('/service-workflow/reports/'+id+'/deliveries').catch(()=>[])}
  catch(e){error.value=e.message}
}
async function saveReport() {
  if(!report.value)return;busy.value=true
  try{const f=reportForm.value;report.value=await api('/business/reports/'+report.value.id,{method:'PUT',body:JSON.stringify({expectedVersionNo:report.value.current_version_no,title:f.title,content:{summary:f.summary,scope:f.scope,methods:f.methods,findings:f.findings,conclusion:f.conclusion}})});await openReport(report.value.id);notice.value='报告内容已保存为新版本'}
  catch(e){error.value=e.message}finally{busy.value=false}
}
async function reportAction(action) {
  if(!report.value)return
  const body=action==='return'||action==='withdraw'?{reason:prompt('请填写原因：')||''}:{}
  if(['return','withdraw'].includes(action)&&!body.reason)return
  try{const reportId=report.value.id;await api('/business/reports/'+reportId+'/'+action,{method:'POST',body:JSON.stringify(body)});await selectPackage(workPackage.value.id);await openReport(reportId);notice.value='报告状态已更新'}
  catch(e){error.value=e.message}
}
async function chooseTemplate(event) {
  const file=event.target.files?.[0];event.target.value='';if(!file)return
  try{const form=new FormData();form.append('file',file);const uploaded=await api('/files',{method:'POST',body:form});templateFile.value=uploaded;notice.value='报告模板已上传'}
  catch(e){error.value=e.message}
}
async function generateDocx() {
  if(!report.value||!templateFile.value||!selectedBatches.value.length){error.value='请先选择 DOCX 模板和已审核照片批次';return}
  try{
    const result=await api('/service-workflow/reports/'+report.value.id+'/generate-docx',{method:'POST',body:JSON.stringify({templateFileId:templateFile.value.id,batchIds:selectedBatches.value,photoMode:photoMode.value})})
    await downloadFile(result.fileId,result.filename);notice.value='DOCX 已生成并归档到报告版本 '+result.versionNo
  }catch(e){error.value=e.message}
}
async function createDelivery() {
  if(!report.value)return
  try{await api('/service-workflow/reports/'+report.value.id+'/deliveries',{method:'POST',body:JSON.stringify({versionNo:report.value.current_version_no,...deliveryForm.value})});deliveries.value=await api('/service-workflow/reports/'+report.value.id+'/deliveries');notice.value='报告交付登记已创建'}
  catch(e){error.value=e.message}
}
async function updateDelivery(delivery,status) {
  const trackingNo= status==='dispatched'?prompt('快递单号（可留空）：',delivery.tracking_no||''):null
  try{await api('/service-workflow/deliveries/'+delivery.id+'/status',{method:'POST',body:JSON.stringify({status,trackingNo})});deliveries.value=await api('/service-workflow/reports/'+report.value.id+'/deliveries')}
  catch(e){error.value=e.message}
}
function batchLabel(status){return ({collecting:'整理中',submitted:'待审核',reviewed:'已审核',locked:'已用于报告'})[status]||status}
function reportLabel(status){return ({draft:'草稿',submitted:'待审核',reviewing:'审核中',returned:'已退回',approved:'审核通过',issued:'已签发',archived:'已归档',withdrawn:'已撤回'})[status]||status}
onMounted(load)
onBeforeUnmount(()=>Object.values(previewUrls.value).forEach(URL.revokeObjectURL))
</script>

<template>
<section class="service-workflow">
  <div class="sw-head"><div><span class="sw-kicker">TECHNICAL SERVICE · DELIVERY</span><h2>技术服务交付</h2><p>现场执行、问题整改、照片整理、报告审核和交付登记，按项目与服务批次留痕。</p></div><button @click="load">刷新</button></div>
  <p v-if="error" class="sw-msg error">{{ error }}</p><p v-if="notice" class="sw-msg success">{{ notice }}</p>
  <div class="sw-selects">
    <label>项目<select :value="project?.id||''" @change="selectProject(Number($event.target.value)||null)"><option value="">选择项目</option><option v-for="p in projects" :key="p.id" :value="p.id">{{ p.project_no }} · {{ p.name }}</option></select></label>
    <label>服务工作包<select :value="workPackage?.id||''" @change="selectPackage(Number($event.target.value)||null)" :disabled="!project"><option value="">选择服务工作包</option><option v-for="w in packages" :key="w.id" :value="w.id">{{ w.package_no }} · {{ w.title }}</option></select></label>
  </div>
  <div v-if="!workPackage" class="sw-empty">选择项目和服务工作包后，管理现场服务、问题、照片与报告。</div>
  <template v-else>
    <nav class="sw-tabs">
      <button v-for="t in visibleTabs" :key="t.id" :class="{active:tab===t.id}" @click="tab=t.id">{{ t.name }}</button>
    </nav>

    <div v-if="tab==='visits'" class="sw-panel">
      <div class="sw-panel-title"><h3>多次现场服务记录</h3><span>{{ visits.length }} 次</span></div>
      <form class="sw-form" @submit.prevent="createVisit">
        <label>到场日期<input v-model="visitForm.visitDate" type="date" required></label>
        <label>服务类型<select v-model="visitForm.visitType"><option value="inspection">首次检测</option><option value="rectification_review">整改复核</option><option value="supplement">补充采集</option><option value="maintenance">维护保养</option><option value="other">其他</option></select></label>
        <label>标题<input v-model="visitForm.title" required></label>
        <label class="wide">执行人员<select v-model="visitForm.staff" multiple><option v-for="e in employees" :key="e.id" :value="{employeeId:e.id,role:e.employee_type==='engineer'?'engineer':'field_operator'}">{{ e.full_name }}（{{ e.employee_type==='engineer'?'工程师':e.employee_type==='operator'?'操作员':'员工' }}）</option></select></label>
        <label class="wide">现场情况<textarea v-model="visitForm.summary" rows="2"></textarea></label>
        <label class="wide">结果/待办<textarea v-model="visitForm.resultSummary" rows="2"></textarea></label>
        <button class="primary" :disabled="busy">保存现场记录</button>
      </form>
      <div class="sw-list">
        <article v-for="v in visits" :key="v.id"><div><b>{{ v.title }}</b><small>{{ v.visit_date }} · {{ ({inspection:'现场检测',rectification_review:'整改复核',supplement:'补充采集',maintenance:'维护保养',other:'其他'})[v.visit_type] }}</small><p>{{ v.summary||'暂无现场记录' }}</p><p>现场人员：{{ v.staff_names||'未登记' }}</p></div><div><span class="sw-pill">{{ v.staff_count }} 位人员 · {{ v.issue_count }} 项问题</span><button @click="linkIssueToVisit(v)">关联问题</button></div></article>
        <div v-if="!visits.length" class="sw-empty small">尚未登记现场服务记录</div>
      </div>
    </div>

    <div v-if="tab==='issues'" class="sw-panel">
      <div class="sw-panel-title"><h3>问题清单与整改跟踪</h3><span>{{ issues.length }} 项</span></div>
      <form class="sw-form" @submit.prevent="createIssue">
        <label>问题标题<input v-model="issueForm.title" required></label>
        <label>严重程度<select v-model="issueForm.severity"><option value="low">低</option><option value="medium">中</option><option value="high">高</option><option value="critical">严重</option></select></label>
        <label>责任人<select v-model="issueForm.ownerEmployeeId"><option value="">待指定</option><option v-for="e in employees" :key="e.id" :value="e.id">{{ e.full_name }}</option></select></label>
        <label>整改期限<input v-model="issueForm.dueOn" type="date"></label>
        <label class="wide">问题描述<textarea v-model="issueForm.description" rows="2"></textarea></label>
        <button class="primary" :disabled="busy">登记问题</button>
      </form>
      <div class="sw-list">
        <article v-for="i in issues" :key="i.id"><div><b>#{{ i.id }} · {{ i.title }}</b><small>{{ i.severity }} · {{ i.owner_name||'未分配' }} · 截止 {{ i.due_on||'未设置' }}</small><p>{{ i.description||'暂无描述' }}</p><p v-if="i.resolution" class="sw-resolution">处理结果：{{ i.resolution }}</p></div><div class="sw-issue-actions"><span class="sw-pill">{{ ({open:'待整改',in_progress:'整改中',resolved:'已解决',closed:'已关闭'})[i.status] }}</span><button v-if="i.status==='open'" @click="changeIssue(i,'in_progress')">开始整改</button><button v-if="i.status==='in_progress'||i.status==='open'" @click="changeIssue(i,'resolved')">提交复核</button><button v-if="i.status==='resolved'" @click="changeIssue(i,'closed')">关闭</button></div></article>
        <div v-if="!issues.length" class="sw-empty small">当前没有问题清单</div>
      </div>
    </div>

    <div v-if="tab==='photos'" class="sw-panel">
      <div class="sw-panel-title"><h3>现场照片批次</h3><span>{{ batches.length }} 批</span></div>
      <div v-if="!batch" class="sw-two">
        <form class="sw-form" @submit.prevent="createBatch">
          <label>批次名称<input v-model="batchForm.title" required></label><label>现场日期<input v-model="batchForm.visitDate" type="date" required></label>
          <label class="wide">关联现场服务<select v-model="batchForm.visitId"><option value="">不关联特定到场记录</option><option v-for="v in visits" :key="v.id" :value="v.id">{{ v.visit_date }} · {{ v.title }}</option></select></label>
          <button class="primary">创建照片批次</button>
        </form>
        <div class="sw-list">
          <article v-for="b in batches" :key="b.id"><div><b>{{ b.title }}</b><small>{{ b.visit_date }} · {{ b.photo_count }} 张照片</small></div><div><span class="sw-pill">{{ batchLabel(b.status) }}</span><button @click="openBatch(b.id)">打开</button></div></article>
          <div v-if="!batches.length" class="sw-empty small">尚未创建照片批次</div>
        </div>
      </div>
      <div v-else class="sw-batch-view">
        <div class="sw-panel-title"><div><button class="back" @click="batch=null">← 批次列表</button><h3>{{ batch.title }}</h3><small>{{ batch.visit_date }} · {{ batch.photos.length }} 张照片</small></div><span class="sw-pill">{{ batchLabel(batch.status) }}</span></div>
        <div v-if="batch.status==='collecting'" class="sw-upload-row"><label class="primary">＋ 批量导入照片<input type="file" hidden multiple accept="image/jpeg,image/png" @change="importPhotos"></label><button @click="photoView=photoView==='cards'?'table':'cards'">{{photoView==='cards'?'表格视图':'卡片视图'}}</button><small>支持 JPG / PNG，导入后需人工确认一级系统分类和拍摄日期。</small></div>
        <div v-if="photoView==='cards'" class="sw-photo-grid">
          <article v-for="p in batch.photos" :key="p.id" class="sw-photo-card">
            <img v-if="previewUrls[p.file_id]" :src="previewUrls[p.file_id]" :alt="p.original_name"><div v-else class="sw-photo-placeholder">加载预览…</div>
            <div class="sw-photo-meta"><b :title="p.original_name">{{ p.original_name }}</b>
              <template v-if="batch.status==='collecting'">
                <label>所属系统<select v-model="photoDrafts[p.id].systemCode"><option value="">请选择一级系统</option><option v-for="s in systems" :key="s" :value="s">{{ s }}</option></select></label>
                <label>拍摄日期<input v-model="photoDrafts[p.id].takenOn" type="date"></label>
                <label>照片说明<input v-model="photoDrafts[p.id].caption" placeholder="可选"></label>
                <label class="confirm"><input v-model="photoDrafts[p.id].confirmed" type="checkbox"> 已人工核对</label>
                <div class="sw-row-actions"><button @click="movePhoto(p,-1)">↑</button><button @click="movePhoto(p,1)">↓</button><button @click="savePhoto(p)">保存</button><button class="danger" @click="removePhoto(p)">移除</button></div>
              </template>
              <template v-else><small>{{ p.system_code||'未分类' }} · {{ p.taken_on||'无日期' }}</small><small>{{ p.confirmation_status==='confirmed'?'人工已确认':'未确认' }}</small></template>
            </div>
          </article>
        </div>
        <div v-else class="sw-photo-table-wrap"><table class="sw-photo-table"><thead><tr><th>预览</th><th>文件名</th><th>一级系统</th><th>拍摄日期</th><th>人工确认</th><th>操作</th></tr></thead><tbody>
          <tr v-for="p in batch.photos" :key="p.id"><td><img v-if="previewUrls[p.file_id]" :src="previewUrls[p.file_id]" :alt="p.original_name"></td><td>{{p.original_name}}</td>
          <td v-if="batch.status==='collecting'"><select v-model="photoDrafts[p.id].systemCode"><option value="">未分类</option><option v-for="x in systems" :key="x" :value="x">{{x}}</option></select></td><td v-else>{{p.system_code||'未分类'}}</td>
          <td v-if="batch.status==='collecting'"><input v-model="photoDrafts[p.id].takenOn" type="date"></td><td v-else>{{p.taken_on||'—'}}</td>
          <td v-if="batch.status==='collecting'"><input v-model="photoDrafts[p.id].confirmed" type="checkbox"></td><td v-else>{{p.confirmation_status==='confirmed'?'已确认':'未确认'}}</td>
          <td v-if="batch.status==='collecting'"><button @click="movePhoto(p,-1)">↑</button> <button @click="movePhoto(p,1)">↓</button> <button @click="savePhoto(p)">保存</button> <button @click="removePhoto(p)">移除</button></td><td v-else>—</td></tr>
        </tbody></table></div>
        <div v-if="batch.status==='collecting'" class="sw-bottom-actions"><button class="primary" @click="submitBatch">提交照片审核</button><small>所有照片必须完成分类、日期和人工确认。</small></div>
        <div v-if="batch.status==='submitted'&&can('report:review')&&Number(batch.created_by)!==props.userId" class="sw-bottom-actions"><button class="primary" @click="reviewBatch">审核通过照片批次</button></div>
      </div>
    </div>

    <div v-if="tab==='reports'" class="sw-panel">
      <div class="sw-panel-title"><h3>检测报告与交付</h3><span>{{ reports.length }} 份</span></div>
      <div v-if="!report" class="sw-two">
        <form class="sw-form" @submit.prevent="createReport">
          <label>报告编号<input v-model="reportForm.reportNo" required></label><label>报告标题<input v-model="reportForm.title" required></label>
          <label>报告类型<select v-model="reportForm.reportType"><option value="inspection">消防检测</option><option value="service">技术服务</option><option value="maintenance">维护保养</option></select></label>
          <label class="wide">摘要<textarea v-model="reportForm.summary" required rows="2"></textarea></label>
          <label class="wide">检测范围<textarea v-model="reportForm.scope" rows="2"></textarea></label>
          <label class="wide">检测方法<textarea v-model="reportForm.methods" rows="2"></textarea></label>
          <label class="wide">检测发现<textarea v-model="reportForm.findings" rows="2"></textarea></label>
          <label class="wide">服务结论<textarea v-model="reportForm.conclusion" required rows="2"></textarea></label>
          <button class="primary" :disabled="busy">创建报告草稿</button>
        </form>
        <div class="sw-list">
          <article v-for="r in reports" :key="r.id"><div><b>{{ r.title }}</b><small>{{ r.report_no }} · v{{ r.current_version_no }} · 修订 {{ r.revision_no }}</small></div><div><span class="sw-pill">{{ reportLabel(r.status) }}</span><button @click="openReport(r.id)">打开</button></div></article>
          <div v-if="!reports.length" class="sw-empty small">尚未创建报告</div>
        </div>
      </div>
      <div v-else class="sw-report-view">
        <div class="sw-panel-title"><div><button class="back" @click="report=null">← 报告列表</button><h3>{{ report.title }}</h3><small>{{ report.report_no }} · 版本 {{ report.current_version_no }} · 修订 {{ report.revision_no }}</small></div><span class="sw-pill">{{ reportLabel(report.status) }}</span></div>
        <div class="sw-form">
          <label>报告标题<input v-model="reportForm.title"></label><label>报告编号<input :value="report.report_no" disabled></label>
          <label class="wide">摘要<textarea v-model="reportForm.summary" rows="2"></textarea></label><label class="wide">检测范围<textarea v-model="reportForm.scope" rows="2"></textarea></label><label class="wide">检测方法<textarea v-model="reportForm.methods" rows="2"></textarea></label><label class="wide">检测发现<textarea v-model="reportForm.findings" rows="3"></textarea></label><label class="wide">服务结论<textarea v-model="reportForm.conclusion" rows="2"></textarea></label>
        </div>
        <div class="sw-bottom-actions"><button v-if="['draft','returned'].includes(report.status)&&can('report:write')" class="primary" @click="saveReport">保存新版本</button><button v-if="report.status==='draft'&&can('report:write')" @click="reportAction('submit')">提交审核</button><button v-if="report.status==='submitted'&&can('report:review')" @click="reportAction('start-review')">开始审核</button><button v-if="report.status==='reviewing'&&can('report:review')" @click="reportAction('approve')">审核通过</button><button v-if="report.status==='reviewing'&&can('report:review')" class="danger" @click="reportAction('return')">退回修改</button><button v-if="report.status==='approved'&&can('report:issue')" @click="reportAction('issue')">签发报告</button><button v-if="report.status==='issued'&&can('report:issue')" @click="reportAction('archive')">归档报告</button><button v-if="['issued','archived'].includes(report.status)&&can('report:write')" @click="reportAction('revise')">创建修订版</button></div>
        <section v-if="['draft','returned'].includes(report.status)&&can('report:write')" class="sw-docx">
          <h4>照片插入与 DOCX 生成</h4><label class="sw-upload">上传现有报告 DOCX 模板<input type="file" hidden accept=".docx" @change="chooseTemplate"></label><small>{{ templateFile?.original_name||'尚未选择模板' }}</small>
          <div class="sw-batch-checks"><label v-for="b in batches.filter(x=>x.status==='reviewed'||(photoMode==='replace'&&x.status==='locked'))" :key="b.id"><input type="checkbox" :value="b.id" v-model="selectedBatches">{{ b.title }} · {{ b.photo_count }} 张 · {{ batchLabel(b.status) }}</label></div>
          <label class="sw-mode"><input type="radio" value="append" v-model="photoMode"> 保留模板现有照片，在对应系统照片表格中追加（默认，非破坏性）</label>
          <label class="sw-mode"><input type="radio" value="replace" v-model="photoMode"> 替换模板中所有系统照片，仅保留本次已选批次（请确认模板原照片可替换）</label>
          <p class="sw-disclaimer">DOCX 正文和检测结论以所上传模板为准；ERP 中的摘要、范围、方法和结论字段用于流程记录与审核，不会自动改写模板正文。追加模式下，已用过的批次不可重复追加；继续补充照片时，请以上一版生成的 DOCX 作为模板并选择新的审核批次。</p>
          <button class="primary" @click="generateDocx">按系统章节插入照片并生成 DOCX</button>
        </section>
        <section v-if="report.status==='issued'||report.status==='archived'" class="sw-docx">
          <h4>报告邮寄 / 交付登记</h4>
          <div class="sw-form">
            <label>交付方式<select v-model="deliveryForm.deliveryMethod"><option value="courier">快递</option><option value="hand_delivery">现场交付</option><option value="electronic">电子交付</option></select></label><label>收件人<input v-model="deliveryForm.recipientName" required></label><label>收件单位<input v-model="deliveryForm.recipientOrganization"></label><label>联系电话<input v-model="deliveryForm.recipientPhone"></label><label class="wide">收件地址<input v-model="deliveryForm.recipientAddress" required></label><label>快递公司<input v-model="deliveryForm.courierName"></label><label>快递单号<input v-model="deliveryForm.trackingNo"></label><label class="wide">备注<textarea v-model="deliveryForm.notes" rows="2"></textarea></label>
          </div><button class="primary" @click="createDelivery">登记交付</button>
          <div class="sw-list"><article v-for="d in deliveries" :key="d.id"><div><b>{{ d.recipient_organization||d.recipient_name }}</b><small>{{ d.courier_name||d.delivery_method }} · {{ d.tracking_no||'无单号' }}</small><small>{{ d.recipient_address }}</small><small v-for="ev in d.events||[]" :key="ev.id">{{ev.from_status||'创建'}} → {{ev.to_status}} · {{ev.actor_name}} · {{ev.created_at}}</small></div><div><span class="sw-pill">{{ ({prepared:'待寄出',dispatched:'已寄出',delivered:'已签收',returned:'退回',cancelled:'已取消'})[d.status] }}</span><button v-if="d.status==='prepared'||d.status==='returned'" @click="updateDelivery(d,'dispatched')">登记寄出</button><button v-if="d.status==='dispatched'" @click="updateDelivery(d,'delivered')">登记签收</button><button v-if="d.status==='dispatched'" @click="updateDelivery(d,'returned')">登记退回</button></div></article></div>
        </section>
        <section class="sw-history"><h4>版本历史</h4><div v-for="v in report.versions||[]" :key="v.version_no">v{{ v.version_no }} · {{ v.content_sha256?.slice(0,16) }} · {{ v.created_at }}</div><h4>生成文件历史</h4><div v-for="a in report.artifacts||[]" :key="a.id">v{{ a.version_no }} / DOCX #{{ a.artifact_revision_no }} · {{ a.original_name }} · {{ a.sha256?.slice(0,16) }} <button @click="downloadFile(a.file_id,a.original_name)">下载</button></div><h4>操作记录</h4><div v-for="e in report.events||[]" :key="e.id">{{ e.from_status||'新建' }} → {{ e.to_status }} · {{ e.actor_name }} · {{ e.created_at }} · {{ e.reason }}</div></section>
      </div>
    </div>
  </template>
</section>
</template>

<style scoped>
.service-workflow{display:grid;gap:16px;color:#24344b}.sw-head{display:flex;justify-content:space-between;align-items:flex-start}.sw-head h2{margin:4px 0;font-size:23px}.sw-head p{margin:0;color:#7b899b;font-size:12px}.sw-kicker{font-size:10px;letter-spacing:.14em;color:#5579bb;font-weight:700}.sw-head button,.sw-tabs button,.sw-list button,.sw-bottom-actions button,.sw-row-actions button,.sw-issue-actions button,.back{border:1px solid #dfe6ef;background:#fff;color:#52647b;border-radius:8px;padding:8px 12px;font-size:12px;cursor:pointer}.sw-selects{display:grid;grid-template-columns:1fr 1fr;gap:12px;padding:15px;background:#fff;border:1px solid #e6ebf2;border-radius:12px}.sw-selects label,.sw-form label{display:grid;gap:6px;color:#64748b;font-size:11px}.sw-selects select,.sw-form input,.sw-form select,.sw-form textarea,.sw-photo-meta select,.sw-photo-meta input{width:100%;min-width:0;border:1px solid #dfe6ef;border-radius:8px;padding:9px;background:#fff;color:#34465e;font:inherit;font-size:12px}.sw-form select[multiple]{min-height:76px}.sw-tabs{display:flex;gap:8px;border-bottom:1px solid #e6ebf2;padding-bottom:10px}.sw-tabs button.active{background:#edf3ff;border-color:#c8d7fa;color:#315bb3}.sw-panel{background:#fff;border:1px solid #e6ebf2;border-radius:13px;padding:18px;display:grid;gap:16px}.sw-panel-title{display:flex;justify-content:space-between;align-items:center;gap:12px}.sw-panel-title h3{font-size:16px;margin:4px 0;color:#293d59}.sw-panel-title span,.sw-panel-title small{font-size:11px;color:#8290a3}.sw-form{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.sw-form .wide{grid-column:1/-1}.sw-form .primary,.primary{background:#315fca;color:#fff;border:1px solid #315fca;border-radius:8px;padding:10px 14px;font-size:12px;cursor:pointer}.sw-list{display:grid;gap:8px}.sw-list article{display:flex;justify-content:space-between;align-items:center;gap:15px;padding:12px;border:1px solid #edf0f5;border-radius:10px}.sw-list article>div:first-child{display:grid;gap:4px;min-width:0}.sw-list b{font-size:12px}.sw-list small,.sw-list p{font-size:11px;color:#8290a3;margin:0;overflow-wrap:anywhere}.sw-list article>div:last-child{display:flex;align-items:center;gap:8px;flex-wrap:wrap}.sw-pill{white-space:nowrap;background:#f0f4fa;color:#5e6f86;border-radius:20px;padding:5px 9px;font-size:10px}.sw-issue-actions{display:grid;justify-items:end;gap:6px}.sw-resolution{color:#367c58!important}.sw-two{display:grid;grid-template-columns:minmax(260px,.9fr) minmax(0,1.1fr);gap:18px}.sw-empty{padding:35px 15px;text-align:center;border:1px dashed #d6deea;border-radius:10px;color:#8290a3;font-size:12px}.sw-empty.small{padding:18px}.sw-msg{padding:10px 13px;border-radius:8px;font-size:12px}.sw-msg.error{background:#fff0ed;color:#a33}.sw-msg.success{background:#eaf7ef;color:#287449}.sw-upload-row{display:flex;align-items:center;gap:12px;flex-wrap:wrap}.sw-upload{display:inline-flex;align-items:center;background:#315fca;color:#fff;border-radius:8px;padding:10px 14px;font-size:12px;cursor:pointer}.sw-upload-row small,.sw-bottom-actions small{font-size:11px;color:#8290a3}.sw-photo-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(205px,1fr));gap:12px}.sw-photo-card{border:1px solid #e4eaf2;border-radius:10px;overflow:hidden;background:#fff}.sw-photo-table-wrap{overflow:auto;border:1px solid #e6ebf2;border-radius:10px}.sw-photo-table{width:100%;border-collapse:collapse;background:#fff}.sw-photo-table th,.sw-photo-table td{padding:8px;border-bottom:1px solid #edf0f5;text-align:left;font-size:11px;white-space:nowrap}.sw-photo-table img{width:88px;height:62px;object-fit:contain;background:#f1f4f8}.sw-photo-table select,.sw-photo-table input:not([type=checkbox]){border:1px solid #dfe6ef;border-radius:6px;padding:6px;font:inherit;font-size:11px;max-width:210px}.sw-photo-table button{border:1px solid #dfe6ef;background:#fff;border-radius:6px;padding:6px 8px;font-size:10px;color:#56677d}.sw-photo-card>img,.sw-photo-placeholder{width:100%;height:150px;object-fit:contain;background:#f1f4f8}.sw-photo-placeholder{display:grid;place-items:center;color:#8794a6;font-size:12px}.sw-photo-meta{display:grid;gap:8px;padding:10px}.sw-photo-meta b{font-size:11px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.sw-photo-meta label{display:grid;gap:4px;color:#7c899a;font-size:10px}.sw-photo-meta .confirm{display:flex;align-items:center;gap:6px}.sw-photo-meta .confirm input{width:auto}.sw-row-actions{display:flex;justify-content:space-between}.sw-row-actions .danger,.sw-bottom-actions .danger{color:#b54e4e}.sw-bottom-actions{display:flex;align-items:center;gap:10px;flex-wrap:wrap}.sw-docx{display:grid;gap:12px;padding:16px;border:1px solid #e3eaf4;border-radius:10px;background:#f9fbfe}.sw-docx h4,.sw-history h4{font-size:13px;margin:0;color:#405675}.sw-batch-checks{display:flex;gap:12px;flex-wrap:wrap}.sw-batch-checks label{font-size:11px;color:#607189}.sw-mode{display:flex;gap:8px;align-items:flex-start;font-size:11px;color:#607189}.sw-mode input{margin-top:2px}.sw-disclaimer{font-size:11px;line-height:1.7;color:#987044;background:#fff8ed;border:1px solid #f1e1c8;padding:9px 11px;border-radius:8px}.sw-history{display:grid;gap:8px;padding:14px;border-top:1px solid #edf0f5;font-size:11px;color:#7b899a}.sw-history h4{margin-top:8px}.back{padding:4px 8px;margin-bottom:6px}@media(max-width:800px){.sw-two{grid-template-columns:1fr}.sw-selects{grid-template-columns:1fr}}@media(max-width:560px){.sw-form{grid-template-columns:1fr}.sw-form .wide{grid-column:auto}.sw-list article{align-items:flex-start;flex-direction:column}.sw-issue-actions{display:flex;justify-items:start}}
</style>
