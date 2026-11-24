<script setup>
import {computed,onMounted,ref} from 'vue'
import {api} from './api'
const config=ref(null),scan=ref(null),preview=ref(null),busy=ref(false),error=ref(''),query=ref('')
const tables=computed(()=>{const q=query.value.trim().toLowerCase();return (scan.value?.tables||[]).filter(t=>!q||t.table.toLowerCase().includes(q))})
const labels={review_mapping:'待业务映射',security_review:'账号权限专项核查',deferred_archive:'暂缓业务原样保全',preserve_raw:'原样保全/人工核查'}
async function load(){try{config.value=await api('/migration/config')}catch(e){error.value=e.message}}
async function scanSource(){busy.value=true;error.value='';scan.value=null;preview.value=null;try{scan.value=await api('/migration/scan')}catch(e){error.value=e.message}finally{busy.value=false}}
async function open(t){busy.value=true;error.value='';preview.value=null;try{preview.value=await api('/migration/tables/'+encodeURIComponent(t.table))}catch(e){error.value=e.message}finally{busy.value=false}}
onMounted(load)
</script>
<template>
<section class="migration-panel">
 <h2>旧版数据迁移 · 只读预检</h2><p>只连接隔离的旧库副本（server_ 开头），不直接使用生产 server。配置在服务端环境变量中，网页不提交数据库密码。本页不执行业务写入。</p>
 <div v-if="error" role="alert" class="migration-error">{{error}}</div>
 <div v-if="config" class="migration-card"><h3>迁移来源</h3><div class="migration-meta"><span>来源：{{config.sourceSchema}}</span><span>目标：{{config.targetSchema}}</span><span>{{config.configured?'已配置只读来源':'尚未启用/配置'}}</span></div><p>配置 ERP_LEGACY_SCAN_ENABLED、ERP_LEGACY_DB_URL、ERP_LEGACY_DB_USER、ERP_LEGACY_DB_PASSWORD、ERP_LEGACY_DB_SCHEMA 后重启新版 API。请仅授权 SELECT，且确认连接到离线副本。</p><button :disabled="busy||!config.configured" @click="scanSource">{{busy?'正在读取…':'扫描源库表及冲突'}}</button> <button :disabled="busy" @click="load">刷新配置</button></div>
 <template v-if="scan">
  <div class="migration-card"><h3>扫描结果</h3><div class="migration-meta"><span>实体表 {{scan.baseTables}}</span><span>已计数记录 {{scan.countedRows}}</span><span>计数失败 {{scan.failedCounts}}</span></div><p>无法读取或未纳入自动映射的表不会被视作空表或允许丢弃。视图仅记录结构，不重复迁移其行。</p></div>
  <div class="migration-card"><h3>冲突与人工核查</h3><table><thead><tr><th>检查</th><th>数量 / 问题</th></tr></thead><tbody><tr v-for="c in scan.conflicts" :key="c.key"><td>{{c.description}} <small>{{c.key}}</small></td><td :class="{'migration-error':c.error||c.count>0}">{{c.error||c.count}}</td></tr></tbody></table></div>
  <div class="migration-card"><h3>表级数据清单</h3><input v-model="query" placeholder="筛选表名"><div class="migration-scroll"><table><thead><tr><th>表</th><th>类型</th><th>记录数</th><th>处理方式</th><th>操作</th></tr></thead><tbody><tr v-for="t in tables" :key="t.table"><td>{{t.table}}</td><td>{{t.type}}</td><td>{{t.error||(t.rows??'—')}}</td><td>{{labels[t.disposition]}}</td><td><button v-if="t.previewAllowed" :disabled="busy" @click="open(t)">预览</button></td></tr></tbody></table></div></div>
 </template>
 <div v-if="preview" class="migration-card"><h3>{{preview.table}} · 前 {{preview.limit}} 条</h3><p>仅作业务核查，长文本截断；凭证、文件路径、快照等敏感字段不展示。完整原始数据以离线备份为准。</p><div class="migration-scroll"><table><thead><tr><th v-for="c in preview.columns" :key="c">{{c}}</th></tr></thead><tbody><tr v-for="(row,i) in preview.rows" :key="i"><td v-for="c in preview.columns" :key="c">{{row[c]??'—'}}</td></tr></tbody></table></div><button @click="preview=null">关闭预览</button></div>
</section>
</template>
<style scoped>
.migration-panel{display:grid;gap:16px;color:#34465e}.migration-panel h2,.migration-panel h3,.migration-panel p{margin:0}.migration-panel p{font-size:12px;line-height:1.8;color:#65758c}.migration-card{display:grid;gap:12px;background:white;border:1px solid #e2e8f1;border-radius:12px;padding:16px;min-width:0}.migration-meta{display:flex;gap:16px;flex-wrap:wrap;font-weight:600}.migration-scroll{overflow:auto;max-height:520px}.migration-panel table{border-collapse:collapse;width:100%;font-size:12px}.migration-panel td,.migration-panel th{border-bottom:1px solid #e4eaf1;padding:10px;text-align:left;max-width:320px;overflow-wrap:anywhere}.migration-panel small{display:block;color:#8290a3}.migration-panel button,.migration-panel input{padding:8px 12px;border:1px solid #dfe6ef;border-radius:7px;background:white;color:#405675}.migration-panel button{cursor:pointer;justify-self:start}.migration-panel button:disabled{opacity:.5;cursor:not-allowed}.migration-error{color:#ab3535}
</style>
