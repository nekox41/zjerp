<script setup>
import {computed,onMounted,ref} from 'vue'
import {api,downloadEmployeeDocument} from './api'
import FormDialog from './FormDialog.vue'
import ModalDialog from './ModalDialog.vue'
import FileDropzone from './FileDropzone.vue'
const props=defineProps({permissions:{type:Array,default:()=>[]},destination:Object})
const canEdit=computed(()=>props.permissions.includes('employee:write'))
const rows=ref([]),q=ref(''),type=ref(''),loading=ref(false),busy=ref(false),error=ref(''),notice=ref('')
const detail=ref(null),origin=ref(null),editor=ref(null),form=ref({})
const labels={operator:'操作员',engineer:'工程师'},genders={male:'男',female:'女'}
const blank=()=>({fullName:'',gender:'',age:'',employeeType:'operator',idNumber:'',phone:'',certificateNo:'',extraFields:[],documents:[],certificateFiles:[],identityFiles:[]})
async function load(){
 loading.value=true;error.value=''
 try{rows.value=await api('/business/employees?q='+encodeURIComponent(q.value)+'&type='+encodeURIComponent(type.value))}
 catch(e){error.value=e.message}finally{loading.value=false}
}
async function show(id,event){
 origin.value=event?.currentTarget?.getBoundingClientRect()||null
 try{error.value='';detail.value=await api('/business/employees/'+id)}catch(e){error.value=e.message}
}
function edit(record=null){
 error.value='';notice.value=''
 form.value=blank()
 if(record){Object.assign(form.value,{fullName:record.full_name,gender:record.gender||'',age:record.age??'',employeeType:record.employee_type,idNumber:record.id_number||'',phone:record.phone||'',certificateNo:record.certificate_no||'',extraFields:(record.extra_fields||[]).map(x=>({...x})),documents:[...(record.documents||[])]})}
 editor.value={id:record?.id||null}
}
function close(){if(!busy.value){editor.value=null;form.value=blank()}}
function addExtra(){if(form.value.extraFields.length<50)form.value.extraFields.push({key:'',value:''})}
function existing(kind){return form.value.documents.filter(d=>d.document_type===kind)}
function removeExisting(id){form.value.documents=form.value.documents.filter(d=>d.id!==id)}
async function save(){
 if(busy.value)return
 busy.value=true;error.value=''
 try{
  const f=form.value,keys=new Set()
  const extras=f.extraFields.map(x=>{
   const key=x.key.trim();if(!key)throw new Error('请填写自定义资料名称')
   if(keys.has(key.toLowerCase()))throw new Error('自定义资料名称不能重复')
   keys.add(key.toLowerCase());return {key,value:x.value}
  })
  const payload={fullName:f.fullName,gender:f.gender||null,age:f.age===''?null:f.age,employeeType:f.employeeType,idNumber:f.idNumber,phone:f.phone,certificateNo:f.certificateNo,extraFields:extras,keepDocumentIds:f.documents.map(d=>d.id)}
  for(const [kind,key] of [['certificate','certificateFiles'],['identity','identityFiles']]){
   if(existing(kind).length+f[key].length>10)throw new Error('每类证件最多上传10个附件')
   for(const file of f[key])if(file.size>25*1024*1024)throw new Error('单个证件附件不能超过25MB')
  }
  const fd=new FormData();fd.append('profile',JSON.stringify(payload))
  for(const file of f.certificateFiles)fd.append('certificateFiles',file)
  for(const file of f.identityFiles)fd.append('identityFiles',file)
  const id=editor.value.id
  const result=await api('/business/employees'+(id?'/'+id:''),{method:id?'PUT':'POST',body:fd})
  if(detail.value?.id===result.id)detail.value=result
  editor.value=null;form.value=blank();await load();notice.value=id?'档案已更新':'员工已创建'
 }catch(e){error.value=e.message}finally{busy.value=false}
}
async function download(doc){
 try{error.value='';await downloadEmployeeDocument(detail.value.id,doc.id,doc.original_name)}catch(e){error.value=e.message}
}
onMounted(async()=>{await load();if(props.destination?.id)await show(props.destination.id)})
</script>
<template>
<section class="panel employee-page">
 <div class="employee-toolbar"><div><h3>员工列表 <span class="muted">· {{rows.length}} 人</span></h3><p>现场操作员与工程师的制文资料档案。</p></div>
 <div class="employee-actions">
  <select v-model="type" aria-label="员工类型" @change="load"><option value="">全部类型</option><option value="operator">操作员</option><option value="engineer">工程师</option></select>
  <input v-model="q" aria-label="搜索员工" placeholder="搜索姓名、手机或证书编号" @keyup.enter="load">
  <button type="button" class="secondary-button" :disabled="loading" @click="load">搜索</button>
  <button v-if="canEdit" type="button" class="primary-button" @click="edit()">＋ 新建员工</button>
 </div></div>
 <p v-if="error&&!editor" class="message error" role="alert">{{error}}</p><p v-if="notice" class="message success" role="status">{{notice}}</p>
 <div class="table-wrap"><table class="employee-table"><thead><tr><th>姓名</th><th>性别</th><th>年龄</th><th>类型</th><th>手机号码</th><th>证书编号</th><th>操作</th></tr></thead>
 <tbody><tr v-for="r in rows" :key="r.id"><td><div class="employee-name"><span class="employee-avatar">{{r.full_name.slice(0,1)}}</span><b>{{r.full_name}}</b></div></td><td>{{genders[r.gender]||'—'}}</td><td>{{r.age??'—'}}</td><td><span class="employee-type" :class="{engineer:r.employee_type==='engineer'}">{{labels[r.employee_type]}}</span></td><td>{{r.phone||'—'}}</td><td>{{r.certificate_no||'—'}}</td><td><button type="button" class="secondary-button" @click="show(r.id,$event)">查看详情</button></td></tr></tbody></table>
 <div v-if="!rows.length" class="employee-empty"><b>{{loading?'正在加载…':'暂无员工记录'}}</b><p v-if="!loading&&canEdit">点击右上角「新建员工」添加第一份档案。</p></div></div>
</section>
<ModalDialog v-if="detail" :title="detail.full_name+' · 员工档案'" :origin="origin" wide :busy="busy" @close="detail=null">
 <div class="employee-detail">
 <div class="employee-detail-heading"><h4>基本资料</h4><button v-if="canEdit" type="button" class="secondary-button" @click="edit(detail)">编辑档案</button></div>
 <dl class="employee-fields"><div><dt>姓名</dt><dd>{{detail.full_name}}</dd></div><div><dt>性别</dt><dd>{{genders[detail.gender]||'—'}}</dd></div><div><dt>年龄</dt><dd>{{detail.age??'—'}}</dd></div><div><dt>类型</dt><dd>{{labels[detail.employee_type]}}</dd></div>
 <div><dt>身份证号码</dt><dd>{{detail.id_number||'—'}}</dd></div><div><dt>手机号码</dt><dd>{{detail.phone||'—'}}</dd></div><div><dt>证书编号</dt><dd>{{detail.certificate_no||'—'}}</dd></div></dl>
  <div v-for="[kind,title] in [['certificate','证书附件'],['identity','身份证附件']]" :key="kind" class="employee-docs"><h4>{{title}}</h4><p v-if="!detail.documents?.some(d=>d.document_type===kind)" class="muted">暂无附件</p><div v-for="doc in detail.documents?.filter(d=>d.document_type===kind)" :key="doc.id" class="employee-document"><span>{{doc.original_name}}</span><button type="button" class="secondary-button" @click="download(doc)">下载原件</button></div></div>
  <h4>其他资料</h4><dl v-if="detail.extra_fields?.length" class="employee-fields"><div v-for="x in detail.extra_fields" :key="x.key"><dt>{{x.key}}</dt><dd>{{x.value||'—'}}</dd></div></dl><p v-else class="muted">暂无自定义资料</p>

 <p v-if="error&&!editor" class="message error" role="alert">{{error}}</p>
 </div>
</ModalDialog>
<FormDialog v-if="editor" :title="editor.id?'编辑员工':'新建员工'" :steps="['基本信息','证书与身份证附件','其他资料']" :busy="busy" :error="error" @clear-error="error=''" auto-open @close="close">
 <template #default="{step,finish}"><form @submit.prevent="finish(save)">
 <div v-show="step===0" class="wizard-fields" data-wizard-step="0">
  <label>姓名<input v-model.trim="form.fullName" required maxlength="200"></label>
  <label>性别<select v-model="form.gender"><option value="">未填写</option><option value="male">男</option><option value="female">女</option></select></label>
  <label>年龄<input v-model="form.age" type="number" min="0" max="120" step="1" placeholder="周岁"></label>
  <label>类型<select v-model="form.employeeType" required><option value="operator">操作员</option><option value="engineer">工程师</option></select></label>
  <label>身份证号码<input v-model.trim="form.idNumber" maxlength="40" autocomplete="off"></label>
  <label>手机号码<input v-model.trim="form.phone" type="tel" maxlength="40"></label>
 </div>
 <div v-show="step===1" class="wizard-fields" data-wizard-step="1">
  <label class="wide">证书编号<input v-model.trim="form.certificateNo" maxlength="120"></label>
  <section v-for="[kind,key,title] in [['certificate','certificateFiles','证书附件'],['identity','identityFiles','身份证附件']]" :key="kind" class="employee-upload">
   <FileDropzone v-model="form[key]" :label="title" accept=".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png" hint="PDF / JPG / PNG · 单文件最大 25 MB" multiple :max-files="10" :existing-count="existing(kind).length" :disabled="busy"/>
   <div v-for="doc in existing(kind)" :key="doc.id" class="employee-document"><span>{{doc.original_name}}</span><button type="button" class="secondary-button" @click="removeExisting(doc.id)">移除</button></div>
  </section><p class="muted">支持 PDF、JPG、PNG，单文件最大 25 MB；可上传身份证正反面及多个证书文件。</p>
 </div>
 <div v-show="step===2" class="wizard-fields" data-wizard-step="2">
  <p class="muted">按文件制作需要添加额外资料，例如学历、专业、证书有效期。无需填写时可以直接保存。</p>
  <div v-for="(x,i) in form.extraFields" :key="i" class="employee-extra"><label>名称<input v-model.trim="x.key" required maxlength="80" placeholder="例如：学历"></label><label>内容<input v-model="x.value" maxlength="4000" placeholder="例如：本科"></label><button type="button" class="secondary-button" @click="form.extraFields.splice(i,1)">删除</button></div>
  <button type="button" class="secondary-button wide" :disabled="form.extraFields.length>=50" @click="addExtra">＋ 添加自定义资料</button>
  <button type="submit" class="primary-button">保存</button>
 </div>
 </form></template>
</FormDialog>
</template>
<style scoped>
.employee-page{min-height:calc(100dvh - 142px);padding:24px;display:flex;flex-direction:column;gap:22px}.employee-toolbar{display:flex;justify-content:space-between;align-items:center;gap:16px;flex-wrap:wrap}.employee-toolbar h3{font-size:19px;margin:0 0 6px}.employee-toolbar p{font-size:13px;color:var(--erp-muted);margin:0}.employee-actions{display:flex;align-items:center;gap:10px;flex-wrap:wrap}.employee-actions input,.employee-actions select{border:1px solid var(--erp-border);padding:11px 13px;border-radius:10px}.employee-actions input{min-width:210px}
.employee-table{width:100%;white-space:nowrap}.employee-table th,.employee-table td{padding:16px 12px;text-align:left}.employee-table tbody tr{transition:background .18s}.employee-table tbody tr:hover{background:var(--erp-subtle)}.employee-name{display:flex;align-items:center;gap:10px}.employee-avatar{width:34px;height:34px;display:grid;place-items:center;background:var(--erp-accent-soft);color:var(--erp-accent);border-radius:12px;font-weight:700}.employee-type{background:var(--erp-subtle);border-radius:7px;padding:5px 9px;font-size:12px}.employee-type.engineer{color:var(--erp-accent);background:var(--erp-accent-soft)}
.employee-empty{text-align:center;padding:70px 12px;color:var(--erp-muted)}.employee-empty p{font-size:13px}.employee-detail{padding:20px 26px}.employee-detail-heading{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-bottom:16px}.employee-detail h4{margin:12px 0}.employee-fields{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px;margin:0 0 24px}.employee-fields dt{color:var(--erp-muted);font-size:12px;margin-bottom:5px}.employee-fields dd{margin:0;overflow-wrap:anywhere;font-size:14px;white-space:pre-wrap}.employee-docs,.employee-upload{padding:14px;border:1px solid var(--erp-border);border-radius:12px;margin-bottom:12px}.employee-document{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:8px 0;border-bottom:1px solid var(--erp-border);font-size:13px}.employee-document:last-child{border-bottom:0}.employee-document span{overflow-wrap:anywhere;min-width:0}.employee-document button{flex-shrink:0}.employee-extra{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1.5fr) auto;align-items:end;gap:8px}.employee-extra button{margin-bottom:2px}
@media(max-width:700px){.employee-page{padding:16px;min-height:65dvh}.employee-fields{grid-template-columns:1fr}.employee-actions input{min-width:120px;max-width:100%}.employee-extra{grid-template-columns:1fr}}
</style>
