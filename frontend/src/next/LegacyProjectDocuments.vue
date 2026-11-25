<script setup>
import {onMounted,ref} from 'vue'
import {api,downloadFile} from './api'
const props=defineProps({projectId:{type:Number,required:true},permissions:{type:Array,default:()=>[]}})
const items=ref([]),more=ref(false),busy=ref(false),error=ref('')
async function load(reset=false){
 busy.value=true;error.value='';try{const page=await api('/business/projects/'+props.projectId+'/legacy-documents?offset='+(reset?0:items.value.length)+'&limit=50');items.value=reset?page.rows:[...items.value,...page.rows];more.value=page.hasMore}catch(e){error.value=e.message}finally{busy.value=false}
}
async function download(f){try{await downloadFile(f.file_id,f.original_name)}catch(e){error.value=e.message}}
onMounted(()=>load(true))
</script>
<template>
<section class="legacy-documents">
 <h4>旧版历史档案</h4><p>原版报告及项目文件，只读留存，不视为新版审核、签发或交付记录。</p>
 <p v-if="error" role="alert">{{error}}</p><p v-if="!items.length&&!busy">暂无已关联的旧版档案。</p>
 <article v-for="d in items" :key="d.id"><b>{{d.title}}</b><small>{{d.report_no||'无报告编号'}} · {{d.original_created_at||'原日期未记录'}} · {{d.source_table}} #{{d.source_key}}</small><small v-if="d.original_status">原状态：{{d.original_status}}</small><p v-if="d.archive_note">{{d.archive_note}}</p><small v-if="!d.files.length">原件未关联，详见迁移档案。</small><button v-if="permissions.includes('file:read')" v-for="f in d.files" :key="f.file_id" @click="download(f)">{{f.original_name}} · 下载原件</button></article>
 <button v-if="more" :disabled="busy" @click="load()">加载更多</button>
</section>
</template>
<style scoped>
.legacy-documents{display:grid;gap:9px;margin-top:16px;padding:12px;border:1px solid var(--erp-border-e3e9f1);border-radius:8px}.legacy-documents h4,.legacy-documents p{margin:0}.legacy-documents p,.legacy-documents small{font-size:12px;color:var(--erp-fg-64748b);line-height:1.6}.legacy-documents article{display:grid;gap:6px;border-top:1px solid var(--erp-border-edf0f5);padding-top:10px}.legacy-documents button{justify-self:start;padding:7px 10px;background:var(--erp-bg-fff);border:1px solid var(--erp-border-dfe6ef);border-radius:6px;color:var(--erp-fg-405675);cursor:pointer;font-size:12px}
</style>
