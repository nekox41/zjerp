<script setup>
import {computed,ref,watch,onBeforeUnmount} from 'vue'
import {downloadFile} from './api'
const props=defineProps({artifact:{type:Object,required:true},content:{type:Object,default:()=>({})},canFile:{type:Boolean,default:false},canPhoto:{type:Boolean,default:false}})
const manifest=computed(()=>{try{return typeof props.artifact.generation_manifest==='string'?JSON.parse(props.artifact.generation_manifest):props.artifact.generation_manifest||{}}catch{return {}}})
const data=computed(()=>props.content?.inspectionProfile?.content||{})
const photos=computed(()=>Array.isArray(manifest.value.photos)?manifest.value.photos:[])
const system=ref(''),onlyLow=ref(false),page=ref(0),opened=ref(false),urls=ref({}),original=ref(''),focused=ref(null),error=ref(''),loading=ref(false)
let epoch=0,originalEpoch=0
const systems=computed(()=>[...new Set(photos.value.map(p=>p.system).filter(Boolean))])
const filtered=computed(()=>photos.value.filter(p=>(!system.value||p.system===system.value)&&(!onlyLow.value||Number(p.effectiveDpi)<200)))
const displayed=computed(()=>filtered.value.slice(page.value*12,page.value*12+12))
function release(){epoch++;Object.values(urls.value).forEach(URL.revokeObjectURL);urls.value={}}
function closeOriginal(){originalEpoch++;if(original.value)URL.revokeObjectURL(original.value);original.value='';focused.value=null;loading.value=false}
async function imageUrl(id,kind){
 const token=sessionStorage.getItem('erp-next-token')
 const response=await fetch((import.meta.env.VITE_NEXT_API_BASE||'/next-api')+'/files/'+id+'/'+kind,{headers:token?{Authorization:'Bearer '+token}:{}})
 if(!response.ok)throw Error('照片读取失败（'+response.status+'）')
 return URL.createObjectURL(await response.blob())
}
async function load(){
 release();closeOriginal();error.value=''
 if(!opened.value||!props.canFile||!props.canPhoto)return
 const generation=epoch
 // Bounded concurrency: at most four original-size-independent thumbnail requests in flight.
 for(let i=0;i<displayed.value.length&&generation===epoch;i+=4){
  await Promise.all(displayed.value.slice(i,i+4).map(async p=>{
   try{const url=await imageUrl(p.fileId,'thumbnail');if(generation===epoch)urls.value={...urls.value,[p.photoId]:url};else URL.revokeObjectURL(url)}catch(e){if(generation===epoch)error.value=e.message}
  }))
 }
}
async function focus(p){
 closeOriginal();focused.value=p;loading.value=true;const n=originalEpoch
 try{const url=await imageUrl(p.fileId,'download');if(n===originalEpoch)original.value=url;else URL.revokeObjectURL(url)}catch(e){if(n===originalEpoch)error.value=e.message}finally{if(n===originalEpoch)loading.value=false}
}
async function download(){try{await downloadFile(props.artifact.file_id,props.artifact.original_name)}catch(e){error.value=e.message}}
watch([system,onlyLow],()=>{page.value=0})
watch(()=>props.artifact.id,()=>{system.value='';onlyLow.value=false;page.value=0;opened.value=false})
watch([opened,page,system,onlyLow,()=>props.artifact.id,()=>props.canFile,()=>props.canPhoto],load)
onBeforeUnmount(()=>{release();closeOriginal()})
</script>
<template><section class="review-panel"><div class="review-head"><div><h4>固定文件与审核资料</h4><p>报告 v{{artifact.version_no}} · 文件 #{{artifact.artifact_revision_no}} · {{artifact.original_name}}</p><small>SHA256 {{artifact.sha256}}</small></div><button v-if="canFile" @click="download">下载当前文件 Word 核对</button></div>
<p v-if="manifest.tocPending">当前生成草稿的目录尚待 Word 更新，必须保存更新后的文件并关联此来源重新登记，才能送审。</p><p v-else-if="manifest.layout">请在 Word 中核对页码、章节、照片及分页。若需要修改文件，请由编制人撤回后重新登记，不覆盖已送审原件。</p><p v-if="manifest.sourceArtifactId">人工编辑来源文件 #{{manifest.sourceArtifactId}} · SHA256 {{manifest.sourceArtifactSha256}}</p><p v-if="manifest.photoEvidenceNote">{{manifest.photoEvidenceNote}}</p>
<p v-if="error" role="alert">{{error}}</p>
<details v-if="data.general"><summary>工程及检测依据快照</summary><p>{{data.general.projectName}} · {{data.general.address}}</p><p>{{data.general.scope}}</p><article v-for="s in data.basisReferences||[]" :key="s.id"><b>{{s.standard_no}} {{s.edition}} · {{s.title}}</b><p>{{s.scope}} · {{s.reason}}</p><a :href="s.source_url" target="_blank" rel="noopener noreferrer">来源</a> <a v-if="s.access_url" :href="s.access_url" target="_blank" rel="noopener noreferrer">原文</a></article><p v-if="!data.basisReferences?.length" class="review-text">{{data.general.basis}}</p></details>
<details v-if="data.records?.length"><summary>检测明细快照 · {{data.records.length}} 项</summary><div class="review-table"><table><thead><tr><th>系统 / 项目</th><th>标准要求</th><th>部位及结果</th><th>人工判定</th></tr></thead><tbody><tr v-for="(r,i) in data.records" :key="i"><td>{{r.system}}<br>{{r.item}}</td><td>{{r.standard}}<small>{{r.clauseReference}}</small></td><td>{{r.location}}<br>{{r.result}}</td><td>{{r.judgment}}</td></tr></tbody></table></div><p>人工结论：{{data.conclusion?.text}}</p></details>
<template v-if="canFile&&canPhoto&&photos.length"><div class="review-head"><b>来源照片 {{photos.length}} 张 · {{photos.filter(p=>Number(p.effectiveDpi)<200).length}} 张低于200 DPI</b><button @click="opened=!opened">{{opened?'收起照片':'展开原图审核'}}</button></div><template v-if="opened"><div class="review-head"><select v-model="system"><option value="">全部系统</option><option v-for="s in systems" :key="s">{{s}}</option></select><label><input v-model="onlyLow" type="checkbox">只显示低于200 DPI</label></div><div class="review-grid"><article v-for="p in displayed" :key="p.photoId"><button @click="focus(p)"><img v-if="urls[p.photoId]" :src="urls[p.photoId]" :alt="p.originalName||'现场照片'"><span v-else>读取缩略图</span></button><b>{{p.system||'旧来源'}} · {{p.effectiveDpi}} DPI</b><small>{{p.takenOn}} {{p.caption}}</small></article></div><div class="review-head"><button :disabled="page===0" @click="page--">上一页</button><small>{{filtered.length?page+1:0}} / {{Math.ceil(filtered.length/12)}}</small><button :disabled="(page+1)*12>=filtered.length" @click="page++">下一页</button></div></template></template>
<p v-else-if="!photos.length">本文件没有自动关联的照片来源清单（可能为人工上传或无照片）。需结合实际现场资料人工核对。</p>
<div v-if="focused" class="review-original" @click.self="closeOriginal"><div class="review-dialog"><div class="review-head"><b>{{focused.originalName||'原始照片'}}</b><button @click="closeOriginal">关闭</button></div><p v-if="loading">读取原图…</p><template v-if="original"><a :href="original" target="_blank" rel="noopener">以原始像素打开</a><img :src="original" alt="原始证据照片"></template><p>{{focused.takenOn}} {{focused.caption}}</p></div></div></section></template>
<style scoped>.review-panel{display:grid;gap:12px;border:1px solid var(--erp-border-dfe6ef);background:var(--erp-bg-fff);border-radius:10px;padding:14px;color:var(--erp-fg-405675)}.review-panel h4,.review-panel p{margin:0}.review-panel p,.review-panel small{font-size:12px;line-height:1.7;overflow-wrap:anywhere;white-space:pre-wrap}.review-head{display:flex;align-items:center;justify-content:space-between;gap:8px;flex-wrap:wrap}.review-panel button,.review-panel select{padding:7px;border:1px solid var(--erp-border-dfe6ef);background:var(--erp-bg-fff);border-radius:6px;color:inherit;cursor:pointer}.review-table{max-height:400px;overflow:auto}.review-table table{border-collapse:collapse;width:100%;font-size:12px}.review-table td,.review-table th{border:1px solid var(--erp-border-e4eaf1);padding:6px;white-space:pre-wrap;vertical-align:top}.review-table small{display:block}.review-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(140px,1fr));gap:10px}.review-grid article{display:grid;gap:5px}.review-grid button{height:140px;overflow:hidden}.review-grid img{width:100%;height:100%;object-fit:contain}.review-original{position:fixed;inset:0;z-index:90;background:#0009;display:grid;place-items:center;padding:20px}.review-dialog{background:var(--erp-bg-fff);border-radius:10px;padding:15px;display:grid;gap:10px;max-width:95vw;max-height:95vh;overflow:auto}.review-dialog img{max-width:85vw;max-height:72vh;object-fit:contain}</style>