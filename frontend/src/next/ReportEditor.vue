<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { api, downloadFile, uploadFile } from './api'

const props = defineProps({
  reportId: { type: [Number, String], required: true },
  permissions: { type: Array, default: () => [] }
})
const emit = defineEmits(['close', 'open-photos', 'changed'])
const report = ref(null)
const loading = ref(false)
const busy = ref(false)
const error = ref('')
const notice = ref('')
const actionReason = ref('')
const form = ref({ title: '', summary: '', scope: '', methods: '', findings: '', conclusion: '' })
const deliveryForm = ref({ recipientName: '', contactName: '', contactPhone: '', deliveryAddress: '', courierName: '', trackingNo: '', notes: '' })
const can = p => props.permissions.includes(p)
const editable = computed(() => ['draft', 'returned'].includes(report.value?.status))
const statusLabels = {
  draft: '草稿', submitted: '待审核', reviewing: '审核中', returned: '已退回',
  approved: '审核通过', issued: '已签发', archived: '已归档', withdrawn: '已撤回'
}
const statusClass = computed(() => report.value?.status || 'draft')

async function load() {
  loading.value = true; error.value = ''; notice.value = ''
  try {
    report.value = await api('/business/reports/' + props.reportId)
    const c = report.value.content || {}
    form.value = {
      title: report.value.title || '', summary: c.summary || '', scope: c.scope || '',
      methods: c.methods || '', findings: c.findings || '', conclusion: c.conclusion || ''
    }
  } catch (e) { error.value = e.message }
  finally { loading.value = false }
}
async function save() {
  if (!editable.value) return
  busy.value = true; error.value = ''; notice.value = ''
  try {
    report.value = await api('/business/reports/' + props.reportId, {
      method: 'PUT',
      body: JSON.stringify({
        expectedVersionNo: report.value.current_version_no,
        title: form.value.title,
        content: {
          summary: form.value.summary, scope: form.value.scope,
          methods: form.value.methods, findings: form.value.findings, conclusion: form.value.conclusion
        }
      })
    })
    notice.value = '报告已保存为新版本。'
    emit('changed')
  } catch (e) { error.value = e.message }
  finally { busy.value = false }
}
async function attach(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  busy.value = true; error.value = ''
  try {
    const saved = await uploadFile(file)
    report.value = await api('/business/reports/' + props.reportId + '/attachments', {
      method: 'POST', body: JSON.stringify({ fileId: saved.id })
    })
    notice.value = '附件已加入当前报告版本。'
  } catch (e) { error.value = e.message }
  finally { busy.value = false }
}
async function action(name, body = {}) {
  busy.value = true; error.value = ''; notice.value = ''
  try {
    report.value = await api('/business/reports/' + props.reportId + '/' + name, {
      method: 'POST', body: JSON.stringify(body)
    })
    await load()
    notice.value = '报告状态已更新。'
    emit('changed')
  } catch (e) { error.value = e.message }
  finally { busy.value = false }
}
async function createDelivery() {
  busy.value = true; error.value = ''
  try {
    await api('/business/reports/' + props.reportId + '/deliveries', {
      method: 'POST', body: JSON.stringify(deliveryForm.value)
    })
    deliveryForm.value = { recipientName: '', contactName: '', contactPhone: '', deliveryAddress: '', courierName: '', trackingNo: '', notes: '' }
    await load()
    notice.value = '邮寄交付记录已创建。'
  } catch (e) { error.value = e.message }
  finally { busy.value = false }
}
async function updateDelivery(item, status) {
  busy.value = true; error.value = ''
  try {
    const body = { deliveryStatus: status }
    if (status === 'sent') {
      body.sentAt = new Date().toISOString().slice(0, 19)
      body.courierName = item.courier_name
      body.trackingNo = item.tracking_no
    }
    if (status === 'delivered') body.deliveredAt = new Date().toISOString().slice(0, 19)
    await api('/business/deliveries/' + item.id, { method: 'PUT', body: JSON.stringify(body) })
    await load()
  } catch (e) { error.value = e.message }
  finally { busy.value = false }
}
function openPhotos() {
  emit('open-photos', { reportId: report.value.id, projectId: report.value.project_id || report.value.project?.id })
}
watch(() => props.reportId, load)
onMounted(load)
</script>

<template>
  <section class="report-editor">
    <header class="re-header">
      <button class="re-back" type="button" @click="$emit('close')">← 返回报告列表</button>
      <div class="re-title">
        <div><span class="eyebrow">REPORT / {{ report?.report_no || '…' }}</span><h2>{{ report?.title || '报告详情' }}</h2></div>
        <span v-if="report" class="re-status" :class="statusClass">{{ statusLabels[report.status] || report.status }}</span>
      </div>
      <div v-if="report" class="re-meta">版本 v{{ report.current_version_no }} · 修订版 {{ report.revision_no }} · {{ report.report_type }}</div>
    </header>

    <p v-if="error" class="re-message error" role="alert">{{ error }}</p>
    <p v-if="notice" class="re-message success" role="status">{{ notice }}</p>
    <div v-if="loading" class="re-empty">正在加载报告…</div>
    <template v-else-if="report">
      <section class="re-panel">
        <div class="re-section-title"><div><h3>报告内容</h3><p>每次保存都会生成新的报告版本，历史版本不会被覆盖。</p></div><button type="button" class="re-primary" :disabled="busy || !editable || !can('report:write')" @click="save">保存新版本</button></div>
        <div class="re-form">
          <label class="wide">报告标题<input v-model="form.title" :disabled="!editable"></label>
          <label>报告摘要<textarea v-model="form.summary" rows="3" :disabled="!editable"></textarea></label>
          <label>检测范围<textarea v-model="form.scope" rows="3" :disabled="!editable"></textarea></label>
          <label>检测方法<textarea v-model="form.methods" rows="3" :disabled="!editable"></textarea></label>
          <label>主要发现<textarea v-model="form.findings" rows="3" :disabled="!editable"></textarea></label>
          <label class="wide">服务结论<textarea v-model="form.conclusion" rows="3" :disabled="!editable"></textarea></label>
        </div>
        <div class="re-actions">
          <button v-if="can('photo:read')" type="button" class="re-secondary" :disabled="busy" @click="openPhotos">整理并插入现场照片</button>
          <label v-if="editable && can('report:write')" class="re-upload">＋ 添加报告附件<input type="file" hidden accept=".docx,.pdf,.jpg,.jpeg,.png,.txt" @change="attach"></label>
        </div>
      </section>

      <section class="re-panel">
        <div class="re-section-title"><div><h3>报告流转</h3><p>编制、审核、签发相互分离，状态由服务端校验。</p></div></div>
        <div class="re-workflow">
          <div v-for="(step, index) in ['draft','submitted','reviewing','approved','issued','archived']" :key="step" class="re-step" :class="{ current: report.status === step, done: ['submitted','reviewing','approved','issued','archived'].includes(report.status) && ['draft','submitted','reviewing','approved','issued','archived'].indexOf(report.status) >= index }">
            <i>{{ index + 1 }}</i><span>{{ statusLabels[step] }}</span>
          </div>
        </div>
        <label v-if="['reviewing','submitted'].includes(report.status) && can('report:write')" class="re-reason">操作说明<input v-model="actionReason" placeholder="退回或撤回时填写原因"></label>
        <div class="re-actions">
          <button v-if="report.status==='draft' && can('report:write')" class="re-primary" :disabled="busy" @click="action('submit')">提交审核</button>
          <button v-if="report.status==='submitted' && can('report:review')" class="re-primary" :disabled="busy" @click="action('start-review')">开始审核</button>
          <button v-if="report.status==='reviewing' && can('report:review')" class="re-secondary" :disabled="busy || !actionReason.trim()" @click="action('return',{reason:actionReason})">退回修改</button>
          <button v-if="report.status==='reviewing' && can('report:review')" class="re-primary" :disabled="busy" @click="action('approve')">审核通过</button>
          <button v-if="report.status==='approved' && can('report:issue')" class="re-primary" :disabled="busy" @click="action('issue')">签发报告</button>
          <button v-if="report.status==='issued' && can('report:issue')" class="re-primary" :disabled="busy" @click="action('archive')">归档报告</button>
          <button v-if="['submitted','reviewing'].includes(report.status) && can('report:write')" class="re-secondary" :disabled="busy || !actionReason.trim()" @click="action('withdraw',{reason:actionReason})">撤回</button>
          <button v-if="['issued','archived'].includes(report.status) && can('report:write')" class="re-secondary" :disabled="busy" @click="action('revise')">创建修订版</button>
        </div>
        <p v-if="report.return_reason" class="re-return">退回原因：{{ report.return_reason }}</p>
      </section>

      <section class="re-panel">
        <div class="re-section-title"><div><h3>附件与版本</h3><p>当前版本附件及历史版本摘要。</p></div></div>
        <div class="re-two-col">
          <div><h4>当前版本附件</h4><div v-for="file in report.attachments || []" :key="file.attachment_id" class="re-file">
            <span class="file-icon">DOC</span><div><b>{{ file.original_name }}</b><small>{{ file.media_type }} · {{ Math.round(file.file_size/1024) }} KB</small></div>
            <button type="button" @click="downloadFile(file.file_id,file.original_name)">下载</button>
          </div><p v-if="!report.attachments?.length" class="re-empty">暂无附件</p></div>
          <div><h4>版本历史</h4><div v-for="version in report.versions || []" :key="version.version_no" class="re-version">
            <b>v{{ version.version_no }}</b><span>{{ version.created_at }}</span><small>{{ version.content_sha256?.slice(0,16) }}…</small>
          </div></div>
        </div>
      </section>

      <section class="re-panel">
        <div class="re-section-title"><div><h3>邮寄与交付</h3><p>报告签发或归档后登记寄送、快递单号和签收状态。</p></div></div>
        <form v-if="['issued','archived'].includes(report.status) && can('report:write')" class="re-delivery-form" @submit.prevent="createDelivery">
          <label>收件单位<input v-model="deliveryForm.recipientName" required></label>
          <label>联系人<input v-model="deliveryForm.contactName"></label>
          <label>联系电话<input v-model="deliveryForm.contactPhone"></label>
          <label class="wide">收件地址<input v-model="deliveryForm.deliveryAddress" required></label>
          <label>快递公司<input v-model="deliveryForm.courierName"></label>
          <label>快递单号<input v-model="deliveryForm.trackingNo"></label>
          <button class="re-primary" :disabled="busy">登记邮寄</button>
        </form>
        <div v-for="delivery in report.deliveries || []" :key="delivery.id" class="re-delivery">
          <div><b>{{ delivery.recipient_name }}</b><small>{{ delivery.delivery_address }}</small><small>{{ delivery.courier_name || '未填写快递公司' }} · {{ delivery.tracking_no || '暂无单号' }}</small></div>
          <span class="re-status" :class="delivery.delivery_status">{{ ({prepared:'待寄出',sent:'已寄出',delivered:'已签收',returned:'退回',cancelled:'已取消'})[delivery.delivery_status] }}</span>
          <button v-if="delivery.delivery_status==='prepared' && can('report:write')" type="button" @click="updateDelivery(delivery,'sent')">标记寄出</button>
          <button v-if="delivery.delivery_status==='sent' && can('report:write')" type="button" @click="updateDelivery(delivery,'delivered')">标记签收</button>
        </div>
        <p v-if="!report.deliveries?.length" class="re-empty">暂无邮寄记录</p>
      </section>
    </template>
  </section>
</template>

<style scoped>
.report-editor{display:grid;gap:16px}.re-header{display:grid;gap:8px}.re-back{justify-self:start;border:0;background:none;color:#59739a;font-size:12px;padding:0;cursor:pointer}.re-title{display:flex;align-items:center;justify-content:space-between;gap:12px}.re-title h2{font-size:22px;margin:4px 0;color:#24344c}.re-meta{font-size:11px;color:#8995a7}.re-status{display:inline-flex;padding:6px 10px;border-radius:20px;background:#edf2f8;color:#50647e;font-size:11px;white-space:nowrap}.re-status.issued,.re-status.archived,.re-status.delivered{background:#e5f5ed;color:#28764d}.re-status.returned,.re-status.returned{background:#fff1e9;color:#b15d35}.re-status.reviewing,.re-status.submitted{background:#fff6dd;color:#916d21}.re-panel{background:#fff;border:1px solid #e5ebf2;border-radius:14px;padding:18px;display:grid;gap:15px}.re-section-title{display:flex;justify-content:space-between;align-items:center;gap:12px}.re-section-title h3{margin:0;font-size:15px;color:#2b3c55}.re-section-title p{margin:5px 0 0;color:#8b96a6;font-size:11px}.re-form,.re-delivery-form{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.re-form label,.re-delivery-form label,.re-reason{display:grid;gap:6px;font-size:11px;color:#62738b}.re-form label.wide,.re-delivery-form label.wide{grid-column:1/-1}.re-form input,.re-form textarea,.re-delivery-form input,.re-reason input{border:1px solid #dfe6ef;border-radius:8px;padding:10px;font:inherit;color:#33465f;background:#fff}.re-form input:disabled,.re-form textarea:disabled{background:#f7f8fa;color:#7e8998}.re-actions{display:flex;flex-wrap:wrap;gap:8px}.re-primary,.re-secondary,.re-upload{border:1px solid #d8e1ed;border-radius:8px;padding:9px 13px;font-size:11px;cursor:pointer;background:#fff;color:#4d6280}.re-primary{background:#315fca;color:#fff;border-color:#315fca}.re-primary:disabled,.re-secondary:disabled{opacity:.5;cursor:not-allowed}.re-upload{display:inline-flex;align-items:center}.re-workflow{display:grid;grid-template-columns:repeat(6,minmax(0,1fr));gap:7px}.re-step{display:grid;justify-items:center;gap:6px;color:#9aa5b3;font-size:10px;text-align:center}.re-step i{width:25px;height:25px;border-radius:50%;display:grid;place-items:center;background:#eef1f5;font-style:normal}.re-step.done i,.re-step.current i{background:#315fca;color:white}.re-step.current span{color:#315fca;font-weight:700}.re-reason{max-width:480px}.re-return{padding:10px;background:#fff5ec;border-radius:8px;color:#9b5a34;font-size:12px}.re-two-col{display:grid;grid-template-columns:1fr 1fr;gap:24px}.re-two-col h4{font-size:12px;color:#60718a}.re-file,.re-version,.re-delivery{display:flex;align-items:center;gap:10px;padding:10px 0;border-top:1px solid #edf0f4;font-size:11px}.re-file div,.re-delivery>div{display:grid;gap:4px;min-width:0;flex:1}.re-file b,.re-delivery b{color:#3b4c63;overflow-wrap:anywhere}.re-file small,.re-delivery small,.re-version small{color:#929eae;font-size:10px}.file-icon{padding:6px;background:#fcecea;border-radius:5px;color:#bd6257;font-size:9px}.re-file button,.re-delivery button{border:1px solid #dfe6ef;background:#fff;border-radius:7px;padding:6px 9px;color:#587194;font-size:10px}.re-version span{color:#8995a7;margin-left:auto}.re-delivery-form{margin-bottom:12px}.re-delivery .re-status{margin-right:8px}.re-empty{padding:15px;color:#96a1af;text-align:center;font-size:11px}.re-message{padding:10px 12px;border-radius:8px;font-size:12px}.re-message.error{background:#fff0ed;color:#a43e36}.re-message.success{background:#e8f7ee;color:#2e754e}@media(max-width:720px){.re-two-col{grid-template-columns:1fr}.re-workflow{grid-template-columns:repeat(3,1fr)}.re-form,.re-delivery-form{grid-template-columns:1fr}.re-form label.wide,.re-delivery-form label.wide{grid-column:auto}.re-title{align-items:flex-start;flex-direction:column}}
</style>
