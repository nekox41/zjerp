<script setup>
import FormDialog from './FormDialog.vue'
import { computed, onMounted, onBeforeUnmount, ref, nextTick } from 'vue'
import { api, fetchPdf, downloadFile } from './api'
import { systemOptions } from '../utils/wbcard'
const props=defineProps({projectId:{type:Number,required:true},contractId:{type:Number,required:true},permissions:{type:Array,default:()=>[]}})
const can=p=>props.permissions.includes(p)
const endpoint=computed(()=>'/maintenance/projects/'+props.projectId+'/contracts/'+props.contractId)
const data=ref(null),contacts=ref([]),materials=ref([]),busy=ref(false),error=ref(''),notice=ref('')
const form=ref({code:'',company:'河南众基消防技术有限公司',leader:'',phone:'',startsOn:'',endsOn:'',systems:''})
const systems=ref([]),contactId=ref(''),materialId=ref(''),previewUrl=ref('')
const contactDialog=ref(null),materialDialog=ref(null)
const contact=ref({id:null,name:'',phone:'',active:true}),material=ref({id:null,name:'',notes:'',active:true}),materialFile=ref(null)
let disposed=false
function clearPreview(){if(previewUrl.value)URL.revokeObjectURL(previewUrl.value);previewUrl.value=''}
onBeforeUnmount(()=>{disposed=true;clearPreview()})
async function refresh(){
  const [d,c,m]=await Promise.all([api(endpoint.value),api('/maintenance/contacts'),api('/maintenance/materials')])
  data.value=d;contacts.value=c;materials.value=m
}
onMounted(async()=>{try{await refresh();Object.assign(form.value,{leader:data.value.leader||'',phone:data.value.phone||'',startsOn:data.value.starts_on||'',endsOn:data.value.ends_on||''})}catch(e){error.value=e.message}})
async function run(fn){if(busy.value)return;busy.value=true;error.value='';notice.value='';try{await fn()}catch(e){error.value=e.message}finally{busy.value=false}}
function selectContact(){const c=contacts.value.find(x=>String(x.id)===String(contactId.value));if(c)form.value.phone=c.phone}
function selectSystems(){form.value.systems=systems.value.join('、')}
function validate(){if(!form.value.company.trim()||!form.value.leader.trim()||!form.value.phone.trim()||!form.value.systems.trim()||!form.value.startsOn||!form.value.endsOn||form.value.endsOn<form.value.startsOn)throw new Error('请填写完整维保卡信息并检查起止日期')}
function show(blob){if(disposed)return;clearPreview();previewUrl.value=URL.createObjectURL(blob)}
async function preview(){await run(async()=>{validate();show(await fetchPdf(endpoint.value+'/preview',{method:'POST',body:JSON.stringify(form.value)}));notice.value='未保存预览；自动编号以正式生成时为准'})}
async function save(){await run(async()=>{validate();const card=await api(endpoint.value+'/cards',{method:'POST',body:JSON.stringify(form.value)});form.value.code='';clearPreview();await refresh();notice.value='已归档维保卡 '+card.code;if(can('file:read'))show(await fetchPdf('/files/'+card.file_id+'/download'))})}
async function openPdf(id){await run(async()=>show(await fetchPdf('/files/'+id+'/download')))}
async function setStatus(card,key){await run(async()=>{await api('/maintenance/cards/'+card.id+'/status',{method:'PUT',body:JSON.stringify({[key]:!card[key]})});await refresh();notice.value='状态已更新'})}
async function editMaterial(m,event){const origin=event.currentTarget.getBoundingClientRect();material.value={...m,active:!!m.active};materialFile.value=null;await nextTick();materialDialog.value?.open(origin)}
function resetContact(){contact.value={id:null,name:'',phone:'',active:true}}
async function saveContact(){await run(async()=>{const c=contact.value;await api('/maintenance/contacts'+(c.id?'/'+c.id:''),{method:c.id?'PUT':'POST',body:JSON.stringify(c)});resetContact();await refresh();notice.value='联系人已保存'})}
function resetMaterial(){material.value={id:null,name:'',notes:'',active:true};materialFile.value=null}
async function saveMaterial(){await run(async()=>{const m=material.value;if(m.id){await api('/maintenance/materials/'+m.id,{method:'PUT',body:JSON.stringify(m)})}else{if(!materialFile.value)throw new Error('请选择 PDF 原件');const fd=new FormData();fd.append('file',materialFile.value);const f=await api('/files',{method:'POST',body:fd});await api('/maintenance/materials',{method:'POST',body:JSON.stringify({name:m.name,notes:m.notes,fileId:f.id})})}resetMaterial();await refresh();notice.value='资料已保存，原始 PDF 未修改'})}
async function archive(){await run(async()=>{await api(endpoint.value+'/materials',{method:'POST',body:JSON.stringify({materialId:materialId.value})});materialId.value='';await refresh();notice.value='已归档到本项目及合同'})}
async function download(id,name){await run(()=>downloadFile(id,name))}
</script>
<template>
<section class="md-workspace">
  <h3>年度维保卡与附带资料</h3>
  <p v-if="error" class="md-error" role="alert">{{error}}</p><p v-if="notice" class="md-notice" role="status">{{notice}}</p>
  <template v-if="data">
    <p><b>{{data.project_name}}</b> · {{data.contract_no}} · {{data.starts_on||'—'}} 至 {{data.ends_on||'—'}}</p>
    <FormDialog v-if="can('maintenance:write')" title="制作维保卡" :steps="['卡片与人员','期限与维保内容']" :busy="busy" :error="error" @clear-error="error=''" save-label="生成归档">
<template #default="{step,finish}"><form class="md-form" @submit.prevent="finish(()=>save())">
<div v-show="step===0" data-wizard-step="0" class="wizard-fields">
<label>维保卡编号<input v-model="form.code" maxlength="100" :placeholder="'自动编号 '+data.nextCode"></label>
<label>维保机构<input v-model="form.company" maxlength="255" required></label>
<label>项目负责人<input v-model="form.leader" maxlength="100" required></label>
<label>联系人资源<select v-model="contactId" @change="selectContact"><option value="">手动填写电话</option><option v-for="c in contacts.filter(x=>x.active)" :key="c.id" :value="c.id">{{c.name}} · {{c.phone}}</option></select></label>
<label>联系电话<input v-model="form.phone" maxlength="255" required></label>
</div>
<div v-show="step===1" data-wizard-step="1" class="wizard-fields">
<label>开始日期<input v-model="form.startsOn" type="date" required></label>
<label>结束日期<input v-model="form.endsOn" type="date" required></label>
<label class="wide">维护系统<select v-model="systems" multiple @change="selectSystems"><option v-for="s in systemOptions" :key="s">{{s}}</option></select></label>
<label class="wide">维护保养内容<textarea v-model="form.systems" rows="3" maxlength="4000" required></textarea></label>
<div class="wide md-actions"><button type="button" class="secondary-button" :disabled="busy" @click="preview">预览 PDF</button><button class="primary-button" :disabled="busy||data.status!=='active'">生成并归档</button></div>
<small v-if="data.status!=='active'" class="wide">正式生成仅支持履行中的合同；可先预览。</small>
<div v-if="previewUrl" class="md-preview"><a :href="previewUrl" target="_blank" rel="noopener">新窗口预览 / 打印</a><iframe :src="previewUrl" title="维保卡 PDF 预览" style="width:100%;height:380px;border:0"></iframe></div>
</div>
</form></template></FormDialog>
    <div v-if="previewUrl" class="md-preview"><div class="md-actions"><a :href="previewUrl" target="_blank" rel="noopener">新窗口预览 / 打印（A4 横向）</a><button type="button" class="secondary-button" @click="clearPreview">关闭预览</button></div><iframe :src="previewUrl" title="维保卡或附带资料 PDF 预览"></iframe></div>
    <h4>维保卡生成历史</h4>
    <p v-if="!data.cards.length">暂无生成记录</p>
    <article v-for="c in data.cards" :key="c.id" class="md-record"><b>{{c.code}} · {{c.project_name}}</b><small>{{c.starts_on}} 至 {{c.ends_on}} · {{c.printed?'已打印':'未打印'}} · {{c.sent?'已邮寄':'未邮寄'}}</small><div class="md-actions">
      <button v-if="can('file:read')" class="secondary-button" :disabled="busy" @click="openPdf(c.file_id)">预览 / 打印</button><button v-if="can('file:read')" class="secondary-button" :disabled="busy" @click="download(c.file_id,c.code+'.pdf')">下载</button>
      <template v-if="can('maintenance:write')"><button class="secondary-button" :disabled="busy" @click="setStatus(c,'printed')">{{c.printed?'撤销打印登记':'登记已打印'}}</button><button class="secondary-button" :disabled="busy" @click="setStatus(c,'sent')">{{c.sent?'撤销邮寄登记':'登记已邮寄'}}</button></template>
    </div></article>
    <h4>本合同及项目附带资料</h4>
    <FormDialog v-if="can('maintenance:write')" title="归档附带资料" :steps="['选择资料']" :busy="busy" :error="error" @clear-error="error=''" save-label="归档">
<template #default="{step,finish}"><form class="md-actions" @submit.prevent="finish(()=>archive())">
<div v-show="step===0" data-wizard-step="0" class="wizard-fields">
<select v-model="materialId" required><option value="">选择资料库原件</option><option v-for="m in materials.filter(x=>x.active&&!data.materials.some(a=>a.material_id===x.id))" :key="m.id" :value="m.id">{{m.name}}</option></select>
<button class="primary-button" :disabled="busy">归档</button>
</div>
</form></template></FormDialog>
    <article v-for="m in data.materials" :key="m.id" class="md-record"><b>{{m.title}}</b><div v-if="can('file:read')" class="md-actions"><button class="secondary-button" :disabled="busy" @click="openPdf(m.file_id)">预览</button><button class="secondary-button" :disabled="busy" @click="download(m.file_id,m.original_name)">下载原件</button></div></article>
    <details><summary>联系人资源管理</summary><article v-for="c in contacts" :key="c.id" class="md-record"><span>{{c.name}} · {{c.phone}} · {{c.active?'启用':'停用'}}</span><button v-if="can('maintenance:write')" class="secondary-button" @click="contact={...c,active:!!c.active};contactDialog?.open($event)">编辑</button></article>
      <FormDialog v-if="can('maintenance:write')" ref="contactDialog" title="维护联系人资源" :steps="['联系人信息']" :busy="busy" :error="error" @clear-error="error=''">
<template #default="{step,finish}"><form class="md-form" @submit.prevent="finish(()=>saveContact())">
<div v-show="step===0" data-wizard-step="0" class="wizard-fields">
<label>姓名<input v-model="contact.name" maxlength="100" required></label>
<label>电话<input v-model="contact.phone" maxlength="50" required></label>
<label v-if="contact.id"><input v-model="contact.active" type="checkbox">启用</label>
<div class="md-actions"><button class="primary-button" :disabled="busy">{{contact.id?'保存联系人':'新增联系人'}}</button><button type="button" class="secondary-button" @click="resetContact">清空</button></div>
</div>
</form></template></FormDialog>
    </details>
    <details><summary>附带资料库管理（独立 PDF，不自动加水印）</summary><article v-for="m in materials" :key="m.id" class="md-record"><b>{{m.name}} · {{m.active?'启用':'停用'}}</b><div class="md-actions"><button v-if="can('file:read')" class="secondary-button" :disabled="busy" @click="download(m.file_id,m.original_name)">下载原件</button><button v-if="can('maintenance:write')" class="secondary-button" @click="editMaterial(m,$event)">编辑</button></div></article>
      <FormDialog v-if="can('maintenance:write')&&(material.id||can('file:upload'))" ref="materialDialog" title="维护附带资料" :steps="['资料与原件']" :busy="busy" :error="error" @clear-error="error=''">
<template #default="{step,finish}"><form class="md-form" @submit.prevent="finish(()=>saveMaterial())">
<div v-show="step===0" data-wizard-step="0" class="wizard-fields">
<label>资料名称<input v-model="material.name" maxlength="200" required></label>
<label>备注<input v-model="material.notes" maxlength="2000"></label>
<label v-if="!material.id">PDF 原件<input type="file" accept="application/pdf,.pdf" required @change="materialFile=$event.target.files?.[0]"></label>
<label v-else><input v-model="material.active" type="checkbox">启用</label>
<div class="md-actions"><button class="primary-button" :disabled="busy">{{material.id?'保存资料信息':'上传并登记资料'}}</button><button type="button" class="secondary-button" @click="resetMaterial">清空</button></div>
</div>
</form></template></FormDialog>
    </details>
  </template>
</section>
</template>
<style scoped>
.md-workspace{display:grid;gap:12px;padding:14px;border:1px solid var(--erp-border-dfe6ef);border-radius:10px;color:var(--erp-fg-405675)}.md-workspace h3,.md-workspace h4,.md-workspace p{margin:0}.md-form{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}.md-form label{display:grid;gap:5px;font-size:12px}.md-form .wide{grid-column:1/-1}.md-form input,.md-form select,.md-form textarea,.md-actions select{min-width:0;padding:8px;border:1px solid var(--erp-border-dfe6ef);border-radius:6px;font:inherit;background:var(--erp-bg-fff)}.md-form select[multiple]{min-height:100px}.md-actions{display:flex;flex-wrap:wrap;align-items:center;gap:8px}.md-record{display:grid;gap:6px;padding:10px;border:1px solid var(--erp-border-e6ebf2);border-radius:8px;font-size:12px}.md-record small{color:var(--erp-fg-667891)}.md-preview iframe{width:100%;height:420px;border:1px solid var(--erp-border-dfe6ef)}.md-error{color:var(--erp-fg-a33)}.md-notice{color:var(--erp-fg-287449)}details{border-top:1px solid var(--erp-border-e6ebf2);padding-top:10px}summary{cursor:pointer;margin-bottom:10px}@media(max-width:600px){.md-form{grid-template-columns:1fr}}
</style>
