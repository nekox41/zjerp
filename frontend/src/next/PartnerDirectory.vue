<script setup>
import {computed,onMounted,ref} from 'vue'
import {api} from './api'
import ModalDialog from './ModalDialog.vue'
import FormDialog from './FormDialog.vue'
const props=defineProps({permissions:{type:Array,default:()=>[]}})
const emit=defineEmits(['navigate'])
const can=p=>props.permissions.includes(p)
const canEdit=computed(()=>can('partner:write'))
const rows=ref([]),q=ref(''),loading=ref(false),busy=ref(false),error=ref(''),notice=ref('')
const detail=ref(null),origin=ref(null),editor=ref(null),form=ref({name:'',contact:'',region:''})
const statusLabels={draft:'草稿',pending:'待生效',active:'履行中',completed:'已完成',terminated:'已终止'}
const serviceLabels={maintenance:'维保',fire_inspection:'检测',fire_assessment:'评定',fire_evaluation:'评估',other:'其他服务'}
async function load(){
 loading.value=true;error.value=''
 try{rows.value=await api('/business/partners?q='+encodeURIComponent(q.value))}
 catch(e){error.value=e.message}finally{loading.value=false}
}
async function show(id,event){
 origin.value=event?.currentTarget?.getBoundingClientRect()||null
 try{error.value='';detail.value=await api('/business/partners/'+id)}
 catch(e){error.value=e.message}
}
function blank(){return {name:'',contact:'',region:''}}
function edit(record=null){
 error.value='';notice.value='';form.value=blank()
 if(record)form.value={name:record.name||'',contact:record.contact||'',region:record.region||''}
 editor.value={id:record?.id||null}
}
function close(){if(!busy.value){editor.value=null;form.value=blank()}}
async function save(){
 if(busy.value)return
 busy.value=true;error.value=''
 try{
  const id=editor.value.id
  const result=await api('/business/partners'+(id?'/'+id:''),{method:id?'PUT':'POST',body:JSON.stringify(form.value)})
  if(detail.value?.id===result.id)detail.value=result
  editor.value=null;form.value=blank();await load()
  notice.value=id?'中间人资料已更新':'中间人已创建'
 }catch(e){error.value=e.message}finally{busy.value=false}
}
onMounted(load)
</script>
<template>
<section class="panel partner-page">
 <div class="partner-toolbar"><div><h3>中间人列表 <span class="muted">· {{rows.length}} 人</span></h3><p>记录业务介绍人及合作方的基本联系资料。</p></div>
  <div class="partner-actions">
   <input v-model="q" aria-label="搜索中间人" placeholder="搜索姓名、联系方式或地区" @keyup.enter="load">
   <button type="button" class="secondary-button" :disabled="loading" @click="load">搜索</button>
   <button v-if="canEdit" type="button" class="primary-button" @click="edit()">＋ 新建中间人</button>
  </div>
 </div>
 <p v-if="error&&!editor" class="message error" role="alert">{{error}}</p><p v-if="notice" class="message success" role="status">{{notice}}</p>
 <div class="table-wrap"><table class="partner-table"><thead><tr><th>姓名</th><th>联系方式</th><th>地区</th><th>关联合同</th><th>操作</th></tr></thead>
  <tbody><tr v-for="r in rows" :key="r.id">
   <td><div class="partner-name"><span class="partner-avatar">{{r.name.slice(0,1)}}</span><b>{{r.name}}</b></div></td>
   <td>{{r.contact||'—'}}</td><td>{{r.region||'—'}}</td><td>{{r.contract_count||0}} 份</td>
   <td><button type="button" class="secondary-button" @click="show(r.id,$event)">查看详情</button></td>
  </tr></tbody></table>
 <div v-if="!rows.length" class="partner-empty"><b>{{loading?'正在加载…':'暂无中间人记录'}}</b><p v-if="!loading&&canEdit">点击右上角「新建中间人」添加资料。</p></div></div>
</section>
<ModalDialog v-if="detail" :title="detail.name+' · 中间人档案'" :origin="origin" wide :busy="busy" @close="detail=null">
 <div class="partner-detail">
  <div class="partner-detail-heading"><h4>基本资料</h4><button v-if="canEdit" type="button" class="secondary-button" @click="edit(detail)">编辑资料</button></div>
  <dl class="partner-fields"><div><dt>姓名</dt><dd>{{detail.name}}</dd></div><div><dt>联系方式</dt><dd>{{detail.contact||'—'}}</dd></div><div><dt>地区</dt><dd>{{detail.region||'—'}}</dd></div></dl>
  <div class="partner-contract-heading"><h4>关联合同</h4><span>{{detail.contracts?.length||0}} 份</span></div>
  <div v-if="detail.contracts?.length" class="partner-contracts">
   <article v-for="c in detail.contracts" :key="c.id" class="partner-contract">
    <div class="partner-contract-main"><b>{{c.title}}</b><small>{{c.contract_no}} · {{serviceLabels[c.service_type]||'其他服务'}} · {{statusLabels[c.status]||c.status}}</small></div>
    <div class="partner-contract-meta"><span>合同金额：¥{{Number(c.amount||0).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2})}}</span><span>约定分成：{{c.share_percent}}%</span></div>
    <button v-if="can('contract:read')" type="button" class="secondary-button" @click="emit('navigate',c.id)">查看合同</button>
   </article>
  </div>
  <p v-else class="partner-empty-inline">这个中间人目前还没有绑定合同。</p>
  <p v-if="error&&!editor" class="message error" role="alert">{{error}}</p>
 </div>
</ModalDialog>
<FormDialog v-if="editor" :title="editor.id?'编辑中间人':'新建中间人'" :steps="['基本信息']" :busy="busy" :error="error" @clear-error="error=''" auto-open @close="close">
 <template #default="{step,finish}"><form @submit.prevent="finish(save)">
  <div v-show="step===0" class="wizard-fields" data-wizard-step="0">
   <label>姓名<input v-model.trim="form.name" required maxlength="240" autocomplete="off"></label>
   <label>联系方式<input v-model.trim="form.contact" maxlength="240" placeholder="电话、手机、微信或邮箱"></label>
   <label>地区<input v-model.trim="form.region" maxlength="160" placeholder="例如：杭州市 · 浙江省"></label>
   <button type="submit" class="primary-button">保存</button>
  </div>
 </form></template>
</FormDialog>
</template>
<style scoped>
.partner-page{min-height:calc(100dvh - 142px);padding:24px;display:flex;flex-direction:column;gap:22px}
.partner-toolbar{display:flex;justify-content:space-between;align-items:center;gap:16px;flex-wrap:wrap}.partner-toolbar h3{font-size:19px;margin:0 0 6px}.partner-toolbar p{font-size:13px;color:var(--erp-muted);margin:0}
.partner-actions{display:flex;align-items:center;gap:10px;flex-wrap:wrap}.partner-actions input{border:1px solid var(--erp-border);padding:11px 13px;border-radius:10px;min-width:240px}
.partner-table{width:100%;white-space:nowrap}.partner-table th,.partner-table td{padding:16px 12px;text-align:left}.partner-table tbody tr{transition:background .18s}.partner-table tbody tr:hover{background:var(--erp-subtle)}
.partner-name{display:flex;align-items:center;gap:10px}.partner-avatar{width:34px;height:34px;display:grid;place-items:center;background:var(--erp-accent-soft);color:var(--erp-accent);border-radius:12px;font-weight:700}
.partner-empty{text-align:center;padding:70px 12px;color:var(--erp-muted)}.partner-empty p{font-size:13px}
.partner-detail{padding:20px 26px}.partner-detail-heading,.partner-contract-heading{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-bottom:16px}.partner-detail h4{margin:12px 0;font-size:14px}.partner-contract-heading span{font-size:12px;color:var(--erp-muted)}
.partner-fields{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px;margin:0 0 24px}.partner-fields dt{color:var(--erp-muted);font-size:12px;margin-bottom:5px}.partner-fields dd{margin:0;overflow-wrap:anywhere;font-size:14px;white-space:pre-wrap}
.partner-contracts{display:grid;gap:10px}.partner-contract{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:8px 14px;align-items:center;border:1px solid var(--erp-border);border-radius:12px;padding:13px}.partner-contract-main{display:grid;gap:5px;min-width:0}.partner-contract-main b{font-size:13px;overflow-wrap:anywhere}.partner-contract-main small{font-size:11px;color:var(--erp-muted)}.partner-contract-meta{display:flex;flex-wrap:wrap;gap:12px;font-size:11px;color:var(--erp-muted)}.partner-contract button{grid-column:2;grid-row:1 / span 2}
.partner-empty-inline{font-size:13px;color:var(--erp-muted);padding:18px;border:1px dashed var(--erp-border);border-radius:12px;text-align:center}
@media(max-width:700px){.partner-page{padding:16px;min-height:65dvh}.partner-fields{grid-template-columns:1fr}.partner-actions input{min-width:120px;max-width:100%}.partner-contract{grid-template-columns:1fr}.partner-contract button{grid-column:auto;grid-row:auto;justify-self:start}}
</style>