<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { api, downloadFile } from './api'

const props=defineProps({permissions:{type:Array,default:()=>[]}})
const can=code=>props.permissions.includes(code)
const tab=ref('visits'),projects=ref([]),employees=ref([]),selectedProjectId=ref('')
const visits=ref([]),issues=ref([]),reports=ref([]),selectedReport=ref(null)
const deliveries=ref([]),reportEvents=ref([]),issueEvents=ref([])
const busy=ref(false),error=ref(''),notice=ref('')
const visitForm=ref({visitNo:'',visitType:'inspection',title:'',performedOn:'',content:'',summary:'',status:'completed',participantIds:[]})
const issueForm=ref({title:'',description:'',severity:'medium',ownerEmployeeId:'',dueOn:'',reportedToCustomerOn:'',sourceSiteVisitId:'',initialNote:''})
const reportForm=ref({reportNo:'',title:'',reportType:'inspection',summary:'',scope:'',methods:'',findings:'',conclusion:''})
const reportEdit=ref({title:'',summary:'',scope:'',methods:'',findings:'',conclusion:''})
const deliveryForm=ref({recipientName:'',recipientAddress:'',courierName:'',trackingNo:'',sentOn:'',deliveryStatus:'pending',notes:''})
const linkVisitId=ref('')
const verifyVisit=ref({})
const issueTransitionNote=ref({})
const selectedIssue=ref(null)
const statusLabel={planned:'计划中',completed:'已完成',cancelled:'已取消',open:'待整改',in_progress:'整改中',resolved:'待复核',closed:'已关闭',draft:'草稿',submitted:'待审核',reviewing:'审核中',returned:'已退回',approved:'已通过',issued:'已签发',archived:'已归档'}
const activeProject=computed(()=>projects.value.find(x=>String(x.id)===String(selectedProjectId.value)))
async function loadProjects(){
  projects.value=await api('/business/projects').catch(()=>[])
  employees.value=await api('/business/employees').catch(()=>[])
  if(!selectedProjectId.value&&projects.value.length)selectedProjectId.value=String(projects.value[0].id)
}
async function loadProject(){
  if(!selectedProjectId.value){visits.value=[];issues.value=[];reports.value=[];return}
  const id=selectedProjectId.value
  try{
    const [v,i,r]=await Promise.all([
      api('/business/projects/'+id+'/site-visits'),
      api('/business/projects/'+id+'/issues'),
      api('/business/projects/'+id+'/reports')
    ])
    visits.value=v;issues.value=i;reports.value=r
  }catch(e){error.value=e.message}
}
async function createVisit(){
  busy.value=true;error.value='';notice.value=''
  try{
    await api('/business/projects/'+selectedProjectId.value+'/site-visits',{method:'POST',body:JSON.stringify({
      ...visitForm.value,participants:visitForm.value.participantIds.map(id=>({employeeId:Number(id),role:'现场人员'}))
    })})
    visitForm.value={visitNo:'',visitType:'inspection',title:'',performedOn:'',content:'',summary:'',status:'completed',participantIds:[]}
    await loadProject();notice.value='现场服务记录已保存'
  }catch(e){error.value=e.message}finally{busy.value=false}
}
async function createIssue(){
  busy.value=true;error.value='';notice.value=''
  try{
    const data={...issueForm.value,ownerEmployeeId:issueForm.value.ownerEmployeeId?Number(issueForm.value.ownerEmployeeId):null,
      sourceSiteVisitId:issueForm.value.sourceSiteVisitId?Number(issueForm.value.sourceSiteVisitId):null}
    delete data.initialNote
    data.initialNote=issueForm.value.initialNote
    await api('/business/projects/'+selectedProjectId.value+'/issues',{method:'POST',body:JSON.stringify(data)})
    issueForm.value={title:'',description:'',severity:'medium',ownerEmployeeId:'',dueOn:'',reportedToCustomerOn:'',sourceSiteVisitId:'',initialNote:''}
    await loadProject();notice.value='问题已登记'
  }catch(e){error.value=e.message}finally{busy.value=false}
}
async function transitionIssue(issue,toStatus){
  const note=issueTransitionNote.value[issue.id]||({in_progress:'开始整改',resolved:'客户反馈整改完成',closed:'现场复核通过',open:'重新打开整改项'}[toStatus])
  if(!note){error.value='请填写本次状态变更说明';return}
  const siteVisitId=toStatus==='closed'?verifyVisit.value[issue.id]:null
  if(toStatus==='closed'&&!siteVisitId){error.value='请选择已完成的现场复核记录';return}
  busy.value=true;error.value=''
  try{
    await api('/business/issues/'+issue.id+'/transition',{method:'POST',body:JSON.stringify({
      toStatus,note,siteVisitId:siteVisitId?Number(siteVisitId):null
    })})
    await loadProject();if(selectedIssue.value?.id===issue.id)await openIssue(issue.id)
    notice.value='整改状态已更新'
  }catch(e){error.value=e.message}finally{busy.value=false}
}
async function openIssue(id){
  selectedIssue.value=issues.value.find(x=>x.id===id)||null
  issueEvents.value=await api('/business/issues/'+id+'/events').catch(()=>[])
}
async function createReport(){
  busy.value=true;error.value=''
  try{
    const content={summary:reportForm.value.summary,scope:reportForm.value.scope,methods:reportForm.value.methods,
      findings:reportForm.value.findings,conclusion:reportForm.value.conclusion}
    const r=await api('/business/projects/'+selectedProjectId.value+'/reports',{method:'POST',body:JSON.stringify({
      reportNo:reportForm.value.reportNo,title:reportForm.value.title,reportType:reportForm.value.reportType,content
    })})
    reportForm.value={reportNo:'',title:'',reportType:'inspection',summary:'',scope:'',methods:'',findings:'',conclusion:''}
    await loadProject();await openReport(r.id);notice.value='报告草稿已创建'
  }catch(e){error.value=e.message}finally{busy.value=false}
}
async function openReport(id){
  try{
    selectedReport.value=await api('/business/reports/'+id)
    const c=selectedReport.value.content||{}
    reportEdit.value={title:selectedReport.value.title,summary:c.summary||'',scope:c.scope||'',methods:c.methods||'',findings:c.findings||'',conclusion:c.conclusion||''}
    deliveries.value=selectedReport.value.deliveries||[]
    reportEvents.value=selectedReport.value.events||[]
  }catch(e){error.value=e.message}
}
async function saveReport(){
  if(!selectedReport.value)return
  busy.value=true;error.value=''
  try{
    await api('/business/reports/'+selectedReport.value.id,{method:'PUT',body:JSON.stringify({
      expectedVersionNo:selectedReport.value.current_version_no,title:reportEdit.value.title,
      content:{summary:reportEdit.value.summary,scope:reportEdit.value.scope,methods:reportEdit.value.methods,
        findings:reportEdit.value.findings,conclusion:reportEdit.value.conclusion}
    })})
    await openReport(selectedReport.value.id);await loadProject();notice.value='报告已保存为新版本'
  }catch(e){error.value=e.message}finally{busy.value=false}
}
async function reportAction(action,payload={}){
  if(!selectedReport.value)return
  busy.value=true;error.value=''
  try{
    await api('/business/reports/'+selectedReport.value.id+'/'+action,{method:'POST',body:JSON.stringify(payload)})
    await openReport(selectedReport.value.id);await loadProject();notice.value='报告状态已更新'
  }catch(e){error.value=e.message}finally{busy.value=false}
}
async function linkVisit(){
  if(!selectedReport.value||!linkVisitId.value)return
  try{
    await api('/business/reports/'+selectedReport.value.id+'/site-visits',{method:'POST',body:JSON.stringify({siteVisitId:Number(linkVisitId.value)})})
    await openReport(selectedReport.value.id);linkVisitId.value='';notice.value='现场记录已关联报告'
  }catch(e){error.value=e.message}
}
async function addDelivery(){
  if(!selectedReport.value)return
  busy.value=true;error.value=''
  try{
    await api('/business/reports/'+selectedReport.value.id+'/deliveries',{method:'POST',body:JSON.stringify({
      ...deliveryForm.value,versionNo:selectedReport.value.current_version_no
    })})
    deliveryForm.value={recipientName:'',recipientAddress:'',courierName:'',trackingNo:'',sentOn:'',deliveryStatus:'pending',notes:''}
    await openReport(selectedReport.value.id);notice.value='邮寄登记已保存'
  }catch(e){error.value=e.message}finally{busy.value=false}
}
async function updateDelivery(item,toStatus){
  const payload={deliveryStatus:toStatus}
  if(toStatus==='sent'){payload.courierName=item.courier_name;payload.trackingNo=item.tracking_no;payload.sentOn=item.sent_on||new Date().toISOString().slice(0,10)}
  if(toStatus==='delivered')payload.deliveredOn=new Date().toISOString().slice(0,10)
  try{await api('/business/deliveries/'+item.id,{method:'PUT',body:JSON.stringify(payload)});await openReport(selectedReport.value.id)}
  catch(e){error.value=e.message}
}
async function uploadReportAttachment(event){
  const file=event.target.files?.[0];if(!file||!selectedReport.value)return
  try{
    const form=new FormData();form.append('file',file)
    const f=await api('/files',{method:'POST',body:form})
    await api('/business/reports/'+selectedReport.value.id+'/attachments',{method:'POST',body:JSON.stringify({fileId:f.id})})
    await openReport(selectedReport.value.id);notice.value='报告附件已归档'
  }catch(e){error.value=e.message}finally{event.target.value=''}
}
async function download(id,name){try{await downloadFile(id,name)}catch(e){error.value=e.message}}
watch(selectedProjectId,loadProject)
onMounted(async()=>{await loadProjects();await loadProject()})
</script>

<template>
<section class="delivery-module">
  <div class="delivery-head"><div><span class="eyebrow">SERVICE DELIVERY</span><h2>现场服务与报告</h2><p>同一项目可以有多次现场服务；整改、报告审核和邮寄均保留过程记录。</p></div>
    <label>当前项目<select v-model="selectedProjectId"><option value="">选择项目</option><option v-for="p in projects" :key="p.id" :value="String(p.id)">{{p.project_no}} · {{p.name}}</option></select></label>
  </div>
  <p v-if="error" class="message error">{{error}}</p><p v-if="notice" class="message success">{{notice}}</p>
  <div class="delivery-tabs"><button v-for="x in [{id:'visits',name:'现场服务'},{id:'issues',name:'问题整改'},{id:'reports',name:'报告审核与邮寄'}]" :key="x.id" :class="{active:tab===x.id}" @click="tab=x.id">{{x.name}}</button></div>
  <div v-if="!selectedProjectId" class="empty panel">请先选择项目</div>
  <template v-else-if="tab==='visits'">
    <div class="delivery-grid">
      <section class="panel"><h3>登记现场服务</h3><form class="create-grid" @submit.prevent="createVisit">
        <label>记录编号（可选）<input v-model="visitForm.visitNo"></label>
        <label>服务类型<select v-model="visitForm.visitType"><option value="maintenance">维护保养</option><option value="inspection">检测</option><option value="rating">评定</option><option value="assessment">评估</option><option value="other">其他</option></select></label>
        <label class="span-2">标题<input v-model="visitForm.title" required placeholder="例如：第一次现场检测"></label>
        <label>现场时间<input v-model="visitForm.performedOn" type="datetime-local" required></label>
        <label>状态<select v-model="visitForm.status"><option value="completed">已完成</option><option value="planned">计划中</option><option value="cancelled">已取消</option></select></label>
        <label class="span-2">现场人员（可多选）<select v-model="visitForm.participantIds" multiple size="4"><option v-for="e in employees.filter(x=>x.status==='active')" :key="e.id" :value="String(e.id)">{{e.full_name}} · {{e.employee_type}}</option></select></label>
        <label class="span-2">服务内容<textarea v-model="visitForm.content" rows="3"></textarea></label>
        <label class="span-2">现场结论/摘要<textarea v-model="visitForm.summary" rows="2"></textarea></label>
        <button class="primary-button" :disabled="busy">保存现场服务记录</button>
      </form></section>
      <section class="panel"><h3>现场服务历史 <span>{{visits.length}} 次</span></h3>
        <article v-for="v in visits" :key="v.id" class="timeline-card"><div class="timeline-top"><b>{{v.title}}</b><span class="tag">{{statusLabel[v.status]}}</span></div>
          <small>{{v.performed_on}} · {{v.visit_type}} · {{v.participant_count}} 位现场人员 · {{v.issue_count}} 个问题</small><p>{{v.summary||v.content||'暂无记录摘要'}}</p></article>
        <p v-if="!visits.length" class="empty">尚无现场服务记录</p>
      </section>
    </div>
  </template>
  <template v-else-if="tab==='issues'">
    <div class="delivery-grid">
      <section class="panel"><h3>登记问题/整改项</h3><form class="create-grid" @submit.prevent="createIssue">
        <label class="span-2">问题标题<input v-model="issueForm.title" required></label>
        <label>严重程度<select v-model="issueForm.severity"><option value="low">低</option><option value="medium">中</option><option value="high">高</option><option value="critical">严重</option></select></label>
        <label>责任人<select v-model="issueForm.ownerEmployeeId"><option value="">未分配</option><option v-for="e in employees" :key="e.id" :value="String(e.id)">{{e.full_name}}</option></select></label>
        <label>来源现场服务<select v-model="issueForm.sourceSiteVisitId"><option value="">未关联</option><option v-for="v in visits" :key="v.id" :value="String(v.id)">{{v.performed_on}} · {{v.title}}</option></select></label>
        <label>整改期限<input v-model="issueForm.dueOn" type="date"></label>
        <label>告知客户日期<input v-model="issueForm.reportedToCustomerOn" type="date"></label>
        <label class="span-2">问题描述<textarea v-model="issueForm.description" rows="3"></textarea></label>
        <label class="span-2">登记说明<textarea v-model="issueForm.initialNote" rows="2"></textarea></label>
        <button class="primary-button">登记问题</button>
      </form></section>
      <section class="panel"><h3>问题整改清单 <span>{{issues.length}} 项</span></h3>
        <article v-for="i in issues" :key="i.id" class="issue-card">
          <div class="timeline-top"><b>{{i.title}}</b><span class="tag" :class="'severity-'+i.severity">{{i.severity}}</span></div>
          <small>{{statusLabel[i.status]}} · 责任人 {{i.owner_name||'未分配'}} · 截止 {{i.due_on||'—'}}</small><p>{{i.description||'暂无描述'}}</p>
          <div class="issue-actions">
            <button v-if="i.status==='open'" class="secondary-button" @click="transitionIssue(i,'in_progress')">开始整改</button>
            <template v-if="i.status==='in_progress'"><input v-model="issueTransitionNote[i.id]" placeholder="填写整改完成情况"><button class="secondary-button" @click="transitionIssue(i,'resolved')">登记整改完成</button></template>
            <template v-if="i.status==='resolved'"><select v-model="verifyVisit[i.id]"><option value="">选择复核现场记录</option><option v-for="v in visits.filter(x=>x.status==='completed')" :key="v.id" :value="String(v.id)">{{v.performed_on}} · {{v.title}}</option></select><input v-model="issueTransitionNote[i.id]" placeholder="复核说明"><button class="secondary-button" @click="transitionIssue(i,'closed')">复核并关闭</button></template>
            <button class="text-button" @click="openIssue(i.id)">查看历史</button>
          </div>
        </article>
        <p v-if="!issues.length" class="empty">当前项目没有问题整改项</p>
      </section>
    </div>
    <section v-if="selectedIssue" class="panel"><h3>{{selectedIssue.title}} · 整改历史</h3><p v-for="e in issueEvents" :key="e.id" class="log-line">{{e.created_at}} · {{statusLabel[e.from_status]||'创建'}} → {{statusLabel[e.to_status]}} · {{e.actor_name}} · {{e.content}}</p></section>
  </template>
  <template v-else>
    <div class="delivery-grid">
      <section class="panel"><h3>创建项目报告</h3><form class="create-grid" @submit.prevent="createReport">
        <label>报告编号<input v-model="reportForm.reportNo" required></label><label>报告名称<input v-model="reportForm.title" required></label>
        <label>报告类型<select v-model="reportForm.reportType"><option value="inspection">检测报告</option><option value="rating">评定报告</option><option value="assessment">评估报告</option><option value="maintenance">维保报告</option><option value="service">服务报告</option><option value="other">其他</option></select></label>
        <label class="span-2">报告摘要<textarea v-model="reportForm.summary" required rows="2"></textarea></label>
        <label class="span-2">服务范围<textarea v-model="reportForm.scope" rows="2"></textarea></label>
        <label class="span-2">方法/依据<textarea v-model="reportForm.methods" rows="2"></textarea></label>
        <label class="span-2">主要发现<textarea v-model="reportForm.findings" rows="2"></textarea></label>
        <label class="span-2">服务结论<textarea v-model="reportForm.conclusion" required rows="2"></textarea></label>
        <button class="primary-button" :disabled="busy">创建报告草稿</button>
      </form>
      <h3 class="subheading">项目报告</h3><button v-for="r in reports" :key="r.id" class="report-row" @click="openReport(r.id)"><b>{{r.report_no}} · {{r.title}}</b><span>{{statusLabel[r.status]||r.status}} · v{{r.current_version_no}}</span></button>
      </section>
      <section class="panel" v-if="selectedReport">
        <div class="timeline-top"><div><h3>{{selectedReport.report_no}} · {{selectedReport.title}}</h3><small>{{statusLabel[selectedReport.status]||selectedReport.status}} · 当前版本 v{{selectedReport.current_version_no}}</small></div><span class="tag">{{selectedReport.report_type}}</span></div>
        <form class="create-grid" @submit.prevent="saveReport">
          <label class="span-2">报告名称<input v-model="reportEdit.title"></label>
          <label class="span-2">摘要<textarea v-model="reportEdit.summary" rows="2"></textarea></label>
          <label class="span-2">服务范围<textarea v-model="reportEdit.scope" rows="2"></textarea></label>
          <label class="span-2">方法/依据<textarea v-model="reportEdit.methods" rows="2"></textarea></label>
          <label class="span-2">主要发现<textarea v-model="reportEdit.findings" rows="2"></textarea></label>
          <label class="span-2">服务结论<textarea v-model="reportEdit.conclusion" rows="2"></textarea></label>
          <button class="primary-button" v-if="['draft','returned'].includes(selectedReport.status)">保存为新版本</button>
        </form>
        <div class="report-actions">
          <button v-if="selectedReport.status==='draft'||selectedReport.status==='returned'" class="secondary-button" @click="reportAction('submit')">提交审核</button>
          <button v-if="selectedReport.status==='submitted'&&can('report:review')" class="secondary-button" @click="reportAction('start-review')">开始审核</button>
          <button v-if="selectedReport.status==='reviewing'&&can('report:review')" class="secondary-button" @click="reportAction('approve')">审核通过</button>
          <button v-if="selectedReport.status==='reviewing'&&can('report:review')" class="secondary-button" @click="reportAction('return',{reason:prompt('请填写退回意见')||''})">退回修改</button>
          <button v-if="selectedReport.status==='approved'&&can('report:issue')" class="secondary-button" @click="reportAction('issue')">签发</button>
          <button v-if="selectedReport.status==='issued'&&can('report:issue')" class="secondary-button" @click="reportAction('archive')">归档</button>
          <label>添加报告附件<input type="file" accept=".pdf,.docx,.jpg,.jpeg,.png" @change="uploadReportAttachment"></label>
        </div>
        <h4>版本附件</h4><div v-for="a in selectedReport.attachments||[]" :key="a.attachment_id" class="log-line">{{a.original_name}} · v{{a.version_no}} <button class="text-button" @click="download(a.file_id,a.original_name)">下载</button></div>
        <h4>关联现场记录</h4><div v-for="v in selectedReport.siteVisits||[]" :key="v.id" class="log-line">{{v.performed_on}} · {{v.title}}</div>
        <div class="report-actions"><select v-model="linkVisitId"><option value="">选择现场记录</option><option v-for="v in visits" :key="v.id" :value="String(v.id)">{{v.performed_on}} · {{v.title}}</option></select><button class="secondary-button" @click="linkVisit">关联现场记录</button></div>
        <h4>审核记录</h4><p v-for="e in selectedReport.events||[]" :key="e.id" class="log-line">{{e.created_at}} · {{statusLabel[e.from_status]||'创建'}} → {{statusLabel[e.to_status]}} · {{e.actor_name}} · {{e.reason}}</p>
        <h4>报告邮寄</h4><div v-for="d in deliveries" :key="d.id" class="log-line">{{d.recipient_name||'收件人未填'}} · {{d.courier_name||'未发出'}} {{d.tracking_no||''}} · {{statusLabel[d.delivery_status]||d.delivery_status}}
          <button v-if="d.delivery_status==='pending'" class="text-button" @click="updateDelivery(d,'sent')">登记已寄出</button>
          <button v-if="d.delivery_status==='sent'" class="text-button" @click="updateDelivery(d,'delivered')">登记已送达</button>
          <button v-if="d.delivery_status==='sent'" class="text-button" @click="updateDelivery(d,'returned')">登记退回</button>
        </div>
        <form v-if="['issued','archived'].includes(selectedReport.status)" class="create-grid compact" @submit.prevent="addDelivery">
          <label>收件人<input v-model="deliveryForm.recipientName"></label><label>收件地址<input v-model="deliveryForm.recipientAddress"></label>
          <label>快递公司<input v-model="deliveryForm.courierName"></label><label>快递单号<input v-model="deliveryForm.trackingNo"></label>
          <label>寄出日期<input v-model="deliveryForm.sentOn" type="date"></label><label>备注<input v-model="deliveryForm.notes"></label>
          <button class="primary-button">新增邮寄登记</button>
        </form>
      </section>
      <section class="panel empty" v-else>选择或创建一份报告以查看审核流程</section>
    </div>
  </template>
</section>
</template>

<style scoped>
.delivery-module{display:grid;gap:18px}.delivery-head{display:flex;align-items:end;justify-content:space-between;gap:18px;flex-wrap:wrap}.delivery-head h2{margin:5px 0;font-size:23px;color:#203149}.delivery-head p{margin:0;color:#7d889a;font-size:12px}.delivery-head label{display:grid;gap:6px;font-size:11px;color:#65738a}.delivery-head select{min-width:280px;padding:10px;border:1px solid #dce2eb;border-radius:8px;background:#fff}.delivery-tabs{display:flex;gap:8px;flex-wrap:wrap}.delivery-tabs button,.secondary-button{border:1px solid #dce3ed;background:#fff;color:#516078;border-radius:8px;padding:9px 13px;font-size:12px}.delivery-tabs button.active{background:#eaf0ff;border-color:#b8c9f4;color:#2856b7;font-weight:700}.delivery-grid{display:grid;grid-template-columns:minmax(300px,.95fr) minmax(350px,1.05fr);gap:16px;align-items:start}.panel{min-width:0}.panel h3{font-size:14px;margin:0 0 15px}.create-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.create-grid label{display:grid;gap:6px;font-size:11px;color:#66748a}.create-grid input,.create-grid select,.create-grid textarea,.issue-actions input,.issue-actions select,.report-actions select{min-width:0;width:100%;padding:9px 10px;border:1px solid #dce2eb;border-radius:8px;font:inherit;font-size:12px;background:#fff;color:#34445c}.create-grid textarea{resize:vertical}.span-2{grid-column:span 2}.timeline-card,.issue-card{padding:12px 0;border-bottom:1px solid #edf0f5}.timeline-top{display:flex;align-items:center;justify-content:space-between;gap:10px}.timeline-top b{font-size:12px;color:#34455f}.timeline-card small,.issue-card small{display:block;font-size:10px;color:#95a0af;margin-top:6px}.timeline-card p,.issue-card p{font-size:11px;color:#68768b;line-height:1.7}.tag{font-size:10px;color:#315fca;background:#edf2fd;padding:5px 8px;border-radius:20px}.severity-critical{color:#b33c3c;background:#fff0ed}.severity-high{color:#b76a25;background:#fff5e9}.severity-medium{color:#8b6a26;background:#fbf7e8}.severity-low{color:#48866b;background:#eaf7f0}.issue-actions{display:flex;flex-wrap:wrap;gap:8px;align-items:center}.issue-actions input,.issue-actions select{max-width:200px}.text-button{border:0;background:transparent;color:#315fca;font-size:11px}.subheading{margin-top:24px!important}.report-row{width:100%;display:flex;justify-content:space-between;gap:10px;text-align:left;border:1px solid #edf0f5;border-radius:8px;background:#fff;padding:11px;margin:6px 0;color:#394b65}.report-row span{font-size:10px;color:#8490a2;white-space:nowrap}.report-actions{display:flex;flex-wrap:wrap;gap:8px;align-items:center;margin:14px 0}.report-actions label{display:grid;gap:5px;font-size:11px;color:#68768b}.report-actions input[type=file]{max-width:220px;font-size:11px}.log-line{font-size:11px;color:#68768b;padding:8px 0;border-bottom:1px solid #edf0f5;line-height:1.7}.empty{font-size:12px;color:#98a3b3;text-align:center;padding:30px}.panel .empty{padding:22px}.detail-block{margin-top:18px}@media(max-width:950px){.delivery-grid{grid-template-columns:1fr}.create-grid{grid-template-columns:1fr}.span-2{grid-column:auto}.delivery-head select{min-width:220px}}
</style>
