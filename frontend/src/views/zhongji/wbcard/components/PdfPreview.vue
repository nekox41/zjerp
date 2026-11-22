<template><el-dialog v-model="open" :title="title||'PDF预览'" width="min(1200px,96vw)" destroy-on-close><iframe v-if="open&&url" ref="frame" :src="url" title="PDF文档预览" style="width:100%;height:70vh;border:0"/><template #footer><el-button @click="openFile">新窗口打开</el-button><el-button type="primary" @click="print">打印</el-button><el-button @click="open=false">关闭</el-button></template></el-dialog></template>
<script setup>
const open=defineModel({type:Boolean});const props=defineProps({url:String,title:String});const frame=ref();
function openFile(){window.open(props.url,'_blank','noopener,noreferrer')}
function print(){try{frame.value.contentWindow.focus();frame.value.contentWindow.print()}catch{openFile()}}
</script>
