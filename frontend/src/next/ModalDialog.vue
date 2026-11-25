<script setup>
import {ref,onMounted,onBeforeUnmount,nextTick} from 'vue'
const props=defineProps({title:{type:String,required:true},busy:Boolean,description:String})
const emit=defineEmits(['close'])
const dialog=ref(null)
let previous
function close(){if(!props.busy)emit('close')}
onMounted(async()=>{previous=document.activeElement;await nextTick();dialog.value.showModal()})
onBeforeUnmount(()=>{if(dialog.value?.open)dialog.value.close();previous?.focus?.()})
</script>
<template><Teleport to="body"><dialog ref="dialog" class="erp-dialog" @cancel.prevent="close" @click.self="close"><header class="erp-dialog-header"><div><h2>{{title}}</h2><p v-if="description">{{description}}</p></div><button type="button" class="erp-dialog-close" aria-label="关闭" :disabled="busy" @click="close">×</button></header><slot/></dialog></Teleport></template>
<style scoped>
.erp-dialog{border:1px solid #dbe3ed;border-radius:16px;padding:0;width:min(520px,calc(100vw - 32px));max-height:calc(100dvh - 40px);background:#fff;color:#172b4d;box-shadow:0 20px 80px #10234230;overflow:auto}.erp-dialog::backdrop{background:#172b4d80;backdrop-filter:blur(2px)}.erp-dialog-header{display:flex;align-items:flex-start;justify-content:space-between;gap:12px;padding:24px 24px 16px;border-bottom:1px solid #e9eef4}.erp-dialog-header h2{font-size:19px;margin:0}.erp-dialog-header p{font-size:13px;color:#66758a;margin:7px 0 0;line-height:1.6}.erp-dialog-close{background:transparent;border:0;font-size:24px;color:#64748b;cursor:pointer;line-height:1}.erp-dialog-close:disabled{opacity:.5}
</style>