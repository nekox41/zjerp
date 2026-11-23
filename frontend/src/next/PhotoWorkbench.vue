<script setup>
import {computed,onBeforeUnmount,onMounted,ref,watch} from 'vue'
import {api,downloadFile,privateBlob} from './api'

const systems=[
{code:'fire_alarm',label:'火灾自动报警系统'},{code:'fire_water',label:'消防给水系统'},
{code:'hydrant',label:'消火栓系统'},{code:'sprinkler',label:'自动喷水灭火系统'},
{code:'smoke_control',label:'防烟排烟系统'},{code:'emergency_lighting',label:'消防应急照明和疏散指示系统'},
{code:'fire_separation',label:'防火分隔设施'},{code:'electrical_fire',label:'电气火灾监控系统'},
{code:'fire_power_monitoring',label:'消防电源监控系统'},{code:'fire_alarm_broadcast',label:'火灾警报和消防应急广播系统'},
{code:'fire_phone',label:'消防专用电话系统'},{code:'fire_elevator',label:'消防电梯'},
{code:'fire_distribution',label:'消防供配电设施'},{code:'extinguisher',label:'建筑灭火器'}
]
const projects=ref([]),reports=ref([]),visits=ref([]),batches=ref([])
const selectedProjectId=ref(''),selectedReportId=ref(''),selectedVisitId=ref('')
const batchTitle=ref('现场检测照片整理'),templateFile=ref(null)
const activeBatch=ref(null),selectedPhotoIds=ref([]),filter=ref('全部'),view=ref('cards')
const modes=ref({}),previewUrls=ref({}),focused=ref(null)
const busy=ref(false),uploading=ref(false),error=ref(''),notice=ref('')
const photoCount=computed(()=>activeBatch.value?.photos?.length||0)
const unclassified=computed(()=>activeBatch.value?.photos?.filter(p=>!p.system_code)||[])
const selectedCount=computed(()=>selectedPhotoIds.value.length)
const grouped=computed(()=>systems.map(s=>({ ...s, photos:(activeBatch.value?.photos||[]).filter(p=>p.system_code===s.code), anchor:(activeBatch.value?.systems||[]).find(x=>x.systemCode===s.code) })).filter(x=>x.photos.length))
const visiblePhotos=computed(()=>{
 const all=activeBatch.value?.photos||[]
 if(filter.value==='全部')return all
 if(filter.value==='未分类')return all.filter(p=>!p.system_code)
 return all.filter(p=>p.system_code===filter.value)
})
const exportBlocked=computed(()=>!activeBatch.value||!photoCount.value||unclassified.value.length>0||grouped.value.some(g=>!g.anchor?.headingFound))
function revokePreviews(){Object.values(previewUrls.value).forEach(URL.revokeObjectURL);previewUrls.value={};focused.value=null}
async function loadPreviews(items){
 revokePreviews()
 for(let i=0;i<(items||[]).length;i+=16){
  await Promise.all(items.slice(i,i+16).map(async p=>{
   try{const blob=await privateBlob('/report-photos/photos/'+p.id+'/thumbnail');previewUrls.value[p.id]=URL.createObjectURL(blob)}
   catch(e){previewUrls.value[p.id]=''}
  }))
 }
}
async function loadProjects(){
 projects.value=await api('/business/projects').catch(()=>[])
 if(!selectedProjectId.value&&projects.value.length)selectedProjectId.value=String(projects.value[0].id)
}
async function loadProject(){
 activeBatch.value=null;selectedPhotoIds.value=[];revokePreviews()
 if(!selectedProjectId.value){reports.value=[];visits.value=[];batches.value=[];return}
 try{
  const [r,v,b]=await Promise.all([
   api('/business/projects/'+selectedProjectId.value+'/reports'),
   api('/business/projects/'+selectedProjectId.value+'/site-visits'),
   api('/report-photos/batches?projectId='+selectedProjectId.value)
  ])
  reports.value=r;visits.value=v;batches.value=b
  if(!selectedReportId.value||!reports.value.some(x=>String(x.id)===String(selectedReportId.value)))selectedReportId.value=''
 }catch(e){error.value=e.message}
}
function chooseTemplate(event){
 const f=event.target.files?.[0];error.value=''
 if(!f)return
 if(!f.name.toLowerCase().endsWith('.docx')||f.size>25*1024*1024){error.value='报告模板必须是 25MB 以内的 DOCX';event.target.value='';return}
 templateFile.value=f
}
async function createBatch(){
 error.value='';notice.value=''
 if(!selectedProjectId.value||!selectedReportId.value){error.value='请先选择项目和草稿/退回报告';return}
 if(!templateFile.value){error.value='请选择原始 DOCX 报告模板';return}
 const report=reports.value.find(x=>String(x.id)===String(selectedReportId.value))
 if(!report||!['draft','returned'].includes(report.status)){error.value='照片只能插入草稿或退回报告';return}
 busy.value=true
 try{
  const form=new FormData()
  form.append('projectId',selectedProjectId.value);form.append('reportId',selectedReportId.value)
  if(selectedVisitId.value)form.append('siteVisitId',selectedVisitId.value)
  form.append('title',batchTitle.value||'现场检测照片整理');form.append('template',templateFile.value)
  const result=await api('/report-photos/batches',{method:'POST',body:form})
  templateFile.value=null;await loadProject();await openBatch(result.id)
  notice.value='照片整理批次已创建，模板章节已识别'
 }catch(e){error.value=e.message}finally{busy.value=false}
}
async function openBatch(id){
 error.value='';busy.value=true;selectedPhotoIds.value=[];revokePreviews()
 try{
  activeBatch.value=await api('/report-photos/batches/'+id)
  modes.value={}
  for(const p of activeBatch.value.photos||[])if(p.system_code)modes.value[p.system_code]=modes.value[p.system_code]||'replace'
  await loadPreviews(activeBatch.value.photos);filter.value='全部'
 }catch(e){error.value=e.message}finally{busy.value=false}
}
async function refreshBatch(){
 if(!activeBatch.value)return
 const id=activeBatch.value.id
 activeBatch.value=await api('/report-photos/batches/'+id)
 await loadPreviews(activeBatch.value.photos)
}
async function addPhotos(event){
 const files=Array.from(event.target.files||[])
 if(!files.length||!activeBatch.value)return
 const good=files.filter(f=>['image/jpeg','image/png'].includes(f.type))
 if(good.length!==files.length)error.value='已忽略非 JPG/PNG 文件'
 if(good.length+photoCount.value>300){error.value='一个批次最多 300 张照片';event.target.value='';return}
 if(good.some(f=>f.size>20*1024*1024)){error.value='单张照片不能超过 20MB';event.target.value='';return}
 uploading.value=true;error.value='';notice.value=''
 try{
  const form=new FormData();good.forEach(f=>form.append('photos',f))
  const result=await api('/report-photos/batches/'+activeBatch.value.id+'/photos',{method:'POST',body:form})
  activeBatch.value=result;await loadPreviews(result.photos);notice.value='已导入 '+good.length+' 张原始照片'
 }catch(e){error.value=e.message}finally{uploading.value=false;event.target.value=''}
}
async function saveCategory(photo,code){
 try{await api('/report-photos/photos/'+photo.id,{method:'PUT',body:JSON.stringify({systemCode:code||null,sortOrder:photo.sort_order,notes:photo.notes||null})});await refreshBatch()}
 catch(e){error.value=e.message}
}
async function classifySelected(code){
 if(!selectedPhotoIds.value.length||!code)return
 busy.value=true;error.value=''
 try{
  activeBatch.value=await api('/report-photos/batches/'+activeBatch.value.id+'/classify',{method:'POST',body:JSON.stringify({photoIds:selectedPhotoIds.value,systemCode:code})})
  selectedPhotoIds.value=[];await loadPreviews(activeBatch.value.photos);notice.value='批量分类已保存'
 }catch(e){error.value=e.message}finally{busy.value=false}
}
function selectVisible(checked){selectedPhotoIds.value=checked?visiblePhotos.value.map(p=>p.id):[]}
async function movePhoto(photo,direction){
 const list=[...(activeBatch.value?.photos||[])],index=list.findIndex(p=>p.id===photo.id)
 const same=list.filter(p=>p.system_code===photo.system_code),pos=same.findIndex(p=>p.id===photo.id),target=same[pos+direction]
 if(!target)return
 const targetIndex=list.findIndex(p=>p.id===target.id)
 ;[list[index],list[targetIndex]]=[list[targetIndex],list[index]]
 try{activeBatch.value=await api('/report-photos/batches/'+activeBatch.value.id+'/reorder',{method:'POST',body:JSON.stringify({photoIds:list.map(p=>p.id)})});await loadPreviews(activeBatch.value.photos)}
 catch(e){error.value=e.message}
}
async function removePhoto(photo){
 if(!confirm('从当前整理批次移除这张照片？原始文件仍会保留。'))return
 try{await api('/report-photos/photos/'+photo.id,{method:'DELETE'});await refreshBatch();selectedPhotoIds.value=selectedPhotoIds.value.filter(x=>x!==photo.id)}
 catch(e){error.value=e.message}
}
async function exportReport(){
 if(exportBlocked.value){error.value=unclassified.value.length?'仍有未分类照片':'有照片分类在当前 DOCX 模板中找不到对应照片位置';return}
 busy.value=true;error.value='';notice.value=''
 try{
  const result=await api('/report-photos/batches/'+activeBatch.value.id+'/export',{method:'POST',body:JSON.stringify({expectedReportVersionNo:activeBatch.value.current_version_no,modes:modes.value})})
  const batchId=activeBatch.value.id;await downloadFile(result.fileId,result.fileName);await openBatch(batchId);reports.value=await api('/business/projects/'+selectedProjectId.value+'/reports')
  notice.value='已生成报告新版本 v'+result.reportVersionNo+'，'+result.photoCount+' 张照片已按系统写入原有章节'
 }catch(e){error.value=e.message}finally{busy.value=false}
}
async function download(id,name){try{await downloadFile(id,name)}catch(e){error.value=e.message}}
watch(selectedProjectId,loadProject)
onMounted(loadProjects)
onBeforeUnmount(revokePreviews)
</script>

<template>
<section class="photo-workbench">
 <div class="pw-head"><div><span class="eyebrow">REPORT · PHOTO WORKFLOW</span><h2>现场照片整理与报告插图</h2><p>导入照片、人工标记消防系统、核对原报告锚点，再生成新的报告版本。</p></div></div>
 <p v-if="error" class="message error" role="alert">{{error}}</p><p v-if="notice" class="message success" role="status">{{notice}}</p>
 <section class="panel pw-create">
  <div class="pw-section-head"><div><h3>创建照片整理批次</h3><p>每个批次关联一个项目、一份报告和一份原始 DOCX 模板。</p></div></div>
  <div class="pw-create-grid">
   <label>项目<select v-model="selectedProjectId"><option value="">选择项目</option><option v-for="p in projects" :key="p.id" :value="String(p.id)">{{p.project_no}} · {{p.name}}</option></select></label>
   <label>目标报告<select v-model="selectedReportId"><option value="">选择草稿/退回报告</option><option v-for="r in reports.filter(x=>['draft','returned'].includes(x.status))" :key="r.id" :value="String(r.id)">{{r.report_no}} · {{r.title}} · v{{r.current_version_no}}</option></select></label>
   <label>关联现场服务（可选）<select v-model="selectedVisitId"><option value="">不关联</option><option v-for="v in visits" :key="v.id" :value="String(v.id)">{{v.performed_on}} · {{v.title}}</option></select></label>
   <label>批次名称<input v-model="batchTitle"></label>
   <label class="pw-file">原始 DOCX 报告<input type="file" accept=".docx" @change="chooseTemplate"><small>{{templateFile?.name||'尚未选择 DOCX 文件'}}</small></label>
   <button class="primary-button" :disabled="busy||!selectedProjectId||!selectedReportId||!templateFile" @click="createBatch">{{busy?'处理中…':'创建批次并分析模板'}}</button>
  </div>
 </section>
 <div class="pw-layout">
  <section class="panel pw-batches"><h3>历史整理批次 <span>{{batches.length}}</span></h3>
   <button v-for="b in batches" :key="b.id" :class="{active:activeBatch?.id===b.id}" @click="openBatch(b.id)">
    <b>{{b.title}}</b><small>{{b.template_name}}</small><span>{{b.report_no}} · {{b.photo_count}} 张 · {{b.status==='exported'?'已导出':'草稿'}}</span>
   </button><p v-if="!batches.length" class="empty">当前项目还没有照片批次</p>
  </section>
  <section class="pw-main panel" v-if="activeBatch">
   <div class="pw-active-head"><div><h3>{{activeBatch.title}}</h3><p>{{activeBatch.template_name}} · 报告 {{activeBatch.report_no}} · 当前版本 v{{activeBatch.current_version_no}}</p></div><span class="tag">{{activeBatch.report_status}}</span></div>
   <div class="pw-stats"><span>照片 {{photoCount}}</span><span>已分类 {{photoCount-unclassified.length}}</span><span>未分类 {{unclassified.length}}</span><span>模板照片组 {{activeBatch.systems.reduce((n,x)=>n+x.groupCount,0)}}</span></div>
   <div class="pw-anchor"><div class="pw-section-head"><div><h4>模板系统章节识别</h4><p>系统标题下的“现场检测照片”表格作为插图锚点，不会把照片追加到文末。</p></div></div>
    <div class="pw-anchor-grid"><div v-for="s in activeBatch.systems" :key="s.systemCode" :class="{missing:!s.headingFound}"><b>{{s.label}}</b><span>{{s.headingFound?s.groupCount+' 组 · '+s.imageCount+' 张原图':'未找到照片位置'}}</span></div></div>
   </div>
   <div class="pw-toolbar"><label class="pw-upload">＋ 批量导入照片<input hidden type="file" multiple accept="image/jpeg,image/png" @change="addPhotos"></label>
    <span>{{uploading?'正在上传原始照片…':'原始照片保存在私有文件区'}}</span>
    <button class="secondary-button" @click="view=view==='cards'?'table':'cards'">{{view==='cards'?'表格视图':'卡片视图'}}</button>
    <button class="primary-button" :disabled="busy||uploading||exportBlocked" @click="exportReport">{{busy?'正在生成…':'插入照片并生成报告新版本'}}</button>
   </div>
   <div class="pw-modes" v-if="grouped.length"><div v-for="g in grouped" :key="g.code" class="pw-mode-row">
    <b>{{g.label}} · {{g.photos.length}} 张</b><span>模板已有 {{g.anchor?.groupCount||0}} 组 / {{g.anchor?.imageCount||0}} 张</span>
    <select v-model="modes[g.code]"><option value="replace">替换本系统原有照片</option><option value="append">保留原照片并追加</option></select>
   </div><p>“替换”只替换所选系统章节中的照片表格，不修改检测明细；“追加”保留原图并在该系统最后一组照片表格后追加。</p></div>
   <div class="pw-controls"><div class="pw-filter"><button :class="{chosen:filter==='全部'}" @click="filter='全部'">全部</button><button :class="{chosen:filter==='未分类'}" @click="filter='未分类'">未分类</button>
    <button v-for="s in systems.filter(s=>activeBatch.photos.some(p=>p.system_code===s.code))" :key="s.code" :class="{chosen:filter===s.code}" @click="filter=s.code">{{s.label}}</button></div>
    <div class="pw-select-actions"><button @click="selectVisible(true)">全选当前</button><button @click="selectVisible(false)">取消选择</button></div>
   </div>
   <div v-if="selectedCount" class="pw-bulk"><b>已选 {{selectedCount}} 张</b><select @change="classifySelected($event.target.value);$event.target.value=''"><option value="">批量设置消防系统…</option><option v-for="s in systems" :key="s.code" :value="s.code">{{s.label}}</option></select></div>
   <div v-if="!photoCount" class="pw-empty"><h3>尚未导入照片</h3><p>可以一次选择多张 JPG/PNG 原始照片。</p></div>
   <div v-else-if="view==='cards'" class="pw-photo-grid">
    <article v-for="p in visiblePhotos" :key="p.id" class="pw-photo" :class="{selected:selectedPhotoIds.includes(p.id)}">
     <label class="pw-check"><input type="checkbox" :value="p.id" v-model="selectedPhotoIds"></label>
     <img v-if="previewUrls[p.id]" :src="previewUrls[p.id]" :alt="p.original_name" loading="lazy" @click="focused=p"><div v-else class="pw-image-placeholder">预览加载中…</div>
     <div class="pw-photo-body"><b :title="p.original_name">{{p.original_name}}</b>
      <select :value="p.system_code||''" @change="saveCategory(p,$event.target.value)"><option value="">未分类</option><option v-for="s in systems" :key="s.code" :value="s.code">{{s.label}}</option></select>
      <div class="pw-photo-actions"><button @click="movePhoto(p,-1)" title="上移">↑</button><button @click="movePhoto(p,1)" title="下移">↓</button><button class="danger" @click="removePhoto(p)">移除</button></div>
     </div>
    </article>
   </div>
   <div v-else class="pw-table-wrap"><table><thead><tr><th>选</th><th>预览</th><th>文件</th><th>分类</th><th>顺序</th><th>操作</th></tr></thead><tbody>
    <tr v-for="p in visiblePhotos" :key="p.id"><td><input type="checkbox" :value="p.id" v-model="selectedPhotoIds"></td>
     <td><img v-if="previewUrls[p.id]" :src="previewUrls[p.id]" @click="focused=p" :alt="p.original_name"></td><td>{{p.original_name}}</td>
     <td><select :value="p.system_code||''" @change="saveCategory(p,$event.target.value)"><option value="">未分类</option><option v-for="s in systems" :key="s.code" :value="s.code">{{s.label}}</option></select></td>
     <td>{{p.sort_order}}</td><td><button @click="removePhoto(p)" class="text-button danger">移除</button></td>
    </tr>
   </tbody></table></div>
   <div class="pw-exports" v-if="activeBatch.exports?.length"><h4>历史导出</h4><div v-for="x in activeBatch.exports" :key="x.id" class="pw-export-row"><span>报告 v{{x.report_version_no}} · {{x.original_name}} · {{x.created_at}}</span><button class="text-button" @click="download(x.file_id,x.original_name)">下载</button></div></div>
  </section>
  <section v-else class="panel pw-empty"><h3>选择或创建一个照片批次</h3><p>批次会保存照片分类和顺序，刷新页面后可以继续整理。</p></section>
 </div>
 <div v-if="focused" class="pw-lightbox" role="dialog" aria-modal="true" @click.self="focused=null"><div><button class="pw-close" @click="focused=null">关闭 ✕</button><h3>{{focused.original_name}}</h3><img :src="previewUrls[focused.id]" :alt="focused.original_name"><p>{{systems.find(s=>s.code===focused.system_code)?.label||'未分类'}}</p></div></div>
</section>
</template>

<style scoped>
.photo-workbench{display:grid;gap:17px}.pw-head h2{margin:5px 0;font-size:23px;color:#203149}.pw-head p{margin:0;font-size:12px;color:#7d889a}.pw-create{padding:18px}.pw-section-head{display:flex;justify-content:space-between;align-items:center}.pw-section-head h3,.pw-section-head h4{margin:0;font-size:13px}.pw-section-head p{font-size:10px;color:#8792a4;margin:5px 0}.pw-create-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px;margin-top:15px}.pw-create-grid label{display:grid;gap:6px;font-size:11px;color:#65738a}.pw-create-grid input,.pw-create-grid select{min-width:0;padding:9px;border:1px solid #dce2eb;border-radius:8px;background:white;font:inherit;font-size:12px}.pw-file small{font-size:10px;color:#8894a5;overflow-wrap:anywhere}.pw-layout{display:grid;grid-template-columns:minmax(210px,.55fr) minmax(480px,1.8fr);gap:15px;align-items:start}.pw-batches{display:grid;gap:8px}.pw-batches h3{font-size:13px;margin:0 0 5px}.pw-batches h3 span{color:#98a2b1;font-weight:400}.pw-batches button{display:grid;gap:5px;text-align:left;border:1px solid #edf0f5;border-radius:9px;padding:11px;background:white;color:#36465d}.pw-batches button.active{border-color:#9bb3eb;background:#f5f8ff}.pw-batches small,.pw-batches span{font-size:10px;color:#8793a5}.pw-main{min-width:0;padding:18px}.pw-active-head{display:flex;align-items:start;justify-content:space-between;gap:10px}.pw-active-head h3{font-size:15px;margin:0}.pw-active-head p{font-size:10px;color:#8792a4;margin:6px 0}.tag{font-size:10px;padding:5px 8px;border-radius:20px;background:#edf2fd;color:#315fca}.pw-stats{display:flex;flex-wrap:wrap;gap:8px;margin:12px 0}.pw-stats span{padding:7px 10px;border:1px solid #e8edf3;border-radius:8px;color:#64738a;font-size:10px}.pw-anchor{border:1px solid #e9edf3;border-radius:10px;padding:12px;margin:14px 0}.pw-anchor-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:7px;margin-top:10px}.pw-anchor-grid>div{display:grid;gap:4px;padding:8px;background:#f7f9fc;border-radius:7px}.pw-anchor-grid b{font-size:10px;color:#4d5e77}.pw-anchor-grid span{font-size:9px;color:#65866f}.pw-anchor-grid .missing span{color:#b55b52}.pw-toolbar{display:flex;align-items:center;flex-wrap:wrap;gap:8px;margin:14px 0}.pw-upload{padding:9px 12px;border-radius:8px;background:#315fca;color:white;font-size:11px;cursor:pointer}.pw-toolbar>span{font-size:10px;color:#8994a5;margin-right:auto}.pw-toolbar button{font-size:10px}.pw-modes{border:1px solid #edf0f5;border-radius:9px;padding:10px;margin-bottom:14px}.pw-mode-row{display:grid;grid-template-columns:minmax(130px,1fr) minmax(90px,.6fr) minmax(190px,1fr);gap:10px;align-items:center;padding:7px 0;border-bottom:1px solid #f0f2f6}.pw-mode-row b{font-size:10px}.pw-mode-row span,.pw-modes p{font-size:10px;color:#8793a4}.pw-mode-row select{padding:7px;border:1px solid #dce2eb;border-radius:7px;font-size:10px;background:white}.pw-controls{display:flex;justify-content:space-between;gap:10px;align-items:start;flex-wrap:wrap;margin:14px 0}.pw-filter,.pw-select-actions{display:flex;flex-wrap:wrap;gap:6px}.pw-filter button,.pw-select-actions button{border:1px solid #e2e8f0;border-radius:7px;padding:6px 8px;background:white;color:#65738a;font-size:9px}.pw-filter button.chosen{background:#edf2fd;border-color:#bdcdf1;color:#315fca}.pw-bulk{display:flex;align-items:center;gap:10px;background:#edf3ff;padding:10px;border-radius:8px;font-size:11px;color:#3c5c94;margin-bottom:12px}.pw-bulk select{padding:8px;border:1px solid #cbd8f0;border-radius:7px;background:white}.pw-photo-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(155px,1fr));gap:10px}.pw-photo{position:relative;border:1px solid #e6ebf2;border-radius:9px;overflow:hidden;background:#fff;min-width:0}.pw-photo.selected{border-color:#6082d3;box-shadow:0 0 0 2px #6082d322}.pw-photo>img,.pw-image-placeholder{width:100%;height:125px;object-fit:contain;background:#f0f2f6;cursor:zoom-in}.pw-image-placeholder{display:grid;place-items:center;color:#9aa5b4;font-size:10px}.pw-check{position:absolute;top:7px;left:7px;z-index:1;background:white;padding:4px;border-radius:5px}.pw-photo-body{display:grid;gap:7px;padding:8px}.pw-photo-body>b{font-size:9px;color:#53627a;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.pw-photo-body select,.pw-table-wrap select{width:100%;min-width:0;padding:7px;border:1px solid #dce2eb;border-radius:7px;font-size:10px;background:white}.pw-photo-actions{display:flex;gap:5px;justify-content:end}.pw-photo-actions button{border:0;background:#f2f5fa;border-radius:5px;padding:5px 8px;color:#62728b;font-size:10px}.pw-photo-actions .danger,.danger{color:#b94e4e}.pw-table-wrap{overflow:auto}.pw-table-wrap table{width:100%;border-collapse:collapse}.pw-table-wrap th,.pw-table-wrap td{font-size:10px;padding:8px;border-bottom:1px solid #edf0f5}.pw-table-wrap img{width:80px;height:58px;object-fit:contain;background:#f1f3f7;cursor:zoom-in}.pw-exports{margin-top:18px}.pw-exports h4{font-size:12px}.pw-export-row{display:flex;justify-content:space-between;gap:10px;padding:8px 0;border-bottom:1px solid #edf0f5;font-size:10px;color:#65738a}.text-button{border:0;background:transparent;color:#315fca;font-size:10px}.pw-empty{padding:35px;text-align:center;color:#96a1b1;font-size:11px}.pw-empty h3{font-size:14px;color:#56677e}.pw-lightbox{position:fixed;inset:0;z-index:60;background:#142036d9;display:grid;place-items:center;padding:20px}.pw-lightbox>div{background:#fff;border-radius:12px;padding:14px;width:min(900px,96vw);max-height:94vh;display:grid;gap:10px}.pw-lightbox img{width:100%;height:75vh;object-fit:contain;background:#f2f4f8}.pw-lightbox h3{font-size:13px;margin:0}.pw-lightbox p{font-size:11px;color:#7d889a;margin:0}.pw-close{justify-self:end;border:0;background:#edf1f7;border-radius:7px;padding:7px 10px;font-size:10px}.secondary-button{border:1px solid #dce3ed;background:white;color:#516078;border-radius:8px;padding:9px 13px}.primary-button{border:0;border-radius:8px;background:#315fca;color:white;padding:10px 13px;font-size:11px}
@media(max-width:1050px){.pw-layout{grid-template-columns:1fr}.pw-batches{grid-template-columns:repeat(2,minmax(0,1fr))}.pw-batches h3{grid-column:1/-1}.pw-create-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}
@media(max-width:620px){.pw-create-grid,.pw-anchor-grid{grid-template-columns:1fr}.pw-mode-row{grid-template-columns:1fr}.pw-photo-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.pw-batches{grid-template-columns:1fr}.pw-batches h3{grid-column:auto}}
</style>
