<template>
  <div class="upload-image-plus">
    <!-- ① 差异点：用 list-type="picture-card" 展示缩略图 -->
    <el-upload
      :action="uploadUrl"
      :headers="headers"
      :data="uploadData"
      list-type="picture-card"
      :file-list="displayFileList"
      :show-file-list="true"
      :before-upload="beforeUpload"
      :on-success="handleSuccess"
      :on-error="handleError"
      :on-remove="handleRemove"
      :on-preview="handlePreview"
      :multiple="limit > 1"
      :disabled="disabled || imageList.length >= limit"
      :class="{ 'hide-upload-btn': imageList.length >= limit || disabled }"
    >
      <el-icon><Plus /></el-icon>
    </el-upload>

    <!-- ② 差异点：点击预览用 el-image 大图查看 -->
    <el-image-viewer v-if="previewVisible" :url-list="[previewUrl]" @close="previewVisible = false" />

    <div class="el-upload__tip">
      支持 {{ fileTypeText }}，单图不超过 {{ fileSize }}MB，最多 {{ limit }} 张
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, getCurrentInstance } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { getToken } from '@/utils/auth'
import { listByIds } from '@/api/system/file'

import { fileUrl } from '@/utils/files'

const { proxy } = getCurrentInstance()

const props = defineProps({
  modelValue: { type: Array, default: () => [] },   // fileId 数组
  bizType:    { type: String, default: '' },
  bizId:      { type: [Number, String], default: null },
  limit:      { type: Number, default: 5 },
  fileSize:   { type: Number, default: 10 },
  // ③ 差异点：默认只允许图片格式
  fileType:   { type: Array, default: () => ['jpg', 'jpeg', 'png', 'gif', 'webp'] },
  disabled:   { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue'])

const baseUrl = import.meta.env.VITE_APP_BASE_API
const uploadUrl = baseUrl + '/system/file/upload'
const headers = { Authorization: 'Bearer ' + getToken() }

const imageList = ref([])       // 存储完整的 SysFile 对象（含 fileUrl）
const pendingUploads = ref(0)
const uploading = computed(() => pendingUploads.value > 0)
const previewVisible = ref(false)
const previewUrl = ref('')

const uploadData = computed(() => ({
  bizType: props.bizType || '',
  bizId: props.bizId || ''
}))

const fileTypeText = computed(() =>
  props.fileType.length > 0 ? props.fileType.join(' / ') : '任意图片格式'
)

// ④ 差异点：转换成 el-upload 需要的格式 {name, url, uid}
const displayFileList = computed(() =>
  imageList.value.map(f => ({
    name: f.fileName,
    url: fileUrl(f.fileUrl),
    uid: f.fileId,
    fileId: f.fileId
  }))
)

// 监听 v-model，回显
watch(
  () => props.modelValue,
  async (ids, _, onCleanup) => {
    let cancelled = false
    onCleanup(() => { cancelled = true })
    if (!ids || ids.length === 0) {
      imageList.value = []
      return
    }
    try {
      const res = await listByIds(ids)
      if (cancelled) return
      const map = {}
      ;(res.data || []).forEach(f => (map[f.fileId] = f))
      imageList.value = ids.map(id => map[id]).filter(Boolean)
    } catch (e) {
      imageList.value = []
    }
  },
  { immediate: true, deep: true }
)

function beforeUpload(file) {
  // ⑤ 差异点：双重校验（扩展名 + MIME type）
  const ext = file.name.split('.').pop().toLowerCase()
  const allowed = props.fileType.map(t => t.toLowerCase())
  if (!allowed.includes(ext)) {
    proxy.$modal.msgError(`图片格式不正确，只允许 ${fileTypeText.value}`)
    return false
  }
  if (!file.type.startsWith('image/')) {
    proxy.$modal.msgError('只能上传图片文件')
    return false
  }
  if (file.size / 1024 / 1024 > props.fileSize) {
    proxy.$modal.msgError(`图片大小不能超过 ${props.fileSize}MB`)
    return false
  }
  if (imageList.value.length + pendingUploads.value >= props.limit) {
    proxy.$modal.msgError(`最多只能上传 ${props.limit} 张图片`)
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
  emit('update:modelValue', [...imageList.value.map(file => file.fileId), f.fileId])
  imageList.value = [...imageList.value, f]
  proxy.$modal.msgSuccess('上传成功')
}

function handleError(err) {
  pendingUploads.value = Math.max(0, pendingUploads.value - 1)
  proxy.$modal.msgError('上传失败：' + (err.message || '未知错误'))
}

// ⑥ 差异点：el-upload 的 on-remove 回调，file.uid 里带的是 fileId
async function handleRemove(file) {
  const fileId = file.fileId || file.uid
  try {
    emit('update:modelValue', props.modelValue.filter(id => id !== fileId))
    imageList.value = imageList.value.filter(x => x.fileId !== fileId)
  } catch (e) {
    proxy.$modal.msgError('删除失败：' + (e.message || '未知错误'))
  }
}

// ⑦ 差异点：点击缩略图预览
function handlePreview(file) {
  previewUrl.value = file.url
  previewVisible.value = true
}

function formatSize(size) {
  if (!size) return '0 B'
  if (size < 1024) return size + ' B'
  if (size < 1024 * 1024) return (size / 1024).toFixed(2) + ' KB'
  return (size / 1024 / 1024).toFixed(2) + ' MB'
}
</script>

<style scoped>
.upload-image-plus {
  line-height: 1.5;
}
.upload-image-plus :deep(.el-upload--picture-card) {
  width: 100px;
  height: 100px;
  line-height: 100px;
}
.upload-image-plus :deep(.el-upload-list--picture-card .el-upload-list__item) {
  width: 100px;
  height: 100px;
}
/* ⑧ 差异点：达到上限或只读时隐藏上传按钮 */
.upload-image-plus.hide-upload-btn :deep(.el-upload--picture-card) {
  display: none;
}
</style>