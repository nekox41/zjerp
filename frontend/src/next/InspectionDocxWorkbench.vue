<script setup>
import { computed,onMounted,ref,watch } from 'vue'
import { api,downloadFile } from './api'
const props=defineProps({reportId:{type:Number,required:true},versionNo:{type:Number,required:true},templateId:{type:Number,required:true},baseTemplateId:{type:Number,default:null},sectionTemplateIds:{type:Array,default:()=>[]},photoQuality:{type:String,default:'print'},batchIds:{type:Array,default:()=>[]},photoMode:{type:String,default:'append'},canDownload:{type:Boolean,default:false}})
const emit=defineEmits(['generated'])
const inspection=ref(null),bindings=ref([]),busy=ref(false),error=ref(''),notice=ref(''),reviewed=ref(false),checkedPlan=ref(''),search=ref(''),field=ref(''),target=ref(''),candidatePage=ref(1)
const endpoint='/service-workflow/reports/'+props.reportId
const filtered=computed(()=>{const q=search.value.trim().toLowerCase();return (inspection.value?.inspection.candidates||[]).filter(c=>!q||c.target.toLowerCase().includes(q)||c.text.toLowerCase().includes(q))})
const maxPages=computed(()=>Math.max(1,Math.ceil(filtered.value.length/50)))
const page=computed(()=>filtered.value.slice((candidatePage.value-1)*50,candidatePage.value*50))
const ready=computed(()=>!!inspection.value&&!inspection.value.inspection.errors.length&&checkedPlan.value===JSON.stringify(bindings.value)&&reviewed.value&&!busy.value)
const labels={general:'工程基本信息',buildings:'建筑单体',facilities:'共用设施',records:'检测明细',instruments:'检测仪器',summaries:'系统判定',conclusion:'最终结论',report:'报告信息'}
function invalidate(){reviewed.value=false;checkedPlan.value=''}
watch(()=>[props.photoMode,props.photoQuality,JSON.stringify(props.batchIds)],()=>{reviewed.value=false})
function chooseCandidate(c){target.value=c.target}
function addBinding(){
 const c=inspection.value?.inspection.candidates.find(x=>x.target===target.value)
 if(!c||!field.value)return
 bindings.value=[...bindings.value.filter(b=>b.target!==c.target),{target:c.target,expectedText:c.text,field:field.value}]
 target.value='';invalidate()
}
function applySuggestions(){
 const suggestions=inspection.value?.inspection.suggestedBindings||[]
 const existing=new Set(bindings.value.map(b=>b.target))
 bindings.value=[...bindings.value,...suggestions.filter(b=>!existing.has(b.target))]
 invalidate()
}
function exportMapping(){
 if(!inspection.value)return
 const payload={format:'erp-inspection-mapping-v1',templateSha256:inspection.value.templateSha256,bindings:bindings.value}
 const url=URL.createObjectURL(new Blob([JSON.stringify(payload,null,2)],{type:'application/json'}))
 const a=document.createElement('a');a.href=url;a.download='inspection-mapping.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000)
}
async function importMapping(event){
 const file=event.target.files?.[0];event.target.value=''
 if(!file||!inspection.value)return
 error.value=''
 try{
  if(file.size>5*1024*1024)throw Error('映射文件超过 5MB')
  const data=JSON.parse(await file.text())
  if(data.format!=='erp-inspection-mapping-v1'||data.templateSha256!==inspection.value.templateSha256||!Array.isArray(data.bindings)||data.bindings.length>20000)throw Error('映射文件格式错误或模板 SHA256 不一致')
  const targets=new Set()
  const candidates=new Map(inspection.value.inspection.candidates.map(c=>[c.target,c.text]))
  for(const b of data.bindings){
   if(typeof b?.target!=='string'||typeof b.expectedText!=='string'||typeof b.field!=='string'||targets.has(b.target)||!candidates.has(b.target)||candidates.get(b.target)!==b.expectedText)throw Error('映射目标、原文或重复行结构不一致，请重新绑定')
   targets.add(b.target)
  }
  bindings.value=data.bindings.map(b=>({target:b.target,expectedText:b.expectedText,field:b.field}))
  invalidate();await inspect()
 }catch(e){error.value=e.message}
}
function removeBinding(t){bindings.value=bindings.value.filter(b=>b.target!==t);invalidate()}
async function inspect(){
 busy.value=true;error.value='';notice.value='';invalidate()
 try{
  inspection.value=await api(endpoint+'/inspect-docx',{method:'POST',body:JSON.stringify({templateFileId:props.templateId,baseTemplateId:props.baseTemplateId,sectionTemplateIds:props.sectionTemplateIds,expectedVersionNo:props.versionNo,bodyBindings:bindings.value})})
  checkedPlan.value=JSON.stringify(bindings.value)
 }catch(e){error.value=e.message}finally{busy.value=false}
}
async function generate(){
 if(!ready.value)return
 busy.value=true;error.value=''
 try{
  const result=await api(endpoint+'/generate-docx',{method:'POST',body:JSON.stringify({templateFileId:props.templateId,baseTemplateId:props.baseTemplateId,sectionTemplateIds:props.sectionTemplateIds,expectedVersionNo:props.versionNo,photoQuality:props.photoQuality,fillBody:true,bodyBindings:bindings.value,bodyPlanSha256:inspection.value.planSha256,templateSha256:inspection.value.templateSha256,templateReviewed:true,batchIds:props.batchIds,photoMode:props.batchIds.length?props.photoMode:'append'})})
  notice.value='草稿已归档 · 报告 v'+result.versionNo+' / 文件 #'+result.artifactRevisionNo+' · '+(result.fileBytes/1024/1024).toFixed(2)+' MB · '+result.lowDpiCount+' 张低于200 DPI（需核对原图）'
  emit('generated',result)
  if(props.canDownload)await downloadFile(result.fileId,result.filename)
 }catch(e){error.value=e.message}finally{busy.value=false;reviewed.value=false}
}
onMounted(inspect)
</script>
<template>
<section class="binding-workbench">
 <h4>检测正文填充与预检</h4><p>可使用 Word 中的 <code v-text="'{{erp.general.projectName}}'"></code> 标记，或手动将实际正文段落绑定到资料字段。表格按真实单元格定位，不按网格列号猜测。多个楼栋/检测记录通过字段索引分别绑定；独立无纵向合并的模板行可使用 <code v-text="'{{erp.buildings.*.name}}'"></code> 或 <code v-text="'{{erp.records[消防给水系统].*.result}}'"></code> 自动展开。页眉页脚支持普通文字段落绑定，复杂域及签署位置仍不自动改写。</p>
 <p v-if="error" role="alert" class="binding-error">{{error}}</p><p v-if="notice" role="status">{{notice}}</p>
 <template v-if="inspection">
 <div class="binding-summary"><b>报告 v{{inspection.versionNo}} · 资料 v{{inspection.profileVersionNo}}</b><span>可选段落 {{inspection.inspection.candidates.length}} · 待填充 {{inspection.inspection.changes.length}} · 未映射字段 {{inspection.inspection.unmappedFields.length}}</span></div>
 <div v-if="inspection.inspection.suggestedBindings?.length" class="binding-actions"><span>识别到 {{inspection.inspection.suggestedBindings.length}} 项工程信息与建筑字段候选；使用前请检查原文与数据对应关系。</span><button :disabled="busy" @click="applySuggestions">加入建议绑定（不自动覆盖手动绑定）</button></div>
 <div class="binding-select">
  <label>搜索模板原文或段落位置<input v-model="search" @input="candidatePage=1" placeholder="输入工程名称、地址、单元格位置…"></label>
  <label>选择资料字段<select v-model="field"><option value="">请选择字段</option><option v-for="f in inspection.inspection.fields" :key="f.key" :value="f.key">{{labels[f.key.split('.')[0]]}} / {{f.key}}：{{f.value.slice(0,80)}}</option></select></label>
 </div>
 <div class="binding-candidates"><button v-for="c in page" :key="c.target" type="button" :class="{picked:target===c.target}" @click="chooseCandidate(c)"><small>{{c.target}}</small><span>{{c.text||'（空白单元格）'}}</span></button></div>
 <div class="binding-actions"><button :disabled="candidatePage<=1" @click="candidatePage--">上一页</button><span>{{candidatePage}} / {{maxPages}} 页</span><button :disabled="candidatePage>=maxPages" @click="candidatePage++">下一页</button><button class="primary-button" :disabled="busy||!target||!field" @click="addBinding">绑定选中段落</button></div>
 <div class="binding-actions"><button :disabled="busy||!bindings.length" @click="exportMapping">导出可复用映射 JSON</button><label>导入同一模板映射<input type="file" accept=".json,application/json" :disabled="busy" @change="importMapping"></label><small>映射包含模板原文，仅在公司内部保存；同一模板哈希和目标原文均须匹配，导入后重新预检。</small></div>
 <details open><summary>手动绑定（{{bindings.length}}）</summary><article v-for="b in bindings" :key="b.target" class="binding-row"><b>{{b.target}} → {{b.field}}</b><span>{{b.expectedText}}</span><button :disabled="busy" @click="removeBinding(b.target)">移除</button></article></details>
 <button class="secondary-button" :disabled="busy" @click="inspect">重新预检全部绑定</button>
 <p v-if="checkedPlan!==JSON.stringify(bindings)" class="binding-error">绑定已修改，须重新预检。</p>
 <div v-if="inspection.inspection.errors.length" role="alert" class="binding-error"><b>预检问题</b><p v-for="(e,i) in inspection.inspection.errors" :key="i">{{e}}</p></div>
 <details open><summary>正文变更预览（{{inspection.inspection.changes.length}}）</summary><div class="binding-changes"><article v-for="c in inspection.inspection.changes" :key="c.target" class="binding-row"><b>{{c.target}} · {{c.fields.join('、')}}</b><label>原文<span>{{c.before}}</span></label><label>填充后<span>{{c.after}}</span></label></article></div></details>
 <details><summary>未映射资料字段（{{inspection.inspection.unmappedFields.length}}）</summary><p>未映射字段不会写入。未绑定的模板原文也不会自动删除，请逐项核对。</p><p v-for="k in inspection.inspection.unmappedFields" :key="k">{{k}}</p></details>
 <p v-if="!batchIds.length">{{baseTemplateId?'未选择照片批次，组合报告不生成空照片页。':'未选择照片批次，本次仅填充正文，模板原有照片保持不变。'}}</p>
 <label class="binding-ack"><input v-model="reviewed" type="checkbox" :disabled="busy||inspection.inspection.errors.length>0||checkedPlan!==JSON.stringify(bindings)">我已检查变更及未映射字段，确认模板中未遗留不适用的旧项目信息、照片、证照、签字或印章；生成文件仅作为人工审核草稿。</label>
 <button class="primary-button" :disabled="!ready" @click="generate">生成正文{{batchIds.length?'与 '+batchIds.length+' 批照片':''}}并归档草稿</button>
 </template>
 <p v-else-if="busy">读取模板结构…</p><button v-else :disabled="busy" @click="inspect">重新读取模板</button>
</section>
</template>
<style scoped>
.binding-workbench{border:1px solid #dfe6ef;border-radius:10px;padding:14px;display:grid;gap:12px;font-size:12px;color:#405675}.binding-workbench h4,.binding-workbench p{margin:0}.binding-workbench p{line-height:1.7}.binding-summary,.binding-actions{display:flex;align-items:center;gap:10px;flex-wrap:wrap}.binding-select{display:grid;grid-template-columns:1fr 1fr;gap:8px}.binding-select label,.binding-row label{display:grid;gap:5px}.binding-select input,.binding-select select{min-width:0;padding:8px;border:1px solid #dfe6ef;border-radius:6px;background:white}.binding-candidates,.binding-changes{display:grid;gap:6px;max-height:360px;overflow:auto}.binding-candidates button{text-align:left;display:grid;gap:4px;white-space:normal;overflow-wrap:anywhere}.binding-candidates button.picked{border-color:#7292db;background:#edf3ff}.binding-workbench button{padding:8px;border:1px solid #dfe6ef;border-radius:7px;background:white;color:#405675;cursor:pointer}.binding-workbench button.primary-button{background:#315fca;color:white}.binding-workbench button:disabled{opacity:.5;cursor:default}.binding-row{display:grid;gap:6px;padding:10px;border:1px solid #e6ebf2;border-radius:7px;margin:4px 0;overflow-wrap:anywhere;white-space:pre-wrap}.binding-row label span{padding:6px;background:#f5f7fa}.binding-error{color:#a33}.binding-ack{display:flex;gap:8px;align-items:flex-start;line-height:1.8}.binding-workbench summary{cursor:pointer;font-weight:600}@media(max-width:650px){.binding-select{grid-template-columns:1fr}}
</style>
