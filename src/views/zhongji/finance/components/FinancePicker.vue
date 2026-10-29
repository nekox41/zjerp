<template>
  <div class="finance-picker">
    <el-button class="picker-trigger" :disabled="disabled" @click="show"><span class="picker-value"><span class="picker-label">{{selectedLabel || placeholder}}</span><span class="picker-action">选择</span></span></el-button>
    <el-button v-if="modelValue && clearable" link @click="choose(null)">清除</el-button>
    <el-dialog v-model="open" :title="placeholder" width="min(600px,96vw)" append-to-body :close-on-click-modal="false">
      <el-input v-model="query" autofocus clearable placeholder="输入姓名、名称或编号，支持单字搜索" @compositionstart="composing=true" @compositionend="composing=false;schedule()" @input="schedule" @keyup.enter="search"/>
      <p class="picker-hint">{{hint || '打开即可选择，也可以输入关键字缩小范围。'}}</p>
      <el-alert v-if="error" title="搜索失败，请重试" type="error" :closable="false"><el-button link @click="search">重试</el-button></el-alert>
      <div v-loading="loading" class="picker-results">
        <button v-for="option in options" :key="option.id" type="button" class="picker-option" @click="choose(option)"><strong>{{option.name}}</strong><small>{{option.code || option.amount || ''}}</small></button>
        <el-empty v-if="!loading&&!error&&!options.length" description="没有匹配结果，请更换关键词"/>
      </div>
    </el-dialog>
  </div>
</template>
<script setup>
import {onBeforeUnmount,watch} from 'vue'
const props=defineProps({modelValue:[Number,String],label:String,placeholder:{type:String,default:'选择'},loader:{type:Function,required:true},disabled:Boolean,clearable:{type:Boolean,default:true},hint:String})
const emit=defineEmits(['update:modelValue','select']);const open=ref(false),query=ref(''),options=ref([]),loading=ref(false),error=ref(false),selectedLabel=ref(props.label||''),composing=ref(false);let seq=0,timer
watch(()=>props.label,v=>{if(v)selectedLabel.value=v});watch(()=>props.modelValue,v=>{if(!v)selectedLabel.value=''});
function show(){open.value=true;query.value='';search()}
function schedule(){clearTimeout(timer);if(!composing.value)timer=setTimeout(search,250)}
async function search(){clearTimeout(timer);if(composing.value)return;const n=++seq;loading.value=true;error.value=false;try{const result=await props.loader(query.value.trim());if(n===seq)options.value=Array.isArray(result)?result:result.data||[]}catch{if(n===seq){error.value=true;options.value=[]}}finally{if(n===seq)loading.value=false}}
function choose(option){seq++;loading.value=false;selectedLabel.value=option?.name||'';emit('update:modelValue',option?.id??null);emit('select',option);open.value=false}
onBeforeUnmount(()=>{seq++;clearTimeout(timer)})
</script>
<style scoped>.finance-picker{width:100%}.picker-trigger{width:100%;height:auto;min-height:42px;white-space:normal;text-align:left}.picker-trigger>:deep(span){width:100%}.picker-value{display:flex;width:100%;align-items:center;justify-content:space-between;gap:12px}.picker-label{min-width:0;flex:1;overflow-wrap:anywhere}.picker-action{color:#2458d3;flex:none;white-space:nowrap}.picker-results{max-height:52vh;overflow:auto;min-height:100px;margin-top:12px}.picker-option{display:flex;flex-direction:column;width:100%;padding:14px 12px;text-align:left;border:0;border-bottom:1px solid #e5eaf2;background:white;cursor:pointer;color:#17243b}.picker-option:hover,.picker-option:focus-visible{background:#edf3ff;outline:2px solid #2458d3;outline-offset:-2px}.picker-option small,.picker-hint{color:#65748b;margin-top:6px}.picker-hint{font-size:12px;line-height:1.7}</style>
