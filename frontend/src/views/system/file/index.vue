<template>
  <div class="app-container">
    <el-form :inline="true" @submit.prevent="search">
      <el-form-item label="文件名"><el-input v-model="query.fileName" clearable @keyup.enter="search" /></el-form-item>
      <el-form-item label="业务类型"><el-select v-model="query.bizType" clearable style="width:160px">
        <el-option v-for="(label, value) in types" :key="value" :label="label" :value="value" />
      </el-select></el-form-item>
      <el-form-item><el-button type="primary" @click="search">查询</el-button></el-form-item>
      <el-form-item><el-button :loading="migrating" @click="migrate">迁移旧附件</el-button></el-form-item>
    </el-form>
    <el-alert title="移除业务附件请在对应业务表单中操作。未关联的上传记录可在此删除。" type="info" :closable="false" />
    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="fileId" label="ID" width="90" />
      <el-table-column label="文件名" min-width="240"><template #default="{ row }"><el-link :href="fileUrl(row.fileUrl)" target="_blank">{{ row.fileName }}</el-link></template></el-table-column>
      <el-table-column label="大小" width="110"><template #default="{ row }">{{ row.fileSize == null ? '未核实' : (row.fileSize / 1024).toFixed(1) + ' KB' }}</template></el-table-column>
      <el-table-column label="业务" width="140"><template #default="{ row }">{{ types[row.bizType] || row.bizType || '未关联' }}</template></el-table-column>
      <el-table-column prop="bizId" label="业务ID" width="100" />
      <el-table-column prop="createBy" label="上传人" width="120" />
      <el-table-column prop="createTime" label="上传时间" width="170" />
      <el-table-column label="操作" width="90"><template #default="{ row }"><el-button v-if="!row.bizId" link type="danger" @click="remove(row)">删除</el-button></template></el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" v-model:page="query.pageNum" v-model:limit="query.pageSize" @pagination="load" />
    <el-alert v-if="migrationResult" :title="`迁移成功 ${migrationResult.success} 项，失败 ${migrationResult.failed} 项`" :type="migrationResult.failed ? 'warning' : 'success'" :closable="false" />
    <ul v-if="migrationResult?.errors?.length"><li v-for="error in migrationResult.errors" :key="error">{{ error }}</li></ul>
  </div>
</template>

<script setup>
import { ref, reactive, getCurrentInstance } from 'vue'
import { listFiles, delFile, migrateFiles } from '@/api/system/file'
import { fileUrl } from '@/utils/files'
const { proxy } = getCurrentInstance()
const types = { contract: '合同附件', report: '报告正文', report_attach: '报告附件', wbcard: '维保卡' }
const query = reactive({ pageNum: 1, pageSize: 20, fileName: '', bizType: '' })
const rows = ref([]), total = ref(0), loading = ref(false), migrating = ref(false), migrationResult = ref(null)
async function load() {
  loading.value = true
  try { const result = await listFiles(query); rows.value = result.rows; total.value = result.total }
  finally { loading.value = false }
}
function search() { query.pageNum = 1; load() }
async function remove(row) {
  try { await proxy.$modal.confirm(`确认删除「${row.fileName}」？`); await delFile(row.fileId); await load() }
  catch (_) { /* 取消或错误已由请求拦截器提示 */ }
}
async function migrate() {
  try {
    await proxy.$modal.confirm('请先备份数据库和上传目录。迁移成功后会清空旧附件列，原磁盘文件保留。是否开始？')
    migrating.value = true
    const result = await migrateFiles()
    migrationResult.value = result.data
    await load()
  } catch (_) { /* 取消或错误已由请求拦截器提示 */ }
  finally { migrating.value = false }
}
load()
</script>
