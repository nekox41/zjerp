<script setup>
import {ref,onMounted,onBeforeUnmount,nextTick} from 'vue'
const props=defineProps({title:{type:String,required:true},busy:Boolean,description:String,wide:Boolean,origin:Object})
const emit=defineEmits(['close'])
const dialog=ref(null),panel=ref(null),closing=ref(false)
let previous,timer
const reduced=()=>window.matchMedia('(prefers-reduced-motion: reduce)').matches
function close(){
 if(props.busy||closing.value)return
 if(reduced()){emit('close');return}
 closing.value=true;timer=setTimeout(()=>emit('close'),180)
}
onMounted(async()=>{
 previous=document.activeElement;await nextTick()
 if(!dialog.value?.isConnected)return
 dialog.value.showModal()
 const rect=panel.value.getBoundingClientRect()
 const source=props.origin||previous?.getBoundingClientRect?.()
 const x=source?(source.x+source.width/2):rect.x+rect.width/2
 const y=source?(source.y+source.height/2):rect.y+rect.height/2
 dialog.value.style.setProperty('--pop-x',Math.max(-innerWidth,Math.min(innerWidth,x-(rect.x+rect.width/2)))+'px')
 dialog.value.style.setProperty('--pop-y',Math.max(-innerHeight,Math.min(innerHeight,y-(rect.y+rect.height/2)))+'px')
 dialog.value.classList.add('entered')
})
onBeforeUnmount(()=>{clearTimeout(timer);if(dialog.value?.open)dialog.value.close();if(previous?.isConnected)previous.focus?.({preventScroll:true})})
</script>
<template>
 <Teleport to="body">
  <dialog ref="dialog" class="erp-dialog" :class="{wide,closing}" :aria-label="title" @cancel.prevent="close" @click.self="close">
   <div class="erp-dialog-layout" @click.self="close">
    <section ref="panel" class="erp-dialog-panel">
     <header class="erp-dialog-header"><div><h2>{{title}}</h2><p v-if="description">{{description}}</p></div><button type="button" class="erp-dialog-close" aria-label="关闭" :disabled="busy" @click="close">×</button></header>
     <slot/>
    </section>
    <aside v-if="$slots.dock" class="erp-dialog-dock" aria-label="弹窗操作"><slot name="dock"/></aside>
   </div>
  </dialog>
 </Teleport>
</template>
<style>
.erp-dialog{--pop-x:0px;--pop-y:0px;border:0;background:transparent;color:var(--erp-text);padding:22px;width:min(820px,100vw);max-width:100vw;max-height:100dvh;overflow:visible}
.erp-dialog:not([open]){display:none}.erp-dialog::backdrop{background:#101a30a6;backdrop-filter:blur(5px);animation:erp-fade .22s ease both}
.erp-dialog-layout{display:flex;align-items:center;justify-content:center;gap:14px;max-height:calc(100dvh - 44px)}
.erp-dialog-panel{width:min(520px,100%);max-height:calc(100dvh - 44px);overflow:auto;overscroll-behavior:contain;border:1px solid var(--erp-border);border-radius:24px;background:var(--erp-surface);box-shadow:0 28px 85px #101a3040;opacity:0;scrollbar-width:thin}
.erp-dialog.wide .erp-dialog-panel{width:min(660px,100%)}
.erp-dialog.entered .erp-dialog-panel{animation:erp-pop-in .42s cubic-bezier(.19,.87,.24,1.2) both}
.erp-dialog.closing .erp-dialog-panel{animation:erp-pop-out .18s ease-in both}
.erp-dialog-header{display:flex;align-items:flex-start;justify-content:space-between;gap:16px;padding:24px 26px 18px;border-bottom:1px solid var(--erp-border);position:sticky;top:0;z-index:2;background:var(--erp-surface)}
.erp-dialog-header h2{font-size:20px;letter-spacing:-.03em;margin:0}.erp-dialog-header p{font-size:13px;color:var(--erp-muted);margin:7px 0 0;line-height:1.6}
.erp-dialog-close{flex-shrink:0;width:34px;height:34px;display:grid;place-items:center;border:0;border-radius:12px;background:var(--erp-subtle);color:var(--erp-muted);font-size:25px;line-height:1}.erp-dialog-close:hover{color:var(--erp-text)}
.erp-dialog-dock{flex:0 0 86px;display:grid;gap:10px;align-content:center;opacity:0}.erp-dialog.entered .erp-dialog-dock{animation:erp-dock .28s .14s ease both}
@keyframes erp-pop-in{0%{opacity:0;transform:translate(var(--pop-x),var(--pop-y)) scale(.08)}65%{opacity:1;transform:translate(0,0) scale(1.025)}100%{opacity:1;transform:translate(0,0) scale(1)}}
@keyframes erp-pop-out{to{opacity:0;transform:translate(calc(var(--pop-x)*.45),calc(var(--pop-y)*.45)) scale(.4)}}@keyframes erp-fade{from{opacity:0}to{opacity:1}}@keyframes erp-dock{from{opacity:0;transform:translateX(-12px)}to{opacity:1;transform:translateX(0)}}
@media(max-width:700px){.erp-dialog{padding:12px;width:100vw}.erp-dialog-layout{flex-direction:column;max-height:calc(100dvh - 24px);gap:10px}.erp-dialog-panel,.erp-dialog.wide .erp-dialog-panel{width:100%;max-height:calc(100dvh - 102px)}.erp-dialog-dock{flex:0 0 auto;width:100%;display:flex;justify-content:center}.erp-dialog-header{padding:18px 20px 14px}}
@media(prefers-reduced-motion:reduce){.erp-dialog.entered .erp-dialog-panel,.erp-dialog.entered .erp-dialog-dock,.erp-dialog::backdrop{animation:none;opacity:1}}
</style>
