<script setup>
import {computed,onMounted,ref} from 'vue'
import {api,downloadFile} from './api'
const props=defineProps({permissions:{type:Array,default:()=>[]}})
const can=p=>props.permissions.includes(p)
const rows=ref([]),busy=ref(false),error=ref(''),notice=ref(''),query=ref(''),file=ref(null)
const form=ref({kind:'system',system:'',name:'',notes:'',cleanConfirmed:false,standardIds:[]})
const standardQuery=ref(''),standardRows=ref([]),standardLabels=ref({})
async function searchStandards(){
 try{const result=await api('/fire-standards?q='+encodeURIComponent(standardQuery.value)+'&limit=100');standardRows.value=result.rows.filter(s=>s.active&&s.verified);for(const s of standardRows.value)standardLabels.value[s.id]=s.standard_no+' '+s.edition+' · '+s.title}catch(e){error.value=e.message}
}
function addStandard(s){if(!form.value.standardIds.includes(s.id))form.value.standardIds.push(s.id)}
function removeStandard(id){form.value.standardIds=form.value.standardIds.filter(x=>x!==id)}
const systems=computed(()=>[...new Set(['火灾自动报警系统','消防给水系统','消火栓系统','自动喷水灭火系统','防烟排烟系统','消防应急照明和疏散指示系统','防火分隔设施','电气火灾监控系统','消防电源监控系统','火灾警报和消防应急广播系统','消防专用电话系统','消防电梯','消防供配电设施','建筑灭火器',...rows.value.map(x=>x.system_name).filter(Boolean)])])
const filtered=computed(()=>rows.value.filter(x=>(x.template_name+' '+x.system_name+' '+x.original_name).includes(query.value.trim())))
async function load(){try{rows.value=await api('/inspection-templates?includeInactive=true')}catch(e){error.value=e.message}}
function choose(e){file.value=e.target.files?.[0]||null;form.value.cleanConfirmed=false}
async function save(){
 if(!file.value||!form.value.cleanConfirmed||busy.value)return
 busy.value=true;error.value='';notice.value=''
 try{
  const data=new FormData();data.append('file',file.value)
  const f=await api('/files',{method:'POST',body:data})
  const result=await api('/inspection-templates',{method:'POST',body:JSON.stringify({...form.value,fileId:f.id})})
  notice.value='模板 v'+result.revisionNo+' 已登记 · '+result.tables+' 个表格 / '+result.rows+' 行'
  file.value=null;form.value.cleanConfirmed=false;await load()
 }catch(e){error.value=e.message}finally{busy.value=false}
}
async function toggle(row){
 if(busy.value)return
 busy.value=true;error.value=''
 try{await api('/inspection-templates/'+row.id+'/active',{method:'PUT',body:JSON.stringify({active:!row.active})});await load()}catch(e){error.value=e.message}finally{busy.value=false}
}
function newVersion(row){form.value={kind:row.template_kind,system:row.system_name,name:row.template_name,notes:row.notes||'',cleanConfirmed:false,standardIds:(row.recommendedStandards||[]).filter(s=>s.active&&s.verified).map(s=>s.id)};for(const s of row.recommendedStandards||[])standardLabels.value[s.id]=s.standard_no+' '+s.edition+' · '+s.title;file.value=null}
async function download(row){try{await downloadFile(row.file_id,row.original_name)}catch(e){error.value=e.message}}
onMounted(async()=>{await load();if(can('report:read'))await searchStandards()})
</script>
<template>
<section class="tpl-page">
 <header><h2>检测报告模板库</h2><p>公共基础模板 + 独立消防系统章节。选择章节组合报告，历史版本不覆盖。</p></header>
 <p v-if="error" role="alert" class="tpl-error">{{error}}</p><p v-if="notice" role="status">{{notice}}</p>
 <div class="tpl-panel">
  <h3>建立 / 更新模板</h3>
  <p>基础 Word 模板保留封面、工程资料、通用报告内容、页眉页脚，在需要插入系统章节的位置单独放置 <code v-text="'{{erp.systemSections}}'"></code>。系统模板仅包含本系统的检测表格和纯文本说明，不含现场照片、签章、链接、自动编号或跨文档部件。复杂合并表格可保留，请从同一基础 Word 模板建立章节，确保样式一致。标准要求由公司人工确认，检测结果与结论不能预填旧项目数据。</p>
  <form v-if="can('file:upload')" @submit.prevent="save" class="tpl-form">
   <label>模板类型<select v-model="form.kind"><option value="base">基础报告</option><option value="system">系统章节</option></select></label>
   <label v-if="form.kind==='system'">消防系统（可新增）<input v-model="form.system" list="tpl-system-list" required maxlength="80"><datalist id="tpl-system-list"><option v-for="s in systems" :key="s" :value="s"/></datalist></label>
   <label>模板名称<input v-model="form.name" required maxlength="150" placeholder="例如：2026 版标准检测表"></label>
   <label>Word 文件<input type="file" accept=".docx" @change="choose" :disabled="busy"></label>
   <label class="wide">版本备注<textarea v-model="form.notes" maxlength="1000" rows="2"></textarea></label>
   <div v-if="can('report:read')" class="wide"><h4>模板参考规范（非强制选用）</h4><p>记录该模板的编制依据；项目仍须独立确认适用版本。仅可关联已核验版本。</p><div v-for="id in form.standardIds" :key="id" class="tpl-actions">{{standardLabels[id]||id}} <button type="button" @click="removeStandard(id)">移除</button></div><div class="tpl-actions"><input v-model="standardQuery" placeholder="规范名称或编号" @keydown.enter.prevent="searchStandards"><button type="button" @click="searchStandards">搜索规范</button></div><details><summary>已核验候选 {{standardRows.length}}</summary><div v-for="s in standardRows" :key="s.id" class="tpl-actions"><small>{{s.standard_no}} {{s.edition}} · {{s.title}}</small><button type="button" :disabled="form.standardIds.includes(s.id)" @click="addStandard(s)">关联</button></div></details></div>
   <label class="wide check"><input type="checkbox" v-model="form.cleanConfirmed">已人工确认清除旧项目信息、检测部位、结果、判定、照片和签署等不应复用的内容。</label>
   <button class="primary" :disabled="busy||!file||!form.cleanConfirmed">保存新版本</button>
  </form><p v-else>当前角色没有上传文件权限，仅能维护已存在模板的启用状态。</p>
 </div>
 <div class="tpl-panel"><h3>模板版本</h3><input v-model="query" placeholder="搜索系统、模板、文件名称"><p v-if="!filtered.length">暂无模板，请先准备空白 Word 原件。</p>
  <article v-for="t in filtered" :key="t.id"><div><b>{{t.system_name||'公共基础'}} · {{t.template_name}} · v{{t.revision_no}}</b><small>{{t.original_name}} · {{(t.byte_size/1024).toFixed(0)}} KB · {{t.active?'启用':'停用'}}</small><small>{{t.notes||'无版本备注'}} · {{t.sha256.slice(0,16)}}</small><small v-if="t.recommendedStandards?.length">参考规范：{{t.recommendedStandards.map(s=>s.standard_no+' '+s.edition+(s.active?'':'（停用）')).join('、')}}</small></div><div class="tpl-actions"><button v-if="can('file:read')" @click="download(t)">下载模板</button><button @click="newVersion(t)">新增修订</button><button :disabled="busy" @click="toggle(t)">{{t.active?'停用':'启用'}}</button></div></article>
 </div>
</section>
</template>
<style scoped>
.tpl-page{display:grid;gap:16px;color:#293d59}.tpl-page h2,.tpl-page h3,.tpl-page p{margin:0}.tpl-page p,.tpl-page small{font-size:12px;line-height:1.7;color:#64748b}.tpl-panel{display:grid;gap:12px;background:white;border:1px solid #e1e7ef;padding:18px;border-radius:12px}.tpl-form{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}.tpl-form label{display:grid;gap:5px;font-size:12px;color:#52647b}.tpl-form .wide{grid-column:1/-1}.tpl-form .check{display:flex;align-items:center;gap:8px}.tpl-page input:not([type=checkbox]),.tpl-page select,.tpl-page textarea{box-sizing:border-box;min-width:0;width:100%;border:1px solid #dfe6ef;border-radius:7px;padding:8px;background:white;font:inherit}.tpl-page article{display:flex;justify-content:space-between;gap:12px;border-top:1px solid #e9eef5;padding:12px 0}.tpl-page article div:first-child{display:grid;gap:4px}.tpl-actions{display:flex;flex-wrap:wrap;gap:6px;align-items:center}.tpl-page button{padding:8px 10px;border:1px solid #dfe6ef;border-radius:7px;background:white;color:#405675;cursor:pointer}.tpl-page button.primary{background:#315fca;color:white}.tpl-page button:disabled{opacity:.5}.tpl-page .tpl-error{color:#a33}@media(max-width:700px){.tpl-form{grid-template-columns:1fr}.tpl-page article{flex-direction:column}}
</style>
