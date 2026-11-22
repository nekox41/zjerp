<template>
  <div class="upload-file-plus">
    <el-upload
      :action="uploadUrl"
      :headers="headers"
      :data="uploadData"
      :show-file-list="false"
      :before-upload="beforeUpload"
      :on-success="handleSuccess"
      :on-error="handleError"
      :multiple="limit > 1"
      :disabled="disabled"
    >
      <el-button type="primary" plain :disabled="disabled" :loading="uploading">
        <el-icon><Upload /></el-icon>
        <span>选择文件</span>
      </el-button>
      <template #tip>
        <div class="el-upload__tip">
          支持 {{ fileTypeText }}，单文件不超过 {{ fileSize }}MB，最多 {{ limit }} 个
        </div>
      </template>
    </el-upload>

    <ul v-if="fileList.length > 0" class="file-list">
      <li v-for="f in fileList" :key="f.fileId">
        <el-icon><Document /></el-icon>
        <a :href="fileUrl(f.fileUrl)" target="_blank" class="file-name" :title="f.fileName">
          {{ f.fileName }}
        </a>
        <span class="file-size">{{ formatSize(f.fileSize) }}</span>
        <el-icon v-if="!disabled" class="del-icon" @click="removeFile(f)"><Close /></el-icon>
      </li>
    </ul>
  </div>
</template>

<script setup>
import { ref, computed, watch, getCurrentInstance } from 'vue'
import { Upload, Document, Close } from '@element-plus/icons-vue'
import { getToken } from '@/utils/auth'
import { listByIds } from '@/api/system/file'

import { fileUrl } from '@/utils/files'

const { proxy } = getCurrentInstance()

const props = defineProps({
  /** 绑定的 fileId 数组，如 [1, 2, 3] */
  modelValue: {
    type: Array,
    default: () => []
  },
  /** 业务类型，如 'contract' */
  bizType: {
    type: String,
    default: ''
  },
  /** 业务主键，新增时可传 null，保存后调 bindBiz 回填 */
  bizId: {
    type: [Number, String],
    default: null
  },
  /** 最多上传个数 */
  limit: {
    type: Number,
    default: 5
  },
  /** 单文件大小限制（MB） */
  fileSize: {
    type: Number,
    default: 50
  },
  /** 允许的扩展名，如 ['pdf', 'jpg']，空数组表示不限制 */
  fileType: {
    type: Array,
    default: () => []
  },
  /** 是否只读 */
  disabled: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue'])

const baseUrl = import.meta.env.VITE_APP_BASE_API
const uploadUrl = baseUrl + '/system/file/upload'
const headers = { Authorization: 'Bearer ' + getToken() }

const fileList = ref([])
const pendingUploads = ref(0)
const uploading = computed(() => pendingUploads.value > 0)

const uploadData = computed(() => ({
  bizType: props.bizType || '',
  bizId: props.bizId || ''
}))

const fileTypeText = computed(() =>
  props.fileType.length > 0 ? props.fileType.join(' / ') : '任意格式'
)

// 监听 v-model，按 fileId 回显
watch(
  () => props.modelValue,
  async (ids, _, onCleanup) => {
    let cancelled = false
    onCleanup(() => { cancelled = true })
    if (!ids || ids.length === 0) {
      fileList.value = []
      return
    }
    try {
      const res = await listByIds(ids)
      if (cancelled) return
      // 保持顺序与 v-model 一致
      const map = {}
      ;(res.data || []).forEach(f => (map[f.fileId] = f))
      fileList.value = ids.map(id => map[id]).filter(Boolean)
    } catch (e) {
      fileList.value = []
    }
  },
  { immediate: true, deep: true }
)

function beforeUpload(file) {
  // 格式校验
  if (props.fileType.length > 0) {
    const ext = file.name.split('.').pop().toLowerCase()
    const allowed = props.fileType.map(t => t.toLowerCase())
    if (!allowed.includes(ext)) {
      proxy.$modal.msgError(`文件格式不正确，只允许 ${fileTypeText.value}`)
      return false
    }
  }
  // 大小校验
  if (file.size / 1024 / 1024 > props.fileSize) {
    proxy.$modal.msgError(`文件大小不能超过 ${props.fileSize}MB`)
    return false
  }
  // 数量校验
  if (fileList.value.length + pendingUploads.value >= props.limit) {
    proxy.$modal.msgError(`最多只能上传 ${props.limit} 个文件`)
    return false
  }
  pendingUploads.value++
  return true
}

function handleSuccess(res) {
  pendingUploads.value = Math.max(0, pendingUploads.value - 1)
  if (res.code !== 200) {
    proxy.$modal.msgError(res.msg || '上传失败')
    return
  }
  const f = res.data
  // 更新 v-model
  emit('update:modelValue', [...fileList.value.map(file => file.fileId), f.fileId])
  // 追加到本地列表
  fileList.value = [...fileList.value, f]
  proxy.$modal.msgSuccess('上传成功')
}

function handleError(err) {
  pendingUploads.value = Math.max(0, pendingUploads.value - 1)
  proxy.$modal.msgError('上传失败：' + (err.message || '未知错误'))
}

async function removeFile(f) {
  try {
    await proxy.$modal.confirm(`确认移除文件「${f.fileName}」吗？`)
    emit('update:modelValue', props.modelValue.filter(id => id !== f.fileId))
    fileList.value = fileList.value.filter(x => x.fileId !== f.fileId)
    proxy.$modal.msgSuccess('已移除，保存表单后生效')
  } catch (e) {
    // 用户取消删除，不做处理
  }
}

function formatSize(size) {
  if (!size) return '0 B'
  if (size < 1024) return size + ' B'
  if (size < 1024 * 1024) return (size / 1024).toFixed(2) + ' KB'
  return (size / 1024 / 1024).toFixed(2) + ' MB'
}
</script>

<style scoped>
.upload-file-plus {
  line-height: 1.5;
}
.file-list {
  margin-top: 8px;
  padding: 0;
  list-style: none;
}
.file-list li {
  display: flex;
  align-items: center;
  padding: 4px 0;
  font-size: 13px;
}
.file-list .file-name {
  margin: 0 8px;
  color: #409eff;
  text-decoration: none;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.file-list .file-name:hover {
  text-decoration: underline;
}
.file-list .file-size {
  color: #909399;
  margin-right: 8px;
  font-size: 12px;
}
.file-list .del-icon {
  color: #f56c6c;
  cursor: pointer;
}
.file-list .del-icon:hover {
  color: #f23c3c;
}
</style>