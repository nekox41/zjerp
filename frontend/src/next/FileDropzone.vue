<script setup>
import {computed,ref} from 'vue'
import {collectUploadFiles,formatUploadSize} from './upload-files.js'
const props=defineProps({
 modelValue:{type:Array,default:()=>[]},label:{type:String,required:true},
 accept:{type:String,default:''},hint:{type:String,default:''},
 multiple:Boolean,maxFiles:{type:Number,default:1},existingCount:{type:Number,default:0},
 maxBytes:{type:Number,default:25*1024*1024},fileLimit:Function,disabled:Boolean
})
const emit=defineEmits(['update:modelValue'])
const input=ref(null),depth=ref(0),errors=ref([])
const dragging=computed(()=>depth.value>0)
const capacity=computed(()=>props.multiple?Math.max(0,props.maxFiles-props.existingCount-props.modelValue.length):1)
const unavailable=computed(()=>props.disabled||capacity.value===0)
function pick(){if(!unavailable.value)input.value?.click()}
function receive(files){
 if(props.disabled||!files.length)return
 const result=collectUploadFiles(props.modelValue,files,{multiple:props.multiple,maxFiles:props.maxFiles,existingCount:props.existingCount,maxBytes:props.fileLimit||props.maxBytes,accept:props.accept})
 errors.value=result.errors
 if(result.changed)emit('update:modelValue',result.files)
}
function change(event){const files=Array.from(event.target.files||[]);event.target.value='';receive(files)}
function cancel(event){event.stopPropagation();depth.value=0}
function enter(event){if(Array.from(event.dataTransfer?.types||[]).includes('Files'))depth.value++}
function leave(){depth.value=Math.max(0,depth.value-1)}
function over(event){if(event.dataTransfer)event.dataTransfer.dropEffect=unavailable.value?'none':'copy'}
function drop(event){depth.value=0;receive(Array.from(event.dataTransfer?.files||[]))}
function remove(i){if(props.disabled)return;errors.value=[];emit('update:modelValue',props.modelValue.filter((_,index)=>i!==index))}
const size=formatUploadSize
</script>
<template>
 <section class="file-dropzone" :aria-label="label" @dragenter.prevent.stop="enter" @dragover.prevent.stop="over" @dragleave.prevent.stop="leave" @drop.prevent.stop="drop">
  <div class="file-zone-title"><b>{{label}}</b><small v-if="multiple">{{existingCount+modelValue.length}} / {{maxFiles}}</small></div>
  <input ref="input" class="file-zone-native" type="file" :accept="accept" :multiple="multiple" :disabled="disabled" tabindex="-1" @change="change" @cancel.stop="cancel">
  <button type="button" class="file-zone-target" :class="{dragging}" :disabled="unavailable" :aria-label="'选择'+label" @click="pick">
   <span class="file-zone-icon" aria-hidden="true">
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M7 17H6a4 4 0 0 1-.6-8A6 6 0 0 1 17 7a5 5 0 0 1 1 10h-1"/><path d="M12 20V10m-4 4 4-4 4 4"/></svg>
   </span>
   <span class="file-zone-copy"><strong>{{dragging&&!unavailable?'松开即可添加':unavailable&&!disabled?'附件数量已达上限':'点击选择或拖放文件'}}</strong><small>{{hint||('单文件最大 '+size(maxBytes))}}</small></span>
   <span class="file-zone-action">{{!multiple&&modelValue.length?'更换文件':'浏览文件'}}</span>
  </button>
  <ul v-if="errors.length" class="file-zone-errors" role="alert"><li v-for="(message,i) in errors" :key="i">{{message}}</li></ul>
  <div v-if="modelValue.length" class="file-zone-list">
   <div v-for="(file,i) in modelValue" :key="file.name+'-'+file.size+'-'+file.lastModified+'-'+i" class="file-zone-row">
    <span class="file-zone-type" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M13 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V10z"/><path d="M13 3v7h7M8 15h8M8 18h5"/></svg></span>
    <span class="file-zone-name"><b :title="file.name">{{file.name}}</b><small>{{size(file.size)}} · 待保存</small></span>
    <button type="button" class="file-zone-remove" :aria-label="'移除 '+file.name" :disabled="disabled" @click="remove(i)">×</button>
   </div>
  </div>
 </section>
</template>
<style scoped>
.file-dropzone{min-width:0;display:grid;gap:9px;color:var(--erp-text);width:100%}
.file-zone-title{display:flex;align-items:center;justify-content:space-between;gap:12px;font-size:13px}.file-zone-title small{font-size:11px;color:var(--erp-muted);font-variant-numeric:tabular-nums}
.file-zone-native{display:none!important}
.file-zone-target{width:100%;min-width:0;display:flex;align-items:center;text-align:left;gap:12px;padding:14px;border:1.5px dashed var(--erp-border);border-radius:14px;color:var(--erp-text);background:var(--erp-subtle);cursor:pointer;transition:border-color .2s,background .2s,transform .2s cubic-bezier(.2,1.5,.4,1);min-height:86px}
.file-zone-target:not(:disabled):hover,.file-zone-target.dragging:not(:disabled){border-color:var(--erp-accent);background:var(--erp-accent-soft)}
.file-zone-target.dragging:not(:disabled){transform:scale(1.015);box-shadow:0 5px 20px #426cdb20}.file-zone-target:disabled{cursor:not-allowed;opacity:.65}
.file-zone-icon,.file-zone-type{display:grid;place-items:center;flex-shrink:0;width:42px;height:42px;border-radius:12px;background:var(--erp-accent-soft);color:var(--erp-accent)}
.file-zone-icon svg,.file-zone-type svg{width:23px;height:23px;transition:transform .22s cubic-bezier(.2,1.6,.4,1)}
.file-zone-target:hover:not(:disabled) .file-zone-icon svg,.file-zone-target.dragging .file-zone-icon svg{transform:translateY(-3px)}
.file-zone-copy{display:grid;gap:5px;flex:1;min-width:0}.file-zone-copy strong{font-size:13px;font-weight:650;line-height:1.45}.file-zone-copy small{font-size:11px;color:var(--erp-muted);line-height:1.5}
.file-zone-action{flex-shrink:0;color:var(--erp-accent);background:var(--erp-surface);border:1px solid var(--erp-border);font-size:11px;font-weight:600;border-radius:9px;padding:7px 10px;white-space:nowrap}
.file-zone-list{display:grid;gap:6px}.file-zone-row{display:flex;align-items:center;gap:10px;border:1px solid var(--erp-border);padding:8px 10px;border-radius:11px;background:var(--erp-surface);min-width:0;animation:file-zone-in .22s ease both}
.file-zone-type{width:34px;height:34px;border-radius:9px}.file-zone-type svg{width:18px;height:18px}.file-zone-name{flex:1;display:grid;gap:3px;min-width:0}.file-zone-name b{font-size:12px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.file-zone-name small{font-size:11px;color:var(--erp-muted)}
.file-zone-remove{flex-shrink:0;width:29px;height:29px;display:grid;place-items:center;border:0;border-radius:8px;background:transparent;color:var(--erp-muted);font-size:21px;line-height:1;cursor:pointer}.file-zone-remove:not(:disabled):hover{background:var(--erp-subtle);color:var(--erp-danger)}
.file-zone-errors{margin:0;padding:0 0 0 17px;color:var(--erp-danger);font-size:12px;line-height:1.6;overflow-wrap:anywhere}
@keyframes file-zone-in{from{opacity:0;transform:translateY(4px)}to{opacity:1;transform:translateY(0)}}
@media(max-width:500px){.file-zone-target{padding:11px;gap:9px}.file-zone-action{display:none}.file-zone-icon{width:35px;height:35px}}
@media(prefers-reduced-motion:reduce){.file-zone-target,.file-zone-icon svg,.file-zone-row{transition:none;animation:none}}
</style>
