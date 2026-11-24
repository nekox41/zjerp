<script setup>
import { computed,ref,watch } from 'vue'
import { api } from './api'
const props=defineProps({customerId:{type:Number,required:true},permissions:{type:Array,default:()=>[]},projects:{type:Array,default:()=>[]},employees:{type:Array,default:()=>[]}})
const emit=defineEmits(['open-project','open-contract'])
const can=p=>props.permissions.includes(p)
const types=computed(()=>[
  {key:'contracts',label:'合同',permission:'contract:read'},
  {key:'projects',label:'项目',permission:'project:read'},
  {key:'visits',label:'技术服务出勤',permission:'service:visit:read'},
  {key:'maintenance',label:'维保出勤',permission:'maintenance:read'},
  {key:'repairs',label:'维修记录',permission:'repair:read'},
  {key:'reports',label:'报告',permission:'report:read'},
  {key:'deliveries',label:'报告交付',permission:'delivery:read'}
].filter(t=>can(t.permission)))
const kind=ref(''),rows=ref([]),offset=ref(0),more=ref(false),loading=ref(false),busy=ref(false),error=ref(''),notice=ref('')
const limit=50
const ownProjects=computed(()=>props.projects.filter(p=>Number(p.customer_id)===props.customerId))
const writeRepair=computed(()=>can('repair:read')&&can('repair:write')&&can('project:read')&&can('employee:read'))
const editor=ref(false),editingId=ref(null)
const initial=()=>({projectId:'',repairDate:new Date().toLocaleDateString('sv-SE'),title:'',description:'',resultSummary:'',employeeIds:[]})
const form=ref(initial())
let sequence=0
async function load(nextOffset=0){
  const seq=++sequence
  rows.value=[];more.value=false;offset.value=nextOffset
  if(!types.value.some(x=>x.key===kind.value)){kind.value=types.value[0]?.key||''}
  if(!kind.value)return
  loading.value=true;error.value=''
  try{
    const r=await api('/business/customers/'+props.customerId+'/history/'+kind.value+'?limit='+limit+'&offset='+nextOffset)
    if(seq!==sequence)return
    rows.value=r.rows;more.value=r.hasMore
  }catch(e){if(seq===sequence)error.value=e.message}
  finally{if(seq===sequence)loading.value=false}
}
function select(key){kind.value=key;editor.value=false;notice.value='';load(0)}
function startRepair(){editingId.value=null;form.value=initial();editor.value=true;error.value=''}
async function editRepair(row){
  const customerId=props.customerId,seq=sequence
  error.value=''
  try{
    const r=await api('/business/customers/'+customerId+'/repairs/'+row.id)
    if(customerId!==props.customerId||seq!==sequence)return
    editingId.value=row.id
    form.value={projectId:r.project_id,repairDate:r.repair_date,title:r.title,description:r.description,resultSummary:r.result_summary||'',employeeIds:r.employeeIds||[]}
    editor.value=true
  }catch(e){error.value=e.message}
}
async function saveRepair(){
  busy.value=true;error.value='';notice.value=''
  const customerId=props.customerId,id=editingId.value
  try{
    const body={...form.value,projectId:Number(form.value.projectId),employeeIds:form.value.employeeIds.map(Number)}
    await api('/business/customers/'+customerId+'/repairs'+(id?'/'+id:''),{method:id?'PUT':'POST',body:JSON.stringify(body)})
    if(customerId!==props.customerId)return
    editor.value=false;notice.value='维修记录已保存';await load(0)
  }catch(e){error.value=e.message}finally{busy.value=false}
}
watch(()=>[props.customerId,types.value.map(x=>x.key).join(',')],()=>{editor.value=false;notice.value='';load(0)},{immediate:true})
const typeLabels={inspection:'现场检测',rectification_review:'整改复核',supplement:'补充采集',maintenance:'维护保养',other:'其他',fire_inspection:'消防检测',fire_assessment:'消防评定',fire_evaluation:'消防评估'}
const statusLabels={draft:'草稿',pending:'待生效',active:'履行中',completed:'已完成',terminated:'已终止',planned:'计划中',in_progress:'进行中',paused:'暂停',cancelled:'已取消',submitted:'已提交',reviewing:'审核中',returned:'已退回',approved:'已审核',issued:'已签发',archived:'已归档',withdrawn:'已撤回',prepared:'待交付',dispatched:'已寄出',delivered:'已签收'}
</script>
<template>
  <section class="customer-history">
    <h4>甲方业务历史</h4>
    <div class="ch-tabs"><button v-for="t in types" :key="t.key" type="button" class="secondary-button" :class="{active:kind===t.key}" @click="select(t.key)">{{t.label}}</button></div>
    <p v-if="!types.length" class="muted">当前账号没有关联业务的查看权限。</p>
    <p v-if="error" class="message error">{{error}}</p><p v-if="notice" class="message success">{{notice}}</p>
    <template v-if="kind==='repairs'">
      <button v-if="writeRepair" class="primary-button" type="button" @click="startRepair">登记维修记录</button>
      <p v-else-if="can('repair:write')" class="muted">登记维修还需要项目、员工和维修记录查看权限。</p>
      <form v-if="editor" class="create-grid compact" @submit.prevent="saveRepair">
        <label>项目<select v-model="form.projectId" :disabled="!!editingId" required><option value="">选择本甲方项目</option><option v-for="p in ownProjects" :key="p.id" :value="p.id">{{p.project_no}} · {{p.name}}</option></select></label>
        <label>维修日期<input v-model="form.repairDate" type="date" required></label>
        <label class="ch-wide">维修标题<input v-model="form.title" required maxlength="240"></label>
        <label class="ch-wide">维修内容<textarea v-model="form.description" required maxlength="10000" rows="3"></textarea></label>
        <label class="ch-wide">维修结果<textarea v-model="form.resultSummary" maxlength="10000" rows="2"></textarea></label>
        <label class="ch-wide">维修人员<select multiple v-model="form.employeeIds" required><option v-for="e in employees.filter(x=>x.status==='active'||form.employeeIds.includes(x.id))" :key="e.id" :value="e.id">{{e.full_name}}</option></select></label>
        <div class="report-actions"><button class="primary-button" :disabled="busy">保存维修记录</button><button type="button" class="secondary-button" @click="editor=false">取消</button></div>
      </form>
    </template>
    <p v-if="loading" class="muted">加载中…</p>
    <p v-else-if="kind&&!rows.length" class="muted">暂无此类记录。</p>
    <div v-for="r in rows" :key="r.id" class="ch-record">
      <div class="ch-row">
        <b>{{r.title||r.name||r.report_no||'维护保养出勤'}}</b>
        <small>{{statusLabels[r.status]||r.status||''}}</small>
      </div>
      <small>{{[r.contract_no,r.project_no,r.report_no,r.project_name,r.visit_date,r.repair_date,typeLabels[r.visit_type||r.service_type]||r.visit_type||r.service_type,r.staff_names].filter(Boolean).join(' · ')}}</small>
      <small v-if="r.starts_on||r.ends_on||r.planned_start||r.planned_end">{{r.starts_on||r.planned_start||'—'}} 至 {{r.ends_on||r.planned_end||'—'}}</small>
      <p v-if="r.summary||r.description||r.notes">{{r.summary||r.description||r.notes}}</p>
      <p v-if="r.result_summary">结果：{{r.result_summary}}</p>
      <small v-if="r.current_version_no||r.version_no">版本 {{r.current_version_no||r.version_no}}</small>
      <small v-if="kind==='deliveries'">{{[r.courier_name,r.tracking_no,r.dispatched_at,r.delivered_at].filter(Boolean).join(' · ')}}</small>
      <div class="ch-actions">
        <button v-if="kind==='contracts'" type="button" class="secondary-button" @click="emit('open-contract',r.id)">合同详情</button>
        <button v-if="can('project:read')&&(kind==='projects'||r.project_id)" type="button" class="secondary-button" @click="emit('open-project',kind==='projects'?r.id:r.project_id)">项目详情</button>
        <button v-if="kind==='repairs'&&writeRepair" type="button" class="secondary-button" @click="editRepair(r)">编辑维修</button>
      </div>
    </div>
    <div v-if="offset>0||more" class="ch-row"><button type="button" class="secondary-button" :disabled="offset===0||loading" @click="load(Math.max(0,offset-limit))">上一页</button><small>第 {{Math.floor(offset/limit)+1}} 页</small><button type="button" class="secondary-button" :disabled="!more||loading" @click="load(offset+limit)">下一页</button></div>
  </section>
</template>
<style scoped>
.customer-history{display:grid;gap:12px;margin-top:20px;border-top:1px solid #e5e7eb;padding-top:16px}.ch-tabs,.ch-actions{display:flex;flex-wrap:wrap;gap:7px}.ch-tabs button.active{border-color:#315fca;color:#315fca;background:#eef3ff}.ch-record{display:grid;gap:6px;padding:12px;border:1px solid #e5e7eb;border-radius:8px}.ch-record p{white-space:pre-wrap;margin:0;font-size:13px}.ch-record small{color:#68758a}.ch-row{display:flex;align-items:center;justify-content:space-between;gap:8px}.ch-wide{grid-column:1/-1}.customer-history select[multiple]{min-height:100px}
</style>
