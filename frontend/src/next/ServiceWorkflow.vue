<script setup>
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { api, downloadFile } from './api'
import InspectionProfile from './InspectionProfile.vue'
import InspectionDocxWorkbench from './InspectionDocxWorkbench.vue'
import ReportReviewEvidence from './ReportReviewEvidence.vue'

const props = defineProps({ permissions: { type: Array, default: () => [] }, userId: { type: Number, default: 0 }, destination: {type:Object,default:null} })
const emit=defineEmits(['navigate'])
const can = p => props.permissions.includes(p)
const visibleTabs = computed(()=>[
  {id:'visits',name:'现场服务',permission:'service:visit:read'},
  {id:'issues',name:'问题清单',permission:'project:read'},
  {id:'photos',name:'照片批次',permission:'report:photo:read'},
  {id:'inspection',name:'检测资料档案',permission:'report:read'},
  {id:'reports',name:'报告与交付',permission:'report:read'}
].filter(x=>can(x.permission)))
const projects = ref([]), packages = ref([]), employees = ref([]), issues = ref([])
const visits = ref([]), batches = ref([]), reports = ref([]), deliveries = ref([])
const project = ref(null), workPackage = ref(null), batch = ref(null), report = ref(null)
const systems = ref([]), templateFile = ref(null), selectedBatches = ref([])
const library=ref([]),templateMode=ref('legacy'),baseTemplateId=ref(''),sectionIds=ref([]),addSectionId=ref(''),photoQuality=ref('print')
const baseOptions=computed(()=>library.value.filter(t=>t.template_kind==='base'))
const sectionOptions=computed(()=>library.value.filter(t=>t.template_kind==='system'))
const selectedBase=computed(()=>baseOptions.value.find(t=>t.id===baseTemplateId.value))
const selectedSections=computed(()=>sectionIds.value.map(id=>sectionOptions.value.find(t=>t.id===id)).filter(Boolean))
const recommendedBasis=computed(()=>{
 const linked=new Map();for(const t of [selectedBase.value,...selectedSections.value].filter(Boolean))for(const s of t.recommendedStandards||[])linked.set(s.id,s)
 const actual=new Set((report.value?.content?.inspectionProfile?.content?.basisReferences||[]).map(s=>s.id))
 return [...linked.values()].map(s=>({...s,chosen:actual.has(s.id)}))
})
const modular=computed(()=>templateMode.value==='library')
const templateReady=computed(()=>modular.value?!!selectedBase.value&&sectionIds.value.length>0:!!templateFile.value)
const effectiveTemplateId=computed(()=>modular.value?Number(selectedBase.value?.file_id||0):Number(templateFile.value?.id||0))
function resetTemplateSelection(){templateFile.value=null;showBodyBinding.value=false;baseTemplateId.value='';sectionIds.value=[];addSectionId.value='';templateMode.value='legacy'}
function switchTemplateMode(){showBodyBinding.value=false;selectedBatches.value=[]}
function addSection(){
 const chosen=sectionOptions.value.find(t=>t.id===addSectionId.value)
 if(!chosen)return
 if(selectedSections.value.some(t=>t.system_name===chosen.system_name)){error.value='同一消防系统只能选择一个模板版本';return}
 sectionIds.value.push(chosen.id);addSectionId.value='';showBodyBinding.value=false
}
function moveSection(i,delta){
 const j=i+delta;if(j<0||j>=sectionIds.value.length)return
 const ids=[...sectionIds.value];[ids[i],ids[j]]=[ids[j],ids[i]];sectionIds.value=ids;showBodyBinding.value=false
}
function removeSection(i){sectionIds.value.splice(i,1);showBodyBinding.value=false}
async function loadLibrary(){try{library.value=await api('/inspection-templates')}catch(e){error.value=e.message}}
const inspectionVersions=ref([]),inspectionVersion=ref(''),showBodyBinding=ref(false)
const selectedArtifact=ref(''),deliveryVersion=ref(''),historyVersion=ref(null)
const editableReport=computed(()=>!!report.value&&['draft','returned','withdrawn'].includes(report.value.status)&&can('report:write'))
function manifestFor(a){try{return typeof a?.generation_manifest==='string'?JSON.parse(a.generation_manifest):a?.generation_manifest||{}}catch{return {}}}
const reviewArtifact=computed(()=>(report.value?.artifacts||[]).find(a=>Number(a.id)===Number(report.value?.status==='draft'?selectedArtifact.value:report.value?.selectedArtifactId)))
const draftArtifacts=computed(()=>(report.value?.artifacts||[]).filter(a=>a.version_no===report.value?.current_version_no&&a.artifact_type==='docx'))
const issuedVersions=computed(()=>(report.value?.versions||[]).filter(v=>v.version_issued_at&&v.submitted_artifact_id))
function syncReportSelection(){
 const r=report.value;if(!r)return
 selectedArtifact.value=r.selectedArtifactId||draftArtifacts.value[0]?.id||''
 deliveryVersion.value=issuedVersions.value[0]?.version_no||''
 historyVersion.value=null
}
async function viewVersion(no){
 try{historyVersion.value=await api('/business/reports/'+report.value.id+'/versions/'+no)}catch(e){error.value=e.message}
}
const loading = ref(false), busy = ref(false), error = ref(''), notice = ref('')
const tab = ref('visits')
const packageOpen=ref(false),packageForm=ref({packageNo:'',title:'',description:'',assigneeEmployeeId:'',plannedStart:'',plannedEnd:''})
function newPackage(){
 packageForm.value={packageNo:(project.value?.project_no||'')+'-FW-'+String(packages.value.length+1).padStart(2,'0'),title:'现场技术服务',description:'',assigneeEmployeeId:'',plannedStart:'',plannedEnd:''}
 packageOpen.value=true
}
async function createPackage(){
 if(!project.value||busy.value)return
 const id=project.value.id;busy.value=true
 try{const f=packageForm.value;const row=await api('/business/projects/'+id+'/work-packages',{method:'POST',body:JSON.stringify({...f,assigneeEmployeeId:f.assigneeEmployeeId||null,plannedStart:f.plannedStart||null,plannedEnd:f.plannedEnd||null})});await selectProject(id);await selectPackage(row.id);packageOpen.value=false;notice.value='服务工作已建立'}catch(e){error.value=e.message}finally{busy.value=false}
}
const visitForm = ref({visitDate:new Date().toISOString().slice(0,10),visitType:'inspection',title:'现场检测',summary:'',resultSummary:'',staff:[]})
const issueForm = ref({title:'',description:'',severity:'medium',ownerEmployeeId:'',dueOn:''})
const batchForm = ref({title:'现场照片',visitDate:new Date().toISOString().slice(0,10),visitId:''})
const reportForm = ref({reportNo:'',title:'',reportType:'inspection',summary:'',scope:'',methods:'',findings:'',conclusion:''})
const deliveryForm = ref({deliveryMethod:'courier',recipientName:'',recipientPhone:'',recipientOrganization:'',recipientAddress:'',courierName:'',trackingNo:'',notes:''})
const previewUrls = ref({}),originalPreview=ref(''),originalLoading=ref(false)
let originalGeneration=0
function closeOriginal(){originalGeneration++;focusedPhoto.value=null;originalLoading.value=false;if(originalPreview.value)URL.revokeObjectURL(originalPreview.value);originalPreview.value=''}
async function focusPhoto(p){
 closeOriginal();focusedPhoto.value=p;const gen=++originalGeneration;originalLoading.value=true
 try{
  const token=sessionStorage.getItem('erp-next-token')
  const response=await fetch((import.meta.env.VITE_NEXT_API_BASE||'/next-api')+'/files/'+p.file_id+'/download',{headers:token?{Authorization:'Bearer '+token}:{}})
  if(!response.ok)throw Error('原图加载失败')
  const blob=await response.blob();if(gen===originalGeneration)originalPreview.value=URL.createObjectURL(blob)
 }catch(e){if(gen===originalGeneration)error.value=e.message}finally{if(gen===originalGeneration)originalLoading.value=false}
}
const photoView=ref('cards'),photoMode=ref('append')
const photoDrafts = ref({})
const photoFilter=ref('all'),photoSelection=ref([]),bulkSystem=ref(''),bulkConfirm=ref(false),focusedPhoto=ref(null)
const photoEditable=computed(()=>batch.value?.status==='collecting'&&can('report:photo:write'))
const photoRows=computed(()=>batch.value?.photos||[])
const photoGroups=computed(()=>[
  {key:'all',name:'全部',count:photoRows.value.length},
  {key:'unclassified',name:'未分类',count:photoRows.value.filter(p=>!p.system_code).length},
  {key:'pending',name:'待确认',count:photoRows.value.filter(p=>p.confirmation_status!=='confirmed').length},
  ...systems.value.map(s=>({key:s,name:s,count:photoRows.value.filter(p=>p.system_code===s).length})).filter(g=>g.count)
])
const visiblePhotos=computed(()=>photoRows.value.filter(p=>photoFilter.value==='all'||(photoFilter.value==='unclassified'?!p.system_code:photoFilter.value==='pending'?p.confirmation_status!=='confirmed':p.system_code===photoFilter.value)))
function selectFilter(key){photoFilter.value=key;photoSelection.value=[];bulkConfirm.value=false}
function selectVisible(){photoSelection.value=visiblePhotos.value.map(p=>p.id);bulkConfirm.value=false}
function resetPhotoSelection(){photoSelection.value=[];bulkSystem.value='';bulkConfirm.value=false;closeOriginal()}
function draft(p){return {systemCode:p.system_code||'',takenOn:p.taken_on||'',caption:p.caption||'',confirmed:p.confirmation_status==='confirmed',sortOrder:p.sort_order}}
function dirty(p){return JSON.stringify(photoDrafts.value[p.id])!==JSON.stringify(draft(p))}
async function applyClassification(){
  if(busy.value||!photoEditable.value||!photoSelection.value.length)return
  const ids=[...photoSelection.value],id=batch.value.id
  if(photoRows.value.some(p=>ids.includes(p.id)&&dirty(p))&&!confirm('所选照片有未保存的单张编辑。批量操作将以已保存记录为准，继续？'))return
  if(bulkConfirm.value&&!confirm('确认已人工核对所选全部照片的系统与已保存日期？'))return
  busy.value=true;error.value=''
  try{
    const result=await api('/service-workflow/photo-batches/'+id+'/classification',{method:'PUT',body:JSON.stringify({photoIds:ids,systemCode:bulkSystem.value||null,confirmed:bulkConfirm.value})})
    if(batch.value?.id!==id)return
    batch.value=result
    for(const p of result.photos)if(ids.includes(p.id))photoDrafts.value[p.id]=draft(p)
    resetPhotoSelection();notice.value='已保存 '+ids.length+' 张照片的分类'
  }catch(e){error.value=e.message}finally{busy.value=false}
}
let previewGeneration=0
function releasePreviews(){previewGeneration++;Object.values(previewUrls.value).forEach(URL.revokeObjectURL);previewUrls.value={};closeOriginal()}
function closeBatch(){releasePreviews();batch.value=null;photoDrafts.value={};selectFilter('all');resetPhotoSelection()}
const batchReviewReason = ref('')
const visitIssueSelection = ref({})

async function load() {
  loading.value=true; error.value=''
  try {
    const [p,e,s] = await Promise.all([
      api('/service-workflow/projects'),
      can('employee:read')?api('/business/employees'):Promise.resolve([]),
      can('report:photo:read')?api('/service-workflow/photo-systems'):Promise.resolve([])
    ])
    projects.value=p; employees.value=e; systems.value=s
    if(!visibleTabs.value.some(t=>t.id===tab.value))tab.value=visibleTabs.value[0]?.id||''
    if(project.value){const packageId=workPackage.value?.id;await selectProject(project.value.id);if(packageId&&packages.value.some(p=>p.id===packageId))await selectPackage(packageId)}
  } catch(e){error.value=e.message} finally{loading.value=false}
}
async function selectProject(id) {
  error.value=''; project.value=projects.value.find(x=>x.id===id)||null
  packageOpen.value=false;workPackage.value=null; closeBatch(); report.value=null
  packages.value=[];visits.value=[];batches.value=[];reports.value=[];issues.value=[];deliveries.value=[];selectedBatches.value=[];templateFile.value=null
  if(!project.value)return
  try { const rows=await api('/service-workflow/projects/'+id+'/work-packages');if(project.value?.id===id)packages.value=rows }
  catch(e){error.value=e.message}
}
async function selectPackage(id) {
  workPackage.value=packages.value.find(x=>x.id===id)||null
  closeBatch(); report.value=null; selectedBatches.value=[];templateFile.value=null;showBodyBinding.value=false;inspectionVersions.value=[];inspectionVersion.value='';visits.value=[];batches.value=[];reports.value=[];issues.value=[];deliveries.value=[]
  if(!id)return
  try {
    const [v,b,r,i]=await Promise.all([
      can('service:visit:read')?api('/service-workflow/work-packages/'+id+'/visits'):Promise.resolve([]),
      can('report:photo:read')?api('/service-workflow/work-packages/'+id+'/photo-batches'):Promise.resolve([]),
      can('report:read')?api('/business/work-packages/'+id+'/reports'):Promise.resolve([]),
      can('project:read')?api('/business/projects/'+project.value.id+'/issues'):Promise.resolve([])
    ])
    if(workPackage.value?.id!==id)return
    visits.value=v; batches.value=b; reports.value=r; issues.value=i
    if(can('report:read')){const versions=await api('/service-workflow/work-packages/'+id+'/inspection-profile/versions');if(workPackage.value?.id===id)inspectionVersions.value=versions}
  } catch(e){error.value=e.message}
}
async function createVisit() {
  busy.value=true;error.value='';try{
    await api('/service-workflow/work-packages/'+workPackage.value.id+'/visits',{method:'POST',body:JSON.stringify(visitForm.value)})
    visitForm.value={...visitForm.value,title:'补充现场服务',summary:'',resultSummary:'',staff:[]}
    await selectPackage(workPackage.value.id);notice.value='现场服务记录已保存'
  }catch(e){error.value=e.message}finally{busy.value=false}
}
async function createIssue() {
  busy.value=true;error.value='';try{
    const b={...issueForm.value};if(!b.ownerEmployeeId)b.ownerEmployeeId=null;if(!b.dueOn)b.dueOn=null
    await api('/business/projects/'+project.value.id+'/issues',{method:'POST',body:JSON.stringify(b)})
    issueForm.value={title:'',description:'',severity:'medium',ownerEmployeeId:'',dueOn:''}
    issues.value=await api('/business/projects/'+project.value.id+'/issues');notice.value='问题清单已登记'
  }catch(e){error.value=e.message}finally{busy.value=false}
}
async function linkIssueToVisit(visit) {
  const issueId=Number(visitIssueSelection.value[visit.id])
  if(!issueId)return
  try{
    await api('/service-workflow/visits/'+visit.id+'/issues',{method:'POST',body:JSON.stringify({issueId,relationType:'reviewed'})})
    visitIssueSelection.value[visit.id]=''
    await selectPackage(workPackage.value.id)
    notice.value='问题已关联到本次现场服务'
  }catch(e){error.value=e.message}
}
async function changeIssue(issue,status) {
  const resolution=status==='resolved'||status==='closed'?prompt('填写整改/复核结果：'):null
  if((status==='resolved'||status==='closed')&&!resolution)return
  try { await api('/business/issues/'+issue.id,{method:'PUT',body:JSON.stringify({status,resolution})});issues.value=await api('/business/projects/'+project.value.id+'/issues') }
  catch(e){error.value=e.message}
}
async function createBatch() {
  busy.value=true;error.value='';try{
    const b=await api('/service-workflow/work-packages/'+workPackage.value.id+'/photo-batches',{method:'POST',body:JSON.stringify({...batchForm.value,visitId:batchForm.value.visitId||null})})
    await selectPackage(workPackage.value.id);await openBatch(b.id);notice.value='照片批次已创建'
  }catch(e){error.value=e.message}finally{busy.value=false}
}
async function openBatch(id,changedIds=null) {
  try {
    if(batch.value?.id!==id)closeBatch()
    const generation=++previewGeneration
    const result=await api('/service-workflow/photo-batches/'+id)
    if(generation!==previewGeneration)return
    const oldRows=new Map((batch.value?.photos||[]).map(p=>[p.id,p]))
    const oldDrafts=photoDrafts.value
    batch.value=result
    photoDrafts.value=Object.fromEntries(result.photos.map(p=>[p.id,changedIds&&!changedIds.includes(p.id)&&oldRows.has(p.id)&&JSON.stringify(oldDrafts[p.id])!==JSON.stringify(draft(oldRows.get(p.id)))?oldDrafts[p.id]:draft(p)]))
    photoSelection.value=photoSelection.value.filter(id=>result.photos.some(p=>p.id===id))
    if(can('file:read')){
      const pending=result.photos.filter(p=>!previewUrls.value[p.file_id])
      // Four concurrent downloads, independent failure, stop when changing batches.
      await Promise.all(Array.from({length:Math.min(4,pending.length)},async ()=>{
        while(pending.length&&generation===previewGeneration){
          const p=pending.shift()
          try{await loadPreview(p.file_id,generation)}catch(e){if(generation===previewGeneration)error.value='部分照片预览加载失败，可刷新重试'}
        }
      }))
    }
  }catch(e){error.value=e.message}
}
async function loadPreview(fileId,generation=previewGeneration) {
  const token=sessionStorage.getItem('erp-next-token')
  const response=await fetch((import.meta.env.VITE_NEXT_API_BASE||'/next-api')+'/files/'+fileId+'/thumbnail',{headers:token?{Authorization:'Bearer '+token}:{}})
  if(!response.ok)throw new Error('照片预览加载失败')
  const blob=await response.blob()
  if(generation===previewGeneration&&!previewUrls.value[fileId])previewUrls.value[fileId]=URL.createObjectURL(blob)
}
async function importPhotos(event) {
  const files=Array.from(event.target.files||[]);event.target.value=''
  if(!photoEditable.value||busy.value)return
  busy.value=true;error.value='';let added=0
  try{
    for(const file of files){
      if(!['image/jpeg','image/png'].includes(file.type)){continue}
      const form=new FormData();form.append('file',file)
      const uploaded=await api('/files',{method:'POST',body:form})
      await api('/service-workflow/photo-batches/'+batch.value.id+'/photos',{method:'POST',body:JSON.stringify({fileId:uploaded.id,takenOn:batch.value.visit_date})})
      added++
    }
    const batchId=batch.value.id;await selectPackage(workPackage.value.id);await openBatch(batchId)
    notice.value='已导入 '+added+' 张照片；可多选批量分类，日期需人工核对'
  }catch(e){error.value=e.message}finally{busy.value=false}
}
async function savePhoto(photo) {
  const d=photoDrafts.value[photo.id]
  try{
    await api('/service-workflow/photos/'+photo.id,{method:'PUT',body:JSON.stringify({...d,confirmed:d.confirmed})})
    await openBatch(batch.value.id,[photo.id]);notice.value='照片信息已保存'
  }catch(e){error.value=e.message}
}
async function movePhoto(photo,delta) {
  const ordered=[...batch.value.photos].sort((a,b)=>a.sort_order-b.sort_order)
  const index=ordered.findIndex(x=>x.id===photo.id),target=index+delta
  if(target<0||target>=ordered.length)return
  const other=ordered[target]
  const a=photoDrafts.value[photo.id],b=photoDrafts.value[other.id]
  const current=a.sortOrder;a.sortOrder=b.sortOrder;b.sortOrder=current
  try {
    await Promise.all([
      api('/service-workflow/photos/'+photo.id,{method:'PUT',body:JSON.stringify({...a,confirmed:a.confirmed})}),
      api('/service-workflow/photos/'+other.id,{method:'PUT',body:JSON.stringify({...b,confirmed:b.confirmed})})
    ])
    await openBatch(batch.value.id,[photo.id,other.id])
  } catch(e){error.value=e.message}
}
async function removePhoto(photo) {
  if(!confirm('确定从当前批次移除这张照片？'))return
  try{await api('/service-workflow/photos/'+photo.id,{method:'DELETE'});await openBatch(batch.value.id,[photo.id])}catch(e){error.value=e.message}
}
async function submitBatch() {
  try{const batchId=batch.value.id;await api('/service-workflow/photo-batches/'+batchId+'/submit',{method:'POST'});await selectPackage(workPackage.value.id);await openBatch(batchId);notice.value='照片批次已提交内部审核'}catch(e){error.value=e.message}
}
async function returnBatch(){
 const reason=prompt('请填写退回原因：');if(!reason?.trim()||busy.value)return
 const id=batch.value.id;busy.value=true
 try{await api('/service-workflow/photo-batches/'+id+'/return',{method:'POST',body:JSON.stringify({reason})});await selectPackage(workPackage.value.id);await openBatch(id);notice.value='照片批次已退回整理'}catch(e){error.value=e.message}finally{busy.value=false}
}
async function reviewBatch() {
  try{const batchId=batch.value.id;await api('/service-workflow/photo-batches/'+batchId+'/review',{method:'POST'});await selectPackage(workPackage.value.id);await openBatch(batchId);notice.value='照片批次审核通过'}catch(e){error.value=e.message}
}
async function createReport() {
  busy.value=true;error.value='';try{
    const f=reportForm.value
    const r=await api('/business/work-packages/'+workPackage.value.id+'/reports',{method:'POST',body:JSON.stringify({reportNo:f.reportNo,title:f.title,reportType:f.reportType,...(f.reportType==='inspection'&&inspectionVersion.value?{inspectionProfileVersionNo:Number(inspectionVersion.value)}:{}),content:{summary:f.summary,scope:f.scope,methods:f.methods,findings:f.findings,conclusion:f.conclusion}})})
    reportForm.value={reportNo:'',title:'',reportType:'inspection',summary:'',scope:'',methods:'',findings:'',conclusion:''}
    await selectPackage(workPackage.value.id);await openReport(r.id);notice.value='报告草稿已创建'
  }catch(e){error.value=e.message}finally{busy.value=false}
}
async function openReport(id) {
  if(report.value?.id!==id){resetTemplateSelection();selectedBatches.value=[];if(can('report:read'))await loadLibrary()}
  try{report.value=await api('/business/reports/'+id);const x=report.value.content||{};inspectionVersion.value=x.inspectionProfile?.versionNo||'';reportForm.value={reportNo:report.value.report_no,title:report.value.title,reportType:report.value.report_type,summary:x.summary||'',scope:x.scope||'',methods:x.methods||'',findings:x.findings||'',conclusion:x.conclusion||''};syncReportSelection();deliveries.value=can('delivery:read')?await api('/service-workflow/reports/'+id+'/deliveries'):[]}
  catch(e){error.value=e.message}
}
async function saveReport() {
  if(!report.value)return;busy.value=true
  try{const f=reportForm.value;report.value=await api('/business/reports/'+report.value.id,{method:'PUT',body:JSON.stringify({expectedVersionNo:report.value.current_version_no,title:f.title,...(report.value.report_type==='inspection'?{inspectionProfileVersionNo:inspectionVersion.value?Number(inspectionVersion.value):null}:{}),content:{summary:f.summary,scope:f.scope,methods:f.methods,findings:f.findings,conclusion:f.conclusion}})});await openReport(report.value.id);notice.value='报告内容已保存为新版本'}
  catch(e){error.value=e.message}finally{busy.value=false}
}
async function reportAction(action) {
  if(!report.value)return
  if(busy.value)return
  const body={expectedVersionNo:report.value.current_version_no}
  if(['return','withdraw'].includes(action)){body.reason=prompt('请填写原因：')||'';if(!body.reason)return}
  if(action==='submit'){
    if(selectedArtifact.value)body.artifactId=Number(selectedArtifact.value)
    if(!confirm('确认送审当前已保存版本及所选文件？未保存的正文修改不会提交。'))return
  }
  if(action==='submit'&&manifestFor(reviewArtifact.value).tocPending){error.value='请下载 Word、更新整个目录并保存，再关联本文件上传最终版。';return}
  if(['submit','approve'].includes(action)&&manifestFor(reviewArtifact.value).layout==='modular-photo-pages-v1'){
    if(!confirm('确认已在 Word 核对当前文件目录、页码、分页及照片清晰度？如需修改，应重新登记文件。'))return
    body.layoutReviewed=true
  }
  if(action==='issue'&&!confirm('确认签发已审核的版本及对应文件？'))return
  busy.value=true
  try{const reportId=report.value.id;await api('/business/reports/'+reportId+'/'+action,{method:'POST',body:JSON.stringify(body)});await selectPackage(workPackage.value.id);await openReport(reportId);notice.value='报告状态已更新'}
  catch(e){error.value=e.message}finally{busy.value=false}
}
async function registerEditedDocx(event){
 const file=event.target.files?.[0];event.target.value=''
 if(!file||busy.value||!report.value)return
 if(!confirm('确认该 Word 文件已经人工核对正文、照片、结论及旧资料，并对应当前报告版本？'))return
 const id=report.value.id,version=report.value.current_version_no
 const source=reviewArtifact.value,manifest=manifestFor(source)
 const linked=source&&confirm('此文件是否基于当前选中的 DOCX 修改？确认将保留来源文件关联；取消则登记为独立人工文件。')
 const tocUpdated=!!(linked&&manifest.tocPending&&confirm('已在 Word 中更新整个目录、检查页码并保存？'))
 if(linked&&manifest.tocPending&&!tocUpdated)return
 const sourceEvidenceConfirmed=!!(linked&&manifest.photos?.length&&confirm('最终 Word 中仍使用来源文件的同一组照片？确认后保留来源照片清单；取消则不继承。'))
 busy.value=true
 try{
  const form=new FormData();form.append('file',file)
  const uploaded=await api('/files',{method:'POST',body:form})
  const result=await api('/service-workflow/reports/'+id+'/register-docx',{method:'POST',body:JSON.stringify({fileId:uploaded.id,expectedVersionNo:version,manualReviewed:true,...(linked?{sourceArtifactId:source.id,tocUpdated,sourceEvidenceConfirmed}:{})})})
  if(report.value?.id===id){await openReport(id);selectedArtifact.value=result.artifactId;notice.value='人工编辑 DOCX 已登记为本版本候选文件'}
 }catch(e){error.value=e.message}finally{busy.value=false}
}
async function chooseTemplate(event) {
  const file=event.target.files?.[0];event.target.value='';if(!file)return
  try{const form=new FormData();form.append('file',file);const uploaded=await api('/files',{method:'POST',body:form});templateFile.value=uploaded;showBodyBinding.value=false;notice.value='报告模板已上传'}
  catch(e){error.value=e.message}
}
async function refreshGenerated(result){
  if(report.value?.id!==result.reportId)return
  try{report.value=await api('/business/reports/'+result.reportId);syncReportSelection();batches.value=can('report:photo:read')?await api('/service-workflow/work-packages/'+workPackage.value.id+'/photo-batches'):[];selectedBatches.value=[];notice.value='DOCX 草稿已归档'+(result.fileBytes?' · '+(result.fileBytes/1024/1024).toFixed(2)+' MB · '+result.lowDpiCount+' 张低于200 DPI（请核对原图）':'')}
  catch(e){error.value=e.message}
}
async function generateDocx() {
  if(!report.value||!templateFile.value||!selectedBatches.value.length){error.value='请先选择 DOCX 模板和已审核照片批次';return}
  try{
    const result=await api('/service-workflow/reports/'+report.value.id+'/generate-docx',{method:'POST',body:JSON.stringify({templateFileId:templateFile.value.id,expectedVersionNo:report.value.current_version_no,batchIds:selectedBatches.value,photoMode:photoMode.value,photoQuality:photoQuality.value})})
    await refreshGenerated(result);if(can('file:read'))await downloadFile(result.fileId,result.filename);notice.value='DOCX 已生成并归档到报告版本 '+result.versionNo
  }catch(e){error.value=e.message}
}
async function createDelivery() {
  if(!report.value)return
  try{await api('/service-workflow/reports/'+report.value.id+'/deliveries',{method:'POST',body:JSON.stringify({versionNo:Number(deliveryVersion.value),...deliveryForm.value})});deliveries.value=await api('/service-workflow/reports/'+report.value.id+'/deliveries');notice.value='报告交付登记已创建'}
  catch(e){error.value=e.message}
}
async function updateDelivery(delivery,status) {
  const trackingNo= status==='dispatched'?prompt('快递单号（可留空）：',delivery.tracking_no||''):null
  try{await api('/service-workflow/deliveries/'+delivery.id+'/status',{method:'POST',body:JSON.stringify({status,trackingNo})});deliveries.value=await api('/service-workflow/reports/'+report.value.id+'/deliveries')}
  catch(e){error.value=e.message}
}
function batchLabel(status){return ({collecting:'整理中',submitted:'待审核',reviewed:'已审核',locked:'已用于报告'})[status]||status}
function reportLabel(status){return ({draft:'草稿',submitted:'待审核',reviewing:'审核中',returned:'已退回',approved:'审核通过',issued:'已签发',archived:'已归档',withdrawn:'已撤回'})[status]||status}
onMounted(async ()=>{
  await load()
  if(props.destination?.id) {
    await selectProject(props.destination.id)
    if(packages.value.length===1)await selectPackage(packages.value[0].id)
  }
})
onBeforeUnmount(releasePreviews)
</script>

<template>
<section class="service-workflow">
  <div class="sw-head"><div><span class="sw-kicker">TECHNICAL SERVICE · DELIVERY</span><h2>技术服务交付</h2><p>现场执行、问题整改、照片整理、报告审核和交付登记，按项目与服务批次留痕。</p></div><button :disabled="busy" @click="load">刷新</button></div>
  <p v-if="error" class="sw-msg error">{{ error }}</p><p v-if="notice" class="sw-msg success">{{ notice }}</p>
  <div v-if="project&&can('project:read')" class="report-actions"><button class="secondary-button" @click="emit('navigate','business','projects',project.id)">返回项目档案</button></div>
  <div class="sw-selects">
    <label>项目<select :value="project?.id||''" :disabled="busy" @change="selectProject(Number($event.target.value)||null)"><option value="">选择项目</option><option v-for="p in projects" :key="p.id" :value="p.id">{{ p.project_no }} · {{ p.name }}</option></select></label>
    <label>服务工作包<select :value="workPackage?.id||''" @change="selectPackage(Number($event.target.value)||null)" :disabled="busy||!project"><option value="">选择服务工作包</option><option v-for="w in packages" :key="w.id" :value="w.id">{{ w.package_no }} · {{ w.title }}</option></select></label>
  </div>
  <div v-if="project&&can('service:write')" class="sw-bottom-actions"><button :disabled="busy" @click="newPackage">新建服务工作</button></div>
  <form v-if="project&&packageOpen&&can('service:write')" class="sw-panel sw-form" @submit.prevent="createPackage"><label>编号<input v-model="packageForm.packageNo" required maxlength="80"></label><label>工作名称<input v-model="packageForm.title" required maxlength="240"></label><label>执行人员<select v-model="packageForm.assigneeEmployeeId"><option value="">暂不分配</option><option v-for="e in employees.filter(x=>x.status==='active')" :key="e.id" :value="e.id">{{e.full_name}}</option></select></label><label>计划开始<input type="date" v-model="packageForm.plannedStart"></label><label>计划结束<input type="date" v-model="packageForm.plannedEnd"></label><label class="wide">备注<textarea v-model="packageForm.description" rows="2"></textarea></label><button class="primary" :disabled="busy">创建并打开</button><button type="button" :disabled="busy" @click="packageOpen=false">取消</button></form>
  <div v-if="!workPackage" class="sw-empty">选择项目和服务工作后，管理现场服务、问题、照片与报告。尚无服务工作时，可在上方创建。</div>
  <template v-else>
    <nav class="sw-tabs">
      <button v-for="t in visibleTabs" :key="t.id" :class="{active:tab===t.id}" :disabled="busy" @click="tab=t.id">{{ t.name }}</button>
    </nav>

    <div v-if="tab==='visits'" class="sw-panel">
      <div class="sw-panel-title"><h3>多次现场服务记录</h3><span>{{ visits.length }} 次</span></div>
      <form v-if="can('service:visit:write')" class="sw-form" @submit.prevent="createVisit">
        <label>到场日期<input v-model="visitForm.visitDate" type="date" required></label>
        <label>服务类型<select v-model="visitForm.visitType"><option value="inspection">首次检测</option><option value="rectification_review">整改复核</option><option value="supplement">补充采集</option><option value="maintenance">维护保养</option><option value="other">其他</option></select></label>
        <label>标题<input v-model="visitForm.title" required></label>
        <label class="wide">执行人员<select v-model="visitForm.staff" multiple><option v-for="e in employees.filter(x=>x.status==='active')" :key="e.id" :value="{employeeId:e.id,role:e.employee_type==='engineer'?'engineer':'field_operator'}">{{ e.full_name }}（{{ e.employee_type==='engineer'?'工程师':e.employee_type==='operator'?'操作员':'员工' }}）</option></select></label>
        <label class="wide">现场情况<textarea v-model="visitForm.summary" rows="2"></textarea></label>
        <label class="wide">结果/待办<textarea v-model="visitForm.resultSummary" rows="2"></textarea></label>
        <button class="primary" :disabled="busy">保存现场记录</button>
      </form>
      <div class="sw-list">
        <article v-for="v in visits" :key="v.id"><div><b>{{ v.title }}</b><small>{{ v.visit_date }} · {{ ({inspection:'现场检测',rectification_review:'整改复核',supplement:'补充采集',maintenance:'维护保养',other:'其他'})[v.visit_type] }}</small><p>{{ v.summary||'暂无现场记录' }}</p><p>现场人员：{{ v.staff_names||'未登记' }}</p></div><div class="sw-visit-links"><span class="sw-pill">{{ v.staff_count }} 位人员 · {{ v.issue_count }} 项问题</span><template v-if="can('project:read')&&can('service:visit:write')"><select v-model="visitIssueSelection[v.id]"><option value="">选择问题清单项</option><option v-for="item in issues" :key="item.id" :value="item.id">#{{item.id}} · {{item.title}}</option></select><button :disabled="!visitIssueSelection[v.id]" @click="linkIssueToVisit(v)">关联问题</button></template></div></article>
        <div v-if="!visits.length" class="sw-empty small">尚未登记现场服务记录</div>
      </div>
    </div>

    <div v-if="tab==='issues'" class="sw-panel">
      <div class="sw-panel-title"><h3>问题清单与整改跟踪</h3><span>{{ issues.length }} 项</span></div>
      <form class="sw-form" @submit.prevent="createIssue">
        <label>问题标题<input v-model="issueForm.title" required></label>
        <label>严重程度<select v-model="issueForm.severity"><option value="low">低</option><option value="medium">中</option><option value="high">高</option><option value="critical">严重</option></select></label>
        <label>责任人<select v-model="issueForm.ownerEmployeeId"><option value="">待指定</option><option v-for="e in employees" :key="e.id" :value="e.id">{{ e.full_name }}</option></select></label>
        <label>整改期限<input v-model="issueForm.dueOn" type="date"></label>
        <label class="wide">问题描述<textarea v-model="issueForm.description" rows="2"></textarea></label>
        <button class="primary" :disabled="busy">登记问题</button>
      </form>
      <div class="sw-list">
        <article v-for="i in issues" :key="i.id"><div><b>#{{ i.id }} · {{ i.title }}</b><small>{{ i.severity }} · {{ i.owner_name||'未分配' }} · 截止 {{ i.due_on||'未设置' }}</small><p>{{ i.description||'暂无描述' }}</p><p v-if="i.resolution" class="sw-resolution">处理结果：{{ i.resolution }}</p></div><div class="sw-issue-actions"><span class="sw-pill">{{ ({open:'待整改',in_progress:'整改中',resolved:'已解决',closed:'已关闭'})[i.status] }}</span><button v-if="i.status==='open'" @click="changeIssue(i,'in_progress')">开始整改</button><button v-if="i.status==='in_progress'||i.status==='open'" @click="changeIssue(i,'resolved')">提交复核</button><button v-if="i.status==='resolved'" @click="changeIssue(i,'closed')">关闭</button></div></article>
        <div v-if="!issues.length" class="sw-empty small">当前没有问题清单</div>
      </div>
    </div>

    <div v-if="tab==='photos'" class="sw-panel">
      <div class="sw-panel-title"><h3>现场照片批次</h3><span>{{ batches.length }} 批</span></div>
      <div v-if="!batch" class="sw-two">
        <form v-if="can('report:photo:write')" class="sw-form" @submit.prevent="createBatch">
          <label>批次名称<input v-model="batchForm.title" required></label><label>现场日期<input v-model="batchForm.visitDate" type="date" required></label>
          <label class="wide">关联现场服务<select v-model="batchForm.visitId"><option value="">不关联特定到场记录</option><option v-for="v in visits" :key="v.id" :value="v.id">{{ v.visit_date }} · {{ v.title }}</option></select></label>
          <button class="primary">创建照片批次</button>
        </form>
        <div class="sw-list">
          <article v-for="b in batches" :key="b.id"><div><b>{{ b.title }}</b><small>{{ b.visit_date }} · {{ b.photo_count }} 张照片</small></div><div><span class="sw-pill">{{ batchLabel(b.status) }}</span><button @click="openBatch(b.id)">打开</button></div></article>
          <div v-if="!batches.length" class="sw-empty small">尚未创建照片批次</div>
        </div>
      </div>
      <div v-else class="sw-batch-view">
        <div class="sw-panel-title"><div><button class="back" :disabled="busy" @click="closeBatch">← 批次列表</button><h3>{{ batch.title }}</h3><small>{{ batch.visit_date }} · {{ batch.photos.length }} 张照片</small></div><span class="sw-pill">{{ batchLabel(batch.status) }}</span></div>
        <div class="sw-upload-row"><label v-if="photoEditable&&can('file:upload')" class="primary">＋ 批量导入照片<input type="file" hidden multiple accept="image/jpeg,image/png" :disabled="busy" @change="importPhotos"></label><button @click="photoView=photoView==='cards'?'table':'cards'">{{photoView==='cards'?'表格视图':'卡片视图'}}</button><small>支持 JPG / PNG；分类及拍摄日期需要人工核对。</small></div>
        <div class="photo-filters"><button v-for="g in photoGroups" :key="g.key" :class="{active:photoFilter===g.key}" @click="selectFilter(g.key)">{{g.name}} · {{g.count}}</button></div>
        <div v-if="photoEditable" class="sw-upload-row">
          <button :disabled="busy||!visiblePhotos.length" @click="selectVisible">全选当前 {{visiblePhotos.length}} 张</button><button :disabled="busy" @click="resetPhotoSelection">取消选择</button><b>已选 {{photoSelection.length}} 张</b>
          <select v-model="bulkSystem" :disabled="busy" @change="bulkConfirm=false"><option value="">设为未分类</option><option v-for="s in systems" :key="s">{{s}}</option></select>
          <label><input v-model="bulkConfirm" type="checkbox" :disabled="busy||!bulkSystem"> 已核对所选照片分类和日期</label>
          <button class="primary" :disabled="busy||!photoSelection.length" @click="applyClassification">批量保存分类</button>
        </div>
        <p v-if="!visiblePhotos.length" class="sw-empty small">当前分组暂无照片</p>
        <div v-if="photoView==='cards'" class="sw-photo-grid">
          <article v-for="p in visiblePhotos" :key="p.id" class="sw-photo-card" :class="{picked:photoSelection.includes(p.id)}">
            <label v-if="photoEditable" class="photo-select"><input type="checkbox" v-model="photoSelection" :value="p.id" :disabled="busy" @change="bulkConfirm=false" aria-label="选择照片"> 选择</label>
            <img v-if="previewUrls[p.file_id]" :src="previewUrls[p.file_id]" :alt="p.original_name" loading="lazy" class="photo-zoom" @click="focusPhoto(p)"><div v-else class="sw-photo-placeholder">{{can('file:read')?'加载预览…':'无文件查看权限'}}</div>
            <div class="sw-photo-meta"><b :title="p.original_name">{{ p.original_name }}</b>
              <template v-if="photoEditable">
                <label>所属系统<select v-model="photoDrafts[p.id].systemCode" @change="photoDrafts[p.id].confirmed=false"><option value="">请选择一级系统</option><option v-for="s in systems" :key="s" :value="s">{{ s }}</option></select></label>
                <label>拍摄日期<input v-model="photoDrafts[p.id].takenOn" @change="photoDrafts[p.id].confirmed=false" type="date"></label>
                <label>照片说明<input v-model="photoDrafts[p.id].caption" placeholder="可选"></label>
                <label class="confirm"><input v-model="photoDrafts[p.id].confirmed" type="checkbox"> 已人工核对</label>
                <div class="sw-row-actions"><button  :disabled="busy" @click="movePhoto(p,-1)">↑</button><button  :disabled="busy" @click="movePhoto(p,1)">↓</button><button  :disabled="busy" @click="savePhoto(p)">保存</button><button class="danger"  :disabled="busy" @click="removePhoto(p)">移除</button></div>
              </template>
              <template v-else><small>{{ p.system_code||'未分类' }} · {{ p.taken_on||'无日期' }}</small><small>{{ p.confirmation_status==='confirmed'?'人工已确认':'未确认' }}</small></template>
            </div>
          </article>
        </div>
        <div v-else class="sw-photo-table-wrap"><table class="sw-photo-table"><thead><tr><th v-if="photoEditable">选择</th><th>预览</th><th>文件名</th><th>一级系统</th><th>拍摄日期</th><th>人工确认</th><th>操作</th></tr></thead><tbody>
          <tr v-for="p in visiblePhotos" :key="p.id"><td v-if="photoEditable"><input type="checkbox" v-model="photoSelection" :value="p.id" :disabled="busy" @change="bulkConfirm=false" aria-label="选择照片"></td><td><img v-if="previewUrls[p.file_id]" :src="previewUrls[p.file_id]" :alt="p.original_name" loading="lazy" class="photo-zoom" @click="focusPhoto(p)"></td><td>{{p.original_name}}</td>
          <td v-if="photoEditable"><select v-model="photoDrafts[p.id].systemCode" @change="photoDrafts[p.id].confirmed=false"><option value="">未分类</option><option v-for="x in systems" :key="x" :value="x">{{x}}</option></select></td><td v-else>{{p.system_code||'未分类'}}</td>
          <td v-if="photoEditable"><input v-model="photoDrafts[p.id].takenOn" @change="photoDrafts[p.id].confirmed=false" type="date"></td><td v-else>{{p.taken_on||'—'}}</td>
          <td v-if="photoEditable"><input v-model="photoDrafts[p.id].confirmed" type="checkbox"></td><td v-else>{{p.confirmation_status==='confirmed'?'已确认':'未确认'}}</td>
          <td v-if="photoEditable"><button  :disabled="busy" @click="movePhoto(p,-1)">↑</button> <button  :disabled="busy" @click="movePhoto(p,1)">↓</button> <button  :disabled="busy" @click="savePhoto(p)">保存</button> <button  :disabled="busy" @click="removePhoto(p)">移除</button></td><td v-else>—</td></tr>
        </tbody></table></div>
        <div v-if="focusedPhoto" class="photo-lightbox" role="dialog" aria-modal="true" aria-label="照片预览" @click.self="closeOriginal()" @keydown.esc="closeOriginal()"><div><button autofocus @click="closeOriginal()">关闭预览</button><p>{{focusedPhoto.original_name}} · {{focusedPhoto.system_code||'未分类'}}</p><p v-if="originalLoading">正在读取原始照片…</p><a v-if="originalPreview" :href="originalPreview" target="_blank" rel="noopener">以原始像素打开</a><img :src="originalPreview||previewUrls[focusedPhoto.file_id]" :alt="focusedPhoto.original_name"></div></div>
        <div v-if="photoEditable" class="sw-bottom-actions"><button class="primary" :disabled="busy||photoRows.some(dirty)" @click="submitBatch">提交照片审核</button><small>{{photoRows.some(dirty)?'请先保存所有单张编辑，再提交审核。':'所有照片必须完成分类、日期和人工确认。'}}</small></div>
        <div v-if="batch.status==='submitted'&&can('report:review')&&Number(batch.created_by)!==props.userId" class="sw-bottom-actions"><button class="primary" @click="reviewBatch">审核通过照片批次</button><button :disabled="busy" @click="returnBatch">退回整理</button></div><details v-if="batch.reviewHistory?.length"><summary>照片审核历史</summary><div v-for="(h,i) in batch.reviewHistory" :key="i">{{h.occurred_at}} · {{h.actor_name}} · {{({ 'report.photo_batch.submitted':'提交','report.photo_batch.reviewed':'通过','report.photo_batch.returned':'退回'})[h.action]}} · {{typeof h.summary_json==='string'?JSON.parse(h.summary_json).summary:h.summary_json?.summary}}</div></details>
      </div>
    </div>

    <InspectionProfile v-if="tab==='inspection'" :key="workPackage.id" :work-package-id="Number(workPackage.id)" :project-name="project?.name||''" :permissions="props.permissions" />
    <div v-if="tab==='reports'" class="sw-panel">
      <div class="sw-panel-title"><h3>检测报告与交付</h3><span>{{ reports.length }} 份</span></div>
      <div v-if="!report" class="sw-two">
        <form v-if="can('report:write')" class="sw-form" @submit.prevent="createReport">
          <label>报告编号<input v-model="reportForm.reportNo" required></label><label>报告标题<input v-model="reportForm.title" required></label>
          <label>报告类型<select v-model="reportForm.reportType"><option value="inspection">消防检测</option><option value="service">技术服务</option><option value="maintenance">维护保养</option></select></label>
          <label v-if="reportForm.reportType==='inspection'" class="wide">检测资料版本（可选，绑定后不随档案更新）<select v-model="inspectionVersion"><option value="">暂不绑定</option><option v-for="v in inspectionVersions" :key="v.version_no" :value="v.version_no">资料 v{{v.version_no}} · {{v.created_at}}</option></select></label>
          <label class="wide">摘要<textarea v-model="reportForm.summary" required rows="2"></textarea></label>
          <label class="wide">检测范围<textarea v-model="reportForm.scope" :disabled="!editableReport||busy" rows="2"></textarea></label>
          <label class="wide">检测方法<textarea v-model="reportForm.methods" :disabled="!editableReport||busy" rows="2"></textarea></label>
          <label class="wide">检测发现<textarea v-model="reportForm.findings" :disabled="!editableReport||busy" rows="2"></textarea></label>
          <label class="wide">服务结论<textarea v-model="reportForm.conclusion" required rows="2"></textarea></label>
          <button class="primary" :disabled="busy">创建报告草稿</button>
        </form>
        <div class="sw-list">
          <article v-for="r in reports" :key="r.id"><div><b>{{ r.title }}</b><small>{{ r.report_no }} · v{{ r.current_version_no }} · 修订 {{ r.revision_no }}</small></div><div><span class="sw-pill">{{ reportLabel(r.status) }}</span><button @click="openReport(r.id)">打开</button></div></article>
          <div v-if="!reports.length" class="sw-empty small">尚未创建报告</div>
        </div>
      </div>
      <div v-else class="sw-report-view">
        <div class="sw-panel-title"><div><button class="back" @click="report=null;inspectionVersion=''">← 报告列表</button><h3>{{ report.title }}</h3><small>{{ report.report_no }} · 版本 {{ report.current_version_no }} · 修订 {{ report.revision_no }}</small></div><span class="sw-pill">{{ reportLabel(report.status) }}</span></div>
        <div class="sw-form">
          <label>报告标题<input v-model="reportForm.title" :disabled="!editableReport||busy"></label><label>报告编号<input :value="report.report_no" disabled></label>
          <label v-if="report.report_type==='inspection'" class="wide">检测资料版本<select v-model="inspectionVersion" :disabled="!editableReport||busy"><option value="">暂不绑定</option><option v-for="v in inspectionVersions" :key="v.version_no" :value="v.version_no">资料 v{{v.version_no}} · {{v.created_at}}</option></select><small v-if="report.content?.inspectionProfile">已绑定资料 v{{report.content.inspectionProfile.versionNo}}，保存新报告版本才会变更。</small></label>
          <label class="wide">摘要<textarea v-model="reportForm.summary" :disabled="!editableReport||busy" rows="2"></textarea></label><label class="wide">检测范围<textarea v-model="reportForm.scope" rows="2"></textarea></label><label class="wide">检测方法<textarea v-model="reportForm.methods" rows="2"></textarea></label><label class="wide">检测发现<textarea v-model="reportForm.findings" rows="3"></textarea></label><label class="wide">服务结论<textarea v-model="reportForm.conclusion" :disabled="!editableReport||busy" rows="2"></textarea></label>
        </div>
        <details v-if="report.content?.inspectionProfile?.content?.basisReferences?.length"><summary>本报告固定的检测依据</summary><article v-for="s in report.content.inspectionProfile.content.basisReferences" :key="s.id"><b>《{{s.title}}》 {{s.standard_no}} {{s.edition}}</b><p>{{s.scope}} · {{s.reason}}</p><a :href="s.source_url" target="_blank" rel="noopener noreferrer">来源</a> <a v-if="s.access_url" :href="s.access_url" target="_blank" rel="noopener noreferrer">原文入口</a></article></details>
        <div v-if="report.status==='draft'&&can('report:write')" class="sw-form"><label class="wide">本版本送审文件<select v-model="selectedArtifact" :disabled="busy"><option value="">未选择 DOCX</option><option v-for="a in draftArtifacts" :key="a.id" :value="a.id">文件 #{{a.artifact_revision_no}} · {{a.original_name}}</option></select></label><label v-if="can('file:upload')" class="sw-upload">上传人工编辑 DOCX<input type="file" hidden accept=".docx" :disabled="busy" @change="registerEditedDocx"></label><small>送审后固定所选文件，旧版生成文件不会自动成为新版本正式报告。</small></div>
        <ReportReviewEvidence v-if="reviewArtifact" :key="reviewArtifact.id" :artifact="reviewArtifact" :content="report.content" :can-file="can('file:read')" :can-photo="can('report:photo:read')"/>
        <p v-if="report.return_reason">退回意见：{{report.return_reason}}</p>
        <div class="sw-bottom-actions"><button v-if="editableReport" :disabled="busy" class="primary" @click="saveReport">保存新版本</button><button v-if="report.status==='draft'&&can('report:write')"  :disabled="busy||(report.report_type==='inspection'&&!selectedArtifact)" @click="reportAction('submit')">提交审核</button><button v-if="report.status==='submitted'&&can('report:review')&&Number(report.versionCreatedBy)!==props.userId&&Number(report.submittedBy)!==props.userId" :disabled="busy" @click="reportAction('start-review')">开始审核</button><button v-if="report.status==='reviewing'&&can('report:review')&&Number(report.reviewer_id)===props.userId" :disabled="busy" @click="reportAction('approve')">审核通过</button><button v-if="report.status==='reviewing'&&can('report:review')&&Number(report.reviewer_id)===props.userId" :disabled="busy" class="danger" @click="reportAction('return')">退回修改</button><button v-if="['submitted','reviewing','approved'].includes(report.status)&&can('report:write')&&(Number(report.versionCreatedBy)===props.userId||Number(report.submittedBy)===props.userId)" :disabled="busy" @click="reportAction('withdraw')">撤回重新编制</button><button v-if="report.status==='approved'&&can('report:issue')&&Number(report.reviewer_id)!==props.userId&&Number(report.versionCreatedBy)!==props.userId&&Number(report.submittedBy)!==props.userId" :disabled="busy" @click="reportAction('issue')">签发报告</button><button v-if="report.status==='issued'&&can('report:issue')" @click="reportAction('archive')">归档报告</button><button v-if="['issued','archived'].includes(report.status)&&can('report:write')" @click="reportAction('revise')">创建修订版</button></div>
        <section v-if="report.status==='draft'&&can('report:write')&&can('file:upload')" class="sw-docx">
          <h4>报告模板与 DOCX 生成</h4>
          <div v-if="report.report_type==='inspection'" class="sw-form"><label class="wide">模板来源<select v-model="templateMode" @change="switchTemplateMode"><option value="legacy">上传完整 Word 模板</option><option value="library">模板库 · 选择系统章节</option></select></label></div>
          <template v-if="modular">
           <div class="sw-form"><label class="wide">公共基础模板<select v-model="baseTemplateId" @change="showBodyBinding=false"><option value="">选择基础模板</option><option v-for="t in baseOptions" :key="t.id" :value="t.id">{{t.template_name}} · v{{t.revision_no}}</option></select></label>
           <label class="wide">加入系统章节<select v-model="addSectionId"><option value="">请选择</option><option v-for="t in sectionOptions" :key="t.id" :value="t.id">{{t.system_name}} · {{t.template_name}} · v{{t.revision_no}}</option></select></label><button :disabled="!addSectionId" @click="addSection">加入章节</button><button @click="loadLibrary">刷新模板库</button></div>
           <div class="sw-list"><article v-for="(t,i) in selectedSections" :key="t.id"><div><b>{{i+1}}. {{t.system_name}}</b><small>{{t.template_name}} · v{{t.revision_no}}</small></div><div><button :disabled="i===0" @click="moveSection(i,-1)">上移</button><button :disabled="i===selectedSections.length-1" @click="moveSection(i,1)">下移</button><button @click="removeSection(i)">移除</button></div></article></div>
           <small>生成时只包含选中的系统及其实际资料，未选择的系统不会生成章节；每份报告固定所选模板版本。</small><details v-if="recommendedBasis.length"><summary>模板参考规范与本报告检测依据对照</summary><div v-for="s in recommendedBasis" :key="s.id">{{s.standard_no}} {{s.edition}} · {{s.title}} · {{s.chosen?'已引用':s.active?'未引用，请核对适用性':'资料库已停用，请核对版本'}}</div><p>仅作核对提醒，不自动添加或更换报告依据。如需调整，请在检测资料页保存新版本并重新绑定报告。</p></details>
          </template><template v-else><label v-if="can('file:upload')" class="sw-upload">上传现有报告 DOCX 模板<input type="file" hidden accept=".docx" @change="chooseTemplate"></label><small>{{ templateFile?.original_name||'尚未选择模板' }}</small></template>
          <label class="sw-mode">照片质量<select v-model="photoQuality"><option value="print">标准打印 · 300 DPI 上限（推荐）</option><option value="compact">轻量打印 · 240 DPI 上限</option><option value="detail">细节优先 · 360 DPI 上限</option></select></label><small>仅压缩 DOCX 内嵌副本，不修改原图、不放大低分辨率照片；审核可按需打开原图。</small>
          <div class="sw-batch-checks"><label v-for="b in batches.filter(x=>x.status==='reviewed'||((modular||photoMode==='replace')&&x.status==='locked'&&Number(x.report_id)===Number(report.id)))" :key="b.id"><input type="checkbox" :value="b.id" v-model="selectedBatches">{{ b.title }} · {{ b.photo_count }} 张 · {{ batchLabel(b.status) }}</label></div>
          <label v-if="!modular" class="sw-mode"><input type="radio" value="append" v-model="photoMode"> 保留模板现有照片，在对应系统照片表格中追加（默认，非破坏性）</label>
          <label v-if="!modular" class="sw-mode"><input type="radio" value="replace" v-model="photoMode"> 替换模板中所有系统照片，仅保留本次已选批次（请确认模板原照片可替换）</label>
          <p class="sw-disclaimer">照片模式保留正文原样；正文填充仅使用已绑定的检测资料快照及明确绑定字段，不自动推导结论或签章。追加模式不能重复使用已归档照片批次。正文生成可不选照片，或与已审核照片共同生成。</p>
          <div class="sw-bottom-actions"><button v-if="!modular" class="primary" :disabled="busy||!templateFile||!selectedBatches.length" @click="generateDocx">仅插入照片并生成 DOCX</button><button v-if="report.report_type==='inspection'" :disabled="busy||!templateReady||!report.content?.inspectionProfile" @click="showBodyBinding=!showBodyBinding">{{showBodyBinding?'收起正文工作台':'打开正文填充工作台'}}</button></div>
          <InspectionDocxWorkbench v-if="showBodyBinding&&templateReady&&report.content?.inspectionProfile" :key="report.id+'-'+report.current_version_no+'-'+effectiveTemplateId+'-'+sectionIds.join(',')" :report-id="Number(report.id)" :version-no="Number(report.current_version_no)" :template-id="effectiveTemplateId" :base-template-id="modular?Number(baseTemplateId):null" :section-template-ids="modular?sectionIds:[]" :batch-ids="selectedBatches" :photo-mode="modular?'replace':photoMode" :photo-quality="photoQuality" :can-download="can('file:read')" @generated="refreshGenerated"/>
        </section>
        <section v-if="can('delivery:read')" class="sw-docx">
          <h4>报告邮寄 / 交付登记</h4>
          <p v-if="!issuedVersions.length" class="sw-disclaimer">暂无明确签发文件的版本，历史交付仍可查阅。</p>
          <div v-if="can('delivery:write')&&issuedVersions.length" class="sw-form"><label class="wide">签发版本<select v-model="deliveryVersion"><option v-for="v in issuedVersions" :key="v.version_no" :value="v.version_no">v{{v.version_no}} · {{v.title_snapshot||report.report_no}} · 文件 #{{v.submitted_artifact_id}}</option></select></label>
            <label>交付方式<select v-model="deliveryForm.deliveryMethod"><option value="courier">快递</option><option value="hand_delivery">现场交付</option><option value="electronic">电子交付</option></select></label><label>收件人<input v-model="deliveryForm.recipientName" required></label><label>收件单位<input v-model="deliveryForm.recipientOrganization"></label><label>联系电话<input v-model="deliveryForm.recipientPhone"></label><label class="wide">收件地址<input v-model="deliveryForm.recipientAddress" required></label><label>快递公司<input v-model="deliveryForm.courierName"></label><label>快递单号<input v-model="deliveryForm.trackingNo"></label><label class="wide">备注<textarea v-model="deliveryForm.notes" rows="2"></textarea></label>
          </div><button v-if="can('delivery:write')&&issuedVersions.length" class="primary" :disabled="busy||!deliveryVersion" @click="createDelivery">登记交付</button>
          <div class="sw-list"><article v-for="d in deliveries" :key="d.id"><div><b>{{ d.recipient_organization||d.recipient_name }}</b><small>{{ d.courier_name||d.delivery_method }} · {{ d.tracking_no||'无单号' }}</small><small>{{ d.recipient_address }}</small><small>报告 v{{d.version_no}} · {{d.artifact_id?'签发文件 #'+d.artifact_id:'历史文件未固定'}}</small><button v-if="can('file:read')&&d.file_id" @click="downloadFile(d.file_id,d.original_name)">下载交付原件</button><small v-for="ev in d.events||[]" :key="ev.id">{{ev.from_status||'创建'}} → {{ev.to_status}} · {{ev.actor_name}} · {{ev.created_at}}</small></div><div><span class="sw-pill">{{ ({prepared:'待寄出',dispatched:'已寄出',delivered:'已签收',returned:'退回',cancelled:'已取消'})[d.status] }}</span><button v-if="can('delivery:write')&&['prepared','returned'].includes(d.status)" @click="updateDelivery(d,'dispatched')">登记寄出</button><button v-if="can('delivery:write')&&d.status==='dispatched'" @click="updateDelivery(d,'delivered')">登记签收</button><button v-if="can('delivery:write')&&d.status==='dispatched'" @click="updateDelivery(d,'returned')">登记退回</button><button v-if="can('delivery:write')&&['prepared','returned'].includes(d.status)" @click="updateDelivery(d,'cancelled')">取消交付</button></div></article></div>
        </section>
        <section class="sw-history"><h4>版本历史</h4><div v-for="v in report.versions||[]" :key="v.version_no">v{{ v.version_no }} · {{v.title_snapshot||'历史标题未记录'}} · {{ v.created_at }} <button @click="viewVersion(v.version_no)">查看快照</button></div><div v-if="historyVersion" class="sw-docx"><h4>历史版本 v{{historyVersion.version_no}}</h4><p>{{historyVersion.title_snapshot||'历史标题未记录'}}</p><pre class="artifact-manifest">{{JSON.stringify(historyVersion.content,null,2)}}</pre><div v-for="f in historyVersion.attachments||[]" :key="f.file_id">{{f.original_name}} <button v-if="can('file:read')" @click="downloadFile(f.file_id,f.original_name)">下载原件</button></div><button @click="historyVersion=null">关闭快照</button></div><h4>生成文件历史</h4><div v-for="a in report.artifacts||[]" :key="a.id">v{{ a.version_no }} / DOCX #{{ a.artifact_revision_no }} · {{ a.original_name }} · {{ a.sha256?.slice(0,16) }} <button v-if="can('file:read')" @click="downloadFile(a.file_id,a.original_name)">下载</button><details v-if="a.generation_manifest"><summary>生成来源记录</summary><pre class="artifact-manifest">{{typeof a.generation_manifest==='string'?JSON.stringify(JSON.parse(a.generation_manifest),null,2):JSON.stringify(a.generation_manifest,null,2)}}</pre></details></div><h4>操作记录</h4><div v-for="e in report.events||[]" :key="e.id">{{ e.from_status||'新建' }} → {{ e.to_status }} · {{ e.actor_name }} · {{ e.created_at }} · {{ e.reason }}</div></section>
      </div>
    </div>
  </template>
</section>
</template>

<style scoped>
.service-workflow{display:grid;gap:16px;color:#24344b}.sw-head{display:flex;justify-content:space-between;align-items:flex-start}.sw-head h2{margin:4px 0;font-size:23px}.sw-head p{margin:0;color:#7b899b;font-size:12px}.sw-kicker{font-size:10px;letter-spacing:.14em;color:#5579bb;font-weight:700}.sw-head button,.sw-tabs button,.sw-list button,.sw-bottom-actions button,.sw-row-actions button,.sw-issue-actions button,.back{border:1px solid #dfe6ef;background:#fff;color:#52647b;border-radius:8px;padding:8px 12px;font-size:12px;cursor:pointer}.sw-selects{display:grid;grid-template-columns:1fr 1fr;gap:12px;padding:15px;background:#fff;border:1px solid #e6ebf2;border-radius:12px}.sw-selects label,.sw-form label{display:grid;gap:6px;color:#64748b;font-size:11px}.sw-selects select,.sw-form input,.sw-form select,.sw-form textarea,.sw-photo-meta select,.sw-photo-meta input{width:100%;min-width:0;border:1px solid #dfe6ef;border-radius:8px;padding:9px;background:#fff;color:#34465e;font:inherit;font-size:12px}.sw-form select[multiple]{min-height:76px}.sw-tabs{display:flex;gap:8px;border-bottom:1px solid #e6ebf2;padding-bottom:10px}.sw-tabs button.active{background:#edf3ff;border-color:#c8d7fa;color:#315bb3}.sw-panel{background:#fff;border:1px solid #e6ebf2;border-radius:13px;padding:18px;display:grid;gap:16px}.sw-panel-title{display:flex;justify-content:space-between;align-items:center;gap:12px}.sw-panel-title h3{font-size:16px;margin:4px 0;color:#293d59}.sw-panel-title span,.sw-panel-title small{font-size:11px;color:#8290a3}.sw-form{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.sw-form .wide{grid-column:1/-1}.sw-form .primary,.primary{background:#315fca;color:#fff;border:1px solid #315fca;border-radius:8px;padding:10px 14px;font-size:12px;cursor:pointer}.sw-list{display:grid;gap:8px}.sw-list article{display:flex;justify-content:space-between;align-items:center;gap:15px;padding:12px;border:1px solid #edf0f5;border-radius:10px}.sw-list article>div:first-child{display:grid;gap:4px;min-width:0}.sw-list b{font-size:12px}.sw-list small,.sw-list p{font-size:11px;color:#8290a3;margin:0;overflow-wrap:anywhere}.sw-list article>div:last-child{display:flex;align-items:center;gap:8px;flex-wrap:wrap}.sw-pill{white-space:nowrap;background:#f0f4fa;color:#5e6f86;border-radius:20px;padding:5px 9px;font-size:10px}.sw-issue-actions{display:grid;justify-items:end;gap:6px}.sw-resolution{color:#367c58!important}.sw-two{display:grid;grid-template-columns:minmax(260px,.9fr) minmax(0,1.1fr);gap:18px}.sw-empty{padding:35px 15px;text-align:center;border:1px dashed #d6deea;border-radius:10px;color:#8290a3;font-size:12px}.sw-empty.small{padding:18px}.sw-msg{padding:10px 13px;border-radius:8px;font-size:12px}.sw-msg.error{background:#fff0ed;color:#a33}.sw-msg.success{background:#eaf7ef;color:#287449}.sw-upload-row{display:flex;align-items:center;gap:12px;flex-wrap:wrap}.sw-upload{display:inline-flex;align-items:center;background:#315fca;color:#fff;border-radius:8px;padding:10px 14px;font-size:12px;cursor:pointer}.sw-upload-row small,.sw-bottom-actions small{font-size:11px;color:#8290a3}.sw-photo-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(205px,1fr));gap:12px}.sw-photo-card{border:1px solid #e4eaf2;border-radius:10px;overflow:hidden;background:#fff}.sw-photo-table-wrap{overflow:auto;border:1px solid #e6ebf2;border-radius:10px}.sw-photo-table{width:100%;border-collapse:collapse;background:#fff}.sw-photo-table th,.sw-photo-table td{padding:8px;border-bottom:1px solid #edf0f5;text-align:left;font-size:11px;white-space:nowrap}.sw-photo-table img{width:88px;height:62px;object-fit:contain;background:#f1f4f8}.sw-photo-table select,.sw-photo-table input:not([type=checkbox]){border:1px solid #dfe6ef;border-radius:6px;padding:6px;font:inherit;font-size:11px;max-width:210px}.sw-photo-table button{border:1px solid #dfe6ef;background:#fff;border-radius:6px;padding:6px 8px;font-size:10px;color:#56677d}.sw-photo-card>img,.sw-photo-placeholder{width:100%;height:150px;object-fit:contain;background:#f1f4f8}.sw-photo-placeholder{display:grid;place-items:center;color:#8794a6;font-size:12px}.sw-photo-meta{display:grid;gap:8px;padding:10px}.sw-photo-meta b{font-size:11px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.sw-photo-meta label{display:grid;gap:4px;color:#7c899a;font-size:10px}.sw-photo-meta .confirm{display:flex;align-items:center;gap:6px}.sw-photo-meta .confirm input{width:auto}.sw-row-actions{display:flex;justify-content:space-between}.sw-row-actions .danger,.sw-bottom-actions .danger{color:#b54e4e}.sw-bottom-actions{display:flex;align-items:center;gap:10px;flex-wrap:wrap}.sw-docx{display:grid;gap:12px;padding:16px;border:1px solid #e3eaf4;border-radius:10px;background:#f9fbfe}.sw-docx h4,.sw-history h4{font-size:13px;margin:0;color:#405675}.sw-batch-checks{display:flex;gap:12px;flex-wrap:wrap}.sw-batch-checks label{font-size:11px;color:#607189}.sw-mode{display:flex;gap:8px;align-items:flex-start;font-size:11px;color:#607189}.sw-mode input{margin-top:2px}.sw-disclaimer{font-size:11px;line-height:1.7;color:#987044;background:#fff8ed;border:1px solid #f1e1c8;padding:9px 11px;border-radius:8px}.sw-visit-links{display:flex;align-items:center;gap:7px;flex-wrap:wrap}.sw-visit-links select{max-width:210px;border:1px solid #dfe6ef;border-radius:7px;padding:6px;font-size:11px;color:#56677d}.sw-visit-links button:disabled{opacity:.45;cursor:not-allowed}.sw-history{display:grid;gap:8px;padding:14px;border-top:1px solid #edf0f5;font-size:11px;color:#7b899a}.sw-history h4{margin-top:8px}.back{padding:4px 8px;margin-bottom:6px}@media(max-width:800px){.sw-two{grid-template-columns:1fr}.sw-selects{grid-template-columns:1fr}}@media(max-width:560px){.sw-form{grid-template-columns:1fr}.sw-form .wide{grid-column:auto}.sw-list article{align-items:flex-start;flex-direction:column}.sw-issue-actions{display:flex;justify-items:start}}
.photo-filters{display:flex;flex-wrap:wrap;gap:6px}.photo-filters button{background:#fff;border:1px solid #dfe6ef;border-radius:7px;padding:7px 10px;font-size:12px}.photo-filters button.active{background:#ecf2ff;border-color:#86a3e8;color:#315fca}.sw-photo-card.picked{outline:2px solid #5680cf}.photo-select{display:flex;align-items:center;gap:5px;padding:7px;font-size:12px}.photo-zoom{cursor:zoom-in}.photo-lightbox{position:fixed;inset:0;z-index:100;background:#152033d9;display:grid;place-items:center;padding:20px}.photo-lightbox>div{background:white;padding:14px;border-radius:10px;max-width:95vw;max-height:95vh}.photo-lightbox img{display:block;max-width:90vw;max-height:75vh;object-fit:contain}
.artifact-manifest{white-space:pre-wrap;overflow-wrap:anywhere;max-height:260px;overflow:auto;font-size:11px}
</style>
