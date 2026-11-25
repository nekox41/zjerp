<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'

const systems = [
  '火灾自动报警系统', '消防给水系统', '消火栓系统', '自动喷水灭火系统',
  '防烟排烟系统', '消防应急照明和疏散指示系统', '防火分隔设施',
  '电气火灾监控系统', '消防电源监控系统', '火灾警报和消防应急广播系统',
  '消防专用电话系统', '消防电梯', '消防供配电设施', '建筑灭火器', '其他'
]
const photos = ref([])
const template = ref(null)
const activeFilter = ref('全部')
const viewMode = ref('cards')
const busy = ref(false)
const focused = ref(null)
const error = ref('')
const notice = ref('')
const selectedCount = computed(() => photos.value.filter(p => p.selected).length)
const classifiedCount = computed(() => photos.value.filter(p => !!p.category).length)
const filtered = computed(() => photos.value.filter(p => activeFilter.value === '全部' ||
  (activeFilter.value === '未分类' ? !p.category : p.category === activeFilter.value)))
const displayedSystems = computed(() => systems.filter(s => photos.value.some(p => p.category === s)))
const maxPhotos = 300

function addPhotos(event) {
  error.value = ''
  const incoming = Array.from(event.target.files || [])
  const supported = incoming.filter(f => f.type === 'image/jpeg' || f.type === 'image/png')
  if (incoming.length !== supported.length) error.value = '已跳过非 JPG / PNG 格式的文件'
  if (photos.value.length + supported.length > maxPhotos) {
    error.value = '单批照片最多 300 张，请分批处理'
    event.target.value = ''
    return
  }
  for (const file of supported) photos.value.push({
    id: crypto.randomUUID(), file, preview: URL.createObjectURL(file),
    category: '', selected: false
  })
  event.target.value = ''
}
function removePhoto(item) {
  URL.revokeObjectURL(item.preview)
  photos.value = photos.value.filter(p => p.id !== item.id)
  if (focused.value?.id === item.id) focused.value = null
}
function clearPhotos() {
  photos.value.forEach(p => URL.revokeObjectURL(p.preview))
  photos.value = []
  focused.value = null
  activeFilter.value = '全部'
  notice.value = ''
}
function toggleVisible(checked) { filtered.value.forEach(p => { p.selected = checked }) }
function assignSelected(category) {
  if (!selectedCount.value) return
  photos.value.forEach(p => {
    if (p.selected) { p.category = category; p.selected = false }
  })
  notice.value = '已完成批量分类'
}
function chooseTemplate(event) {
  const file = event.target.files?.[0]
  error.value = ''
  if (!file) return
  if (!file.name.toLowerCase().endsWith('.docx') || file.size > 25 * 1024 * 1024) {
    error.value = '请上传 25MB 以内的 DOCX 报告'
    event.target.value = ''
    return
  }
  template.value = file
}

function saveBlob(blob, name) {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = name
  anchor.click()
  setTimeout(() => URL.revokeObjectURL(url), 1500)
}
async function resizedForDocx(file) {
  // Reduce export size without changing the local originals.
  const bitmap = await createImageBitmap(file)
  try {
    const limit = 1500
    const scale = Math.min(1, limit / Math.max(bitmap.width, bitmap.height))
    const canvas = document.createElement('canvas')
    canvas.width = Math.max(1, Math.round(bitmap.width * scale))
    canvas.height = Math.max(1, Math.round(bitmap.height * scale))
    canvas.getContext('2d').drawImage(bitmap, 0, 0, canvas.width, canvas.height)
    const blob = await new Promise((resolve, reject) =>
      canvas.toBlob(result => result ? resolve(result) : reject(new Error('图片压缩失败')),
        'image/jpeg', .84))
    return new File([blob], file.name.replace(/\.(png|jpe?g)$/i, '') + '.jpg',
      { type: 'image/jpeg' })
  } finally { bitmap.close() }
}
async function exportDocx() {
  error.value = ''; notice.value = ''
  if (!template.value) { error.value = '请先选择要插入照片的 DOCX 报告'; return }
  if (!photos.value.length) { error.value = '请先导入现场照片'; return }
  if (classifiedCount.value !== photos.value.length) {
    error.value = '还有未分类照片，请完成一级分类后再生成报告'
    activeFilter.value = '未分类'
    return
  }
  busy.value = true
  try {
    const form = new FormData()
    form.append('template', template.value)
    for (const photo of photos.value) form.append('photos', await resizedForDocx(photo.file))
    form.append('categories', JSON.stringify(photos.value.map(p => p.category)))
    const token = sessionStorage.getItem('erp-next-token')
    const response = await fetch((import.meta.env.VITE_NEXT_API_BASE || '/next-api') + '/report-photos/docx', {
      method: 'POST', headers: token ? { Authorization: 'Bearer ' + token } : {}, body: form
    })
    if (!response.ok) {
      const payload = await response.json().catch(() => ({}))
      throw new Error(payload.detail || payload.message || '生成失败（HTTP ' + response.status + '）')
    }
    const filename = template.value.name.replace(/\.docx$/i, '') + '-已整理照片.docx'
    saveBlob(await response.blob(), filename)
    notice.value = '照片已按系统分组写入新的 DOCX。请核对排版和报告内容后再正式使用。'
  } catch (e) { error.value = e.message || '生成失败，请重试' }
  finally { busy.value = false }
}
onBeforeUnmount(() => { photos.value.forEach(p => URL.revokeObjectURL(p.preview)) })
</script>

<template>
  <section class="photo-workbench">
    <div class="pw-heading">
      <div><span class="pw-eyebrow">REPORT · PHOTO WORKBENCH</span><h2>现场照片整理</h2>
        <p>批量预览并标记照片所属消防系统，再写入现有 Word 检测报告。</p></div>
    </div>

    <div class="pw-toolbar">
      <label class="pw-upload">＋ 导入照片<input hidden type="file" multiple accept="image/jpeg,image/png" @change="addPhotos"></label>
      <label class="pw-template">上传 DOCX 报告<input type="file" hidden accept=".docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document" @change="chooseTemplate"></label>
      <span class="pw-template-name" :title="template?.name">{{ template?.name || '尚未选择报告模板' }}</span>
      <button type="button" class="pw-export" :disabled="busy || !photos.length" @click="exportDocx">{{ busy ? '正在处理图片…' : '生成 DOCX' }}</button>
    </div>

    <p class="pw-help">第一版会保留原 DOCX 内容，在文档末尾追加按消防系统分组的照片页；不会修改原文件。正式使用前请人工检查 Word 排版。</p>
    <p v-if="error" class="pw-alert pw-error" role="alert">{{ error }}</p>
    <p v-if="notice" class="pw-alert pw-success" role="status">{{ notice }}</p>

    <div class="pw-statline">
      <span>全部 {{ photos.length }} 张</span>
      <span>已分类 {{ classifiedCount }} 张</span>
      <span>待分类 {{ photos.length - classifiedCount }} 张</span>
      <span>已选择 {{ selectedCount }} 张</span>
    </div>

    <div class="pw-controls">
      <div class="pw-filter">
        <button v-for="filter in ['全部', '未分类', ...displayedSystems]" :key="filter" type="button"
          :class="{ chosen: activeFilter === filter }" @click="activeFilter = filter">{{ filter }}</button>
      </div>
      <div class="pw-actions">
        <button type="button" @click="toggleVisible(true)">全选当前</button>
        <button type="button" @click="toggleVisible(false)">取消选择</button>
        <button type="button" @click="viewMode = viewMode === 'cards' ? 'table' : 'cards'">{{ viewMode === 'cards' ? '表格视图' : '卡片视图' }}</button>
        <button type="button" class="danger" @click="clearPhotos" :disabled="!photos.length">清空</button>
      </div>
    </div>

    <div v-if="selectedCount" class="pw-batch">
      <strong>已选 {{ selectedCount }} 张</strong>
      <label>设置所属系统
        <select @change="assignSelected($event.target.value); $event.target.value = ''">
          <option value="">选择系统批量分类…</option>
          <option v-for="s in systems" :key="s" :value="s">{{ s }}</option>
        </select>
      </label>
    </div>

    <div v-if="!photos.length" class="pw-empty">
      <span>▧</span><h3>还没有现场照片</h3><p>选择多张 JPG / PNG 照片即可开始整理。不要求照片包含水印。</p>
    </div>
    <div v-else-if="!filtered.length" class="pw-empty"><p>当前分类下没有照片</p></div>
    <div v-else-if="viewMode === 'cards'" class="pw-grid">
      <article v-for="photo in filtered" :key="photo.id" class="pw-card" :class="{ selected: photo.selected }">
        <label class="pw-checkbox"><input type="checkbox" v-model="photo.selected" aria-label="选择照片"></label>
        <img :src="photo.preview" :alt="photo.file.name" loading="lazy" @click="focused = photo" title="点击放大预览" class="pw-zoomable">
        <div class="pw-card-body">
          <div class="pw-filename" :title="photo.file.name">{{ photo.file.name }}</div>
          <select v-model="photo.category" :aria-label="'分类：'+photo.file.name">
            <option value="">未分类</option><option v-for="s in systems" :key="s" :value="s">{{ s }}</option>
          </select>
          <button class="pw-remove" type="button" @click="removePhoto(photo)">移除</button>
        </div>
      </article>
    </div>
    <div v-else class="pw-table-wrap">
      <table class="pw-table"><thead><tr><th>选择</th><th>预览</th><th>文件</th><th>消防系统</th><th>操作</th></tr></thead>
        <tbody><tr v-for="photo in filtered" :key="photo.id">
          <td><input type="checkbox" v-model="photo.selected" aria-label="选择照片"></td>
          <td><img :src="photo.preview" :alt="photo.file.name" loading="lazy" @click="focused = photo" title="点击放大预览" class="pw-zoomable"></td><td>{{ photo.file.name }}</td>
          <td><select v-model="photo.category"><option value="">未分类</option><option v-for="s in systems" :key="s" :value="s">{{ s }}</option></select></td>
          <td><button class="pw-remove" type="button" @click="removePhoto(photo)">移除</button></td>
        </tr></tbody>
      </table>
    </div>
    <div v-if="focused" class="pw-lightbox" role="dialog" aria-modal="true" aria-label="查看原始照片" @click.self="focused = null">
      <div class="pw-lightbox-content">
        <div class="pw-lightbox-heading">
          <span :title="focused.file.name">{{ focused.file.name }}</span>
          <button type="button" @click="focused = null" aria-label="关闭图片预览">✕</button>
        </div>
        <img :src="focused.preview" :alt="focused.file.name">
        <label>所属系统
          <select v-model="focused.category"><option value="">未分类</option><option v-for="s in systems" :key="s" :value="s">{{ s }}</option></select>
        </label>
      </div>
    </div>
  </section>
</template>

<style scoped>
.photo-workbench{display:grid;gap:19px}.pw-heading h2{margin:5px 0;font-size:23px;color:var(--erp-fg-203149)}.pw-heading p,.pw-help{font-size:12px;color:var(--erp-fg-7d889a);line-height:1.8;margin:4px 0}.pw-eyebrow{font-size:10px;letter-spacing:.15em;color:var(--erp-fg-5079c4);font-weight:700}.pw-toolbar{display:flex;flex-wrap:wrap;gap:10px;align-items:center;padding:18px;background:var(--erp-bg-fff);border:1px solid var(--erp-border-e6ecf3);border-radius:14px}.pw-upload,.pw-template,.pw-export{border:1px solid var(--erp-border-d7e1ef);border-radius:9px;padding:10px 14px;font-size:12px;cursor:pointer;white-space:nowrap}.pw-upload,.pw-export{background:var(--erp-bg-315fca);border-color:var(--erp-border-315fca);color:white;font-weight:700}.pw-template{background:var(--erp-bg-f5f8fd);color:var(--erp-fg-365578)}.pw-export{margin-left:auto}.pw-export:disabled{opacity:.5;cursor:not-allowed}.pw-template-name{font-size:11px;color:var(--erp-fg-7b8798);max-width:260px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.pw-statline{display:flex;flex-wrap:wrap;gap:8px}.pw-statline span{padding:9px 15px;background:var(--erp-bg-fff);border:1px solid var(--erp-border-e5eaf1);border-radius:10px;font-size:12px;color:var(--erp-fg-506178)}.pw-controls,.pw-actions,.pw-filter{display:flex;gap:8px;flex-wrap:wrap;align-items:center}.pw-controls{justify-content:space-between}.pw-controls button,.pw-remove{background:var(--erp-bg-fff);border:1px solid var(--erp-border-dfe6f0);border-radius:8px;padding:8px 11px;color:var(--erp-fg-546379);font-size:11px}.pw-filter button.chosen{color:var(--erp-fg-2856b7);background:var(--erp-bg-ecf2fe);border-color:var(--erp-border-bdd0fa)}.pw-actions .danger,.pw-remove{color:var(--erp-fg-bd5151)}.pw-batch{background:var(--erp-bg-ebf2ff);border:1px solid var(--erp-border-c8d9fa);border-radius:10px;padding:12px 16px;display:flex;gap:16px;align-items:center;flex-wrap:wrap;font-size:12px;color:var(--erp-fg-365b8f)}.pw-batch label{display:flex;align-items:center;gap:9px}.pw-batch select,.pw-card select,.pw-table select{min-width:0;width:100%;padding:8px;border:1px solid var(--erp-border-dce3ef);border-radius:7px;font:inherit;font-size:12px;background:var(--erp-bg-fff);color:var(--erp-fg-4d6078)}.pw-empty{background:var(--erp-bg-fff);text-align:center;padding:65px 15px;border:1px dashed var(--erp-border-cdd6e5);border-radius:14px;color:var(--erp-fg-8b98aa)}.pw-empty span{font-size:44px;color:#adc1df}.pw-empty h3{margin:8px 0;font-size:16px;color:var(--erp-fg-53647e)}.pw-empty p{font-size:12px}.pw-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(178px,1fr));gap:15px}.pw-card{background:var(--erp-bg-fff);border:1px solid var(--erp-border-e1e8f0);border-radius:12px;overflow:hidden;position:relative;min-width:0}.pw-card.selected{border-color:var(--erp-border-5b82d9);box-shadow:0 0 0 2px #5b82d924}.pw-zoomable{cursor:zoom-in}.pw-card>img{width:100%;height:154px;object-fit:contain;background:var(--erp-bg-eef1f5)}.pw-checkbox{position:absolute;top:9px;left:9px;background:var(--erp-bg-fff);border-radius:6px;padding:5px;box-shadow:0 1px 7px #0002}.pw-checkbox input{display:block}.pw-card-body{display:grid;gap:9px;padding:11px}.pw-filename{font-size:11px;color:var(--erp-fg-374966);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.pw-remove{justify-self:end;border:none}.pw-table-wrap{overflow:auto;background:var(--erp-bg-fff);border-radius:12px;border:1px solid var(--erp-border-e5eaf2)}.pw-table{width:100%;border-collapse:collapse}.pw-table td,.pw-table th{padding:10px;border-bottom:1px solid var(--erp-border-edf0f5);text-align:left;font-size:11px}.pw-table img{height:70px;width:95px;object-fit:contain;background:var(--erp-bg-f0f2f6)}.pw-table select{min-width:190px}.pw-alert{padding:12px 14px;font-size:12px;border-radius:8px}.pw-error{background:var(--erp-bg-fff0ed);color:var(--erp-fg-a83232)}.pw-success{background:var(--erp-bg-e7f8ee);color:var(--erp-fg-257448)}@media(max-width:650px){.pw-export{margin-left:0}.pw-toolbar>*{flex:1}.pw-template-name{flex-basis:100%}}
.pw-lightbox{position:fixed;inset:0;background:#142036ce;display:flex;align-items:center;justify-content:center;z-index:50;padding:20px}.pw-lightbox-content{background:var(--erp-bg-fff);padding:16px;border-radius:14px;max-width:min(100%,1000px);width:100%;max-height:95vh;display:flex;flex-direction:column;gap:12px}.pw-lightbox-heading{display:flex;justify-content:space-between;align-items:center;gap:15px;font-size:13px;color:var(--erp-fg-384962)}.pw-lightbox-heading span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.pw-lightbox-heading button{border:0;background:var(--erp-bg-edf2fa);border-radius:8px;padding:8px 12px}.pw-lightbox-content>img{width:100%;min-height:0;object-fit:contain;flex:1;background:var(--erp-bg-f2f4f8)}.pw-lightbox-content label{display:flex;align-items:center;gap:12px;font-size:12px;color:var(--erp-fg-64748b)}.pw-lightbox-content select{max-width:350px;flex:1;padding:9px;border:1px solid var(--erp-border-dce3ee);border-radius:8px}
</style>
