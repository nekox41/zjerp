<script setup>
import {computed,onMounted,ref} from 'vue'
import {api} from './api'
const config=ref(null),sourceStatus=ref(null),detecting=ref(false),scan=ref(null),preview=ref(null),ledger=ref(null),busy=ref(false),error=ref(''),query=ref('')
const tables=computed(()=>{const q=query.value.trim().toLowerCase();return (scan.value?.tables||[]).filter(t=>!q||t.table.toLowerCase().includes(q))})
const labels={review_mapping:'待业务映射',security_review:'账号权限专项核查',deferred_archive:'暂缓业务原样保全',preserve_raw:'原样保全/人工核查'}
async function detect(){
 if(detecting.value)return
 detecting.value=true;sourceStatus.value=null;scan.value=null;preview.value=null
 try{sourceStatus.value=await api('/migration/source-status')}catch(e){sourceStatus.value={state:'unavailable',message:'检测请求失败：'+e.message}}finally{detecting.value=false}
}
async function load(){error.value='';try{[config.value,ledger.value]=await Promise.all([api('/migration/config'),api('/migration/runs')]);await detect()}catch(e){error.value=e.message}}
async function scanSource(){busy.value=true;error.value='';scan.value=null;preview.value=null;try{scan.value=await api('/migration/scan')}catch(e){error.value=e.message}finally{busy.value=false}}
async function open(t){busy.value=true;error.value='';preview.value=null;try{preview.value=await api('/migration/tables/'+encodeURIComponent(t.table))}catch(e){error.value=e.message}finally{busy.value=false}}
onMounted(load)
</script>
<template>
<section class="migration-panel">
 <h2>旧版数据迁移</h2><p>新版独立运行，旧库不是启动条件。打开本页自动检查已配置来源（原 server 或隔离副本），仅只读检测；实际迁移必须由管理员明确确认，并通过只读归档和预演。</p>
 <div v-if="error" role="alert" class="migration-error">{{error}}</div>
 <div v-if="config" class="migration-card"><h3>旧数据库检测</h3><div class="migration-meta"><span>来源：{{config.sourceSchema}}</span><span>目标：{{config.targetSchema}}</span><span>{{detecting?'正在检测…':sourceStatus?.state==='available'?'发现旧版数据':sourceStatus?.state==='empty'?'旧库为空':sourceStatus?.state==='unrecognized'?'待核对来源':sourceStatus?.state==='not_configured'?'未配置':sourceStatus?.state==='missing'?'不存在':'暂不可用'}}</span></div><p v-if="sourceStatus">{{sourceStatus.message}}<template v-if="sourceStatus.baseTables!==undefined">（可见实体表 {{sourceStatus.baseTables}}）</template></p><p v-if="config.mode==='existing-server-read-only'">旧 server 与 erp_next 可以位于同一个 MySQL 实例，旧库只读。迁移前停止旧应用写入并备份 SQL 和附件。</p><button :disabled="busy||detecting||sourceStatus?.state!=='available'" @click="scanSource">{{busy?'正在读取…':'扫描源库表及冲突'}}</button><button :disabled="busy||detecting" @click="load">重新检测</button></div>
 <div class="migration-card"><h3>导入旧版 SQL（可选）</h3><p>未安装过旧版可直接跳过本页。若有 SQL 备份，在部署主机仓库目录执行以下命令；仅在独立新建的 MySQL 实例内按需建立 server 数据库。已有旧版共享实例不会被 SQL 导入覆盖。请使用单 schema 导出，不含 USE server 或全局管理语句。</p><pre>bash deploy.sh legacy-import /path/to/legacy.sql</pre><p>导入后点击重新检测，并复制旧附件。部署脚本优先复用已识别的旧版 MySQL；不自动搜索网络或尝试密码。网页不直接执行任意 SQL。</p><p>迁移需依次归档、审核映射、预演、明确确认和核对，不随部署执行。</p></div>
 <div v-if="ledger" class="migration-card"><h3>隔离试迁移账本</h3><p>通过仓库离线工具 专用试迁移环境及 new-plan / preview / apply / reconcile 执行。网页仅显示已提交批次与映射，不直接写入旧数据库。</p><div v-if="!ledger.runs.length">尚无提交的迁移批次</div><details v-for="r in ledger.runs" :key="r.id"><summary>批次 #{{r.id}} · {{r.source_namespace}} · {{r.created_at}}</summary><p>档案 SHA256：{{r.archive_sha256}}</p><p>审核映射 SHA256：{{r.decision_sha256}}</p><pre>{{typeof r.summary_json==='string'?r.summary_json:JSON.stringify(r.summary_json,null,2)}}</pre></details><div v-if="ledger.mapped.length" class="migration-scroll"><table><thead><tr><th>来源</th><th>旧表</th><th>新版表</th><th>映射数量</th></tr></thead><tbody><tr v-for="m in ledger.mapped" :key="m.source_namespace+'-'+m.source_table+'-'+m.target_table"><td>{{m.source_namespace}}</td><td>{{m.source_table}}</td><td>{{m.target_table}}</td><td>{{m.mapped_count}}</td></tr></tbody></table></div></div>
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
.migration-panel pre{white-space:pre-wrap;overflow-wrap:anywhere;max-height:260px;overflow:auto;font-size:12px}
</style>
