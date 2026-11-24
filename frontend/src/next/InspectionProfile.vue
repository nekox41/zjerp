<script setup>
import { computed,onMounted,ref } from 'vue'
import { api } from './api'
import FireBasisPicker from './FireBasisPicker.vue'
const props=defineProps({workPackageId:{type:Number,required:true},projectName:{type:String,default:''},permissions:{type:Array,default:()=>[]}})
const canWrite=computed(()=>props.permissions.includes('report:write'))
const endpoint='/service-workflow/work-packages/'+props.workPackageId+'/inspection-profile'
const systems=ref(['火灾自动报警系统','消防给水系统','消火栓系统','自动喷水灭火系统','防烟排烟系统','消防应急照明和疏散指示系统','防火分隔设施','电气火灾监控系统','消防电源监控系统','火灾警报和消防应急广播系统','消防专用电话系统','消防电梯','消防供配电设施','建筑灭火器'])
const general=[['projectName','工程名称'],['address','工程地址'],['projectCategory','工程类别'],['constructionUnit','建设单位'],['constructionQualification','建设单位资质'],['designUnit','设计单位'],['designQualification','设计单位资质'],['contractor','施工单位'],['contractorQualification','施工单位资质'],['supervisionUnit','监理单位'],['supervisionQualification','监理单位资质'],['totalArea','建筑总面积（注明单位）'],['commissioningUnit','委托单位'],['contact','委托联系人'],['phone','联系电话'],['commissionedOn','委托时间'],['inspectionDates','检测日期/范围'],['reportNo','报告编号'],['reportDate','报告日期'],['inspectionCategory','检测类别'],['overview','检测概况'],['introduction','工程简介'],['scope','检测范围'],['designDate','设计日期'],['designReviewDate','设计审查日期'],['completionDate','竣工日期'],['acceptanceDate','消防验收日期'],['renovationDate','最近改扩建日期'],['inspectionDate','本次检测日期'],['applicabilityNotes','年代及适用性说明'],['basis','检测依据（旧版文字或选用后自动生成）']]
const sections=[
 {key:'buildings',name:'建筑单体与区域',fields:[['name','单体/区域名称'],['structure','结构类型'],['use','使用性质'],['fireResistance','耐火等级'],['aboveGroundFloors','地上层数'],['belowGroundFloors','地下层数'],['height','建筑高度'],['length','建筑长度'],['footprint','占地面积'],['aboveGroundArea','地上建筑面积'],['belowGroundArea','地下建筑面积'],['constructionYear','建成年份'],['designReviewDate','设计审查日期'],['acceptanceDate','验收日期'],['renovationDate','改造日期']]},
 {key:'facilities',name:'共用设施与服务范围',fields:[['system','所属系统'],['name','设施名称'],['location','实际位置（可在受检区域之外）'],['servedAreas','服务楼栋/区域'],['description','设施信息']]},
 {key:'records',name:'检测记录',fields:[['system','所属系统'],['item','检验项目'],['standard','标准要求'],['location','检测部位'],['result','检验结果'],['judgment','单项判定（人工）']]},
 {key:'instruments',name:'检测仪器',fields:[['name','名称'],['model','型号'],['serialNo','编号'],['calibrationExpiry','检定有效期']]},
 {key:'summaries',name:'系统判定汇总（人工填写）',fields:[['system','所属系统'],['judgment','系统判定'],['notes','备注']]}
]
const active=ref('general'),recordFilter=ref(''),profile=ref(null),version=ref(0),history=ref([]),busy=ref(false),error=ref(''),notice=ref(''),historical=ref(null),saved=ref('')
const templateOptions=ref([]),importTemplate=ref(''),importBusy=ref(false)
async function loadTemplates(){try{templateOptions.value=(await api('/inspection-templates')).filter(t=>t.template_kind==='system'&&t.item_count>0)}catch(e){error.value=e.message}}
async function importItems(){
 if(!editing.value||!importTemplate.value||importBusy.value)return
 importBusy.value=true;error.value=''
 try{
  const template=templateOptions.value.find(t=>String(t.id)===String(importTemplate.value));if(!template)throw Error('模板已不可用')
  const existing=profile.value.records.filter(r=>r.system===template.system_name)
  if(existing.some(r=>String(r.templateId||'')!==String(template.id)))throw Error('该系统已有手工记录或不同模板版本。请先人工处理旧记录，不自动覆盖。')
  const items=await api('/inspection-templates/'+template.id+'/items')
  if(items.some(i=>!i.clause_active||!i.standard_active||!i.verified))throw Error('模板有停用规范或条款，请先修订模板')
  const seen=new Set(existing.map(r=>String(r.templateItemId)));let added=0
  for(const i of items)if(!seen.has(String(i.id))){
   profile.value.records.push({system:template.system_name,item:i.item_name,standard:i.requirement_summary+'\n（'+i.standard_no+' '+i.edition+' 第'+i.clause_no+'条）',templateItemId:String(i.id),templateId:String(template.id),clauseId:String(i.clause_id),standardId:String(i.standard_id),clauseReference:i.standard_no+' '+i.edition+' 第'+i.clause_no+'条',sourceLocator:i.source_locator,location:'',result:'',judgment:''});added++
  }
  recordFilter.value=template.system_name;notice.value='新增 '+added+' 项，已有记录保留；请填写实际检测结果及人工判定后保存'
 }catch(e){error.value=e.message}finally{importBusy.value=false}
}
function repeatItem(row){
 if(!editing.value)return
 profile.value.records.push({...row,location:'',result:'',judgment:''})
}
const section=computed(()=>sections.find(s=>s.key===active.value))
const editing=computed(()=>canWrite.value&&!historical.value&&!busy.value)
const dirty=computed(()=>profile.value&&JSON.stringify(profile.value)!==saved.value)
function normalize(content={}){return {general:{...Object.fromEntries(general.map(([key])=>[key,''])),projectName:props.projectName,...content.general},buildings:content.buildings||[],facilities:content.facilities||[],records:content.records||[],instruments:content.instruments||[],summaries:content.summaries||[],conclusion:{text:'',...content.conclusion},basisReferences:content.basisReferences||[]}}
async function load(force=false){
 if(!force&&dirty.value&&!confirm('放弃未保存的修改？'))return
 busy.value=true;error.value='';try{const [r,h]=await Promise.all([api(endpoint),api(endpoint+'/versions')]);profile.value=normalize(r.content);version.value=r.versionNo;history.value=h;historical.value=null;saved.value=JSON.stringify(profile.value)}catch(e){error.value=e.message}finally{busy.value=false}
}
async function save(){
 if(!editing.value)return
 busy.value=true;error.value='';notice.value='';try{const {basisReferences,...content}=profile.value;const choices=basisReferences.map(s=>({id:s.id,scope:s.scope,reason:s.reason}));const r=await api(endpoint,{method:'PUT',body:JSON.stringify({expectedVersionNo:version.value,content,basisChoices:choices})});profile.value=normalize(r.content);version.value=r.versionNo;saved.value=JSON.stringify(profile.value);notice.value='已保存资料版本 '+version.value;history.value=await api(endpoint+'/versions')}catch(e){error.value=e.message}finally{busy.value=false}
}
async function openVersion(v){
 if(dirty.value&&!confirm('放弃未保存的修改？'))return
 busy.value=true;error.value='';try{const r=await api(endpoint+'/versions/'+v);profile.value=normalize(r.content);historical.value=v;saved.value=JSON.stringify(profile.value)}catch(e){error.value=e.message}finally{busy.value=false}
}
function addRow(s){const row=Object.fromEntries(s.fields.map(([key])=>[key,'']));if(s.key==='records'&&recordFilter.value)row.system=recordFilter.value;profile.value[s.key].push(row)}
const visibleRows=computed(()=>section.value&&profile.value?profile.value[section.value.key].map((row,index)=>({row,index})).filter(x=>section.value.key!=='records'||!recordFilter.value||x.row.system===recordFilter.value):[])
onMounted(async()=>{try{systems.value=await api('/inspection-templates/systems')}catch(e){error.value=e.message}if(canWrite.value)await loadTemplates();await load(true)})
</script>
<template>
<section class="ip-panel">
 <div class="ip-head"><div><h3>消防检测资料档案</h3><p>依据两份真实报告的业务字段录入。资料版本与正式报告版本独立；所有判定由人工填写，不自动签章、不推导结论。</p></div><div><button class="secondary-button" :disabled="busy" @click="load()">刷新当前版本</button> <button v-if="canWrite&&!historical" class="primary-button" :disabled="busy||!dirty" @click="save">保存新版本</button></div></div>
 <p v-if="error" role="alert" class="ip-error">{{error}}</p><p v-if="notice" role="status">{{notice}}</p>
 <template v-if="profile">
 <div class="ip-history"><b>{{historical?'历史只读版本 '+historical:'当前版本 '+version}}</b><span v-if="dirty"> · 有未保存修改</span><label>历史版本<select :value="historical||''" :disabled="busy" @change="$event.target.value?openVersion(Number($event.target.value)):load()"><option value="">当前</option><option v-for="h in history" :key="h.version_no" :value="h.version_no">版本 {{h.version_no}} · {{h.created_at}}</option></select></label></div>
 <nav class="ip-tabs"><button v-for="s in [{key:'general',name:'工程基本信息'},{key:'basis',name:'检测依据'},...sections,{key:'conclusion',name:'最终结论'}]" :key="s.key" :class="{active:active===s.key}" @click="active=s.key">{{s.name}}<template v-if="s.key==='basis'"> · {{profile.basisReferences.length}}</template><template v-else-if="Array.isArray(profile[s.key])"> · {{profile[s.key].length}}</template></button></nav>
 <fieldset :disabled="!editing">
 <div v-if="active==='general'" class="ip-grid"><label v-for="[key,label] in general" :key="key" :class="{wide:['overview','introduction','scope','basis'].includes(key)}">{{label}}<textarea v-if="['overview','introduction','scope','basis'].includes(key)" v-model="profile.general[key]" :readonly="key==='basis'&&profile.basisReferences.length>0" rows="3" maxlength="12000"></textarea><input v-else v-model="profile.general[key]" :type="key.endsWith('Date')?'date':'text'" maxlength="12000"></label></div>
 <FireBasisPicker v-else-if="active==='basis'" v-model="profile.basisReferences" :reference-date="profile.general.designReviewDate||profile.general.designDate||profile.general.inspectionDate||''" :disabled="!editing" :can-download="props.permissions.includes('file:read')"/>
 <div v-else-if="active==='conclusion'"><label>最终检测结论（人工填写，须按正式流程审核）<textarea v-model="profile.conclusion.text" rows="5" maxlength="12000"></textarea></label></div>
 <template v-else-if="section">
 <div class="ip-head"><label v-if="active==='records'">筛选消防系统<select v-model="recordFilter"><option value="">全部</option><option v-for="s in systems" :key="s">{{s}}</option></select></label><button v-if="editing" class="secondary-button" @click="addRow(section)">新增{{section.name}}记录</button></div>
 <div v-if="active==='records'&&editing" class="ip-record"><b>从系统模板建立检测项目</b><div class="ip-head"><select v-model="importTemplate"><option value="">选择已建立检测项目的系统模板</option><option v-for="t in templateOptions" :key="t.id" :value="t.id">{{t.system_name}} · {{t.template_name}} v{{t.revision_no}} · {{t.item_count}}项</option></select><button :disabled="!importTemplate||importBusy" @click="importItems">导入缺少项目</button><button @click="loadTemplates">刷新模板</button></div><p>只新增缺少项，不覆盖已有检测结果。导入不会自动选用规范或产生判定。</p></div>
 <p v-if="!visibleRows.length">暂无记录</p>
 <article v-for="{row,index} in visibleRows" :key="index" class="ip-record"><div class="ip-head"><b>#{{index+1}} <small v-if="row.templateItemId">模板 #{{row.templateId}} · {{row.clauseReference}}</small></b><button v-if="editing&&section.key==='records'&&row.templateItemId" @click="repeatItem(row)">新增同项目检测部位</button><button v-if="editing" class="secondary-button" @click="profile[section.key].splice(index,1)">移除</button></div><div class="ip-grid"><label v-for="[key,label] in section.fields" :key="key">{{label}}<select v-if="key==='system'" v-model="row[key]" :disabled="!!row.templateItemId"><option value="">待填写</option><option v-for="s in systems" :key="s">{{s}}</option></select><input v-else-if="key.endsWith('Date')" type="date" v-model="row[key]"><textarea v-else v-model="row[key]" :readonly="!!row.templateItemId&&['item','standard'].includes(key)" rows="2" maxlength="12000"></textarea></label></div></article>
 </template>
 </fieldset>
 </template>
</section>
</template>
<style scoped>
.ip-panel{display:grid;gap:14px}.ip-head,.ip-history,.ip-tabs{display:flex;justify-content:space-between;gap:8px;align-items:center;flex-wrap:wrap}.ip-head h3{margin:0}.ip-head p,.ip-history{font-size:12px;color:#65758c}.ip-tabs{justify-content:flex-start}.ip-tabs button{background:#fff;border:1px solid #dfe6ef;border-radius:7px;padding:8px 12px;color:#405675}.ip-tabs button.active{background:#edf3ff;border-color:#8da9e3}.ip-panel fieldset{border:0;padding:0;margin:0;min-width:0}.ip-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:12px}.ip-grid .wide{grid-column:1/-1}.ip-panel label{display:grid;gap:5px;font-size:12px}.ip-panel input,.ip-panel textarea,.ip-panel select{padding:9px;border:1px solid #dfe6ef;border-radius:7px;min-width:0;font:inherit;background:white}.ip-record{display:grid;gap:10px;margin-top:12px;padding:12px;border:1px solid #e4eaf1;border-radius:8px}.ip-error{color:#a33}
</style>
