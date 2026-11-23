<script setup>
import { computed,onMounted,ref,watch } from 'vue'
import {api,downloadFile} from './api'
const projects=ref([]),employees=ref([]),selectedId=ref(''),detail=ref(null),documents=ref([]),visits=ref([])
const busy=ref(false),error=ref(''),notice=ref(''),q=ref('')
const edit=ref({honeybeeUploaded:false,honeybeeUploadedOn:'',documentsReady:false,documentsReadyOn:'',maintenanceCardSent:false,maintenanceCardSentOn:'',status:'in_progress',actualStart:'',actualEnd:''})
const visitForm=ref({title:'维护保养现场服务',performedOn:'',content:'',summary:'',participantIds:[]})
const currentContract=computed(()=>detail.value?.contracts?.find(x=>x.is_current)||null)
const rows=computed(()=>projects.value.filter(p=>{
  const term=q.value.trim().toLowerCase()
  return !term||[p.name,p.project_no,p.customer_name,p.contract_no].some(x=>String(x||'').toLowerCase().includes(term))
}))
async function load(){
  projects.value=await api('/business/projects').catch(()=>[])
  employees.value=await api('/business/employees').catch(()=>[])
  if(!selectedId.value&&projects.value.length)selectedId.value=String(projects.value[0].id)
}
async function openProject(id){
  try{
    detail.value=await api('/business/projects/'+id)
    edit.value={
      honeybeeUploaded:!!detail.value.honeybee_uploaded,honeybeeUploadedOn:detail.value.honeybee_uploaded_on||'',
      documentsReady:!!detail.value.documents_ready,documentsReadyOn:detail.value.documents_ready_on||'',
      maintenanceCardSent:!!detail.value.maintenance_card_sent,maintenanceCardSentOn:detail.value.maintenance_card_sent_on||'',
      status:detail.value.status,actualStart:detail.value.actual_start||'',actualEnd:detail.value.actual_end||''
    }
    const contract=detail.value.contract_id
    documents.value=contract?await api('/business/contracts/'+contract+'/documents').catch(()=>[]):[]
    visits.value=await api('/business/projects/'+id+'/site-visits').catch(()=>[])
  }catch(e){error.value=e.message}
}
async function save(){
  if(!detail.value)return
  busy.value=true;error.value=''
  try{
    await api('/business/projects/'+detail.value.id,{method:'PUT',body:JSON.stringify(edit.value)})
    await openProject(detail.value.id);await load();notice.value='维保项目状态已更新'
  }catch(e){error.value=e.message}finally{busy.value=false}
}
async function uploadCard(event){
  const file=event.target.files?.[0]
  if(!file||!currentContract.value){error.value='请先为项目关联当前有效合同';event.target.value='';return}
  busy.value=true;error.value=''
  try{
    const form=new FormData();form.append('file',file)
    const f=await api('/files',{method:'POST',body:form})
    await api('/business/contracts/'+currentContract.value.contract_id+'/documents',{method:'POST',body:JSON.stringify({fileId:f.id,documentType:'maintenance_card',label:file.name})})
    await openProject(detail.value.id);notice.value='维保公示卡已按当前合同归档'
  }catch(e){error.value=e.message}finally{busy.value=false;event.target.value=''}
}
async function addVisit(){
  busy.value=true;error.value=''
  try{
    await api('/business/projects/'+detail.value.id+'/site-visits',{method:'POST',body:JSON.stringify({
      visitType:'maintenance',title:visitForm.value.title,performedOn:visitForm.value.performedOn,
      content:visitForm.value.content,summary:visitForm.value.summary,status:'completed',
      participants:visitForm.value.participantIds.map(id=>({employeeId:Number(id),role:'维护保养现场人员'}))
    })})
    visitForm.value={title:'维护保养现场服务',performedOn:'',content:'',summary:'',participantIds:[]}
    await openProject(detail.value.id);notice.value='维护保养记录已保存'
  }catch(e){error.value=e.message}finally{busy.value=false}
}
async function download(id,name){try{await downloadFile(id,name)}catch(e){error.value=e.message}}
watch(selectedId,()=>selectedId.value&&openProject(Number(selectedId.value)))
onMounted(async()=>{await load();if(selectedId.value)await openProject(Number(selectedId.value))})
</script>
<template>
<section class="maintenance-module">
  <div class="maintenance-head"><div><span class="eyebrow">MAINTENANCE MANAGEMENT</span><h2>维护保养</h2><p>维护项目资料、小蜜蜂上传、维保公示卡和每份合同对应的资料历史。</p></div><input v-model="q" placeholder="搜索项目、客户或合同"></div>
  <p v-if="error" class="message error">{{error}}</p><p v-if="notice" class="message success">{{notice}}</p>
  <div class="maintenance-layout">
    <section class="panel project-list"><h3>维保项目 <span>{{rows.length}}</span></h3>
      <button v-for="p in rows" :key="p.id" :class="{active:String(p.id)===selectedId}" @click="selectedId=String(p.id)">
        <b>{{p.name}}</b><small>{{p.project_no}} · {{p.customer_name}}</small><span>{{p.contract_no||'尚未关联合同'}} · {{p.status}}</span>
      </button>
      <p v-if="!rows.length" class="empty">暂无项目</p>
    </section>
    <section class="panel" v-if="detail">
      <div class="project-title"><div><h3>{{detail.name}}</h3><p>{{detail.project_no}} · {{detail.customer_name}}</p></div><span class="tag">{{detail.status}}</span></div>
      <div class="contract-banner" v-if="currentContract"><b>当前合同：{{currentContract.contract_no}} · {{currentContract.title}}</b><small>合同期限：{{currentContract.starts_on||'—'}} 至 {{currentContract.ends_on||'—'}} · {{currentContract.status}}</small></div>
      <p v-else class="message error">项目尚未关联当前合同。请先在「业务档案 → 项目」中关联合同。</p>
      <form class="status-form" @submit.prevent="save">
        <h4>项目维护状态</h4>
        <label class="check-line"><input type="checkbox" v-model="edit.honeybeeUploaded"> 小蜜蜂信息已上传</label>
        <label>上传日期<input type="date" v-model="edit.honeybeeUploadedOn"></label>
        <label class="check-line"><input type="checkbox" v-model="edit.documentsReady"> 项目资料已齐全</label>
        <label>资料完成日期<input type="date" v-model="edit.documentsReadyOn"></label>
        <label class="check-line"><input type="checkbox" v-model="edit.maintenanceCardSent"> 维保公示卡已寄出</label>
        <label>寄出日期<input type="date" v-model="edit.maintenanceCardSentOn"></label>
        <label>项目状态<select v-model="edit.status"><option value="planned">计划中</option><option value="in_progress">进行中</option><option value="paused">暂停</option><option value="completed">已完成</option><option value="cancelled">已取消</option></select></label>
        <label>实际开始<input type="date" v-model="edit.actualStart"></label><label>实际结束<input type="date" v-model="edit.actualEnd"></label>
        <button class="primary-button" :disabled="busy">保存维护状态</button>
      </form>
      <div class="related-block">
        <h4>当前合同的维保公示卡</h4>
        <label class="upload-label">上传公示卡<input type="file" accept=".pdf,.docx,.jpg,.jpeg,.png" @change="uploadCard" :disabled="!currentContract"></label>
        <div v-for="d in documents.filter(x=>x.document_type==='maintenance_card')" :key="d.id" class="document-row">
          <span>{{d.original_name}} <small>· {{d.created_at}}</small></span><button class="text-button" @click="download(d.file_id,d.original_name)">下载</button>
        </div>
        <p v-if="!documents.some(x=>x.document_type==='maintenance_card')" class="empty">当前合同尚无公示卡附件</p>
        <h4>合同资料历史</h4>
        <div v-for="d in documents" :key="d.id" class="document-row"><span>{{d.document_type}} · {{d.original_name}}</span><button class="text-button" @click="download(d.file_id,d.original_name)">下载</button></div>
        <p v-if="!documents.length" class="empty">当前合同暂无归档资料</p>
      </div>
      <div class="related-block">
        <h4>维护保养现场记录</h4>
        <form class="visit-form" @submit.prevent="addVisit">
          <label>记录标题<input v-model="visitForm.title" required></label><label>服务时间<input v-model="visitForm.performedOn" type="datetime-local" required></label>
          <label>现场人员（可多选）<select v-model="visitForm.participantIds" multiple size="3"><option v-for="e in employees.filter(x=>x.status==='active')" :key="e.id" :value="String(e.id)">{{e.full_name}} · {{e.employee_type}}</option></select></label>
          <label>服务内容<textarea v-model="visitForm.content" rows="2"></textarea></label><label>服务摘要<textarea v-model="visitForm.summary" rows="2"></textarea></label>
          <button class="primary-button" :disabled="busy">登记现场维护</button>
        </form>
        <div v-for="v in visits.filter(x=>x.visit_type==='maintenance')" :key="v.id" class="document-row"><span>{{v.performed_on}} · {{v.title}} · {{v.participant_count}} 位人员</span><span class="tag">{{v.status}}</span></div>
      </div>
      <div class="related-block">
        <h4>项目合同时间线</h4>
        <div v-for="c in detail.contracts||[]" :key="c.id" class="document-row"><span>{{c.contract_no}} · {{c.relation_type==='renewal'?'续签':c.relation_type==='supplemental'?'补充':'初始'}} · {{c.starts_on||'—'}} 至 {{c.ends_on||'—'}}</span><b v-if="c.is_current">当前合同</b></div>
      </div>
    </section>
    <section v-else class="panel empty">请选择一个项目</section>
  </div>
</section>
</template>
<style scoped>
.maintenance-module{display:grid;gap:18px}.maintenance-head{display:flex;align-items:end;justify-content:space-between;gap:15px;flex-wrap:wrap}.maintenance-head h2{margin:5px 0;font-size:23px;color:#203149}.maintenance-head p{font-size:12px;color:#7d889a;margin:0}.maintenance-head>input{padding:10px;border:1px solid #dce2eb;border-radius:8px}.maintenance-layout{display:grid;grid-template-columns:minmax(240px,.75fr) minmax(420px,1.4fr);gap:16px;align-items:start}.project-list{display:grid;gap:8px}.project-list h3{font-size:14px;margin:0 0 8px}.project-list h3 span{color:#9aa4b3;font-weight:400;margin-left:8px}.project-list button{display:grid;gap:5px;text-align:left;background:#fff;border:1px solid #edf0f5;border-radius:9px;padding:12px;color:#34455f}.project-list button.active{border-color:#9eb5ec;background:#f6f8ff}.project-list small,.project-list span{font-size:10px;color:#8995a7}.project-title{display:flex;align-items:center;justify-content:space-between}.project-title h3{margin:0;font-size:16px}.project-title p{font-size:11px;color:#8995a7}.tag{font-size:10px;color:#315fca;background:#edf2fd;padding:5px 8px;border-radius:20px}.contract-banner{display:grid;gap:6px;padding:12px;background:#f4f7fd;border-radius:9px;font-size:12px;color:#3f587b}.contract-banner small{font-size:10px;color:#8090a7}.status-form{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px;margin-top:20px}.status-form h4{grid-column:span 2;margin:0;font-size:13px}.status-form label,.visit-form label{display:grid;gap:6px;font-size:11px;color:#68768b}.status-form input,.status-form select,.visit-form input,.visit-form select,.visit-form textarea{padding:9px;border:1px solid #dce2eb;border-radius:8px;background:white;font:inherit;font-size:12px;min-width:0}.status-form .check-line{display:flex;align-items:center;gap:8px}.status-form .check-line input{width:auto}.status-form button{grid-column:span 2}.related-block{border-top:1px solid #edf0f5;margin-top:20px;padding-top:16px}.related-block h4{font-size:12px;margin:0 0 10px}.upload-label{display:inline-flex;gap:10px;align-items:center;font-size:11px;color:#315fca;margin-bottom:10px}.upload-label input{font-size:11px}.document-row{display:flex;justify-content:space-between;gap:12px;align-items:center;padding:9px 0;border-bottom:1px solid #edf0f5;font-size:11px;color:#62718a}.document-row small{color:#98a2b1}.text-button{border:0;background:transparent;color:#315fca;font-size:11px}.visit-form{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}.visit-form label:nth-child(3),.visit-form label:nth-child(4),.visit-form label:nth-child(5){grid-column:span 2}.visit-form button{grid-column:span 2}.empty{font-size:12px;color:#9aa4b3;text-align:center;padding:20px}@media(max-width:900px){.maintenance-layout{grid-template-columns:1fr}.status-form,.visit-form{grid-template-columns:1fr}.status-form h4,.status-form button,.visit-form label,.visit-form button{grid-column:auto}}
</style>
