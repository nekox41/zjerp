<script setup>
import {computed,onBeforeUnmount,onMounted,ref,watch} from 'vue'
import FormDialog from './FormDialog.vue'
import ModalDialog from './ModalDialog.vue'
import FileDropzone from './FileDropzone.vue'
import {api,downloadFile} from './api'

const props=defineProps({permissions:{type:Array,default:()=>[]},destination:{type:Object,default:null}})
const emit=defineEmits(['navigate'])
const can=p=>props.permissions.includes(p)
const categories=[
 {id:'maintenance',name:'维保',prefix:'ZJWB'},
 {id:'fire_inspection',name:'检测',prefix:'ZJJC'},
 {id:'fire_assessment',name:'评定',prefix:'ZJPD'},
 {id:'fire_evaluation',name:'评估',prefix:'ZJPG'},
 {id:'other',name:'其他',prefix:'ZJFW'}
]
const initialType=categories.some(x=>x.id===props.destination?.serviceType)?props.destination.serviceType:'maintenance'
const activeType=ref(initialType),q=ref(''),rows=ref([]),customers=ref([]),partners=ref([]),projects=ref([])
const loading=ref(false),busy=ref(false),error=ref(''),notice=ref('')
const selected=ref(null),origin=ref(null),createOpen=ref(false),editOpen=ref(false)
const files=ref([]),uploadedFileIds=ref([])
const numberReservation=ref(null),reservingNumber=ref(false)
const customerCreateOpen=ref(false),partnerCreateOpen=ref(false),creatingRelated=ref(false)
const relatedError=ref('')
const newCustomer=ref({name:'',creditCode:'',customerType:'enterprise',phone:'',address:''})
const newPartner=ref({name:'',contact:'',region:''})
let disposed=false
const blank=()=>({title:'',externalContractNo:'',customerId:'',partnerId:'',amount:'0',signedOn:'',startsOn:'',endsOn:'',existingProjectId:''})
const form=ref(blank())
const editForm=ref({title:'',externalContractNo:'',amount:'0',signedOn:'',startsOn:'',endsOn:'',status:'draft'})
const category=computed(()=>categories.find(x=>x.id===activeType.value)||categories[0])
const generatedHint=computed(()=>numberReservation.value?.number||'')
const availableProjects=computed(()=>projects.value.filter(p=>String(p.customer_id)===String(form.value.customerId)&&p.service_type===activeType.value))
const statusNames={draft:'草稿',pending:'待生效',active:'履行中',completed:'已完成',terminated:'已终止'}
const serviceNames={maintenance:'维保',fire_inspection:'检测',fire_assessment:'评定',fire_evaluation:'评估',other:'其他'}
const money=v=>'¥'+Number(v||0).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2})
const date=v=>v?String(v).slice(0,10):'—'
async function loadAux(){
 const [c,p,pr]=await Promise.all([
  can('customer:read')?api('/business/customers'):Promise.resolve([]),
  can('partner:read')?api('/business/partners'):Promise.resolve([]),
  can('project:read')?api('/business/projects'):Promise.resolve([])
 ])
 customers.value=c;partners.value=p;projects.value=pr
}
async function load(){
 loading.value=true;error.value=''
 try{
  await loadAux()
  rows.value=await api('/business/contracts?q='+encodeURIComponent(q.value)+'&serviceType='+encodeURIComponent(activeType.value))
 }catch(e){error.value=e.message}finally{loading.value=false}
}
async function show(id,event){
 origin.value=event?.currentTarget?.getBoundingClientRect?.()||null
 try{
  error.value=''
  const record=await api('/business/contracts/'+id)
  selected.value=record
  if(record.service_type&&record.service_type!==activeType.value)activeType.value=record.service_type
 }catch(e){error.value=e.message}
}
async function releaseNumberReservation(reservation=numberReservation.value){
 if(!reservation?.id)return
 try{await api('/business/number-reservations/'+encodeURIComponent(reservation.id),{method:'DELETE'})}catch{}
}
async function beginCreate(){
 if(reservingNumber.value||busy.value||createOpen.value)return
 error.value='';notice.value='';form.value=blank();files.value=[];uploadedFileIds.value=[]
 reservingNumber.value=true
 try{
  const reservation=await api('/business/number-reservations',{method:'POST',body:JSON.stringify({serviceType:activeType.value,purpose:'contract'})})
  if(disposed){await releaseNumberReservation(reservation);return}
  numberReservation.value=reservation
  createOpen.value=true
 }catch(e){if(!disposed)error.value=e.message}
 finally{reservingNumber.value=false}
}
async function closeCreate(){
 if(busy.value)return
 createOpen.value=false
 const reservation=numberReservation.value
 numberReservation.value=null
 await releaseNumberReservation(reservation)
 form.value=blank();files.value=[];uploadedFileIds.value=[]
}
function updateFiles(next){files.value=next;uploadedFileIds.value=[]}
async function createCustomer(){
 if(creatingRelated.value)return
 creatingRelated.value=true;relatedError.value=''
 try{
  const created=await api('/business/customers',{method:'POST',body:JSON.stringify(newCustomer.value)})
  customers.value=[created,...customers.value]
  form.value.customerId=String(created.id)
  customerCreateOpen.value=false
  newCustomer.value={name:'',creditCode:'',customerType:'enterprise',phone:'',address:''}
 }catch(e){relatedError.value=e.message}finally{creatingRelated.value=false}
}
async function createPartner(){
 if(creatingRelated.value)return
 creatingRelated.value=true;relatedError.value=''
 try{
  const created=await api('/business/partners',{method:'POST',body:JSON.stringify(newPartner.value)})
  partners.value=[created,...partners.value]
  form.value.partnerId=String(created.id)
  partnerCreateOpen.value=false
  newPartner.value={name:'',contact:'',region:''}
 }catch(e){relatedError.value=e.message}finally{creatingRelated.value=false}
}
async function save(){
 if(busy.value)return
 busy.value=true;error.value=''
 try{
  const fileIds=[]
  if(uploadedFileIds.value.length===files.value.length)fileIds.push(...uploadedFileIds.value)
  else{
   for(const file of files.value){
    const fd=new FormData();fd.append('file',file)
    fileIds.push((await api('/files',{method:'POST',body:fd})).id)
   }
   uploadedFileIds.value=[...fileIds]
  }
  if(!numberReservation.value?.id)throw new Error('编号预留已失效，请关闭窗口后重新新建合同')
  const body={...form.value,serviceType:activeType.value,fileIds,createProject:!form.value.existingProjectId,numberReservationId:numberReservation.value.id}
  for(const key of Object.keys(body))if(body[key]==='')body[key]=null
  const created=await api('/business/contracts',{method:'POST',body:JSON.stringify(body)})
  numberReservation.value=null
  files.value=[];uploadedFileIds.value=[];form.value=blank()
  await load();await show(created.id)
  notice.value=created.projectId?'合同已创建，项目已同步建立':'合同已创建并关联已有项目'
 }catch(e){error.value=e.message}finally{busy.value=false}
}
function beginEdit(){
 if(!selected.value)return
 error.value='';editForm.value={
  title:selected.value.title||'',externalContractNo:selected.value.external_contract_no||'',
  amount:String(selected.value.amount??'0'),signedOn:selected.value.signed_on||'',
  startsOn:selected.value.starts_on||'',endsOn:selected.value.ends_on||'',
  status:selected.value.status||'draft'
 }
 editOpen.value=true
}
function closeEdit(){if(!busy.value)editOpen.value=false}
async function saveEdit(){
 if(!selected.value||busy.value)return
 busy.value=true;error.value=''
 try{
  const body={...editForm.value}
  for(const key of ['externalContractNo','signedOn','startsOn','endsOn'])if(body[key]==='')body[key]=null
  await api('/business/contracts/'+selected.value.id,{method:'PUT',body:JSON.stringify(body)})
  editOpen.value=false
  await load();await show(selected.value.id)
  notice.value='合同资料已更新'
 }catch(e){error.value=e.message}finally{busy.value=false}
}
async function download(file){
 try{await downloadFile(file.file_id,file.original_name)}catch(e){error.value=e.message}
}
function openProject(project){emit('navigate','business','projects',project.id,project.service_type)}
watch(activeType,()=>{q.value='';if(selected.value?.service_type!==activeType.value)selected.value=null;load()})
watch(()=>form.value.customerId,()=>{if(form.value.existingProjectId&&!availableProjects.value.some(p=>String(p.id)===String(form.value.existingProjectId)))form.value.existingProjectId=''})
onBeforeUnmount(()=>{disposed=true;if(numberReservation.value?.id)void releaseNumberReservation(numberReservation.value)})
onMounted(async()=>{await load();if(props.destination?.id)await show(props.destination.id)})
</script>
<template>
<section class="contract-directory">
 <div class="contract-top">
  <div class="contract-category-tabs" role="tablist" aria-label="合同服务类别">
   <button v-for="t in categories" :key="t.id" type="button" role="tab" :disabled="reservingNumber" :aria-selected="activeType===t.id" :class="{active:activeType===t.id}" @click="activeType=t.id">{{t.name}}</button>
  </div>
  <div class="contract-tools">
   <input v-model="q" aria-label="搜索合同" placeholder="搜索合同名称、编号或甲方" @keyup.enter="load">
   <button type="button" class="secondary-button" :disabled="loading" @click="load">搜索</button>
   <button v-if="can('contract:write')" type="button" class="primary-button" :disabled="reservingNumber" @click="beginCreate">{{reservingNumber?'正在预留编号…':'＋ 新建合同'}}</button>
  </div>
 </div>
 <p v-if="error&&!createOpen&&!editOpen" class="message error" role="alert">{{error}}</p>
 <p v-if="notice" class="message success" role="status">{{notice}}</p>
 <section class="panel contract-table-panel">
  <div class="contract-table-heading"><h3>{{category.name}}合同</h3><button type="button" class="secondary-button" :disabled="loading" @click="load">刷新</button></div>
  <div class="table-wrap"><table class="contract-table">
   <thead><tr><th>系统编号</th><th>其他编号</th><th>合同名称</th><th>甲方</th><th>签订日期</th><th>服务日期</th><th>合同金额</th><th>中间人</th><th>附件</th><th>操作</th></tr></thead>
   <tbody><tr v-for="r in rows" :key="r.id">
    <td class="contract-number">{{r.contract_no}}</td><td>{{r.external_contract_no||'—'}}</td><td class="contract-title-cell">{{r.title}}</td><td>{{r.customer_name}}</td><td>{{date(r.signed_on)}}</td>
    <td>{{activeType==='maintenance'?(date(r.starts_on)+' 至 '+date(r.ends_on)):'—'}}</td>
    <td class="contract-money">{{money(r.amount)}}</td><td>{{r.intermediary_names||'公司自主签订'}}</td><td>{{r.file_count||0}} 个</td>
    <td><button type="button" class="secondary-button" @click="show(r.id,$event)">查看详情</button></td>
   </tr></tbody>
  </table>
  <div v-if="!rows.length" class="contract-empty"><b>{{loading?'正在加载…':'暂无'+category.name+'合同'}}</b><p v-if="!loading&&can('contract:write')">点击右上角「新建合同」添加第一份合同。</p></div>
  </div>
 </section>
</section>
<FormDialog v-if="createOpen" :title="'新建'+category.name+'合同'" :steps="['合同基本信息','签订日期与金额','合同附件与项目']" :busy="busy" :error="error" @clear-error="error=''" save-label="创建合同并建立项目" auto-open @close="closeCreate">
 <template #default="{step,finish}"><form @submit.prevent="finish(save)">
  <div v-show="step===0" class="wizard-fields" data-wizard-step="0">
   <label>系统合同编号<input :value="generatedHint" disabled></label>
   <label>其他编号（选填）<input v-model.trim="form.externalContractNo" maxlength="160" placeholder="甲方指定的合同编号"></label>
   <label class="wide">合同名称<input v-model.trim="form.title" required maxlength="240" autocomplete="off"></label>
   <div class="wide"><label>甲方</label><div class="contract-select-with-action"><select v-model="form.customerId" required class="contract-related-select"><option value="">请选择甲方</option><option v-for="x in customers" :key="x.id" :value="String(x.id)">{{x.name}}</option></select><button v-if="can('customer:write')" type="button" class="secondary-button" @click="relatedError='';customerCreateOpen=true">＋ 新建甲方</button></div></div>
   <div v-if="can('partner:read')" class="wide"><label>中间人（选填）</label><div class="contract-select-with-action"><select v-model="form.partnerId" class="contract-related-select"><option value="">无（公司自主签订）</option><option v-for="x in partners" :key="x.id" :value="String(x.id)">{{x.name}}</option></select><button v-if="can('partner:write')" type="button" class="secondary-button" @click="relatedError='';partnerCreateOpen=true">＋ 新建中间人</button></div></div>
   <p class="wide muted">不选择中间人时，合同记录为公司自主签订。</p>
  </div>
  <div v-show="step===1" class="wizard-fields" data-wizard-step="1">
   <label>合同签订日期<el-date-picker v-model="form.signedOn" type="date" value-format="YYYY-MM-DD" format="YYYY年MM月DD日" placeholder="选择签订日期" :clearable="true" /></label>
   <label>合同金额<input v-model="form.amount" type="number" min="0" step="0.01" required></label>
   <template v-if="activeType==='maintenance'">
    <label>服务开始日期<el-date-picker v-model="form.startsOn" type="date" value-format="YYYY-MM-DD" format="YYYY年MM月DD日" placeholder="选择开始日期" :clearable="false" required /></label>
    <label>服务结束日期<el-date-picker v-model="form.endsOn" type="date" value-format="YYYY-MM-DD" format="YYYY年MM月DD日" placeholder="选择结束日期" :clearable="false" required /></label>
   </template>
   <p v-else class="wide muted">检测、评定、评估及其他合同不填写维保服务期限。</p>
  </div>
  <div v-show="step===2" class="wizard-fields" data-wizard-step="2">
   <FileDropzone v-if="can('file:upload')" class="wide" label="合同附件" :model-value="files" :multiple="true" :max-files="10" accept=".pdf,.doc,.docx,.jpg,.jpeg,.png" hint="最多10个文件；单文件默认25 MB，DOCX最大100 MB" :max-bytes="25*1024*1024" :file-limit="file=>file.name.toLowerCase().endsWith('.docx')?100*1024*1024:25*1024*1024" :disabled="busy" @update:model-value="updateFiles"/>
   <label v-if="can('project:read')" class="wide">关联已有项目（选填）<select v-model="form.existingProjectId" :disabled="!form.customerId"><option value="">不选择，自动建立同编号项目</option><option v-for="p in availableProjects" :key="p.id" :value="String(p.id)">{{p.project_no}} · {{p.name}}</option></select></label>
   <p class="wide muted">新合同默认自动创建对应项目，项目编号与合同编号一致。维保年度续签可以选择原项目，沿用原项目编号并保留历史合同。</p>
   <button type="submit" class="primary-button" :disabled="busy">保存合同</button>
  </div>
 </form></template>
</FormDialog>
<FormDialog v-if="customerCreateOpen" title="快速新建甲方" :steps="['甲方资料']" :busy="creatingRelated" :error="relatedError" @clear-error="relatedError=''" auto-open @close="customerCreateOpen=false">
 <template #default="{step,finish}"><form @submit.prevent="finish(createCustomer)"><div v-show="step===0" class="wizard-fields" data-wizard-step="0">
  <label>甲方名称<input v-model.trim="newCustomer.name" required maxlength="240"></label>
  <label>统一社会信用代码（选填）<input v-model.trim="newCustomer.creditCode" maxlength="32"></label>
  <label>客户类型<select v-model="newCustomer.customerType"><option value="enterprise">企业</option><option value="government">政府/事业单位</option><option value="individual">个人</option></select></label>
  <label>联系电话<input v-model.trim="newCustomer.phone" maxlength="40"></label>
  <label class="wide">地址<input v-model.trim="newCustomer.address" maxlength="500"></label>
  <button type="submit" class="primary-button">创建并选中甲方</button>
 </div></form></template>
</FormDialog>
<FormDialog v-if="partnerCreateOpen" title="快速新建中间人" :steps="['基本信息']" :busy="creatingRelated" :error="relatedError" @clear-error="relatedError=''" auto-open @close="partnerCreateOpen=false">
 <template #default="{step,finish}"><form @submit.prevent="finish(createPartner)"><div v-show="step===0" class="wizard-fields" data-wizard-step="0">
  <label>姓名<input v-model.trim="newPartner.name" required maxlength="240"></label>
  <label>联系方式<input v-model.trim="newPartner.contact" maxlength="240" placeholder="电话、手机或微信"></label>
  <label>地区<input v-model.trim="newPartner.region" maxlength="160"></label>
  <button type="submit" class="primary-button">创建并选中中间人</button>
 </div></form></template>
</FormDialog>
<ModalDialog v-if="selected" :title="selected.contract_no+' · 合同详情'" :origin="origin" wide :busy="busy" @close="selected=null">
 <div class="contract-detail">
  <div class="contract-detail-head"><div><h3>{{selected.title}}</h3><p>{{serviceNames[selected.service_type]||selected.service_type}} · {{selected.customer_name}}</p></div><button v-if="can('contract:write')" type="button" class="secondary-button" @click="beginEdit">编辑合同</button></div>
  <dl class="contract-fields">
   <div><dt>系统合同编号</dt><dd>{{selected.contract_no}}</dd></div><div><dt>其他编号</dt><dd>{{selected.external_contract_no||'—'}}</dd></div>
   <div><dt>甲方</dt><dd>{{selected.customer_name}}</dd></div><div><dt>中间人</dt><dd>{{selected.partners?.length?selected.partners.map(p=>p.name).join('、'):'公司自主签订'}}</dd></div>
   <div><dt>合同签订日期</dt><dd>{{date(selected.signed_on)}}</dd></div><div><dt>合同金额</dt><dd>{{money(selected.amount)}}</dd></div>
   <div v-if="selected.service_type==='maintenance'"><dt>服务开始日期</dt><dd>{{date(selected.starts_on)}}</dd></div>
   <div v-if="selected.service_type==='maintenance'"><dt>服务结束日期</dt><dd>{{date(selected.ends_on)}}</dd></div>
   <div><dt>合同状态</dt><dd>{{statusNames[selected.status]||selected.status}}</dd></div>
  </dl>
  <section class="contract-detail-section"><div class="contract-detail-section-head"><h4>合同附件</h4><span>{{selected.files?.length||0}} 个</span></div>
   <div v-if="selected.files?.length" class="contract-file-list">
    <div v-for="f in selected.files" :key="f.id" class="contract-file-row"><span>{{f.original_name}}</span><button v-if="can('file:read')" type="button" class="secondary-button" @click="download(f)">下载</button></div>
   </div><p v-else class="muted">暂无合同附件。</p>
  </section>
  <section class="contract-detail-section"><div class="contract-detail-section-head"><h4>关联合同项目</h4><span>{{selected.projects?.length||0}} 个</span></div>
   <div v-if="selected.projects?.length" class="contract-project-list">
    <button v-for="p in selected.projects" :key="p.id" type="button" class="contract-project-row" @click="openProject(p)"><b>{{p.project_no}}</b><span>{{p.name}}</span><small>{{p.status}}</small></button>
   </div><p v-else class="muted">暂无关联项目。</p>
  </section>
  <div class="contract-detail-actions"><button v-if="can('contract:write')" type="button" class="secondary-button" @click="emit('navigate','partner-maintenance','links',selected.id)">管理合同关联与附件</button></div>
 </div>
</ModalDialog>
<FormDialog v-if="editOpen" title="编辑合同资料" :steps="['合同信息','签订日期与金额']" :busy="busy" :error="error" @clear-error="error=''" save-label="保存修改" auto-open @close="closeEdit">
 <template #default="{step,finish}"><form @submit.prevent="finish(saveEdit)">
  <div v-show="step===0" class="wizard-fields" data-wizard-step="0">
   <label>系统合同编号<input :value="selected?.contract_no||''" disabled></label>
   <label>其他编号（选填）<input v-model.trim="editForm.externalContractNo" maxlength="160"></label>
   <label class="wide">合同名称<input v-model.trim="editForm.title" required maxlength="240"></label>
  </div>
  <div v-show="step===1" class="wizard-fields" data-wizard-step="1">
   <label>合同签订日期<el-date-picker v-model="editForm.signedOn" type="date" value-format="YYYY-MM-DD" format="YYYY年MM月DD日" placeholder="选择签订日期" clearable /></label>
   <label>合同金额<input v-model="editForm.amount" type="number" min="0" step="0.01" required></label>
   <template v-if="selected?.service_type==='maintenance'">
    <label>服务开始日期<el-date-picker v-model="editForm.startsOn" type="date" value-format="YYYY-MM-DD" format="YYYY年MM月DD日" placeholder="选择开始日期" :clearable="false" /></label>
    <label>服务结束日期<el-date-picker v-model="editForm.endsOn" type="date" value-format="YYYY-MM-DD" format="YYYY年MM月DD日" placeholder="选择结束日期" :clearable="false" /></label>
   </template>
   <label>合同状态<select v-model="editForm.status"><option value="draft">草稿</option><option value="pending">待生效</option><option value="active">履行中</option><option value="completed">已完成</option><option value="terminated">已终止</option></select></label>
   <button type="submit" class="primary-button" :disabled="busy">保存合同资料</button>
  </div>
 </form></template>
</FormDialog>
</template>
<style scoped>
.contract-directory{display:grid;gap:18px;min-width:0}
.contract-select-with-action{display:flex;align-items:center;gap:9px}.contract-related-select{flex:1;min-width:0;border:1px solid var(--erp-border);border-radius:10px;background:var(--erp-surface);color:var(--erp-text);padding:11px 12px;font:inherit}
.contract-top{display:flex;align-items:center;justify-content:space-between;gap:16px;flex-wrap:wrap}
.contract-category-tabs{display:flex;align-items:center;gap:7px;flex-wrap:wrap}
.contract-category-tabs button{border:1px solid transparent;border-radius:11px;background:transparent;color:var(--erp-muted);font-size:13px;font-weight:650;padding:10px 17px;cursor:pointer;transition:background .18s,color .18s,border-color .18s}
.contract-category-tabs button:hover{background:var(--erp-subtle);color:var(--erp-text)}
.contract-category-tabs button.active{background:var(--erp-accent-soft);color:var(--erp-accent);border-color:color-mix(in srgb,var(--erp-accent) 22%,transparent)}
.contract-tools{display:flex;align-items:center;gap:9px;flex-wrap:wrap;margin-left:auto}
.contract-tools input{min-width:240px;border:1px solid var(--erp-border);border-radius:10px;background:var(--erp-surface);color:var(--erp-text);padding:10px 12px;font-size:13px}
.contract-table-panel{padding:22px;display:grid;gap:15px;min-height:calc(100dvh - 185px);align-content:start}
.contract-table-heading{display:flex;justify-content:space-between;align-items:center;gap:12px}.contract-table-heading h3{font-size:17px;margin:0}
.contract-table{width:100%;min-width:1120px;border-collapse:separate;border-spacing:0}
.contract-table th,.contract-table td{padding:14px 11px;text-align:left;border-bottom:1px solid var(--erp-border);font-size:12px;white-space:nowrap}
.contract-table th{font-size:11px;color:var(--erp-muted);font-weight:650;background:var(--erp-subtle)}
.contract-table tbody tr:hover{background:var(--erp-subtle)}
.contract-number{font-weight:700;color:var(--erp-accent);font-variant-numeric:tabular-nums}
.contract-title-cell{font-weight:650;max-width:240px;overflow:hidden;text-overflow:ellipsis}
.contract-money{font-variant-numeric:tabular-nums;font-weight:650}
.contract-empty{text-align:center;padding:70px 16px;color:var(--erp-muted)}.contract-empty p{font-size:12px}
.contract-detail{padding:20px 26px 26px}
.contract-detail-head{display:flex;justify-content:space-between;align-items:flex-start;gap:14px;margin-bottom:18px}
.contract-detail-head h3{font-size:18px;margin:0 0 5px}.contract-detail-head p{font-size:12px;color:var(--erp-muted);margin:0}
.contract-fields{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px;margin:0 0 24px}
.contract-fields dt{font-size:11px;color:var(--erp-muted);margin-bottom:5px}.contract-fields dd{font-size:13px;margin:0;overflow-wrap:anywhere}
.contract-detail-section{margin-top:22px}.contract-detail-section-head{display:flex;justify-content:space-between;align-items:center;gap:12px;margin-bottom:10px}.contract-detail-section h4{font-size:13px;margin:0}.contract-detail-section-head span{font-size:11px;color:var(--erp-muted)}
.contract-file-list,.contract-project-list{display:grid;gap:7px}
.contract-file-row,.contract-project-row{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:10px 12px;border:1px solid var(--erp-border);border-radius:10px;background:var(--erp-surface);min-width:0}
.contract-file-row span{font-size:12px;overflow-wrap:anywhere;min-width:0}
.contract-project-row{text-align:left;color:var(--erp-text);cursor:pointer}.contract-project-row:hover{border-color:var(--erp-accent)}
.contract-project-row b{font-size:12px;color:var(--erp-accent);white-space:nowrap}.contract-project-row span{flex:1;font-size:12px}.contract-project-row small{font-size:10px;color:var(--erp-muted)}
.contract-detail-actions{display:flex;justify-content:flex-end;margin-top:20px}
@media(max-width:900px){.contract-top{align-items:stretch}.contract-tools{width:100%;margin-left:0}.contract-tools input{flex:1;min-width:150px}.contract-table-panel{padding:14px}.contract-fields{grid-template-columns:1fr}}
</style>