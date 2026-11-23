<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { api, apiBlob, uploadFile } from './api'

const props = defineProps({
  projectId: { type: [Number, String], default: null },
  reportId: { type: [Number, String], default: null },
  permissions: { type: Array, default: () => [] }
})
const projects = ref([])
const reports = ref([])
const batches = ref([])
const systems = ref([])
const projectIdLocal = ref(props.projectId ? String(props.projectId) : '')
const reportIdLocal = ref(props.reportId ? String(props.reportId) : '')
const batchId = ref('')
const batch = ref(null)
const report = ref(null)
const photos = ref([])
const serviceVisits = ref([])
const activeFilter = ref('全部')
const viewMode = ref('cards')
const selectedIds = ref([])
const templateFileId = ref('')
const newBatchTitle = ref('')
const serviceVisitId = ref('')
const focused = ref(null)
const busy = ref(false)
const loading = ref(false)
const progress = ref('')
const error = ref('')
const notice = ref('')
const objectUrls = new Set()
const can = p => props.permissions.includes(p)
const currentProjectId = computed(() => props.projectId ? String(props.projectId) : projectIdLocal.value)
const docxAttachments = computed(() => (report.value?.attachments || []).filter(a =>
  a.media_type === 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' ||
  (a.original_name || '').toLowerCase().endsWith('.docx')
))
const filtered = computed(() => photos.value.filter(p =>
  activeFilter.value === '全部' ||
  (activeFilter.value === '未分类' ? !p.system_code : p.system_code === activeFilter.value)
))
const classifiedCount = computed(() => photos.value.filter(p => !!p.system_code).length)
const selectedCount = computed(() => selectedIds.value.length)
const ready = computed(() => photos.value.length > 0 && classifiedCount.value === photos.value.length)
const editable = computed(() => batch.value && !['inserted','archived'].includes(batch.value.status))

function clearUrls() {
  objectUrls.forEach(url => URL.revokeObjectURL(url))
  objectUrls.clear()
  photos.value.forEach(p => { p.previewUrl = '' })
}
async function loadProjects() {
  projects.value = await api('/business/projects')
  if (!currentProjectId.value && projects.value.length) projectIdLocal.value = String(projects.value[0].id)
}
async function loadProjectData() {
  if (!currentProjectId.value) return
  const id = currentProjectId.value
  const [projectReports, photoBatches, visits] = await Promise.all([
    api('/business/projects/' + id + '/reports'),
    api('/report-photos/batches?projectId=' + id),
    api('/business/projects/' + id + '/service-visits').catch(() => [])
  ])
  reports.value = projectReports
  batches.value = photoBatches
  serviceVisits.value = visits
  if (props.reportId) reportIdLocal.value = String(props.reportId)
  else if (!projectReports.some(r => String(r.id) === String(reportIdLocal.value))) reportIdLocal.value = projectReports[0] ? String(projectReports[0].id) : ''
  if (!photoBatches.some(b => String(b.id) === String(batchId.value))) batchId.value = photoBatches[0] ? String(photoBatches[0].id) : ''
  await loadReport()
  await loadBatch()
}
async function loadReport() {
  report.value = null; templateFileId.value = ''
  if (!reportIdLocal.value) return
  report.value = await api('/business/reports/' + reportIdLocal.value)
  const firstDocx = docxAttachments.value[0]
  if (firstDocx) templateFileId.value = String(firstDocx.file_id)
}
async function loadBatch() {
  clearUrls()
  batch.value = null; photos.value = []; selectedIds.value = []
  if (!batchId.value) return
  batch.value = await api('/report-photos/batches/' + batchId.value)
  photos.value = (batch.value.photos || []).map(p => ({ ...p, previewUrl:'', selected:false }))
  await loadPreviews()
  if (batch.value.status === 'inserted') notice.value = '该批次已插入报告并生成新版本，历史记录已保留。'
}
async function loadPreviews() {
  const items = photos.value
  for (let i=0; i<items.length; i+=8) {
    const group = items.slice(i,i+8)
    await Promise.all(group.map(async p => {
      try {
        const blob = await apiBlob('/report-photos/batches/' + batchId.value + '/photos/' + p.photo_id + '/preview')
        const url = URL.createObjectURL(blob); objectUrls.add(url); p.previewUrl = url
      } catch { p.previewUrl = '' }
    }))
  }
}
async function refresh() {
  loading.value = true; error.value = ''
  try {
    systems.value = await api('/report-photos/systems')
    if (!props.projectId) await loadProjects()
    await loadProjectData()
  } catch(e) { error.value = e.message }
  finally { loading.value = false }
}
async function selectProject() {
  batchId.value = ''; reportIdLocal.value = ''; batch.value = null; photos.value = []
  await loadProjectData()
}
async function selectBatch() {
  error.value = ''; notice.value = ''
  await loadBatch()
}
async function selectReport() {
  error.value = ''
  await loadReport()
}
async function createBatch() {
  if (!currentProjectId.value) return
  busy.value = true; error.value = ''
  try {
    const result = await api('/report-photos/batches', {
      method:'POST',
      body:JSON.stringify({ projectId:Number(currentProjectId.value), serviceVisitId:serviceVisitId.value || null, title:newBatchTitle.value || '现场照片批次' })
    })
    newBatchTitle.value=''; serviceVisitId.value=''
    await loadProjectData()
    batchId.value = String(result.id)
    await loadBatch()
    notice.value = '照片批次已创建，可以导入照片。'
  } catch(e) { error.value = e.message }
  finally { busy.value = false }
}
async function compressPhoto(file) {
  const bitmap = await createImageBitmap(file)
  try {
    const maxSide = 1800
    const scale = Math.min(1, maxSide / Math.max(bitmap.width, bitmap.height))
    const canvas = document.createElement('canvas')
    canvas.width = Math.max(1, Math.round(bitmap.width * scale))
    canvas.height = Math.max(1, Math.round(bitmap.height * scale))
    canvas.getContext('2d').drawImage(bitmap,0,0,canvas.width,canvas.height)
    const blob = await new Promise((resolve,reject) => canvas.toBlob(v => v ? resolve(v) : reject(new Error('图片压缩失败')),'image/jpeg',.9))
    const name = file.name.replace(/\.(png|jpe?g|webp|heic)$/i,'') + '.jpg'
    return new File([blob],name,{type:'image/jpeg'})
  } finally { bitmap.close() }
}
async function uploadPhotos(event) {
  const files = Array.from(event.target.files || [])
  event.target.value = ''
  if (!files.length || !batchId.value) return
  if (!editable.value) { error.value='该批次已插入报告，不能再修改'; return }
  if (photos.value.length + files.length > 300) { error.value='一个批次最多 300 张照片'; return }
  busy.value = true; error.value=''; notice.value=''
  try {
    const form = new FormData()
    for (let i=0;i<files.length;i++) {
      progress.value = '正在处理照片 ' + (i+1) + '/' + files.length
      form.append('photos',await compressPhoto(files[i]))
    }
    progress.value = '正在上传照片…'
    batch.value = await api('/report-photos/batches/' + batchId.value + '/photos',{method:'POST',body:form})
    photos.value = (batch.value.photos || []).map(p=>({...p,previewUrl:'',selected:false}))
    await loadPreviews()
    notice.value = '已导入 ' + files.length + ' 张照片。请逐张确认系统分类。'
  } catch(e) { error.value = e.message }
  finally { busy.value=false; progress.value='' }
}
function selectVisible(value) {
  const ids = new Set(selectedIds.value)
  filtered.value.forEach(p => value ? ids.add(p.photo_id) : ids.delete(p.photo_id))
  selectedIds.value = [...ids]
  photos.value.forEach(p => { p.selected = ids.has(p.photo_id) })
}
function toggleSelected(photo) {
  const ids = new Set(selectedIds.value)
  photo.selected ? ids.add(photo.photo_id) : ids.delete(photo.photo_id)
  selectedIds.value = [...ids]
}
function assignSelected(system) {
  if (!system || !selectedIds.value.length) return
  photos.value.forEach(p => {
    if (selectedIds.value.includes(p.photo_id)) p.system_code = system
  })
  selectedIds.value = []; photos.value.forEach(p => p.selected=false)
}
function movePhoto(photo,direction) {
  const group = photos.value.filter(p => p.system_code === photo.system_code)
  const index = group.findIndex(p => p.photo_id === photo.photo_id)
  const target = index + direction
  if (target < 0 || target >= group.length) return
  const a=group[index], b=group[target]
  const temp=a.sort_order; a.sort_order=b.sort_order; b.sort_order=temp
  photos.value.sort((x,y) => {
    const ax=systems.value.indexOf(x.system_code), ay=systems.value.indexOf(y.system_code)
    return ax-ay || x.sort_order-y.sort_order || x.photo_id-y.photo_id
  })
}
async function saveClassification() {
  if (!batchId.value || !editable.value) return
  busy.value=true; error.value=''; notice.value=''
  try {
    batch.value = await api('/report-photos/batches/' + batchId.value + '/photos',{
      method:'PUT',
      body:JSON.stringify({photos:photos.value.map((p,index)=>({
        photoId:p.photo_id, systemCode:p.system_code || null, sortOrder:Number(p.sort_order ?? index)
      }))})
    })
    photos.value=(batch.value.photos || []).map(p=>({...p,previewUrl:photos.value.find(x=>x.photo_id===p.photo_id)?.previewUrl || '',selected:false}))
    selectedIds.value=[]
    notice.value = ready.value ? '分类和排序已保存，可以预览 DOCX。' : '分类已保存，还有照片尚未分类。'
  } catch(e) { error.value=e.message }
  finally { busy.value=false }
}
async function deletePhoto(photo) {
  if (!confirm('确定移除这张照片吗？')) return
  busy.value=true; error.value=''
  try {
    if(photo.previewUrl){URL.revokeObjectURL(photo.previewUrl);objectUrls.delete(photo.previewUrl)}
    batch.value=await api('/report-photos/batches/'+batchId.value+'/photos/'+photo.photo_id,{method:'DELETE'})
    photos.value=(batch.value.photos || []).map(p=>({...p,previewUrl:'',selected:false}))
    await loadPreviews()
  } catch(e) { error.value=e.message }
  finally { busy.value=false }
}
async function uploadTemplate(event) {
  const file=event.target.files?.[0]; event.target.value=''
  if(!file) return
  if(!file.name.toLowerCase().endsWith('.docx')) { error.value='模板必须是 DOCX 文件'; return }
  busy.value=true; error.value=''
  try {
    const saved=await uploadFile(file)
    await api('/business/reports/'+reportIdLocal.value+'/attachments',{method:'POST',body:JSON.stringify({fileId:saved.id})})
    await loadReport()
    templateFileId.value=String(saved.id)
    notice.value='DOCX 模板已上传并关联到当前报告版本。'
  } catch(e) { error.value=e.message }
  finally { busy.value=false }
}
function insertionBody() {
  if(!report.value || !templateFileId.value) throw new Error('请选择报告和 DOCX 模板')
  if(!ready.value) throw new Error('请先完成全部照片分类并保存')
  return { reportId:Number(reportIdLocal.value), templateFileId:Number(templateFileId.value), expectedVersionNo:Number(report.value.current_version_no) }
}
function saveBlob(blob,filename) {
  const url=URL.createObjectURL(blob); const a=document.createElement('a')
  a.href=url; a.download=filename; a.click(); setTimeout(()=>URL.revokeObjectURL(url),1500)
}
async function previewDocx() {
  busy.value=true; error.value=''; notice.value=''
  try {
    await saveClassification()
    const blob=await apiBlob('/report-photos/batches/'+batchId.value+'/preview',{method:'POST',body:JSON.stringify(insertionBody())})
    saveBlob(blob,(report.value.report_no || 'report')+'-照片预览.docx')
    notice.value='预览文档已生成。请用 Word 检查每个系统的照片位置、分页和版式。'
  } catch(e) { error.value=e.message }
  finally { busy.value=false }
}
async function insertDocx() {
  if(!confirm('确认将照片插入报告并生成一个新的不可变报告版本？原始 DOCX 和原始照片不会被覆盖。')) return
  busy.value=true; error.value=''; notice.value=''
  try {
    await saveClassification()
    const result=await api('/report-photos/batches/'+batchId.value+'/insert',{method:'POST',body:JSON.stringify(insertionBody())})
    await loadProjectData()
    batchId.value=String(result.batchId)
    await loadBatch()
    await loadReport()
    notice.value='已插入报告，生成版本 v'+result.versionNo+'。请在报告管理中继续审核流转。'
  } catch(e) { error.value=e.message }
  finally { busy.value=false }
}
function formatSize(size) { return size < 1024*1024 ? Math.round(size/1024)+' KB' : (size/1024/1024).toFixed(1)+' MB' }
watch(() => props.projectId, async () => { projectIdLocal.value=props.projectId?String(props.projectId):''; await refresh() })
watch(() => props.reportId, async () => { if(props.reportId){reportIdLocal.value=String(props.reportId);await loadReport()} })
onMounted(refresh)
onBeforeUnmount(clearUrls)
</script>

<template>
  <section class="photo-workbench">
    <header class="photo-head"><div><span class="eyebrow">REPORT / PHOTO WORKBENCH</span><h2>现场照片整理</h2><p>批量导入 → 人工分类 → 分组排序 → DOCX 预览 → 插入报告。</p></div><span class="pw-limit">JPG / PNG · 最多 300 张 / 批次</span></header>
    <div class="pw-stepper"><span class="active">01 导入照片</span><i>→</i><span :class="{active:ready}">02 分类确认</span><i>→</i><span :class="{active:ready && !!templateFileId}">03 预览报告</span><i>→</i><span :class="{active:batch?.status==='inserted'}">04 生成版本</span></div>
    <div class="pw-context">
      <label v-if="!props.projectId">项目<select v-model="projectIdLocal" @change="selectProject"><option value="">选择项目</option><option v-for="p in projects" :key="p.id" :value="String(p.id)">{{ p.project_no }} · {{ p.name }}</option></select></label>
      <label>目标报告<select v-model="reportIdLocal" @change="selectReport" :disabled="!currentProjectId"><option value="">选择项目报告</option><option v-for="r in reports" :key="r.id" :value="String(r.id)">{{ r.report_no }} · {{ r.title }}（{{ r.status }}）</option></select></label>
      <label>照片批次<select v-model="batchId" @change="selectBatch"><option value="">选择照片批次</option><option v-for="b in batches" :key="b.id" :value="String(b.id)">{{ b.batch_no }} · {{ b.title }}（{{ b.photo_count }} 张）</option></select></label>
      <button class="pw-primary" type="button" :disabled="busy || !currentProjectId || !can('photo:write')" @click="createBatch">＋ 新建批次</button>
    </div>
    <p v-if="error" class="pw-message error" role="alert">{{ error }}</p><p v-if="notice" class="pw-message success" role="status">{{ notice }}</p><p v-if="progress" class="pw-progress">{{ progress }}</p>
    <div v-if="loading" class="pw-empty">正在加载照片工作台…</div>
    <template v-else-if="batch">
      <div class="pw-batch-head"><div><b>{{ batch.title }}</b><small>{{ batch.batch_no }} · {{ batch.created_at }}</small></div><span class="pw-batch-status" :class="batch.status">{{ ({draft:'待分类',ready:'已分类待插入',inserted:'已插入报告',archived:'已归档'})[batch.status] }}</span></div>
      <div class="pw-toolbar">
        <label v-if="editable" class="pw-upload">＋ 批量导入<input hidden type="file" multiple accept="image/jpeg,image/png" @change="uploadPhotos"></label>
        <button type="button" class="pw-secondary" :disabled="busy || !photos.length" @click="selectVisible(true)">全选当前</button>
        <button type="button" class="pw-secondary" :disabled="busy || !selectedCount" @click="selectVisible(false)">取消选择</button>
        <label v-if="selectedCount && editable" class="pw-bulk">批量分类<select @change="assignSelected($event.target.value);$event.target.value=''"><option value="">选择消防系统…</option><option v-for="s in systems" :key="s" :value="s">{{ s }}</option></select></label>
        <button type="button" class="pw-secondary" @click="viewMode=viewMode==='cards'?'table':'cards'">{{ viewMode==='cards'?'表格视图':'卡片视图' }}</button>
        <button type="button" class="pw-primary" :disabled="busy || !editable || !photos.length" @click="saveClassification">保存分类与排序</button>
      </div>
      <div class="pw-stats"><span>照片 {{ photos.length }} 张</span><span>已分类 {{ classifiedCount }} 张</span><span>待分类 {{ photos.length-classifiedCount }} 张</span><span>已选择 {{ selectedCount }} 张</span></div>
      <nav class="pw-filters"><button v-for="f in ['全部','未分类',...systems.filter(s=>photos.some(p=>p.system_code===s))]" :key="f" :class="{active:activeFilter===f}" @click="activeFilter=f">{{ f }}</button></nav>
      <div v-if="!photos.length" class="pw-empty"><span>▧</span><b>当前批次还没有照片</b><p>可以一次选择多张现场照片导入。</p></div>
      <div v-else-if="viewMode==='cards'" class="pw-grid">
        <article v-for="photo in filtered" :key="photo.photo_id" class="pw-card" :class="{selected:photo.selected}">
          <label class="pw-check"><input type="checkbox" v-model="photo.selected" @change="toggleSelected(photo)" :disabled="!editable"></label>
          <button class="pw-image" type="button" @click="focused=photo"><img v-if="photo.previewUrl" :src="photo.previewUrl" :alt="photo.original_name" loading="lazy"><span v-else>图片加载中</span></button>
          <div class="pw-card-body"><b :title="photo.original_name">{{ photo.original_name }}</b><small>{{ formatSize(photo.file_size) }}</small>
            <select v-model="photo.system_code" :disabled="!editable"><option value="">未分类</option><option v-for="s in systems" :key="s" :value="s">{{ s }}</option></select>
            <div class="pw-card-actions"><button type="button" :disabled="!editable" @click="movePhoto(photo,-1)">↑ 上移</button><button type="button" :disabled="!editable" @click="movePhoto(photo,1)">↓ 下移</button><button type="button" :disabled="!editable" class="danger" @click="deletePhoto(photo)">移除</button></div>
          </div>
        </article>
      </div>
      <div v-else class="pw-table-wrap"><table><thead><tr><th>选择</th><th>预览</th><th>文件名</th><th>大小</th><th>所属消防系统</th><th>排序</th><th></th></tr></thead><tbody>
        <tr v-for="photo in filtered" :key="photo.photo_id"><td><input type="checkbox" v-model="photo.selected" @change="toggleSelected(photo)" :disabled="!editable"></td><td><img v-if="photo.previewUrl" :src="photo.previewUrl" @click="focused=photo"></td><td>{{ photo.original_name }}</td><td>{{ formatSize(photo.file_size) }}</td><td><select v-model="photo.system_code" :disabled="!editable"><option value="">未分类</option><option v-for="s in systems" :key="s" :value="s">{{ s }}</option></select></td><td><button @click="movePhoto(photo,-1)">↑</button><button @click="movePhoto(photo,1)">↓</button></td><td><button class="danger" @click="deletePhoto(photo)">移除</button></td></tr>
      </tbody></table></div>
      <section class="pw-insert-panel">
        <div><h3>插入报告</h3><p>系统会在对应消防系统章节内寻找最后一个「现场检测照片」位置，并在其后插入新的 2×2 照片组。原有检测记录和原照片不会被删除。</p></div>
        <div class="pw-template-row"><label>目标报告附件中的 DOCX 模板<select v-model="templateFileId" :disabled="!report"><option value="">选择 DOCX 模板</option><option v-for="f in docxAttachments" :key="f.file_id" :value="String(f.file_id)">{{ f.original_name }} · v{{ f.version_no }}</option></select></label><label class="pw-upload secondary">上传新的 DOCX<input type="file" hidden accept=".docx" @change="uploadTemplate"></label></div>
        <p v-if="report" class="pw-report-meta">{{ report.report_no }} · 当前版本 v{{ report.current_version_no }} · {{ report.title }}</p>
        <div class="pw-insert-actions"><button type="button" class="pw-secondary" :disabled="busy || !ready || !templateFileId || !report || !editable" @click="previewDocx">生成 DOCX 预览</button><button type="button" class="pw-primary" :disabled="busy || !ready || !templateFileId || !report || !editable || !can('report:write')" @click="insertDocx">确认插入并生成报告新版本</button></div>
        <small>预览文件需要用 Word 检查分页、图片完整性和版式。确认后才会创建新的报告版本；原始 DOCX 与原始照片始终保留。</small>
      </section>
    </template>
    <div v-else class="pw-empty"><span>▧</span><b>选择项目并创建照片批次</b><p>每个批次独立保存照片、一级系统分类和排序。</p></div>
    <div v-if="focused" class="pw-lightbox" role="dialog" aria-modal="true" @click.self="focused=null"><div class="pw-lightbox-card"><header><b>{{ focused.original_name }}</b><button type="button" @click="focused=null">✕</button></header><img :src="focused.previewUrl" :alt="focused.original_name"><label>所属消防系统<select v-model="focused.system_code" :disabled="!editable"><option value="">未分类</option><option v-for="s in systems" :key="s" :value="s">{{ s }}</option></select></label></div></div>
  </section>
</template>

<style scoped>
.photo-workbench{display:grid;gap:15px}.photo-head{display:flex;justify-content:space-between;align-items:flex-start;gap:15px}.photo-head h2{margin:4px 0;font-size:22px;color:#24364f}.photo-head p{margin:0;color:#8996a7;font-size:11px}.pw-limit{font-size:10px;color:#6f83a0;background:#eef3fa;padding:8px 11px;border-radius:8px;white-space:nowrap}.pw-stepper{display:flex;align-items:center;gap:9px;flex-wrap:wrap;padding:11px 14px;background:#f5f7fa;border-radius:10px;font-size:10px;color:#9aa5b3}.pw-stepper .active{color:#315fca;font-weight:700}.pw-context{display:grid;grid-template-columns:1fr 1.4fr 1.4fr auto;gap:10px;align-items:end;background:#fff;border:1px solid #e6ecf3;padding:13px;border-radius:11px}.pw-context label,.pw-template-row label{display:grid;gap:6px;color:#65758b;font-size:10px}.pw-context select,.pw-template-row select{width:100%;min-width:0;border:1px solid #dfe6ef;border-radius:8px;padding:9px;background:white;color:#40536c;font-size:11px}.pw-primary,.pw-secondary,.pw-upload{border:1px solid #dce4ef;border-radius:8px;padding:9px 12px;font-size:10px;cursor:pointer;background:#fff;color:#58708f;white-space:nowrap}.pw-primary{background:#315fca;border-color:#315fca;color:#fff;font-weight:700}.pw-primary:disabled,.pw-secondary:disabled{opacity:.5;cursor:not-allowed}.pw-upload{display:inline-flex;align-items:center;justify-content:center;background:#315fca;color:white;border-color:#315fca}.pw-upload.secondary{background:#fff;color:#58708f;border-color:#dce4ef}.pw-message{padding:10px 12px;border-radius:8px;font-size:11px}.pw-message.error{background:#fff0ed;color:#a43e36}.pw-message.success{background:#e8f7ee;color:#2e754e}.pw-progress{color:#5275b7;font-size:11px}.pw-batch-head{display:flex;justify-content:space-between;align-items:center;padding:12px 14px;border:1px solid #e5ebf2;border-radius:10px;background:white}.pw-batch-head div{display:grid;gap:4px}.pw-batch-head b{font-size:12px;color:#3c506d}.pw-batch-head small{font-size:10px;color:#98a2b1}.pw-batch-status{font-size:10px;padding:6px 9px;border-radius:20px;background:#fff6df;color:#92722d}.pw-batch-status.ready{background:#e9f2ff;color:#3e6caa}.pw-batch-status.inserted{background:#e5f5ed;color:#28764d}.pw-toolbar{display:flex;gap:8px;align-items:center;flex-wrap:wrap}.pw-bulk{display:flex;align-items:center;gap:7px;font-size:10px;color:#65758b}.pw-bulk select{border:1px solid #dfe6ef;border-radius:7px;padding:8px;background:#fff;font-size:10px}.pw-stats{display:flex;gap:8px;flex-wrap:wrap}.pw-stats span{font-size:10px;color:#5c6f89;padding:8px 11px;background:#f3f6fa;border-radius:8px}.pw-filters{display:flex;gap:6px;overflow:auto;padding-bottom:3px}.pw-filters button{border:1px solid #e3e9f0;background:#fff;border-radius:7px;padding:7px 10px;font-size:10px;color:#77879d;white-space:nowrap}.pw-filters button.active{background:#edf3ff;color:#315fca;border-color:#c7d6f4}.pw-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(180px,1fr));gap:12px}.pw-card{position:relative;min-width:0;border:1px solid #e5ebf2;border-radius:11px;overflow:hidden;background:#fff}.pw-card.selected{border-color:#6286d8;box-shadow:0 0 0 2px #6286d820}.pw-check{position:absolute;z-index:2;left:8px;top:8px;background:#fff;border-radius:5px;padding:4px;box-shadow:0 1px 5px #0002}.pw-image{display:grid;place-items:center;width:100%;height:145px;border:0;background:#eef1f5;padding:0;cursor:zoom-in}.pw-image img{width:100%;height:100%;object-fit:contain}.pw-image span{font-size:10px;color:#98a3b2}.pw-card-body{display:grid;gap:7px;padding:10px}.pw-card-body b{font-size:10px;color:#40536c;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.pw-card-body small{font-size:9px;color:#98a2b1}.pw-card-body select,.pw-table-wrap select{width:100%;min-width:0;padding:7px;border:1px solid #dfe6ef;border-radius:7px;background:white;font-size:10px;color:#526781}.pw-card-actions{display:flex;gap:4px;justify-content:space-between}.pw-card-actions button,.pw-table-wrap button{border:0;background:none;color:#6780a2;font-size:9px;cursor:pointer;padding:3px}.pw-card-actions button.danger,.pw-table-wrap button.danger{color:#b85d5d}.pw-table-wrap{overflow:auto;background:#fff;border:1px solid #e5ebf2;border-radius:10px}.pw-table-wrap table{width:100%;border-collapse:collapse}.pw-table-wrap th,.pw-table-wrap td{padding:9px;border-bottom:1px solid #edf0f4;text-align:left;font-size:10px;color:#61738c}.pw-table-wrap th{color:#909baa}.pw-table-wrap img{width:70px;height:55px;object-fit:contain;cursor:zoom-in;background:#f1f3f6}.pw-table-wrap input{accent-color:#315fca}.pw-insert-panel{display:grid;gap:12px;padding:16px;background:#f7f9fc;border:1px solid #e5ebf2;border-radius:12px}.pw-insert-panel h3{margin:0;font-size:14px;color:#364c68}.pw-insert-panel p{margin:5px 0 0;color:#8390a2;font-size:10px;line-height:1.7}.pw-template-row{display:flex;gap:10px;align-items:end}.pw-template-row label:first-child{flex:1}.pw-report-meta{font-size:10px;color:#667e9e}.pw-insert-actions{display:flex;gap:8px;flex-wrap:wrap}.pw-insert-panel>small{font-size:9px;color:#919cab;line-height:1.6}.pw-empty{display:grid;justify-items:center;gap:7px;padding:42px 15px;border:1px dashed #d1dae6;border-radius:12px;color:#98a4b4;font-size:11px}.pw-empty span{font-size:34px;color:#a8bbd9}.pw-empty b{color:#526681}.pw-lightbox{position:fixed;inset:0;background:#17243bc9;display:flex;align-items:center;justify-content:center;z-index:70;padding:20px}.pw-lightbox-card{background:#fff;border-radius:14px;padding:14px;width:min(100%,1000px);height:min(90vh,850px);display:flex;flex-direction:column;gap:12px}.pw-lightbox-card header{display:flex;justify-content:space-between;align-items:center;font-size:12px;color:#425570}.pw-lightbox-card header button{border:0;background:#eef2f7;border-radius:7px;padding:7px 10px}.pw-lightbox-card>img{min-height:0;flex:1;width:100%;object-fit:contain;background:#f0f2f6}.pw-lightbox-card label{display:flex;align-items:center;gap:10px;font-size:11px;color:#66758a}.pw-lightbox-card select{flex:1;max-width:380px;padding:8px;border:1px solid #dfe6ef;border-radius:7px}@media(max-width:760px){.pw-context{grid-template-columns:1fr}.photo-head{flex-direction:column}.pw-template-row{flex-direction:column;align-items:stretch}}
</style>
