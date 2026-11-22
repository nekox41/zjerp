<template>
  <section class="zj-child-records">
    <div class="zj-summary">
      <span>{{ kind === 'issues' ? '记录现场排查发现的问题及整改进展' : '安排现场、完工、出报告等工作节点' }}</span>
      <el-button v-if="canEdit" type="primary" icon="Plus" @click="edit()">
        {{ kind === 'issues' ? '登记问题' : '创建计划' }}
      </el-button>
    </div>

    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon>
      <el-button link @click="load">重试</el-button>
    </el-alert>

    <el-table v-loading="loading" :data="rows" :empty-text="kind === 'issues' ? '暂无问题记录' : '暂无计划记录'">
      <template v-if="kind === 'issues'">
        <el-table-column prop="title" label="问题" min-width="220" />
        <el-table-column label="程度" width="100">
          <template #default="{ row }">{{ labels[row.severity] }}</template>
        </el-table-column>
        <el-table-column label="进展" width="100">
          <template #default="{ row }">{{ labels[row.status] }}</template>
        </el-table-column>
        <el-table-column prop="foundDate" label="发现日期" width="130" />
        <el-table-column prop="resolvedDate" label="解决日期" width="130" />
      </template>
      <template v-else>
        <el-table-column prop="title" label="工作事项" min-width="200" />
        <el-table-column label="类型" width="110">
          <template #default="{ row }">{{ labels[row.planType] }}</template>
        </el-table-column>
        <el-table-column prop="startDate" label="计划开始" width="130" />
        <el-table-column prop="endDate" label="计划完成" width="130" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">{{ labels[row.status] }}</template>
        </el-table-column>
      </template>
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="edit(row)">{{ canEdit ? '编辑' : '查看' }}</el-button>
          <el-button v-if="canEdit" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-if="total > pageSize"
      v-model:current-page="page"
      :page-size="pageSize"
      :total="total"
      layout="total, prev, pager, next"
      @current-change="load"
    />

    <el-dialog
      v-model="dialogOpen"
      :title="dialogTitle"
      width="min(680px, 96vw)"
      :close-on-click-modal="false"
      :before-close="beforeClose"
    >
      <el-form label-position="top" :disabled="!canEdit || saving">
        <el-form-item label="标题" required>
          <el-input v-model="form.title" maxlength="200" />
        </el-form-item>

        <template v-if="kind === 'issues'">
          <div class="zj-grid">
            <el-form-item label="问题程度">
              <el-select v-model="form.severity">
                <el-option v-for="value in ['low', 'medium', 'high']" :key="value" :label="labels[value]" :value="value" />
              </el-select>
            </el-form-item>
            <el-form-item label="进展">
              <el-select v-model="form.status">
                <el-option v-for="value in ['open', 'processing', 'resolved']" :key="value" :label="labels[value]" :value="value" />
              </el-select>
            </el-form-item>
            <el-form-item label="发现日期">
              <el-date-picker v-model="form.foundDate" value-format="YYYY-MM-DD" />
            </el-form-item>
            <el-form-item label="解决日期">
              <el-date-picker v-model="form.resolvedDate" value-format="YYYY-MM-DD" />
            </el-form-item>
          </div>
          <el-form-item label="问题描述">
            <el-input v-model="form.description" type="textarea" :rows="5" maxlength="10000" />
          </el-form-item>
          <el-form-item label="现场照片 / 附件">
            <FileUpload v-model="form.files" :disabled="!canEdit" :limit="20" :file-type="['png', 'jpg', 'jpeg', 'pdf', 'doc', 'docx']" />
          </el-form-item>
        </template>

        <template v-else>
          <div class="zj-grid">
            <el-form-item label="计划类型">
              <el-select v-model="form.planType">
                <el-option v-for="value in ['visit', 'completion', 'report', 'other']" :key="value" :label="labels[value]" :value="value" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="form.status">
                <el-option v-for="value in ['planned', 'in_progress', 'done', 'cancelled']" :key="value" :label="labels[value]" :value="value" />
              </el-select>
            </el-form-item>
            <el-form-item label="计划开始日期" required>
              <el-date-picker v-model="form.startDate" value-format="YYYY-MM-DD" />
            </el-form-item>
            <el-form-item label="计划完成日期">
              <el-date-picker v-model="form.endDate" value-format="YYYY-MM-DD" />
            </el-form-item>
          </div>
          <el-form-item label="计划说明">
            <el-input v-model="form.remark" type="textarea" :rows="5" maxlength="5000" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="close">关闭</el-button>
        <el-button v-if="canEdit" type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { onBeforeRouteLeave } from 'vue-router'
import FileUpload from '@/components/FileUpload/index.vue'
import { projectChildren, saveProjectChild, deleteProjectChild } from '@/api/workProject'

const props = defineProps({
  projectId: { type: [String, Number], required: true },
  kind: { type: String, required: true, validator: value => ['issues', 'plans'].includes(value) },
  canEdit: { type: Boolean, default: false },
  active: { type: Boolean, default: false }
})
const emit = defineEmits(['busy-change'])
const { proxy } = getCurrentInstance()

const labels = {
  low: '一般', medium: '较严重', high: '严重',
  open: '待处理', processing: '处理中', resolved: '已解决',
  visit: '现场作业', completion: '完工', report: '出报告', other: '其他',
  planned: '待执行', in_progress: '执行中', done: '已完成', cancelled: '已取消'
}
const rows = ref([])
const page = ref(1)
const pageSize = 20
const total = ref(0)
const loading = ref(false)
const error = ref('')
const dialogOpen = ref(false)
const form = ref({})
const saving = ref(false)
let snapshot = ''
let requestSequence = 0

const dialogTitle = computed(() => {
  const label = props.kind === 'issues' ? '问题' : '计划'
  if (!props.canEdit) return '查看' + label
  return (form.value.id ? '编辑' : '新增') + label
})

function createForm(row) {
  if (row) return { ...row }
  return props.kind === 'issues'
    ? { title: '', description: '', severity: 'low', status: 'open', foundDate: null, resolvedDate: null, files: '' }
    : { title: '', planType: 'visit', status: 'planned', startDate: null, endDate: null, remark: '' }
}

async function load() {
  if (!props.projectId || !props.active) return
  const sequence = ++requestSequence
  loading.value = true
  error.value = ''
  try {
    const result = await projectChildren(props.projectId, props.kind, { pageNum: page.value, pageSize })
    if (sequence === requestSequence) {
      rows.value = result.rows || []
      total.value = result.total || 0
    }
  } catch {
    if (sequence === requestSequence) {
      rows.value = []
      total.value = 0
      error.value = '数据加载失败，请重试'
    }
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}

function edit(row) {
  form.value = createForm(row)
  snapshot = JSON.stringify(form.value)
  dialogOpen.value = true
}

function isDirty() {
  return props.canEdit && snapshot !== JSON.stringify(form.value)
}

async function allowLeave() {
  if (saving.value) return false
  if (!dialogOpen.value || !isDirty()) return true
  try {
    await proxy.$modal.confirm('当前记录有未保存的修改，确认放弃？')
    return true
  } catch {
    return false
  }
}

async function beforeClose(done) {
  if (await allowLeave()) done()
}

async function close() {
  if (await allowLeave()) dialogOpen.value = false
}

async function save() {
  if (!form.value.title?.trim() || (props.kind === 'plans' && !form.value.startDate)) {
    proxy.$modal.msgError(props.kind === 'plans' ? '请填写标题和计划开始日期' : '请填写标题')
    return
  }
  saving.value = true
  emit('busy-change', true)
  try {
    await saveProjectChild(props.projectId, props.kind, form.value)
    dialogOpen.value = false
    proxy.$modal.msgSuccess('已保存')
    await load()
  } finally {
    saving.value = false
    emit('busy-change', false)
  }
}

async function remove(row) {
  try {
    await proxy.$modal.confirm(`确认删除“${row.title}”？`)
  } catch {
    return
  }
  saving.value = true
  emit('busy-change', true)
  try {
    await deleteProjectChild(props.projectId, props.kind, row.id)
    await load()
  } finally {
    saving.value = false
    emit('busy-change', false)
  }
}

watch(() => [props.projectId, props.active], ([projectId, active]) => {
  if (projectId && active) {
    page.value = 1
    load()
  }
}, { immediate: true })

async function leaveTab() {
  if (!await allowLeave()) return false
  dialogOpen.value = false
  return true
}

onBeforeRouteLeave(allowLeave)
defineExpose({ load, leaveTab })
</script>
