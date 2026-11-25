<script setup>
import {computed,nextTick,onMounted,ref} from 'vue'
import ModalDialog from './ModalDialog.vue'
const props=defineProps({title:{type:String,required:true},trigger:String,steps:{type:Array,default:()=>['填写资料']},autoOpen:Boolean,busy:Boolean,error:String,saveLabel:{type:String,default:'保存'},disabled:Boolean})
const emit=defineEmits(['close','clear-error'])
const opened=ref(false),step=ref(0),origin=ref(null),body=ref(null),saving=ref(false),localError=ref(''),direction=ref('forward')
const last=computed(()=>step.value===props.steps.length-1)
const waiting=computed(()=>props.busy||saving.value)
const form=()=>body.value?.querySelector('form')
const submitter=()=>form()?.querySelector('button:not([type]),button[type="submit"],input[type="submit"]')
const submitDisabled=ref(false)
let observer
function syncDisabled(){const btn=submitter();submitDisabled.value=!btn||btn.matches(':disabled')}
async function open(event){
 if(opened.value)return
 const trigger=event?.currentTarget||document.activeElement
 origin.value=(event&&typeof event.width==='number'&&typeof event.x==='number'?event:trigger?.getBoundingClientRect?.())||null
 step.value=0;direction.value='forward';localError.value='';emit('clear-error');opened.value=true
 await nextTick();syncDisabled()
 if(body.value){observer?.disconnect();observer=new MutationObserver(syncDisabled);observer.observe(body.value,{subtree:true,attributes:true,attributeFilter:['disabled'],childList:true})}
}
function close(){if(waiting.value)return;observer?.disconnect();opened.value=false;emit('close')}
function controls(){return [...(form()?.querySelectorAll('input,select,textarea')||[])]}
function focusInvalid(el){
 const n=Number(el.closest('[data-wizard-step]')?.dataset.wizardStep||0)
 if(n!==step.value){direction.value=n>step.value?'forward':'back';step.value=n}
 nextTick(()=>{el.focus?.();el.reportValidity?.()})
}
function validCurrent(){
 for(const el of controls()){if(el.willValidate&&el.getClientRects().length&&!el.checkValidity()){focusInvalid(el);return false}}
 return true
}
function go(n){
 if(waiting.value||n<0||n>=props.steps.length)return
 if(n>step.value&&!validCurrent())return
 direction.value=n>step.value?'forward':'back';step.value=n
}
function advance(){
 if(waiting.value)return
 if(!last.value){go(step.value+1);return}
 if(!validCurrent())return
 for(const el of controls())if(el.willValidate&&!el.checkValidity()){focusInvalid(el);return}
 const f=form(),btn=submitter()
 if(f&&btn&&!btn.matches(':disabled'))f.requestSubmit(btn)
}
async function finish(action){
 if(waiting.value)return
 if(!last.value){go(step.value+1);return}
 saving.value=true;localError.value='';emit('clear-error')
 try{await action();await nextTick();if(!props.error){saving.value=false;close()}}
 catch(e){localError.value=e?.message||String(e)}
 finally{saving.value=false}
}
defineExpose({open})
onMounted(()=>{if(props.autoOpen)open()})
import {onBeforeUnmount} from 'vue'
onBeforeUnmount(()=>observer?.disconnect())
</script>
<template>
 <button v-if="!autoOpen" type="button" class="primary-button wizard-trigger" :disabled="disabled||busy" @click="open"><span aria-hidden="true">＋</span>{{trigger||title}}</button>
 <ModalDialog v-if="opened" :title="title" :busy="waiting" :origin="origin" wide @close="close">
  <div v-if="steps.length>1" class="wizard-progress"><div class="wizard-step-meta"><span>步骤 {{step+1}} / {{steps.length}}</span><b>{{steps[step]}}</b></div><div class="wizard-track"><span :style="{width:((step+1)/steps.length*100)+'%'}"/></div><div class="wizard-step-labels"><button v-for="(name,i) in steps" :key="i" type="button" :class="{current:i===step,done:i<step}" :disabled="waiting||i>step" @click="go(i)">{{i<step?'✓ ':''}}{{name}}</button></div></div>
  <div ref="body" class="wizard-body" :class="direction" @keydown.enter.capture="e=>{if(!last&&e.target.tagName==='INPUT'&&e.target.type!=='file'){e.preventDefault();go(step+1)}}">
   <slot :step="step" :finish="finish"/>
  </div>
  <p v-if="localError||error" class="wizard-error" role="alert">{{localError||error}}</p>
  <template #dock>
   <button v-if="step>0" class="wizard-dock-button secondary-button" :disabled="waiting" @click="go(step-1)"><span>←</span>上一步</button>
   <button class="wizard-dock-button primary-button" :disabled="waiting||(last&&submitDisabled)" @click="advance"><span>{{waiting?'…':last?'✓':'→'}}</span>{{waiting?'处理中':last?saveLabel:'下一步'}}</button>
  </template>
 </ModalDialog>
</template>
<style>
.wizard-trigger{display:inline-flex;justify-content:center;align-items:center;gap:9px;width:fit-content;margin:8px 0 12px}.wizard-trigger>span{font-size:18px;line-height:1}
.wizard-progress{padding:18px 26px 0}.wizard-step-meta{display:flex;justify-content:space-between;font-size:12px;color:var(--erp-muted)}.wizard-step-meta b{color:var(--erp-text)}
.wizard-track{height:5px;background:var(--erp-subtle);border-radius:999px;overflow:hidden;margin:11px 0}.wizard-track span{display:block;height:100%;border-radius:999px;background:var(--erp-accent);transition:width .36s cubic-bezier(.2,.8,.2,1)}
.wizard-step-labels{display:flex;gap:8px;flex-wrap:wrap}.wizard-step-labels button{border:0;border-radius:8px;padding:6px 9px;background:var(--erp-subtle);color:var(--erp-muted);font-size:11px}.wizard-step-labels button.current{color:var(--erp-accent);background:var(--erp-accent-soft)}.wizard-step-labels button.done{color:var(--erp-accent)}.wizard-step-labels button:disabled{cursor:default}
.wizard-body{padding:22px 26px;min-width:0}.wizard-body form{margin:0!important;padding:0!important;min-width:0;display:grid!important;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px!important}
.wizard-fields{display:grid;grid-column:1/-1;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px;min-width:0;align-items:start}.wizard-fields>*{min-width:0}.wizard-fields>div,.wizard-fields>section,.wizard-fields>p,.wizard-fields>h4,.wizard-fields>.wide{grid-column:1/-1}.wizard-body.forward .wizard-fields:not([style*="display: none"]){animation:erp-step-in .25s ease both}.wizard-body.back .wizard-fields:not([style*="display: none"]){animation:erp-step-back .25s ease both}
.wizard-body form button:not([type]),.wizard-body form button[type=submit],.wizard-body form input[type=submit]{display:none!important}
.wizard-body form label{display:grid;gap:7px;font-size:12px;color:var(--erp-muted);font-weight:600}.wizard-body form label:has(textarea){grid-column:1/-1}.wizard-body form input:not([type=checkbox]):not([type=radio]),.wizard-body form select,.wizard-body form textarea{width:100%;min-width:0;max-width:100%;background:var(--erp-surface);color:var(--erp-text);border:1px solid var(--erp-border);border-radius:12px;padding:11px 12px;font:inherit}.wizard-body form textarea{min-height:84px;resize:vertical}.wizard-body form input:focus,.wizard-body form select:focus,.wizard-body form textarea:focus{outline:2px solid var(--erp-accent);outline-offset:2px}.wizard-body form label:has(input[type=checkbox]){display:flex;align-items:center;gap:8px}.wizard-body form .muted{margin:0}
.wizard-error{margin:0 26px 22px;color:var(--erp-danger);font-size:13px;line-height:1.6}
.wizard-dock-button{display:flex!important;flex-direction:column;align-items:center;justify-content:center;gap:7px;border-radius:17px!important;padding:12px 10px!important;min-width:82px;min-height:70px;font-size:12px!important;white-space:normal;line-height:1.3;box-shadow:0 8px 24px #101a3020}.wizard-dock-button>span{font-size:23px;line-height:1}.wizard-dock-button:disabled{opacity:.45}
@keyframes erp-step-in{from{opacity:0;transform:translateX(12px)}to{opacity:1;transform:translateX(0)}}@keyframes erp-step-back{from{opacity:0;transform:translateX(-12px)}to{opacity:1;transform:translateX(0)}}
@media(max-width:700px){.wizard-body{padding:18px 20px}.wizard-progress{padding:14px 20px 0}.wizard-fields,.wizard-body form{grid-template-columns:1fr}.wizard-dock-button{flex-direction:row;min-height:52px;min-width:120px}.wizard-error{margin:0 20px 16px}}@media(prefers-reduced-motion:reduce){.wizard-fields,.wizard-track span{animation:none!important;transition:none!important}}
</style>
